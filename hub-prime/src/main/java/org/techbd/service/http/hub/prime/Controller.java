package org.techbd.service.http.hub.prime;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.ocpsoft.prettytime.PrettyTime;
import org.postgresql.util.PGobject;
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
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.techbd.conf.Configuration;
import org.techbd.orchestrate.fhir.OrchestrationEngine;
import org.techbd.orchestrate.fhir.OrchestrationEngine.Device;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.Helpers;
import org.techbd.service.http.Interactions.RequestResponseEncountered;
import org.techbd.service.http.InteractionsFilter;
import org.techbd.udi.InteractionRepository;
import org.techbd.udi.SessionIssueRepository;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.UdiPrimeRepository;
import org.techbd.udi.auto.jooq.ingress.routines.UdiInsertSessionWithState;
import org.techbd.udi.entity.FhirValidationResultIssue;
import org.techbd.udi.entity.Interaction;
import org.techbd.util.SessionWithState;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
    private final UdiPrimeRepository udiPrimeRepository;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;
    private final SftpManager sftpManager;
    private final InteractionRepository interactionRepository;
    private final SessionIssueRepository sessionIssueRepository;

    @Value(value = "${org.techbd.service.baggage.user-agent.enable-sensitive:false}")
    private boolean userAgentSensitiveBaggageEnabled = false;

    @Value(value = "${org.techbd.service.baggage.user-agent.exposure:false}")
    private boolean userAgentBaggageExposureEnabled = false;

    @Autowired
    private Environment environment;
    private WebClient webClient;

    public Controller(final Environment environment, final AppConfig appConfig,
            final UdiPrimeJpaConfig udiPrimeJpaConfig,
            final UdiPrimeRepository udiPrimeRepository, final SftpManager sftpManager,
            InteractionRepository interactionRepository, SessionIssueRepository sessionIssueRepository,
            WebClient.Builder webClientBuilder) {
        this.environment = environment;
        this.appConfig = appConfig;
        this.udiPrimeRepository = udiPrimeRepository;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
        this.sftpManager = sftpManager;
        this.interactionRepository = interactionRepository;
        this.sessionIssueRepository = sessionIssueRepository;
        this.webClient = webClientBuilder.baseUrl("https://40lafnwsw7.execute-api.us-east-1.amazonaws.com/dev")
                .build();
        ssrBaggage.put("appVersion", appConfig.getVersion());
        ssrBaggage.put("activeSpringProfiles", List.of(this.environment.getActiveProfiles()));
    }

    protected void populateModel(final Model model, final HttpServletRequest request) {
        try {
            OAuth2User principal = (OAuth2User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
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
        populateModel(model, request);
        return "page/home";
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
        populateModel(model, request);
        return "page/documentation";
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
    public Object validateBundleAndCreate(final @RequestBody @Nonnull String payload,
            @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
            // "profile" is the same name that HL7 validator uses
            @RequestParam(value = "profile", required = false) String fhirProfileUrlParam,
            @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.FHIR_STRUCT_DEFN_PROFILE_URI, required = false) String fhirProfileUrlHeader,
            @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.FHIR_VALIDATION_STRATEGY, required = false) String uaValidationStrategyJson,
            @RequestHeader(value = AppConfig.Servlet.HeaderName.Request.DATALAKE_API_URL, required = false) String datalakeApi,
            @RequestParam(value = "include-request-in-outcome", required = false) boolean includeRequestInOutcome,
            final HttpServletRequest request) throws SQLException {

        Connection conn = udiPrimeJpaConfig.udiPrimaryDataSource().getConnection();

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

        // Async call to Datalake API
        // POST
        // https://40lafnwsw7.execute-api.us-east-1.amazonaws.com/dev?processingAgent=QE
        // Make the POST request asynchronously
        // Prepare Target API URI
        String targetApiUrl = null;
        if (datalakeApi != null && !datalakeApi.isEmpty()) {
            targetApiUrl = datalakeApi;
            this.webClient = WebClient.builder()
                    .baseUrl(datalakeApi) // Set the base URL
                    .build();
        } else {
            targetApiUrl = appConfig.getDefaultSdohFhirProfileUrl();
        }
        String host = null, path = null;
        try {
            URL url = URI.create(targetApiUrl).toURL();
            host = url.getHost();
            path = url.getPath();
        } catch (MalformedURLException e) {
            // log.error("Exception in parsing shinnyDataLakeApiUri: ", e);
        }

        String sessionId = UUID.randomUUID().toString();
        LOG.info("The Session id passed: " + sessionId);
        SessionWithState firstProcedureCall = callUdiInsertSessionWithState(
                conn, sessionId,
                "org.techbd.service.http.hub.prime",
                payload,
                "application/json",
                "{}",
                "{}",
                "",
                "",
                "",
                "STARTED");

        final var dsl = udiPrimeJpaConfig.dsl();
        try {
            final var sp = new UdiInsertSessionWithState();
            sp.setSessionId(UUID.randomUUID().toString());
            sp.setFromState("ASYNC_FAILED");
            sp.setToState("ASYNC_FAILED");
            sp.setCreatedBy("user");
            sp.setProvenance("pro time 23:00 ");

            final var spResult = sp.execute(dsl.configuration());

            LOG.info("result of Joog SP " + spResult);
        } catch (Exception e) {
            e.printStackTrace();
        }

        webClient.post()
                .uri("?processingAgent=" + tenantId)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(response -> {
                    LOG.info("Response from Datalake API call");
                    SessionWithState secondProcedureCall = callUdiInsertSessionWithState(
                            conn,
                            firstProcedureCall.getHubSessionId(),
                            "org.techbd.service.http.hub.prime",
                            response,
                            "application/json",
                            "{}",
                            "{}",
                            "",
                            "",
                            "",
                            "FINISHED");
                }, (Throwable error) -> { // Explicitly specify the type Throwable

                    String content;
                    ObjectMapper mapper = new ObjectMapper();
                    try {
                        content = mapper.writeValueAsString(error);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }

                    SessionWithState secondProcedureCall = callUdiInsertSessionWithState(
                            conn,
                            firstProcedureCall.getHubSessionId(),
                            "org.techbd.service.http.hub.prime",
                            content,
                            "application/json",
                            "{}",
                            "{}",
                            "",
                            "",
                            "",
                            "ASYNC_FAILED");
                    if (error instanceof WebClientResponseException responseException) {
                        // TODO: Process the response here, and save to db.
                        if (responseException.getStatusCode() == HttpStatus.FORBIDDEN) {
                            // Handle 403 Forbidden err
                        } else if (responseException.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                            // Handle 403 Forbidden error
                        } else {
                            // Handle other types of WebClientResponseException
                        }
                    } else {
                        // Handle other types of exceptions
                    }
                });
        SessionWithState thirdProcedureCall = callUdiInsertSessionWithState(
                conn,
                firstProcedureCall.getHubSessionId(),
                "org.techbd.service.http.hub.prime",
                "{}",
                "application/json",
                "{}",
                "{}",
                "",
                "",
                "",
                "ASYNC_IN_PROGRESS");
        LOG.info("Datalake API called, ASYNC_IN_PROGRESS");
        return result;
    }

    public SessionWithState callUdiInsertSessionWithState(Connection conn, String session_id, String namespace,
            String content, String content_type, String boundary, String elaboration, String created_by,
            String provenance, String from_state, String to_state) {

        SessionWithState sessionWithState = new SessionWithState();
        try {
            String sql = "{ call techbd_udi_ingress.udi_insert_session_with_state(?, ?, ?, ?, ?, ?, ?, ?, ?, ?) }";

            CallableStatement stmt = conn.prepareCall(sql);

            stmt.setString(1, session_id);
            stmt.setString(2, namespace);
            stmt.setString(3, content);
            stmt.setString(4, content_type);

            PGobject jsonObject1 = new PGobject();
            jsonObject1.setType("jsonb");
            jsonObject1.setValue(boundary);
            stmt.setObject(5, jsonObject1);

            PGobject jsonObject2 = new PGobject();
            jsonObject2.setType("jsonb");
            jsonObject2.setValue(elaboration);
            stmt.setObject(6, jsonObject2);

            stmt.setString(7, created_by);

            if (provenance != null) {
                stmt.setString(8, provenance);
            } else {
                stmt.setNull(8, java.sql.Types.VARCHAR);
            }

            if (provenance != null) {
                stmt.setString(9, from_state);
            } else {
                stmt.setNull(9, java.sql.Types.VARCHAR);
            }
            if (to_state != null) {
                stmt.setString(10, to_state);
            } else {
                stmt.setNull(10, java.sql.Types.VARCHAR);
            }

            stmt.execute();
            ResultSet rs = stmt.getResultSet();
            if (rs.next()) {
                sessionWithState.setHubSessionId(rs.getString(1));
                sessionWithState.setHubSessionEntryId(rs.getString(2));
                LOG.info("New Session Id: " + sessionWithState.getHubSessionId());
                LOG.info("New Session Entry Id: " + sessionWithState.getHubSessionEntryId());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sessionWithState;
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
        return "redirect:/admin/observe/interactions/sftp";
    }

    @GetMapping("/admin/observe/interactions/{nature}")
    public String observeInteractionsNature(@PathVariable String nature, final Model model,
            final HttpServletRequest request) {
        populateModel(model, request);
        return "page/interactions/" + nature;
    }

    @Operation(summary = "Recent SFTP Interactions")
    @GetMapping("/admin/observe/interaction/sftp/recent.json")
    @ResponseBody
    public List<?> observeRecentSftpInteractions() {
        return sftpManager.tenantEgressSessions();
    }

    @Operation(summary = "Recent HTTP Request/Response Interactions")
    @GetMapping("/admin/observe/interaction/https/recent.json")
    @ResponseBody
    public Page<Interaction> observeRecentHttpsInteractions(final Model model,
            final HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        populateModel(model, request);
        long count = interactionRepository.count();
        Pageable pageable = PageRequest.of(page, size);
        return new PageImpl<>(interactionRepository.findAllInteractions(), pageable, count);
    }

    @GetMapping("/admin/observe/interaction/https/recent.json/{interactionId}")
    public String observeRecentHttpsInteractionDetails(final Model model, HttpServletRequest request,
            @PathVariable String interactionId) throws JsonProcessingException {
        Map<UUID, RequestResponseEncountered> history = InteractionsFilter.interactions.getHistory();
        RequestResponseEncountered reqResp = history.get(UUID.fromString(interactionId));
        ObjectMapper objectMapper = new ObjectMapper();
        var jsonRequestResponseEncountered = objectMapper.registerModule(new JavaTimeModule())
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(reqResp);
        model.addAttribute("sessionDetails", jsonRequestResponseEncountered);
        return "page/interactions/session-details.html";
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
    public Page<FhirValidationResultIssue> adminDiagnosticsJson(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        long count = udiPrimeRepository.count();
        Pageable pageable = PageRequest.of(page, size);
        return new PageImpl<>(udiPrimeRepository.findAll(), pageable, count);
    }

    @GetMapping("/admin/observe/sessions")
    public String adminDiagnostics(final Model model, final HttpServletRequest request) {
        populateModel(model, request);
        model.addAttribute("udiPrimaryDataSrcHealth", udiPrimeJpaConfig.udiPrimaryDataSrcHealth());
        return "page/diagnostics";
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
}
