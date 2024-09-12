package lib.aide;

import com.jayway.jsonpath.JsonPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JsonContentAction provides a framework for defining rejection rules based on JSONPath
 * expressions and applying transformations to JSON objects using Spring SpEL expressions.
 * <p>
 * The class supports two main types of transformations:
 * - Appending objects to arrays.
 * - Applying key-value pairs to maps.
 * <p>
 * This is useful in scenarios where you need to reject or modify JSON content based on
 * specific rules, like handling errors or modifying content dynamically.
 * <p>
 * The class uses a builder pattern to allow chaining multiple rejection rules, and each
 * rule defines a JSONPath condition and a set of transformations to apply when the condition is met.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create JsonContentAction with reject rules
 * JsonContentAction jsonContentAction = new JsonContentAction.Builder()
 *     .withReject(
 *         ".location[?(@[0] =~ /Bundle\\.entry\\[\\d+\\]\\.resource\\/Patient\\/.*\\.meta\\.lastUpdated$/)]",
 *         List.of(new JsonContentAction.ApplyKeyValuePairs(
 *             JsonPath.compile("$.rejectionsMap"), Map.of(
 *                 "'injectedKey' + '1'", "'Injected value for ' + rule.description() + ' ' + rule.elaboration.e1"))),
 *         "test 1", Map.of("e1", "e1Value"))
 *     .withReject(
 *         ".location[?(@[0] =~ /Bundle\\.entry\\[\\d+\\]\\.resource\\/Patient\\/.*\\.meta\\.lastUpdated$/)]",
 *         List.of(new JsonContentAction.AppendObject(
 *             JsonPath.compile("$.rejectionsList"), Map.of(
 *                 "'injectedKey' + '1'", "'Injected value for ' + rule.description() + ' ' + rule.elaboration.e1"))),
 *         "test 2", Map.of("e1", "e1Value"))
 *     .build();
 *
 * // Execute the action on a sample JSON
 * Map<String, Object> json = Map.of(
 *     "severity", "error",
 *     "location", List.of("Bundle.entry[0].resource/Patient/12345/.meta.lastUpdated"),
 *     "rejectionsList", List.of(),
 *     "rejectionsMap", Map.of()
 * );
 *
 * JsonContentAction.ExecuteResults result = jsonContentAction.execute(json);
 *
 * // Assert that a rejection occurred and transformations were applied
 * Map<String, Object> transformedJson = result.rejections().get(0).transformed(new HashMap<>(json), Map.of());
 * }</pre>
 */
public class JsonContentAction {
    static private final Logger LOG = LoggerFactory.getLogger(JsonContentAction.class);

    // Algebraic Data Type for InjectJson using sealed classes
    public sealed interface InjectJson permits AppendObject, ApplyKeyValuePairs {
        /**
         * Performs the transformation on the target JSON based on the SpEL expressions provided.
         *
         * @param targetJson the target JSON to be transformed
         * @param spelContext the Spring SpEL evaluation context
         * @param parser the SpEL expression parser
         */
        void perform(Map<String, Object> targetJson, StandardEvaluationContext spelContext, ExpressionParser parser);
    }

    /**
     * AppendObject assumes that {@code injectInJsonPath} is an array and appends
     * the evaluated SpEL expressions to that array.
     */
    public record AppendObject(JsonPath injectInJsonPath, Map<String, String> spelExpressions) implements InjectJson {
        @Override
        public void perform(final Map<String, Object> targetJson, final StandardEvaluationContext spelContext,
                            final ExpressionParser parser) {
            final var newContent = new HashMap<String, Object>();
            try {
                spelExpressions.forEach((key, value) -> {
                    newContent.put(parser.parseExpression(key).getValue(spelContext, String.class),
                            parser.parseExpression(value).getValue(spelContext, Object.class));
                });
            } catch (Exception e) {
                newContent.put("error", e.toString());
                LOG.error("AppendObject error in creating newContent in perform: " + injectInJsonPath, e);
            }
            try {
                final var context = JsonPath.parse(targetJson);
                final var existingList = context.read(injectInJsonPath.getPath(), List.class);
                @SuppressWarnings("unchecked")
                final var newList = new ArrayList<HashMap<String, Object>>(existingList);
                newList.add(newContent);
                context.set(injectInJsonPath, newList);
            } catch (Exception e) {
                LOG.error("AppendObject perform: " + injectInJsonPath, e);
            }
        }
    }

    /**
     * ApplyKeyValuePairs assumes that {@code injectInJsonPath} is a map and applies
     * the evaluated SpEL expressions as key-value pairs.
     */
    public record ApplyKeyValuePairs(JsonPath injectInJsonPath, Map<String, String> spelExpressions)
            implements InjectJson {
        @Override
        public void perform(final Map<String, Object> targetJson, final StandardEvaluationContext spelContext,
                            final ExpressionParser parser) {
            try {
                final var context = JsonPath.parse(targetJson);
                final var existingMap = context.read(injectInJsonPath.getPath(), Map.class);
                @SuppressWarnings("unchecked")
                final var newMap = new HashMap<String, Object>(existingMap);
                spelExpressions.forEach((key, value) -> {
                    try {
                        final var evaluatedKey = parser.parseExpression(key).getValue(spelContext, String.class);
                        final var evaluatedValue = parser.parseExpression(value).getValue(spelContext, Object.class);
                        newMap.put(evaluatedKey, evaluatedValue);
                    } catch (Exception e) {
                        LOG.error("ApplyKeyValuePairs perform: " + injectInJsonPath + " " + key, e);
                    }
                });
                context.set(injectInJsonPath, newMap);
            } catch (Exception e) {
                LOG.error("ApplyKeyValuePairs perform: " + injectInJsonPath, e);
            }
        }
    }

    // Algebraic Data Type for ActionRule using sealed classes
    public sealed interface ActionRule permits RejectActionRule {
    }

    /**
     * RejectActionRule defines a rejection rule with a JSONPath condition and a list of
     * transformations to apply if the condition is met.
     *
     * @param ifJsonPathFound the JSONPath expression to find the matching condition
     * @param injects         the list of transformations to apply if the condition is met
     * @param description     a description of the rejection rule
     * @param elaboration     additional details or context for the rule
     */
    public record RejectActionRule(JsonPath ifJsonPathFound, List<InjectJson> injects, String description,
                                   Map<String, Object> elaboration) implements ActionRule {
    }

    // Algebraic Data Type for ExecuteResult using sealed classes
    public sealed interface ExecuteResult permits AcceptContent, RejectContent {
    }

    /**
     * AcceptContent represents a case where no rejection occurred and the original JSON is returned.
     */
    public record AcceptContent(Map<String, Object> originalJson) implements ExecuteResult {
    }

    /**
     * RejectContent represents a case where a rejection occurred and the JSON was transformed.
     *
     * @param originalJson the original JSON before transformation
     * @param rule         the rule that caused the rejection
     */
    public record RejectContent(Map<String, Object> originalJson, RejectActionRule rule) implements ExecuteResult {
        /**
         * Transforms the given JSON using the transformations defined in the rejection rule.
         *
         * @param targetJson    the target JSON to be transformed
         * @param spelVariables additional variables for use in SpEL expressions
         * @return the transformed JSON
         */
        public Map<String, Object> transformed(final Map<String, Object> targetJson,
                                               final Map<String, Object> spelVariables) {
            final var spelContext = new StandardEvaluationContext(Map.of("content", originalJson, "rule", rule));
            spelContext.addPropertyAccessor(new MapAccessor());
            spelVariables.forEach(spelContext::setVariable);
            final var parser = new SpelExpressionParser();

            for (final var inject : rule.injects()) {
                inject.perform(targetJson, spelContext, parser);
            }

            return targetJson;
        }
    }

    /**
     * ExecuteResults stores the results of applying the rejection rules, including any rejections
     * and the content that was accepted without rejection.
     *
     * @param rejections    the list of rejected and transformed content
     * @param acceptContent the content that was accepted without rejection
     */
    public record ExecuteResults(List<RejectContent> rejections, AcceptContent acceptContent) {
    }

    private final List<ActionRule> actionRules = new ArrayList<>();

    /**
     * Builder pattern for creating {@code JsonContentAction} instances.
     */
    public static class Builder {
        private final JsonContentAction instance = new JsonContentAction();

        /**
         * Adds a reject action rule that defines a condition and a transformation to apply
         * when the condition is met.
         *
         * @param ifJsonPathFound the JSONPath condition to match
         * @param injects         the list of transformations to apply
         * @param description     a description of the rule
         * @param elaboration     additional context or details for the rule
         * @return the builder instance for method chaining
         */
        public Builder withReject(final JsonPath ifJsonPathFound, final List<InjectJson> injects, String description,
                                  Map<String, Object> elaboration) {
            instance.actionRules.add(new RejectActionRule(ifJsonPathFound, injects, description, elaboration));
            return this;
        }

        /**
         * Adds a reject action rule using a string-based JSONPath.
         *
         * @param ifJsonPathFound the string-based JSONPath condition to match
         * @param injects         the list of transformations to apply
         * @param description     a description of the rule
         * @param elaboration     additional context or details for the rule
         * @return the builder instance for method chaining
         */
        public Builder withReject(final String ifJsonPathFound, final List<InjectJson> injects, String description,
                                  Map<String, Object> elaboration) {
            return withReject(JsonPath.compile(ifJsonPathFound), injects, description, elaboration);
        }

        /**
         * Builds the {@code JsonContentAction} instance with the defined rules.
         *
         * @return the {@code JsonContentAction} instance
         */
        public JsonContentAction build() {
            return instance;
        }
    }

    /**
     * Executes the rejection rules on the provided JSON and returns the results.
     *
     * @param json the JSON object to be processed
     * @return the results of the execution, including any rejections and accepted content
     */
    public ExecuteResults execute(final Map<String, Object> json) {
        final var rejections = new ArrayList<RejectContent>();

        for (final var rule : actionRules) {
            if (rule instanceof RejectActionRule rejectActionRule) {
                final var result = JsonPath.parse(json).read(rejectActionRule.ifJsonPathFound());
                if (result != null) {
                    rejections.add(new RejectContent(json, rejectActionRule));
                }
            }
        }

        return new ExecuteResults(
                rejections.isEmpty() ? List.of() : rejections,
                new AcceptContent(json));
    }
}
