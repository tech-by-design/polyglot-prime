package org.techbd.service.http.hub.prime.ux;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.PermissionService;
import org.techbd.service.http.hub.prime.route.RouteMapping;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "Tech by Design Hub Interactions UX API",
        description = "Tech by Design Hub Interactions UX API")
public class InteractionsController {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(InteractionsController.class.getName());

    private final Presentation presentation;
    private final SftpManager sftpManager;
    private final DSLContext primaryDslContext;
    private final DSLContext secondaryDslContext;
    private final PermissionService permissionService; 
   public InteractionsController(
        final Presentation presentation,
        final SftpManager sftpManager,
        @Qualifier("primaryDslContext") DSLContext primaryDslContext,
        @Autowired(required = false)
        @Qualifier("secondaryDslContext") DSLContext secondaryDslContext,
        final PermissionService permissionService) {

    this.presentation = presentation;
    this.sftpManager = sftpManager;
    this.primaryDslContext = primaryDslContext;
    this.secondaryDslContext = secondaryDslContext;
    this.permissionService = permissionService;
}

    @GetMapping("/interactions")
    @RouteMapping(label = "Interactions", siblingOrder = 20)
    public String observeInteractions(HttpServletRequest request) {
    
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof OAuth2AuthenticationToken token
            && token.getPrincipal() instanceof DefaultOAuth2User user) {

        String authProvider = (String) user.getAttribute("authProvider");

        if ("github".equalsIgnoreCase(authProvider)) {
            return "redirect:/interactions/httpsfhir";
        }
    }

    return "redirect:" +
            permissionService.getDefaultRoute("/interactions", request);
    }

    @GetMapping("/interactions/httpsfhir")
    @RouteMapping(label = "FHIR Data", title = "FHIR Data", siblingOrder = 20)
    public String httpsfhir(final Model model, final HttpServletRequest request) {
       model.addAttribute("writerInfo",
            getSessionInfo(primaryDslContext, "WRITER"));

    model.addAttribute("readerInfo",
            getSessionInfo(secondaryDslContext, "READER"));
        return presentation.populateModel("page/interactions/httpsfhir", model, request);
    }

    @GetMapping("/interactions/httpsfailed")
    @RouteMapping(label = "FHIR Failed Submissions (Data Lake)", title = "FHIR Failed Submissions (Data Lake)", siblingOrder = 30)
    public String httpsFailed(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/httpsfailed", model, request);
    }

    @GetMapping("/interactions/httpscsv")
    @RouteMapping(label = "CSV Submissions", title = "CSV Submissions", siblingOrder = 40)
    public String httpscsv(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/httpscsv", model, request);
    }   

    @GetMapping("/interactions/httpsccda")
    @RouteMapping(label = "CCDA Submissions", title = "CCDA Submissions", siblingOrder = 50)
    public String httpsccda(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/httpsccda", model, request);
    }       
    @GetMapping("/interactions/httpshl7v2")
    @RouteMapping(label = "HL7v2 Submissions", title = "HL7v2 Submissions", siblingOrder = 60)
    public String httpshl7v2(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/httpshl7v2", model, request);
    } 
    // @Operation(summary = "Recent SFTP Interactions")
    // @GetMapping("/support/interaction/sftp/recent.json")
    // @ResponseBody
    // public List<?> observeRecentSftpInteractions(
    //         @Parameter(description = "Optional variable to mention the number of entries to be fetched for each tenant. If no value is specified, 10 entries will be taken by default.", required = false)
    //         final @RequestParam(defaultValue = "10") int limitMostRecent) {
    //     return sftpManager.tenantEgressSessions(limitMostRecent);
    // }

    @GetMapping("/interactions/https")
    @RouteMapping(label = "Hub Interactions", title = "Hub Interactions", siblingOrder = 80)
    public String https(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/https", model, request);
    }

    @GetMapping("/interactions/observe")
    @RouteMapping(label = "Performance Overview", title = "Performance Overview", siblingOrder = 90)
    public String osberve(final Model model, final HttpServletRequest request) {

     Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
     if (authentication instanceof OAuth2AuthenticationToken token
            && token.getPrincipal() instanceof DefaultOAuth2User user) {

        String authProvider = (String) user.getAttribute("authProvider");

        if ("github".equalsIgnoreCase(authProvider)) {
        return "redirect:/interactions/observe/api-performance";        
        }
    }
    return "redirect:" +
            permissionService.getDefaultRoute("/interactions/observe", request);
    }

    @GetMapping("/interactions/observe/api-performance")
    @RouteMapping(label = "API", title = "API", siblingOrder = 10)
    public String apiPerformance(final Model model, final HttpServletRequest request) {        
        return presentation.populateModel("page/interactions/api-performance", model, request);
    }         

    @GetMapping("/interactions/observe/user-interaction-performance")
    @RouteMapping(label = "User Interactions", title = "User Interactions", siblingOrder = 20)
    public String userInteractionPerformance(final Model model, final HttpServletRequest request) {        
        return presentation.populateModel("page/interactions/user-interaction-performance", model, request);
    }         

    @GetMapping("/interactions/provenance")
    @RouteMapping(label = "Provenance", title = "Provenance", siblingOrder = 100)
    public String provenance(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/provenance", model, request);
    }

    @GetMapping("/interactions/user")
    @RouteMapping(label = "User Sessions", title = "User", siblingOrder = 110)
    public String user(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/interactions/user", model, request);
    }
    private String getSessionInfo(DSLContext dsl, String name) {

    if (dsl == null) {
        return name + " : NOT CONFIGURED";
    }

    var r = dsl.fetchOne("""
        SELECT
            current_user,
            session_user,
            current_setting('jwt.claims.tenants', true)
        """);

    return String.format(
            "%s -> current_user=%s | session_user=%s | tenants=%s",
            name,
            r.get(0),
            r.get(1),
            r.get(2)
    );
}
}
