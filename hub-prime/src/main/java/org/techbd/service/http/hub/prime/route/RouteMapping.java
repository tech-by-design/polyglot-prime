package org.techbd.service.http.hub.prime.route;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.web.bind.annotation.Mapping;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Mapping
public @interface RouteMapping {
    String label();
    String title() default "";
    String namespace() default "prime";
    int siblingOrder() default Integer.MAX_VALUE; // MAX_VALUE means "don't care", greater than zero means sort in that order with lowest to highest
}
