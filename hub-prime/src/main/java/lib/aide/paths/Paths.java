package lib.aide.paths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.validation.constraints.NotNull;

/**
 * This class represents a hierarchical path structure that supports
 * adding, finding, and resolving nodes within the structure.
 * Each node can have a payload and a list of child nodes.
 *
 * @param <C> the type of the path components
 * @param <P> the type of the payload
 */
public class Paths<C, P> {
    @FunctionalInterface
    public interface InterimPayloadSupplier<C, P> {
        Optional<P> payload(final List<C> components, final int index);
    }

    @FunctionalInterface
    public interface NodePopulationStrategy<C, P> {
        void populate(final Paths<C, P>.Node parent, final Paths<C, P>.Node newNode);
    }

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
        private Optional<P> payload;
        private final List<C> components;
        private final Node parent;
        private final List<Node> children = new ArrayList<>();
        private final Map<String, Object> attributes = new HashMap<>();
        private final List<Exception> issues = new ArrayList<>();

        /**
         * Constructs a new Node with the specified components, payload, and parent
         * node.
         *
         * @param components the components of the path for this node
         * @param payload    the payload associated with this node
         * @param parent     the parent node
         */
        public Node(final List<C> components, final Optional<P> payload, final Node parent) {
            this.components = components;
            this.payload = payload;
            this.parent = parent;
        }

        public List<C> components() {
            return components;
        }

        public Optional<P> payload() {
            return payload;
        }

        public void setPayload(final Optional<P> payload) {
            this.payload = payload;
        }

        public Node parent() {
            return parent;
        }

        public void addChild(final Node child) {
            children.add(child);
        }

        public void addAttribute(final String key, Object value) {
            attributes.put(key, value);
        }

        public List<Map.Entry<String, DeepMergeOperation>> mergeAttributes(final Map<String, Object> updates) {
            return deepMerge(attributes, updates);
        }

        public Optional<Object> getAttribute(final String key) {
            return Optional.ofNullable(attributes.get(key));
        }

        public void addIssue(final Exception e) {
            issues.add(e);
        }

        public Optional<Node> findChild(final C component) {
            return children.stream()
                    .filter(node -> node.components().get(node.components().size() - 1).equals(component))
                    .findFirst();
        }

        public List<Node> siblings(final boolean withSelf) {
            if (parent == null) {
                return List.of();
            }
            return withSelf ? parent.children.stream().collect(Collectors.toList())
                    : parent.children.stream()
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
            // the physical root isn't really an ancestor
            if (ancestors.size() > 0) {
                ancestors.removeLast();
            }
            return ancestors;
        }

        public boolean isRoot() {
            return parent == null;
        }

        public boolean isLeaf() {
            return children.isEmpty();
        }

        public String absolutePath(final boolean includeRoot) {
            return pcSupplier.assemble(includeRoot ? components : components.subList(1, components.size()));
        }

        public String absolutePath() {
            return absolutePath(true);
        }

        public Optional<C> basename() {
            return components.isEmpty() ? Optional.empty() : Optional.of(components.getLast());
        }

        public List<Node> children() {
            return children;
        }

        public Map<String, Object> attributes() {
            return Map.copyOf(attributes);
        }

        public List<Exception> issues() {
            return List.copyOf(issues);
        }

        /**
         * Populates the tree structure with the specified components and payload.
         * If a parent is not already created, it's called an "interim" node and
         * will have an "interim payload" which can be empty for default behavior.
         * An empty interim payload will be filled in with a final payload if it's
         * supplied later.
         *
         * @param components the components to add
         * @param payload    payload associated with the terminal component
         * @param index      the current index in the components list
         * @param ips        payload associated with an interim component
         */
        public void populate(final List<C> components, final Optional<P> payload, final int index,
                final InterimPayloadSupplier<C, P> ips) {
            final var terminalIndex = components.size() - 1;
            if (index > terminalIndex) {
                return; // end recursion
            }
            final var isTerminal = index == terminalIndex;

            final var component = components.get(index);
            final var child = findChild(component).orElseGet(() -> {
                final var newNode = new Node(new ArrayList<>(components.subList(0, index + 1)),
                        isTerminal ? payload : ips.payload(components, index), this);
                // it's the delegate's job to either add it as a child or set attributes or just
                // ignore it
                Paths.this.nodePopulate.populate(this, newNode);
                return newNode;
            });
            if (isTerminal && child.payload().isEmpty()) {
                // this means that the child was defined before parent ("iterim") but now we
                // have the parent so payload is now available
                child.setPayload(payload);
            }
            child.populate(components, payload, index + 1, ips);
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
            for (C component : relativeComponents) {
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
            }
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

        /**
         * Runs action for every node in the tree, depth first
         *
         * @param action     callback to run
         * @param depthFirst true to run depth-first
         */
        public void forEach(Consumer<? super Node> action, boolean depthFirst) {
            if (!depthFirst) {
                action.accept(this);
            }
            children.stream().forEach(c -> c.forEach(action, depthFirst));
            if (depthFirst) {
                action.accept(this);
            }
        }
    }

    private final Node root = new Node(List.of(), Optional.empty(), null);
    private final PayloadComponentsSupplier<C, P> pcSupplier;
    private final NodePopulationStrategy<C, P> nodePopulate;

    /**
     * Constructs a Paths object with the specified root payload and payload
     * components supplier.
     *
     * @param rootPayload the payload for the initial root node
     * @param parser      the supplier for payload components
     */
    public Paths(final @NotNull PayloadComponentsSupplier<C, P> parser,
            @NotNull final NodePopulationStrategy<C, P> nodePopulate) {
        this.pcSupplier = parser;
        this.nodePopulate = nodePopulate;
    }

    /**
     * Constructs a Paths object with the specified root payload and payload
     * components supplier.
     *
     * @param rootPayload the payload for the initial root node
     * @param parser      the supplier for payload components
     */
    public Paths(final @NotNull PayloadComponentsSupplier<C, P> parser) {
        this(parser, (parent, newNode) -> parent.addChild(newNode));
    }

    /**
     * Returns the root node.
     *
     * @return the root node
     */
    public Paths<C, P>.Node root() {
        return root;
    }

    /**
     * Populates the tree structure with the specified payload, starting from root.
     *
     * @param payload the payload to populate
     * @param ips     what to do with payloads for child nodes defined before
     *                parents
     */
    public void populate(final P payload, final InterimPayloadSupplier<C, P> ips) {
        root.populate(pcSupplier.components(payload), Optional.of(payload), 0, ips);
    }

    /**
     * Populates the tree structure with the specified payload, starting from root
     * with default behavior for interim payloads.
     *
     * @param payload the payload to populate
     */
    public void populate(final P payload) {
        populate(payload, (components, index) -> Optional.empty());
    }

    /**
     * Finds a node in the tree structure based on the full path.
     *
     * @param fullPath the full path of the node to find
     * @return an Optional containing the found node if exists, or an empty Optional
     */
    public Optional<Node> findNode(String fullPath) {
        final var components = pcSupplier.components(fullPath);
        var current = root;
        for (final var component : components) {
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
        return Optional.empty();
    }

    /**
     * Runs action for every node in the tree
     *
     * @param action     callback to run
     * @param depthFirst whether to run depth-first
     */
    public void forEach(Consumer<? super Node> action, boolean depthFirst) {
        root.forEach(action, depthFirst);
    }

    /**
     * Runs action for every node in the tree
     *
     * @param action callback to run depth-first
     */
    public void forEach(Consumer<? super Node> action) {
        root.forEach(action, true);
    }

    /**
     * Enum to represent the type of merge operation performed.
     */
    public enum DeepMergeOperation {
        ADD, REPLACE
    }

    /**
     * Deeply merges the updates map into the original map. If both maps contain
     * nested maps for the same key, those maps are merged recursively. Otherwise,
     * the value from the updates map overwrites the value in the original map.
     *
     * @param original The original map to be updated.
     * @param updates  The map containing updates to be merged into the original
     *                 map.
     * @return A list of key and MergeOperation pairs representing the changes made
     *         during the merge.
     */
    public static List<Map.Entry<String, DeepMergeOperation>> deepMerge(final Map<String, Object> original,
            final Map<String, Object> updates) {
        final var changes = new ArrayList<Map.Entry<String, DeepMergeOperation>>();
        deepMerge(original, updates, "", changes);
        return changes;
    }

    @SuppressWarnings("unchecked")
    protected static void deepMerge(final Map<String, Object> original, final Map<String, Object> updates,
            final String prefix, final List<Map.Entry<String, DeepMergeOperation>> changes) {
        updates.forEach((mergeKey, mergeValue) -> {
            String fullKey = prefix.isEmpty() ? mergeKey : prefix + "." + mergeKey;
            if (original.containsKey(mergeKey)) {
                final var existingMap = original.get(mergeKey);
                if (existingMap instanceof Map && mergeValue instanceof Map) {
                    deepMerge((Map<String, Object>) existingMap, (Map<String, Object>) mergeValue, fullKey, changes);
                } else {
                    original.put(mergeKey, mergeValue);
                    changes.add(Map.entry(fullKey, DeepMergeOperation.REPLACE));
                }
            } else {
                original.put(mergeKey, mergeValue);
                changes.add(Map.entry(fullKey, DeepMergeOperation.ADD));
            }
        });
    }
}
