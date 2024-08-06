package org.techbd.service;

import java.net.URI;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.kohsuke.github.GitHub;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.techbd.service.http.SandboxHelpers;
import org.techbd.util.NoOpUtils;

import lib.aide.paths.Paths;
import lib.aide.resource.Editable;
import lib.aide.resource.Nature;
import lib.aide.resource.Provenance;
import lib.aide.resource.Resource;
import lib.aide.resource.ResourceProvenance;
import lib.aide.resource.TextResource;
import lib.aide.resource.collection.GitHubRepoResources;
import lib.aide.resource.collection.Resources;
import lib.aide.resource.collection.VfsResources;
import lib.aide.resource.content.MarkdownResource.UntypedMarkdownNature;
import lib.aide.resource.content.ResourceFactory;
import lib.aide.resource.nature.FrontmatterNature.Components;

@Service
public final class DocResourcesService {
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
                if (tryAttr.isPresent()) {
                    return tryAttr.orElseThrow().toString();
                }
            }
            return node.basename().orElse("UNKNOWN BASENAME");
        }

        public String title(
                Paths<String, ? extends ResourceProvenance<? extends Provenance, Resource<? extends Nature, ?>>>.Node node) {
            // attributes may be filled by PathElaboration, loadTechBdHubDocsCatalog or any
            // other resource hooks
            for (final var key : titleKeyNames) {
                var tryAttr = node.getAttribute(key);
                if (tryAttr.isPresent()) {
                    return tryAttr.orElseThrow().toString();
                }
            }
            return node.basename().orElse("UNKNOWN BASENAME");
        }
    }

    /**
     * Utility class to access node's content in a manner convenient for Thymeleaf
     * or others.
     */
    public class NodeAide {
        private final SpelExpressionParser parser = new SpelExpressionParser();
        private final StandardEvaluationContext typicalCtx;

        NodeAide() {
            typicalCtx = new StandardEvaluationContext();
            typicalCtx.addPropertyAccessor(new MapAccessor());
            typicalCtx.addPropertyAccessor(new ReflectivePropertyAccessor());
        }

        /**
         * Retrieves the value of a nested property from a node's attributes map using
         * Spring Expression Language (SpEL).
         *
         * @param node         the node containing the attributes map
         * @param propertyPath the dot-notated path to the property
         * @return the value of the nested property, or an exception message if an error
         *         occurs
         */
        public Object attributeExpr(
                Paths<String, ? extends ResourceProvenance<? extends Provenance, Resource<? extends Nature, ?>>>.Node node,
                final String propertyPath,
                final Optional<Object> defaultValue) {
            try {
                return parser.parseExpression(propertyPath).getValue(typicalCtx, node.attributes());
            } catch (Exception e) {
                return defaultValue.isPresent() ? defaultValue.orElseThrow() : e.toString();
            }
        }

        public Object attributeExpr(
                Paths<String, ? extends ResourceProvenance<? extends Provenance, Resource<? extends Nature, ?>>>.Node node,
                final String propertyPath) {
            return this.attributeExpr(node, propertyPath, Optional.empty());
        }

        public Optional<URI> editURI(
                Paths<String, ? extends ResourceProvenance<? extends Provenance, Resource<? extends Nature, ?>>>.Node node) {
            return node.payload()
                    .flatMap(payload -> {
                        if (payload.provenance() instanceof Editable provenance) {
                            return provenance.editURI();
                        } else {
                            return Optional.empty();
                        }
                    });
        }

        public String editableUrlOrBlank(
                Paths<String, ? extends ResourceProvenance<? extends Provenance, Resource<? extends Nature, ?>>>.Node node) {
            final var editURI = this.editURI(node);
            if (editURI.isEmpty()) {
                return "";
            }
            final var editableUrl = editURI.orElseThrow();
            if (editableUrl.getScheme().equals("file")) {
                return sboxHelpers.getEditorUrlFromAbsolutePath(editableUrl.getPath());
            } else {
                return editableUrl.toString();
            }
        }

        /**
         * Returns the children of a node sorted by the nested property
         * 'nav.sequence.weight' in the node's attributes.
         *
         * @param node the node containing the children
         * @return the list of children nodes sorted by 'nav.sequence.weight'
         */
        public List<Paths<String, ? extends ResourceProvenance<? extends Provenance, Resource<? extends Nature, ?>>>.Node> sequenceableChildren(
                Paths<String, ? extends ResourceProvenance<? extends Provenance, Resource<? extends Nature, ?>>>.Node node) {

            return node.children().stream()
                    .filter(child -> child.payload().map(payload -> payload.resource() instanceof TextResource)
                            .orElse(true))
                    .sorted(Comparator.comparingInt(child -> {
                        final var attributes = child.attributes();
                        final var navVal = attributes.get("nav");
                        if (navVal instanceof Map nav) {
                            final var seqVal = nav.get("sequence");
                            if (seqVal instanceof Map sequence) {
                                final var weightVal = sequence.get("weight");
                                if (weightVal instanceof Integer weight) {
                                    return weight.intValue();
                                } else if (weightVal != null) {
                                    try {
                                        return Integer.parseInt(weightVal.toString());
                                    } catch (NumberFormatException e) {
                                        // Ignore and fall through to default value
                                        NoOpUtils.ignore(e);
                                    }
                                }
                            }
                        }
                        return Integer.MAX_VALUE;
                    }))
                    .collect(Collectors.toList());
        }

        public List<Paths<String, ? extends ResourceProvenance<? extends Provenance, Resource<? extends Nature, ?>>>.Node> sidebarItems() {
            final var sbp = sidebarPaths();
            final var docs = sbp.root().findChild("docs");
            if (docs.isPresent()) {
                return sequenceableChildren(docs.orElseThrow());
            } else {
                return sequenceableChildren(sbp.root());
            }
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
    private NodeAide nodeAide = new NodeAide();
    private final SandboxHelpers sboxHelpers;

    public DocResourcesService(final SandboxHelpers sboxHelpers) throws Exception {
        vfsManager = VFS.getManager();
        this.sboxHelpers = sboxHelpers;
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
                        .withRootPath("src/content");
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

    public NodeAide getNodeAide() {
        return nodeAide;
    }

    public Paths<String, ResourceProvenance<? extends Provenance, Resource<? extends Nature, ?>>> sidebarPaths() {
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
