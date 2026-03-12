package org.techbd.corelib.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.core.env.Environment;
import org.springframework.util.PropertyPlaceholderHelper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Encapsulates configuration that is common across all Technology by Design
 * (TechBD) packages.
 * 
 * Configurations specific to particular modules or packages will be in
 * submodules.
 */
public class Configuration {
    // this is the "global" thread-safe objectMapper for use when a special instance
    // is not required; use this when possible since it's got good "default"
    // behavior
    public static final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();
    public static final ObjectMapper objectMapperConcise = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

    public static final Map<String, String> ownEnvVars = getEnvVarsAvailable(".*TECHBD.*");

    public static class Servlet {
        public static class HeaderName {
            public static final String PREFIX = "X-TechBD-";

            public static class Request {
                public static final String TENANT_ID = PREFIX + "Tenant-ID";
                public static final String TENANT_NAME = PREFIX + "Tenant-Name";
            }

            public static class Response {
                // in case they're necessary
            }
        }
    }
    public static List<String> checkProperties(Environment environment, String... propertyExpressions) {
        final var missingProperties = new ArrayList<String>();

        for (String propertyExpression : propertyExpressions) {
            missingProperties.add(propertyExpression);
            try {
                final var placeholderHelper = new PropertyPlaceholderHelper("${", "}", ":", true);
                final var resolvedPropertyName = placeholderHelper.replacePlaceholders(propertyExpression,
                        environment::getProperty);
                missingProperties.add(Optional.ofNullable(resolvedPropertyName).orElseThrow());
                if (environment.getProperty(resolvedPropertyName) == null
                        && System.getenv(resolvedPropertyName) == null) {
                    missingProperties.add(resolvedPropertyName);
                }
            } catch (IllegalArgumentException | NoSuchElementException ex) {
                // Add the expression itself if it couldn't be resolved
                missingProperties.add(propertyExpression);
            }
        }

        return missingProperties;
    }
    public static Map<String, String> getEnvVarsAvailable(final String regexPattern) {
        final var matchingVariables = new HashMap<String, String>();
        final var pattern = Pattern.compile(regexPattern);

        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            if (pattern.matcher(entry.getKey()).matches()) {
                matchingVariables.put(entry.getKey(), entry.getValue());
            }
        }

        return matchingVariables;
    }
}
