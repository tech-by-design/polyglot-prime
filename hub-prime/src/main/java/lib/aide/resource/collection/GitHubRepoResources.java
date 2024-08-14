package lib.aide.resource.collection;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tika.Tika;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;

import lib.aide.paths.Paths;
import lib.aide.resource.Editable;
import lib.aide.resource.ForwardableResource;
import lib.aide.resource.Nature;
import lib.aide.resource.Provenance;
import lib.aide.resource.Resource;
import lib.aide.resource.ResourceProvenance;
import lib.aide.resource.ResourcesSupplier;
import lib.aide.resource.StreamResource;
import lib.aide.resource.content.ResourceFactory;

public class GitHubRepoResources
        implements ResourcesSupplier<GitHubRepoResources.GitHubFileProvenance, String, Resource<? extends Nature, ?>> {
    public record GitHubFileProvenance(URI uri, Optional<URI> editURI, GHContent fileContent)
            implements Provenance, Editable {
    }

    private final Tika tika = new Tika();
    private final ResourceFactory rf;
    private final GitHubFilePathComponentsSupplier ghfpcs = new GitHubFilePathComponentsSupplier();
    private final List<PathElaboration> pathElaboration = new ArrayList<>();
    private final URI identity;
    private final GHRepository repo;
    private String rootPath = "";
    private boolean populateAbsolutePaths;

    private final AtomicReference<List<ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>>>> resources = new AtomicReference<>();
    private final AtomicReference<Paths<String, ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>>>> paths = new AtomicReference<>();

    public GitHubRepoResources(final ResourceFactory rf, final URI identity, final GHRepository repo) throws Exception {
        this.rf = rf;
        this.identity = identity;
        this.repo = repo;
    }

    public GitHubRepoResources withRootPath(final String rootPath) {
        this.rootPath = rootPath;
        return this;
    }

    public GitHubRepoResources populateAbsolutePaths(boolean value) {
        this.populateAbsolutePaths = value;
        return this;
    }

    @Override
    public URI identity() {
        return identity;
    }

    @Override
    public Paths<String, ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>>> paths() {
        return paths.updateAndGet(existingPaths -> {
            if (existingPaths == null) {
                final var result = new Paths<String, ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>>>(
                        ghfpcs, (parent, newNode) -> {
                            // If the file is `.path.yml` `.path.yaml` or `.path.json` or
                            // `.[parent-dir-name].path.yml` or `.yaml` or `.json` it's a special file which
                            // "elaborates" the current path, not a content resource
                            if (newNode.basename().isPresent()) {
                                final var pe = PathElaboration.fromBasename(newNode.basename().orElseThrow(), parent,
                                        newNode);
                                if (pe.isPresent()) {
                                    pathElaboration.add(pe.orElseThrow());
                                    return;
                                }
                            }

                            // if we get to here, nothing special was found so just add the child
                            parent.addChild(newNode);
                        });
                for (var resourceProvenance : resources()) {
                    result.populate(resourceProvenance);
                }
                return result;
            }
            return existingPaths;
        });
    }

    @Override
    public List<ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>>> resources() {
        return resources.updateAndGet(existingResources -> {
            if (existingResources == null) {
                final var result = new ArrayList<ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>>>();
                try {
                    walkRepository(repo.getDirectoryContent(rootPath), result);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return result;
            }
            return existingResources;
        });
    }

    public GitHubRepoResources clearCache() {
        resources.set(null);
        paths.set(null);
        return this;
    }

    protected void walkRepository(List<GHContent> contents,
            List<ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>>> resources)
            throws Exception {
        for (var content : contents) {
            if (content.isDirectory()) {
                walkRepository(content.listDirectoryContent().toList(), resources);
            } else {
                final var resource = rf.resourceFromSuffix(content.getName(), () -> {
                    try (var reader = content.read()) {
                        // TODO: this should really check the nature first not just always return text
                        return new String(reader.readAllBytes(), Charset.defaultCharset());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, Optional.empty(), Optional.empty());
                final var ghContentURI = URI.create(content.getHtmlUrl());
                final var provenance = new GitHubFileProvenance(ghContentURI, Optional.of(ghContentURI), content);
                resources.add(resource.isPresent() ? new ResourceProvenance<>(provenance, resource.orElseThrow())
                        : new ResourceProvenance<>(provenance, new GitHubForwardableResource(content)));
            }
        }
    }

    class GitHubFilePathComponentsSupplier implements
            Paths.PayloadComponentsSupplier<String, ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>>> {
        @Override
        public List<String> components(
                ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>> payload) {
            final var suppliedPath = payload.provenance().fileContent().getPath();
            String computedPath;
            if (populateAbsolutePaths) {
                computedPath = suppliedPath;
            } else {
                if (suppliedPath.startsWith(rootPath)) {
                    computedPath = suppliedPath.substring(rootPath.length());
                    if (computedPath.startsWith("/")) {
                        computedPath = computedPath.substring(1);
                    }
                } else {
                    computedPath = suppliedPath;
                }
            }
            return components(computedPath);
        }

        @Override
        public List<String> components(String path) {
            return List.of(path.split("/"));
        }

        @Override
        public String assemble(List<String> components) {
            return String.join("/", components);
        }
    }

    public class GitHubForwardableResource
            implements ForwardableResource<GitHubForwardableNature, InputStream>,
            StreamResource<GitHubForwardableNature> {

        private final GHContent ghContent;
        private final GitHubForwardableNature nature;

        public GitHubForwardableResource(final GHContent ghContent) {
            this.ghContent = ghContent;
            this.nature = new GitHubForwardableNature(ghContent);
        }

        @Override
        public GitHubForwardableNature nature() {
            return nature;
        }

        @Override
        public InputStream content() {
            try {
                return ghContent.read();
            } catch (IOException e) {
                return new ByteArrayInputStream(e.getMessage().getBytes(StandardCharsets.UTF_8));
            }
        }

        @Override
        public Class<InputStream> contentClass() {
            return InputStream.class;
        }
    }

    public class GitHubForwardableNature implements Nature {
        final String mimeType;

        GitHubForwardableNature(final GHContent fileObject) {
            this.mimeType = tika.detect(fileObject.getPath());
        }

        @Override
        public String mimeType() {
            return mimeType;
        }
    }

}
