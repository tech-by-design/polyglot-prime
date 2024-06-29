package lib.aide.cms;

import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.type.TypeReference;

public class MarkdownContent<F, N extends FrontmatterNature<F>> implements Content<N, String> {
    private final Supplier<String> src;
    private final N nature;

    public MarkdownContent(final String src, N nature) {
        this.src = () -> src;
        this.nature = nature;
    }

    public MarkdownContent(final Supplier<String> src, N nature) {
        this.src = src;
        this.nature = nature;
    }

    @Override
    public N nature() {
        return nature;
    }

    @Override
    public String content() {
        return src.get();
    }

    public static class MarkdownNature<F> extends FrontmatterNature<F> {
        public MarkdownNature(TypeReference<F> frontmatterType) {
            super(frontmatterType);
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
            super(new TypeReference<Map<String, Object>>() {});
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
