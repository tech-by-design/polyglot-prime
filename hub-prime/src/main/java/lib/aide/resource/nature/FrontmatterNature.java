package lib.aide.resource.nature;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import lib.aide.resource.Nature;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class FrontmatterNature<F> implements Nature {
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    public record Components<F>(String original, Optional<F> frontmatter, String content, List<Exception> exceptions) {
    }

    private final TypeReference<F> frontmatterType;
    private final Components<F> parsedComponents;

    public FrontmatterNature(final TypeReference<F> frontmatterType) {
        this.frontmatterType = frontmatterType;
        this.parsedComponents = null;
    }

    public FrontmatterNature(final TypeReference<F> frontmatterType, Supplier<String> content) {
        this.frontmatterType = frontmatterType;
        this.parsedComponents = parseContent(content.get());
    }

    public Components<F> parsedComponents() {
        return parsedComponents;
    }

    public Components<F> parseContent(final String content) {
        final var original = content;
        var frontmatter = Optional.<F>empty();
        var bodyContent = content;
        final var exceptions = new ArrayList<Exception>();

        if (content.startsWith("---")) {
            final var endOfYaml = findYamlEndDelimiter(content, exceptions);
            if (endOfYaml != -1) {
                final var yamlContent = content.substring(3, endOfYaml).trim();
                bodyContent = content.substring(endOfYaml + 4).trim();
                try {
                    frontmatter = Optional.of(yamlMapper.readValue(yamlContent, frontmatterType));
                } catch (final Exception e) {
                    exceptions.add(e);
                }
            }
        } else if (content.startsWith("{")) {
            final var endOfJson = findJsonEndDelimiter(content, exceptions);
            if (endOfJson != -1) {
                final var jsonContent = content.substring(0, endOfJson + 1).trim();
                bodyContent = content.substring(endOfJson + 1).trim();
                try {
                    frontmatter = Optional.of(jsonMapper.readValue(jsonContent, frontmatterType));
                } catch (final Exception e) {
                    exceptions.add(e);
                }
            }
        }

        return new Components<>(original, frontmatter, bodyContent, exceptions);
    }

    private int findYamlEndDelimiter(final String content, final List<Exception> exceptions) {
        int endOfYaml = -1;
        int pos = 3;
        while ((pos = content.indexOf("---", pos)) != -1) {
            if (content.charAt(pos - 1) == '\n' && content.charAt(pos + 3) == '\n') {
                endOfYaml = pos;
                break;
            }
            pos += 3;
        }
        if (endOfYaml == -1) {
            exceptions.add(new Exception("Unbalanced YAML frontmatter delimiters."));
        }
        return endOfYaml;
    }

    private int findJsonEndDelimiter(final String content, final List<Exception> exceptions) {
        int braces = 0;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '{') {
                braces++;
            } else if (ch == '}') {
                braces--;
                if (braces == 0) {
                    return i;
                }
            }
        }
        if (braces != 0) {
            exceptions.add(new Exception("Unbalanced JSON frontmatter delimiters."));
        }
        return -1;
    }

    @Override
    public abstract String mimeType();
}
