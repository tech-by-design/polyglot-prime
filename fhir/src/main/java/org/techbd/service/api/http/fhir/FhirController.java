package org.techbd.service.api.http.fhir;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.techbd.orchestrate.fhir.OrchestrationEngine;
import org.techbd.orchestrate.fhir.OrchestrationEngine.Device;
import org.techbd.service.api.http.Helpers;
import org.techbd.service.api.http.InteractionsFilter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "FHIR Endpoints", description = "FHIR Bundles API")
public class FhirController {
    final OrchestrationEngine engine = new OrchestrationEngine();

    @Value("${org.techbd.service.api.http.fhir.FhirController.defaultFhirProfileUrl:https://djq7jdt8kb490.cloudfront.net/1115/StructureDefinition-SHINNYBundleProfile.json}")
    private String defaultFhirProfileUrl;

    // retrieve from properties file which is injected from pom.xml
    @Value("${org.techbd.service.api.http.fhir.FhirApplication.version}")
    private String appVersion;

    @GetMapping("/")
    public String home(final Model model) {
        model.addAttribute("version", this.appVersion);
        model.addAttribute("interactionsCount", InteractionsFilter.interactions.getHistory().size());
        return "index";
    }

    @GetMapping("/metadata")
    @Operation(summary = "FHIR server's conformance statement")
    @ApiResponse(responseCode = "200", content = { @Content(mediaType = "application/xml") })
    public String metadata(final Model model, HttpServletRequest request) {
        final var baseUrl = Helpers.getBaseUrl(request);

        model.addAttribute("version", this.appVersion);
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
            @RequestHeader(value = SwaggerConfig.REQ_HEADER_TECH_BD_FHIR_SERVICE_QE_IDENTIFIER, required = true) String tenantId,
            @RequestParam(value = "profile", required = false) String fhirProfileUrlParam, // "profile" is the same name
                                                                                           // that HL7 validator uses
            @RequestHeader(value = "TECH_BD_FHIR_SERVICE_STRUCT_DEFN_PROFILE_URI", required = false) String fhirProfileUrlHeader,
            @RequestParam(value = "include-request-in-outcome", required = false) boolean includeRequestInOutcome,
            HttpServletRequest request) {

        final var fhirProfileUrl = (fhirProfileUrlParam != null) ? fhirProfileUrlParam
                : (fhirProfileUrlHeader != null) ? fhirProfileUrlHeader : defaultFhirProfileUrl;
        final var session = engine.session()
                .onDevice(Device.createDefault())
                .withPayloads(List.of(payload))
                .withFhirProfileUrl(fhirProfileUrl)
                .addHapiValidationEngine()
                .addHl7ValidationEngine()
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
    public String observeInteractions() {
        return "interactions";
    }

    @Operation(summary = "Recent HTTP Request/Response Interactions")
    @GetMapping("/admin/observe/interaction/recent.json")
    @ResponseBody
    public List<?> observeRecentInteractions() {
        return new ArrayList<>(InteractionsFilter.interactions.getHistory().values());
    }
}
