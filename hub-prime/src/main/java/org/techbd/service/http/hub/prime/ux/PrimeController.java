package org.techbd.service.http.hub.prime.ux;

import org.ocpsoft.prettytime.PrettyTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.hub.prime.route.RouteMapping;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "TechBD Hub UX API")
public class PrimeController {
    private static final Logger LOG = LoggerFactory.getLogger(PrimeController.class.getName());

    private final Presentation presentation;
    private final SftpManager sftpManager;

    public PrimeController(final Presentation presentation,
            final SftpManager sftpManager) {
        this.presentation = presentation;
        this.sftpManager = sftpManager;
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
            final var content = sftpManager.tenantEgressContent(account.get());
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

    @RouteMapping(label = "Documentation", siblingOrder = 50)
    @GetMapping("/docs")
    public String docs() {
        return "redirect:/docs/swagger-ui";
    }

    @RouteMapping(label = "OpenAPI UI", title="OpenAPI Documentation", siblingOrder = 0)
    @GetMapping("/docs/swagger-ui")
    public String swaggerUI(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/docs/swagger-ui", model, request);
    }

    @RouteMapping(label = "Health Information", title="Health Information", siblingOrder = 1)
    @GetMapping("/docs/health-info")
    public String healthInformation(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/docs/health-info", model, request);
    }

    @RouteMapping(label = "Announcements", title="Announcements", siblingOrder = 2)
    @GetMapping("/docs/announcements")
    public String announcements(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/docs/announcements", model, request);
    }

    @GetMapping(value = "/experiment/{page}.html")
    @Profile(value = "sandbox")
    public String navPrimeDebug(@PathVariable String page, final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/experiments/%s".formatted(page), model, request);
    }
}
