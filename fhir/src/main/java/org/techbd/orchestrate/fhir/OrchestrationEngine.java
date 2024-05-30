package org.techbd.orchestrate.fhir;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.text.StringEscapeUtils;
import java.util.stream.StreamSupport;

import org.techbd.util.JsonText.JsonTextSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.StrictErrorHandler;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationOptions;
import jakarta.validation.constraints.NotNull;

/**
 * The {@code OrchestrationEngine} class is responsible for managing and
 * orchestrating FHIR validation sessions.
 * It contains inner classes and interfaces that define the structure and
 * behavior of the validation engines
 * and the orchestration sessions.
 * <p>
 * This class also includes a cache for {@link ValidationEngine} instances to
 * ensure that only one instance of
 * each type is used for a given FHIR profile URL.
 * <p>
 * Usage example:
 * 
 * <pre>{@code
 * OrchestrationEngine engine = new OrchestrationEngine();
 *
 * OrchestrationEngine.OrchestrationSession session1 = engine.session()
 *         .withPayloads(List.of("payload1", "payload2"))
 *         .withFhirProfileUrl("http://example.com/fhirProfile")
 *         .addHapiValidationEngine()
 *         .addHl7ValidationApiEngine()
 *         .addInfernoValidationEngine()
 *         .build();
 *
 * OrchestrationEngine.OrchestrationSession session2 = engine.session()
 *         .withPayloads(List.of("payload3", "payload4"))
 *         .withFhirProfileUrl("http://example.com/fhirProfile")
 *         .addHapiValidationEngine()
 *         .build();
 *
 * engine.orchestrate(session1, session2);
 *
 * for (OrchestrationEngine.OrchestrationSession session : engine.getSessions()) {
 *     for (OrchestrationEngine.ValidationResult result : session.getValidationResults()) {
 *         System.out.println("Is valid: " + result.isValid());
 *         for (OrchestrationEngine.ValidationIssue issue : result.getIssues()) {
 *             System.out.println("Issue: " + issue.getMessage());
 *         }
 *     }
 * }
 * }</pre>
 * <p>
 * Key components:
 * <ul>
 * <li>{@code ValidationEngineIdentifier}: Enumeration to identify different
 * validation engine types.</li>
 * <li>{@code ValidationEngineKey}: A composite key class used to uniquely
 * identify each {@link ValidationEngine}
 * instance by its type and FHIR profile URL.</li>
 * <li>{@code session()}: This method returns a new
 * {@link OrchestrationSession.Builder} instance that is aware of
 * the {@code OrchestrationEngine} instance.</li>
 * <li>{@code ValidationResult} Interface: Defines the methods {@code isValid}
 * and {@code getIssues} for the validation result.</li>
 * <li>{@code ValidationIssue} Interface: Defines the {@code getMessage} method
 * for validation issues.</li>
 * <li>{@code ValidationEngine} Interface: Defines the {@code validate} method
 * which each validation engine must implement.</li>
 * <li>{@code HapiValidationEngine}, {@code Hl7ValidationEngine},
 * {@code InfernoValidationEngine}: Implement the {@code ValidationEngine}
 * interface, each providing its own validation logic and holding the
 * {@code fhirProfileUrl} as a member field. These classes return
 * anonymous classes for {@code ValidationResult} and
 * {@code ValidationIssue}.</li>
 * <li>{@code OrchestrationSession}: Holds a list of {@link ValidationEngine}
 * instances, payloads, and validation results. The {@code validate}
 * method performs validation and stores the results. The {@code Builder} class
 * within {@code OrchestrationSession} allows setting the
 * {@code fhirProfileUrl} and adding specific validation engine implementations
 * that use this URL.</li>
 * </ul>
 */
public class OrchestrationEngine {
    private final List<OrchestrationSession> sessions;
    private final Map<ValidationEngineKey, ValidationEngine> validationEngineCache;

    public OrchestrationEngine() {
        this.sessions = new ArrayList<>();
        this.validationEngineCache = new HashMap<>();
    }

    public List<OrchestrationSession> getSessions() {
        return Collections.unmodifiableList(sessions);
    }

    public void orchestrate(@NotNull final OrchestrationSession... sessions) {
        for (OrchestrationSession session : sessions) {
            session.validate();
            this.sessions.add(session);
        }
    }

    public ValidationEngine getValidationEngine(@NotNull final ValidationEngineIdentifier type,
            @NotNull final String fhirProfileUrl) {
        ValidationEngineKey key = new ValidationEngineKey(type, fhirProfileUrl);
        return validationEngineCache.computeIfAbsent(key, k -> {
            switch (type) {
                case HAPI:
                    return new HapiValidationEngine.Builder().withFhirProfileUrl(fhirProfileUrl).build();
                case HL7_EMBEDDED:
                    return new Hl7ValidationEngineEmbedded.Builder().withFhirProfileUrl(fhirProfileUrl).build();
                case HL7_API:
                    return new Hl7ValidationEngineApi.Builder().withFhirProfileUrl(fhirProfileUrl).build();
                case INFERNO:
                    return new InfernoValidationEngine.Builder().withFhirProfileUrl(fhirProfileUrl).build();
                default:
                    throw new IllegalArgumentException("Unknown validation engine type: " + type);
            }
        });
    }

    public OrchestrationSession.Builder session() {
        return new OrchestrationSession.Builder(this);
    }

    public enum ValidationEngineIdentifier {
        HAPI, HL7_EMBEDDED, HL7_API, INFERNO
    }

    private static class ValidationEngineKey {
        private final ValidationEngineIdentifier type;
        private final String fhirProfileUrl;

        public ValidationEngineKey(@NotNull ValidationEngineIdentifier type, @NotNull String fhirProfileUrl) {
            this.type = type;
            this.fhirProfileUrl = fhirProfileUrl;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ValidationEngineKey that = (ValidationEngineKey) o;
            return type == that.type && Objects.equals(fhirProfileUrl, that.fhirProfileUrl);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, fhirProfileUrl);
        }
    }

    // Inner classes and interfaces

    public interface ValidationResult {
        String getProfileUrl();

        String getEngine();

        boolean isValid();

        List<ValidationIssue> getIssues();

        String getOperationOutcome();
    }

    public record SourceLocation(Integer line, Integer column, String diagnostics) {
    }

    public interface ValidationIssue {
        String getMessage();

        SourceLocation getLocation();

        String getSeverity();
    }

    public interface ValidationEngine {
        ValidationResult validate(@NotNull final String payload);
    }

    public static class HapiValidationEngine implements OrchestrationEngine.ValidationEngine {
        private final String fhirProfileUrl;
        private final FhirContext fhirContext;
        private final FhirValidator validator;
        private final ValidationOptions options;

        private HapiValidationEngine(final Builder builder) {
            this.fhirProfileUrl = builder.fhirProfileUrl;
            this.fhirContext = FhirContext.forR4();
            this.options = new ValidationOptions();
            if (this.fhirProfileUrl != null) {
                this.options.addProfile(this.fhirProfileUrl);
            }
            this.validator = fhirContext.newValidator();
        }

        @Override
        public OrchestrationEngine.ValidationResult validate(@NotNull final String payload) {
            try {
                final var strictParser = fhirContext.newJsonParser();
                strictParser.setParserErrorHandler(new StrictErrorHandler());
                final var parsedResource = strictParser.parseResource(payload);
                final var hapiVR = validator.validateWithResult(parsedResource, this.options);
                return new OrchestrationEngine.ValidationResult() {
                    @Override
                    @JsonSerialize(using = JsonTextSerializer.class)
                    public String getOperationOutcome() {
                        final var jp = FhirContext.forR4Cached().newJsonParser();
                        return jp.encodeResourceToString(hapiVR.toOperationOutcome());
                    }

                    @Override
                    public boolean isValid() {
                        return hapiVR.isSuccessful();
                    }

                    @Override
                    public List<OrchestrationEngine.ValidationIssue> getIssues() {
                        return hapiVR.getMessages().stream()
                                .map(issue -> new OrchestrationEngine.ValidationIssue() {
                                    @Override
                                    public String getMessage() {
                                        return issue.getMessage();
                                    }

                                    @Override
                                    public SourceLocation getLocation() {
                                        return new SourceLocation(issue.getLocationLine(), issue.getLocationCol(),
                                                issue.getLocationString());
                                    }

                                    @Override
                                    public String getSeverity() {
                                        return issue.getSeverity().toString();
                                    }

                                })
                                .collect(Collectors.toList());
                    }

                    @Override
                    public String getProfileUrl() {
                        return HapiValidationEngine.this.fhirProfileUrl;
                    }

                    @Override
                    public String getEngine() {
                        return ValidationEngineIdentifier.HAPI.toString();
                    }
                };

            } catch (Exception e) {
                return new OrchestrationEngine.ValidationResult() {
                    @Override
                    public String getOperationOutcome() {
                        return null;
                    }

                    @Override
                    public boolean isValid() {
                        return false;
                    }

                    @Override
                    public List<OrchestrationEngine.ValidationIssue> getIssues() {
                        return List.of(new OrchestrationEngine.ValidationIssue() {
                            @Override
                            public String getMessage() {
                                return e.getMessage();
                            }

                            @Override
                            public SourceLocation getLocation() {
                                return new SourceLocation(null, null, e.getClass().getName());
                            }

                            @Override
                            public String getSeverity() {
                                return "FATAL";
                            }
                        });
                    }

                    @Override
                    public String getProfileUrl() {
                        return HapiValidationEngine.this.fhirProfileUrl;
                    }

                    @Override
                    public String getEngine() {
                        return ValidationEngineIdentifier.HAPI.toString();
                    }
                };

            }

        }

        public static class Builder {
            private String fhirProfileUrl;

            public Builder withFhirProfileUrl(@NotNull final String fhirProfileUrl) {
                this.fhirProfileUrl = fhirProfileUrl;
                return this;
            }

            public HapiValidationEngine build() {
                return new HapiValidationEngine(this);
            }
        }
    }

    public static class Hl7ValidationEngineEmbedded implements ValidationEngine {
        private final String fhirProfileUrl;

        private Hl7ValidationEngineEmbedded(final Builder builder) {
            this.fhirProfileUrl = builder.fhirProfileUrl;
        }

        @Override
        public ValidationResult validate(@NotNull final String payload) {
            return new ValidationResult() {
                @Override
                public String getOperationOutcome() {
                    return null;
                }

                @Override
                public boolean isValid() {
                    return true;
                }

                @Override
                public List<ValidationIssue> getIssues() {
                    return List.of();
                }

                @Override
                public String getProfileUrl() {
                    return Hl7ValidationEngineEmbedded.this.fhirProfileUrl;
                }

                @Override
                public String getEngine() {
                    return ValidationEngineIdentifier.HL7_EMBEDDED.toString();
                }
            };
        }

        public static class Builder {
            private String fhirProfileUrl;

            public Builder withFhirProfileUrl(@NotNull final String fhirProfileUrl) {
                this.fhirProfileUrl = fhirProfileUrl;
                return this;
            }

            public Hl7ValidationEngineEmbedded build() {
                return new Hl7ValidationEngineEmbedded(this);
            }
        }
    }

    public static class Hl7ValidationEngineApi implements ValidationEngine {
        private final String fhirProfileUrl;
        private final String fhirContext;
        private final String locale;
        private final String fileType;
        private final String fileName;
        private String fileContent;

        private Hl7ValidationEngineApi(final Builder builder) {
            this.fhirProfileUrl = builder.fhirProfileUrl;
            this.fhirContext = "4.0.1";
            this.locale = "en";
            this.fileType = "json";
            this.fileName = "input.json";
        }

        @Override
        public ValidationResult validate(@NotNull final String payload) {

            String escapedPayload = StringEscapeUtils.escapeJson(payload);
            String result = escapedPayload.replace("\\n", "\\r\\n");
            
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .connectTimeout(Duration.ofSeconds(120))
                    .build();

            fileContent = """
                    {
                      "cliContext": {
                        "sv": "%s",
                        "ig": [
                          "%s"
                        ],
                        "locale": "%s"
                      },
                      "filesToValidate": [
                        {
                          "fileName": "%s",
                          "fileContent": "%s",
                          "fileType": "%s"
                        }
                      ]
                    }
                    """.formatted(fhirContext, fhirProfileUrl, locale, fileName, result, fileType);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://validator.fhir.org/validate"))
                    .POST(BodyPublishers.ofString(fileContent))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build();

            CompletableFuture<HttpResponse<String>> response = client.sendAsync(request, BodyHandlers.ofString());

            return response.thenApply(HttpResponse::body)
                    .thenApply(responseBody -> {
                        return new ValidationResult() {
                            @Override
                            public String getOperationOutcome() {
                                return null;
                            }

                            @Override
                            public boolean isValid() {
                                return responseBody.contains("OperationOutcome");
                            }

                            public List<OrchestrationEngine.ValidationIssue> getIssues() {
                                List<OrchestrationEngine.ValidationIssue> issuesList = new ArrayList<>();

                                try {
                                    final var mapper = new ObjectMapper();
                                    final var root = mapper.readTree(responseBody);
                                    final var outcomes = root.path("outcomes");

                                    if (outcomes.isArray()) {
                                        for (final var outcome : outcomes) {
                                            final var issues = outcome.path("issues");
                                            if (issues.isArray()) {
                                                issuesList.addAll(extractIssues(issues));
                                            }
                                        }
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                return issuesList;
                            }

                            private List<OrchestrationEngine.ValidationIssue> extractIssues(JsonNode issues) {
                                return StreamSupport.stream(issues.spliterator(), false)
                                        .map(issue -> new OrchestrationEngine.ValidationIssue() {
                                            @Override
                                            public String getMessage() {
                                                return issue.path("message").asText();
                                            }

                                            @Override
                                            public OrchestrationEngine.SourceLocation getLocation() {
                                                Integer line = issue.path("line").isInt()
                                                        ? issue.path("line").intValue()
                                                        : null;
                                                Integer column = issue.path("col").isInt()
                                                        ? issue.path("col").intValue()
                                                        : null;
                                                String diagnostics = "ca.uhn.fhir.parser.DataFormatException";
                                                return new OrchestrationEngine.SourceLocation(line, column,
                                                        diagnostics);
                                            }

                                            @Override
                                            public String getSeverity() {
                                                return issue.path("level").asText();
                                            }
                                        })
                                        .collect(Collectors.toList());
                            }

                            @Override
                            public String getProfileUrl() {
                                return Hl7ValidationEngineApi.this.fhirProfileUrl;
                            }

                            @Override
                            public String getEngine() {
                                return ValidationEngineIdentifier.HL7_API.toString();
                            }
                        };
                    }).join(); // Wait for the request to complete
        }

        public static class Builder {
            private String fhirProfileUrl;

            public Builder withFhirProfileUrl(@NotNull final String fhirProfileUrl) {
                this.fhirProfileUrl = fhirProfileUrl;
                return this;
            }

            public Hl7ValidationEngineApi build() {
                return new Hl7ValidationEngineApi(this);
            }
        }
    }

    public static class InfernoValidationEngine implements ValidationEngine {
        private final String fhirProfileUrl;

        private InfernoValidationEngine(final Builder builder) {
            this.fhirProfileUrl = builder.fhirProfileUrl;
        }

        @Override
        public ValidationResult validate(@NotNull final String payload) {
            return new ValidationResult() {
                @Override
                public String getOperationOutcome() {
                    return null;
                }

                @Override
                public boolean isValid() {
                    return true;
                }

                @Override
                public List<ValidationIssue> getIssues() {
                    return List.of();
                }

                @Override
                public String getProfileUrl() {
                    return InfernoValidationEngine.this.fhirProfileUrl;
                }

                @Override
                public String getEngine() {
                    return ValidationEngineIdentifier.INFERNO.toString();
                }
            };
        }

        public static class Builder {
            private String fhirProfileUrl;

            public Builder withFhirProfileUrl(@NotNull final String fhirProfileUrl) {
                this.fhirProfileUrl = fhirProfileUrl;
                return this;
            }

            public InfernoValidationEngine build() {
                return new InfernoValidationEngine(this);
            }
        }
    }

    public record Device(String deviceId, String deviceName) {
        public static Device INSTANCE = createDefault();

        public static Device createDefault() {
            try {
                InetAddress localHost = InetAddress.getLocalHost();
                String ipAddress = localHost.getHostAddress();
                String hostName = localHost.getHostName();
                return new Device(ipAddress, hostName);
            } catch (UnknownHostException e) {
                return new Device("Unable to retrieve the localhost information", e.toString());
            }
        }
    }

    public static class OrchestrationSession {
        private final Device device;
        private final List<String> payloads;
        private final List<ValidationEngine> validationEngines;
        private final List<ValidationResult> validationResults;
        private final String fhirProfileUrl;

        private OrchestrationSession(final Builder builder) {
            this.payloads = Collections.unmodifiableList(builder.payloads);
            this.validationEngines = Collections.unmodifiableList(builder.validationEngines);
            this.validationResults = new ArrayList<>();
            this.fhirProfileUrl = builder.fhirProfileUrl;
            this.device = builder.device;
        }

        public List<String> getPayloads() {
            return payloads;
        }

        public List<ValidationEngine> getValidationEngines() {
            return validationEngines;
        }

        public List<ValidationResult> getValidationResults() {
            return validationResults;
        }

        public Device getDevice() {
            return device;
        }

        public String getFhirProfileUrl() {
            return fhirProfileUrl;
        }

        public void validate() {
            for (String payload : payloads) {
                for (ValidationEngine engine : validationEngines) {
                    ValidationResult result = engine.validate(payload);
                    validationResults.add(result);
                }
            }
        }

        public static class Builder {
            private final OrchestrationEngine engine;
            private final List<String> payloads = new ArrayList<>();
            private final List<ValidationEngine> validationEngines = new ArrayList<>();
            private Device device = Device.INSTANCE;
            private String fhirProfileUrl;

            public Builder(@NotNull final OrchestrationEngine engine) {
                this.engine = engine;
            }

            public Builder onDevice(@NotNull final Device device) {
                this.device = device;
                return this;
            }

            public Builder withPayloads(@NotNull final List<String> payloads) {
                this.payloads.addAll(payloads);
                return this;
            }

            public Builder withFhirProfileUrl(@NotNull final String fhirProfileUrl) {
                this.fhirProfileUrl = fhirProfileUrl;
                return this;
            }

            public Builder addValidationEngine(@NotNull final ValidationEngine validationEngine) {
                this.validationEngines.add(validationEngine);
                return this;
            }

            public Builder addHapiValidationEngine() {
                this.validationEngines
                        .add(engine.getValidationEngine(ValidationEngineIdentifier.HAPI, this.fhirProfileUrl));
                return this;
            }

            public Builder addHl7ValidationEmbeddedEngine() {
                this.validationEngines
                        .add(engine.getValidationEngine(ValidationEngineIdentifier.HL7_EMBEDDED, this.fhirProfileUrl));
                return this;
            }

            public Builder addHl7ValidationApiEngine() {
                this.validationEngines
                        .add(engine.getValidationEngine(ValidationEngineIdentifier.HL7_API, this.fhirProfileUrl));
                return this;
            }

            public Builder addInfernoValidationEngine() {
                this.validationEngines
                        .add(engine.getValidationEngine(ValidationEngineIdentifier.INFERNO, this.fhirProfileUrl));
                return this;
            }

            public OrchestrationSession build() {
                return new OrchestrationSession(this);
            }
        }
    }
}