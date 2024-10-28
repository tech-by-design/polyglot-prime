package org.techbd.service.http.hub.prime.ux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.techbd.service.http.hub.prime.route.RoutesTree;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lib.aide.paths.PathsHtml;
import lib.aide.paths.PathsVisuals;

@Controller
@Tag(name = "Tech by Design Hub UX Presentation Shell")
public class ShellController {

    private final ResourceLoader resourceLoader;
    private final Presentation presentation;
    private final PathsHtml<String, RoutesTree.Route> routesHtml = new PathsHtml.Builder<String, RoutesTree.Route>()
            .withIds(node -> "id=\"" + node.absolutePath().replaceAll("[^a-zA-Z0-9]", "-") + "\"")
            .build(); // Use defaults for all other settings

    public ShellController(final ResourceLoader resourceLoader, final Presentation presentation) {
        this.presentation = presentation;
        this.resourceLoader = resourceLoader;
    }

    @Operation(summary = "Application Shell Primary Navigation")
    @GetMapping(value = "/presentation/shell/nav/prime.fragment.html", produces = {"text/html"})
    public ResponseEntity<?> navPrime() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(routesHtml.toHtmlUL(presentation.getRoutesTrees().get("prime")));
    }

    @Operation(summary = "Application Shell Primary Navigation")
    @GetMapping(value = "/presentation/shell/nav/prime-ascii.fragment.html", produces = {"text/html"})
    public ResponseEntity<?> navPrimeAscii() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(new PathsVisuals().asciiTree(presentation.getRoutesTrees().get("prime"), Optional.empty()));
    }

    @Operation(summary = "RoutesTrees")
    @GetMapping(value = "/presentation/shell/routes/{namespace}.json", produces = {"application/json"})
    public ResponseEntity<?> routeTrees(
            @Parameter(description = "Path variable namespace", required = true)
            @PathVariable String namespace) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(presentation.getRoutesTrees().toJson());
    }

    @Operation(summary = "Application Shell JavaScript")
    @GetMapping(value = "/presentation/shell/js/{namespace}.js", produces = {"text/javascript"})
    public ResponseEntity<?> shellJs(
            @Parameter(description = "Path variable namespace", required = true)
            @PathVariable String namespace) {
        final var resourcePath = "classpath:templates/js/" + namespace + ".js";
        final var resource = resourceLoader.getResource(resourcePath);

        if (!resource.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("JavaScript file " + resourcePath + "not found");
        }

        try {
            return ResponseEntity.ok()
                    .body(StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error reading JavaScript file: " + e.getMessage());
        }
    }
}
