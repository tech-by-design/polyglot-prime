package org.techbd.ingest.util;

import org.springframework.stereotype.Component;
import org.techbd.ingest.config.AppConfig;

/**
 * Spring-managed factory component for creating {@link TemplateLogger} instances.
 *
 * <p>
 * This class centralizes logger creation to ensure that all loggers
 * are initialized with the application build version from {@link AppConfig}.
 * By doing this, every log entry generated through a {@link TemplateLogger}
 * will automatically append the current build version at the end of the log message
 * (e.g., <code>... for TechBD Version : 1.0.0</code>), ensuring better traceability
 * across environments and deployments.
 * </p>
 *
 * <h2>Usage</h2>
 * Example usage in a service or controller:
 * <pre>{@code
 * @RestController
 * public class MyServiceOrController {
 *
 *     private final TemplateLogger log;
 *
 *     public MyServiceOrController(AppLogger appLogger) {
 *         this.log = appLogger.getLogger(MyServiceOrController.class);
 *     }
 *
 *     public void process(String interactionId) {
 *         log.info("Bundle processing start at {} for interaction id {}.", "10:05", interactionId);
 *     }
 * }
 * }</pre>
 */

@Component
public class AppLogger {
    private final AppConfig appConfig;

    /**
     * Constructs an {@code AppLogger} with the given application configuration.
     *
     * @param appConfig the application configuration containing build details
     */
    public AppLogger(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * Creates a new {@link TemplateLogger} for the specified class.
     *
     * <p>
     * The returned logger will automatically include the application build version
     * (from {@link AppConfig}) in every log entry.
     * </p>
     *
     * @param clazz the class for which the logger is being created
     * @return a {@link TemplateLogger} bound to the given class
     */
    public TemplateLogger getLogger(Class<?> clazz) {
        return new TemplateLogger(clazz, appConfig.getVersion());
    }
}