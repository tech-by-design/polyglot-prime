package org.techbd.service.http.hub.prime.ux;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lib.aide.tabular.JooqRowsSupplier;
import org.jooq.DSLContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.techbd.service.http.hub.prime.route.RouteMapping;
import org.techbd.udi.auto.jooq.ingress.Tables;

@Controller
@Tag(name = "User Settings")
public class SettingsController {

    private final Presentation presentation;
    private final DSLContext primaryDslContext;
    private final DSLContext readerDslContext;

    public SettingsController(
            Presentation presentation,
            DSLContext primaryDslContext) {

        this.presentation = presentation;
        this.primaryDslContext = primaryDslContext;
        this.readerDslContext = null;
    }

    private DSLContext getDsl() {
        if (readerDslContext != null) {
            // LOG.info("READER INSTANCE - Exceuting Query");
            return readerDslContext;
        }
        // LOG.info("WRITER INSTANCE - Exceuting Query");
        return primaryDslContext;
    }

    @RouteMapping(label = "Settings", siblingOrder = 90)
    @GetMapping("/settings")
    public String settings(HttpServletRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof OAuth2AuthenticationToken token
                && token.getPrincipal() instanceof DefaultOAuth2User user) {

            String authProvider = user.getAttribute("authProvider");

            // GitHub users should only see FHIR Rules
            if ("github".equalsIgnoreCase(authProvider)) {
                return "redirect:/settings/fhir-rules";
            }
        }

        HttpSession session = request.getSession(false);

        Boolean configAccess = session != null
                ? (Boolean) session.getAttribute("configAccess")
                : false;

        if (Boolean.TRUE.equals(configAccess)) {
            return "redirect:/settings/role-access-management";
        }

        return "redirect:/profile/profile";
    }

    @RouteMapping(label = "Role Access Management", siblingOrder = 100)
    @GetMapping("/settings/role-access-management")
    public String rolePermissions(final Model model, final HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        Boolean configAccess = session != null
                ? (Boolean) session.getAttribute("configAccess")
                : false;

        if (!Boolean.TRUE.equals(configAccess)) {
            return "redirect:/settings/fhir-rules";
        }

        return presentation.populateModel(
                "page/settings/role-access-management",
                model,
                request);
    }

    @RouteMapping(label = "FHIR Rules", siblingOrder = 110)
    @GetMapping("/settings/fhir-rules")
    public String fhirRules(final Model model,
            final HttpServletRequest request) {
        return presentation.populateModel("page/diagnostics/fhir-rules", model, request);
    }

    @RouteMapping(label = "Tenants", siblingOrder = 120)
    @GetMapping("/settings/tenants")
    public String tenant(final Model model, final HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        Boolean configAccess = session != null
                ? (Boolean) session.getAttribute("configAccess")
                : false;
        if (!Boolean.TRUE.equals(configAccess)) {
            return "redirect:/settings/fhir-rules";
        }

        return presentation.populateModel("page/settings/tenants", model, request);
    }

    @GetMapping("/api/tenants/active")
    @ResponseBody
    public Object getActiveTenants() {
        final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(
                Tables.class,
                "techbd_udi_ingress",
                "active_tenants");

        return getDsl()
                .selectFrom(typableTable.table())
                .fetch()
                .intoMaps();
    }

}