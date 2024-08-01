package lib.aide.paths;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This class provides HTML representation utilities for the Paths class.
 */
public class PathsHtml<C, P> {
    /**
     * Record to hold HTML content details.
     */
    public record HtmlContent(Optional<String> labelHtml, Optional<Map<String, String>> liAttrs,
            Optional<Map<String, String>> detailsAttrs, Optional<Map<String, String>> summaryAttrs,
            Optional<Map<String, String>> anchorAttrs) {
        /**
         * Constructor for HtmlContent record.
         *
         * @param labelHtml    the HTML content for the label
         * @param liAttrs      the attributes for the
         *                     <li>tag
         * @param detailsAttrs the attributes for the <details> tag
         * @param summaryAttrs the attributes for the <summary> tag
         * @param anchorAttrs  the attributes for the <a> tag
         */
        public HtmlContent {
            if (labelHtml == null) {
                labelHtml = Optional.empty();
            }
            if (liAttrs == null) {
                liAttrs = Optional.empty();
            }
            if (detailsAttrs == null) {
                detailsAttrs = Optional.empty();
            }
            if (summaryAttrs == null) {
                summaryAttrs = Optional.empty();
            }
            if (anchorAttrs == null) {
                anchorAttrs = Optional.empty();
            }
        }
    }

    private final Optional<Function<Paths<C, P>.Node, HtmlContent>> leafNodeHtmlContent;
    private final Optional<Function<Paths<C, P>.Node, HtmlContent>> parentNodeHtmlContent;
    private final Optional<Function<Paths<C, P>.Node, String>> htmlFriendlyId;
    private final String lineIndent;
    private final String tagIndent;

    private static final HtmlContent DEFAULT_HTML_CONTENT = new HtmlContent(
            Optional.empty(),
            Optional.of(Map.of("style", "text-indent: -1em; padding-left: 1em;")),
            Optional.of(Map.of("class", "cursor-pointer font-semibold", "open", "true")),
            Optional.empty(),
            Optional.of(Map.of("onclick", "window.loadResource(event)")));

    private PathsHtml(final Builder<C, P> builder) {
        this.leafNodeHtmlContent = Optional.ofNullable(builder.leafNodeHtmlContent);
        this.parentNodeHtmlContent = Optional.ofNullable(builder.parentNodeHtmlContent);
        this.htmlFriendlyId = builder.htmlFriendlyId;
        this.lineIndent = builder.lineIndent;
        this.tagIndent = builder.tagIndent;
    }

    /**
     * Generates an HTML representation of the given Paths object wrapped in a
     * <ul>
     *
     * @param paths the Paths object to visualize
     * @return a string representing the HTML tree
     */
    public String toHtmlUL(final Paths<C, P> paths, Optional<Map<String, String>> ulAttrs) {
        var sb = new StringBuilder();
        sb.append(lineIndent).append("<ul")
                .append(ulAttrs.map(this::mapToAttributes).orElse(""))
                .append(">");
        appendNode(sb, paths.root(), 0, true);
        sb.append(lineIndent).append("</ul>");
        return sb.toString();
    }

    public String toHtmlUL(final Paths<C, P> paths) {
        return toHtmlUL(paths, Optional.empty());
    }

    /**
     * Appends a node and its children to the StringBuilder with the appropriate
     * HTML structure.
     *
     * @param sb          the StringBuilder to append to
     * @param node        the current node to append
     * @param level       the current level in the tree
     * @param isFirstNode indicates if the node is the first node to avoid starting
     *                    with a blank line
     */
    protected void appendNode(final StringBuilder sb, final Paths<C, P>.Node node, final int level,
            final boolean isFirstNode) {
        final boolean isLeaf = node.isLeaf();
        final var content = isLeaf
                ? leafNodeHtmlContent.flatMap(func -> Optional.ofNullable(func.apply(node)))
                        .orElse(DEFAULT_HTML_CONTENT)
                : parentNodeHtmlContent.flatMap(func -> Optional.ofNullable(func.apply(node)))
                        .orElse(DEFAULT_HTML_CONTENT);
        final var anchorAttrs = content.anchorAttrs().map(this::mapToAttributes).orElse("");
        final var label = "<a href=\"" + node.absolutePath() + "\"" + anchorAttrs + ">"
                + content.labelHtml().orElse(isLeaf ? "📄 " + getDefaultLabel(node) : getDefaultLabel(node)) + "</a>";
        final var liAttrs = content.liAttrs().map(this::mapToAttributes).orElse("");
        final var detailsAttrs = content.detailsAttrs().map(this::mapToAttributes).orElse("");
        final var summaryAttrs = content.summaryAttrs().map(this::mapToAttributes).orElse("");

        if (!isFirstNode) {
            sb.append("\n").append(lineIndent).append(tagIndent.repeat(level));
        }

        sb.append("<li");
        htmlFriendlyId.ifPresent(idFunc -> sb.append(" ").append(idFunc.apply(node)));
        sb.append(liAttrs).append(">");
        if (!isLeaf) {
            sb.append("<details").append(detailsAttrs).append(">");
            sb.append("<summary").append(summaryAttrs).append(">").append(label).append("</summary>");
            sb.append("\n").append(lineIndent).append(tagIndent.repeat(level + 1)).append("<ul>");
            for (var child : node.children()) {
                appendNode(sb, child, level + 1, false);
            }
            sb.append("\n").append(lineIndent).append(tagIndent.repeat(level)).append("</ul>");
            sb.append("</details>");
        } else {
            sb.append(label);
        }
        sb.append("</li>");
    }

    /**
     * Converts a map of attributes to a string representation, properly escaping
     * the attribute values.
     *
     * @param attributes the map of attributes
     * @return the string representation of the attributes
     */
    protected String mapToAttributes(final Map<String, String> attributes) {
        return attributes.entrySet().stream()
                .map(entry -> " " + entry.getKey() + "=\"" + escapeHtml(entry.getValue()) + "\"")
                .collect(Collectors.joining());
    }

    /**
     * Escapes HTML special characters in a string.
     *
     * @param text the input string
     * @return the escaped string
     */
    protected String escapeHtml(final String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Returns the default label for a node.
     *
     * @param node the node
     * @return the default label
     */
    protected String getDefaultLabel(final Paths<C, P>.Node node) {
        return node.components().isEmpty() ? "" : node.components().get(node.components().size() - 1).toString();
    }

    /**
     * Builder class to configure and create an instance of PathsHtml.
     */
    public static class Builder<C, P> {
        private Function<Paths<C, P>.Node, HtmlContent> leafNodeHtmlContent;
        private Function<Paths<C, P>.Node, HtmlContent> parentNodeHtmlContent;
        private Optional<Function<Paths<C, P>.Node, String>> htmlFriendlyId = Optional.empty();
        private String lineIndent = "";
        private String tagIndent = "  ";

        public Builder() {
        }

        /**
         * Sets the function to generate HtmlContent for leaf nodes.
         *
         * @param leafNodeHtmlContent the function to generate HtmlContent for leaf
         *                            nodes
         * @return the Builder instance
         */
        public Builder<C, P> leafNodeHtmlContent(final Function<Paths<C, P>.Node, HtmlContent> leafNodeHtmlContent) {
            this.leafNodeHtmlContent = leafNodeHtmlContent;
            return this;
        }

        /**
         * Sets the function to generate HtmlContent for parent nodes.
         *
         * @param parentNodeHtmlContent the function to generate HtmlContent for parent
         *                              nodes
         * @return the Builder instance
         */
        public Builder<C, P> parentNodeHtmlContent(
                final Function<Paths<C, P>.Node, HtmlContent> parentNodeHtmlContent) {
            this.parentNodeHtmlContent = parentNodeHtmlContent;
            return this;
        }

        /**
         * Sets the function to generate HTML-friendly IDs.
         *
         * @param htmlFriendlyId the function to generate HTML-friendly IDs
         * @return the Builder instance
         */
        public Builder<C, P> withIds(final Function<Paths<C, P>.Node, String> htmlFriendlyId) {
            this.htmlFriendlyId = Optional.ofNullable(htmlFriendlyId);
            return this;
        }

        /**
         * Sets the line indentation.
         *
         * @param indent the line indentation string
         * @return the Builder instance
         */
        public Builder<C, P> indentLines(final String indent) {
            this.lineIndent = Optional.ofNullable(indent).orElse("");
            return this;
        }

        /**
         * Sets the tag indentation.
         *
         * @param indent the tag indentation string
         * @return the Builder instance
         */
        public Builder<C, P> indentTags(final String indent) {
            this.tagIndent = Optional.ofNullable(indent).orElse("  ");
            return this;
        }

        /**
         * Builds the PathsHtml instance.
         *
         * @return the PathsHtml instance
         */
        public PathsHtml<C, P> build() {
            return new PathsHtml<>(this);
        }
    }
}
