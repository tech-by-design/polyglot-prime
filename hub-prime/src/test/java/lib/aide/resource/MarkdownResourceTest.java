package lib.aide.resource;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;

import lib.aide.resource.content.MarkdownResource;

public class MarkdownResourceTest {

    public static class TestFrontmatter {
        public String title;
        public String author;
    }

    @Test
    void testMarkdownContentWithYamlFrontmatter() {
        String yamlContent = """
                ---
                title: Test Title
                author: Test Author
                ---
                This is the body content.
                """;

        var nature = new MarkdownResource.MarkdownNature<TestFrontmatter>(new TypeReference<TestFrontmatter>() {});
        var markdownContent = new MarkdownResource<>(yamlContent, nature);

        var components = nature.components(markdownContent.content());

        assertThat(components.original()).isEqualTo(yamlContent);
        assertThat(components.frontmatter())
                .isPresent()
                .hasValueSatisfying(frontmatter -> {
                    assertThat(frontmatter.title).isEqualTo("Test Title");
                    assertThat(frontmatter.author).isEqualTo("Test Author");
                });
        assertThat(components.content()).isEqualTo("This is the body content.");
        assertThat(components.exceptions()).isEmpty();
    }

    @Test
    void testMarkdownContentWithJsonFrontmatter() {
        String jsonContent = """
                {
                  "title": "Test Title",
                  "author": "Test Author"
                }
                This is the body content.
                """;

        var nature = new MarkdownResource.MarkdownNature<TestFrontmatter>(new TypeReference<TestFrontmatter>() {});
        var markdownContent = new MarkdownResource<>(jsonContent, nature);

        var components = nature.components(markdownContent.content());

        assertThat(components.original()).isEqualTo(jsonContent);
        assertThat(components.frontmatter())
                .isPresent()
                .hasValueSatisfying(frontmatter -> {
                    assertThat(frontmatter.title).isEqualTo("Test Title");
                    assertThat(frontmatter.author).isEqualTo("Test Author");
                });
        assertThat(components.content()).isEqualTo("This is the body content.");
        assertThat(components.exceptions()).isEmpty();
    }

    @Test
    void testMarkdownContentWithUnbalancedYamlFrontmatter() {
        String yamlContent = """
                ---
                title: Test Title
                author: Test Author
                This is the body content.
                """;

        var nature = new MarkdownResource.MarkdownNature<TestFrontmatter>(new TypeReference<TestFrontmatter>() {});
        var markdownContent = new MarkdownResource<>(yamlContent, nature);

        var components = nature.components(markdownContent.content());

        assertThat(components.original()).isEqualTo(yamlContent);
        assertThat(components.frontmatter()).isEmpty();
        assertThat(components.content()).isEqualTo(yamlContent);
        assertThat(components.exceptions())
                .hasSize(1)
                .first()
                .satisfies(e -> assertThat(e).hasMessage("Unbalanced YAML frontmatter delimiters."));
    }

    @Test
    void testMarkdownContentWithUnbalancedJsonFrontmatter() {
        String jsonContent = """
                {
                  "title": "Test Title",
                  "author": "Test Author"
                This is the body content.
                """;

        var nature = new MarkdownResource.MarkdownNature<TestFrontmatter>(new TypeReference<TestFrontmatter>() {});
        var markdownContent = new MarkdownResource<>(jsonContent, nature);

        var components = nature.components(markdownContent.content());

        assertThat(components.original()).isEqualTo(jsonContent);
        assertThat(components.frontmatter()).isEmpty();
        assertThat(components.content()).isEqualTo(jsonContent);
        assertThat(components.exceptions())
                .hasSize(1)
                .first()
                .satisfies(e -> assertThat(e).hasMessage("Unbalanced JSON frontmatter delimiters."));
    }

    @Test
    void testMarkdownContentWithoutFrontmatter() {
        String noFrontmatterContent = """
                This is the body content without frontmatter.
                """;

        var nature = new MarkdownResource.MarkdownNature<TestFrontmatter>(new TypeReference<TestFrontmatter>() {});
        var markdownContent = new MarkdownResource<>(noFrontmatterContent, nature);

        var components = nature.components(markdownContent.content());

        assertThat(components.original()).isEqualTo(noFrontmatterContent);
        assertThat(components.frontmatter()).isEmpty();
        assertThat(components.content()).isEqualTo(noFrontmatterContent);
        assertThat(components.exceptions()).isEmpty();
    }

    @Test
    void testUntypedMarkdownContentWithYamlFrontmatter() {
        String yamlContent = """
                ---
                key1: value1
                key2: value2
                ---
                This is the body content.
                """;

        var nature = new MarkdownResource.UntypedMarkdownNature();
        var markdownContent = new MarkdownResource<>(yamlContent, nature);

        var components = nature.components(markdownContent.content());

        assertThat(components.original()).isEqualTo(yamlContent);
        assertThat(components.frontmatter())
                .isPresent()
                .hasValueSatisfying(frontmatter -> {
                    assertThat(frontmatter)
                            .containsEntry("key1", "value1")
                            .containsEntry("key2", "value2");
                });
        assertThat(components.content()).isEqualTo("This is the body content.");
        assertThat(components.exceptions()).isEmpty();
    }

    @Test
    void testUntypedMarkdownContentWithJsonFrontmatter() {
        String jsonContent = """
                {
                  "key1": "value1",
                  "key2": "value2"
                }
                This is the body content.
                """;

        var nature = new MarkdownResource.UntypedMarkdownNature();
        var markdownContent = new MarkdownResource<>(jsonContent, nature);

        var components = nature.components(markdownContent.content());

        assertThat(components.original()).isEqualTo(jsonContent);
        assertThat(components.frontmatter())
                .isPresent()
                .hasValueSatisfying(frontmatter -> {
                    assertThat(frontmatter)
                            .containsEntry("key1", "value1")
                            .containsEntry("key2", "value2");
                });
        assertThat(components.content()).isEqualTo("This is the body content.");
        assertThat(components.exceptions()).isEmpty();
    }
}
