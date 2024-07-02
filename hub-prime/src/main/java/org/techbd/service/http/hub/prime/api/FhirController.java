package org.techbd.service.http.hub.prime.api;

import static org.techbd.udi.auto.jooq.ingress.Tables.INTERACTION_HTTP_REQUEST;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.techbd.conf.Configuration;
import org.techbd.orchestrate.fhir.OrchestrationEngine;
import org.techbd.orchestrate.fhir.OrchestrationEngine.Device;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.Helpers;
import org.techbd.service.http.InteractionsFilter;
import org.techbd.service.http.SandboxHelpers;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Mono;

@Controller
@Tag(name = "TechBD Hub FHIR Endpoints")
public class FhirController {
    private static final Logger LOG = LoggerFactory.getLogger(FhirController.class.getName());

    private final OrchestrationEngine engine = new OrchestrationEngine();
    private final AppConfig appConfig;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;

    public FhirController(final Environment environment, final AppConfig appConfig,
            final UdiPrimeJpaConfig udiPrimeJpaConfig,
            final SftpManager sftpManager,
            final SandboxHelpers sboxHelpers) {
        this.appConfig = appConfig;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
    }

    @GetMapping(value = "/metadata", produces = { MediaType.APPLICATION_XML_VALUE })
    @Operation(summary = "FHIR server's conformance statement")
    public String metadata(final Model model, HttpServletRequest request) {
        final var baseUrl = Helpers.getBaseUrl(request);

        model.addAttribute("version", appConfig.getVersion());
        model.addAttribute("implUrlValue", baseUrl);
        model.addAttribute("opDefnValue", baseUrl + "/OperationDefinition/Bundle--validate");

        return "metadata.xml";
    }

    @PostMapping(value = { "/Bundle", "/Bundle/" }, consumes = { MediaType.APPLICATION_JSON_VALUE,
            AppConfig.Servlet.FHIR_CONTENT_TYPE_HEADER_VALUE })
    @Operation(summary = "Endpoint to to validate, store, and then forward a payload to SHIN-NY. If you want to validate a payload and not store it or forward it to SHIN-NY, use $validate.")
    @ResponseBody
    @Async
    public Object validateBundleAndForward(final @RequestBody @Nonnull String payload,
            @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
            // "profile" is the same name that HL7 validator uses
            @RequestParam(value = "profile", required = false) String fhirProfileUrlParam,
            @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.FHIR_STRUCT_DEFN_PROFILE_URI, required = false) String fhirProfileUrlHeader,
            @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.FHIR_VALIDATION_STRATEGY, required = false) String uaValidationStrategyJson,
            @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.DATALAKE_API_URL, required = false) String customDataLakeApi,
            @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.DATALAKE_API_CONTENT_TYPE, required = false) String dataLakeApiContentType,
            @RequestParam(value = "immediate", required = false) boolean isSync,
            @RequestParam(value = "include-request-in-outcome", required = false) boolean includeRequestInOutcome,
            @RequestParam(value = "include-incoming-payload-in-db", required = false) boolean includeIncomingPayloadInDB,
            final HttpServletRequest request) throws SQLException {

        final var requestURI = request.getRequestURI();
        final var provenance = "%s.validateBundleAndForward(%s)".formatted(FhirController.class.getName(),
                isSync ? "sync" : "async");
        final var bundleAsyncInteractionId = InteractionsFilter.getActiveRequestEnc(request).requestId().toString();
        final var fhirProfileUrl = (fhirProfileUrlParam != null) ? fhirProfileUrlParam
                : (fhirProfileUrlHeader != null) ? fhirProfileUrlHeader : appConfig.getDefaultSdohFhirProfileUrl();
        final var sessionBuilder = engine.session()
                .onDevice(Device.createDefault())
                .withPayloads(List.of(payload))
                .withFhirProfileUrl(fhirProfileUrl)
                .addHapiValidationEngine() // by default
                // clearExisting is set to true so engines can be fully supplied through header
                .withUserAgentValidationStrategy(uaValidationStrategyJson, true);
        final var session = sessionBuilder.build();
        engine.orchestrate(session);

        // TODO: if there are errors that should prevent forwarding, stop here
        // TODO: need to implement `immediate` (sync) webClient op, right now it's async
        // only

        // immediateResult is what's returned to the user while async operation
        // continues
        final var forwardedAt = OffsetDateTime.now();
        final var baseUrl = Helpers.getBaseUrl(request);
        final var immediateResult = new HashMap<>(Map.of(
                "resourceType", "OperationOutcome",
                "bundleSessionId", bundleAsyncInteractionId, // for tracking in database, etc.
                "isAsync", true,
                "validationResults", session.getValidationResults(),
                "statusUrl",
                baseUrl + "/Bundle/$status/" + bundleAsyncInteractionId.toString(),
                "device", session.getDevice()));
        final var result = Map.of("OperationOutcome", immediateResult);
        if (uaValidationStrategyJson != null) {
            immediateResult.put("uaValidationStrategy",
                    Map.of(AppConfig.Servlet.HeaderName.Request.FHIR_VALIDATION_STRATEGY, uaValidationStrategyJson,
                            "issues",
                            sessionBuilder.getUaStrategyJsonIssues()));
        }
        if (includeRequestInOutcome) {
            immediateResult.put("request", InteractionsFilter.getActiveRequestEnc(request));
        }

        final var DSL = udiPrimeJpaConfig.dsl();
        final var jooqCfg = DSL.configuration();
        final var dataLakeApiBaseURL = customDataLakeApi != null && !customDataLakeApi.isEmpty() ? customDataLakeApi
                : appConfig.getDefaultDatalakeApiUrl();
        final var webClient = WebClient.builder().baseUrl(dataLakeApiBaseURL)
                .filter(ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
                    final var requestBuilder = new StringBuilder();
                    requestBuilder.append(clientRequest.method().name())
                            .append(" ")
                            .append(clientRequest.url())
                            .append(" HTTP/1.1")
                            .append("\n");
                    clientRequest.headers().forEach((name, values) -> values
                            .forEach(value -> requestBuilder.append(name).append(": ").append(value).append("\n")));

                    final var payloadRIHR = new RegisterInteractionHttpRequest();
                    try {
                        payloadRIHR.setInteractionId(bundleAsyncInteractionId);
                        payloadRIHR.setInteractionKey(requestURI);
                        payloadRIHR.setNature(Configuration.objectMapper.valueToTree(
                                Map.of("nature", "Original FHIR Payload", "tenant_id", tenantId)));
                        payloadRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                        try {
                            // input FHIR Bundle JSON payload
                            payloadRIHR.setPayload(Configuration.objectMapper.readTree(payload));
                        } catch (JsonProcessingException jpe) {
                            // in case the payload is not JSON store the string
                            payloadRIHR.setPayload(Configuration.objectMapper.valueToTree(payload));
                        }
                        payloadRIHR.setFromState("NONE");
                        payloadRIHR.setToState("ACCEPT_FHIR_BUNDLE");
                        payloadRIHR.setCreatedAt(OffsetDateTime.now());
                        payloadRIHR.setCreatedBy(FhirController.class.getName());
                        payloadRIHR.setProvenance(provenance);
                        final var execResult = payloadRIHR.execute(jooqCfg);
                        LOG.info("payloadRIHR execResult" + execResult);
                    } catch (Exception e) {
                        LOG.error("CALL " + payloadRIHR.getName() + " payloadRIHR error", e);
                    }

                    final var outboundHttpMessage = requestBuilder.toString();
                    final var initRIHR = new RegisterInteractionHttpRequest();
                    try {
                        immediateResult.put("outboundHttpMessage",
                                outboundHttpMessage + "\n" + (includeIncomingPayloadInDB ? payload
                                        : "The incoming FHIR payload was not stored (to save space).\nThis is not an error or warning just an FYI - if you'd like to see the incoming FHIR payload for debugging, next time just pass in the optional `?include-incoming-payload-in-db=true` to request payload storage for each request that you'd like to store."));
                        initRIHR.setInteractionId(bundleAsyncInteractionId);
                        initRIHR.setInteractionKey(requestURI);
                        initRIHR.setNature(Configuration.objectMapper.valueToTree(
                                Map.of("nature", "Forward HTTP Request", "tenant_id", tenantId)));
                        initRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                        initRIHR.setPayload(Configuration.objectMapper.valueToTree(immediateResult));
                        initRIHR.setFromState("ACCEPT_FHIR_BUNDLE");
                        initRIHR.setToState("FORWARD");
                        initRIHR.setCreatedAt(forwardedAt); // don't let DB set this, use app time
                        initRIHR.setCreatedBy(FhirController.class.getName());
                        initRIHR.setProvenance(provenance);
                        final var execResult = initRIHR.execute(jooqCfg);
                        LOG.info("initRIHR execResult" + execResult);
                    } catch (Exception e) {
                        LOG.error("CALL " + initRIHR.getName() + " initRIHR error", e);
                    }
                    return Mono.just(clientRequest);
                })).build();

        try {
            webClient.post()
                    .uri("?processingAgent=" + tenantId)
                    .body(BodyInserters.fromValue(payload))
                    .header("Content-Type",
                            Optional.ofNullable(
                                    Optional.ofNullable(dataLakeApiContentType).orElse(request.getContentType()))
                                    .orElse(AppConfig.Servlet.FHIR_CONTENT_TYPE_HEADER_VALUE))
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(response -> {
                        final var forwardRIHR = new RegisterInteractionHttpRequest();
                        try {
                            forwardRIHR.setInteractionId(bundleAsyncInteractionId);
                            forwardRIHR.setInteractionKey(requestURI);
                            forwardRIHR.setNature(Configuration.objectMapper.valueToTree(
                                    Map.of("nature", "Forwarded HTTP Response", "tenant_id", tenantId)));
                            forwardRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                            try {
                                // expecting a JSON payload from the server
                                forwardRIHR.setPayload(Configuration.objectMapper.readTree(response));
                            } catch (JsonProcessingException jpe) {
                                // in case the payload is not JSON store the string
                                forwardRIHR.setPayload(Configuration.objectMapper.valueToTree(response));
                            }
                            forwardRIHR.setFromState("FORWARD");
                            forwardRIHR.setToState("COMPLETE");
                            forwardRIHR.setCreatedAt(OffsetDateTime.now()); // don't let DB set this, use app time
                            forwardRIHR.setCreatedBy(FhirController.class.getName());
                            forwardRIHR.setProvenance(provenance);
                            final var execResult = forwardRIHR.execute(jooqCfg);
                            LOG.info("forwardRIHR execResult" + execResult);
                        } catch (Exception e) {
                            LOG.error("CALL " + forwardRIHR.getName() + " forwardRIHR error", e);
                        }
                    }, (Throwable error) -> { // Explicitly specify the type Throwable
                        final var errorRIHR = new RegisterInteractionHttpRequest();
                        try {
                            errorRIHR.setInteractionId(bundleAsyncInteractionId);
                            errorRIHR.setInteractionKey(requestURI);
                            errorRIHR.setNature(Configuration.objectMapper.valueToTree(
                                    Map.of("nature", "Forwarded HTTP Response Error", "tenant_id", tenantId)));
                            errorRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                            errorRIHR.setPayload(Configuration.objectMapper.valueToTree(
                                    Map.of("dataLakeApiBaseURL", dataLakeApiBaseURL, "error", error.toString(),
                                            "tenantId", tenantId)));
                            errorRIHR.setFromState("FORWARD");
                            errorRIHR.setToState("FAIL");
                            errorRIHR.setCreatedAt(OffsetDateTime.now()); // don't let DB set this, use app time
                            errorRIHR.setCreatedBy(FhirController.class.getName());
                            errorRIHR.setProvenance(provenance);
                            final var execResult = errorRIHR.execute(jooqCfg);
                            LOG.info("forwardRIHR execResult" + execResult);
                        } catch (Exception e) {
                            LOG.error("CALL " + errorRIHR.getName() + " errorRIHR error", e);
                        }
                    });
        } catch (Exception e) {
            LOG.error(provenance, e);
            immediateResult.put("exception", e.toString());
            e.printStackTrace();
        }

        return result;
    }

    @PostMapping(value = { "/Bundle/$validate", "/Bundle/$validate/" }, consumes = { MediaType.APPLICATION_JSON_VALUE,
            AppConfig.Servlet.FHIR_CONTENT_TYPE_HEADER_VALUE })
    @Operation(summary = "Endpoint to validate but not store or forward a payload to SHIN-NY. If you want to validate a payload, store it and then forward it to SHIN-NY, use /Bundle not /Bundle/$validate.")
    @ResponseBody
    public Object validateBundle(final @RequestBody @Nonnull String payload,
            @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
            // "profile" is the same name that HL7 validator uses
            @RequestParam(value = "profile", required = false) String fhirProfileUrlParam,
            @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.FHIR_STRUCT_DEFN_PROFILE_URI, required = false) String fhirProfileUrlHeader,
            @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.FHIR_VALIDATION_STRATEGY, required = false) String uaValidationStrategyJson,
            @RequestParam(value = "include-request-in-outcome", required = false) boolean includeRequestInOutcome,
            final HttpServletRequest request) {

        final var fhirProfileUrl = (fhirProfileUrlParam != null) ? fhirProfileUrlParam
                : (fhirProfileUrlHeader != null) ? fhirProfileUrlHeader : appConfig.getDefaultSdohFhirProfileUrl();
        final var sessionBuilder = engine.session()
                .onDevice(Device.createDefault())
                .withPayloads(List.of(payload))
                .withFhirProfileUrl(fhirProfileUrl)
                .addHapiValidationEngine() // by default
                // clearExisting is set to true so engines can be fully supplied through header
                .withUserAgentValidationStrategy(uaValidationStrategyJson, true);
        final var session = sessionBuilder.build();
        engine.orchestrate(session);

        final var opOutcome = new HashMap<>(Map.of("resourceType", "OperationOutcome", "validationResults",
                session.getValidationResults(), "device",
                session.getDevice()));
        final var result = Map.of("OperationOutcome", opOutcome);
        if (uaValidationStrategyJson != null) {
            opOutcome.put("uaValidationStrategy",
                    Map.of(AppConfig.Servlet.HeaderName.Request.FHIR_VALIDATION_STRATEGY, uaValidationStrategyJson,
                            "issues",
                            sessionBuilder.getUaStrategyJsonIssues()));
        }
        if (includeRequestInOutcome) {
            opOutcome.put("request", InteractionsFilter.getActiveRequestEnc(request));
        }

        return result;
    }

    @GetMapping(value = "/Bundle/$status/{bundleSessionId}", produces = { "application/json", "text/html" })
    @ResponseBody
    @Operation(summary = "Check the state/status of async operation")
    public Object bundleStatus(@PathVariable String bundleSessionId, final Model model, HttpServletRequest request) {
        final var jooqDSL = udiPrimeJpaConfig.dsl();
        try {
            final var result = jooqDSL.select()
                    .from(INTERACTION_HTTP_REQUEST)
                    .where(INTERACTION_HTTP_REQUEST.INTERACTION_ID.eq(bundleSessionId))
                    .fetch();
            return Configuration.objectMapper.writeValueAsString(result.intoMaps());
        } catch (Exception e) {
            LOG.error("Error executing JOOQ query for retrieving SAT_INTERACTION_HTTP_REQUEST.HUB_INTERACTION_ID for "
                    + bundleSessionId, e);
            return """
                    "error": "%s",
                    "bundleSessionId": "%s"
                    """.formatted(e.toString(), bundleSessionId);
        }
    }

    @Operation(summary = "Send mock JSON payloads pretending to be from SHIN-NY Data Lake 1115 Waiver validation (scorecard) server.")
    @GetMapping("/mock/shinny-data-lake/1115-validate/{resourcePath}.json")
    public ResponseEntity<String> getJsonFile(
            @PathVariable String resourcePath,
            @RequestParam(required = false, defaultValue = "0") long simulateLifetimeMs) {
        final var cpResourceName = "templates/mock/shinny-data-lake/1115-validate/" + resourcePath + ".json";
        try {
            if (simulateLifetimeMs > 0) {
                Thread.sleep(simulateLifetimeMs);
            }
            ClassPathResource resource = new ClassPathResource(cpResourceName);
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new ResponseEntity<>(content, headers, HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(cpResourceName + " not found", HttpStatus.NOT_FOUND);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ResponseEntity<>("Request interrupted", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}