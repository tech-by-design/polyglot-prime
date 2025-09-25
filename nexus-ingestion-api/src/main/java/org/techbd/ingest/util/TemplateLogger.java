package org.techbd.ingest.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * A wrapper around the standard SLF4J {@link Logger} that enforces
 * a consistent log message template across the application.
 *
 * <p>
 * Each log line automatically appends the application build version
 * (provided at construction time, usually from <code>pom.xml</code>)
 * to the end of the log message. This ensures that every log entry
 * clearly shows which build or deployment generated it.
 * </p>
 *
 * <h2>Example</h2>
 * Code:
 * <pre>{@code
 * templateLogger.info("Bundle processing start at {} for interaction id {}.", "10:05", "12345");
 * }</pre>
 *
 * Output:
 * <pre>
 * Bundle processing start at 10:05 for interaction id 12345 for TechBD Version : 0.1.21
 * </pre>
 *
 * <h2>Usage</h2>
 * Typical usage in a service or controller:
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
 *         log.info("Started processing for interaction id {}.", interactionId);
 *     }
 * }
 * }</pre>
 */

public class TemplateLogger {
    private final Logger delegate;
    private final String version;

    public TemplateLogger(Class<?> clazz, String version) {
        this.delegate = LoggerFactory.getLogger(clazz);
        this.version = version;
    }

    private String appendVersion(String message) {
        return message + " for TechBD Ingestion API Version : {}";
    }

    public void info(String message, Object... args) {
        delegate.info(appendVersion(message), extendArgs(args));
    }

    public void warn(String message, Object... args) {
        delegate.warn(appendVersion(message), extendArgs(args));
    }

    public void debug(String message, Object... args) {
        delegate.debug(appendVersion(message), extendArgs(args));
    }

    public void error(String message, Object... args) {
        delegate.error(appendVersion(message), extendArgs(args));
    }

    private Object[] extendArgs(Object... args) {
        Object[] extended = new Object[args.length + 1];
        System.arraycopy(args, 0, extended, 0, args.length);
        extended[args.length] = version;
        return extended;
    }

    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    public void trace(String message, Object... args) {
        delegate.trace(appendVersion(message), extendArgs(args));
    }

    public boolean isTraceEnabled() {
        return delegate.isTraceEnabled();
    }
}
