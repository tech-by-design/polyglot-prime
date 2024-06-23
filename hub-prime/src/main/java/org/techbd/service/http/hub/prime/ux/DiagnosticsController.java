package org.techbd.service.http.hub.prime.ux;

import static org.techbd.udi.auto.jooq.ingress.Tables.HUB_OPERATION_SESSION;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.SandboxHelpers;
import org.techbd.service.http.hub.prime.route.RouteMapping;
import org.techbd.udi.UdiPrimeJpaConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "TechBD Hub Diagnostics UX API")
public class DiagnosticsController {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticsController.class.getName());

    private final Presentation presentation;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;

    public DiagnosticsController(final Presentation presentation,
            final UdiPrimeJpaConfig udiPrimeJpaConfig,
            final SftpManager sftpManager,
            final SandboxHelpers sboxHelpers) {
        this.presentation = presentation;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
    }

    @Operation(summary = "Recent HTTP Request/Response Diagnostics")
    @GetMapping("/diagnostics.json")
    @ResponseBody
    public Page<?> adminDiagnosticsJson(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // TODO: don't select from HUB_OPERATION_SESSION, use the proper VIEW
        final var DSL = udiPrimeJpaConfig.dsl();
        final var result = DSL.selectFrom(HUB_OPERATION_SESSION).offset(page).limit(size).fetch();
        return new PageImpl<>(result.intoMaps(), PageRequest.of(page, size), DSL.fetchCount(HUB_OPERATION_SESSION));
    }

    @GetMapping("/diagnostics")
    @RouteMapping(label = "Diagnostics", siblingOrder = 20)
    public String adminDiagnostics(final Model model, final HttpServletRequest request) {
        model.addAttribute("udiPrimaryDataSrcHealth", udiPrimeJpaConfig.udiPrimaryDataSrcHealth());
        return presentation.populateModel("page/diagnostics", model, request);
    }
}
