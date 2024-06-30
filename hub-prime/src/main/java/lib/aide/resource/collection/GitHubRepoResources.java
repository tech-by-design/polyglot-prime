package lib.aide.resource.collection;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;

import lib.aide.paths.Paths;
import lib.aide.resource.Nature;
import lib.aide.resource.Provenance;
import lib.aide.resource.Resource;
import lib.aide.resource.ResourceProvenance;
import lib.aide.resource.ResourcesSupplier;
import lib.aide.resource.content.ExceptionResource;
import lib.aide.resource.content.ResourceFactory;

public class GitHubRepoResources
        implements ResourcesSupplier<GitHubRepoResources.GitHubFileProvenance, String, Resource<? extends Nature, ?>> {
    public record GitHubFileProvenance(URI uri, GHContent fileContent) implements Provenance {
    }

    private final ResourceFactory rf;
    private final GitHubFilePathComponentsSupplier ghfpcs = new GitHubFilePathComponentsSupplier();
    private final URI identity;
    private final GHRepository repo;

    private final AtomicReference<List<ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>>>> resources = new AtomicReference<>();
    private final AtomicReference<Paths<String, ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>>>> paths = new AtomicReference<>();

    public GitHubRepoResources(final ResourceFactory rf, final URI identity, final GHRepository repo) throws Exception {
        this.rf = rf;
        this.identity = identity;
        this.repo = repo;
    }

    @Override
    public URI identity() {
        return identity;
    }

    /**
     * Call clearCache().resources() or clearCache().paths() to re-read from
     * sources.
     * 
     * @return this resources supplier instance to allow fluent chaining
     */
    public GitHubRepoResources clearCache() {
        resources.set(null);
        paths.set(null);
        return this;
    }

    @Override
    public Paths<String, ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>>> paths() {
        if (paths.get() == null) {
            synchronized (this) {
                if (paths.get() == null) {
                    final var result = new Paths<String, ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>>>(
                            ghfpcs);
                    for (var resourceProvenance : resources()) {
                        result.populate(resourceProvenance);
                    }
                    paths.set(result);
                }
            }
        }
        return paths.get();
    }

    @Override
    public List<ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>>> resources() {
        if (resources.get() == null) {
            synchronized (this) {
                if (resources.get() == null) {
                    final var result = new ArrayList<ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>>>();
                    try {
                        walkRepository(repo.getDirectoryContent(""), result);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    resources.set(result);
                }
            }
        }
        return resources.get();
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
                }, Optional.empty()).orElse(
                        new ExceptionResource(new RuntimeException("Unsupported resource type: " + content.getName())));
                final var provenance = new GitHubFileProvenance(URI.create(content.getHtmlUrl()), content);
                resources.add(new ResourceProvenance<>(provenance, resource));
            }
        }
    }

    class GitHubFilePathComponentsSupplier implements
            Paths.PayloadComponentsSupplier<String, ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>>> {
        @Override
        public List<String> components(
                ResourceProvenance<GitHubFileProvenance, Resource<? extends Nature, ?>> payload) {
            return components(payload.provenance().fileContent().getPath());
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
}
