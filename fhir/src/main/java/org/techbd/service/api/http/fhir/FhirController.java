package org.techbd.service.api.http.fhir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import org.techbd.orchestrate.fhir.OrchestrationEngine;
import org.techbd.orchestrate.fhir.OrchestrationEngine.Device;
import org.techbd.service.api.http.Helpers;
import org.techbd.service.api.http.InteractionsFilter;
import org.techbd.service.api.http.fhir.entity.FhirValidationResultIssue;
import org.techbd.service.api.http.fhir.repository.FhirValidationResultIssueRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "FHIR Endpoints", description = "FHIR Bundles API")
public class FhirController {
    private final OrchestrationEngine engine = new OrchestrationEngine();
    private final FhirAppConfiguration appConfig;
    private final FhirValidationResultIssueRepository fhirValidationResultIssueRepository;

    @Autowired
    public FhirController(final FhirAppConfiguration appConfig, FhirValidationResultIssueRepository fhirValidationResultIssueRepository) {
        this.appConfig = appConfig;
        this.fhirValidationResultIssueRepository = fhirValidationResultIssueRepository;
    }

    @GetMapping("/")
    public String home(final Model model) {
        model.addAttribute("version", appConfig.getVersion());
        model.addAttribute("interactionsCount", InteractionsFilter.interactions.getHistory().size());
        return "index";
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
            @RequestHeader(value = FhirAppConfiguration.Servlet.HeaderName.Request.STRUCT_DEFN_PROFILE_URI, required = false) String fhirProfileUrlHeader,
            @RequestParam(value = "include-request-in-outcome", required = false) boolean includeRequestInOutcome,
            final HttpServletRequest request) {

        final var fhirProfileUrl = (fhirProfileUrlParam != null) ? fhirProfileUrlParam
                : (fhirProfileUrlHeader != null) ? fhirProfileUrlHeader : appConfig.getDefaultSdohFhirProfileUrl();
        final var session = engine.session()
                .onDevice(Device.createDefault())
                .withPayloads(List.of(payload))
                .withFhirProfileUrl(fhirProfileUrl)
                .addHapiValidationEngine()
                .addHl7ValidationApiEngine()
                .addInfernoValidationEngine()
                .build();
        engine.orchestrate(session);

        final var opOutcome = Map.of("resourceType", "OperationOutcome", "validationResults",
                session.getValidationResults(), "device",
                session.getDevice());
        final var result = Map.of("OperationOutcome", opOutcome);
        if (includeRequestInOutcome) {
            opOutcome.put("request", InteractionsFilter.getActiveRequestEnc(request));
        }

        return result;
    }

    @GetMapping("/admin/observe/interactions")
    public String observeInteractions(final Model model, final HttpServletRequest request) {
        model.addAttribute("contextPath", request.getContextPath());
        return "interactions";
    }

    @Operation(summary = "Recent HTTP Request/Response Interactions")
    @GetMapping("/admin/observe/interaction/recent.json")
    @ResponseBody
    public List<?> observeRecentInteractions() {
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
        long count = fhirValidationResultIssueRepository.count();
        Pageable pageable = PageRequest.of(page, size);
        return new PageImpl<>(fhirValidationResultIssueRepository.findAll(), pageable, count);
    }

    @GetMapping("/admin/observe/sessions")
    public String adminDiagnostics(final Model model, final HttpServletRequest request) {
        model.addAttribute("contextPath", request.getContextPath());
        return "diagnostics";
    }
}
