package org.techbd.service.http.hub.prime.ux;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.SandboxHelpers;
import org.techbd.service.http.hub.prime.route.RouteMapping;
import org.techbd.udi.UdiPrimeJpaConfig;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "Tech by Design Hub Data Quality UX API")
public class DataQualityController {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DataQualityController.class.getName());

    private final Presentation presentation;

    public DataQualityController(final Presentation presentation,
            @SuppressWarnings("PMD.UnusedFormalParameter") final UdiPrimeJpaConfig udiPrimeJpaConfig,
            @SuppressWarnings("PMD.UnusedFormalParameter") final SftpManager sftpManager,
            @SuppressWarnings("PMD.UnusedFormalParameter") final SandboxHelpers sboxHelpers) {
        this.presentation = presentation;
    }

    public List<String> getValuesForField(String field) {
        List<String> values = Arrays.asList(field);
        return values;
    }

    @GetMapping("/data-quality")
    @RouteMapping(label = "Data Quality", siblingOrder = 10)
    public String adminDiagnostics() {
        return "redirect:/data-quality/needs-attention";
    }

    @GetMapping("/data-quality/needs-attention")
    @RouteMapping(label = "Needs Attention", title = "Needs Attention", siblingOrder = 5)
    public String diagnosticsFhirNeedsAttention(final Model model, final HttpServletRequest request) {
        String templateName = "page/diagnostics/needs-attention";
        if (null != request.getParameter("qeName")) {
            model.addAttribute("qeName", request.getParameter("qeName"));
            templateName = "page/diagnostics/techbd-to-scoring-engine";
        }
        return presentation.populateModel(templateName, model, request);
    }

    @GetMapping("/data-quality/sftp")
    @RouteMapping(label = "Flat File (CSV) Data Quality", title = "Flat File (CSV) Data Quality", siblingOrder = 10)
    public String diagnosticsSftp(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/diagnostics/sftp", model, request);
    }

    @GetMapping("/data-quality/sftp-rejected")
    @RouteMapping(label = "Flat File (CSV) Data Quality - REJECTION", title = "Flat File (CSV) Data Quality - FHIR Generation Failures due to REJECTION", siblingOrder = 20)
    public String diagnosticsSftpRejected(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/diagnostics/sftp-rejected", model, request);
    }

    @GetMapping("/data-quality/fhir-validation-issues")
    @RouteMapping(label = "FHIR Data Quality", title = "FHIR validation issues", siblingOrder = 30)
    public String diagnosticsFhirValidationIssues(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/diagnostics/fhir-validation-issues", model, request);
    }

    @GetMapping("/data-quality/ig-publication-issues")
    @RouteMapping(label = "IG Publication Issues", title = "IG Publication Issues", siblingOrder = 40)
    public String igPublicationIssues(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/diagnostics/ig-publication-issues", model, request);
    }

}
