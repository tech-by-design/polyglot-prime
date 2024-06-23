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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import lib.aide.paths.PathsJson;

@Service
public class RoutesTrees extends HashMap<String, RoutesTree> {
    private static final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

    public RoutesTrees(ApplicationContext applicationContext,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        final var handlerMethods = handlerMapping.getHandlerMethods();

        handlerMethods.forEach((info, handlerMethod) -> {
            final var routeAnn = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), RouteMapping.class);
            final var getMapAnn = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), GetMapping.class);

            if (routeAnn != null && getMapAnn != null) {
                final var patternVals = info.getPatternValues();
                if (patternVals != null) {
                    for (final var path : patternVals) {
                        final var provenance = Map.of("routeAnn", routeAnn.toString(), "getMapAnn",
                                getMapAnn.toString(), "class",
                                handlerMethod.getBeanType().getName(), "method", handlerMethod.getMethod().getName());
                        final var route = new RoutesTree.Route(routeAnn, path, provenance);

                        final var routesTree = this.computeIfAbsent(routeAnn.namespace(),
                                k -> new RoutesTree(routeAnn.namespace()));
                        routesTree.populate(route);
                    }
                }
            }
        });
    }

    public JsonNode toJson() {
        PathsJson.PayloadJsonSupplier<String, RoutesTree.Route> payloadRenderer = (node, paths) -> {
            final var route = node.payload();
            final var payloadMap = new HashMap<>();
            payloadMap.put("href", route.href());
            payloadMap.put("label", route.label());
            payloadMap.put("description", route.description());
            payloadMap.put("provenance", route.provenance());
            return Optional.of(payloadMap);
        };

        final var result = objectMapper.createObjectNode();
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
