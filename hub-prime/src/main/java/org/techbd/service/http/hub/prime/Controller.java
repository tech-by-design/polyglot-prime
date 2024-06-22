package org.techbd.service.http.hub.prime;

import static org.techbd.udi.auto.jooq.ingress.Tables.HUB_OPERATION_SESSION;
import static org.techbd.udi.auto.jooq.ingress.Tables.HUB_OPERATION_SESSION_ENTRY;
import static org.techbd.udi.auto.jooq.ingress.Tables.INTERACTION_HTTP;
import static org.techbd.udi.auto.jooq.ingress.Tables.LINK_SESSION_ENTRY;
import static org.techbd.udi.auto.jooq.ingress.Tables.SAT_OPERATION_SESSION_ENTRY_SESSION_STATE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.Record;
import org.ocpsoft.prettytime.PrettyTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
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
import org.techbd.service.http.aggrid.ServerRowsRequest;
import org.techbd.service.http.aggrid.ServerRowsResponse;
import org.techbd.service.http.aggrid.SqlQueryBuilder;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.UdiInsertSessionWithState;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.core.util.ObjectMapperFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;

@org.springframework.stereotype.Controller
@Tag(name = "TechBD Hub", description = "Business Operations API")
public class Controller {
    private static final Logger LOG = LoggerFactory.getLogger(Controller.class.getName());

    private final Map<String, Object> ssrBaggage = new HashMap<>();
    private final ObjectMapper baggageMapper = ObjectMapperFactory.buildStrictGenericObjectMapper();
    private final OrchestrationEngine engine = new OrchestrationEngine();
    private final AppConfig appConfig;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;
    private final SftpManager sftpManager;
    private final SandboxHelpers sboxHelpers;

    @Value(value = "${org.techbd.service.baggage.user-agent.enable-sensitive:false}")
    private boolean userAgentSensitiveBaggageEnabled = false;

    @Value(value = "${org.techbd.service.baggage.user-agent.exposure:false}")
    private boolean userAgentBaggageExposureEnabled = false;

    @Autowired
    private Environment environment;

    public Controller(final Environment environment, final AppConfig appConfig,
            final UdiPrimeJpaConfig udiPrimeJpaConfig,
            final SftpManager sftpManager,
            final SandboxHelpers sboxHelpers) {
        this.environment = environment;
        this.appConfig = appConfig;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
        this.sftpManager = sftpManager;
        this.sboxHelpers = sboxHelpers;
        ssrBaggage.put("appVersion", appConfig.getVersion());
        ssrBaggage.put("activeSpringProfiles", List.of(this.environment.getActiveProfiles()));
    }

    protected void populateModel(final String templateName, final Model model, final HttpServletRequest request) {
        try {
            final var principal = (OAuth2User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            AuthenticatedUser authUser = null;
            if (principal != null) {
                authUser = new AuthenticatedUser(principal, "", new ArrayList<String>());
            }

            // make the request, authUser available to templates
            model.addAttribute("req", request);
            model.addAttribute("authUser", authUser);

            final var baggage = new HashMap<>(ssrBaggage);
            baggage.put("userAgentBaggageExposureEnabled", userAgentBaggageExposureEnabled);
            baggage.put("health",
                    Map.of("udiPrimaryDataSourceAlive", udiPrimeJpaConfig.udiPrimaryDataSrcHealth().isAlive()));

            if (sboxHelpers.isEditorAvailable()) {
                final var canonicalTmplName = "templates/" + templateName + ".html";
                final var targetResource = getClass().getClassLoader().getResource(canonicalTmplName);
                final var targetFsPath = targetResource != null ? targetResource.getFile() : null;
                final var srcFsPath = targetFsPath != null
                        ? targetFsPath.replace("target/classes", "src/main/resources")
                        : null;
                final var editUrl = srcFsPath != null ? sboxHelpers.getEditorUrlFromAbsolutePath(srcFsPath) : null;
                baggage.put("template",
                        Map.of("supplied", templateName, "canonical", canonicalTmplName, "targetFsPath", targetFsPath,
                                "srcFsPath", srcFsPath, "editUrl", editUrl));
            }

            // "baggage" is for typed server-side usage by templates
            // "ssrBaggageJSON" is for JavaScript client use
            model.addAttribute("baggage", baggage);
            model.addAttribute("ssrBaggageJSON", baggageMapper.writeValueAsString(baggage));
            LOG.info("Logged in user Information"
                    + SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        } catch (JsonProcessingException e) {
            LOG.error("error setting ssrBaggageJSON in populateModel", e);
        }
    }

    @GetMapping("/home")
    public String home(final Model model, final HttpServletRequest request) {
        final var templateName = "page/home";
        populateModel(templateName, model, request);
        return templateName;
    }

    @GetMapping("/")
    public String index() {
        return "login/login";
    }

    @GetMapping(value = "/admin/cache/tenant-sftp-egress-content/clear")
    @CacheEvict(value = { SftpManager.TENANT_EGRESS_CONTENT_CACHE_KEY,
            SftpManager.TENANT_EGRESS_SESSIONS_CACHE_KEY }, allEntries = true)
    public ResponseEntity<?> emptyTenantEgressCacheOnDemand() {
        LOG.info("emptying tenant-sftp-egress-content (on demand)");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("emptying tenant-sftp-egress-content");
    }

    @GetMapping(value = "/dashboard/stat/sftp/most-recent-egress/{tenantId}.{extension}", produces = {
            "application/json", "text/html" })
    public ResponseEntity<?> handleRequest(@PathVariable String tenantId, @PathVariable String extension) {
        final var account = sftpManager.configuredTenant(tenantId);
        if (account.isPresent()) {
            final var content = sftpManager.tenantEgressContent(account.get());
            final var mre = content.mostRecentEgress();

            if ("html".equalsIgnoreCase(extension)) {
                String timeAgo = mre.map(zonedDateTime -> new PrettyTime().format(zonedDateTime)).orElse("None");
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                        .body(content.error() == null
                                ? "<span title=\"%d sessions found, most recent %s\">%s</span>".formatted(
                                        content.directories().length,
                                        mre,
                                        timeAgo)
                                : "<span title=\"No directories found in %s\">⚠️</span>".formatted(content.sftpUri()));
            } else if ("json".equalsIgnoreCase(extension)) {
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mre);
            } else {
                return ResponseEntity.badRequest().build();
            }
        } else {
            if ("html".equalsIgnoreCase(extension)) {
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                        .body("Unknown tenantId '%s'".formatted(tenantId));
            } else if ("json".equalsIgnoreCase(extension)) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.badRequest().build();
            }

        }
    }

    @GetMapping("/docs")
    public String docs(final Model model, final HttpServletRequest request) {
        final var templateName = "page/documentation";
        populateModel(templateName, model, request);
        return templateName;
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

        final var provenance = "%s.validateBundleAndForward(%s)".formatted(Controller.class.getName(),
                isSync ? "sync" : "async");
        final var bundleAsyncInteractionId = UUID.randomUUID();
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
        final var immediateResult = new HashMap<>(Map.of(
                "resourceType", "OperationOutcome",
                "bundleSessionId", bundleAsyncInteractionId.toString(), // for tracking in database, etc.
                "isAsync", true,
                "validationResults", session.getValidationResults(),
                "statusUrl",
                "https://" + request.getServerName() + "/Bundle/$status/" + bundleAsyncInteractionId.toString(),
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
            dataLakeApiBaseURL = appConfig.getDefaultSdohFhirProfileUrl();
        }
        LOG.debug("%s %s dataLakeApiBaseURL".formatted(provenance, tenantId, dataLakeApiBaseURL));

        final var webClient = WebClient.builder().baseUrl(dataLakeApiBaseURL).build();
        final var jooqDSL = udiPrimeJpaConfig.dsl();
        final var jooqCfg = jooqDSL.configuration();

        try {
            final var init = new UdiInsertSessionWithState();
            final var initState = "%s.IN_PROCESS".formatted(provenance);
            init.setSessionId(bundleAsyncInteractionId.toString());
            init.setFromState("%s.INIT".formatted(provenance));
            init.setToState(initState);
            init.setContentType(request.getHeader("Content-Type"));
            init.setContent(payload);
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
                        success.setSessionId(bundleAsyncInteractionId.toString());
                        success.setFromState(initState);
                        success.setToState("%s.SUCCESS".formatted(provenance));
                        success.setContentType("TODO"); // add content type from response
                        success.setContent(response);
                        success.setCreatedBy(tenantId);
                        success.setProvenance(provenance);
                        success.setNamespace(provenance);

                        final var successExecResult = success.execute(jooqCfg);
                        final var successResults = success.getResults();

                        LOG.info("successExecResult" + successExecResult);
                        LOG.info("successResults" + successResults);
                    }, (Throwable error) -> { // Explicitly specify the type Throwable
                        LOG.info("%s webClient.Throwable(error)".formatted(provenance));
                        String content;
                        final ObjectMapper mapper = new ObjectMapper();
                        try {
                            content = mapper.writeValueAsString(error);
                        } catch (JsonProcessingException e) {
                            content = e.toString();
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

    @GetMapping("/admin/observe/interactions")
    public String observeInteractions() {
        return "redirect:/admin/observe/interactions/https";
    }

    @GetMapping("/admin/observe/interactions/{nature}")
    public String observeInteractionsNature(@PathVariable String nature, final Model model,
            final HttpServletRequest request) {
        final var templateName = "page/interactions/" + nature;
        populateModel(templateName, model, request);
        return templateName;
    }

    @Operation(summary = "Recent SFTP Interactions")
    @GetMapping("/admin/observe/interaction/sftp/recent.json")
    @ResponseBody
    public List<?> observeRecentSftpInteractions() {
        return sftpManager.tenantEgressSessions();
    }

    @Operation(summary = "HTTP Request/Response Interactions for Populating Grid")
    @PostMapping(value = "/support/interaction/http.json", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ServerRowsResponse httpInteractions(final @RequestBody @Nonnull ServerRowsRequest payload) {
        // TODO: figure out how to write dynamic queries in jOOQ
        // final var DSL = udiPrimeJpaConfig.dsl();
        // final var result =
        // DSL.selectFrom(INTERACTION_HTTP).offset(payload.getStartRow())
        // .limit(payload.getEndRow() - payload.getStartRow() + 1).fetch();
        // return ServerRowsResponse.createResponse(payload, result.intoMaps(), null);

        // TODO: obtain the pivot values from the DB for the requested pivot columns
        // see
        // https://github.com/ag-grid/ag-grid-server-side-oracle-example/src/main/java/com/ag/grid/enterprise/oracle/demo/dao/TradeDao.java
        // final var pivotValues = getPivotValues(request.getPivotCols());
        final Map<String, List<String>> pivotValues = Map.of();

        final var DSL = udiPrimeJpaConfig.dsl();
        final var result = DSL.fetch(new SqlQueryBuilder().createSql(payload, "techbd_udi_ingress.interaction_http",
                pivotValues));
        final var rows = result.intoMaps();
        for (final var row : rows) {
            // this is a JSONB and might be large so don't send it even if it was requested
            // since we'll get it in /support/interaction/{interactionId}.json if required;
            // also since SqlQueryBuilder().createSql() is custom SQL, org.jooq.JSONB type
            // will not be able to be serialized by Jackson anyway.
            row.remove("request_payload");
        }

        // create response with our results
        return ServerRowsResponse.createResponse(payload, rows, pivotValues);

    }

    @Operation(summary = "Specific HTTP Request/Response Interaction which is assumed to exist")
    @GetMapping("/support/interaction/{interactionId}.json")
    @ResponseBody
    public Map<String, Object> httpInteraction(final @PathVariable String interactionId) {
        final var DSL = udiPrimeJpaConfig.dsl();
        final var result = DSL.selectFrom(INTERACTION_HTTP).where(INTERACTION_HTTP.INTERACTION_ID.eq(interactionId))
                .fetchSingle();
        return result.intoMap();
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

    @Operation(summary = "Recent HTTP Request/Response Diagnostics")
    @GetMapping("/admin/observe/sessions/data")
    @ResponseBody
    public Page<?> adminDiagnosticsJson(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // TODO: don't select from HUB_OPERATION_SESSION, use the proper VIEW
        final var DSL = udiPrimeJpaConfig.dsl();
        final var result = DSL.selectFrom(HUB_OPERATION_SESSION).offset(page).limit(size).fetch();
        return new PageImpl<>(result.intoMaps(), PageRequest.of(page, size), DSL.fetchCount(HUB_OPERATION_SESSION));
    }

    @GetMapping("/admin/observe/sessions")
    public String adminDiagnostics(final Model model, final HttpServletRequest request) {
        final var templateName = "page/diagnostics";
        populateModel(templateName, model, request);
        model.addAttribute("udiPrimaryDataSrcHealth", udiPrimeJpaConfig.udiPrimaryDataSrcHealth());
        return templateName;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthenticatedUser(String name, String emailPrimary, String profilePicUrl, String gitHubId,
            String tenantId, List<String> roles) {
        public AuthenticatedUser(final OAuth2User principal, String tenantId, List<String> roles) {
            this((String) principal.getAttribute("name"), (String) principal.getAttribute("email"),
                    (String) principal.getAttribute("avatar_url"), (String) principal.getAttribute("login"), tenantId,
                    roles);
        }
    }

    @GetMapping(value = "/Bundle/$status/{bundleSessionId}", produces = { "application/json", "text/html" })
    @ResponseBody
    @Operation(summary = "Check the state/status of async operation")
    public Object bundleStatus(@PathVariable String bundleSessionId, final Model model, HttpServletRequest request) {

        final var jooqDSL = udiPrimeJpaConfig.dsl();
        final var mapper = new ObjectMapper();
        final var responseJson = mapper.createObjectNode();

        // TODO: need to give actual response from upstream server (meaning from SHIN-NY
        // Data Lake) so far, this method gives meta data but not the actual response

        try {
            final var result = jooqDSL.select()
                    .from(HUB_OPERATION_SESSION)
                    .join(LINK_SESSION_ENTRY)
                    .on(LINK_SESSION_ENTRY.HUB_OPERATION_SESSION_ID.eq(HUB_OPERATION_SESSION.HUB_OPERATION_SESSION_ID))
                    .join(HUB_OPERATION_SESSION_ENTRY)
                    .on(LINK_SESSION_ENTRY.HUB_OPERATION_SESSION_ENTRY_ID
                            .eq(HUB_OPERATION_SESSION_ENTRY.HUB_OPERATION_SESSION_ENTRY_ID))
                    .join(SAT_OPERATION_SESSION_ENTRY_SESSION_STATE)
                    .on(HUB_OPERATION_SESSION_ENTRY.HUB_OPERATION_SESSION_ENTRY_ID
                            .eq(SAT_OPERATION_SESSION_ENTRY_SESSION_STATE.HUB_OPERATION_SESSION_ENTRY_ID))
                    .where(HUB_OPERATION_SESSION.HUB_OPERATION_SESSION_ID.in(bundleSessionId))
                    .fetch();

            final var dataArray = mapper.createArrayNode();
            for (final Record record : result) {
                final var recordJson = mapper.createObjectNode();
                recordJson.put("sessionId", record.get(HUB_OPERATION_SESSION.HUB_OPERATION_SESSION_ID));
                recordJson.put("sessionKey", record.get(HUB_OPERATION_SESSION.KEY));
                recordJson.put("entryId", record.get(HUB_OPERATION_SESSION_ENTRY.HUB_OPERATION_SESSION_ENTRY_ID));
                recordJson.put("fromState", record.get(SAT_OPERATION_SESSION_ENTRY_SESSION_STATE.FROM_STATE));
                recordJson.put("toState", record.get(SAT_OPERATION_SESSION_ENTRY_SESSION_STATE.TO_STATE));
                recordJson.put("createdBy", record.get(SAT_OPERATION_SESSION_ENTRY_SESSION_STATE.CREATED_BY));
                recordJson.put("provenance", record.get(SAT_OPERATION_SESSION_ENTRY_SESSION_STATE.PROVENANCE));
                dataArray.add(recordJson);
            }

            responseJson.set("data", dataArray);
            LOG.info("Query execution result: {}", result);
        } catch (Exception e) {
            LOG.error("Error executing JOOQ query", e);
            responseJson.put("error", "Error fetching data");
        }

        return responseJson;
    }
}
