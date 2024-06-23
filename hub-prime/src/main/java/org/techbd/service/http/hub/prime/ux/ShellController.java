package org.techbd.service.http.hub.prime.ux;

import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.techbd.service.http.hub.prime.route.RoutesTree;
import org.techbd.service.http.hub.prime.route.RoutesTree.HtmlAnchor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lib.aide.paths.PathsHtml;
import lib.aide.paths.PathsVisuals;

@Controller
@Tag(name = "TechBD Hub UX Presentation Shell")
public class ShellController {
    private final Presentation presentation;
    private final PathsHtml<String, RoutesTree.Route> routesHtml = new PathsHtml.Builder<String, RoutesTree.Route>()
            .withIds(node -> "id=\"" + node.absolutePath().replaceAll("[^a-zA-Z0-9]", "-") + "\"")
            .build(); // Use defaults for all other settings

    public ShellController(final Presentation presentation) {
        this.presentation = presentation;
    }

    @Operation(summary = "Application Shell Primary Navigation")
    @GetMapping(value = "/presentation/shell/nav/prime.fragment.html", produces = { "text/html" })
    public ResponseEntity<?> navPrime() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(routesHtml.toHtmlUL(presentation.getRoutesTrees().get("prime")));
    }

    @Operation(summary = "Application Shell Primary Navigation")
    @GetMapping(value = "/presentation/shell/nav/prime-ascii.fragment.html", produces = { "text/html" })
    public ResponseEntity<?> navPrimeAscii() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(new PathsVisuals().asciiTree(presentation.getRoutesTrees().get("prime"),
                        Optional.of((node, paths) -> {
                            final var anchor = new HtmlAnchor(node);
                            return "[%s] {%s: %s}".formatted(node.absolutePath(), anchor.text(),
                                    anchor.href().orElse("(interim route, no href)"));
                        })));
    }

    @Operation(summary = "RoutesTrees")
    @GetMapping(value = "/presentation/shell/routes/{namespace}.json", produces = { "application/json" })
    public ResponseEntity<?> routeTrees(@PathVariable String namespace) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(presentation.getRoutesTrees().toJson());
    }
}
