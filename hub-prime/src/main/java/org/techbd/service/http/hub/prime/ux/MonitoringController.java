package org.techbd.service.http.hub.prime.ux;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.Record;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.techbd.service.http.PermissionService;
import org.techbd.service.http.hub.prime.route.RouteMapping;
import org.techbd.service.http.hub.prime.ux.mco.MonthlyVolumeComparison;
import org.techbd.service.http.hub.prime.ux.mco.MonthlyVolumeComparison.MonthVolume;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "Tech by Design Hub Monitoring")
public class MonitoringController {
    private static final Logger LOG = LoggerFactory.getLogger(MonitoringController.class.getName());

    /** Route label; must match idp_screens.scr_name (see 011_idempotent_mco_monthly_volume.psql). */
    static final String MONTHLY_VOLUME_LABEL = "Monthly Volume Comparison";
    // public static final ObjectMapper headersOM = JsonMapper.builder()
    // .findAndAddModules()
    // .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    // .build();

    private final Presentation presentation;
    private final DSLContext primaryDslContext;
    private final PermissionService permissionService;
    private DSLContext readerDslContext;

    public MonitoringController(final Presentation presentation,
            @Qualifier("primaryDslContext") final DSLContext primaryDslContext,
            final PermissionService permissionService) throws Exception {
        this.presentation = presentation;
        this.primaryDslContext = primaryDslContext;
        this.permissionService = permissionService;
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
        return "redirect:/monitoring/monthly-summary";
    }

    @RouteMapping(label = "Monthly Summary", title = "Monthly Summary", siblingOrder = 30)
    @GetMapping("/monitoring/monthly-summary")
    public String sourceMonitoring(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/monitoring/monthly-summary", model, request);
    }

    @RouteMapping(label = "Historical Details", title = "Historical Details", siblingOrder = 40)
    @GetMapping("/monitoring/historical-details")
    public String historicalDetails(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/monitoring/historical-details", model, request);
    }

    // siblingOrder 35: between Monthly Summary (30) and Historical Details (40)
    @RouteMapping(label = MONTHLY_VOLUME_LABEL, title = MONTHLY_VOLUME_LABEL, siblingOrder = 35)
    @GetMapping("/monitoring/monthly-volume")
    public String monthlyVolume(final Model model, final HttpServletRequest request) {
        model.addAttribute("defaultMonthsBack", MonthlyVolumeComparison.DEFAULT_MONTHS_BACK);
        model.addAttribute("maxMonthsBack", MonthlyVolumeComparison.MAX_MONTHS_BACK);
        return presentation.populateModel("page/monitoring/monthly-volume", model, request);
    }

    @GetMapping(value = "/api/monitoring/mco/monthly-volume", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getMcoMonthlyVolume(
            @RequestParam(name = "reporting_month", required = false) final String reportingMonth,
            @RequestParam(name = "months_back", defaultValue = "" + MonthlyVolumeComparison.DEFAULT_MONTHS_BACK) final int monthsBack,
            final HttpServletRequest request) {

        if (!permissionService.isAllowedForRole(MONTHLY_VOLUME_LABEL, request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You do not have permission to view this report."));
        }

        final YearMonth selectedMonth;
        try {
            selectedMonth = MonthlyVolumeComparison.parseReportingMonth(reportingMonth);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "reporting_month must be MM-YYYY."));
        }
        if (!MonthlyVolumeComparison.isValidMonthsBack(monthsBack)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "months_back must be between 1 and " + MonthlyVolumeComparison.MAX_MONTHS_BACK + "."));
        }

        final String monthParam = MonthlyVolumeComparison.toReportingMonth(selectedMonth);
        try {
            final List<MonthVolume> volumes = getDsl()
                    .fetch("SELECT * FROM mco_data.get_mco_monthly_volume(?, ?)", monthParam, monthsBack)
                    .map(MonitoringController::toMonthVolume);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(MonthlyVolumeComparison.build(volumes, selectedMonth, monthsBack));
        } catch (Exception e) {
            LOG.error("Error fetching MCO monthly volume for reporting_month={} months_back={}",
                    monthParam, monthsBack, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unable to load the monthly volume report."));
        }
    }

    static MonthVolume toMonthVolume(final Record r) {
        final LocalDate monthDate = r.get("reporting_month_date", LocalDate.class);
        final Long batchCount = r.get("qualifying_batch_count", Long.class);
        return new MonthVolume(
                r.get("tenant_id", String.class),
                r.get("mco_code", String.class),
                r.get("mco_name", String.class),
                monthDate == null ? null : YearMonth.from(monthDate),
                r.get("status", String.class),
                r.get("total_records", Long.class),
                r.get("processed_count", Long.class),
                r.get("errored_count", Long.class),
                r.get("resubmitted_count", Long.class),
                batchCount == null ? 0L : batchCount);
    }

    @GetMapping(value = "/api/monitoring/mco/historical-records", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getMcoHistoricalRecords(
            @RequestParam(name = "mco_name") final String mcoName,
            @RequestParam(name = "start_reporting_month", required = false) final String startReportingMonth,
            @RequestParam(name = "end_reporting_month", required = false) final String endReportingMonth,
            @RequestParam(name = "last_three_month", defaultValue = "false") final boolean lastThreeMonth) {

        try {
            List<Map<String, Object>> rows = getDsl()
                    .fetch(
                            "SELECT * FROM mco_data.get_mco_historical_records(?, ?, ?, ?)",
                            mcoName.trim(),
                            startReportingMonth == null || startReportingMonth.isBlank()
                                    ? null
                                    : startReportingMonth.trim(),
                            endReportingMonth == null || endReportingMonth.isBlank()
                                    ? null
                                    : endReportingMonth.trim(),
                            lastThreeMonth)
                    .intoMaps();

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(rows);

        } catch (Exception e) {
            LOG.error(
                    "Error fetching MCO historical records for mco={} start={} end={} lastThreeMonth={}",
                    mcoName,
                    startReportingMonth,
                    endReportingMonth,
                    lastThreeMonth,
                    e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of());
        }
    }

    @GetMapping(value = "/api/monitoring/mco/error-logs/{batchDetailsId}", produces = MediaType.APPLICATION_JSON_VALUE)
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

            rows.forEach(row -> row.replaceAll((key, value) -> value instanceof org.jooq.JSONB
                    ? ((org.jooq.JSONB) value).data()
                    : value));

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
