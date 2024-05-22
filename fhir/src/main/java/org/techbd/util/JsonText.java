package org.techbd.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonText {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public JsonObjectResult getJsonObject(String jsonString) {
        List<Exception> exceptions = new ArrayList<>();
        Map<String, Object> jsonMap = null;

        try {
            jsonMap = objectMapper.readValue(jsonString, Map.class);
        } catch (Exception e) {
            exceptions.add(e);
        }

        if (!exceptions.isEmpty()) {
            return new JsonObjectResult.InvalidResult(jsonString, exceptions);
        }

        if (jsonMap == null) {
            exceptions.add(new RuntimeException("jsonMap should not be null"));
            return new JsonObjectResult.InvalidResult(jsonString, exceptions);
        }

        if (jsonMap.containsKey("$class")) {
            String className = (String) jsonMap.get("$class");
            try {
                Class<?> clazz = Class.forName(className);
                Object instance = clazz.getDeclaredConstructor(Map.class, String.class).newInstance(jsonMap,
                        jsonString);
                return new JsonObjectResult.ValidResult<>(jsonString, jsonMap, instance);
            } catch (ClassNotFoundException e) {
                return new JsonObjectResult.ValidResultClassNotFound(jsonString, jsonMap, className);
            } catch (Exception e) {
                return new JsonObjectResult.ValidResultClassNotInstantiated(jsonString, jsonMap, className, e);
            }
        }

        return new JsonObjectResult.ValidUntypedResult(jsonString, jsonMap);
    }

    public sealed interface JsonObjectResult permits JsonObjectResult.ValidUntypedResult, JsonObjectResult.ValidResult,
            JsonObjectResult.ValidResultClassNotFound, JsonObjectResult.ValidResultClassNotInstantiated, JsonObjectResult.InvalidResult {
        String originalText();

        boolean isValid();

        record ValidUntypedResult(String originalText, Map<String, Object> jsonObject) implements JsonObjectResult {
            @Override
            public boolean isValid() {
                return true;
            }
        }

        record ValidResult<T>(String originalText, Map<String, Object> jsonObject, T instance) implements JsonObjectResult {
            @Override
            public boolean isValid() {
                return true;
            }
        }

        record ValidResultClassNotFound(String originalText, Map<String, Object> jsonObject, String className)
                implements JsonObjectResult {
            @Override
            public boolean isValid() {
                return false;
            }
        }

        record ValidResultClassNotInstantiated(String originalText, Map<String, Object> jsonObject, String className,
                Exception exception) implements JsonObjectResult {
            @Override
            public boolean isValid() {
                return false;
            }
        }

        record InvalidResult(String originalText, List<Exception> exceptions) implements JsonObjectResult {
            @Override
            public boolean isValid() {
                return false;
            }
        }
    }
}
