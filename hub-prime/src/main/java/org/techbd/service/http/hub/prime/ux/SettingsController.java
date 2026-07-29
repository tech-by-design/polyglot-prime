package org.techbd.service.http.hub.prime.ux;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.techbd.service.http.hub.prime.route.RouteMapping;

@Controller
@Tag(name = "User Settings")
public class SettingsController {

    private final Presentation presentation;

    public SettingsController(Presentation presentation) {
        this.presentation = presentation;
    }

        @RouteMapping(label = "Settings", siblingOrder = 90)
        @GetMapping("/settings")
        public String settings(HttpServletRequest request) {

            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();

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

}