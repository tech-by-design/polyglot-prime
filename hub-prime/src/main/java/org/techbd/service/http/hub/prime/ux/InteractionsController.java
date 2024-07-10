package org.techbd.service.http.hub.prime.ux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.hub.prime.route.RouteMapping;
import org.techbd.udi.UdiPrimeJpaConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "TechBD Hub Interactions UX API")
public class InteractionsController {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(InteractionsController.class.getName());

    private final Presentation presentation;

    public InteractionsController(final Presentation presentation,
            final UdiPrimeJpaConfig udiPrimeJpaConfig,
            final SftpManager sftpManager) {
        this.presentation = presentation;
    }

    @GetMapping("/interactions")
    @RouteMapping(label = "Interactions", siblingOrder = 10)
    public String observeInteractions() {
        return "redirect:/interactions/https";
    }

    @GetMapping("/interactions/https")
    @RouteMapping(label = "FHIR via HTTPs", title = "FHIR Interactions via HTTPs", siblingOrder = 20)
    public String https(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/https", model, request);
    }

    @GetMapping("/interactions/httpsfailed")
    @RouteMapping(label = "FHIR via HTTPs FAILED", title = "FHIR Interactions via HTTPs (POST to SHIN-NY Failures)", siblingOrder = 30)
    public String https_failed(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/httpsfailed", model, request);
    }

    @GetMapping("/interactions/sftp")
    @RouteMapping(label = "CSV via SFTP (recent 10 egress)", title = "CSV Files via SFTP (egress directory)", siblingOrder = 40)
    public String sftp(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/sftp", model, request);
    }

    @Operation(summary = "Recent Orchctl Interactions")
    @GetMapping("/interactions/orchctl")
    @RouteMapping(label = "CSV via SFTP (DB)", title = "CSV Files via SFTP (in PostgreSQL DB)", siblingOrder = 50)
    public String orchctl(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/orchctl", model, request);
    }
}
