package lib.aide.resource.content;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.type.TypeReference;

import lib.aide.paths.PathSuffixes;
import lib.aide.resource.TextResource;
import lib.aide.resource.nature.FrontmatterNature;

public class MdxResource<F, N extends FrontmatterNature<F>> implements TextResource<N> {
    private final Supplier<String> src;
    private final N nature;
    private final Optional<PathSuffixes> suffixes;

    public MdxResource(final String src, N nature, Optional<PathSuffixes> suffixes) {
        this.src = () -> src;
        this.nature = nature;
        this.suffixes = suffixes;
    }

    public MdxResource(final Supplier<String> src, N nature, Optional<PathSuffixes> suffixes) {
        this.src = src;
        this.nature = nature;
        this.suffixes = suffixes;
    }

    public MdxResource(final String src, N nature) {
        this(src, nature, Optional.empty());
    }

    public MdxResource(final Supplier<String> src, N nature) {
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

    public static class MdxNature<F> extends FrontmatterNature<F> {
        public MdxNature(final TypeReference<F> frontmatterType) {
            super(frontmatterType);
        }

        public MdxNature(final TypeReference<F> frontmatterType, final Supplier<String> content) {
            super(frontmatterType, content);
        }

        @Override
        public String mimeType() {
            return "text/mdx";
        }

        public Components<F> components(final String content) {
            return parseContent(content);
        }
    }

    public static class UntypedMdxNature extends FrontmatterNature<Map<String, Object>> {
        public UntypedMdxNature() {
            super(new TypeReference<Map<String, Object>>() {
            });
        }

        public UntypedMdxNature(final Supplier<String> content) {
            super(new TypeReference<Map<String, Object>>() {
            }, content);
        }

        @Override
        public String mimeType() {
            return "text/mdx";
        }

        public Components<Map<String, Object>> components(final String content) {
            return parseContent(content);
        }
    }
}
