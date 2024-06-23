package org.techbd.service.http.hub.prime.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.text.WordUtils;

import lib.aide.paths.Paths;

public class RoutesTree extends Paths<String, RoutesTree.Route> {
    private final String namespace;

    public RoutesTree(final String namespace) {
        super(new Route(namespace, namespace, Optional.empty(), Optional.empty(),
                Optional.of(Map.of("class", RoutesTree.class.getName()))),
                new RouteComponentsSupplier(namespace));
        this.namespace = namespace;
    }

    public String getNamespace() {
        return namespace;
    }

    public record HtmlAnchor(Optional<Route> route, Optional<String> href, String text) {
        public HtmlAnchor(Paths<String, Route>.Node node) {
            this(node.payload(), node.payload().map(p -> p.href()),
                    node.payload().map(p -> p.label()).orElse(WordUtils.capitalize(node.components().getLast())));
        }
    }

    public record Route(String href, String label, Optional<String> description, Optional<Integer> siblingOrder,
            Optional<Object> provenance) {

        public Route(String href, String label, String description, int siblingOrder, Object provenance) {
            this(href, label,
                    Optional.ofNullable(description != null ? description.isBlank() ? null : description : null),
                    siblingOrder == Integer.MAX_VALUE ? Optional.empty() : Optional.of(Integer.valueOf(siblingOrder)),
                    Optional.ofNullable(provenance));
        }

        public Route(RouteMapping mapping, String href, Object provenance) {
            this(href,
                    mapping.label(),
                    mapping.description(),
                    mapping.siblingOrder(),
                    provenance);
        }
    }

    public static class RouteComponentsSupplier implements PayloadComponentsSupplier<String, Route> {
        private final String root;

        public RouteComponentsSupplier(final String root) {
            this.root = root == null ? "unknown" : root;
        }

        @Override
        public List<String> components(final String path) {
            final var result = new ArrayList<String>(List.of(path.split("/")));
            // we assume that / is the root prefix (which signifies the namespace)
            result.set(0, root);
            return result;
        }

        @Override
        public List<String> components(final Route payload) {
            return components(payload.href());
        }

        @Override
        public String assemble(final List<String> components) {
            return String.join("/", components);
        }
    }

    public static class Builder {
        private final RoutesTree routesTree;

        public Builder(String namespace) {
            this.routesTree = new RoutesTree(namespace);
        }

        public Builder addRoute(Route route) {
            this.routesTree.populate(route);
            return this;
        }

        public RoutesTree build() {
            return this.routesTree;
        }
    }
}
