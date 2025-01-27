package org.techbd.service.http.hub.prime.api;

import static org.techbd.udi.auto.jooq.ingress.Tables.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import io.micrometer.common.util.StringUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@Tag(name = "Tech by Design Hub FHIR Endpoints", description = "Tech by Design Hub FHIR Endpoints")
public class FhirController {

        private static final Logger LOG = LoggerFactory.getLogger(FhirController.class.getName());
        private final OrchestrationEngine engine;
        private final AppConfig appConfig;
        private final UdiPrimeJpaConfig udiPrimeJpaConfig;
        private final FHIRService fhirService;
        private final Tracer tracer;

        public FhirController(@SuppressWarnings("PMD.UnusedFormalParameter") final Environment environment,
                        final AppConfig appConfig,
                        final UdiPrimeJpaConfig udiPrimeJpaConfig,
                        final FHIRService fhirService,
                        final OrchestrationEngine orchestrationEngine,
                        final Tracer tracer,
                        @SuppressWarnings("PMD.UnusedFormalParameter") final SftpManager sftpManager,
                        @SuppressWarnings("PMD.UnusedFormalParameter") final SandboxHelpers sboxHelpers) {
                this.appConfig = appConfig;
                this.udiPrimeJpaConfig = udiPrimeJpaConfig;
                this.fhirService = fhirService;
                this.engine = orchestrationEngine;
                this.tracer = tracer;
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
        @Operation(summary = "Endpoint to to validate, store, and then forward a payload to SHIN-NY. If you want to validate a payload and not store it or forward it to SHIN-NY, use $validate.", description = "Endpoint to to validate, store, and then forward a payload to SHIN-NY.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Request processed successfully", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\n"
                                        + "  \"OperationOutcome\": {\n"
                                        + "    \"validationResults\": [\n"
                                        + "      {\n"
                                        + "        \"operationOutcome\": {\n"
                                        + "          \"resourceType\": \"OperationOutcome\",\n"
                                        + "          \"issue\": [\n"
                                        + "            {\n"
                                        + "              \"severity\": \"error\",\n"
                                        + "              \"diagnostics\": \"Error Message\",\n"
                                        + "              \"location\": [\n"
                                        + "                \"Bundle.entry[0].resource/*Patient/PatientExample*/.extension[0].extension[0].value.ofType(Coding)\",\n"
                                        + "                \"Line[1] Col[5190]\"\n"
                                        + "              ]\n"
                                        + "            }\n"
                                        + "          ]\n"
                                        + "        }\n"
                                        + "      }\n"
                                        + "    ],\n"
                                        + "    \"techByDesignDisposition\": [\n"
                                        + "      {\n"
                                        + "        \"action\": \"reject\",\n"
                                        + "        \"actionPayload\": {\n"
                                        + "          \"message\": \"reject message\",\n"
                                        + "          \"description\": \"rule name\"\n"
                                        + "        }\n"
                                        + "      }\n"
                                        + "    ],\n"
                                        + "    \"resourceType\": \"OperationOutcome\"\n"
                                        + "  }\n"
                                        + "}"))),

                        @ApiResponse(responseCode = "400", description = "Validation Error: Missing or invalid parameter", content = @Content(mediaType = "application/json", examples = {
                                        @ExampleObject(value = "{\n"
                                                        + "  \"status\": \"Error\",\n"
                                                        + "  \"message\": \"Validation Error: Required request body is missing.\"\n"
                                                        + "}"),
                                        @ExampleObject(value = "{\n"
                                                        + "  \"status\": \"Error\",\n"
                                                        + "  \"message\": \"Validation Error: Required request header 'X-TechBD-Tenant-ID' for method parameter type String is not present.\"\n"
                                                        + "}")
                        })),
                        @ApiResponse(responseCode = "500", description = "An unexpected system error occurred", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\n"
                                        + "  \"status\": \"Error\",\n"
                                        + "  \"message\": \"An unexpected system error occurred.\"\n"
                                        + "}")))
        })
        @ResponseBody
        @Async
        public Object validateBundleAndForward(
                        @Parameter(description = "Payload for the API. This <b>must not</b> be <code>null</code>.", required = true) final @RequestBody @Nonnull String payload,
                        @Parameter(description = "Parameter to specify the Tenant ID. This is a <b>mandatory</b> parameter.", required = true) @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
                        // "profile" is the same name that HL7 validator uses
                        @Parameter(description = "Profile URL for the API.", required = false) @RequestParam(value = "profile", required = false) String fhirProfileUrlParam,
                        @Parameter(description = "Optional header to specify the Structure definition profile URL. If not specified, the default settings mentioned in the application configuration will be used.", required = false) @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.FHIR_STRUCT_DEFN_PROFILE_URI, required = false) String fhirProfileUrlHeader,
                        @Parameter(description = """
                                        Optional header to specify the validation strategy. If not specified, the default settings mentioned in the application configuration will be used.
                                        Example for validation strategy JSON:
                                        <code>
                                        {
                                          "engines": [
                                            "HAPI",
                                            "HL7-Official-API",
                                            "HL7-Official-Embedded"
                                          ]
                                        }
                                        </code> """, required = false) @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.FHIR_VALIDATION_STRATEGY, required = false) String uaValidationStrategyJson,
                        @Parameter(description = "Optional header to specify the Datalake API URL. If not specified, the default URL mentioned in the application configuration will be used.", required = false) @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.DATALAKE_API_URL, required = false) String customDataLakeApi,
                        @Parameter(description = "Optional header to specify the request URI to override. This parameter is used for requests forwarded from Mirth Connect, where we override it with the initial request URI from Mirth Connect.", required = false) @RequestHeader(value = "X-TechBD-Override-Request-URI", required = false) String requestUriToBeOverridden,
                        @Parameter(description = "An optional header to provide a UUID that if provided will be used as interaction id.", required = false) @RequestHeader(value = "X-Correlation-ID", required = false) String coRrelationId,
                        @Parameter(description = """
                                        Optional header to specify the Datalake API content type.
                                        Value provided with this header will be used to set the <code>Content-Type</code> header while invoking the Datalake API.
                                        If the header is not provided, <code>application/json</code> will be used.
                                        """, required = false) @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.DATALAKE_API_CONTENT_TYPE, required = false) String dataLakeApiContentType,
                        @Parameter(description = "Header to decide whether the request is just for health check. If <code>true</code>, no information will be recorded in the database. It will be <code>false</code> in by default.", required = false) @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.HEALTH_CHECK_HEADER, required = false) String healthCheck,
                        @Parameter(description = "Optional parameter to decide whether the Datalake submission to be synchronous or asynchronous.", required = false) @RequestParam(value = "immediate", required = false) boolean isSync,
                        @Parameter(description = "Optional parameter to decide whether the request is to be included in the outcome.", required = false) @RequestParam(value = "include-request-in-outcome", required = false) boolean includeRequestInOutcome,
                        @Parameter(hidden = true, description = "Optional parameter to decide whether the incoming payload is to be saved in the database.", required = false) @RequestParam(value = "include-incoming-payload-in-db", required = false) boolean includeIncomingPayloadInDB,
                        @Parameter(description = "An optional parameter determines whether validation results are sent to the scoring engine. If set to <code>false</code>, only the bundle is sent; if <code>true</code>, the operation outcome is also sent.", required = false) @RequestParam(value = "include-operation-outcome", required = false, defaultValue = "true") boolean includeOperationOutcome,
                        @Parameter(description = """
                                        An optional parameter specifies whether the scoring engine API should be called with or without mTLS.<br>
                                        The allowed values for <code>mTlsStrategy</code> are:
                                        <ul>
                                            <li><code>no-mTls</code>: No mTLS is used. The WebClient sends a standard HTTP POST request to the scoring engine API without mutual TLS (mTLS).</li>
                                            <li><code>mTlsResources</code>: mTLS is enabled. The WebClient reads the TLS key and certificate from a local folder, and then sends an HTTPS POST request to the scoring engine API with mutual TLS authentication.</li>
                                            <li><code>aws-secrets</code>: mTLS is enabled. The WebClient retrieves the TLS key and certificate from AWS Secrets Manager, and then sends an HTTPS POST request to the scoring engine API with mutual TLS authentication.</li>
                                            <li><code>post-stdin-payload-to-nyec-datalake-external</code>: This option runs a bash script via ProcessBuilder. The payload is passed through standard input (STDIN) to the script, which uses <code>curl</code> to send the request to the scoring engine API. In the <b>PHI-QA</b> environment, mTLS is enabled for this request. In other environments, mTLS is disabled for this script.</li>
                                        </ul>
                                        """, required = false) @RequestParam(value = "mtls-strategy", required = false) String mtlsStrategy,
                        @Parameter(description = "Optional parameter to decide whether the session cookie (JSESSIONID) should be deleted.", required = false) @RequestParam(value = "delete-session-cookie", required = false) Boolean deleteSessionCookie,
                        @Parameter(description = "Optional parameter to specify source of the request.", required = false) @RequestParam(value = "source", required = false, defaultValue = "FHIR") String source,
                        HttpServletRequest request, HttpServletResponse response) throws SQLException, IOException {
                Span span = tracer.spanBuilder("FhirController.validateBundleAndForward").startSpan();
                try {
                        if (tenantId == null || tenantId.trim().isEmpty()) {
                                LOG.error("FHIRController:Bundle Validate:: Tenant ID is missing or empty");
                                throw new IllegalArgumentException("Tenant ID must be provided");
                        }

                        if (Boolean.TRUE.equals(deleteSessionCookie)) {
                                deleteJSessionCookie(request, response);
                        }
                        if (StringUtils.isNotEmpty(coRrelationId)) {
                                try {
                                        UUID.fromString(coRrelationId);
                                } catch (IllegalArgumentException e) {
                                        throw new IllegalArgumentException("X-Correlation-ID should be a valid UUID");
                                }
                        }
                        final var provenance = "%s.validateBundleAndForward(%s)".formatted(
                                        FhirController.class.getName(),
                                        isSync ? "sync" : "async");
                        request = new CustomRequestWrapper(request, payload);
                        return fhirService.processBundle(payload, tenantId, fhirProfileUrlParam, fhirProfileUrlHeader,
                                        uaValidationStrategyJson,
                                        customDataLakeApi, dataLakeApiContentType, healthCheck, isSync,
                                        includeRequestInOutcome,
                                        includeIncomingPayloadInDB,
                                        request, response, provenance, includeOperationOutcome, mtlsStrategy, null,
                                        null, null, source, requestUriToBeOverridden, coRrelationId);
                } finally {
                        span.end();
                }
        }

        @PostMapping(value = { "/Bundle/$validate", "/Bundle/$validate/" }, consumes = {
                        MediaType.APPLICATION_JSON_VALUE,
                        AppConfig.Servlet.FHIR_CONTENT_TYPE_HEADER_VALUE })
        @Operation(summary = "Endpoint to validate but not store or forward a payload to SHIN-NY. If you want to validate a payload, store it and then forward it to SHIN-NY, use /Bundle not /Bundle/$validate.", description = "Endpoint to validate but not store or forward a payload to SHIN-NY.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Request processed successfully", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\n"
                                        + "  \"OperationOutcome\": {\n"
                                        + "    \"validationResults\": [\n"
                                        + "      {\n"
                                        + "        \"operationOutcome\": {\n"
                                        + "          \"resourceType\": \"OperationOutcome\",\n"
                                        + "          \"issue\": [\n"
                                        + "            {\n"
                                        + "              \"severity\": \"error\",\n"
                                        + "              \"diagnostics\": \"Error Message\",\n"
                                        + "              \"location\": [\n"
                                        + "                \"Bundle.entry[0].resource/*Patient/PatientExample*/.extension[0].extension[0].value.ofType(Coding)\",\n"
                                        + "                \"Line[1] Col[5190]\"\n"
                                        + "              ]\n"
                                        + "            }\n"
                                        + "          ]\n"
                                        + "        }\n"
                                        + "      }\n"
                                        + "    ]\n"
                                        + "  }\n"
                                        + "}"))),
                        @ApiResponse(responseCode = "400", description = "Validation Error: Missing or invalid parameter", content = @Content(mediaType = "application/json", examples = {
                                        @ExampleObject(value = "{\n"
                                                        + "  \"status\": \"Error\",\n"
                                                        + "  \"message\": \"Validation Error: Required request body is missing.\"\n"
                                                        + "}"),
                                        @ExampleObject(value = "{\n"
                                                        + "  \"status\": \"Error\",\n"
                                                        + "  \"message\": \"Validation Error: Required request header 'X-TechBD-Tenant-ID' for method parameter type String is not present.\"\n"
                                                        + "}")
                        })),
                        @ApiResponse(responseCode = "500", description = "An unexpected system error occurred", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\n"
                                        + "  \"status\": \"Error\",\n"
                                        + "  \"message\": \"An unexpected system error occurred.\"\n"
                                        + "}")))
        })
        @ResponseBody
        public Object validateBundle(
                        @Parameter(description = "Payload for the API. This <b>must not</b> be <code>null</code>.", required = true) final @RequestBody @Nonnull String payload,
                        @Parameter(description = "Parameter to specify the Tenant ID. This is a <b>mandatory</b> parameter.", required = true) @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
                        // "profile" is the same name that HL7 validator uses
                        @Parameter(description = "Parameter to specify the profile. This is an optional parameter. If not specified, the default settings mentioned in the application configuration will be used.", required = false) @RequestParam(value = "profile", required = false) String fhirProfileUrlParam,
                        @Parameter(description = "Optional header to specify the Structure definition profile URL. If not specified, the default settings mentioned in the application configuration will be used.", required = false) @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.FHIR_STRUCT_DEFN_PROFILE_URI, required = false) String fhirProfileUrlHeader,
                        @Parameter(description = "Optional header to specify the validation strategy. If not specified, the default settings mentioned in the application configuration will be used.", required = false) @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.FHIR_VALIDATION_STRATEGY, required = false) String uaValidationStrategyJson,
                        @Parameter(description = "Parameter to decide whether the request is to be included in the outcome.", required = false) @RequestParam(value = "include-request-in-outcome", required = false) boolean includeRequestInOutcome,
                        @Parameter(description = "Optional parameter to decide whether the session cookie (JSESSIONID) should be deleted.", required = false) @RequestParam(value = "delete-session-cookie", required = false) Boolean deleteSessionCookie,
                        HttpServletRequest request, HttpServletResponse response) {
                Span span = tracer.spanBuilder("FhirController.validateBundle").startSpan();
                try {

                        if (tenantId == null || tenantId.trim().isEmpty()) {
                                LOG.error("FHIRController:Bundle Validate:: Tenant ID is missing or empty");
                                throw new IllegalArgumentException("Tenant ID must be provided");
                        }

                        if (Boolean.TRUE.equals(deleteSessionCookie)) {
                                deleteJSessionCookie(request, response);
                        }

                        request = new CustomRequestWrapper(request, payload);

                        LOG.info("FHIRController:Bundle Validate::  -BEGIN");
                        final var fhirProfileUrl = (fhirProfileUrlParam != null) ? fhirProfileUrlParam
                                        : (fhirProfileUrlHeader != null) ? fhirProfileUrlHeader
                                                        : appConfig.getDefaultSdohFhirProfileUrl();
                        LOG.info("FHIRController:Bundle Validate :: Getting shinny Urls from config - Before: ");
                        final var igPackages = appConfig.getIgPackages();
                        final var igVersion = appConfig.getIgVersion();
                        final var sessionBuilder = engine.session()
                                        .withSessionId(UUID.randomUUID().toString())
                                        .onDevice(Device.createDefault())
                                        .withInteractionId(InteractionsFilter.getActiveRequestEnc(request).requestId()
                                                        .toString())
                                        .withPayloads(List.of(payload))
                                        .withTracer(tracer)
                                        .withFhirProfileUrl(fhirProfileUrl)
                                        .withFhirIGPackages(igPackages)
                                        .withIgVersion(igVersion)
                                        .addHapiValidationEngine() // by default
                                        // clearExisting is set to true so engines can be fully supplied through header
                                        .withUserAgentValidationStrategy(uaValidationStrategyJson, true);
                        final var session = sessionBuilder.build();
                        try {
                                engine.orchestrate(session);
                                final var opOutcome = new HashMap<>(Map.of("resourceType", "OperationOutcome",
                                                "help",
                                                "If you need help understanding how to decipher OperationOutcome please see "
                                                                + appConfig.getOperationOutcomeHelpUrl(),
                                                "validationResults",
                                                session.getValidationResults(), "device",
                                                session.getDevice()));
                                final var result = Map.of("OperationOutcome", opOutcome);
                                if (uaValidationStrategyJson != null) {
                                        opOutcome.put("uaValidationStrategy",
                                                        Map.of(AppConfig.Servlet.HeaderName.Request.FHIR_VALIDATION_STRATEGY,
                                                                        uaValidationStrategyJson,
                                                                        "issues",
                                                                        sessionBuilder.getUaStrategyJsonIssues()));
                                }
                                if (includeRequestInOutcome) {
                                        opOutcome.put("request", InteractionsFilter.getActiveRequestEnc(request));
                                }
                                LOG.info("FHIRController: Bundle Validate:: Validation completed successfully");
                                return result;
                        } catch (Exception e) {
                                LOG.error("FHIRController: Bundle Validate:: Validation failed", e);
                                return Map.of(
                                                "resourceType", "OperationOutcome",
                                                "error", "Validation failed: " + e.getMessage());
                        } finally {
                                // Ensure the session is cleared to avoid memory leaks
                                if (session != null) {
                                        engine.clear(session);
                                        LOG.info("FHIRController:Bundle Validate::  -END");
                                }
                        }
                } finally {
                        span.end();
                }
        }

        @GetMapping(value = "/Bundle/$status/{bundleSessionId}", produces = { "application/json", "text/html" })
        @ResponseBody
        @Operation(summary = "Check the state/status of async operation")
        public Object bundleStatus(
                        @Parameter(description = "<b>mandatory</b> path variable to specify the bundle session ID.", required = true) @PathVariable String bundleSessionId,
                        final Model model, HttpServletRequest request) {
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
                        @Parameter(description = """
                                        Mandatory path variable.
                                        Possible values are:
                                        <ul>
                                            <li><code>fhir-result-healthelink-20240327-testcase2-MRN-healthelinkV7W7BQTTJS</code></li>
                                            <li><code>fhir-result-healthelink-20240327-testcase2-MRN-healthelinkZNTS7DGCIK</code></li>
                                            <li><code>fhir-result-healthelink-20240523-testcase1-MRN-healthelinkCZ8BSG71LI</code></li>
                                            <li><code>fhir-result-healthelink-20240523-testcase1-MRN-healthelinkS8KXK30S8W</code></li>
                                            <li><code>fhir-result-rochester-20240405-testcase1-MRN-rochesterBICFYAK7QF</code></li>
                                            <li><code>fhir-result-rochester-20240405-testcase1-MRN-rochesterOKNSL0ZX7C</code></li>
                                        </ul>
                                        The API will wait for the number of milliseconds specified in the <code>simulateLifetimeMs</code> parameter
                                        and then return the content of the JSON file specified in the <code>resourcePath</code> parameter.
                                        """, required = true) @PathVariable String resourcePath,
                        @Parameter(description = "Parameter to specify lifetime simulation in milli seconds. The default value is 0, meaning no waiting", required = false) @RequestParam(required = false, defaultValue = "0") long simulateLifetimeMs) {
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

        private void deleteJSessionCookie(HttpServletRequest request, HttpServletResponse response) {
                // Delete the JSESSIONID cookie
                Cookie cookie = new Cookie("JSESSIONID", null); // Set the cookie name
                cookie.setMaxAge(0); // Make it expire immediately
                cookie.setPath("/"); // Set the same path as the original cookie
                response.addCookie(cookie); // Add it to the response to delete
        }
}
