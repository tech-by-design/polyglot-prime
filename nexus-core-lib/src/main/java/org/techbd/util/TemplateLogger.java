package org.techbd.util;


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

    /**
     * Appends thread name and version metadata to the log message.
     * 
     * @param message the original log message
     * @return message with metadata template appended
     */
    private String appendMetadata(String message) {
        return message + " | thread: {} | version: {}";
    }

    /**
     * Logs an informational message with thread name and version.
     * 
     * @param message the message format string
     * @param args arguments for the message format
     */
    public void info(String message, Object... args) {
        delegate.info(appendMetadata(message), extendArgs(args));
    }

    /**
     * Logs a warning message with thread name and version.
     * 
     * @param message the message format string
     * @param args arguments for the message format
     */
    public void warn(String message, Object... args) {
        delegate.warn(appendMetadata(message), extendArgs(args));
    }

    /**
     * Logs a debug message with thread name and version.
     * 
     * @param message the message format string
     * @param args arguments for the message format
     */
    public void debug(String message, Object... args) {
        delegate.debug(appendMetadata(message), extendArgs(args));
    }

    /**
     * Logs an error message with thread name and version.
     * 
     * @param message the message format string
     * @param args arguments for the message format
     */
    public void error(String message, Object... args) {
        delegate.error(appendMetadata(message), extendArgs(args));
    }

    /**
     * Logs a trace message with thread name and version.
     * 
     * @param message the message format string
     * @param args arguments for the message format
     */
    public void trace(String message, Object... args) {
        delegate.trace(appendMetadata(message), extendArgs(args));
    }

    /**
     * Extends the provided arguments array to include thread name and version.
     * 
     * @param args the original arguments
     * @return extended array with thread name and version appended
     */
    private Object[] extendArgs(Object... args) {
        Object[] extended = new Object[args.length + 2];
        System.arraycopy(args, 0, extended, 0, args.length);
        extended[args.length] = Thread.currentThread().getName();  // Thread name
        extended[args.length + 1] = version;                       // Version
        return extended;
    }

    /**
     * Checks if debug level logging is enabled.
     * 
     * @return true if debug is enabled
     */
    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    /**
     * Checks if info level logging is enabled.
     * 
     * @return true if info is enabled
     */
    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    /**
     * Checks if warn level logging is enabled.
     * 
     * @return true if warn is enabled
     */
    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    /**
     * Checks if error level logging is enabled.
     * 
     * @return true if error is enabled
     */
    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    /**
     * Checks if trace level logging is enabled.
     * 
     * @return true if trace is enabled
     */
    public boolean isTraceEnabled() {
        return delegate.isTraceEnabled();
    }

    /**
     * Gets the underlying SLF4J logger (use with caution).
     * Prefer using the templated methods instead.
     * 
     * @return the delegate SLF4J logger
     */
    public Logger getDelegate() {
        return delegate;
    }
}