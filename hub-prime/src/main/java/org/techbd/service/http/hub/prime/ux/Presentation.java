package org.techbd.service.http.hub.prime.ux;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.techbd.conf.Configuration;
import org.techbd.service.http.GitHubUserAuthorizationFilter;
import org.techbd.service.http.SandboxHelpers;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.service.http.hub.prime.route.RoutesTree;
import org.techbd.service.http.hub.prime.route.RoutesTree.HtmlAnchor;
import org.techbd.service.http.hub.prime.route.RoutesTrees;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class Presentation {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(Presentation.class.getName());

    private final boolean sandboxConsoleEnabled;
    private final RoutesTrees routesTrees;
    private final AppConfig appConfig;
    private final SandboxHelpers sboxHelpers;

    private final String navPrimeTreeName = "prime";
    private final RoutesTree navPrimeTree;
    private final List<HtmlAnchor> navPrimeLinks;

    public Presentation(final Environment env, final RoutesTrees routesTrees, final AppConfig appConfig,
            final SandboxHelpers sboxHelpers) {
        this.sandboxConsoleEnabled = env.matchesProfiles("sandbox");
        this.routesTrees = routesTrees;
        this.appConfig = appConfig;
        this.sboxHelpers = sboxHelpers;

        navPrimeTree = routesTrees.get(navPrimeTreeName);
        if (navPrimeTree != null) {
            final var navPrimeTreeRoot = navPrimeTree.root();
            if (navPrimeTreeRoot != null) {
                // Stream through the children of the root node, sorting them by their sibling
                // order if present. If the sibling order is not present, treat it as
                // Integer.MAX_VALUE for sorting purposes (push to end). Then, map each child to
                // a Map containing its "href" and "text" if the payload is present. If the
                // payload is not present, map to an empty Map which means "iterim node".

                navPrimeLinks = navPrimeTreeRoot.children().stream()
                        .sorted(Comparator.comparing(child -> child.payload()
                                .flatMap(payload -> payload.siblingOrder())
                                .orElse(Integer.MAX_VALUE)))
                        .map(child -> new HtmlAnchor(child))
                        .collect(Collectors.toList());
            } else {
                navPrimeLinks = List.of();
            }
        } else {
            navPrimeLinks = List.of();
        }
    }

    public RoutesTrees getRoutesTrees() {
        return routesTrees;
    }

    protected String populateModel(final String templateName, final Model model, final HttpServletRequest request) {
        // make the request, authUser available to templates
        model.addAttribute("appVersion", this.appConfig.getVersion());
        model.addAttribute("req", request);
        model.addAttribute("authUser", GitHubUserAuthorizationFilter.getAuthenticatedUser(request));
        model.addAttribute("appVersion", this.appConfig.getVersion());

        // active route, siblings, ancestors (breadcrumbs) available for navigation
        final var activeRoute = navPrimeTree.findNode(navPrimeTreeName + request.getRequestURI());
        model.addAttribute("navPrime", navPrimeLinks);
        model.addAttribute("activeRoute", activeRoute);

        // if we're running in a developer sandbox and want the templates being
        // displayed via the web to be editable
        final var sandboxConsoleConf = new HashMap<>();
        if (sboxHelpers.isEditorAvailable()) {
            final var canonicalTmplName = "templates/" + templateName + ".html";
            final var targetResource = getClass().getClassLoader().getResource(canonicalTmplName);
            final var targetFsPath = targetResource != null ? targetResource.getFile() : null;
            final var srcFsPath = targetFsPath != null
                    ? targetFsPath.replace("target/classes", "src/main/resources")
                    : null;
            final var editUrl = srcFsPath != null ? sboxHelpers.getEditorUrlFromAbsolutePath(srcFsPath) : null;
            final var template = Map.of("supplied", templateName, "canonical", canonicalTmplName, "targetFsPath",
                    targetFsPath,
                    "srcFsPath", srcFsPath, "editUrl", editUrl);
            model.addAttribute("template", template);
            sandboxConsoleConf.put("template", template);
        }
        if (sandboxConsoleEnabled) {
            sandboxConsoleConf.put("enabled", true);
            model.addAttribute("sandboxConsoleConf", Configuration.objectMapper.valueToTree(sandboxConsoleConf));
        }

        // the actual template to render
        return templateName;
    }

}
