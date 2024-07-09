package org.techbd.service.http.hub.prime.ux;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.aggrid.ColumnVO;
import org.techbd.service.http.hub.prime.route.RouteMapping;
import org.techbd.udi.UdiPrimeJpaConfig;
import static org.techbd.udi.auto.jooq.ingress.Tables.INTERACTION_HTTP_REQUEST;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;


@Controller
@Tag(name = "TechBD Hub Interactions UX API")
public class InteractionsController {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(InteractionsController.class.getName());

    private final Presentation presentation;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;
    private final SftpManager sftpManager;

    public InteractionsController(final Presentation presentation,
            final UdiPrimeJpaConfig udiPrimeJpaConfig,
            final SftpManager sftpManager) {
        this.presentation = presentation;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
        this.sftpManager = sftpManager;
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

    public Map<String, List<String>> getPivotValues(List<ColumnVO> pivotCols) {
        Map<String, List<String>> pivotValues = new HashMap<>();
        for (ColumnVO column : pivotCols) {
            List<String> values = getValuesForField(column.getField());
            pivotValues.put(column.getField(), values);
        }
        return pivotValues;
    }

    public List<String> getValuesForField(String field) {
        List<String> values = Arrays.asList(field);
        return values;
    }

    @Operation(summary = "Specific HTTP Request/Response Interaction which is assumed to exist")
    @GetMapping("/support/interaction/{interactionId}.json")
    @ResponseBody
    public Object httpInteraction(final @PathVariable String interactionId) {
        final var DSL = udiPrimeJpaConfig.dsl();
        final var result = DSL.selectFrom(INTERACTION_HTTP_REQUEST)
                .where(INTERACTION_HTTP_REQUEST.INTERACTION_ID.eq(interactionId))
                .fetch();
        return result.intoMaps();
    }

    @Operation(summary = "Recent SFTP Interactions")
    @GetMapping("/support/interaction/sftp/recent.json")
    @ResponseBody
    public List<?> observeRecentSftpInteractions(final @RequestParam(defaultValue = "10") int limitMostRecent) {
        return sftpManager.tenantEgressSessions(limitMostRecent);
    }

    @Operation(summary = "Recent Orchctl Interactions")
    @GetMapping("/interactions/orchctl")
    @RouteMapping(label = "CSV via SFTP (DB)", title = "CSV Files via SFTP (in PostgreSQL DB)", siblingOrder = 50)
    public String orchctl(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/orchctl", model, request);
    }
}
