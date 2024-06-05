package org.techbd.service.http.hub.prime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.UdiPrimeRepository;
import org.techbd.udi.entity.FhirValidationResultIssue;

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
    static private final Logger LOG = LoggerFactory.getLogger(Controller.class.getName());
    private final Map<String, Object> ssrBaggage = new HashMap<>();
    private final ObjectMapper baggageMapper = ObjectMapperFactory.buildStrictGenericObjectMapper();
    private final OrchestrationEngine engine = new OrchestrationEngine();
    private final AppConfig appConfig;
    private final UdiPrimeRepository udiPrimeRepository;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;
    private final SftpManager sftpManager;

    @Value(value = "${org.techbd.service.baggage.user-agent.enable-sensitive:false}")
    private boolean userAgentSensitiveBaggageEnabled = false;

    @Value(value = "${org.techbd.service.baggage.user-agent.exposure:false}")
    private boolean userAgentBaggageExposureEnabled = false;

    @Autowired
    private Environment environment;

    public Controller(final Environment environment, final AppConfig appConfig,
            final UdiPrimeJpaConfig udiPrimeJpaConfig,
            final UdiPrimeRepository udiPrimeRepository, final SftpManager sftpManager) {
        this.environment = environment;
        this.appConfig = appConfig;
        this.udiPrimeRepository = udiPrimeRepository;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
        this.sftpManager = sftpManager;
        ssrBaggage.put("appVersion", appConfig.getVersion());
        ssrBaggage.put("activeSpringProfiles", List.of(this.environment.getActiveProfiles()));
    }

    protected void populateModel(final Model model, final HttpServletRequest request) {
        try {
            final var baggage = new HashMap<>(ssrBaggage);
            baggage.put("userAgentBaggageExposureEnabled", userAgentBaggageExposureEnabled);
            baggage.put("health",
                    Map.of("udiPrimaryDataSourceAlive", udiPrimeJpaConfig.udiPrimaryDataSrcHealth().isAlive()));

            // "baggage" is for typed server-side usage by templates
            // "ssrBaggageJSON" is for JavaScript client use
            model.addAttribute("baggage", baggage);
            model.addAttribute("ssrBaggageJSON", baggageMapper.writeValueAsString(baggage));
        } catch (JsonProcessingException e) {
            LOG.error("error setting ssrBaggageJSON in populateModel", e);
        }
    }

    @GetMapping("/")
    public String home(final Model model, final HttpServletRequest request) {
        populateModel(model, request);
        return "page/home";
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
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body("Unknown tenantId '%s'".formatted(tenantId));
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

    @Operation(summary = "TODO")
    @PostMapping(value = { "/Bundle" }, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Object handleBundle(final @RequestBody @Nonnull Map<String, Object> payload,
            final HttpServletRequest request) {
        var activeReqEnc = InteractionsFilter.getActiveRequestEnc(request);
        // Process the bundle using activeReqEnc.requestId() as the orch session ID
        return activeReqEnc;
    }

    @PostMapping(value = { "/Bundle/$validate" }, consumes = MediaType.APPLICATION_JSON_VALUE)
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
    public List<?> observeRecentHttpsInteractions() {
        return new ArrayList<>(InteractionsFilter.interactions.getHistory().values());
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
}
