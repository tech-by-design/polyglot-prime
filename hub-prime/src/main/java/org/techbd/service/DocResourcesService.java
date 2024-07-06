package org.techbd.service;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.kohsuke.github.GitHub;
import org.springframework.stereotype.Service;

import lib.aide.paths.Paths;
import lib.aide.resource.Nature;
import lib.aide.resource.Provenance;
import lib.aide.resource.Resource;
import lib.aide.resource.ResourceProvenance;
import lib.aide.resource.collection.GitHubRepoResources;
import lib.aide.resource.collection.Resources;
import lib.aide.resource.collection.VfsResources;
import lib.aide.resource.content.MarkdownResource.UntypedMarkdownNature;
import lib.aide.resource.content.ResourceFactory;
import lib.aide.resource.nature.FrontmatterNature.Components;

@Service
public class DocResourcesService {
    public record LoadedFrom(Optional<URI> uri, boolean isValid) {
    }

    public class NamingStrategy {
        private final String[] captionKeyNames = { "label", "caption", "title" };
        private final String[] titleKeyNames = { "title", "caption", "label" };

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

        public String title(
                Paths<String, ? extends ResourceProvenance<? extends Provenance, Resource<? extends Nature, ?>>>.Node node) {
            // attributes may be filled by PathElaboration, loadTechBdHubDocsCatalog or any
            // other resource hooks
            for (final var key : titleKeyNames) {
                var tryAttr = node.getAttribute(key);
                if (tryAttr.isPresent())
                    return tryAttr.orElseThrow().toString();
            }
            return node.basename().orElse("UNKNOWN BASENAME");
        }
    }

    private final String[] ghEnvVarNames = new String[] { "ORG_TECHBD_SERVICE_HTTP_GITHUB_API_AUTHN_TOKEN",
            "CHEZMOI_GITHUB_ACCESS_TOKEN",
            "GITHUB_TOKEN" };
    private final ResourceFactory rf = new ResourceFactory();
    private final FileSystemManager vfsManager;
    private Optional<LoadedFrom> loadedFrom = Optional.empty();
    private Resources<String, Resource<? extends Nature, ?>> resources;
    private NamingStrategy namingStrategy = new NamingStrategy();

    public DocResourcesService() throws Exception {
        vfsManager = VFS.getManager();
        loadResources();
    }

    protected void loadResources() throws Exception {
        final var docsHome = VfsResources.findRelativeDirectory("docs.techbd.org/src/content", Optional.empty());
        if (docsHome.isPresent()) {
            final var rootFileObject = vfsManager.resolveFile("file://" + docsHome.orElseThrow());
            final var vfsResourcesSupplier = new VfsResources(rf, rootFileObject.getURI(), rootFileObject);
            final var builder = new Resources.Builder<String, Resource<? extends Nature, ?>>()
                    .withSupplier(vfsResourcesSupplier);
            resources = builder.build();
            loadedFrom = Optional.of(new LoadedFrom(Optional.of(rootFileObject.getURI()), true));
        } else {
            final var ghTokenFromEnv = Arrays.stream(ghEnvVarNames)
                    .map(System::getenv)
                    .filter(Objects::nonNull)
                    .findFirst();
            if (ghTokenFromEnv.isPresent()) {
                final var github = GitHub.connectUsingOAuth(ghTokenFromEnv.orElseThrow());
                final var ghRepo = github.getRepository("tech-by-design/docs.techbd.org");
                final var ghResourcesSupplier = new GitHubRepoResources(rf, ghRepo.getHtmlUrl().toURI(), ghRepo)
                        .withRootPath("src/content/docs");
                final var builder = new Resources.Builder<String, Resource<? extends Nature, ?>>()
                        .withSupplier(ghResourcesSupplier);
                resources = builder.build();
                loadedFrom = Optional.of(new LoadedFrom(Optional.of(ghRepo.getHtmlUrl().toURI()), true));
            } else {
                loadedFrom = Optional.of(new LoadedFrom(Optional.empty(), false));
                resources = new Resources.Builder<String, Resource<? extends Nature, ?>>().build();
                // resources will be empty
            }
        }

        resources.getPaths().forEach(paths -> {
            paths.forEach(node -> {
                // if there is any frontmatter, merge the key/value pairs into the nodes as
                // custom attributes; it will include properties like "caption", "title", etc.
                // which the Sidebar and other UI elements will use.
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
        });
    }

    public Optional<LoadedFrom> loadedFrom() {
        return loadedFrom;
    }

    public Resources<String, Resource<? extends Nature, ?>> getResources() {
        return resources;
    }

    public NamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public Paths<String, ResourceProvenance<? extends Provenance, Resource<? extends Nature, ?>>> sidebarItems() {
        return resources.getPaths().size() > 0 ? resources.getPaths().getFirst()
                : new Paths<String, ResourceProvenance<? extends Provenance, Resource<? extends Nature, ?>>>(
                        new EmptyPathsComponents());
    }

    public class EmptyPathsComponents implements
            Paths.PayloadComponentsSupplier<String, ResourceProvenance<? extends Provenance, Resource<? extends Nature, ?>>> {
        @Override
        public List<String> components(final String path) {
            return List.of(path.split("/"));
        }

        @Override
        public List<String> components(
                final ResourceProvenance<? extends Provenance, Resource<? extends Nature, ?>> payload) {
            return components(payload.toString());
        }

        @Override
        public String assemble(final List<String> components) {
            return String.join("/", components);
        }
    }
}
