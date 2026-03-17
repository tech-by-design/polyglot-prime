package org.techbd.service.http.hub.prime.ux;

import java.util.Collections;
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
import org.techbd.service.http.FusionAuthUserAuthorizationFilter;
import org.techbd.service.http.PermissionService;
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

    private static final String navPrimeTreeName = "prime";
    private final RoutesTree navPrimeTree;
    private final List<HtmlAnchor> navPrimeLinks;
    private final PermissionService permissionService;

    public Presentation(final Environment env, final RoutesTrees routesTrees, final AppConfig appConfig,
            final SandboxHelpers sboxHelpers ,PermissionService permissionService) {
        this.sandboxConsoleEnabled = env.matchesProfiles("sandbox");
        this.routesTrees = routesTrees;
        this.appConfig = appConfig;
        this.sboxHelpers = sboxHelpers;
        this.permissionService = permissionService;

        navPrimeTree = routesTrees.get(navPrimeTreeName);
        if (navPrimeTree != null) {
            final var navPrimeTreeRoot = navPrimeTree.root();
            // Stream through the children of the root node, sorting them by their sibling
            // order if present. If the sibling order is not present, treat it as
            // Integer.MAX_VALUE for sorting purposes (push to end). Then, map each child to
            // a Map containing its "href" and "text" if the payload is present. If the
            // payload is not present, map to an empty Map which means "iterim node".

            // the root's first child is `/` so we look under there
            navPrimeLinks = navPrimeTreeRoot.children().getFirst().children().stream()
                    .sorted(Comparator.comparing(child -> child.payload()
                            .flatMap(payload -> payload.siblingOrder())
                            .orElse(Integer.MAX_VALUE)))
                    .map(child -> new HtmlAnchor(child))
                    .collect(Collectors.toList());
        } else {
            navPrimeLinks = List.of();
        }
    }

    public RoutesTrees getRoutesTrees() {
        return routesTrees;
    }

    public String populateModel(final String templateName, final Model model, final HttpServletRequest request) {
        // make the request, authUser available to templates
        model.addAttribute("appVersion", this.appConfig.getVersion());
        model.addAttribute("req", request);
        model.addAttribute("authUser", FusionAuthUserAuthorizationFilter.getAuthenticatedUser(request));      
        List<HtmlAnchor> allowedLinks = permissionService.filterLinksByRole(navPrimeLinks ,request);
        model.addAttribute("navPrime", allowedLinks);
        model.addAttribute("navPrimeTree", navPrimeTree);
        registerActiveRoute(model, request);

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
                    targetFsPath != null ? targetFsPath : "%s not found".formatted(canonicalTmplName),
                    "srcFsPath", srcFsPath != null ? srcFsPath : "%s not found".formatted(canonicalTmplName), "editUrl",
                    editUrl != null ? editUrl : "%s not found".formatted(canonicalTmplName));
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

    protected void registerActiveRoute(final Model model, final HttpServletRequest request) {
        final var activeRouteFound = navPrimeTree.findNode(request.getRequestURI());
        if (activeRouteFound.isEmpty()) {
            return;
        }

        final var activeRoute = activeRouteFound.orElseThrow();
        final var activeRoutePayload = activeRoute.payload().orElseThrow();
        final var activeRoutePath = "/" + activeRoute.absolutePath(false);
        final var activeRouteParentPath = "/" + activeRoute.parent().absolutePath(false);
        final var isHomePage = request.getRequestURI().equals("/home");

        final var breadcrumbs = activeRoute.ancestors().stream()
                .map(child -> new HtmlAnchor(child).intoMap())
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> {
                            Collections.reverse(list);
                            return list.size() > 1 ? list.subList(1, list.size()) : List.of();
                        }));

        final var siblings = activeRoute.siblings(true).stream()
                .sorted(Comparator.comparing(sibling -> sibling.payload()
                        .flatMap(payload -> payload.siblingOrder())
                        .orElse(Integer.MAX_VALUE)))
                .map(sibling -> new HtmlAnchor(sibling))
                .filter(link -> permissionService.isAllowedForRole(link.text(), request))
                .map(HtmlAnchor::intoMap)                
                .collect(Collectors.toList());

        final var parentSiblings = activeRoute.parent() != null ? activeRoute.parent().siblings(true).stream()
                .sorted(Comparator.comparing(sibling -> sibling.payload()
                        .flatMap(payload -> payload.siblingOrder())
                        .orElse(Integer.MAX_VALUE)))
                .map(sibling -> new HtmlAnchor(sibling))
                .filter(link -> permissionService.isAllowedForRole(link.text() , request))
                .map(HtmlAnchor::intoMap)                
                .collect(Collectors.toList()) : List.of();

        model.addAttribute("isHomePage", isHomePage);
        model.addAttribute("activeRoute", activeRoute);
        model.addAttribute("activeRoutePath", activeRoutePath);
        model.addAttribute("activeRouteParentPath", activeRouteParentPath);
        model.addAttribute("activeRouteTitle", activeRoutePayload.title().orElse(activeRoutePayload.label()));
        model.addAttribute("siblingLinks", isHomePage ? List.of() : siblings);
        model.addAttribute("parentSiblingLinks", isHomePage ? List.of() : parentSiblings);
        model.addAttribute("breadcrumbs", breadcrumbs);
    }

}
