package lib.aide.paths;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class represents a hierarchical path structure that supports
 * adding, finding, and resolving nodes within the structure.
 * Each node can have a payload and a list of child nodes.
 *
 * @param <C> the type of the path components
 * @param <P> the type of the payload
 */
public class Paths<C, P> {

    /**
     * Interface defining methods to supply and assemble path components.
     *
     * @param <C> the type of the path components
     * @param <P> the type of the payload
     */
    public interface PayloadComponentsSupplier<C, P> {
        List<C> components(String path);

        List<C> components(P payload);

        String assemble(List<C> components);
    }

    /**
     * Represents a node in the hierarchical path structure.
     */
    public class Node {
        private P payload;
        private final List<C> components;
        private final Node parent;
        private final List<Node> children = new ArrayList<>();

        /**
         * Constructs a new Node with the specified components, payload, and parent
         * node.
         *
         * @param components the components of the path for this node
         * @param payload    the payload associated with this node
         * @param parent     the parent node
         */
        public Node(List<C> components, P payload, Node parent) {
            this.components = components;
            this.payload = payload;
            this.parent = parent;
        }

        public List<C> components() {
            return components;
        }

        public P payload() {
            return payload;
        }

        public void setPayload(P payload) {
            this.payload = payload;
        }

        public Node parent() {
            return parent;
        }

        public void addChild(Node child) {
            children.add(child);
        }

        public Optional<Node> findChild(C component) {
            return children.stream()
                    .filter(node -> node.components().get(node.components().size() - 1).equals(component))
                    .findFirst();
        }

        public List<Node> siblings() {
            if (parent == null) {
                return List.of();
            }
            return parent.children.stream()
                    .filter(node -> !node.equals(this))
                    .collect(Collectors.toList());
        }

        public List<Node> descendants() {
            return children.stream()
                    .flatMap(child -> Stream.concat(Stream.of(child), child.descendants().stream()))
                    .collect(Collectors.toList());
        }

        public List<Node> ancestors() {
            final var ancestors = new ArrayList<Node>();
            var ancestor = parent;
            while (ancestor != null) {
                ancestors.add(ancestor);
                ancestor = ancestor.parent;
            }
            return ancestors;
        }

        public boolean isRoot() {
            return parent == null;
        }

        public boolean isLeaf() {
            return children.isEmpty();
        }

        public String absolutePath() {
            return pcSupplier.assemble(components);
        }

        public List<Node> children() {
            return children;
        }

        /**
         * Populates the tree structure with the specified components and payload.
         *
         * @param components the components to add
         * @param payload    the payload associated with the components
         * @param index      the current index in the components list
         */
        public void populate(List<C> components, P payload, int index) {
            if (index < components.size()) {
                final var component = components.get(index);
                final var child = findChild(component).orElseGet(() -> {
                    final var newNode = new Node(new ArrayList<>(components.subList(0, index + 1)), payload, this);
                    addChild(newNode);
                    return newNode;
                });
                child.populate(components, payload, index + 1);
            } else {
                setPayload(payload);
            }
        }

        /**
         * Resolves the relative components to a node in the tree structure.
         *
         * @param relativeComponents the relative components to resolve
         * @return an Optional containing the resolved node if found, or an empty
         *         Optional
         */
        public Optional<Node> resolve(List<C> relativeComponents) {
            // everything should be relative to the current node's parent which
            // is the "container" of this child
            var current = this.parent();

            System.out
                    .println("looking for %s in %s".formatted(pcSupplier.assemble(relativeComponents), absolutePath()));
            for (C component : relativeComponents) {
                System.out.println(
                        "  at [%s] {%s}".formatted(component, current != null ? current.absolutePath() : "NULL"));
                if (component.equals("..")) {
                    if (current != null) {
                        current = current.parent();
                    } else {
                        return Optional.empty();
                    }
                } else if (current != null && !component.equals(".")) {
                    var child = current.findChild(component);
                    if (child.isEmpty()) {
                        return Optional.empty();
                    }
                    current = child.get();
                }
                System.out.println(
                        "  now [%s] {%s}".formatted(component, current != null ? current.absolutePath() : "NULL"));
            }
            System.out.println("  found %s".formatted(current != null ? current.absolutePath() : "NULL"));
            return current != null ? Optional.of(current) : Optional.empty();
        }

        /**
         * Resolves the relative path to a node in the tree structure.
         *
         * @param relativePath the relative path to resolve
         * @return an Optional containing the resolved node if found, or an empty
         *         Optional
         */
        public Optional<Node> resolve(String relativePath) {
            return resolve(pcSupplier.components(relativePath));
        }

        @Override
        public String toString() {
            return "Node [absolutePath()=" + absolutePath() + "]";
        }
    }

    private final List<Node> roots = new ArrayList<>();
    private final PayloadComponentsSupplier<C, P> pcSupplier;

    /**
     * Constructs a Paths object with the specified root payload and payload
     * components supplier.
     *
     * @param rootPayload the payload for the initial root node
     * @param parser      the supplier for payload components
     */
    public Paths(P rootPayload, PayloadComponentsSupplier<C, P> parser) {
        this.pcSupplier = parser;
        this.roots.add(new Node(parser.components(rootPayload), rootPayload, null));
    }

    /**
     * Adds a new root node to the Paths structure.
     *
     * @param rootPayload the payload for the new root node
     */
    public void addRoot(P rootPayload) {
        final var components = pcSupplier.components(rootPayload);
        if (roots.stream().noneMatch(root -> root.components().equals(components))) {
            this.roots.add(new Node(components, rootPayload, null));
        }
    }

    /**
     * Returns the list of root nodes.
     *
     * @return the list of root nodes
     */
    public List<Node> roots() {
        return roots;
    }

    /**
     * Returns the first of the root nodes (simple trees only have one root so it's
     * just convenient).
     *
     * @return the list of root nodes
     */
    public Paths<C, P>.Node root() {
        return roots.get(0);
    }

    /**
     * Populates the tree structure with the specified payload, starting from all
     * roots.
     *
     * @param payload the payload to populate
     */
    public void populate(P payload) {
        final var components = pcSupplier.components(payload);
        for (Node root : roots) {
            if (root.components().equals(components.subList(0, 1))) {
                root.populate(components, payload, 1);
                return;
            }
        }
        addRoot(payload);
    }

    /**
     * Finds a node in the tree structure based on the full path, starting from all
     * roots.
     *
     * @param fullPath the full path of the node to find
     * @return an Optional containing the found node if exists, or an empty Optional
     */
    public Optional<Node> findNode(String fullPath) {
        final var components = pcSupplier.components(fullPath);
        for (Node root : roots) {
            if (root.components().equals(components.subList(0, 1))) {
                var current = root;
                for (C component : components.subList(1, components.size())) {
                    var child = current.findChild(component);
                    if (child.isEmpty()) {
                        current = null;
                        break;
                    }
                    current = child.get();
                }
                if (current != null) {
                    return Optional.of(current);
                }
            }
        }
        return Optional.empty();
    }
}
