package org.techbd.conf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.core.env.Environment;
import org.springframework.util.PropertyPlaceholderHelper;

/**
 * Encapsulates configuration that is common across all Technology by Design
 * (TechBD) packages.
 * 
 * Configurations specific to particular modules or packages will be in
 * submodules.
 */
public class Configuration {
    public static final Map<String, String> ownEnvVars = getEnvVarsAvailable(".*TECHBD.*");

    public class Servlet {
        public class HeaderName {
            public static final String PREFIX = "X-TechBD-";

            public class Request {
                public static final String TENANT_ID = PREFIX + "Tenant-ID";
                public static final String TENANT_NAME = PREFIX + "Tenant-Name";
            }

            public class Response {
                // in case they're necessary
            }
        }
    }

    static public List<String> checkProperties(Environment environment, String... propertyExpressions) {
        final var missingProperties = new ArrayList<String>();

        for (String propertyExpression : propertyExpressions) {
            missingProperties.add(propertyExpression);
            try {
                final var placeholderHelper = new PropertyPlaceholderHelper("${", "}", ":", true);
                final var resolvedPropertyName = placeholderHelper.replacePlaceholders(propertyExpression,
                        environment::getProperty);
                missingProperties.add(resolvedPropertyName);
                if (resolvedPropertyName != null) {
                    if (environment.getProperty(resolvedPropertyName) == null
                            && System.getenv(resolvedPropertyName) == null) {
                        missingProperties.add(resolvedPropertyName);
                    }
                } else {
                    missingProperties.add(propertyExpression); // Add the expression itself if it couldn't be resolved
                }
            } catch (IllegalArgumentException ex) {
                // Add the expression itself if it couldn't be resolved
                missingProperties.add(propertyExpression);
            }
        }

        return missingProperties;
    }

    static public Map<String, String> getEnvVarsAvailable(final String regexPattern) {
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
