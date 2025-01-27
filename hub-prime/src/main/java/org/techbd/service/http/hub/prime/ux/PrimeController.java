package org.techbd.service.http.hub.prime.ux;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jooq.impl.DSL;
import org.ocpsoft.prettytime.PrettyTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.hub.prime.route.RouteMapping;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.Tables;

import com.nimbusds.oauth2.sdk.util.CollectionUtils;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lib.aide.tabular.JooqRowsSupplier;

@Controller
@Tag(name = "Tech by Design Hub UX API")
public class PrimeController {
    private static final Logger LOG = LoggerFactory.getLogger(PrimeController.class.getName());

    private final UdiPrimeJpaConfig udiPrimeJpaConfig;

    private final Presentation presentation;
    private final SftpManager sftpManager;

    public PrimeController(final Presentation presentation, final UdiPrimeJpaConfig udiPrimeJpaConfig,
            final SftpManager sftpManager) {
        this.presentation = presentation;
        this.sftpManager = sftpManager;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
    }

    @GetMapping("/home")
    @RouteMapping(label = "Home", siblingOrder = 0)
    public String home(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/home", model, request);
    }

    @GetMapping("/")
    public String index() {
        return "login/login";
    }

    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/github");
    }

    @GetMapping(value = "/admin/cache/tenant-sftp-egress-content/clear")
    @CacheEvict(value = { SftpManager.TENANT_EGRESS_CONTENT_CACHE_KEY,
            SftpManager.TENANT_EGRESS_SESSIONS_CACHE_KEY }, allEntries = true)
    public ResponseEntity<?> emptyTenantEgressCacheOnDemand() {
        LOG.info("emptying tenant-sftp-egress-content (on demand)");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("emptying tenant-sftp-egress-content");
    }

    @GetMapping(value = "/dashboard/stat/sftp/most-recent-egress/{tenantId}.{extension}", produces = {
            "application/json", "text/html" })
    public ResponseEntity<?> handleRequest(@PathVariable String tenantId, @PathVariable String extension) {
        final var account = sftpManager.configuredTenant(tenantId);
        if (account.isPresent()) {
            final var content = sftpManager.tenantEgressContent(account.get(), 10);
            final var mre = content.mostRecentEgress();

            if ("html".equalsIgnoreCase(extension)) {
                String timeAgo = mre.map(zonedDateTime -> new PrettyTime().format(zonedDateTime)).orElse("None");
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                        .body(content.error() == null
                                ? "<span title=\"%d sessions found, most recent %s\">%s</span>".formatted(
                                        content.directories().length,
                                        mre,
                                        timeAgo)
                                : "<span title=\"No directories found in %s\">⚠️</span>".formatted(content.sftpUri()));
            } else if ("json".equalsIgnoreCase(extension)) {
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mre);
            } else {
                return ResponseEntity.badRequest().build();
            }
        } else {
            if ("html".equalsIgnoreCase(extension)) {
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                        .body("Unknown tenantId '%s'".formatted(tenantId));
            } else if ("json".equalsIgnoreCase(extension)) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.badRequest().build();
            }
        }
    }

    @GetMapping(value = "/dashboard/stat/fhir/most-recent/{tenantId}.{extension}", produces = {
            "application/json", "text/html" })
    public ResponseEntity<?> handleFHRequest(@PathVariable String tenantId, @PathVariable String extension) {
        String schemaName = "techbd_udi_ingress";
        String viewName = "interaction_recent_fhir";

        // Fetch the result using the dynamically determined table and column; if
        // jOOQ-generated types were found, automatic column value mapping will occur
        final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                viewName);
        List<Map<String, Object>> recentInteractions = udiPrimeJpaConfig.dsl().selectFrom(typableTable.table())
                .where(DSL.upper(typableTable.column("tenant_id").cast(String.class)).eq(tenantId.toUpperCase()))
                .fetch()
                .intoMaps();

        if (recentInteractions != null && recentInteractions.size() > 0) {

            String mre = recentInteractions.get(0).get("interaction_created_at").toString();

            String interactionCount = recentInteractions.get(0).get("interaction_count").toString();

            String formattedTime = getrecentInteractioString(mre);

            if ("html".equalsIgnoreCase(extension)) {
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                        .body(mre.length() > 0
                                ? "<span title=\"%s sessions found, most recent %s \">%s</span>".formatted(
                                        interactionCount,
                                        convertToEST(mre),
                                        formattedTime)
                                : "<span title=\"No data found in %s\">⚠️</span>".formatted(tenantId));

            } else if ("json".equalsIgnoreCase(extension)) {
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mre);
            } else {
                return ResponseEntity.badRequest().build();
            }
        } else {
            if ("html".equalsIgnoreCase(extension)) {
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                        .body("<span title=\"No data found in %s\">⚠️</span>".formatted(tenantId));
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
            List<Map<String, Object>> fhirSubmission = udiPrimeJpaConfig.dsl().selectFrom(typableTable.table())
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

    @GetMapping(value = "/dashboard/stat/fhir/mermaid")
    public ResponseEntity<List<InteractionData>> fetchFHIRSMermaidDiagram(Model model) {
        String schemaName = "techbd_udi_ingress";
        String viewName = "fhir_needs_attention_dashbaord";

        // Initialize list to hold the results
        List<InteractionData> interactions = new ArrayList<>();

        final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName, viewName);

        // Query the view and fetch the results
        List<Map<String, Object>> fhirSubmission = udiPrimeJpaConfig.dsl().selectFrom(typableTable.table())
                .fetch()
                .intoMaps(); 
        // Check if data is available
        if (fhirSubmission != null && !fhirSubmission.isEmpty()) {
            Map<String, Object> data = fhirSubmission.get(0); 

            // Populate the list with data 
            interactions.add(new InteractionData("healthelink_total_submissions",
                    getSafeIntegerValue(data.get("healthelink_total_submissions"))));
            interactions.add(new InteractionData("healtheconnections_total_submissions",
                    getSafeIntegerValue(data.get("healtheconnections_total_submissions"))));
            interactions.add(new InteractionData("healthix_total_submissions",
                    getSafeIntegerValue(data.get("healthix_total_submissions"))));
            interactions.add(new InteractionData("grrhio_total_submissions",
                    getSafeIntegerValue(data.get("grrhio_total_submissions"))));
            interactions.add(new InteractionData("hixny_total_submissions",
                    getSafeIntegerValue(data.get("hixny_total_submissions"))));

            interactions.add(new InteractionData("healthelink_shinny_datalake_submissions",
                    getSafeIntegerValue(data.get("healthelink_shinny_datalake_submissions"))));
            interactions.add(new InteractionData("healtheconnections_shinny_datalake_submissions",
                    getSafeIntegerValue(data.get("healtheconnections_shinny_datalake_submissions"))));                    
            interactions.add(new InteractionData("healthix_shinny_datalake_submissions",
                    getSafeIntegerValue(data.get("healthix_shinny_datalake_submissions"))));
            interactions.add(new InteractionData("grrhio_shinny_datalake_submissions",
                    getSafeIntegerValue(data.get("grrhio_shinny_datalake_submissions"))));
            interactions.add(new InteractionData("hixny_shinny_datalake_submissions",
                    getSafeIntegerValue(data.get("hixny_shinny_datalake_submissions"))));        
        } else {
            // Default values if no data found 
            interactions.add(new InteractionData("healthelink_total_submissions", 0));
            interactions.add(new InteractionData("healtheconnections_total_submissions", 0));
            interactions.add(new InteractionData("healthix_total_submissions", 0));
            interactions.add(new InteractionData("grrhio_total_submissions", 0));
            interactions.add(new InteractionData("hixny_total_submissions", 0));

            interactions.add(new InteractionData("healthelink_shinny_datalake_submissions", 0));
            interactions.add(new InteractionData("healtheconnections_shinny_datalake_submissions", 0));
            interactions.add(new InteractionData("healthix_shinny_datalake_submissions", 0));
            interactions.add(new InteractionData("grrhio_shinny_datalake_submissions", 0));
            interactions.add(new InteractionData("hixny_shinny_datalake_submissions", 0));

        }

        // Return the data with HTTP status OK
        return ResponseEntity.ok().body(interactions);
    }

    public class InteractionData {
        private String label;
        private int count;

        // Constructor
        public InteractionData(String label, int count) {
            this.label = label;
            this.count = count;
        }

        // Getters and Setters
        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    private int getSafeIntegerValue(Object value) {
        if (value == null || value.toString().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            LOG.error("Error parsing integer from value: {}", value, e);
            return 0;
        }
    }

    @GetMapping(value = "/dashboard/stat/csv/most-recent/{tenantId}.{extension}", produces = {
        "application/json", "text/html" })
    public ResponseEntity<?> CSVviaHTTPsubmission(@PathVariable String tenantId, @PathVariable String extension) {
        String schemaName = "techbd_udi_ingress";
        String viewName = "interaction_recent_csv_https";

        // Fetch the result using the dynamically determined table and column; if
        // jOOQ-generated types were found, automatic column value mapping will occur
        final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                viewName);
        List<Map<String, Object>> recentInteractions = udiPrimeJpaConfig.dsl().selectFrom(typableTable.table())
                .where(DSL.upper(typableTable.column("tenant_id").cast(String.class)).eq(tenantId.toUpperCase()))
                .fetch()
                .intoMaps();

        if (recentInteractions != null && recentInteractions.size() > 0) {

            String mre = recentInteractions.get(0).get("interaction_created_at").toString();

            String interactionCount = recentInteractions.get(0).get("interaction_count").toString();

            String formattedTime = getrecentInteractioString(mre);

            if ("html".equalsIgnoreCase(extension)) {
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                        .body(mre.length() > 0
                                ? "<span title=\"%s sessions found, most recent %s \">%s</span>".formatted(
                                        interactionCount,
                                        convertToEST(mre),
                                        formattedTime)
                                : "<span title=\"No data found in %s\">⚠️</span>".formatted(tenantId));

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


    @GetMapping(value = "/dashboard/stat/ccda/most-recent/{tenantId}.{extension}", produces = {
        "application/json", "text/html" })
    public ResponseEntity<?> CCDAviaHTTPsubmission(@PathVariable String tenantId, @PathVariable String extension) {
        String schemaName = "techbd_udi_ingress";
        String viewName = "interaction_recent_ccda_https";

        // Fetch the result using the dynamically determined table and column; if
        // jOOQ-generated types were found, automatic column value mapping will occur
        final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                viewName);
        List<Map<String, Object>> recentInteractions = udiPrimeJpaConfig.dsl().selectFrom(typableTable.table())
                .where(DSL.upper(typableTable.column("tenant_id").cast(String.class)).eq(tenantId.toUpperCase()))
                .fetch()
                .intoMaps();

        if (recentInteractions != null && recentInteractions.size() > 0) {

            String mre = recentInteractions.get(0).get("interaction_created_at").toString();

            String interactionCount = recentInteractions.get(0).get("interaction_count").toString();

            String formattedTime = getrecentInteractioString(mre);

            if ("html".equalsIgnoreCase(extension)) {
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                        .body(mre.length() > 0
                                ? "<span title=\"%s sessions found, most recent %s \">%s</span>".formatted(
                                        interactionCount,
                                        convertToEST(mre),
                                        formattedTime)
                                : "<span title=\"No data found in %s\">⚠️</span>".formatted(tenantId));

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

}
