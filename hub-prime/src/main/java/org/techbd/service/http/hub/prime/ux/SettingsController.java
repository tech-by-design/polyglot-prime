package org.techbd.service.http.hub.prime.ux;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

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
    public String docs() {
        return "redirect:/settings/role-access-management";
    }

    // @RouteMapping(label = "Profile", siblingOrder = 100)
    // @GetMapping("/settings/profile")
    // public String users(final Model model, final HttpServletRequest request) {

    //     HttpSession session = request.getSession(false);
    //     Object sessionUser = session != null ? session.getAttribute("authenticatedUser") : null;
    //     model.addAttribute("sessionUser", sessionUser);

    //     return presentation.populateModel("page/settings/profile", model, request);
    // }

    @RouteMapping(label = "Role Access Management", siblingOrder = 110)
    @GetMapping("/settings/role-access-management")
    public String rolePermissions(final Model model, final HttpServletRequest request) {
        HttpSession session = request.getSession(false);

      Boolean configAccess = session != null
                ? (Boolean) session.getAttribute("configAccess")
                : false;

        if (!Boolean.TRUE.equals(configAccess)) {
            return "redirect:/profile/profile";
        }
        return presentation.populateModel("page/settings/role-access-management", model, request);
    }

}