/**
 * Utility class for initializing and holding a static Spring {@link ApplicationContext}
 * for the application. This context is configured to:
 * <ul>
 *   <li>Load Java beans annotated with Spring annotations from the {@code bridgelink} module,
 *       specifically via the {@link org.techbd.config.AppInitializationConfig} configuration class.</li>
 *   <li>Load configuration properties from YAML files (e.g., {@code application.yml} and
 *       profile-specific files like {@code application-{profile}.yml}) using the Spring
 *       {@link org.springframework.boot.env.YamlPropertySourceLoader}.</li>
 *   <li>Support Spring's active profile mechanism by reading the {@code SPRING_PROFILES_ACTIVE}
 *       environment variable and loading the corresponding profile-specific YAML configuration,
 *       similar to how Spring Boot handles configuration profiles.</li>
 * </ul>
 * <p>
 * Provides a static method to retrieve beans from the context, and a {@link Tracer} bean
 * that falls back to a default tracer if none is defined in the context.
 * </p>
 */
package org.techbd;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;


public class SpringContextHolder {
    private static ApplicationContext context;
    private static final Logger LOG = LoggerFactory.getLogger(SpringContextHolder.class.getName());
    static {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ConfigurableEnvironment env = ctx.getEnvironment();

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

        try {
            List<PropertySource<?>> yamlProps = loader.load("nexus-core-lib/application.yml", new ClassPathResource("nexus-core-lib/application.yml"));

            for (PropertySource<?> ps : yamlProps) {
                env.getPropertySources().addLast(ps);
            }

            String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
            LOG.info("#################################Active Spring profile: {}", activeProfile);
            if (activeProfile != null && !activeProfile.isEmpty()) {
                List<PropertySource<?>> profileProps = loader.load(
                    "nexus-core-lib/application-" + activeProfile + ".yml",
                    new ClassPathResource("nexus-core-lib/application-" + activeProfile + ".yml")
                );

                for (PropertySource<?> ps : profileProps) {
                    env.getPropertySources().addFirst(ps);
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
        @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.US_EAST_1) // Change this to your AWS region
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(Region.US_EAST_1) // Change this to your AWS region
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
