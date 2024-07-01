package org.techbd.service.http.hub.prime.ux;

import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.vfs2.VFS;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.techbd.service.http.hub.prime.route.RouteMapping;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lib.aide.paths.PathsHtml;
import lib.aide.resource.Nature;
import lib.aide.resource.Resource;
import lib.aide.resource.ResourceProvenance;
import lib.aide.resource.collection.Resources;
import lib.aide.resource.collection.VfsResources;
import lib.aide.resource.content.ResourceFactory;

@Controller
@Tag(name = "TechBD Hub Docs API")
public class DocsController {
    private final Presentation presentation;

    public DocsController(final Presentation presentation) {
        this.presentation = presentation;
    }

    @RouteMapping(label = "Documentation", siblingOrder = 50)
    @GetMapping("/docs")
    public String docs() {
        return "redirect:/docs/swagger-ui";
    }

    @RouteMapping(label = "TechBD Hub", title = "Business and Technology Documentation", siblingOrder = 0)
    @GetMapping("/docs/techbd-hub")
    public String techbdHub(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/docs/techbd-hub", model, request);
    }

    @HxRequest
    @GetMapping("/docs/techbd-hub/sidebar/content")
    public ResponseEntity<?> techbdHubSidebar(final Model model, final HttpServletRequest request) throws Exception {
        // TODO: this is an expensive, non-performant way to build the sidebar;
        //       need to cache as much as possible
        final var rf = new ResourceFactory();
        final var fsManager = VFS.getManager();
        final var docsHome = VfsResources.findRelativeDirectory("docs.techbd.org/src/content", Optional.empty());
        final var rootFileObject = fsManager.resolveFile("file://" + docsHome.orElse(Path.of(System.getProperty("user.dir"))));

        final var vfsResourcesSupplier = new VfsResources(rf, rootFileObject.getURI(), rootFileObject);
        final var builder = new Resources.Builder<String, Resource<? extends Nature, ?>>()
                .withSupplier(vfsResourcesSupplier);
        final var catalog = builder.build();

        final var ph = new PathsHtml.Builder<String, ResourceProvenance<?, Resource<? extends Nature, ?>>>()
                .withIds(node -> "id=\"" + node.absolutePath().replaceAll("[^a-zA-Z0-9]", "-") + "\"")
                .build(); // Use defaults for all other settings

        final var pathsHtml = ph.toHtmlUL(catalog.getPaths().getFirst(), Optional.empty());
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(pathsHtml);
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
}
