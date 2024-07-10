package org.techbd.service.http.hub.prime.ux;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.SandboxHelpers;
import org.techbd.service.http.aggrid.ColumnVO;
import org.techbd.service.http.aggrid.ServerRowsRequest;
import org.techbd.service.http.aggrid.ServerRowsResponse;
import org.techbd.service.http.aggrid.SqlQueryBuilder;
import org.techbd.service.http.hub.prime.route.RouteMapping;
import org.techbd.udi.UdiPrimeJpaConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
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

    @GetMapping("/diagnostics")
    @RouteMapping(label = "Diagnostics", siblingOrder = 20)
    public String adminDiagnostics() {
        return "redirect:/diagnostics/sftp";
    }

    @GetMapping("/diagnostics/sftp")
    @RouteMapping(label = "CSV via SFTP(DB)", title = "CSV Diagnostics via SFTPs", siblingOrder = 10)
    public String https(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/diagnostics/sftp", model, request);
    }

    
    @Operation(summary = "SFTP Request/Response Diagnostics for Populating Grid")
    @PostMapping(value = "/support/diagnostics/sftp.json", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ServerRowsResponse sftpDiagnostics(final @RequestBody @Nonnull ServerRowsRequest payload) {
        // TODO: figure out how to write dynamic queries in jOOQ
        // final var DSL = udiPrimeJpaConfig.dsl();
        // final var result =
        // DSL.selectFrom(INTERACTION_HTTP).offset(payload.getStartRow())
        // .limit(payload.getEndRow() - payload.getStartRow() + 1).fetch();
        // return ServerRowsResponse.createResponse(payload, result.intoMaps(), null);

        final Map<String, List<String>> pivotValues = getPivotValues(payload.getPivotCols());

        final var DSL = udiPrimeJpaConfig.dsl();
        final var result = DSL.fetch(new SqlQueryBuilder().createSql(payload, "techbd_udi_ingress.orch_session_diagnostics",
                pivotValues));

        // create response with our results
        return ServerRowsResponse.createResponse(payload, result.intoMaps(), pivotValues);

    }
}
