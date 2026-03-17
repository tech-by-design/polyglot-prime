package org.techbd.service.http;
import java.lang.reflect.Method;
import java.util.HashMap;
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

    private static final Map<String, RouteMapping> routeLookup = new HashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        Map<String, Object> controllers = context.getBeansWithAnnotation(Controller.class);

        for (Object controller : controllers.values()) {
            Class<?> targetClass = AopUtils.getTargetClass(controller);

            for (Method m : targetClass.getDeclaredMethods()) {
                RouteMapping rm = AnnotationUtils.findAnnotation(m, RouteMapping.class);
                GetMapping gm = AnnotationUtils.findAnnotation(m, GetMapping.class);

                if (rm != null && gm != null && gm.value().length > 0) {
                    String path = gm.value()[0]; // e.g. "/console/schema"
                    routeLookup.put(path, rm);

                    System.out.println("Mapped route: " + path + " -> " + rm);
                }
            }
        }
    }
    public static RouteMapping getRouteMapping(String uri) {
        return routeLookup.get(uri);
    }
}