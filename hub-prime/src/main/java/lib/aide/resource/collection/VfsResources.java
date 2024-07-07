package lib.aide.resource.collection;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;

import lib.aide.paths.Paths;
import lib.aide.resource.Editable;
import lib.aide.resource.Nature;
import lib.aide.resource.Provenance;
import lib.aide.resource.Resource;
import lib.aide.resource.ResourceProvenance;
import lib.aide.resource.ResourcesSupplier;
import lib.aide.resource.content.ExceptionResource;
import lib.aide.resource.content.ResourceFactory;

public class VfsResources
        implements ResourcesSupplier<VfsResources.VfsFileObjectProvenance, String, Resource<? extends Nature, ?>> {
    public record VfsFileObjectProvenance(URI uri, Optional<URI> editURI, FileObject fileObject)
            implements Provenance, Editable {
    }

    private final ResourceFactory rf;
    private final FileObjectPathComponentsSupplier fopcs = new FileObjectPathComponentsSupplier();
    private final List<PathElaboration> pathElaboration = new ArrayList<>();
    private final URI identity;
    private final FileObject rootVfsFO;
    private boolean populateAbsolutePaths = false;

    private final AtomicReference<List<ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>>> resources = new AtomicReference<>();
    private final AtomicReference<Paths<String, ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>>> paths = new AtomicReference<>();

    public VfsResources(final ResourceFactory rf, final URI identity, final FileObject rootVfsFO) throws Exception {
        this.rf = rf;
        this.identity = identity;
        this.rootVfsFO = rootVfsFO;
    }

    public VfsResources populateAbsolutePaths(boolean value) {
        this.populateAbsolutePaths = value;
        return this;
    }

    @Override
    public URI identity() {
        return identity;
    }

    @Override
    public Paths<String, ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>> paths() {
        return paths.updateAndGet(existingPaths -> {
            if (existingPaths == null) {
                final var result = new Paths<String, ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>>(
                        fopcs, (parent, newNode) -> {
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
    public List<ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>> resources() {
        return resources.updateAndGet(existingResources -> {
            if (existingResources == null) {
                final var result = new ArrayList<ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>>();
                try {
                    walkFileSystem(rootVfsFO, result);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return result;
            }
            return existingResources;
        });
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
            }, Optional.empty(), Optional.empty()).orElse(
                    new ExceptionResource(new RuntimeException("Unsupported resource type: " + fileObject.getName())));
            final var provenance = new VfsFileObjectProvenance(fileObject.getURI(), Optional.of(fileObject.getURI()),
                    fileObject);
            resources.add(new ResourceProvenance<>(provenance, resource));
        }
    }

    class FileObjectPathComponentsSupplier implements
            Paths.PayloadComponentsSupplier<String, ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>>> {
        @Override
        public List<String> components(
                ResourceProvenance<VfsFileObjectProvenance, Resource<? extends Nature, ?>> payload) {
            final var actualName = payload.provenance().fileObject().getName();
            String computedPath;
            try {
                computedPath = populateAbsolutePaths ? actualName.getPath()
                        : rootVfsFO.getName().getRelativeName(actualName);
            } catch (FileSystemException e) {
                computedPath = actualName.getPath();
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

    /**
     * Searches for a specific relative directory path by traversing up the parent
     * directories from the given start directory or the current directory if no
     * start directory is provided.
     *
     * @param relativePath the relative directory path to search for.
     * @param startFrom    an optional starting directory. If not provided, the
     *                     search starts from the current directory.
     * @return an Optional containing the found directory path if it exists,
     *         otherwise Optional.empty().
     */
    public static Optional<Path> findRelativeDirectory(String relativePath, Optional<Path> startFrom) {
        var startDir = startFrom.orElse(Path.of(System.getProperty("user.dir")));
        var targetPath = startDir.resolve(relativePath);

        while (startDir != null) {
            if (Files.isDirectory(targetPath)) {
                return Optional.of(targetPath);
            }
            startDir = startDir.getParent();
            targetPath = startDir != null ? startDir.resolve(relativePath) : null;
        }
        return Optional.empty();
    }
}
