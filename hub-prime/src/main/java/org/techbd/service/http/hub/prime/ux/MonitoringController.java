package org.techbd.service.http.hub.prime.ux;

import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.techbd.service.http.hub.prime.route.RouteMapping;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "Tech by Design Hub Monitoring")
public class MonitoringController {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(MonitoringController.class.getName());
    // public static final ObjectMapper headersOM = JsonMapper.builder()
    //         .findAndAddModules()
    //         .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    //         .build();

    private final Presentation presentation;
    private final DSLContext primaryDslContext;
    private DSLContext readerDslContext;

    public MonitoringController(final Presentation presentation,
            @Qualifier("primaryDslContext") final DSLContext primaryDslContext) throws Exception {
        this.presentation = presentation;
        this.primaryDslContext = primaryDslContext;
    }

    @Autowired(required = false)
    public void setReaderDslContext(@Qualifier("secondaryDslContext") final DSLContext readerDslContext) {
        this.readerDslContext = readerDslContext;
    }

    private DSLContext getDsl() {
        if (readerDslContext != null) {
            return readerDslContext;
        }
        return primaryDslContext;
    }

    @RouteMapping(label = "Monitoring", siblingOrder = 80)
    @GetMapping("/monitoring")
    public String docs() {
        return "redirect:/monitoring/source-monitoring";
    }

    @RouteMapping(label = "Source Monitoring", title = "Source Monitoring", siblingOrder = 30)
    @GetMapping("/monitoring/source-monitoring")
    public String sourceMonitoring(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/monitoring/source-monitoring", model, request);
    }

    @RouteMapping(label = "Error Monitoring", title = "Error Monitoring", siblingOrder = 40)
    @GetMapping("/monitoring/error-monitoring")
    public String errorMonitoring(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/monitoring/error-monitoring", model, request);
    }

@GetMapping(value = "/api/monitoring/mco/error-logs/{batchDetailsId}",
        produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<List<Map<String, Object>>> getMcoErrorLogs(
        @PathVariable final String batchDetailsId) {

    if (batchDetailsId == null || batchDetailsId.isBlank()) {
        return ResponseEntity.badRequest().body(List.of());
    }

    try {
        List<Map<String, Object>> rows = getDsl()
                .fetch(
                        "SELECT * FROM mco_data.get_mco_error_logs(?::text)",
                        batchDetailsId)
                .intoMaps();

        rows.forEach(row -> row.replaceAll((key, value) ->
                value instanceof org.jooq.JSONB
                        ? ((org.jooq.JSONB) value).data()
                        : value
        ));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(rows);

    } catch (Exception e) {
        LOG.error("Error fetching MCO error logs for batchDetailsId={}",
                batchDetailsId, e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(List.of());
    }
}
}
