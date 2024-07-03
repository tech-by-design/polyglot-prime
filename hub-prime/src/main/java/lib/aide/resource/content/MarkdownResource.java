package lib.aide.resource.content;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.type.TypeReference;

import lib.aide.paths.PathSuffixes;
import lib.aide.resource.TextResource;
import lib.aide.resource.nature.FrontmatterNature;

public class MarkdownResource<F, N extends FrontmatterNature<F>> implements TextResource<N> {
    private final Supplier<String> src;
    private final N nature;
    private final Optional<PathSuffixes> suffixes;

    public MarkdownResource(final String src, N nature, Optional<PathSuffixes> suffixes) {
        this.src = () -> src;
        this.nature = nature;
        this.suffixes = suffixes;
    }

    public MarkdownResource(final Supplier<String> src, N nature, Optional<PathSuffixes> suffixes) {
        this.src = src;
        this.nature = nature;
        this.suffixes = suffixes;
    }

    public MarkdownResource(final String src, N nature) {
        this(src, nature, Optional.empty());
    }

    public MarkdownResource(final Supplier<String> src, N nature) {
        this(src, nature, Optional.empty());
    }

    @Override
    public N nature() {
        return nature;
    }

    @Override
    public String content() {
        return src.get();
    }

    public Optional<PathSuffixes> suffixes() {
        return suffixes;
    }

    public static class MarkdownNature<F> extends FrontmatterNature<F> {
        public MarkdownNature(final TypeReference<F> frontmatterType) {
            super(frontmatterType);
        }

        public MarkdownNature(final TypeReference<F> frontmatterType, final Supplier<String> content) {
            super(frontmatterType, content);
        }

        @Override
        public String mimeType() {
            return "text/markdown";
        }

        public Components<F> components(final String content) {
            return parseContent(content);
        }
    }

    public static class UntypedMarkdownNature extends FrontmatterNature<Map<String, Object>> {
        public UntypedMarkdownNature() {
            super(new TypeReference<Map<String, Object>>() {
            });
        }

        public UntypedMarkdownNature(final Supplier<String> content) {
            super(new TypeReference<Map<String, Object>>() {
            }, content);
        }

        @Override
        public String mimeType() {
            return "text/markdown";
        }

        public Components<Map<String, Object>> components(final String content) {
            return parseContent(content);
        }
    }
}
