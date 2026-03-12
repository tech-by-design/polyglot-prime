package org.techbd.service.http.hub.prime.api;

import static org.techbd.udi.auto.jooq.ingress.Tables.INTERACTION_HTTP_REQUEST;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.techbd.config.Constants;
import org.techbd.config.CoreAppConfig;
import org.techbd.service.dataledger.CoreDataLedgerApiClient;
import org.techbd.service.fhir.FHIRService;
import org.techbd.service.fhir.FhirReplayService;
import org.techbd.service.fhir.engine.OrchestrationEngine;
import org.techbd.service.http.Helpers;
import org.techbd.service.http.hub.CustomRequestWrapper;
import org.techbd.util.FHIRUtil;
import org.techbd.util.fhir.CoreFHIRUtil;

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
        private final CoreAppConfig appConfig;
        private final CoreDataLedgerApiClient dataLedgerApiClient;
        private final DSLContext primaryDslContext;
        private final FHIRService fhirService;
        private final FhirReplayService fhirReplayService;
        private final Tracer tracer;

        public FhirController(final Tracer tracer,final OrchestrationEngine engine,
        final CoreAppConfig appConfig ,final CoreDataLedgerApiClient dataLedgerApiClient,
        final FHIRService fhirService, final FhirReplayService fhirReplayService
        ,@Qualifier("primaryDslContext") final DSLContext primaryDslContext) throws IOException {
                // String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
                // appConfig = ConfigLoader.loadConfig(activeProfile);
                // this.fhirService = new FHIRService();
                // fhirService.setAppConfig(appConfig);
                // org.techbd.util.fhir.FHIRUtil.initialize(appConfig);
                // dataLedgerApiClient = new DataLedgerApiClient(appConfig);
                // OrchestrationEngine engine = new OrchestrationEngine(appConfig);
                // fhirService.setDataLedgerApiClient(dataLedgerApiClient);
                // fhirService.setEngine(engine);
                this.appConfig = appConfig;
                this.engine = engine;
                this.fhirService = fhirService;
                this.dataLedgerApiClient = dataLedgerApiClient;
                this.tracer = tracer;
                this.primaryDslContext = primaryDslContext;
                this.fhirReplayService = fhirReplayService;
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
                        Constants.FHIR_CONTENT_TYPE_HEADER_VALUE })
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
        public Object validateBundleAndForward(
                        @Parameter(description = "Payload for the API. This <b>must not</b> be <code>null</code>.", required = true) final @RequestBody @Nonnull String payload,
                        @Parameter(description = "Parameter to specify the Tenant ID. This is a <b>mandatory</b> parameter.", required = true) @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
                        // "profile" is the same name that HL7 validator uses
                        @Parameter(description = "Optional header to specify the Datalake API URL. If not specified, the default URL mentioned in the application configuration will be used.", required = false) @RequestHeader(value = Constants.DATALAKE_API_URL, required = false) String customDataLakeApi,
                        @Parameter(description = "Optional header to provide elaboration details.", required = false) @RequestHeader(value = "X-TechBD-Elaboration", required = false) String elaboration,
                        @Parameter(description = "Optional header to specify the request URI to override. This parameter is used for requests forwarded from Mirth Connect, where we override it with the initial request URI from Mirth Connect.", required = false) @RequestHeader(value = "X-TechBD-Override-Request-URI", required = false) String requestUriToBeOverridden,
                        @Parameter(description = "An optional header to provide a UUID that if provided will be used as interaction id.", required = false) @RequestHeader(value = "X-Correlation-ID", required = false) String coRrelationId,
                        @Parameter(description = """
                                        Optional header to specify the Datalake API content type.
                                        Value provided with this header will be used to set the <code>Content-Type</code> header while invoking the Datalake API.
                                        If the header is not provided, <code>application/json</code> will be used.
                                        """, required = false) @RequestHeader(value = Constants.DATALAKE_API_CONTENT_TYPE, required = false) String dataLakeApiContentType,
                        @Parameter(description = "Header to decide whether the request is just for health check. If <code>true</code>, no information will be recorded in the database. It will be <code>false</code> in by default.", required = false) @RequestHeader(value = Constants.HEALTH_CHECK_HEADER, required = false) String healthCheck,
                        @Parameter(hidden = true, description = "Optional parameter to decide whether response should be synchronous or asynchronous.", required = false) @RequestParam(value = "immediate", required = false,defaultValue = "true") boolean isSync,

                        @Parameter(hidden = true, description = """
                                        An optional parameter specifies whether the scoring engine API should be called with or without mTLS.<br>
                                        The allowed values for <code>mTlsStrategy</code> are:
                                        <ul>
                                            <li><code>no-mTls</code>: No mTLS is used. The WebClient sends a standard HTTP POST request to the scoring engine API without mutual TLS (mTLS).</li>
                                            <li><code>mTlsResources</code>: mTLS is enabled. The WebClient reads the TLS key and certificate from a local folder, and then sends an HTTPS POST request to the scoring engine API with mutual TLS authentication.</li>
                                            <li><code>aws-secrets</code>: mTLS is enabled. The WebClient retrieves the TLS key and certificate from AWS Secrets Manager, and then sends an HTTPS POST request to the scoring engine API with mutual TLS authentication.</li>
                                            <li><code>post-stdin-payload-to-nyec-datalake-external</code>: This option runs a bash script via ProcessBuilder. The payload is passed through standard input (STDIN) to the script, which uses <code>curl</code> to send the request to the scoring engine API. In the <b>PHI-QA</b> environment, mTLS is enabled for this request. In other environments, mTLS is disabled for this script.</li>
                                        </ul>
                                        """, required = false) @RequestParam(value = "mtls-strategy", required = false) String mtlsStrategy,
                        @Parameter(hidden = true, description = "Optional parameter to decide whether the session cookie (JSESSIONID) should be deleted.", required = false) @RequestParam(value = "delete-session-cookie", required = false) Boolean deleteSessionCookie,
                        @Parameter(hidden = true, description = "Optional parameter to specify source of the request.", required = false) @RequestParam(value = "source", required = false, defaultValue = "FHIR") String source,
                        @Parameter(description = "Optional header to set validation severity level (`information`, `warning`, `error`, `fatal`).", required = false) @RequestHeader(value = "X-TechBD-Validation-Severity-Level", required = false) String validationSeverityLevel,
                        @Parameter(description = "Optional header to specify IG version.", required = false) @RequestHeader(value = "X-SHIN-NY-IG-Version", required = false) String requestedIgVersion ,                    
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
                        Map<String, Object> headers = CoreFHIRUtil.buildHeaderParametersMap(tenantId, customDataLakeApi,
                                        dataLakeApiContentType,
                                        requestUriToBeOverridden, validationSeverityLevel, healthCheck, coRrelationId,
                                        provenance,requestedIgVersion);
                        Map <String,Object> requestDetailsMap = FHIRUtil.extractRequestDetails(request);
                        CoreFHIRUtil.buildRequestParametersMap(requestDetailsMap,deleteSessionCookie,
                                        mtlsStrategy, source, null, null,request.getRequestURI());
                        requestDetailsMap.put(Constants.INTERACTION_ID,UUID.randomUUID().toString()); 
                        requestDetailsMap.put(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME, Instant.now().toString()); 
                        requestDetailsMap.put(Constants.ELABORATION, elaboration);
                        requestDetailsMap.putAll(headers);  
                        request = new CustomRequestWrapper(request, payload);
                        Map<String, Object> responseParameters = new HashMap<>();
                        final var result = fhirService.processBundle(payload, requestDetailsMap,responseParameters);
                        CoreFHIRUtil.addCookieAndHeadersToResponse(response, responseParameters, requestDetailsMap);
                        return result;
                } finally {
                        span.end();
                }
        }

        @PostMapping(value = { "/Bundle/$validate", "/Bundle/$validate/" }, consumes = {
                        MediaType.APPLICATION_JSON_VALUE,
                        Constants.FHIR_CONTENT_TYPE_HEADER_VALUE })
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
                        @Parameter(hidden = true, description = "Optional parameter to decide whether the session cookie (JSESSIONID) should be deleted.", required = false) @RequestParam(value = "delete-session-cookie", required = false) Boolean deleteSessionCookie,
                        @Parameter(description = "Optional header to specify IG version.", required = false) @RequestHeader(value = "X-SHIN-NY-IG-Version", required = false) String requestedIgVersion,
                        HttpServletRequest request, HttpServletResponse response) throws IOException {
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
                        Map<String, Object> headers = CoreFHIRUtil.buildHeaderParametersMap(tenantId, null, null,
                                        null, null, null, null, null,requestedIgVersion );
                        Map <String,Object> requestDetailsMap = FHIRUtil.extractRequestDetails(request);            
                        CoreFHIRUtil.buildRequestParametersMap(requestDetailsMap,deleteSessionCookie,
                                        null, null,
                                        null, null, request.getRequestURI());
                        requestDetailsMap.put(Constants.INTERACTION_ID,UUID.randomUUID().toString());
                        requestDetailsMap.put(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME, Instant.now().toString());
                        requestDetailsMap.putAll(headers);
                        Map<String, Object> responseParameters = new HashMap<>();
                        final var result = fhirService.processBundle(payload, requestDetailsMap,  responseParameters);
                        CoreFHIRUtil.addCookieAndHeadersToResponse(response, responseParameters, requestDetailsMap);
                        return result;
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
                try {
                        final var result = primaryDslContext.select()
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

        @PostMapping(value = { "/Bundle/replay", "/Bundle/replay/" })
        @Operation(summary = "Replay FHIR Bundles between a date or datetime range", description = """
                        Accepts startDate and endDate
                        """)
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Replay triggered successfully."),
                        @ApiResponse(responseCode = "400", description = "Invalid or missing parameters."),
                        @ApiResponse(responseCode = "500", description = "Internal error occurred.")
        })
        @ResponseBody
        public Object replayBundles(
                        @RequestHeader("X-TechBD-StartDate") String startDateStr,
                        @RequestHeader("X-TechBD-EndDate") String endDateStr,
                        @RequestHeader(value = "X-TechBD-Tenant-ID", required = false) String tenantId, HttpServletRequest request) {

                UUID interactionId = UUID.randomUUID();
                 try {
                         if (startDateStr.equals(endDateStr)) {
                                 throw new IllegalArgumentException(
                                                 "startDate cannot be same as endDate for interactionId: "
                                                                 + interactionId);
                         }
                        OffsetDateTime startDate = parseFlexibleDate(startDateStr, true);
                        OffsetDateTime endDate = parseFlexibleDate(endDateStr, false);

                        if (endDate.isBefore(startDate)) {
                                throw new IllegalArgumentException(
                                                "endDate cannot be before startDate for interactionId: "
                                                                + interactionId);
                        }

                        LOG.info("Replaying Bundles from {} to {} for interactionId {}", startDate, endDate,
                                        interactionId);

                        return fhirReplayService.replayBundles(request,interactionId.toString(), startDate, endDate,tenantId);

                } catch (DateTimeParseException e) {
                        LOG.error("Invalid date-time format for startDate='{}' or endDate='{}' for interactionId {}",
                                        startDateStr, endDateStr, interactionId, e);
                        return Map.of(
                                        "status", "Error",
                                        "message",
                                        "Invalid date-time format. Expected one of: yyyy-MM-dd or yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                } 
        }

        @GetMapping(value = { "/Bundles/status/nyec-submission-failed", "/Bundles/status/nyec-submission-failed/" })
        @Operation(summary = "Retrieve FHIR Bundles that failed NYEC submission", description = """
                        Fetches bundles that failed NYEC submission within the specified date/datetime range.
                        Optionally filter by tenant ID.
                        """)
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Successfully retrieved failed bundles."),
                        @ApiResponse(responseCode = "400", description = "Invalid or missing parameters."),
                        @ApiResponse(responseCode = "500", description = "Internal error occurred.")
        })
        @ResponseBody
        public Object getFailedNyecSubmissions(
                        @RequestHeader("X-TechBD-StartDate") String startDateStr,
                        @RequestHeader("X-TechBD-EndDate") String endDateStr,
                        @RequestHeader(value = "X-TechBD-Tenant-ID", required = false) String tenantId,
                        @RequestHeader(value = "X-TechBD-IncludeDetails", required = false) boolean includeDetails,
                        HttpServletRequest request) {

                UUID requestId = UUID.randomUUID();

                try {
                        if (startDateStr.equals(endDateStr)) {
                                throw new IllegalArgumentException(
                                                "startDate cannot be same as endDate for requestId: " + requestId);
                        }
                        OffsetDateTime startDate = parseFlexibleDate(startDateStr, true);
                        OffsetDateTime endDate = parseFlexibleDate(endDateStr, false);

                        // Validate date range
                        if (endDate.isBefore(startDate)) {
                                throw new IllegalArgumentException(
                                                "endDate cannot be before startDate for requestId: " + requestId);
                        }

                        LOG.info("Fetching failed NYEC submissions from {} to {} for requestId {} | tenantId={}",
                                        startDate, endDate, requestId, tenantId != null ? tenantId : "ALL");
                        return fhirReplayService.getFailedNyecSubmissionBundles(
                                        startDate,
                                        endDate,tenantId,includeDetails);

                } catch (DateTimeParseException e) {
                        LOG.error("Invalid date-time format for startDate='{}' or endDate='{}' for requestId {}",
                                        startDateStr, endDateStr, requestId, e);
                        return Map.of(
                                        "status", "Error",
                                        "message",
                                        "Invalid date-time format. Expected one of: yyyy-MM-dd or yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                                        "requestId", requestId.toString());
                } catch (IllegalArgumentException e) {
                        LOG.error("Validation error for requestId {}: {}", requestId, e.getMessage());
                        return Map.of(
                                        "status", "Error",
                                        "message", e.getMessage(),
                                        "requestId", requestId.toString());
                } catch (Exception e) {
                        LOG.error("Unexpected error fetching failed NYEC submissions for requestId {}", requestId, e);
                        return Map.of(
                                        "status", "Error",
                                        "message", "An unexpected error occurred while fetching failed submissions",
                                        "requestId", requestId.toString());
                }
        }

        private OffsetDateTime parseFlexibleDate(String input, boolean isStart) {
                if (input == null || input.isBlank()) {
                        throw new IllegalArgumentException("Date value cannot be null or empty");
                }

                List<DateTimeFormatter> formatters = List.of(
                                DateTimeFormatter.ISO_OFFSET_DATE_TIME, // 2025-09-09T16:16:42.248+05:30
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS Z"), // 2025-09-09 16:16:42.248
                                                                                          // +0530
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"), // 2025-09-09 16:16:42
                                DateTimeFormatter.ofPattern("yyyy-MM-dd") // 2025-09-09
                );

                for (DateTimeFormatter fmt : formatters) {
                        try {
                                TemporalAccessor ta = fmt.parse(input);

                                if (ta.isSupported(ChronoField.OFFSET_SECONDS)) {
                                        // Input has timezone info
                                        return OffsetDateTime.from(ta).withOffsetSameInstant(ZoneOffset.UTC);
                                } else if (ta.isSupported(ChronoField.HOUR_OF_DAY)) {
                                        // Date + time but no zone: assume UTC
                                        return OffsetDateTime.parse(input + "Z",
                                                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]X"));
                                } else {
                                        // Date only: start or end of day in UTC
                                        return isStart
                                                        ? OffsetDateTime.parse(input + "T00:00:00Z")
                                                        : OffsetDateTime.parse(input + "T23:59:59.999999999Z");
                                }
                        } catch (DateTimeParseException ignored) {
                                // try next
                        }
                }
                throw new DateTimeParseException("Unrecognized date format", input, 0);
        }

        private void deleteJSessionCookie(HttpServletRequest request, HttpServletResponse response) {
                // Delete the JSESSIONID cookie
                Cookie cookie = new Cookie("JSESSIONID", null); // Set the cookie name
                cookie.setMaxAge(0); // Make it expire immediately
                cookie.setPath("/"); // Set the same path as the original cookie
                response.addCookie(cookie); // Add it to the response to delete
        }
     
        @GetMapping(value = { "/Bundles/status/operation-outcome", "/Bundles/status/operation-outcome/" })
        @Operation(summary = "Retrieve OperationOutcome(s) for a Bundle or Interaction", description = """
                        Fetches OperationOutcome resources for a given Bundle ID or Interaction ID.
                        Exactly ONE of X-TechBD-Bundle-ID or X-TechBD-Interaction-ID must be provided.
                        """)
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Successfully retrieved OperationOutcome(s)."),
                        @ApiResponse(responseCode = "400", description = "Invalid request parameters."),
                        @ApiResponse(responseCode = "500", description = "Internal error occurred.")
        })
        @ResponseBody
        public Object getOperationOutcomes(
                        @RequestHeader(value = "X-TechBD-Tenant-ID", required = true) String tenantId,
                        @RequestHeader(value = "X-TechBD-Bundle-ID", required = false) String bundleId,
                        @RequestHeader(value = "X-TechBD-Interaction-ID", required = false) String interactionId,
                        HttpServletRequest request) {

                UUID requestId = UUID.randomUUID();
                if (tenantId == null || tenantId.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Invalid request. TenantId is required.");
                }
                if (bundleId == null && interactionId == null) {
                        throw new IllegalArgumentException(
                                        "Invalid request. Either bundleId or interactionId is required.");
                }

                LOG.info(
                                "Fetching OperationOutcome(s) for requestId={} | tenantId={} | bundleId={} | interactionId={}",
                                requestId,
                                tenantId != null ? tenantId : "ALL",
                                bundleId,
                                interactionId);
                return fhirService.getOperationOutcomeSendToNyec(interactionId, bundleId, tenantId);
        }

}

