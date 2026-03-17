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
        return "redirect:/settings/profile";
    }

    @RouteMapping(label = "Profile", siblingOrder = 100)
    @GetMapping("/settings/profile")
    public String users(final Model model, final HttpServletRequest request) {
       
        HttpSession session = request.getSession(false);
        Object sessionUser = session != null ? session.getAttribute("authenticatedUser") : null;
        model.addAttribute("sessionUser", sessionUser);

        return presentation.populateModel("page/settings/profile", model, request);
    }

    @RouteMapping(label = "Roles", siblingOrder = 110)
    @GetMapping("/settings/roles")
    public String rolePermissions(final Model model, final HttpServletRequest request) {
      return presentation.populateModel("page/settings/role-permissions", model, request);
} 

  

}