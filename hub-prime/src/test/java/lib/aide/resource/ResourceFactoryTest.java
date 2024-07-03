package lib.aide.resource;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import lib.aide.resource.content.ResourceFactory;

public class ResourceFactoryTest {

    @ParameterizedTest
    @CsvSource({
            "example-file.md, md",
            "example-file.json, json",
            "example-file.yaml, yaml",
            "example-file.yml, yml"
    })
    void testTextResourceFactoryFromSuffix(final String src, final String expectedSuffix) {
        final var factory = new ResourceFactory();
        final var delimiter = Optional.of(Pattern.compile("\\."));

        final var result = factory.textResourceFactoryFromSuffix(src, delimiter);

        assertThat(result).isPresent();
        assertThat(result.get().suffixes().suffixes()).containsExactly(expectedSuffix);
    }

    @ParameterizedTest
    @CsvSource({
            "example-file.md, This is the content of the markdown file., text/markdown",
            "example-file.json, {\"key\": \"value\"}, application/json",
            "example-file.yaml, key: value, application/x-yaml",
            "example-file.yml, key: value, application/x-yaml"
    })
    void testTextResourceFromSuffix(final String src, final String content, final String expectedMimeType) {
        final var factory = new ResourceFactory();
        final Supplier<String> contentSupplier = () -> content;
        final var delimiter = Optional.of(Pattern.compile("\\."));

        final var result = factory.textResourceFromSuffix(src, contentSupplier, delimiter);

        assertThat(result).isPresent();
        assertThat(result.get().content()).isEqualTo(content);
        assertThat(result.get().nature().mimeType()).isEqualTo(expectedMimeType);
    }

    @Test
    void testTextResourceFactoryFromSuffix_emptyResult() {
        final var factory = new ResourceFactory();
        final var src = "example-file.unknown";
        final var delimiter = Optional.of(Pattern.compile("\\."));

        final var result = factory.textResourceFactoryFromSuffix(src, delimiter);

        assertThat(result).isEmpty();
    }

    @Test
    void testTextResourceFromSuffix_emptyResult() {
        final var factory = new ResourceFactory();
        final var src = "example-file.unknown";
        final Supplier<String> content = () -> "This is the content.";
        final var delimiter = Optional.of(Pattern.compile("\\."));

        final var result = factory.textResourceFromSuffix(src, content, delimiter);

        assertThat(result).isEmpty();
    }

    @Test
    void testTextResourceFromSuffix_emptySuffix() {
        final var factory = new ResourceFactory();
        final var src = "example-file";
        final Supplier<String> content = () -> "This is the content.";
        final var delimiter = Optional.of(Pattern.compile("\\."));

        final var result = factory.textResourceFromSuffix(src, content, delimiter);

        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "example-file.md, This is the content of the markdown file., text/markdown",
            "example-file.json, {\"key\": \"value\"}, application/json"
    })
    void testResourceFromSuffix(final String src, final String content, final String expectedMimeType) {
        final var factory = new ResourceFactory();
        final Supplier<String> contentSupplier = () -> content;
        final var delimiter = Optional.of(Pattern.compile("\\."));
        final var assembler = Optional.of(".");

        final var result = factory.resourceFromSuffix(src, contentSupplier, delimiter, assembler);

        assertThat(result).isPresent();
        assertThat(result.get().content()).isEqualTo(content);
        assertThat(result.get().nature().mimeType()).isEqualTo(expectedMimeType);
    }
}
