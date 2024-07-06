package org.techbd.service.http.hub.prime.ux;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.techbd.service.DocResourcesService;
import org.techbd.service.http.hub.prime.route.RouteMapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lib.aide.paths.Paths;
import lib.aide.paths.PathsHtml;
import lib.aide.resource.Nature;
import lib.aide.resource.Provenance;
import lib.aide.resource.Resource;
import lib.aide.resource.ResourceProvenance;

@Controller
@Tag(name = "TechBD Hub Docs API")
public class DocsController {
    public static final ObjectMapper headersOM = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

    public class NamingStrategy {
        private final String[] captionKeyNames = { "caption", "title" };

        public String caption(
                Paths<String, ? extends ResourceProvenance<? extends Provenance, Resource<? extends Nature, ?>>>.Node node) {
            // attributes may be filled by PathElaboration, loadTechBdHubDocsCatalog or any
            // other resource hooks
            for (final var key : captionKeyNames) {
                var tryAttr = node.getAttribute(key);
                if (tryAttr.isPresent())
                    return tryAttr.orElseThrow().toString();
            }
            return node.basename().orElse("UNKNOWN BASENAME");
        }
    }

    private final Presentation presentation;
    private final DocResourcesService drs;

    public DocsController(final Presentation presentation, final DocResourcesService drs) throws Exception {
        this.presentation = presentation;
        this.drs = drs;
    }

    @RouteMapping(label = "Documentation", siblingOrder = 50)
    @GetMapping("/docs")
    public String docs() {
        return "redirect:/docs/swagger-ui";
    }

    @RouteMapping(label = "TechBD Hub", title = "Business and Technology Documentation", siblingOrder = 0)
    @GetMapping("/docs/techbd-hub")
    public String techbdHub(final Model model, final HttpServletRequest request) {
        model.addAttribute("sidebarNS", drs.getNamingStrategy());
        model.addAttribute("sidebarItems", drs.sidebarItems());
        return presentation.populateModel("page/docs/techbd-hub", model, request);
    }

    @HxRequest
    @GetMapping("/docs/techbd-hub/sidebar/content")
    public ResponseEntity<?> techbdHubSidebar() {
        // this is provided only as an example in case sidebars are large and we want to
        // load them via secondary HTMx fetch instead of primary HTML; if a sidebar is
        // small then just generate the HTML via Thymeleaf in the primary HTML page.
        final var ph = new PathsHtml.Builder<String, ResourceProvenance<?, Resource<? extends Nature, ?>>>()
                .withIds(node -> "id=\"" + node.absolutePath().replaceAll("[^a-zA-Z0-9]", "-") + "\"")
                .build(); // Use defaults for all other settings

        final var pathsHtml = ph.toHtmlUL(drs.sidebarItems(), Optional.empty());
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(pathsHtml);
    }

    @GetMapping("/docs/techbd-hub/resource/content")
    public ResponseEntity<?> techbdHubResource(@RequestParam String path) throws JsonProcessingException {
        final var found = drs.sidebarItems().findNode(path);
        if (found.isPresent()) {
            final var node = found.orElseThrow();
            if (node.payload().isPresent()) {
                final var resource = node.payload().orElseThrow().resource();
                final var ns = drs.getNamingStrategy();
                final var breadcrumbs = node.ancestors().stream()
                        .filter(n -> !n.absolutePath().equals("docs")) // we don't want the relative "root"
                        .map(n -> Map.of("text", ns.caption(n)))
                        .collect(Collectors.toList());
                breadcrumbs.add(Map.of("href", "/docs/techbd-hub", "text", "TechBD Hub"));

                return ResponseEntity.ok()
                        .header("Resource-Breadcrumbs", headersOM.writeValueAsString(breadcrumbs))
                        .header("Resource-Title", ns.title(node))
                        .contentType(MediaType.valueOf(resource.nature().mimeType()))
                        .body(resource.content());
            }
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                    .body("No resource found in path's payload: " + path);

        } else {
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body("Invalid path: " + path);
        }
    }

    @RouteMapping(label = "OpenAPI UI", title = "OpenAPI Documentation", siblingOrder = 10)
    @GetMapping("/docs/swagger-ui")
    public String swaggerUI(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/docs/swagger-ui", model, request);
    }

    @RouteMapping(label = "Health Information", title = "Health Information", siblingOrder = 20)
    @GetMapping("/docs/health-info")
    public String healthInformation(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/docs/health-info", model, request);
    }

    @RouteMapping(label = "Announcements", title = "Announcements", siblingOrder = 30)
    @GetMapping("/docs/announcements")
    public String announcements(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/docs/announcements", model, request);
    }

    @RouteMapping(label = "Project", title = "Project", siblingOrder = 40)
    @GetMapping("/docs/project")
    public String projects(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/docs/project", model, request);
    }
}
