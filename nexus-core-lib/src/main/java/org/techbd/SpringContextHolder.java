package org.techbd;

import java.io.IOException;
import java.util.List;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.techbd.config.AppInitializationConfig;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;


public class SpringContextHolder {
    private static ApplicationContext context;

    static {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ConfigurableEnvironment env = ctx.getEnvironment();

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

        try {
            List<PropertySource<?>> yamlProps = loader.load("nexus-core-lib/application.yml", new ClassPathResource("nexus-core-lib/application.yml"));

            for (PropertySource<?> ps : yamlProps) {
                env.getPropertySources().addLast(ps);
            }

            String activeProfile = "devl";

            if (activeProfile != null && !activeProfile.isEmpty()) {
                List<PropertySource<?>> profileProps = loader.load(
                    "nexus-core-lib/application-" + activeProfile + ".yml",
                    new ClassPathResource("nexus-core-lib/application-" + activeProfile + ".yml")
                );

                for (PropertySource<?> ps : profileProps) {
                    env.getPropertySources().addLast(ps);
                }

                env.setActiveProfiles(activeProfile);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to load YAML properties", e);
        }

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

        // Fallback to the GlobalOpenTelemetry Tracer if not found
        if (tracer == null) {
            tracer = GlobalOpenTelemetry.getTracer("default-tracer");
        }
        
        return tracer;
    }
}
