package org.techbd.service.http.hub.prime.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.techbd.conf.Configuration;
import org.techbd.orchestrate.fhir.OrchestrationEngine;
import org.techbd.orchestrate.fhir.OrchestrationEngine.Device;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.Helpers;
import org.techbd.service.http.InteractionsFilter;
import org.techbd.service.http.SandboxHelpers;
import org.techbd.service.http.hub.CustomRequestWrapper;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.udi.UdiPrimeJpaConfig;
import static org.techbd.udi.auto.jooq.ingress.Tables.INTERACTION_HTTP_REQUEST;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.oauth2.sdk.util.CollectionUtils;

import ca.uhn.fhir.validation.ResultSeverityEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Mono;

@Controller
@Tag(name = "Tech by Design Hub FHIR Endpoints")
public class FhirController {

    private static final Logger LOG = LoggerFactory.getLogger(FhirController.class.getName());

    private final OrchestrationEngine engine = new OrchestrationEngine();
    private final AppConfig appConfig;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;

    public FhirController(@SuppressWarnings("PMD.UnusedFormalParameter") final Environment environment,
            final AppConfig appConfig,
            final UdiPrimeJpaConfig udiPrimeJpaConfig,
            @SuppressWarnings("PMD.UnusedFormalParameter") final SftpManager sftpManager,
            @SuppressWarnings("PMD.UnusedFormalParameter") final SandboxHelpers sboxHelpers) {
        this.appConfig = appConfig;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
    }

    @GetMapping(value = "/metadata", produces = {MediaType.APPLICATION_XML_VALUE})
    @Operation(summary = "FHIR server's conformance statement")
    public String metadata(final Model model, HttpServletRequest request) {
        final var baseUrl = Helpers.getBaseUrl(request);

        model.addAttribute("version", appConfig.getVersion());
        model.addAttribute("implUrlValue", baseUrl);
        model.addAttribute("opDefnValue", baseUrl + "/OperationDefinition/Bundle--validate");

        return "metadata.xml";
    }

    @PostMapping(value = {"/Bundle", "/Bundle/"}, consumes = {MediaType.APPLICATION_JSON_VALUE,
        AppConfig.Servlet.FHIR_CONTENT_TYPE_HEADER_VALUE})
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
            @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.HEALTH_CHECK_HEADER, required = false) String healthCheck,
            @RequestParam(value = "immediate", required = false) boolean isSync,
            @RequestParam(value = "include-request-in-outcome", required = false) boolean includeRequestInOutcome,
            @RequestParam(value = "include-incoming-payload-in-db", required = false) boolean includeIncomingPayloadInDB,
            HttpServletRequest request) throws SQLException {

        final var requestURI = request.getRequestURI();
        final var provenance = "%s.validateBundleAndForward(%s)".formatted(FhirController.class.getName(),
                isSync ? "sync" : "async");
        request = new CustomRequestWrapper(request, payload);
        final var bundleAsyncInteractionId = InteractionsFilter.getActiveRequestEnc(request).requestId().toString();
        final var fhirProfileUrl = (fhirProfileUrlParam != null) ? fhirProfileUrlParam
                : (fhirProfileUrlHeader != null) ? fhirProfileUrlHeader : appConfig.getDefaultSdohFhirProfileUrl();
        LOG.info("Getting structure definition Urls from config - Before: ");
        final var structureDefintionUrls = appConfig.getStructureDefinitionsUrls();
        LOG.info("Getting structure definition Urls from config - After : ", structureDefintionUrls);
        final var valueSetUrls = appConfig.getValueSetUrls();
        LOG.info(" Total value system URLS  in config: ", null != valueSetUrls ? valueSetUrls.size() : 0);
        final var codeSystemUrls = appConfig.getCodeSystemUrls();
        LOG.info(" Total code system URLS  in config: ", null != codeSystemUrls ? codeSystemUrls.size() : 0);
        final var sessionBuilder = engine.session()
                .onDevice(Device.createDefault())
                .withPayloads(List.of(payload))
                .withFhirProfileUrl(fhirProfileUrl)
                .withFhirStructureDefinitionUrls(structureDefintionUrls)
                .withFhirCodeSystemUrls(codeSystemUrls)
                .withFhirValueSetUrls(valueSetUrls)
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

        // Check for the X-TechBD-HealthCheck header
        if ("true".equals(healthCheck)) {
            LOG.info("%s is true, skipping DataLake submission.".formatted(AppConfig.Servlet.HeaderName.Request.HEALTH_CHECK_HEADER));
            return result; // Return without proceeding to DataLake submission
        }

        final var DSL = udiPrimeJpaConfig.dsl();
        final var jooqCfg = DSL.configuration();
        // Check if the validation results has last updated date missing error.If so ,
        // do not forward to scoring engine.
        var hasLastUpdatedMissingError = session.getValidationResults().stream()
                .map(OrchestrationEngine.ValidationResult::getIssues)
                .filter(CollectionUtils::isNotEmpty)
                .flatMap(List::stream)
                .toList().stream()
                .anyMatch(issue -> (ResultSeverityEnum.ERROR.getCode().equalsIgnoreCase(issue.getSeverity()) &&
                        issue.getMessage().contains("Meta.lastUpdated")) ||
                        (ResultSeverityEnum.FATAL.getCode().equalsIgnoreCase(issue.getSeverity()) &&
                                issue.getMessage().contains("lastUpdated")));
        if (hasLastUpdatedMissingError) {
            LOG.info(
                    "PAYLOAD NOT FORWARDED TO SCORING ENGINE : Meta.lastUpdated field has validation errors for interaction id {} and tenant id {} ",
                    bundleAsyncInteractionId, tenantId);
            final var payloadRIHR = new RegisterInteractionHttpRequest();
            try {
                payloadRIHR.setInteractionId(bundleAsyncInteractionId);
                payloadRIHR.setInteractionKey(requestURI);
                payloadRIHR.setNature(Configuration.objectMapper.valueToTree(
                        Map.of("nature", "Validation Failed", "tenant_id", tenantId)));
                payloadRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                payloadRIHR.setPayload(Configuration.objectMapper.valueToTree(immediateResult));
                payloadRIHR.setFromState("NONE");
                payloadRIHR.setToState("VALIDATION_FAILED");
                payloadRIHR.setCreatedAt(OffsetDateTime.now());
                payloadRIHR.setCreatedBy(FhirController.class.getName());
                payloadRIHR.setProvenance(provenance);
                final var execResult = payloadRIHR.execute(jooqCfg);
                LOG.info("Validation Failed state persisted." + execResult);
            } catch (Exception e) {
                LOG.error("CALL " + payloadRIHR.getName() + " payloadRIHR error", e);
            }
            return result;
        }

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
                        LOG.error("Exception while sending FHIR payload to datalake URL {} for interaction id {}",
                                dataLakeApiBaseURL, bundleAsyncInteractionId, error);
                        final var errorRIHR = new RegisterInteractionHttpRequest();
                        try {
                            errorRIHR.setInteractionId(bundleAsyncInteractionId);
                            errorRIHR.setInteractionKey(requestURI);
                            errorRIHR.setNature(Configuration.objectMapper.valueToTree(
                                    Map.of("nature", "Forwarded HTTP Response Error", "tenant_id", tenantId)));
                            errorRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                            final var rootCauseThrowable = NestedExceptionUtils.getRootCause(error);
                            final var rootCause = rootCauseThrowable != null ? rootCauseThrowable.toString() : "null";
                            final var mostSpecificCause = NestedExceptionUtils.getMostSpecificCause(error).toString();
                            final var errorMap = new HashMap<String, Object>() {
                                {
                                    put("dataLakeApiBaseURL", dataLakeApiBaseURL);
                                    put("error", error.toString());
                                    put("message", error.getMessage());
                                    put("rootCause", rootCause);
                                    put("mostSpecificCause", mostSpecificCause);
                                    put("tenantId", tenantId);
                                }
                            };
                            if (error instanceof final WebClientResponseException webClientResponseException) {
                                String responseBody = webClientResponseException.getResponseBodyAsString();
                                errorMap.put("responseBody", responseBody);
                                String bundleId = "";
                                JsonNode rootNode = Configuration.objectMapper.readTree(responseBody);
                                JsonNode bundleIdNode = rootNode.path("bundle_id"); // Adjust this path based on actual
                                if (!bundleIdNode.isMissingNode()) {
                                    bundleId = bundleIdNode.asText();
                                }
                                LOG.error(
                                        "Exception while sending FHIR payload to datalake URL {} for interaction id {} bundle id {} response from datalake {}",
                                        dataLakeApiBaseURL, bundleAsyncInteractionId, bundleId, responseBody);
                                errorMap.put("statusCode", webClientResponseException.getStatusCode().value());
                                final var responseHeaders = webClientResponseException.getHeaders()
                                        .entrySet()
                                        .stream()
                                        .collect(Collectors.toMap(Map.Entry::getKey,
                                                entry -> String.join(",", entry.getValue())));
                                errorMap.put("headers", responseHeaders);
                                errorMap.put("statusText", webClientResponseException.getStatusText());
                            }

                            errorRIHR.setPayload(Configuration.objectMapper.valueToTree(errorMap));
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

    @PostMapping(value = {"/Bundle/$validate", "/Bundle/$validate/"}, consumes = {MediaType.APPLICATION_JSON_VALUE,
        AppConfig.Servlet.FHIR_CONTENT_TYPE_HEADER_VALUE})
    @Operation(summary = "Endpoint to validate but not store or forward a payload to SHIN-NY. If you want to validate a payload, store it and then forward it to SHIN-NY, use /Bundle not /Bundle/$validate.")
    @ResponseBody
    public Object validateBundle(final @RequestBody @Nonnull String payload,
            @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
            // "profile" is the same name that HL7 validator uses
            @RequestParam(value = "profile", required = false) String fhirProfileUrlParam,
            @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.FHIR_STRUCT_DEFN_PROFILE_URI, required = false) String fhirProfileUrlHeader,
            @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.FHIR_VALIDATION_STRATEGY, required = false) String uaValidationStrategyJson,
            @RequestParam(value = "include-request-in-outcome", required = false) boolean includeRequestInOutcome,
            HttpServletRequest request) {
        request = new CustomRequestWrapper(request, payload);
        final var fhirProfileUrl = (fhirProfileUrlParam != null) ? fhirProfileUrlParam
                : (fhirProfileUrlHeader != null) ? fhirProfileUrlHeader : appConfig.getDefaultSdohFhirProfileUrl();
        LOG.info("Getting shinny Urls from config - Before: ");
        final var structureDefintionUrls = appConfig.getStructureDefinitionsUrls();
        LOG.info(" Total structure definition URLS  in config: ",
                null != structureDefintionUrls ? structureDefintionUrls.size() : 0);
        final var valueSetUrls = appConfig.getValueSetUrls();
        LOG.info(" Total value system URLS  in config: ", null != valueSetUrls ? valueSetUrls.size() : 0);
        final var codeSystemUrls = appConfig.getCodeSystemUrls();
        LOG.info(" Total code system URLS  in config: ", null != codeSystemUrls ? codeSystemUrls.size() : 0);
        final var sessionBuilder = engine.session()
                .onDevice(Device.createDefault())
                .withPayloads(List.of(payload))
                .withFhirProfileUrl(fhirProfileUrl)
                .withFhirStructureDefinitionUrls(structureDefintionUrls)
                .withFhirValueSetUrls(valueSetUrls)
                .withFhirCodeSystemUrls(codeSystemUrls)
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

    @GetMapping(value = "/Bundle/$status/{bundleSessionId}", produces = {"application/json", "text/html"})
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
            return String.format("""
                      "error": "%s",
                      "bundleSessionId": "%s"
                    """.replace("\n", "%n"), e.toString(), bundleSessionId);
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
