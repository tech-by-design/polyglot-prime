package org.techbd.service.http.hub.prime.ux;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.vfs2.VFS;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.techbd.service.http.hub.prime.route.RouteMapping;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lib.aide.paths.Paths;
import lib.aide.paths.PathsHtml;
import lib.aide.resource.Nature;
import lib.aide.resource.Provenance;
import lib.aide.resource.Resource;
import lib.aide.resource.ResourceProvenance;
import lib.aide.resource.collection.Resources;
import lib.aide.resource.collection.VfsResources;
import lib.aide.resource.content.MarkdownResource.UntypedMarkdownNature;
import lib.aide.resource.content.ResourceFactory;
import lib.aide.resource.nature.FrontmatterNature.Components;

@Controller
@Tag(name = "TechBD Hub Docs API")
public class DocsController {
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
    private Resources<String, Resource<? extends Nature, ?>> techBdHubDocsCatalog;
    private NamingStrategy sidebarNS = new NamingStrategy();

    public DocsController(final Presentation presentation) throws Exception {
        this.presentation = presentation;
        loadTechBdHubDocsCatalog();
    }

    public void loadTechBdHubDocsCatalog() throws Exception {
        final var rf = new ResourceFactory();
        final var vfsManager = VFS.getManager();
        final var docsHome = VfsResources.findRelativeDirectory("docs.techbd.org/src/content", Optional.empty());
        final var rootFileObject = vfsManager
                .resolveFile("file://" + docsHome.orElse(Path.of(System.getProperty("user.dir"))));

        final var vfsResourcesSupplier = new VfsResources(rf, rootFileObject.getURI(), rootFileObject);
        final var builder = new Resources.Builder<String, Resource<? extends Nature, ?>>()
                .withSupplier(vfsResourcesSupplier);
        techBdHubDocsCatalog = builder.build();
        techBdHubDocsCatalog.getPaths().getFirst().forEach(node -> {
            // if there is any frontmatter, merge the key/value pairs into the nodes as
            // custom attributes
            if (node.payload().isPresent()) {
                final var resource = node.payload().orElseThrow().resource();
                if (resource.nature() instanceof UntypedMarkdownNature nature) {
                    final Components<Map<String, Object>> pc = nature.parsedComponents();
                    if (pc != null && pc.frontmatter().isPresent()) {
                        node.mergeAttributes(pc.frontmatter().orElseThrow());
                    }
                }
            }
        });
    }

    @RouteMapping(label = "Documentation", siblingOrder = 50)
    @GetMapping("/docs")
    public String docs() {
        return "redirect:/docs/swagger-ui";
    }

    @RouteMapping(label = "TechBD Hub", title = "Business and Technology Documentation", siblingOrder = 0)
    @GetMapping("/docs/techbd-hub")
    public String techbdHub(final Model model, final HttpServletRequest request) {
        model.addAttribute("sidebarNS", sidebarNS);
        model.addAttribute("sidebarItems", techBdHubDocsCatalog.getPaths().getFirst());
        return presentation.populateModel("page/docs/techbd-hub", model, request);
    }

    @HxRequest
    @GetMapping("/docs/techbd-hub/sidebar/content")
    public ResponseEntity<?> techbdHubSidebar() {
        final var ph = new PathsHtml.Builder<String, ResourceProvenance<?, Resource<? extends Nature, ?>>>()
                .withIds(node -> "id=\"" + node.absolutePath().replaceAll("[^a-zA-Z0-9]", "-") + "\"")
                .build(); // Use defaults for all other settings

        final var pathsHtml = ph.toHtmlUL(techBdHubDocsCatalog.getPaths().getFirst(), Optional.empty());
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(pathsHtml);
    }

    @GetMapping("/docs/techbd-hub/resource/content")
    public ResponseEntity<?> techbdHubResource(@RequestParam String path) {
        final var found = techBdHubDocsCatalog.getPaths().getFirst().findNode(path);
        if (found.isPresent()) {
            final var resource = found.get().payload().get().resource();
            return ResponseEntity.ok().contentType(MediaType.valueOf(resource.nature().mimeType()))
                    .body(resource.content());
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
