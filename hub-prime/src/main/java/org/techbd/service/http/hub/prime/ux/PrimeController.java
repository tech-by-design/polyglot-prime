package org.techbd.service.http.hub.prime.ux;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.hub.prime.route.RouteMapping;
import org.techbd.udi.auto.jooq.ingress.Tables;

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

    public PrimeController(final Presentation presentation,
            @Qualifier("primaryDslContext") DSLContext primaryDslContext,
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
            final YearMonth defaultReportingMonth = getDefaultReportingMonth();
            final String endReportingMonth = formatReportingMonth(defaultReportingMonth);
            final String startReportingMonth = formatReportingMonth(defaultReportingMonth.minusMonths(2));
            // defulat last 3 month initially
            final var metrics = getMcoDashboardMetrics(startReportingMonth, endReportingMonth);

            model.addAttribute("metrics", metrics); 

        } catch (Exception e) {
            LOG.error("Error loading MCO dashboard metrics for home page", e);
        }
        return presentation.populateModel("page/home", model, request);
    }

    /**
     * Determines the default reporting month based on the reporting window.
     *
     * 20th - 4th  : Current month
     * 5th  - 19th : Previous month
     */
    private YearMonth getDefaultReportingMonth() {
        final LocalDate today = LocalDate.now();
        final int dayOfMonth = today.getDayOfMonth();

        if (dayOfMonth >= 5 && dayOfMonth <= 19) {
            return YearMonth.from(today).minusMonths(1);
        }

        return YearMonth.from(today);
    }

    /**
     * Formats reporting month as MM-yyyy.
     */
    private String formatReportingMonth(final YearMonth month) {
        return month.format(DateTimeFormatter.ofPattern("MM-yyyy"));
    }

    @GetMapping("/api/dashboard/mco/metrics")
    public ResponseEntity<Map<String, Object>> getMcoDashboardMetricsEndpoint(
            @RequestParam(required = false, name = "p_start_reporting_month") String pStartReportingMonth,
            @RequestParam(required = false, name = "p_end_reporting_month") String pEndReportingMonth) {
        try {
            Map<String, Object> metrics = getMcoDashboardMetrics(pStartReportingMonth, pEndReportingMonth);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(metrics);
        } catch (Exception e) {
            LOG.error("Error retrieving MCO dashboard metrics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unable to load MCO dashboard metrics"));
        }
    }

    private Map<String, Object> getMcoDashboardMetrics(String startReportingMonth, String endReportingMonth) {
        final String startParam = (startReportingMonth != null && !startReportingMonth.isBlank())
                ? startReportingMonth.trim()
                : null;
        final String endParam = (endReportingMonth != null && !endReportingMonth.isBlank()) ? endReportingMonth.trim()
                : null;

        final var result = getDsl().fetch(
                "select * from mco_data.get_mco_dashboard_metrics1(?, ?)",
                startParam,
                endParam)
                .intoMaps();

        if (result.isEmpty()) {
            return Map.ofEntries(
                    Map.entry("selected_reporting_month", ""), 
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
