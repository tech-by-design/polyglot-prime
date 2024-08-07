package org.techbd.service.http.hub.prime.route;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fasterxml.jackson.databind.JsonNode;

import lib.aide.paths.PathsJson;

@Service
public class RoutesTrees extends HashMap<String, RoutesTree> {
    public RoutesTrees(@SuppressWarnings("PMD.UnusedFormalParameter") ApplicationContext applicationContext,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        final var handlerMethods = handlerMapping.getHandlerMethods();

        handlerMethods.forEach((info, handlerMethod) -> {
            final var routeAnn = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), RouteMapping.class);
            final var getMapAnn = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), GetMapping.class);

            if (routeAnn != null && getMapAnn != null) {
                final var patternVals = info.getPatternValues();
                if (patternVals != null) {
                    for (var path : patternVals) {
                        final var provenance = Map.of("routeAnn", routeAnn.toString(), "getMapAnn",
                                getMapAnn.toString(), "class",
                                handlerMethod.getBeanType().getName(), "method", handlerMethod.getMethod().getName());
                        final var route = new RoutesTree.Route(routeAnn, path, provenance);
                        final var routesTree = this.computeIfAbsent(routeAnn.namespace(), k -> new RoutesTree());
                        routesTree.populate(route);
                    }
                }
            }
        });
    }

    public JsonNode toJson() {
        PathsJson.PayloadJsonSupplier<String, RoutesTree.Route> payloadRenderer = (node, paths) -> {
            return node.payload().map(route -> Map.of("href", route.href(), "label", route.label(), "description",
                    route.title(), "provenance", route.provenance()));
        };

        final var result = PathsJson.objectMapper.createObjectNode();
        final var pathsJson = new PathsJson<String, RoutesTree.Route>();
        forEach((namespace, routesTree) -> {
            try {
                result.set(namespace, pathsJson.toJson(routesTree, Optional.of(payloadRenderer)));
            } catch (Exception e) {
                result.put(namespace, e.toString());
            }
        });
        return result;
    }
}
