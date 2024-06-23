package lib.aide.paths;

import java.util.List;

/**
 * This class provides visual representation utilities for the Paths class.
 */
public class PathsVisuals {

    /**
     * Generates an ASCII tree representation of the given Paths object.
     *
     * @param paths the Paths object to visualize
     * @param <C>   the type of the path components
     * @param <P>   the type of the payload
     * @return a string representing the ASCII tree
     */
    public <C, P> String asciiTree(Paths<C, P> paths) {
        StringBuilder sb = new StringBuilder();
        for (Paths<C, P>.Node root : paths.roots()) {
            appendNode(sb, root, "", true, true);
        }
        return sb.toString();
    }

    /**
     * Appends a node and its children to the StringBuilder with the appropriate
     * indentation and structure.
     *
     * @param sb     the StringBuilder to append to
     * @param node   the current node to append
     * @param prefix the current indentation prefix
     * @param isTail indicates if the current node is the last child
     * @param isRoot indicates if the current node is the root node
     */
    private <C, P> void appendNode(StringBuilder sb, Paths<C, P>.Node node, String prefix, boolean isTail,
            boolean isRoot) {
        if (!isRoot) {
            sb.append(prefix).append(isTail ? "└── " : "├── ");
        }
        String baseName = node.components().isEmpty() ? ""
                : node.components().get(node.components().size() - 1).toString();
        sb.append(baseName).append(" [").append(node.absolutePath()).append("]").append("\n");
        List<Paths<C, P>.Node> children = node.children();
        for (int i = 0; i < children.size() - 1; i++) {
            appendNode(sb, children.get(i), prefix + (isTail ? "    " : "│   "), false, false);
        }
        if (!children.isEmpty()) {
            appendNode(sb, children.get(children.size() - 1), prefix + (isTail ? "    " : "│   "), true, false);
        }
    }
}
