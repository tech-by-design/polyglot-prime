package org.techbd.util;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.PropertyPlaceholderHelper;

/**
 * The {@code Interpolate} class provides a mechanism for replacing placeholders
 * within strings using a combination of simple key-value pairs and Spring
 * Expression Language (SpEL) expressions. It supports configurable placeholder
 * delimiters and allows extension via custom functions.
 *
 * <p>
 * This class can be extended or inherited to add more custom functions. For
 * example:
 * 
 * <pre>
 * final var engine = new Interpolate(Map.of("fsHome", fsHome, "artifactId", artifactId)) {
 *     public String customFunction() {
 *         return "CustomValue";
 *     }
 * };
 * final var result = engine.interpolate("${fsHome}/${customFunction()}/${formattedDateNow('yyyy/MM/dd/HH')}/${artifactId}.json")
 * </pre>
 * </p>
 */
public class InterpolateEngine {
    private static final Logger LOG = LoggerFactory.getLogger(InterpolateEngine.class);
    private final PropertyPlaceholderHelper placeholderHelper;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final Map<String, Object> placeholders;
    private final List<String> requiredPlaceholderNames;
    private final StandardEvaluationContext spelCtx = new StandardEvaluationContext(this);

    /**
     * Constructs an {@code Interpolate} instance with the given variables and
     * default placeholder delimiters.
     *
     * @param vars a map of variables to be used for placeholder replacement
     */
    public InterpolateEngine(final Map<String, Object> vars, String... requiredPlaceholderNames) {
        this(vars, "${", "}", ":", true, requiredPlaceholderNames);
    }

    /**
     * Constructs an {@code Interpolate} instance with the given variables and
     * configurable placeholder delimiters.
     *
     * @param vars                           a map of variables to be used for
     *                                       placeholder replacement
     * @param prefix                         the prefix for placeholders
     * @param suffix                         the suffix for placeholders
     * @param defaultValueSeparator          the separator for default values within
     *                                       placeholders
     * @param ignoreUnresolvablePlaceholders whether to ignore unresolvable
     *                                       placeholders
     */
    public InterpolateEngine(final Map<String, Object> vars, final String prefix, final String suffix,
            final String defaultValueSeparator,
            final boolean ignoreUnresolvablePlaceholders, String... requiredPlaceholderNames) {
        this.placeholderHelper = new PropertyPlaceholderHelper(prefix, suffix, defaultValueSeparator,
                ignoreUnresolvablePlaceholders);
        this.placeholders = vars != null ? new HashMap<>(vars) : new HashMap<>();
        this.requiredPlaceholderNames = List.of(requiredPlaceholderNames);
        this.spelCtx.setVariables(Collections.unmodifiableMap(this.placeholders));
    }

    /**
     * Adds key-value pairs to the set of placeholders.
     *
     * @param keyValues an array of key-value pairs to be added
     * @return the current {@code Interpolate} instance for method chaining
     * @throws IllegalArgumentException if the key-values array is null or not in
     *                                  pairs
     */
    public InterpolateEngine withValues(final String... keyValues) {
        if (keyValues == null || keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Key-values must be in non-null pairs");
        }
        synchronized (this.placeholders) {
            for (int i = 0; i < keyValues.length; i += 2) {
                String key = keyValues[i];
                String value = keyValues[i + 1];
                this.placeholders.put(key, value);
                this.spelCtx.setVariable(key, value);
            }
        }
        return this;
    }

    /**
     * Interpolates the given source text by replacing placeholders with their
     * corresponding values.
     *
     * @param srcText the source text containing placeholders
     * @return the interpolated text with placeholders replaced by their values
     * @throws IllegalArgumentException if the source text is null
     */
    public String interpolate(final String srcText) {
        if (srcText == null) {
            throw new IllegalArgumentException("Source text must not be null");
        }
        return this.placeholderHelper.replacePlaceholders(srcText, this::resolvePlaceholder);
    }

    protected String resolvePlaceholder(final String placeholderName) {
        synchronized (this.placeholders) {
            if (this.placeholders.containsKey(placeholderName)) {
                final var value = this.placeholders.get(placeholderName);
                return value != null ? value.toString() : String.format("!{%s-NULL}", placeholderName);
            } else {
                if (this.requiredPlaceholderNames.contains(placeholderName)) {
                    // if it's a required placeholder but not supplied, just return it
                    return String.format("!{%s-missing}", placeholderName);
                }

                // if we get to here it's not a regular or required placeholder so let's see if
                // it's a custom function that needs to be resolved
                try {
                    return this.expressionParser.parseExpression(placeholderName).getValue(this.spelCtx, String.class);
                } catch (Exception e) {
                    LOG.error(String.format("placeholder '%s' error", placeholderName), e);
                    return String.format("!{error: %s}", e.toString());
                }
            }
        }
    }

    /**
     * Returns the current date formatted according to the given date pattern.
     * This function can be used within placeholders as
     * {@code ${formattedDateNow('yyyy/MM/dd/HH')}}.
     *
     * @param dateFmt the date format pattern
     * @return the formatted current date
     * @throws IllegalArgumentException if the date format is null or empty
     */
    public static String formattedDateNow(String dateFmt) {
        if (dateFmt == null || dateFmt.isEmpty()) {
            throw new IllegalArgumentException("Date format must not be null or empty");
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat(dateFmt);
        return dateFormat.format(new Date());
    }

    /**
     * Return the value of an environment variable.
     * 
     * @return String
     */
    public static String env(final String name) {
        return System.getenv(name);
    }

    /**
     * Return the current working directory (cwd), usually where Java process was
     * launched from.
     * 
     * @return String
     */
    public static String cwd() {
        return System.getProperty("user.dir");
    }
}
