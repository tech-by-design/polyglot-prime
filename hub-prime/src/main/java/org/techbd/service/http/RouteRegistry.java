package org.techbd.service.http;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import org.techbd.service.http.hub.prime.route.RouteMapping;

@Component
public class RouteRegistry implements ApplicationContextAware {


    private static final Map<String, RouteMapping> routeLookup = new LinkedHashMap<>();
    private static final Map<String, List<RouteInfo>> childRouteLookup = new LinkedHashMap<>();

    public record RouteInfo(
            String path,
            RouteMapping mapping) {
    }

    @Override
    public void setApplicationContext(ApplicationContext context)
            throws BeansException {

        Map<String, Object> controllers = context.getBeansWithAnnotation(Controller.class);

        for (Object controller : controllers.values()) {

            Class<?> targetClass = AopUtils.getTargetClass(controller);

            for (Method method : targetClass.getDeclaredMethods()) {

                RouteMapping routeMapping = AnnotationUtils.findAnnotation(
                        method,
                        RouteMapping.class);

                GetMapping getMapping = AnnotationUtils.findAnnotation(
                        method,
                        GetMapping.class);

                if (routeMapping != null
                        && getMapping != null
                        && getMapping.value().length > 0) {

                    String path = getMapping.value()[0];

                    routeLookup.put(path, routeMapping);
                    registerChildRoute(path, routeMapping);

                    System.out.println(
                            "Mapped route: "
                                    + path
                                    + " -> "
                                    + routeMapping);
                }
            }
        }
    }

    private void registerChildRoute(
            String path,
            RouteMapping mapping) {

        String parentPath = getParentPath(path);

        if (parentPath == null) {
            return;
        }

        childRouteLookup
                .computeIfAbsent(
                        parentPath,
                        key -> new ArrayList<>()) .add(new RouteInfo(path, mapping));

        childRouteLookup
                .get(parentPath)
                .sort(Comparator.comparingInt( route -> route.mapping().siblingOrder()));
    }

    private String getParentPath(String path) {

        int index = path.lastIndexOf('/');

        if (index <= 0) {
            return null;
        }
        return path.substring(0, index);
    }


    public static RouteMapping getRouteMapping(String uri) {
        return routeLookup.get(uri);
    }

    public static List<RouteInfo> getChildRoutes(String parentPath) {

        return childRouteLookup
                .getOrDefault(parentPath, List.of());
    }
}