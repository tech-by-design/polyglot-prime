package org.techbd.service.http.hub.prime.ux;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List; // Ensure this import is present
import java.util.Map;
import org.ocpsoft.prettytime.PrettyTime;
import java.time.temporal.ChronoUnit;

import jakarta.servlet.http.HttpServletResponse;
import lib.aide.tabular.JooqRowsSupplier;
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

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "TechBD Hub UX API")
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
                .where(typableTable.column("tenant_id").eq(tenantId)) 
                .fetch()
                .intoMaps();

        System.out.println("recentInteractionshh" + recentInteractions.get(0).get("interaction_created_at"));

        if (recentInteractions.size() > 0) {
            // final var mre = recentInteractions.get(0).get("interaction_created_at");
            // Example value of input (mre)
            String mre = recentInteractions.get(0).get("interaction_created_at").toString();
            String formattedTime = getrecentInteractioString(mre);

            System.out.println("recentInteractions" + recentInteractions.get(0).get("interaction_created_at"));
            if ("html".equalsIgnoreCase(extension)) { 
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                        .body(mre.length() > 0 ?"<span title=\"%d sessions found, most recent %s \">%s</span>".formatted(
                                recentInteractions.size(),
                                mre,
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
                        .body("Unknown tenantId '%s'".formatted(tenantId));
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
                long delta = Math.round(secondsElapsed / rangesInSeconds[i]);
                formattedTime = delta + " " + rangeLabels[i] + (delta != 1 ? "s" : "") + " ago";
                break;
            }
        }

        // Handle seconds if within the minute range
        if (formattedTime == null) {
            formattedTime = Math.round(secondsElapsed) + " second" + (Math.abs(secondsElapsed) != 1 ? "s" : "")
                    + " ago";
        }

        return formattedTime;

    }
 
}
