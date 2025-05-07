package org.techbd.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.techbd.config.Configuration;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.nimbusds.jose.util.StandardCharset;

public class JsonText {
    public static final ObjectMapper objectMapper = Configuration.objectMapper;

    /**
     * Parses a JSON string and returns a {@link JsonObjectResult} indicating the
     * outcome.
     *
     * <p>
     * This method attempts to parse the provided JSON string into a {@link Map}. If
     * the JSON string contains a key named {@code $class}, it tries to instantiate
     * an object of that class using the parsed JSON map and the original JSON
     * string. The result of the parsing and potential instantiation is encapsulated
     * in a {@link JsonObjectResult} object.
     * </p>
     *
     * <p>
     * Possible results include:
     * </p>
     * <ul>
     * <li>{@link JsonObjectResult.ValidResult} - The JSON was parsed successfully
     * and an instance of the specified class was created.</li>
     * <li>{@link JsonObjectResult.ValidResultClassNotFound} - The JSON was parsed
     * successfully, but the specified class was not found.</li>
     * <li>{@link JsonObjectResult.ValidResultClassNotInstantiated} - The JSON was
     * parsed successfully, but the specified class could not be instantiated.</li>
     * <li>{@link JsonObjectResult.ValidUntypedResult} - The JSON was parsed
     * successfully but does not specify a class.</li>
     * <li>{@link JsonObjectResult.InvalidResult} - The JSON could not be parsed or
     * other errors occurred.</li>
     * </ul>
     *
     * <p>
     * Usage example:
     * </p>
     * 
     * <pre>
     * {@code
     * String jsonString = "{\"$class\":\"com.example.MyClass\", \"key\":\"value\"}";
     * JsonObjectResult result = myObject.getJsonObject(jsonString);
     * 
     * if (result instanceof JsonObjectResult.ValidResult) {
     *     // handle valid result
     * } else if (result instanceof JsonObjectResult.InvalidResult) {
     *     // handle invalid result
     * }
     * }
     * </pre>
     *
     * @param jsonString the JSON string to parse
     * @return a {@link JsonObjectResult} object representing the outcome of the
     *         parsing and potential instantiation
     */
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
            JsonObjectResult.ValidResultClassNotFound, JsonObjectResult.ValidResultClassNotInstantiated,
            JsonObjectResult.InvalidResult {
        String originalText();

        boolean isValid();

        record ValidUntypedResult(String originalText, Map<String, Object> jsonObject) implements JsonObjectResult {
            @Override
            public boolean isValid() {
                return true;
            }
        }

        record ValidResult<T>(String originalText, Map<String, Object> jsonObject, T instance)
                implements JsonObjectResult {
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

    /**
     * Custom serializer for byte arrays.
     * 
     * <p>
     * This serializer attempts to convert a byte array to a string and parse it as
     * JSON. If the string is valid JSON, it serializes the byte array as JSON. If
     * the string is not valid JSON, it serializes the byte array as a plain string.
     * </p>
     * 
     * <p>
     * To use this serializer, annotate the relevant field or class with
     * {@code @JsonSerialize(using = ByteArrayToStringOrJsonSerializer.class)}.
     * </p>
     * 
     * <p>
     * Example usage:
     * </p>
     * 
     * <pre>
     * {@code
     * public class Example {
     * 
     *     @JsonSerialize(using = ByteArrayToStringOrJsonSerializer.class)
     *     private byte[] byteArray;
     * 
     *     // getters and setters
     * }
     * }
     * </pre>
     * 
     * <p>
     * In this example, the {@code byteArray} field will be serialized using the
     * {@code ByteArrayToStringOrJsonSerializer},
     * which will serialize the value as JSON if it's valid JSON, or as a plain
     * string otherwise.
     * </p>
     * 
     * @see com.fasterxml.jackson.databind.JsonSerializer
     * @see com.fasterxml.jackson.databind.annotation.JsonSerialize
     */
    public static class ByteArrayToStringOrJsonSerializer extends StdSerializer<byte[]> {

        public ByteArrayToStringOrJsonSerializer() {
            this(null);
        }

        public ByteArrayToStringOrJsonSerializer(Class<byte[]> t) {
            super(t);
        }

        @Override
        public void serialize(byte[] value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            String stringValue = new String(value, StandardCharset.UTF_8);

            try {
                final var jsonNode = objectMapper.readTree(stringValue);

                // If we reach here, stringValue is valid JSON
                gen.writeTree(jsonNode);
            } catch (JsonParseException e) {
                // stringValue is not valid JSON, write it as a string
                gen.writeString(stringValue);
            }
        }
    }

    /**
     * Custom serializer for JSON text.
     * 
     * <p>
     * This serializer attempts to parse a string as JSON. If the string is valid
     * JSON, it serializes the string as JSON. If the string is not valid JSON, it
     * serializes the string as a plain string.
     * </p>
     * 
     * <p>
     * To use this serializer, annotate the relevant field or class with
     * {@code @JsonSerialize(using = JsonTextSerializer.class)}.
     * </p>
     * 
     * <p>
     * Example usage:
     * </p>
     * 
     * <pre>
     * {@code
     * public class Example {
     * 
     *     @JsonSerialize(using = JsonTextSerializer.class)
     *     private String jsonText;
     * 
     *     // getters and setters
     * }
     * }
     * </pre>
     * 
     * <p>
     * In this example, the {@code jsonText} field will be serialized using the
     * {@code JsonTextSerializer}, which will serialize the value as JSON if it's
     * valid JSON, or as a plain string
     * otherwise.
     * </p>
     * 
     * @see com.fasterxml.jackson.databind.JsonSerializer
     * @see com.fasterxml.jackson.databind.annotation.JsonSerialize
     */
    public static class JsonTextSerializer extends StdSerializer<String> {

        public JsonTextSerializer() {
            this(null);
        }

        public JsonTextSerializer(Class<String> t) {
            super(t);
        }

        @Override
        public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            try {
                final var jsonNode = objectMapper.readTree(value);

                // If we reach here, stringValue is valid JSON
                gen.writeTree(jsonNode);
            } catch (JsonParseException e) {
                // stringValue is not valid JSON, write it as a string
                gen.writeString(value);
            }
        }
    }

}
