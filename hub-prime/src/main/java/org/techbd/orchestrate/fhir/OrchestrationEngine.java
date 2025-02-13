package org.techbd.orchestrate.fhir;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.util.JsonText.JsonTextSerializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nimbusds.oauth2.sdk.util.CollectionUtils;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
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
@Component
public class OrchestrationEngine {
    private final List<OrchestrationSession> sessions;
    private final Map<ValidationEngineKey, ValidationEngine> validationEngineCache;
    private static final Logger LOG = LoggerFactory.getLogger(OrchestrationEngine.class);
    private final Tracer tracer;

    public OrchestrationEngine(final Tracer tracer) {
        this.sessions = new ArrayList<>();
        this.validationEngineCache = new HashMap<>();
        this.tracer = tracer;
    }

    public List<OrchestrationSession> getSessions() {
        return Collections.unmodifiableList(sessions);
    }

    public synchronized void orchestrate(@NotNull final OrchestrationSession... sessions) {
        Span span = tracer.spanBuilder("OrchestrationEngine.orchestrate").startSpan();
        try {
            for (final OrchestrationSession session : sessions) {
                this.sessions.add(session);
                session.validate();
            }
        } finally {
            span.end();
        }
    }

    public void clear(@NotNull final OrchestrationSession... sessionsToRemove) {
        Span span = tracer.spanBuilder("OrchestrationEngine.clear").startSpan();
        try {
            if (sessionsToRemove != null && CollectionUtils.isNotEmpty(sessions)) {
                synchronized (this) {
                    Set<String> sessionIdsToRemove = Arrays.stream(sessionsToRemove)
                            .map(OrchestrationSession::getSessionId)
                            .collect(Collectors.toSet());
                    Iterator<OrchestrationSession> iterator = this.sessions.iterator();
                    while (iterator.hasNext()) {
                        OrchestrationSession session = iterator.next();
                        if (sessionIdsToRemove.contains(session.getSessionId())) {
                            iterator.remove();
                        }
                    }
                }
            }
        } finally {
            span.end();
        }
    }

    public synchronized ValidationEngine getValidationEngine(@NotNull final ValidationEngineIdentifier type,
            @NotNull final String fhirProfileUrl, final Map<String, Map<String, String>> igPackages,
            final String igVersion, final Tracer tracer, String interactionId) {
        final ValidationEngineKey key = new ValidationEngineKey(type, fhirProfileUrl);
        return validationEngineCache.computeIfAbsent(key, k -> {
            switch (type) {
                case HAPI:
                    return new HapiValidationEngine.Builder().withFhirProfileUrl(fhirProfileUrl)
                            .withIgPackages(igPackages)
                            .withIgVersion(igVersion)
                            .withTracer(tracer)
                            .withInteractionId(interactionId)
                            .build();
                case HL7_EMBEDDED:
                    return new Hl7ValidationEngineEmbedded.Builder().withFhirProfileUrl(fhirProfileUrl).build();
                case HL7_API:
                    return new Hl7ValidationEngineApi.Builder().withFhirProfileUrl(fhirProfileUrl).build();
                default:
                    throw new IllegalArgumentException("Unknown validation engine type: " + type);
            }
        });
    }

    public OrchestrationSession.Builder session() {
        return new OrchestrationSession.Builder(this);
    }

    public enum ValidationEngineIdentifier {
        HAPI, HL7_EMBEDDED, HL7_API
    }

    private static class ValidationEngineKey {
        private final ValidationEngineIdentifier type;
        private final String fhirProfileUrl;

        public ValidationEngineKey(@NotNull final ValidationEngineIdentifier type,
                @NotNull final String fhirProfileUrl) {
            this.type = type;
            this.fhirProfileUrl = fhirProfileUrl;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ValidationEngineKey that = (ValidationEngineKey) o;
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

        String getIgVersion();

        ValidationEngine.Observability getObservability();

        boolean isValid();

        String getOperationOutcome();

        Instant getCompletedAt();
    }

    public record SourceLocation(Integer line, Integer column, String diagnostics) {
    }

    public interface ValidationEngine {
        record Observability(String identity, String name, Instant initAt, Instant constructedAt) {
        }

        Observability observability();

        ValidationResult validate(@NotNull final String payload, final String interactionId);
    }

    public static class HapiValidationEngine implements OrchestrationEngine.ValidationEngine {
        private final Observability observability;
        private final Instant engineInitAt = Instant.now();
        private final Instant engineConstructedAt;
        private final String fhirProfileUrl;
        private final FhirContext fhirContext;
        private final Map<String, Map<String, String>> igPackages;
        private final String igVersion;
        private final FhirValidator fhirValidator;
        private final Tracer tracer;
        private final String interactionId;

        private HapiValidationEngine(final Builder builder) {
            this.fhirProfileUrl = builder.fhirProfileUrl;
            this.fhirContext = FhirContext.forR4();
            engineConstructedAt = Instant.now();
            observability = new Observability(HapiValidationEngine.class.getName(),
                    "HAPI version %s (FHIR version %s)"
                            .formatted("7.6.1",
                                    fhirContext.getVersion().getVersion().getFhirVersionString()),
                    engineInitAt,
                    engineConstructedAt);
            this.igPackages = builder.igPackages;
            this.igVersion = builder.igVersion;
            this.tracer = builder.tracer;
            this.interactionId = builder.interactionId;
            this.fhirValidator = initializeFhirValidator();
        }

        public FhirValidator initializeFhirValidator() {
            Span span = tracer.spanBuilder("OrchestrationEngine.initializeFhirValidator").startSpan();
            try {
                final var supportChain = new ValidationSupportChain();
                final var defaultSupport = new DefaultProfileValidationSupport(fhirContext);

                LOG.info("Version of igPackage : {} for interaction Id : {} ", igVersion, interactionId);
                LOG.info("Add IG Packages to npmPackageValidationSupport -BEGIN for interaction Id : {}",
                        interactionId);
                NpmPackageValidationSupport npmPackageValidationSupport = new NpmPackageValidationSupport(fhirContext);

                if (igPackages != null && igPackages.containsKey("fhir-v4")) {
                    Map<String, String> igMap = igPackages.get("fhir-v4");
                    LOG.info("No. of packages to be add : {}  for interaction Id : {}", igMap.size(), interactionId);
                    for (String igKey : igMap.keySet()) {
                        String packagePath = igMap.get(igKey);
                        try {
                            LOG.info("Add IG Package {} -BEGIN for interaction Id : {} ", packagePath, interactionId);
                            npmPackageValidationSupport.loadPackageFromClasspath(packagePath + "/package.tgz");
                            LOG.info("Add IG Package {} -END for interaction Id : {} ", packagePath, interactionId);
                        } catch (Exception e) {
                            LOG.error("Failed to load the package {} for interactionId : {} ", packagePath,
                                    interactionId, e);
                        }
                    }
                } else {
                    LOG.error("IG Package path not defined for Interaction  Id :{} ", interactionId);
                }

                supportChain.addValidationSupport(npmPackageValidationSupport);
                LOG.info("Add IG Packages to npmPackageValidationSupport -END");

                supportChain.addValidationSupport(defaultSupport);
                supportChain.addValidationSupport(new CommonCodeSystemsTerminologyService(fhirContext));
                supportChain.addValidationSupport(new SnapshotGeneratingValidationSupport(fhirContext));
                supportChain.addValidationSupport(new InMemoryTerminologyServerValidationSupport(fhirContext));

                final var prePopulateSupport = new PrePopulateSupport(tracer);
                var prePopulatedValidationSupport = prePopulateSupport.build(fhirContext);
                prePopulateSupport.addCodeSystems(supportChain, prePopulatedValidationSupport);

                supportChain.addValidationSupport(prePopulatedValidationSupport);
                prePopulatedValidationSupport = null;

                final var postPopulateSupport = new PostPopulateSupport(tracer);
                postPopulateSupport.update(supportChain);

                final var cache = new CachingValidationSupport(supportChain);
                final var instanceValidator = new FhirInstanceValidator(cache);
                return fhirContext.newValidator().registerValidatorModule(instanceValidator);
            } finally {
                span.end();
            }
        }

        @Override
        public OrchestrationEngine.ValidationResult validate(@NotNull final String payload,
                final String interactionId) {
            final var initiatedAt = Instant.now();
            Span span = tracer.spanBuilder("OrchestrationEngine.validate").startSpan();
            try {
                try {
                    LOG.info("VALIDATOR -BEGIN initiated At : {} for interactionid:{} with ig version :{} ", initiatedAt, interactionId,igVersion);
                    LOG.debug("BUNDLE PAYLOAD parse -BEGIN for interactionId:{}", interactionId);
                    final var bundle = fhirContext.newJsonParser().parseResource(Bundle.class, payload);
                    LOG.debug("BUNDLE PAYLOAD parse -END");
                    final var hapiVR = fhirValidator.validateWithResult(bundle);
                    final var completedAt = Instant.now();
                    LOG.info("VALIDATOR -END completed at :{} ms for interactionId:{} with ig version :{}",
                            Duration.between(initiatedAt, completedAt).toMillis(), interactionId,igVersion);
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
                        public String getProfileUrl() {
                            return HapiValidationEngine.this.fhirProfileUrl;
                        }

                        @Override
                        public String getIgVersion() {
                            return igVersion;
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

                } catch (final Exception e) {
                    final var completedAt = Instant.now();
                    return new OrchestrationEngine.ValidationResult() {
                        @Override
                        @JsonSerialize(using = JsonTextSerializer.class)
                        public String getOperationOutcome() {
                            OperationOutcome operationOutcome = new OperationOutcome();
                            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
                            issue.setSeverity(IssueSeverity.FATAL);
                            issue.setDiagnostics(e.getMessage());
                            issue.setCode(OperationOutcome.IssueType.EXCEPTION);
                            operationOutcome.addIssue(issue);
                            return FhirContext.forR4().newJsonParser().encodeResourceToString(operationOutcome);
                        }

                        @Override
                        public boolean isValid() {
                            return false;
                        }

                        @Override
                        public String getProfileUrl() {
                            return HapiValidationEngine.this.fhirProfileUrl;
                        }

                        @Override
                        public String getIgVersion() {
                            return igVersion;
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
            } finally {
                span.end();
            }

        }

        public static class Builder {
            private String fhirProfileUrl;
            private Map<String, Map<String, String>> igPackages;
            private String igVersion;
            private String interactionId;
            private Tracer tracer;

            public Builder withInteractionId(@NotNull final String interactionId) {
                this.interactionId = interactionId;
                return this;
            }

            public Builder withFhirProfileUrl(@NotNull final String fhirProfileUrl) {
                this.fhirProfileUrl = fhirProfileUrl;
                return this;
            }

            public Builder withIgPackages(@NotNull final Map<String, Map<String, String>> igPackages) {
                this.igPackages = igPackages;
                return this;
            }

            public Builder withIgVersion(@NotNull final String igVersion) {
                this.igVersion = igVersion;
                return this;
            }

            public Builder withTracer(@NotNull final Tracer tracer) {
                this.tracer = tracer;
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
        private final String interactionId;
        private String igVersion;

        private Hl7ValidationEngineEmbedded(final Builder builder) {
            this.fhirProfileUrl = builder.fhirProfileUrl;
            engineConstructedAt = Instant.now();
            observability = new Observability(Hl7ValidationEngineEmbedded.class.getName(),
                    "HL7 Official Embedded (TODO: version)", engineInitAt,
                    engineConstructedAt);
            this.interactionId = builder.interactionId;
        }

        @Override
        public ValidationResult validate(@NotNull final String payload, final String interactionId) {
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
                public String getProfileUrl() {
                    return Hl7ValidationEngineEmbedded.this.fhirProfileUrl;
                }

                @Override
                public String getIgVersion() {
                    return igVersion;
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
            private String interactionId;

            public Builder withFhirProfileUrl(@NotNull final String fhirProfileUrl) {
                this.fhirProfileUrl = fhirProfileUrl;
                return this;
            }

            public Builder withInteractionId(@NotNull final String interactionId) {
                this.interactionId = interactionId;
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
        private String igVersion;
        private final String fhirContext;
        private final String locale;
        private final String fileType;
        private final String fileName;
        private final String interactionId;

        private Hl7ValidationEngineApi(final Builder builder) {
            this.fhirProfileUrl = builder.fhirProfileUrl;
            this.fhirContext = "4.0.1";
            this.locale = "en";
            this.fileType = "json";
            this.fileName = "input.json";
            this.engineConstructedAt = Instant.now();
            observability = new Observability(Hl7ValidationEngineApi.class.getName(),
                    "HL7 Official API (TODO: version)", engineInitAt,
                    engineConstructedAt);
            this.interactionId = builder.interactionId;
        }

        @Override
        public ValidationResult validate(@NotNull final String payload, final String interactionId) {
            final var initiatedAt = Instant.now();

            final String escapedPayload = StringEscapeUtils.escapeJson(payload);
            final String result = escapedPayload.replace("\\n", "\\r\\n");

            final HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .connectTimeout(Duration.ofSeconds(120))
                    .build();

            final var fileContent = String.format("""
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
                    """.replace("\n", "%n"), fhirContext, fhirProfileUrl, locale, fileName, result, fileType);

            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://validator.fhir.org/validate"))
                    .POST(BodyPublishers.ofString(fileContent))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build();

            final CompletableFuture<HttpResponse<String>> response = client.sendAsync(request, BodyHandlers.ofString());

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

                            @Override
                            public String getProfileUrl() {
                                return Hl7ValidationEngineApi.this.fhirProfileUrl;
                            }

                            @Override
                            public String getIgVersion() {
                                return igVersion;
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
            private String interactionId;

            public Builder withFhirProfileUrl(@NotNull final String fhirProfileUrl) {
                this.fhirProfileUrl = fhirProfileUrl;
                return this;
            }

            public Builder withInteractionId(@NotNull final String interactionId) {
                this.interactionId = interactionId;
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

    public record Device(String deviceId, String deviceName) {
        public static final Device INSTANCE = createDefault();

        public static Device createDefault() {
            try {
                final InetAddress localHost = InetAddress.getLocalHost();
                final String ipAddress = localHost.getHostAddress();
                final String hostName = localHost.getHostName();
                return new Device(ipAddress, hostName);
            } catch (final UnknownHostException e) {
                return new Device("Unable to retrieve the localhost information", e.toString());
            }
        }
    }

    public static class OrchestrationSession {
        private final String sessionId;
        private final Device device;
        private final List<String> payloads;
        private final List<ValidationEngine> validationEngines;
        private final List<ValidationResult> validationResults;
        private final String fhirProfileUrl;
        private String igVersion;
        private String interactionId;

        private OrchestrationSession(final Builder builder) {
            this.sessionId = builder.sessionId;
            this.payloads = Collections.unmodifiableList(builder.payloads);
            this.validationEngines = Collections.unmodifiableList(builder.validationEngines);
            this.validationResults = new ArrayList<>();
            this.fhirProfileUrl = builder.fhirProfileUrl;
            this.device = builder.device;
            this.interactionId = builder.interactionId;
        }

        public List<String> getPayloads() {
            return payloads;
        }

        public List<ValidationEngine> getValidationEngines() {
            return validationEngines;
        }

        public List<ValidationResult> getValidationResults() {
            return Collections.unmodifiableList(validationResults);
        }

        public Device getDevice() {
            return device;
        }

        public String getFhirProfileUrl() {
            return fhirProfileUrl;
        }

        public String getIgVersion() {
            return igVersion;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getInteractionId() {
            return interactionId;
        }

        public synchronized void validate() {
            for (final String payload : payloads) {
                for (final ValidationEngine engine : validationEngines) {
                    final ValidationResult result = engine.validate(payload, interactionId);
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
            private final List<String> uaStrategyJsonIssues = new ArrayList<>();
            private Map<String, Map<String, String>> igPackages;
            private String igVersion;
            private String sessionId;
            private String interactionId;
            private Tracer tracer;

            public Builder(@NotNull final OrchestrationEngine engine) {
                this.engine = engine;
            }

            public List<String> getUaStrategyJsonIssues() {
                return Collections.unmodifiableList(uaStrategyJsonIssues);
            }

            public Builder onDevice(@NotNull final Device device) {
                this.device = device;
                return this;
            }

            public synchronized Builder withPayloads(@NotNull final List<String> payloads) {
                this.payloads.addAll(payloads);
                return this;
            }

            public Builder withFhirProfileUrl(@NotNull final String fhirProfileUrl) {
                this.fhirProfileUrl = fhirProfileUrl;
                return this;
            }

            public Builder withSessionId(@NotNull final String sessionId) {
                this.sessionId = sessionId;
                return this;
            }

            public Builder withInteractionId(String interactionId) {
                this.interactionId = interactionId;
                return this;
            }

            public Builder withFhirIGPackages(@NotNull final Map<String, Map<String, String>> igPackages) {
                this.igPackages = igPackages;
                return this;
            }

            public Builder withIgVersion(@NotNull final String igVersion) {
                this.igVersion = igVersion;
                return this;
            }

            public Builder withTracer(@NotNull final Tracer tracer) {
                this.tracer = tracer;
                return this;
            }

            public Builder withUserAgentValidationStrategy(final String uaStrategyJson, final boolean clearExisting) {
                if (uaStrategyJson != null) {
                    try {
                        if (new ObjectMapper().readValue(uaStrategyJson,
                                Object.class) instanceof final Map uaStrategy) {
                            if (uaStrategy.get("engines") instanceof final List engines) {
                                if (clearExisting) {
                                    validationEngines.clear();
                                }
                                for (final var engineItem : engines) {
                                    if (engineItem instanceof final String engine) {
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
                    } catch (final JsonProcessingException e) {
                        uaStrategyJsonIssues
                                .add("Error parsing uaStrategyJson `%s` in withUserAgentValidationStrategy: %s"
                                        .formatted(uaStrategyJson, e));
                    }
                }

                return this;
            }

            public synchronized Builder addValidationEngine(@NotNull final ValidationEngine validationEngine) {
                this.validationEngines.add(validationEngine);
                return this;
            }

            public synchronized Builder addHapiValidationEngine() {
                this.validationEngines
                        .add(engine.getValidationEngine(ValidationEngineIdentifier.HAPI, this.fhirProfileUrl,
                                this.igPackages,
                                this.igVersion, this.tracer, this.interactionId));
                return this;
            }

            public synchronized Builder addHl7ValidationEmbeddedEngine() {
                this.validationEngines
                        .add(engine.getValidationEngine(ValidationEngineIdentifier.HL7_EMBEDDED, this.fhirProfileUrl,
                                null, null, null, null));
                return this;
            }

            public synchronized Builder addHl7ValidationApiEngine() {
                this.validationEngines
                        .add(engine.getValidationEngine(ValidationEngineIdentifier.HL7_API, this.fhirProfileUrl, null,
                                null, null, null));
                return this;
            }

            public OrchestrationSession build() {
                return new OrchestrationSession(this);
            }

        }
    }
}