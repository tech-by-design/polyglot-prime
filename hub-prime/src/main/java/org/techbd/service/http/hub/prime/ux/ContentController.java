package org.techbd.service.http.hub.prime.ux;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.PermissionService;
import org.techbd.service.http.SandboxHelpers;
import org.techbd.service.http.hub.prime.route.RouteMapping;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "Tech by Design Hub Contents")
public class ContentController {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DataQualityController.class.getName());

    private final Presentation presentation;
    private final PermissionService permissionService;
    public ContentController(final Presentation presentation,
            @SuppressWarnings("PMD.UnusedFormalParameter") final SftpManager sftpManager,
            @SuppressWarnings("PMD.UnusedFormalParameter") final SandboxHelpers sboxHelpers,
            final PermissionService permissionService) {
        this.presentation = presentation;
        this.permissionService = permissionService;
    }

    public List<String> getValuesForField(String field) {
        List<String> values = Arrays.asList(field);
        return values;
    }

    @GetMapping("/content")
    @RouteMapping(label = "Content", siblingOrder = 9)
    public String fhirContent( final HttpServletRequest request) {
    
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof OAuth2AuthenticationToken token
            && token.getPrincipal() instanceof DefaultOAuth2User user) {

        String authProvider = (String) user.getAttribute("authProvider");

        if ("github".equalsIgnoreCase(authProvider)) {
        return "redirect:/content/screenings";
        }
    }
    return "redirect:" +
           permissionService.getDefaultRoute(
                "/content",
                request
           );
    }

    @GetMapping("/content/screenings")
    @RouteMapping(label = "Screenings", title = "Screenings", siblingOrder = 10)
    public String screenings(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/content/screenings", model, request);
    }

    @GetMapping("/content/patients")
    @RouteMapping(label = "Patients", title = "Patients", siblingOrder = 20)
    public String patients(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/content/patients", model, request);
    }

    @GetMapping("/content/organizations")
    @RouteMapping(label = "Organizations", title = "Organizations", siblingOrder = 30)
    public String organizations(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/content/organizations", model, request);
    }

    @GetMapping("/content/scn")
    @RouteMapping(label = "SCN", title = "SCN", siblingOrder = 40)
    public String scn(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/content/scn", model, request);
    }    

    @GetMapping("/content/hrsn-data-tracker")
    @RouteMapping(label = "HRSN Data Tracker", title = "HRSN Data Tracker", siblingOrder = 50)
    public String hrsnDataTracker(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/content/hrsn-data-tracker", model, request);
    }      

}
