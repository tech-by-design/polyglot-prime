package org.techbd.service.http.hub.prime.ux;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.techbd.service.http.hub.prime.route.RouteMapping;

@Controller
@Tag(name = "User Profile")
public class ProfileController {

    private final Presentation presentation;

    public ProfileController(Presentation presentation) {
        this.presentation = presentation;
    }

    @RouteMapping(label = "Profile", siblingOrder = 90)
    @GetMapping("/profile")
    public String docs() {
        return "redirect:/profile/profile";
    }    

    @RouteMapping(label = "Profile", siblingOrder = 100)
    @GetMapping("/profile/profile")
    public String profile(final Model model, final HttpServletRequest request) {

        return presentation.populateModel("page/profile/profile", model, request);
    }
}