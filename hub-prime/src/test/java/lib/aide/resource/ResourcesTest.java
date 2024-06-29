package lib.aide.resource;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.VFS;
import org.junit.jupiter.api.Test;

import lib.aide.paths.PathsVisuals;

public class ResourcesTest {

    public void createTestFile(FileObject rootFileObject, String fileName, String content) throws Exception {
        final var file = rootFileObject.resolveFile(fileName);
        file.createFile();
        try (var outputStream = file.getContent().getOutputStream()) {
            outputStream.write(content.getBytes());
        }
    }

    public void createTestFiles(final FileObject rootFileObject) throws Exception {
        createTestFile(rootFileObject, "test.md", """
                ---
                title: Test Markdown
                ---
                # Heading
                Content""");

        createTestFile(rootFileObject, "test.json", """
                {
                  "title": "Test JSON",
                  "content": "Content"
                }""");

        createTestFile(rootFileObject, "test.yml", """
                title: Test YAML
                content: Content""");
    }

    @Test
    void testSyntheticResources() throws Exception {
        final var rf = new ResourceFactory();
        final var fsManager = VFS.getManager();
        final var rootFileObject = fsManager.resolveFile("ram://test");
        createTestFiles(rootFileObject);

        final var vfsResourcesSupplier = new VfsResources(rf, rootFileObject.getURI(), rootFileObject);
        final var builder = new Resources.Builder<String, Resource<? extends Nature, ?>>()
                .withSupplier(vfsResourcesSupplier);

        // you can hang on to the builder to "refresh" the resources later
        final var resources = builder.build();

        assertThat(resources).isNotNull();
        assertThat(resources.getResources()).hasSize(3);

        List<ResourceProvenance<?, Resource<? extends Nature, ?>>> resourceList = resources.getResources();
        for (ResourceProvenance<?, Resource<? extends Nature, ?>> resourceProvenance : resourceList) {
            assertThat(resourceProvenance.provenance().uri()).isNotNull();
            assertThat(resourceProvenance.resource().content()).isNotNull();
        }

        assertThat(resources.getPaths().size()).isEqualTo(1);
    }

    @Test
    void testProjectResources() throws Exception {
        final var rf = new ResourceFactory();
        final var fsManager = VFS.getManager();
        final var currentDir = System.getProperty("user.dir"); // Get the current directory
        final var rootFileObject = fsManager.resolveFile("file://" + currentDir);

        final var vfsResourcesSupplier = new VfsResources(rf, rootFileObject.getURI(), rootFileObject);
        final var builder = new Resources.Builder<String, Resource<? extends Nature, ?>>()
                .withSupplier(vfsResourcesSupplier);

        // you can hang on to the builder to "refresh" the resources later;
        // to refresh you just call builder.build() again
        final var catalog = builder.build();
        assertThat(catalog).isNotNull();

        final var resources = catalog.getResources();
        assertThat(resources).isNotNull();
        assertThat(resources).hasSizeGreaterThan(0);

        final var paths = catalog.getPaths();
        assertThat(paths).isNotNull();
        assertThat(paths).hasSize(1);

        final var pathsPrime = paths.getFirst();
        System.out.println(pathsPrime.roots());

        final var pv = new PathsVisuals();
        System.out.println(pv.asciiTree(pathsPrime, Optional.empty()));
    }
}
