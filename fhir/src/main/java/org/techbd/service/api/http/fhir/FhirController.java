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

    // retrieve from properties file which is injected from pom.xml
    @Value("${org.techbd.service.api.http.fhir.FhirApplication.version}")
    private String appVersion;

    @GetMapping("/")
    public String home(final Model model) {
        model.addAttribute("version", this.appVersion);
        model.addAttribute("interactionsCount", InteractionsFilter.getObservables().size());
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
    @PostMapping(value = {"/Bundle"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Object handleBundle(final @RequestBody @Nonnull Map<String, Object> payload,
            final HttpServletRequest request) {
        var activeReqEnc = InteractionsFilter.getActiveRequestEnc(request);
        // Process the bundle using activeReqEnc.requestId() as the orch session ID
        return activeReqEnc;
    }

    @Operation(summary = "TODO")
    @PostMapping(value = {"/Bundle/$validate"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Object validateBundle(final @RequestBody @Nonnull Map<String, Object> payload,
            HttpServletRequest request) {
        var activeReqEnc = InteractionsFilter.getActiveRequestEnc(request);
        // Validate the bundle using activeReqEnc.requestId() as the orch session ID
        return activeReqEnc;
    }

    @GetMapping("/admin/observe/interactions")
    public String observeInteractions() {
        return "interactions";
    }

    @Operation(summary = "Recent HTTP Request/Response Interactions")
    @GetMapping("/admin/observe/interaction/recent.json")
    @ResponseBody
    public List<?> observeRecentInteractions() {
        return new ArrayList<>(InteractionsFilter.getObservables().values());
    }
}
