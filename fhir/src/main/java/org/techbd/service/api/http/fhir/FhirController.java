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
import org.springframework.web.bind.annotation.ResponseBody;
import org.techbd.orchestrate.fhir.OrchestrationEngine;
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
    public Object validateBundle(final @RequestBody @Nonnull String payload, HttpServletRequest request) {

        final var session = engine.session()
                .withPayloads(List.of(payload))
                .withFhirProfileUrl("http://example.com/fhirProfile")
                .addHapiValidationEngine()
                .addHl7ValidationEngine()
                .addInfernoValidationEngine()
                .build();
        engine.orchestrate(session);

        final var result = Map.of(
                "OperationOutcome",
                Map.of("validationResults", session.getValidationResults(), "request",
                        InteractionsFilter.getActiveRequestEnc(request)));
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
