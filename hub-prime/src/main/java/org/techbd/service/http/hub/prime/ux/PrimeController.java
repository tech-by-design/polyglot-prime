package org.techbd.service.http.hub.prime.ux;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.hub.prime.route.RouteMapping;
import org.techbd.udi.auto.jooq.ingress.Tables;

import com.nimbusds.oauth2.sdk.util.CollectionUtils;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lib.aide.tabular.JooqRowsSupplier;

@Controller
@Tag(name = "Tech by Design Hub UX API")
public class PrimeController {

    @Value("${AUTH_PROVIDER:github}")
    private String authProvider;

    private static final Logger LOG = LoggerFactory.getLogger(PrimeController.class.getName());

    private final DSLContext primaryDslContext;
    private DSLContext readerDslContext;

    private final Presentation presentation;

    public PrimeController(final Presentation presentation,@Qualifier("primaryDslContext") DSLContext primaryDslContext,
            final SftpManager sftpManager) {
        this.presentation = presentation;
        this.primaryDslContext = primaryDslContext;   
    }

    @Autowired(required = false)
    public void setReaderDslContext(@Qualifier("secondaryDslContext") DSLContext readerDslContext) {
        LOG.info("READER INSTANCE CONFIGURED SUCCESSFULLY!!");
        this.readerDslContext = readerDslContext;
    }
    
    private DSLContext getDsl() {
        if (readerDslContext != null) {
            // LOG.info("READER INSTANCE - Exceuting Query");
            return readerDslContext;
        }
        // LOG.info("WRITER INSTANCE - Exceuting Query");
        return primaryDslContext;
    }
    @GetMapping("/home")
    @RouteMapping(label = "Dashboard", siblingOrder = 0)
    public String home(final Model model, final HttpServletRequest request) {
        try {
            final var metrics = getMcoDashboardMetrics(null);
            model.addAttribute("metrics", metrics);
            model.addAttribute("selectedReportingMonth", metrics.getOrDefault("selected_reporting_month", ""));
            model.addAttribute("recentReportingMonth", metrics.getOrDefault("recent_reporting_month", ""));
        } catch (Exception e) {
            LOG.error("Error loading MCO dashboard metrics for home page", e);
        }
        return presentation.populateModel("page/home", model, request);
    }

    @GetMapping("/api/dashboard/mco/metrics")
    public ResponseEntity<Map<String, Object>> getMcoDashboardMetricsEndpoint(
            @RequestParam(required = false, name = "reportingMonth") String reportingMonth) {
        try {
            Map<String, Object> metrics = getMcoDashboardMetrics(reportingMonth);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(metrics);
        } catch (Exception e) {
            LOG.error("Error retrieving MCO dashboard metrics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Unable to load MCO dashboard metrics"));
        }
    }

    private Map<String, Object> getMcoDashboardMetrics(String reportingMonth) {
        if (reportingMonth != null && reportingMonth.isBlank()) {
            reportingMonth = null;
        }
        final var result = getDsl().fetch(
                "select * from mco_data.get_mco_dashboard_metrics(?)",
                reportingMonth)
                .intoMaps();

        if (result.isEmpty()) {
            return Map.ofEntries(
                    Map.entry("selected_reporting_month", reportingMonth == null ? "" : reportingMonth),
                    Map.entry("recent_reporting_month", ""),
                    Map.entry("is_selected", false),
                    Map.entry("total_mco", 0L),
                    Map.entry("total_files_received", 0L),
                    Map.entry("inprogress", 0L),
                    Map.entry("fully_processed", 0L),
                    Map.entry("errored", 0L),
                    Map.entry("records_received", 0L),
                    Map.entry("records_processed", 0L),
                    Map.entry("records_with_errors", 0L));
        }
        return result.get(0);
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("authProvider", authProvider.toLowerCase());
        return "login/login";
    }

    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
       response.sendRedirect("/oauth2/authorization/" + authProvider.toLowerCase());
    }


    @GetMapping(value = "/dashboard/stat/fhir/most-recent/{tenantId}.{extension}", produces = {
            "application/json", "text/html" })
    public ResponseEntity<?> handleFHRequest(@PathVariable String tenantId, @PathVariable String extension) {
        String schemaName = "techbd_udi_ingress";
        String viewName = "interaction_recent_widget_new";

        // Fetch the result using the dynamically determined table and column; if
        // jOOQ-generated types were found, automatic column value mapping will occur
        final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                viewName);
        List<Map<String, Object>> recentInteractions = getDsl().selectFrom(typableTable.table())
                 //.where(DSL.upper(typableTable.column("tenant_id").cast(String.class)).eq(tenantId.toUpperCase())) 
                 .where( typableTable.column("tenant_id").cast(String.class).eq(tenantId)
                         .and(typableTable.column("widget_name").cast(String.class).eq("FHIR"))
                       )
                .fetch()
                .intoMaps();

        if (recentInteractions != null && recentInteractions.size() > 0) {

            String mre = recentInteractions.get(0).get("last_updated_at").toString();

            //  String interactionCount = recentInteractions.get(0).get("interaction_count").toString();

            String formattedTime = getrecentInteractioString(mre);

            if ("html".equalsIgnoreCase(extension)) {
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                        .body(mre.length() > 0
                                ? "<span title=\"Most recent %s \">%s</span>".formatted( 
                                        convertToEST(mre),
                                        formattedTime)
                                : "<span title=\"No data found in %s\">??</span>".formatted(tenantId));

            } else if ("json".equalsIgnoreCase(extension)) {
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mre);
            } else {
                return ResponseEntity.badRequest().build();
            }
        } else {
            if ("html".equalsIgnoreCase(extension)) {
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                        .body("<span title=\"No data found in %s\" class=\"text-lg\">No records available</span>".formatted(tenantId));
            } else if ("json".equalsIgnoreCase(extension)) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.badRequest().build();
            }
        }
    }

    private String getrecentInteractioString(String mreTime) {

        // Parse mre to ZonedDateTime
        ZonedDateTime mre = ZonedDateTime.parse(mreTime);

        // Get the current time
        ZonedDateTime now = ZonedDateTime.now();

        // Calculate the difference in seconds
        long secondsElapsed = ChronoUnit.SECONDS.between(mre, now);

        // Define ranges in seconds for different time intervals
        long[] rangesInSeconds = {
                3600 * 24 * 365, // years
                3600 * 24 * 30, // months
                3600 * 24 * 7, // weeks
                3600 * 24, // days
                3600, // hours
                60 // minutes
        };

        // Corresponding labels for each range
        String[] rangeLabels = {
                "year",
                "month",
                "week",
                "day",
                "hour",
                "minute"
        };

        // Formatter for displaying relative time
        String formattedTime = null;
        for (int i = 0; i < rangesInSeconds.length; i++) {
            if (Math.abs(secondsElapsed) >= rangesInSeconds[i]) {
                long delta = Math.round((double) secondsElapsed / rangesInSeconds[i]);
                formattedTime = delta + " " + rangeLabels[i] + (delta != 1 ? "s" : "") + " ago";
                break;
            }
        }

        // Handle seconds if within the minute range
        if (formattedTime == null) {
            formattedTime = Math.abs(secondsElapsed) + " second" + (Math.abs(secondsElapsed) != 1 ? "s" : "")
                    + " ago";
        }

        return formattedTime;

    }

    private String convertToEST(String inputTime) {
        // Parse the input time string to a ZonedDateTime
        ZonedDateTime inputDateTime = ZonedDateTime.parse(inputTime, DateTimeFormatter.ISO_ZONED_DATE_TIME);

        // Convert the ZonedDateTime to the EST time zone
        ZonedDateTime estDateTime = inputDateTime.withZoneSameInstant(ZoneId.of("America/New_York"));

        // Format the ZonedDateTime to a string in the desired format
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return estDateTime.format(formatter);
    }

    @GetMapping(value = "/dashboard/stat/fhir/fhir-submission-summary", produces = "text/html")
    public String fetchFHIRsubmissionSummary(Model model) {
        String schemaName = "techbd_udi_ingress";
        String viewName = "fhir_submission_summary";
        final String defaultValue = "0";
        String totalSubmissions = defaultValue;
        String pendingSubmissions = defaultValue;
        String acceptedSubmissions = defaultValue;
        String rejectedSubmissions = defaultValue;
        try {
            final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                    viewName);
            List<Map<String, Object>> fhirSubmission = getDsl().selectFrom(typableTable.table())
                    .fetch()
                    .intoMaps();
            if (CollectionUtils.isNotEmpty(fhirSubmission)) {
                Map<String, Object> data = fhirSubmission.get(0);
                totalSubmissions = data.getOrDefault("total_submissions", defaultValue).toString();
                pendingSubmissions = data.getOrDefault("pending_submissions", defaultValue).toString();
                acceptedSubmissions = data.getOrDefault("accepted_submissions", defaultValue).toString();
                rejectedSubmissions = data.getOrDefault("rejected_submissions", defaultValue).toString();
            }
        } catch (Exception e) {
            LOG.error("Error fetching FHIR interactions", e);
        }
        model.addAttribute("totalSubmissions", totalSubmissions);
        model.addAttribute("pendingSubmissions", pendingSubmissions);
        model.addAttribute("acceptedSubmissions", acceptedSubmissions);
        model.addAttribute("rejectedSubmissions", rejectedSubmissions);
        return "fragments/interactions :: serverTextStat";
    }

    @GetMapping(value = "/api/dashboard/mco/most-recent", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getMcoMostRecentTransactions() {
        try {
            final String schemaName = "mco_data";
            final String viewName = "mco_most_recent_transactions";

            final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                    viewName);
            List<Map<String, Object>> rows = getDsl().selectFrom(typableTable.table()).fetch().intoMaps();

            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rows);
        } catch (Exception e) {
            LOG.error("Error fetching MCO most recent transactions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    @GetMapping(value = "/api/dashboard/qe/most-recent", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getQeMostRecentTransactions() {
        try {
            final String schemaName = "techbd_udi_ingress";
            final String viewName = "qe_most_recent_transactions";

            final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                    viewName);
            List<Map<String, Object>> rows = getDsl().selectFrom(typableTable.table()).fetch().intoMaps();

            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(rows);
        } catch (Exception e) {
            LOG.error("Error fetching QE most recent transactions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }



}
