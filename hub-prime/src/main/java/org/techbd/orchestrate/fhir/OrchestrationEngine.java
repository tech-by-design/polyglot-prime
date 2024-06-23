package org.techbd.orchestrate.fhir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


import org.apache.commons.text.StringEscapeUtils;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.techbd.conf.Configuration;
import org.techbd.util.JsonText.JsonTextSerializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.StrictErrorHandler;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationOptions;
import jakarta.validation.constraints.NotNull;

import org.inferno.validator.Validator;
import org.springframework.cache.annotation.Cacheable;

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

    @Cacheable("fhirProfile")
    public static String fetchFhirProfileVersion(String fhirProfileUrl) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fhirProfileUrl))
                .build();

        String fhirProfileVersion = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(responseBody -> {
                    try {
                        // Read JSON response and parse it into a JsonNode
                        final var rootNode = Configuration.objectMapper.readTree(responseBody);

                        // Get the value of the "version" key from FHIR IG profile JSON
                        JsonNode versionNode = rootNode.path("version");
                        return versionNode.asText();

                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .join(); // Wait for the async operation to complete

        return fhirProfileVersion;
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
        Instant getInitiatedAt();

        String getProfileUrl();

        String getFhirProfileVersion();

        ValidationEngine.Observability getObservability();

        boolean isValid();

        List<ValidationIssue> getIssues();

        String getOperationOutcome();

        Instant getCompletedAt();
    }

    public record SourceLocation(Integer line, Integer column, String diagnostics) {
    }

    public interface ValidationIssue {
        String getMessage();

        SourceLocation getLocation();

        String getSeverity();
    }

    public interface ValidationEngine {
        record Observability(String identity, String name, Instant initAt, Instant constructedAt) {
        }

        Observability observability();

        ValidationResult validate(@NotNull final String payload);
    }

    public static class HapiValidationEngine implements OrchestrationEngine.ValidationEngine {
        private final Observability observability;
        private final Instant engineInitAt = Instant.now();
        private final Instant engineConstructedAt;
        private final String fhirProfileUrl;
        private final FhirContext fhirContext;
        private final FhirValidator validator;
        private final ValidationOptions options;
        private final String fhirProfileVersion;

        private HapiValidationEngine(final Builder builder) {
            this.fhirProfileUrl = builder.fhirProfileUrl;
            fhirProfileVersion = OrchestrationEngine.fetchFhirProfileVersion(fhirProfileUrl);
            this.fhirContext = FhirContext.forR4();
            this.options = new ValidationOptions();
            if (this.fhirProfileUrl != null) {
                this.options.addProfile(this.fhirProfileUrl);
            }
            this.validator = fhirContext.newValidator();
            engineConstructedAt = Instant.now();
            observability = new Observability(HapiValidationEngine.class.getName(),
                    "HAPI version TODO (FHIR version %s)"
                            .formatted(fhirContext.getVersion().getVersion().getFhirVersionString()),
                    engineInitAt,
                    engineConstructedAt);
        }

        @Override
        public OrchestrationEngine.ValidationResult validate(@NotNull final String payload) {
            final var initiatedAt = Instant.now();
            try {
                final var strictParser = fhirContext.newJsonParser();
                strictParser.setParserErrorHandler(new StrictErrorHandler());
                final var parsedResource = strictParser.parseResource(payload);
                final var hapiVR = validator.validateWithResult(parsedResource, this.options);
                final var completedAt = Instant.now();
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
                    public String getFhirProfileVersion() {
                        return HapiValidationEngine.this.fhirProfileVersion;
                    }

                    @Override
                    public ValidationEngine.Observability getObservability() {
                        return observability;
                    }

                    @Override
                    public Instant getInitiatedAt() {
                        return initiatedAt;
                    }

                    @Override
                    public Instant getCompletedAt() {
                        return completedAt;
                    }
                };

            } catch (Exception e) {
                final var completedAt = Instant.now();
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
                    public String getFhirProfileVersion() {
                        return HapiValidationEngine.this.fhirProfileVersion;
                    }

                    @Override
                    public ValidationEngine.Observability getObservability() {
                        return observability;
                    }

                    @Override
                    public Instant getInitiatedAt() {
                        return initiatedAt;
                    }

                    @Override
                    public Instant getCompletedAt() {
                        return completedAt;
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

        @Override
        public Observability observability() {
            return observability;
        }
    }

    public static class Hl7ValidationEngineEmbedded implements ValidationEngine {
        private final Observability observability;
        private final Instant engineInitAt = Instant.now();
        private final Instant engineConstructedAt;
        private final String fhirProfileUrl;
        private final String fhirProfileVersion;

        private Hl7ValidationEngineEmbedded(final Builder builder) {
            this.fhirProfileUrl = builder.fhirProfileUrl;
            fhirProfileVersion = OrchestrationEngine.fetchFhirProfileVersion(fhirProfileUrl);
            engineConstructedAt = Instant.now();
            observability = new Observability(Hl7ValidationEngineEmbedded.class.getName(),
                    "HL7 Official Embedded (TODO: version)", engineInitAt,
                    engineConstructedAt);
        }

        @Override
        public ValidationResult validate(@NotNull final String payload) {
            final var initiatedAt = Instant.now();
            final var completedAt = Instant.now();
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
                public String getFhirProfileVersion() {
                    return Hl7ValidationEngineEmbedded.this.fhirProfileVersion;
                }

                @Override
                public ValidationEngine.Observability getObservability() {
                    return observability;
                }

                @Override
                public Instant getInitiatedAt() {
                    return initiatedAt;
                }

                @Override
                public Instant getCompletedAt() {
                    return completedAt;
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

        @Override
        public Observability observability() {
            return observability;
        }
    }

    public static class Hl7ValidationEngineApi implements ValidationEngine {
        private final Observability observability;
        private final Instant engineInitAt = Instant.now();
        private final Instant engineConstructedAt;
        private final String fhirProfileUrl;
        private final String fhirContext;
        private final String locale;
        private final String fileType;
        private final String fileName;
        private final String fhirProfileVersion;

        private Hl7ValidationEngineApi(final Builder builder) {
            this.fhirProfileUrl = builder.fhirProfileUrl;
            fhirProfileVersion = OrchestrationEngine.fetchFhirProfileVersion(fhirProfileUrl);
            this.fhirContext = "4.0.1";
            this.locale = "en";
            this.fileType = "json";
            this.fileName = "input.json";
            this.engineConstructedAt = Instant.now();
            observability = new Observability(Hl7ValidationEngineApi.class.getName(),
                    "HL7 Official API (TODO: version)", engineInitAt,
                    engineConstructedAt);
        }

        @Override
        public ValidationResult validate(@NotNull final String payload) {
            final var initiatedAt = Instant.now();

            String escapedPayload = StringEscapeUtils.escapeJson(payload);
            String result = escapedPayload.replace("\\n", "\\r\\n");

            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .connectTimeout(Duration.ofSeconds(120))
                    .build();

            final var fileContent = """
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
                        final var completedAt = Instant.now();
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
                            public String getFhirProfileVersion() {
                                return Hl7ValidationEngineApi.this.fhirProfileVersion;
                            }

                            @Override
                            public ValidationEngine.Observability getObservability() {
                                return observability;
                            }

                            @Override
                            public Instant getInitiatedAt() {
                                return initiatedAt;
                            }

                            @Override
                            public Instant getCompletedAt() {
                                return completedAt;
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

        @Override
        public Observability observability() {
            return observability;
        }
    }

    public static class InfernoValidationEngine implements ValidationEngine {
        private final Observability observability;
        private final Instant engineInitAt = Instant.now();
        private final Instant engineConstructedAt;
        private final String fhirProfileUrl;
        private final Validator validator;
        private List<String> fhirBundleProfile;
        private final String fhirProfileVersion;

        private InfernoValidationEngine(final Builder builder) {
            this.fhirProfileUrl = builder.fhirProfileUrl;
            fhirProfileVersion = OrchestrationEngine.fetchFhirProfileVersion(fhirProfileUrl);
            //fhirProfileVersion = OrchestrationEngine.fetchFhirProfileVersion(fhirProfileUrl);
            Validator tempValidator;
            try {
                tempValidator = new Validator("hub-prime/igs", false);
            } catch (Exception e) {
                e.printStackTrace();
                tempValidator = null; // or provide a default initialization
            }
            fhirBundleProfile = new ArrayList<>();
            this.validator = tempValidator;
            this.engineConstructedAt = Instant.now();
            observability = new Observability(InfernoValidationEngine.class.getName(),
                    "Inferno version (TODO: version)", engineInitAt,
                    engineConstructedAt);
        }

        @Override
        public ValidationResult validate(@NotNull final String payload) {
            final Instant initiatedAt = Instant.now();

            try {
                byte[] payloadContent = payload.getBytes(StandardCharsets.UTF_8); // loadFile(payload);
                byte[] bundleProfile = loadFileFromURL(fhirProfileUrl);

                validator.loadProfile(bundleProfile);
                if (validator.getAssignedUrlFrom() != null) {
                    fhirBundleProfile = Arrays.asList(validator.getAssignedUrlFrom());
                } else {
                    fhirBundleProfile = null;
                }

                OperationOutcome oo = validator.validate(payloadContent, fhirBundleProfile);
                ArrayNode issueArray = displayValidationErrors(oo, false);
                final var mapper = new ObjectMapper();
                String responseBody = mapper.writeValueAsString(issueArray);

                final Instant completedAt = Instant.now();

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
                            final var issuesArray = mapper.readTree(responseBody);

                            if (issuesArray.isArray()) {
                                issuesList.addAll(extractIssues(issuesArray));
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
                        return InfernoValidationEngine.this.fhirProfileUrl;
                    }

                    @Override
                    public String getFhirProfileVersion() {
                        return InfernoValidationEngine.this.fhirProfileVersion;
                    }

                    @Override
                    public ValidationEngine.Observability getObservability() {
                        return observability;
                    }

                    @Override
                    public Instant getInitiatedAt() {
                        return initiatedAt;
                    }

                    @Override
                    public Instant getCompletedAt() {
                        return completedAt;
                    }
                };
            } catch (Exception e) {
                final var completedAt = Instant.now();
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
                        return InfernoValidationEngine.this.fhirProfileUrl;
                    }

                    @Override
                    public String getFhirProfileVersion() {
                        return InfernoValidationEngine.this.fhirProfileVersion;
                    }

                    @Override
                    public ValidationEngine.Observability getObservability() {
                        return observability;
                    }

                    @Override
                    public Instant getInitiatedAt() {
                        return initiatedAt;
                    }

                    @Override
                    public Instant getCompletedAt() {
                        return completedAt;
                    }
                };
            }

        }

        public static byte[] loadFileFromURL(String urlString)
                throws IOException, InterruptedException, ExecutionException {
            URI uri = URI.create(urlString);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new IOException("Failed to get file: HTTP " + response.statusCode());
            }

            return response.body();
        }

        public static ArrayNode displayValidationErrors(OperationOutcome oo, boolean areErrorsExpected) {
            List<OperationOutcome.OperationOutcomeIssueComponent> issues = oo.getIssue();
            final var objectMapper = Configuration.objectMapper;
            ArrayNode issueArray = objectMapper.createArrayNode();

            for (OperationOutcome.OperationOutcomeIssueComponent issue : issues) {
                ObjectNode issueJson = objectMapper.createObjectNode();
                issueJson.put("source", "InstanceValidator");
                issueJson.put("server", (String) null);
                issueJson.put("line", (String) null);
                issueJson.put("col", (String) null);
                issueJson.put("location", issue.hasLocation() ? issue.getLocation().get(0).getValue() : null);
                issueJson.put("message", issue.getDetails().getText());
                issueJson.put("messageId", (String) null);
                issueJson.put("type", issue.getCode().toCode());
                issueJson.put("level", issue.getSeverity().toCode());
                issueJson.put("display",
                        issue.getSeverity().toCode() + ": "
                                + (issue.hasLocation() ? issue.getLocation().get(0).getValue() : null) + ": "
                                + issue.getDetails().getText());
                issueJson.put("error", true);
                issueArray.add(issueJson);

            }
            return issueArray;
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

        @Override
        public Observability observability() {
            return observability;
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
            private List<String> uaStrategyJsonIssues = new ArrayList<>();

            public Builder(@NotNull final OrchestrationEngine engine) {
                this.engine = engine;
            }

            public List<String> getUaStrategyJsonIssues() {
                return uaStrategyJsonIssues;
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

            public Builder withUserAgentValidationStrategy(final String uaStrategyJson, final boolean clearExisting) {
                if (uaStrategyJson != null) {
                    try {
                        if (new ObjectMapper().readValue(uaStrategyJson, Object.class) instanceof Map uaStrategy) {
                            if (uaStrategy.get("engines") instanceof List engines) {
                                if (clearExisting) {
                                    validationEngines.clear();
                                }
                                for (var engineItem : engines) {
                                    if (engineItem instanceof String engine) {
                                        switch (engine) {
                                            case "HAPI":
                                                addHapiValidationEngine();
                                                break;
                                            case "HL7-Official-API":
                                                addHl7ValidationApiEngine();
                                                break;
                                            case "HL7-Official-Embedded":
                                                addHl7ValidationEmbeddedEngine();
                                                break;
                                            case "Inferno":
                                                addInfernoValidationEngine();
                                                break;
                                            default:
                                                uaStrategyJsonIssues.add(
                                                        "uaStrategyJson engine `%s` in withUserAgentValidationStrategy was not recognized"
                                                                .formatted(engine));
                                        }
                                    }
                                }
                            } else {
                                uaStrategyJsonIssues.add(
                                        "uaStrategyJson `engines` key not found in `%s` withUserAgentValidationStrategy"
                                                .formatted(uaStrategyJson));
                            }
                        } else {
                            uaStrategyJsonIssues
                                    .add("uaStrategyJson `%s` in withUserAgentValidationStrategy is not a Map"
                                            .formatted(uaStrategyJson));
                        }
                    } catch (JsonProcessingException e) {
                        uaStrategyJsonIssues
                                .add("Error parsing uaStrategyJson `%s` in withUserAgentValidationStrategy: %s"
                                        .formatted(uaStrategyJson, e));
                    }
                }

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