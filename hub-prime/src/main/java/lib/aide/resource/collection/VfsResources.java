package lib.aide.resource.collection;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.vfs2.FileObject;

import lib.aide.paths.Paths;
import lib.aide.resource.Nature;
import lib.aide.resource.Provenance;
import lib.aide.resource.Resource;
import lib.aide.resource.ResourceProvenance;
import lib.aide.resource.ResourcesSupplier;
import lib.aide.resource.content.EmptyResource;
import lib.aide.resource.content.ExceptionResource;
import lib.aide.resource.content.ResourceFactory;

public class VfsResources
        implements ResourcesSupplier<VfsResources.VfsFileObjectProvenance, String, Resource<? extends Nature, ?>> {
    public record VfsFileObjectProvenance(URI uri, FileObject fileObject) implements Provenance {
    }

    private final ResourceFactory rf;
    private final FileObjectPathComponetsSupplier fopcs = new FileObjectPathComponetsSupplier();
    private final URI identity;
    private final FileObject rootVfsFO;

    private final AtomicReference<List<ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>>> resources = new AtomicReference<>();
    private final AtomicReference<Paths<String, ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>>> paths = new AtomicReference<>();

    public VfsResources(final ResourceFactory rf, final URI identity, final FileObject rootVfsFO) throws Exception {
        this.rf = rf;
        this.identity = identity;
        this.rootVfsFO = rootVfsFO;
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
    public VfsResources clearCache() {
        resources.set(null);
        paths.set(null);
        return this;
    }

    @Override
    public Paths<String, ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>> paths() {
        if (paths.get() == null) {
            synchronized (this) {
                if (paths.get() == null) {
                    final var payloadRoot = new ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>(
                            new VfsFileObjectProvenance(rootVfsFO.getURI(), rootVfsFO),
                            EmptyResource.SINGLETON);
                    final var result = new Paths<String, ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>>(
                            payloadRoot,
                            fopcs);
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
    public List<ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>> resources() {
        if (resources.get() == null) {
            synchronized (this) {
                if (resources.get() == null) {
                    final var result = new ArrayList<ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>>();
                    try {
                        walkFileSystem(rootVfsFO, result);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    resources.set(result);
                }
            }
        }
        return resources.get();
    }

    protected void walkFileSystem(FileObject fileObject,
            List<ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>> resources)
            throws Exception {
        if (fileObject.isFolder()) {
            for (var child : fileObject.getChildren()) {
                walkFileSystem(child, resources);
            }
        } else {
            final var resource = rf.resourceFromSuffix(fileObject.getName().getBaseName(), () -> {
                try {
                    // TODO: this should really check the nature first not just always return text
                    return fileObject.getContent().getString(Charset.defaultCharset());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, Optional.empty()).orElse(
                    new ExceptionResource(new RuntimeException("Unsupported resource type: " + fileObject.getName())));
            final var provenance = new VfsFileObjectProvenance(fileObject.getURI(), fileObject);
            resources.add(new ResourceProvenance<>(provenance, resource));
        }
    }

    class FileObjectPathComponetsSupplier implements
            Paths.PayloadComponentsSupplier<String, ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>> {
        @Override
        public List<String> components(
                ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>> payload) {
            return components(payload.provenance().fileObject().toString());
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
