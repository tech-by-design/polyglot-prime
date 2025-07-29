/**
 * Utility class for initializing and holding a static Spring {@link ApplicationContext}
 * for the application. This is primarily used in the {@code bridgelink} module to enable 
 * dependency injection and bean management without having to manually instantiate or wire objects.
 *
 * <p>
 * The context is configured using the {@link org.techbd.config.AppInitializationConfig} class,
 * which defines the application's base configuration and Spring-managed beans.
 * </p>
 *
 * <p>
 * This class does <b>not</b> manually manage active profiles. Instead, profile-specific configuration
 * (such as loading {@code application-{profile}.yml}) is handled by the Spring environment,
 * and optionally extended by the {@link org.techbd.conf.CoreLibYamlLoader} class, which implements
 * {@link org.springframework.boot.env.EnvironmentPostProcessor} to load shared core library
 * configuration from {@code nexus-core-lib/application.yml} and its profile-specific variants.
 * </p>
 *
 * <p>
 * This class provides:
 * <ul>
 *   <li>A static method {@link #getBean(Class)} to retrieve beans from the application context.</li>
 *   <li>A {@link io.opentelemetry.api.trace.Tracer} bean that attempts to fetch a tracing implementation
 *       from the context, falling back to the global tracer if none is available.</li>
 * </ul>
 * </p>
 */
package org.techbd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.techbd.config.AppInitializationConfig;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

public class SpringContextHolder {
    private static ApplicationContext context;
    private static final Logger LOG = LoggerFactory.getLogger(SpringContextHolder.class.getName());
    static {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ConfigurableEnvironment env = ctx.getEnvironment();
        ctx.register(AppInitializationConfig.class);
        ctx.refresh();
        context = ctx;
    }
    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }

    @Bean
    public Tracer tracer(ApplicationContext context) {
        Tracer tracer = null;
        try {
            tracer = context.getBean(Tracer.class);
        } catch (Exception e) {
            if (tracer == null) {
                tracer = GlobalOpenTelemetry.getTracer("default-tracer");
            }
        }
        if (tracer == null) {
            tracer = GlobalOpenTelemetry.getTracer("default-tracer");
        }
        return tracer;
    }
}
