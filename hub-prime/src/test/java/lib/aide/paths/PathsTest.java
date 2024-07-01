package lib.aide.paths;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PathsTest {

    public record SyntheticPayload(String absolutePath, String content, String baseName) {
        public SyntheticPayload(String absolutePath) {
            this(absolutePath, "this is content for " + absolutePath,
                    absolutePath.substring(absolutePath.lastIndexOf('/') + 1));
        }

        @Override
        public String toString() {
            return content;
        }
    }

    private Paths<String, SyntheticPayload> paths;

    @BeforeEach
    public void setup() {
        Paths.PayloadComponentsSupplier<String, SyntheticPayload> supplier = new Paths.PayloadComponentsSupplier<>() {
            @Override
            public List<String> components(String path) {
                return List.of(path.split("/"));
            }

            @Override
            public List<String> components(SyntheticPayload payload) {
                return List.of(payload.absolutePath().split("/"));
            }

            @Override
            public String assemble(List<String> components) {
                return String.join("/", components);
            }
        };

        paths = new Paths<>(supplier);
    }

    private void populateTree() {
        // root [root]
        // ├── dir1 [root/dir1]
        // │ └── dir1_file1.md [root/dir1/dir1_file1.md]
        // ├── dir2 [root/dir2]
        // │ ├── dir2_file1.md [root/dir2/dir2_file1.md]
        // │ └── subdir1 [root/dir2/subdir1]
        // │ └── dir2_subdir1_file1.md [root/dir2/subdir1/dir2_subdir1_file1.md]
        // ├── dir3 [root/dir3]
        // │ ├── dir3_file1.md [root/dir3/dir3_file1.md]
        // │ └── subdir2 [root/dir3/subdir2]
        // │ └── dir3_subdir2_file1.md [root/dir3/subdir2/dir3_subdir2_file1.md]
        // ├── dir4 [root/dir4]
        // │ ├── dir4_file1.md [root/dir4/dir4_file1.md]
        // │ └── subdir3 [root/dir4/subdir3]
        // │ └── dir4_subdir3_file1.md [root/dir4/subdir3/dir4_subdir3_file1.md]
        // └── dir5 [root/dir5]
        // ├── dir5_file1.md [root/dir5/dir5_file1.md]
        // └── subdir4 [root/dir5/subdir4]
        // ├── dir5_subdir4_file1.md [root/dir5/subdir4/dir5_subdir4_file1.md]
        // └── subsubdir1 [root/dir5/subdir4/subsubdir1]
        // └── dir5_subdir4_subsubdir1_file1.md
        // [root/dir5/subdir4/subsubdir1/dir5_subdir4_subsubdir1_file1.md]

        // add paths in order of their ancestors then children (parents first, then
        // kids)
        final Paths.InterimPayloadSupplier<String, SyntheticPayload> ips = (components, index) -> Optional.empty();
        paths.populate(new SyntheticPayload("root/dir1"), ips);
        paths.populate(new SyntheticPayload("root/dir1/dir1_file1.md"), ips);
        paths.populate(new SyntheticPayload("root/dir2"), ips);
        paths.populate(new SyntheticPayload("root/dir2/dir2_file1.md"), ips);
        paths.populate(new SyntheticPayload("root/dir2/subdir1"), ips);
        paths.populate(new SyntheticPayload("root/dir2/subdir1/dir2_subdir1_file1.md"), ips);
        paths.populate(new SyntheticPayload("root/dir3"), ips);
        paths.populate(new SyntheticPayload("root/dir3/dir3_file1.md"), ips);
        paths.populate(new SyntheticPayload("root/dir3/subdir2"), ips);
        paths.populate(new SyntheticPayload("root/dir3/subdir2/dir3_subdir2_file1.md"), ips);
        paths.populate(new SyntheticPayload("root/dir4"), ips);
        paths.populate(new SyntheticPayload("root/dir4/dir4_file1.md"), ips);
        paths.populate(new SyntheticPayload("root/dir4/subdir3"), ips);
        paths.populate(new SyntheticPayload("root/dir4/subdir3/dir4_subdir3_file1.md"), ips);
        paths.populate(new SyntheticPayload("root/dir5"), ips);
        paths.populate(new SyntheticPayload("root/dir5/dir5_file1.md"), ips);
        paths.populate(new SyntheticPayload("root/dir5/subdir4"), ips);
        paths.populate(new SyntheticPayload("root/dir5/subdir4/dir5_subdir4_file1.md"), ips);
        paths.populate(new SyntheticPayload("root/dir5/subdir4/subsubdir1"), ips);
        paths.populate(new SyntheticPayload("root/dir5/subdir4/subsubdir1/dir5_subdir4_subsubdir1_file1.md"), ips);

        // add paths out of order (parents are created in iterim without payloads)
        paths.populate(new SyntheticPayload("root/dir6/dir6_file1.md"), ips);
        paths.populate(new SyntheticPayload("root/dir6"), ips);
    }

    @Test
    public void testAsciiTree() {
        populateTree();
        // this is not a real test, just debugging for now
        System.out.println(new PathsVisuals().asciiTree(paths, Optional.empty()));
    }

    @Test
    public void testPopulateAndFindNodes() {
        populateTree();

        verifyNodeExists("root/dir1", "this is content for root/dir1");
        verifyNodeExists("root/dir1/dir1_file1.md", "this is content for root/dir1/dir1_file1.md");
        verifyNodeExists("root/dir2", "this is content for root/dir2");
        verifyNodeExists("root/dir2/dir2_file1.md", "this is content for root/dir2/dir2_file1.md");
        verifyNodeExists("root/dir2/subdir1", "this is content for root/dir2/subdir1");
        verifyNodeExists("root/dir2/subdir1/dir2_subdir1_file1.md",
                "this is content for root/dir2/subdir1/dir2_subdir1_file1.md");
        verifyNodeExists("root/dir3", "this is content for root/dir3");
        verifyNodeExists("root/dir3/dir3_file1.md", "this is content for root/dir3/dir3_file1.md");
        verifyNodeExists("root/dir3/subdir2", "this is content for root/dir3/subdir2");
        verifyNodeExists("root/dir3/subdir2/dir3_subdir2_file1.md",
                "this is content for root/dir3/subdir2/dir3_subdir2_file1.md");
        verifyNodeExists("root/dir4", "this is content for root/dir4");
        verifyNodeExists("root/dir4/dir4_file1.md", "this is content for root/dir4/dir4_file1.md");
        verifyNodeExists("root/dir4/subdir3", "this is content for root/dir4/subdir3");
        verifyNodeExists("root/dir4/subdir3/dir4_subdir3_file1.md",
                "this is content for root/dir4/subdir3/dir4_subdir3_file1.md");
        verifyNodeExists("root/dir5", "this is content for root/dir5");
        verifyNodeExists("root/dir5/dir5_file1.md", "this is content for root/dir5/dir5_file1.md");
        verifyNodeExists("root/dir5/subdir4", "this is content for root/dir5/subdir4");
        verifyNodeExists("root/dir5/subdir4/dir5_subdir4_file1.md",
                "this is content for root/dir5/subdir4/dir5_subdir4_file1.md");
        verifyNodeExists("root/dir5/subdir4/subsubdir1", "this is content for root/dir5/subdir4/subsubdir1");
        verifyNodeExists("root/dir5/subdir4/subsubdir1/dir5_subdir4_subsubdir1_file1.md",
                "this is content for root/dir5/subdir4/subsubdir1/dir5_subdir4_subsubdir1_file1.md");
    }

    @Test
    public void testAncestors() {
        populateTree();

        verifyAncestors("root/dir1/dir1_file1.md", "root/dir1", "root");
        verifyAncestors("root/dir2/subdir1/dir2_subdir1_file1.md", "root/dir2/subdir1", "root/dir2", "root");
        verifyAncestors("root/dir3/subdir2/dir3_subdir2_file1.md", "root/dir3/subdir2", "root/dir3", "root");
        verifyAncestors("root/dir4/subdir3/dir4_subdir3_file1.md", "root/dir4/subdir3", "root/dir4", "root");
        verifyAncestors("root/dir5/subdir4/dir5_subdir4_file1.md", "root/dir5/subdir4", "root/dir5", "root");
        verifyAncestors("root/dir5/subdir4/subsubdir1/dir5_subdir4_subsubdir1_file1.md", "root/dir5/subdir4/subsubdir1",
                "root/dir5/subdir4", "root/dir5", "root");
    }

    @Test
    public void testSiblings() {
        populateTree();

        verifySiblings("root/dir1", "root/dir2", "root/dir3", "root/dir4", "root/dir5", "root/dir6");
        verifySiblings("root/dir1/dir1_file1.md");
        verifySiblings("root/dir2/dir2_file1.md", "root/dir2/subdir1");
        verifySiblings("root/dir2/subdir1/dir2_subdir1_file1.md");
        verifySiblings("root/dir3/subdir2/dir3_subdir2_file1.md");
        verifySiblings("root/dir4/subdir3/dir4_subdir3_file1.md");
        verifySiblings("root/dir5/subdir4/dir5_subdir4_file1.md", "root/dir5/subdir4/subsubdir1");
        verifySiblings("root/dir5/subdir4/subsubdir1", "root/dir5/subdir4/dir5_subdir4_file1.md");
        verifySiblings("root/dir5/subdir4/subsubdir1/dir5_subdir4_subsubdir1_file1.md");
    }

    @Test
    public void testDescendants() {
        populateTree();

        verifyDescendants("root",
                "root/dir1", "root/dir1/dir1_file1.md",
                "root/dir2", "root/dir2/dir2_file1.md", "root/dir2/subdir1", "root/dir2/subdir1/dir2_subdir1_file1.md",
                "root/dir3", "root/dir3/dir3_file1.md", "root/dir3/subdir2", "root/dir3/subdir2/dir3_subdir2_file1.md",
                "root/dir4", "root/dir4/dir4_file1.md", "root/dir4/subdir3", "root/dir4/subdir3/dir4_subdir3_file1.md",
                "root/dir5", "root/dir5/dir5_file1.md", "root/dir5/subdir4", "root/dir5/subdir4/dir5_subdir4_file1.md",
                "root/dir5/subdir4/subsubdir1", "root/dir5/subdir4/subsubdir1/dir5_subdir4_subsubdir1_file1.md",
                "root/dir6", "root/dir6/dir6_file1.md");
        verifyDescendants("root/dir2",
                "root/dir2/dir2_file1.md", "root/dir2/subdir1", "root/dir2/subdir1/dir2_subdir1_file1.md");
        verifyDescendants("root/dir3",
                "root/dir3/dir3_file1.md", "root/dir3/subdir2", "root/dir3/subdir2/dir3_subdir2_file1.md");
        verifyDescendants("root/dir4",
                "root/dir4/dir4_file1.md", "root/dir4/subdir3", "root/dir4/subdir3/dir4_subdir3_file1.md");
        verifyDescendants("root/dir5",
                "root/dir5/dir5_file1.md", "root/dir5/subdir4", "root/dir5/subdir4/dir5_subdir4_file1.md",
                "root/dir5/subdir4/subsubdir1", "root/dir5/subdir4/subsubdir1/dir5_subdir4_subsubdir1_file1.md");
    }

    @Test
    public void testRelativePathResolution() {
        populateTree();

        // Verify resolving relative paths
        verifyRelativePath("root/dir1/dir1_file1.md", "../dir2/dir2_file1.md", "root/dir2/dir2_file1.md");
        verifyRelativePath("root/dir2/subdir1/dir2_subdir1_file1.md", "../../dir3/dir3_file1.md",
                "root/dir3/dir3_file1.md");
        verifyRelativePath("root/dir3/subdir2/dir3_subdir2_file1.md", "../../dir4/dir4_file1.md",
                "root/dir4/dir4_file1.md");
        verifyRelativePath("root/dir5/subdir4/subsubdir1/dir5_subdir4_subsubdir1_file1.md",
                "../../../dir2/dir2_file1.md", "root/dir2/dir2_file1.md");
    }

    private void verifyNodeExists(String path, String expectedContent) {
        final var node = paths.findNode(path);
        assertThat(node).isPresent();
        assertThat(node.get().payload().get().absolutePath()).isEqualTo(path);
        assertThat(node.get().payload().get().content()).isEqualTo(expectedContent);
    }

    private void verifyAncestors(String path, String... expectedAncestors) {
        final var node = paths.findNode(path);
        assertThat(node).isPresent();

        final var ancestors = node.get().ancestors();
        assertThat(ancestors).hasSize(expectedAncestors.length);

        for (int i = 0; i < expectedAncestors.length; i++) {
            assertThat(ancestors.get(i).absolutePath()).isEqualTo(expectedAncestors[i]);
        }
    }

    private void verifySiblings(String path, String... expectedSiblings) {
        final var node = paths.findNode(path);
        assertThat(node).isPresent();

        final var siblings = node.get().siblings(false);
        assertThat(siblings).hasSize(expectedSiblings.length);

        for (int i = 0; i < expectedSiblings.length; i++) {
            assertThat(siblings.get(i).payload().get().absolutePath()).isEqualTo(expectedSiblings[i]);
        }
    }

    private void verifyDescendants(String path, String... expectedDescendants) {
        final var node = paths.findNode(path);
        assertThat(node).isPresent();

        List<Paths<String, SyntheticPayload>.Node> descendants = node.get().descendants();
        assertThat(descendants).hasSize(expectedDescendants.length);

        for (String expectedDescendant : expectedDescendants) {
            assertThat(descendants)
                    .anyMatch(descendant -> descendant.payload().get().absolutePath().equals(expectedDescendant));
        }
    }

    private void verifyRelativePath(String basePath, String relativePath, String expectedResolvedPath) {
        final var baseNode = paths.findNode(basePath);
        assertThat(baseNode).isPresent();

        final var resolvedPath = baseNode.get().resolve(relativePath);
        assertThat(resolvedPath).isPresent();
        assertThat(resolvedPath.get().absolutePath()).isEqualTo(expectedResolvedPath);
    }

    @Test
    public void testPathsJson() throws Exception {
        // Populate the tree
        populateTree();

        // Define a payload renderer
        PathsJson.PayloadJsonSupplier<String, SyntheticPayload> payloadRenderer = (node, paths) -> {
            final var payloadMap = new HashMap<>();
            node.payload().ifPresent(payload -> {
                payloadMap.put("absolutePath", payload.absolutePath());
                payloadMap.put("content", payload.content());
                payloadMap.put("baseName", payload.baseName());    
            });
            return Optional.of(payloadMap);
        };

        // Create PathsJson instance
        PathsJson<String, SyntheticPayload> pathsJson = new PathsJson<>();

        // Convert Paths instance to JSON
        // TODO: assign this to a variable and then test it
        pathsJson.toJson(paths, Optional.of(payloadRenderer));

        // TODO: Compare generated JSON with expected JSON
        // assertThat(json).contentOf("PathsTestJson.fixture.json").isEqualToIgnoringWhitespace(expectedJson);
    }

    @Test
    public void testPathsHtml() throws Exception {
        // Populate the tree
        populateTree();

        final var ph = new PathsHtml.Builder<String, SyntheticPayload>()
                .withIds(node -> "id=\"" + node.absolutePath().replaceAll("[^a-zA-Z0-9]", "-") + "\"")
                .build(); // Use defaults for all other settings

        @SuppressWarnings("unused")
        final var pathsHtml = ph.toHtmlUL(paths, Optional.empty());

        // assertThat(json).contentOf("PathsTestJson.fixture.json").isEqualToIgnoringWhitespace(expectedJson);
    }
}
