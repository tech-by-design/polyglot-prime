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
@Tag(name = "TechBD Hub Diagnostics UX API")
public class DiagnosticsController {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticsController.class.getName());

    private final Presentation presentation;

    public DiagnosticsController(final Presentation presentation,
            final UdiPrimeJpaConfig udiPrimeJpaConfig,
            final SftpManager sftpManager,
            final SandboxHelpers sboxHelpers) {
        this.presentation = presentation;
    }

    public List<String> getValuesForField(String field) {
        List<String> values = Arrays.asList(field);
        return values;
    }

    @GetMapping("/data-quality")
    @RouteMapping(label = "Data Quality", siblingOrder = 20)
    public String adminDiagnostics() {
        return "redirect:/data-quality/sftp";
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
}
