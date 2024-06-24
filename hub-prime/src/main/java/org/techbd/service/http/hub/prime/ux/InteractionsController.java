package org.techbd.service.http.hub.prime.ux;

import static org.techbd.udi.auto.jooq.ingress.Tables.INTERACTION_HTTP;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.techbd.conf.Configuration;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.aggrid.ServerRowsRequest;
import org.techbd.service.http.aggrid.ServerRowsResponse;
import org.techbd.service.http.aggrid.SqlQueryBuilder;
import org.techbd.service.http.hub.prime.route.RouteMapping;
import org.techbd.udi.UdiPrimeJpaConfig;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
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
    @RouteMapping(label = "FHIR via HTTPs")
    public String https(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/https", model, request);
    }

    @GetMapping("/interactions/sftp")
    @RouteMapping(label = "CSV via SFTP (egress)")
    public String sftp(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/sftp", model, request);
    }

    @Operation(summary = "HTTP Request/Response Interactions for Populating Grid")
    @PostMapping(value = "/support/interaction/{intrNature}.json", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Object httpInteractions(final @PathVariable String intrNature,
            final @RequestBody @Nonnull ServerRowsRequest payload) throws JsonProcessingException {
        // TODO: figure out how to write dynamic queries in jOOQ
        // final var DSL = udiPrimeJpaConfig.dsl();
        // final var result =
        // DSL.selectFrom(INTERACTION_HTTP).offset(payload.getStartRow())
        // .limit(payload.getEndRow() - payload.getStartRow() + 1).fetch();
        // return ServerRowsResponse.createResponse(payload, result.intoMaps(), null);

        // TODO: obtain the pivot values from the DB for the requested pivot columns
        // see
        // https://github.com/ag-grid/ag-grid-server-side-oracle-example/src/main/java/com/ag/grid/enterprise/oracle/demo/dao/TradeDao.java
        // final var pivotValues = getPivotValues(request.getPivotCols());
        final Map<String, List<String>> pivotValues = Map.of();

        final var DSL = udiPrimeJpaConfig.dsl();
        final var result = DSL
                .fetch(new SqlQueryBuilder().createSql(payload, "techbd_udi_ingress.interaction_" + intrNature,
                        pivotValues));
        final var rows = result.intoMaps();
        for (final var row : rows) {
            // this is a JSONB and might be large so don't send it even if it was requested
            // since we'll get it in /support/interaction/{interactionId}.json if required;
            // also since SqlQueryBuilder().createSql() is custom SQL, org.jooq.JSONB type
            // will not be able to be serialized by Jackson anyway.
            row.remove("request_payload");
        }

        // create response with our results
        return Configuration.objectMapper.writeValueAsString(ServerRowsResponse.createResponse(payload, rows, pivotValues));
    }

    @Operation(summary = "Specific HTTP Request/Response Interaction which is assumed to exist")
    @GetMapping("/support/interaction/{interactionId}.json")
    @ResponseBody
    public Map<String, Object> httpInteraction(final @PathVariable String interactionId) {
        final var DSL = udiPrimeJpaConfig.dsl();
        final var result = DSL.select(INTERACTION_HTTP.REQUEST_PAYLOAD)
                .from(INTERACTION_HTTP)
                .where(INTERACTION_HTTP.INTERACTION_ID.eq(interactionId))
                .fetchSingle();
        return result.intoMap();
    }

    @Operation(summary = "Recent SFTP Interactions")
    @GetMapping("/support/interaction/sftp/recent.json")
    @ResponseBody
    public List<?> observeRecentSftpInteractions() {
        return sftpManager.tenantEgressSessions();
    }
}
