package org.techbd.service.http.hub.prime.ux;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.techbd.service.DocResourcesService;
import org.techbd.service.http.hub.prime.route.RouteMapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lib.aide.paths.PathsHtml;
import lib.aide.resource.ForwardableResource;
import lib.aide.resource.Nature;
import lib.aide.resource.Resource;
import lib.aide.resource.ResourceProvenance;

@Controller
@Tag(name = "TechBD Hub Docs API")
public class DocsController {
    private static final Logger LOG = LoggerFactory.getLogger(DocsController.class.getName());
    public static final ObjectMapper headersOM = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

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
        model.addAttribute("nodeAide", drs.getNodeAide());
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

        final var pathsHtml = ph.toHtmlUL(drs.sidebarPaths(), Optional.empty());
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(pathsHtml);
    }

    @GetMapping("/docs/techbd-hub/resource/content/**")
    public ResponseEntity<?> techbdHubResource(final HttpServletRequest request)
            throws JsonProcessingException, UnsupportedEncodingException {
        final var path = URLDecoder.decode(request.getRequestURI().split("/docs/techbd-hub/resource/content/")[1],
                StandardCharsets.UTF_8.toString());

        final var found = drs.sidebarPaths().findNode(path);
        if (found.isPresent()) {
            final var node = found.orElseThrow();
            if (node.payload().isPresent()) {
                final var resource = node.payload().orElseThrow().resource();
                // if the resource is not "renderable" and is "forwardable"
                if (resource instanceof ForwardableResource) {
                    final var location = "/docs/techbd-hub/resource/proxy/" + path;
                    LOG.info("[techbdHubResource] proxying '%s' via '%s'".formatted(path, location));
                    return ResponseEntity
                            .status(HttpStatus.FOUND)
                            .header(HttpHeaders.LOCATION, location)
                            .build();
                }

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

    @GetMapping("/docs/techbd-hub/resource/proxy/**")
    public ResponseEntity<StreamingResponseBody> techbdHubProxy(final HttpServletRequest request)
            throws UnsupportedEncodingException {
        final var path = URLDecoder.decode(request.getRequestURI().split("/docs/techbd-hub/resource/proxy/")[1],
                StandardCharsets.UTF_8.toString());
        LOG.info("proxy '%s'?".formatted(path));

        final var found = drs.sidebarPaths().findNode(path);
        if (found.isEmpty()) {
            LOG.info("proxy '%s' not found".formatted(path));
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .header("X-TechBD-Resource-Proxy-Path", path)
                    .body(null);
        }

        final var node = found.orElseThrow();
        if (node.payload().isEmpty()) {
            LOG.info("proxy '%s' found but has no payload".formatted(path));
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .header("X-TechBD-Resource-Proxy-Path", path)
                    .header("X-TechBD-Resource-Proxy-Path-Payload", "EMPTY")
                    .body(null);
        }

        final var resource = node.payload().orElseThrow().resource();
        if (resource instanceof ForwardableResource fr && InputStream.class.isAssignableFrom(fr.contentClass())) {
            LOG.info("proxy '%s' forwardable".formatted(path));
            @SuppressWarnings("unchecked")
            final var streamFR = (ForwardableResource<?, InputStream>) fr;
            try {
                final StreamingResponseBody responseBody = outputStream -> {
                    try (final var inputStream = streamFR.content()) {
                        var buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                };
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, fr.nature().mimeType())
                        .body(responseBody);
            } catch (final Exception e) {
                LOG.error("proxy '%s' error".formatted(path), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
        } else {
            LOG.info("proxy '%s' found but is not forwardable".formatted(path));
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .header("X-TechBD-Resource-Proxy-Path", path)
                    .header("X-TechBD-Resource-Proxy-Path-Payload", "NOT_FORWARDABLE")
                    .body(null);
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

    @RouteMapping(label = "Schema", title = "Schema", siblingOrder = 50)
    @GetMapping("/docs/schema")
    public String schemaSpy(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/docs/schema", model, request);
    }

    @RouteMapping(label = "ISLM Migration", title = "ISLM", siblingOrder = 60)
    @GetMapping("/docs/islm")
    public String islm(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/docs/islm", model, request);
    }
    
}
