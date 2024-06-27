package org.techbd.service.http.hub.prime.ux;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.orchestrate.sftp.SftpManager.TenantSftpEgressSession;
import org.techbd.service.http.aggrid.ColumnVO;
import org.techbd.service.http.aggrid.ServerRowsRequest;
import org.techbd.service.http.aggrid.ServerRowsResponse;
import org.techbd.service.http.aggrid.SqlQueryBuilder;
import org.techbd.service.http.hub.prime.route.RouteMapping;
import org.techbd.udi.UdiPrimeJpaConfig;
import static org.techbd.udi.auto.jooq.ingress.Tables.INTERACTION_HTTP_REQUEST;

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
    @RouteMapping(label = "FHIR via HTTPs", title = "FHIR Interactions via HTTPs")
    public String https(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/https", model, request);
    }

    @GetMapping("/interactions/httpsfailed")
    @RouteMapping(label = "FHIR via HTTPs FAILED", title = "FHIR Interactions via HTTPs (POST to SHIN-NY Failures)")
    public String https_failed(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/httpsfailed", model, request);
    }

    @GetMapping("/interactions/sftp")
    @RouteMapping(label = "CSV via SFTP (egress)", title = "CSV Files via SFTP (egress directory)")
    public String sftp(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/sftp", model, request);
    }

    @Operation(summary = "HTTP Request/Response Interactions for Populating Grid")
    @PostMapping(value = "/support/interaction/http.json", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ServerRowsResponse httpInteractions(final @RequestBody @Nonnull ServerRowsRequest payload) {
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
        // final Map<String, List<String>> pivotValues = Map.of();
        final Map<String, List<String>> pivotValues = /*Map.of();*/ getPivotValues(payload.getPivotCols());

        final var DSL = udiPrimeJpaConfig.dsl();
        final var result = DSL.fetch(new SqlQueryBuilder().createSql(payload, "techbd_udi_ingress.interaction_http_request",
                pivotValues));

        // create response with our results
        return ServerRowsResponse.createResponse(payload, result.intoMaps(), pivotValues);

    }

    @Operation(summary = "HTTP Request/Response Failed Interactions for Populating Grid")
    @PostMapping(value = "/support/interaction/httpsfailed.json", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ServerRowsResponse httpsFailedInteractions(final @RequestBody @Nonnull ServerRowsRequest payload) {

        final Map<String, List<String>> pivotValues = /* Map.of(); */ getPivotValues(payload.getPivotCols());

        final var DSL = udiPrimeJpaConfig.dsl();
        final var result = DSL
                .fetch(new SqlQueryBuilder().createSql(payload, "techbd_udi_ingress.interaction_http_request_failed",
                        pivotValues));

        // create response with our results
        return ServerRowsResponse.createResponse(payload, result.intoMaps(), pivotValues);

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
    public List<?> observeRecentSftpInteractions() {
        return sftpManager.tenantEgressSessions();
    }

    @Operation(summary = "Recent Orchctl Interactions")
    @GetMapping("/interactions/orchctl")
    @RouteMapping(label = "CSV via SFTP (DB)", title = "CSV Files via SFTP (in PostgreSQL DB)")
    public String orchctl(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/orchctl", model, request);
    }

    @Operation(summary = "Individual Orchctl Interactions")
    @GetMapping("/support/interaction/orchctl/{tenantId}/{interactionId}.json")
    @ResponseBody
    public Optional<SftpManager.IndividualTenantSftpEgressSession> observeRecentSftpInteractionsWithId(
            final @PathVariable String tenantId, final @PathVariable String interactionId) {
        return sftpManager.getTenantEgressSession(tenantId, interactionId);
    }

    @Operation(summary = "Orchctl Interactions for Populating Grid")
    @PostMapping(value = "/support/interaction/orchctl.json", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ServerRowsResponse sftpInteractions(final @RequestBody @Nonnull ServerRowsRequest payload) {
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
        final var result = DSL.fetch(new SqlQueryBuilder().createSql(payload, "techbd_udi_ingress.interaction_sftp",
                pivotValues));
        final var rows = result.intoMaps();
        var sftpResult = sftpManager.tenantEgressSessions();
        Map<String, TenantSftpEgressSession> sessionMap = sftpResult.stream()
                .filter(session -> session.getSessionId() != null)
                .collect(Collectors.toMap(
                        TenantSftpEgressSession::getSessionId,
                        session -> session));

        for (final var row : rows) {
            String sessionId = (String) row.get("session_id");
            if (sessionId != null) {
                TenantSftpEgressSession session = sessionMap.get(sessionId);
                if (session != null) {
                    row.put("published_fhir_count", session.getFhirCount());
                    // Add any other fields you need from TenantSftpEgressSession
                }
            }
        }

        // create response with our results
        return ServerRowsResponse.createResponse(payload, rows, pivotValues);

    }

}
