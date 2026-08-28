package org.techbd.service.http.hub.prime.ux;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.techbd.service.http.hub.prime.route.RouteMapping;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class PermissionDeniedController {
  private final Presentation presentation;
 
    public PermissionDeniedController(Presentation presentation) {
        this.presentation = presentation;
    }
 
    @RouteMapping(label = "Permission Denied", siblingOrder = 10)
    @GetMapping("/permission-denied")
    public String permissionDenied(
            final Model model,
            final HttpServletRequest request) {
      final String reason = request.getParameter("reason");
      final String message = switch (reason == null ? "" : reason) {
        case "no-active-session" -> "You do not have an active session. Please log in again.";
        case "no-role" -> "There is no role assigned to your account. Please contact an administrator.";
        case "no-tenant" -> "There is no tenant assigned to your account. Please contact an administrator.";
        case "no-permissions" -> "No permissions are assigned to your role. Please contact an administrator.";
        case "missing-permission" -> "You do not have permission to view this page. Please contact an administrator.";
        default -> "You do not have permission to view this page. Please contact an administrator.";
      };
      model.addAttribute("permissionDeniedMessage", message);
 
        return presentation.populateModel(
                "page/permission-denied",
                model,
                request);
    }
}
