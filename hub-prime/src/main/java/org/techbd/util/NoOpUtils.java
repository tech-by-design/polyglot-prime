package org.techbd.util;

/**
 * Utility class for handling exceptions by ignoring them.
 * <p>
 * This class is mainly used to avoid warnings from static code analysis tools
 * like PMD which flag empty catch blocks.
 * </p>
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * try {
 *     // some code that may throw an exception
 * } catch (Exception e) {
 *     NoOpUtils.ignore(e);
 * }
 * </pre>
 */
public class NoOpUtils {

    /**
     * Ignores the given exception.
     * <p>
     * This method is intentionally left blank to provide a way to
     * explicitly ignore exceptions without triggering static code
     * analysis warnings.
     * </p>
     *
     * @param e the exception to be ignored
     */
    public static void ignore(Exception e) {
        // This method intentionally left blank
    }
}
