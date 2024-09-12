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

public class JsonContentAction {
    static private final Logger LOG = LoggerFactory.getLogger(JsonContentAction.class);

    // Algebraic Data Type for InjectJson using sealed classes
    public sealed interface InjectJson permits AppendObject, ApplyKeyValuePairs {
        void perform(Map<String, Object> targetJson, StandardEvaluationContext spelContext, ExpressionParser parser);
    }

    // AppendObject assumes that injectInJsonPath is an array and appends
    // spelExpressions
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
                // Clone the existing list and add newContent
                @SuppressWarnings("unchecked")
                final var newList = new ArrayList<HashMap<String, Object>>(existingList);
                newList.add(newContent);
                context.set(injectInJsonPath, newList);
            } catch (Exception e) {
                LOG.error("AppendObject perform: " + injectInJsonPath, e);
            }
        }
    }

    // ApplyKeyValuePairs assumes that injectInJsonPath is an object and applies
    // spelExpressions as key value pairs
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
                        final var evaluatedValue = parser.parseExpression(value).getValue(spelContext,
                                Object.class);
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

    // Specific RejectActionRule instance that provides the JSONPath checks and
    // transformations
    public record RejectActionRule(JsonPath ifJsonPathFound, List<InjectJson> injects, String description,
            Map<String, Object> elaboration)
            implements ActionRule {
    }

    // Algebraic Data Type for ExecuteResult using sealed classes
    public sealed interface ExecuteResult permits AcceptContent, RejectContent {
    }

    // Accept case: No rejection occurred, returning the original content
    public record AcceptContent(Map<String, Object> originalJson) implements ExecuteResult {
    }

    // Reject case: Rejection occurred with original and transformed content
    public record RejectContent(Map<String, Object> originalJson, RejectActionRule rule) implements ExecuteResult {
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

    // New ExecuteResults record to store the results of execution
    public record ExecuteResults(List<RejectContent> rejections, AcceptContent acceptContent) {
    }

    private final List<ActionRule> actionRules = new ArrayList<>();

    // Builder pattern for creating JsonContentAction instances
    public static class Builder {
        private final JsonContentAction instance = new JsonContentAction();

        // Method to add RejectActionRule action by passing JsonPath objects
        public Builder withReject(final JsonPath ifJsonPathFound, final List<InjectJson> injects, String description,
                Map<String, Object> elaboration) {
            instance.actionRules.add(new RejectActionRule(ifJsonPathFound, injects, description, elaboration));
            return this;
        }

        // Method to add RejectActionRule action by passing strings for jsonPaths
        public Builder withReject(final String ifJsonPathFound, final List<InjectJson> injects, String description,
                Map<String, Object> elaboration) {
            withReject(JsonPath.compile(ifJsonPathFound), injects, description, elaboration);
            return this;
        }

        public JsonContentAction build() {
            return instance;
        }
    }

    // Method to execute rules on the provided JSON object and return ExecuteResults
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
