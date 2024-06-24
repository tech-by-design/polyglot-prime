package org.techbd.service.http.hub.prime.api;

import static org.techbd.udi.auto.jooq.ingress.Tables.SAT_INTERACTION_HTTP_REQUEST;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.function.BodyInserters;
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
import org.techbd.udi.auto.jooq.ingress.routines.UdiInsertSessionWithState;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;

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

    @PostMapping(value = { "/Bundle", "/Bundle/" }, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Async
    public Object validateBundleAndForward(final @RequestBody @Nonnull String payload,
            @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
            // "profile" is the same name that HL7 validator uses
            @RequestParam(value = "profile", required = false) String fhirProfileUrlParam,
            @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.FHIR_STRUCT_DEFN_PROFILE_URI, required = false) String fhirProfileUrlHeader,
            @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.FHIR_VALIDATION_STRATEGY, required = false) String uaValidationStrategyJson,
            @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.DATALAKE_API_URL, required = false) String customDataLakeApi,
            @RequestParam(value = "immediate", required = false) boolean isSync,
            @RequestParam(value = "include-request-in-outcome", required = false) boolean includeRequestInOutcome,
            final HttpServletRequest request) throws SQLException {

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

        // TODO: since the interaction filter is already storing full interaction,
        // should we not store the payload again??

        // immediateResult is what's returned to the user while async operation
        // continues
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

        String dataLakeApiBaseURL = null;
        if (customDataLakeApi != null && !customDataLakeApi.isEmpty()) {
            dataLakeApiBaseURL = customDataLakeApi;
        } else {
            dataLakeApiBaseURL = appConfig.getDefaultDatalakeApiUrl();
        }
        final var dataLakeApiBaseUrlFinal = dataLakeApiBaseURL;
        LOG.debug("%s %s dataLakeApiBaseURL".formatted(provenance, tenantId, dataLakeApiBaseURL));

        final var webClient = WebClient.builder().baseUrl(dataLakeApiBaseURL).build();
        final var jooqDSL = udiPrimeJpaConfig.dsl();
        final var jooqCfg = jooqDSL.configuration();

        try {
            final var init = new UdiInsertSessionWithState();
            final var initState = "%s.IN_PROCESS".formatted(provenance);
            init.setSessionId(bundleAsyncInteractionId);
            init.setFromState("%s.INIT".formatted(provenance));
            init.setToState(initState);
            init.setContentType(request.getHeader("Content-Type"));
            init.setContent(Configuration.objectMapper.writeValueAsString(immediateResult));
            init.setCreatedBy(tenantId);
            init.setProvenance(provenance);
            init.setNamespace(provenance);

            final var initExecResult = init.execute(jooqCfg);
            final var initResults = init.getResults();

            LOG.info("initExecResult" + initExecResult);
            LOG.info("initResults" + initResults);

            webClient.post()
                    .uri("?processingAgent=" + tenantId)
                    .body(BodyInserters.fromValue(payload))
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(response -> {
                        LOG.info("%s webClient.subscribe(response)".formatted(provenance));
                        final var success = new UdiInsertSessionWithState();
                        success.setSessionId(bundleAsyncInteractionId);
                        success.setFromState(initState);
                        success.setToState("%s.SUCCESS".formatted(provenance));
                        success.setContentType("TODO"); // add content type from response
                        success.setContent(response);
                        success.setCreatedBy(tenantId);
                        success.setProvenance(provenance);
                        success.setNamespace(provenance);

                        final var successExecResult = success.execute(jooqCfg);
                        final var successResults = success.getResults();

                        try {
                            final var responseJson = Configuration.objectMapper.createObjectNode();
                            responseJson.set("response", Configuration.objectMapper.readTree(response));

                            final var dsl = udiPrimeJpaConfig.dsl();
                            dsl.update(SAT_INTERACTION_HTTP_REQUEST)
                                    .set(SAT_INTERACTION_HTTP_REQUEST.ELABORATION, responseJson)
                                    .where(SAT_INTERACTION_HTTP_REQUEST.HUB_INTERACTION_ID
                                            .eq(bundleAsyncInteractionId))
                                    .execute();

                            LOG.info("SAT_INTERACTION_HTTP_REQUEST.HUB_INTERACTION_ID updated for %s"
                                    .formatted(bundleAsyncInteractionId));
                        } catch (Exception e) {
                            LOG.error(
                                    "error updating SAT_INTERACTION_HTTP_REQUEST.ELABORATION in SAT_INTERACTION_HTTP_REQUEST.HUB_INTERACTION_ID id "
                                            + bundleAsyncInteractionId,
                                    e);
                        }

                        LOG.info("successExecResult" + successExecResult);
                        LOG.info("successResults" + successResults);
                    }, (Throwable error) -> { // Explicitly specify the type Throwable
                        LOG.info("%s webClient.Throwable(error)".formatted(provenance));
                        String content;
                        try {
                            // TODO: add more details from the WebClient
                            content = Configuration.objectMapper.writeValueAsString(
                                    Map.of("dataLakeApiBaseURL", dataLakeApiBaseUrlFinal, "error", error.toString(),
                                            "tenantId", tenantId));
                        } catch (JsonProcessingException e) {
                            content = "dataLakeApiBaseURL: %s, error %s while %s".formatted(dataLakeApiBaseUrlFinal,
                                    error.toString(), e.toString());
                        }
                        final var failure = new UdiInsertSessionWithState();
                        failure.setSessionId(bundleAsyncInteractionId.toString());
                        failure.setFromState(initState);
                        failure.setToState("%s.FAILURE".formatted(provenance));
                        failure.setContentType("application/json");
                        failure.setContent(content);
                        failure.setCreatedBy(tenantId);
                        failure.setProvenance(provenance);
                        failure.setNamespace(provenance);

                        final var failureExecResult = failure.execute(jooqCfg);
                        final var failureResults = failure.getResults();

                        LOG.info("failureExecResult" + failureExecResult);
                        LOG.info("failureResults" + failureResults);

                    });
        } catch (Exception e) {
            LOG.error(provenance, e);
            immediateResult.put("exception", e.toString());
            e.printStackTrace();
        }

        return result;
    }

    @PostMapping(value = { "/Bundle/$validate", "/Bundle/$validate/" }, consumes = MediaType.APPLICATION_JSON_VALUE)
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

        // TODO: need to give actual response from upstream server (meaning from SHIN-NY
        // Data Lake) so far, this method gives meta data but not the actual response

        try {
            final var result = jooqDSL.select()
                    .from(SAT_INTERACTION_HTTP_REQUEST)
                    .where(SAT_INTERACTION_HTTP_REQUEST.HUB_INTERACTION_ID.eq(bundleSessionId))
                    .fetchSingle();
            LOG.info("Query execution result: {}", result);
            return Configuration.objectMapper.writeValueAsString(result.intoMap());
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