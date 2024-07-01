package lib.aide.paths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class PathsJson<C, P> {
    public static final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

    @FunctionalInterface
    public interface PayloadJsonSupplier<C, P> {
        Optional<Object> toJson(Paths<C, P>.Node node, Paths<C, P> paths);
    }

    /**
     * Converts the Paths instance to a JSON string representation.
     *
     * @param paths           the Paths instance to convert
     * @param payloadRenderer an optional payload renderer
     * @return the JSON string representation of the Paths instance
     * @throws Exception if an error occurs during JSON conversion
     */
    public JsonNode toJson(final Paths<C, P> paths, final Optional<PayloadJsonSupplier<C, P>> payloadRenderer)
            throws Exception {
        final var result = new ArrayList<Object>();
        final var visitedNodes = new HashSet<Paths<C, P>.Node>();

        final var nodeMap = new HashMap<String, Object>();
        renderNode(paths.root(), nodeMap, paths, payloadRenderer, visitedNodes);
        result.add(nodeMap);

        return objectMapper.valueToTree(result);
    }

    /**
     * Recursively renders a node and its children into a map.
     *
     * @param node            the current node to render
     * @param nodeMap         the map to populate with the node's data
     * @param paths           the Paths instance
     * @param payloadRenderer an optional payload renderer
     * @param visitedNodes    a set of visited nodes to prevent cycles
     */
    protected void renderNode(final Paths<C, P>.Node node, final Map<String, Object> nodeMap,
            final Paths<C, P> paths, final Optional<PayloadJsonSupplier<C, P>> payloadRenderer,
            final Set<Paths<C, P>.Node> visitedNodes) {

        // Check for cycles
        if (!visitedNodes.add(node)) {
            return; // Node has already been visited, so skip it to avoid a cycle
        }

        payloadRenderer.flatMap(renderer -> renderer.toJson(node, paths))
                .ifPresent(payload -> nodeMap.put("payload", payload));

        nodeMap.put("isRoot", node.isRoot());
        nodeMap.put("isLeaf", node.isLeaf());
        nodeMap.put("components", node.components());

        nodeMap.put("descendants", node.descendants().stream()
                .map(n -> n.absolutePath())
                .toArray(String[]::new));

        nodeMap.put("siblings", node.siblings(false).stream()
                .map(n -> n.absolutePath())
                .toArray(String[]::new));

        nodeMap.put("ancestors", node.ancestors().stream()
                .map(n -> n.absolutePath())
                .toArray(String[]::new));

        if (!node.isLeaf()) {
            final var children = new ArrayList<Object>();
            for (var child : node.children()) {
                final var childMap = new HashMap<String, Object>();
                renderNode(child, childMap, paths, payloadRenderer, visitedNodes);
                children.add(childMap);
            }
            nodeMap.put("children", children);
        }
    }
}
