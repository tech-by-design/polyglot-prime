package org.techbd.service.http.hub.prime.route;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.text.WordUtils;
import org.techbd.conf.Configuration;

import com.fasterxml.jackson.databind.JsonNode;

import lib.aide.paths.Paths;

public class RoutesTree extends Paths<String, RoutesTree.Route> {
    public RoutesTree() {
        super(new RouteComponentsSupplier());
    }

    public record HtmlAnchor(Optional<Route> route, Optional<String> href, String text) {
        public HtmlAnchor(Paths<String, Route>.Node node) {
            this(node.payload(), node.payload().map(p -> p.href()),
                    node.payload().map(p -> p.label()).orElse(WordUtils.capitalize(node.components().getLast())));
        }

        public Map<String, String> intoMap() {
            final var result = new HashMap<String, String>();
            result.put("text", text);
            if (href.isPresent()) {
                result.put("href", href.orElseThrow());
            }
            return result;
        }

        public JsonNode toJson() {
            return Configuration.objectMapper.valueToTree(intoMap());
        }
    }

    public record Route(String href, String label, Optional<String> title, Optional<Integer> siblingOrder,
            Optional<Object> provenance) {

        public Route(String href, String label, String title, int siblingOrder, Object provenance) {
            this(href, label,
                    Optional.ofNullable(title != null ? title.isBlank() ? null : title : null),
                    siblingOrder == Integer.MAX_VALUE ? Optional.empty() : Optional.of(Integer.valueOf(siblingOrder)),
                    Optional.ofNullable(provenance));
        }

        public Route(RouteMapping mapping, String href, Object provenance) {
            this(href,
                    mapping.label(),
                    mapping.title(),
                    mapping.siblingOrder(),
                    provenance);
        }
    }

    public static class RouteComponentsSupplier implements PayloadComponentsSupplier<String, Route> {
        @Override
        public List<String> components(final String path) {
            return List.of(path.split("/"));
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

        public Builder() {
            this.routesTree = new RoutesTree();
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
