package org.techbd.service.fhir.engine;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
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
import org.springframework.stereotype.Component;
import org.techbd.config.Configuration;
import org.techbd.config.CoreAppConfig;
import org.techbd.config.CoreAppConfig.FhirV4Config;
import org.techbd.exceptions.ErrorCode;
import org.techbd.exceptions.JsonValidationException;
import org.techbd.service.fhir.validation.FhirBundleValidator;
import org.techbd.service.fhir.validation.PostPopulateSupport;
import org.techbd.service.fhir.validation.PrePopulateSupport;
import org.techbd.util.AppLogger;
import org.techbd.util.JsonText.JsonTextSerializer;
import org.techbd.util.TemplateLogger;
import org.techbd.util.fhir.CoreFHIRUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.parser.LenientErrorHandler;
import ca.uhn.fhir.validation.FhirValidator;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

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
@Getter
@Setter
public class OrchestrationEngine {
    private final ConcurrentHashMap<String, OrchestrationSession> sessions;
    private final Map<ValidationEngineIdentifier, ValidationEngine> validationEngineCache;
    private final CoreAppConfig coreAppConfig;
    private final TemplateLogger LOG;
    private final AppLogger appLogger;
    private Tracer tracer;

    public OrchestrationEngine(final CoreAppConfig coreAppConfig, AppLogger appLogger) {
        this.sessions = new ConcurrentHashMap<>();
        this.coreAppConfig = coreAppConfig;
        this.validationEngineCache = new HashMap<>();
        this.tracer = GlobalOpenTelemetry.get().getTracer("OrchestrationEngine");
        LOG = appLogger.getLogger(OrchestrationEngine.class);
        this.appLogger = appLogger;
        initializeEngines();
    }

    private void initializeEngines() {
        LOG.info("OrchestrationEngine:: initializeEngines -BEGIN");
        getOrCreateValidationEngine(ValidationEngineIdentifier.HAPI, coreAppConfig.getIgPackages(),
                tracer,appLogger,LOG);
        getOrCreateValidationEngine(ValidationEngineIdentifier.HL7_EMBEDDED, null, null,appLogger,LOG);
        getOrCreateValidationEngine(ValidationEngineIdentifier.HL7_API, null, null,appLogger,LOG);
        LOG.info("OrchestrationEngine:: initializeEngines -END");
    }

    public void orchestrate(@NotNull final OrchestrationSession... newSessions) {
        Span span = tracer.spanBuilder("OrchestrationEngine.orchestrate").startSpan();
        try {
            for (final OrchestrationSession session : newSessions) {
                sessions.put(session.getSessionId(), session);
                session.validate();
            }
        } finally {
            span.end();
        }
    }

    public void clear(@NotNull final OrchestrationSession... sessionsToRemove) {
        Span span = tracer.spanBuilder("OrchestrationEngine.clear").startSpan();
        try {
            if (sessionsToRemove != null && sessionsToRemove.length > 0) {
                for (OrchestrationSession session : sessionsToRemove) {
                    sessions.remove(session.getSessionId());
                }
            }
        } finally {
            span.end();
        }
    }

    private ValidationEngine getOrCreateValidationEngine(@NotNull final ValidationEngineIdentifier type,
            final Map<String, FhirV4Config> igPackages,
            final Tracer tracer, AppLogger appLogger,TemplateLogger LOG) {
        return validationEngineCache.computeIfAbsent(type, k -> {
            switch (type) {
                case HAPI:
                    return new HapiValidationEngine.Builder()
                            .withIgPackages(igPackages)
                            .withTracer(tracer)
                            .withAppLogger(appLogger)
                            .withTemplateLogger(LOG)
                            .build();
                case HL7_EMBEDDED:
                    return new Hl7ValidationEngineEmbedded.Builder().build();
                case HL7_API:
                    return new Hl7ValidationEngineApi.Builder().build();
                default:
                    throw new IllegalArgumentException("Unknown validation engine type: " + type);
            }
        });
    }
    public ValidationEngine getValidationEngine(@NotNull final ValidationEngineIdentifier type) {
        return this.validationEngineCache.get(type);
    }

    public OrchestrationSession.Builder session() {
        return new OrchestrationSession.Builder(this);
    }

    public enum ValidationEngineIdentifier {
        HAPI, HL7_EMBEDDED, HL7_API
    }

    private static class ValidationEngineKey {
        private final ValidationEngineIdentifier type;

        public ValidationEngineKey(@NotNull final ValidationEngineIdentifier type) {
            this.type = type;
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
            return type == that.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type);
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

        ValidationResult validate(@NotNull final String payload, final String interactionId , final String requestedIgVersion);
    }

    @Getter
    public static class HapiValidationEngine implements OrchestrationEngine.ValidationEngine {
        private final Observability observability;
        private final Instant engineInitAt = Instant.now();
        private final Instant engineConstructedAt;
        private String fhirProfileUrl;
        private final FhirContext fhirContext;
        private final Map<String, FhirV4Config> igPackages;
        private String igVersion;
        private final Tracer tracer;
        private final AppLogger appLogger;
        private final TemplateLogger LOG;
        private final String interactionId;
        private final List<FhirBundleValidator> fhirBundleValidators;
        
        private HapiValidationEngine(final Builder builder) {
            this.fhirProfileUrl = builder.fhirProfileUrl;
            this.fhirContext = FhirContext.forR4();
            this.engineConstructedAt = Instant.now();
            this.observability = new Observability(HapiValidationEngine.class.getName(),
                    "HAPI version %s (FHIR version %s)"
                            .formatted("8.0.0", fhirContext.getVersion().getVersion().getFhirVersionString()),
                    engineInitAt, engineConstructedAt);
            this.igPackages = builder.igPackages;
            this.igVersion = builder.igVersion;
            this.tracer = builder.tracer;
            this.appLogger = builder.appLogger;
            this.LOG = builder.LOG;
            this.interactionId = builder.interactionId;
            this.fhirBundleValidators = new ArrayList<>();
            initializeFhirBundleValidators();
        }

        private void initializeFhirBundleValidators() {
            Span span = tracer.spanBuilder("OrchestrationEngine.initializeFhirBundleValidators").startSpan();
            try {
                LOG.info("Processing SHIN-NY IG Packages... interaction Id: {}", interactionId);

                if (igPackages != null && igPackages.containsKey("fhir-v4")) {
                    FhirV4Config fhirV4Config = igPackages.get("fhir-v4");
                    Map<String, Map<String, String>> shinNyPackages = fhirV4Config.getShinnyPackages();
                    LOG.info("Number of SHIN-NY IG Packages to be loaded :{} for interactionId :{} ",
                            null == shinNyPackages ? 0 : shinNyPackages.size(), interactionId);
                    Map<String, String> basePackages = fhirV4Config.getBasePackages();
                    LOG.info("Number of Base Packages to be loaded :{} interactionId :{} ",
                            null == basePackages ? 0 : basePackages.size(), interactionId);
                    for (Map<String, String> igPackageMap : shinNyPackages.values()) {
                        String packagePath = igPackageMap.get("package-path");
                        String profileBaseUrl = igPackageMap.get("profile-base-url");
                        String igVersion = igPackageMap.get("ig-version");

                        LOG.info("Creating FhirBundleValidator for package: {} interactionId :{}", packagePath,
                                interactionId);

                        FhirBundleValidator bundleValidator = FhirBundleValidator.builder()
                                .fhirContext(FhirContext.forR4())
                                .fhirValidator(initializeFhirValidator(packagePath, basePackages,profileBaseUrl)) // Pass igPackageMap
                                                                                                   // directly
                                .baseFHIRUrl(profileBaseUrl)
                                .packagePath(packagePath)
                                .igVersion(igVersion)
                                .build();
                        fhirBundleValidators.add(bundleValidator);
                    }
                } else {
                    LOG.warn("No SHIN-NY IG Packages found in igPackages for interaction id :{}", interactionId);
                }
            } finally {
                span.end();
            }
        }

        public FhirValidator initializeFhirValidator(String shinNyPackagePath, Map<String, String> basePackages, String profileBaseUrl) {
            Span span = tracer.spanBuilder("OrchestrationEngine.initializeFhirValidator").startSpan();
            try {
                LOG.info("Initializing FHIR Validator for package: {} inteactionId :{} ", shinNyPackagePath,
                        interactionId);

                final var supportChain = new ValidationSupportChain();
                final var defaultSupport = new DefaultProfileValidationSupport(fhirContext);

                LOG.info("Adding IG Packages to NpmPackageValidationSupport for package : {} interactionId :{} ",
                        shinNyPackagePath, interactionId);
                var npmPackageValidationSupport = new NpmPackageValidationSupport(fhirContext);

                // Add shinNyPackage
                if (shinNyPackagePath != null) {
                    try {
                        LOG.info("Adding SHIN-NY IG Package: {} interactionId :{} ", shinNyPackagePath, interactionId);
                        npmPackageValidationSupport.loadPackageFromClasspath(shinNyPackagePath + "/package.tgz");
                    } catch (Exception e) {
                        LOG.error("Failed to load SHIN-NY package: {} interactionId :{}", shinNyPackagePath,
                                interactionId, e);
                    }
                }

                // Add hl7Packages
                if (basePackages != null && !basePackages.isEmpty()) {
                    LOG.info("Adding Base Packages... interaction id :{}", interactionId);
                    for (Map.Entry<String, String> entry : basePackages.entrySet()) {
                        String packageName = entry.getKey();
                        String packagePath = entry.getValue();
                        try {
                            LOG.info("Adding Base Package: {} at {} interactionId :{} ", packageName, packagePath,
                                    interactionId);
                            npmPackageValidationSupport.loadPackageFromClasspath(packagePath + "/package.tgz");
                        } catch (Exception e) {
                            LOG.error("Failed to load Base package: {} at {} interactionId: {} ", packageName,
                                    packagePath, interactionId, e);
                        }
                    }
                } else {
                    LOG.warn("No Base packages defined for interactionId : {}", interactionId);
                }

                supportChain.addValidationSupport(npmPackageValidationSupport);
                supportChain.addValidationSupport(defaultSupport);
                supportChain.addValidationSupport(new CommonCodeSystemsTerminologyService(fhirContext));
                supportChain.addValidationSupport(new SnapshotGeneratingValidationSupport(fhirContext));
                supportChain.addValidationSupport(new InMemoryTerminologyServerValidationSupport(fhirContext));
                final var prePopulateSupport = new PrePopulateSupport(tracer, appLogger);
                var prePopulatedValidationSupport = prePopulateSupport.build(fhirContext);
                prePopulateSupport.addCodeSystems(supportChain, prePopulatedValidationSupport);
                supportChain.addValidationSupport(prePopulatedValidationSupport);
                prePopulatedValidationSupport = null;
                final var postPopulateSupport = new PostPopulateSupport(tracer, appLogger);
                postPopulateSupport.update(supportChain,profileBaseUrl);
                final var cache = new CachingValidationSupport(supportChain);
                final var instanceValidator = new FhirInstanceValidator(cache);
                
                FhirValidator fhirValidator = fhirContext.newValidator().registerValidatorModule(instanceValidator);
                // fhirValidator.setConcurrentBundleValidation(true);
                // ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                // fhirValidator.setExecutorService(executorService);
                return fhirValidator;                
            } finally {
                span.end();
            }
        }

        private String extractProfileUrl(String jsonString) {
            try {
                JsonNode rootNode = Configuration.objectMapper.readTree(jsonString);
                JsonNode metaNode = rootNode.path("meta").path("profile");

                List<String> profileList = Optional.ofNullable(metaNode)
                        .filter(JsonNode::isArray)
                        .map(node -> StreamSupport.stream(node.spliterator(), false)
                                .map(JsonNode::asText)
                                .collect(Collectors.toList()))
                        .orElse(List.of());

                return profileList.isEmpty() ? null : profileList.get(0); // Return the first profile URL
            } catch (Exception e) {
                LOG.error("Error extracting profile URL from payload for interactionId : {} ", interactionId, e);
                return StringUtils.EMPTY;
            }
        }

        public FhirBundleValidator findFhirBundleValidator(String profileUrl) {
            return fhirBundleValidators.stream()
                    .peek(validator -> System.out.println("Checking Profile URL: " + validator.getFhirProfileUrl()))
                    .filter(validator -> validator.getFhirProfileUrl().equals(profileUrl))
                    .findFirst()
                    .orElse(null);
        }

        // 1. Validate after parsing into Bundle
        public ca.uhn.fhir.validation.ValidationResult validateAsBundle(
                String payload,
                FhirContext fhirContext,
                FhirBundleValidator bundleValidator,
                String interactionId) {

            LOG.debug("BUNDLE PAYLOAD parse -BEGIN for interactionId:{}", interactionId);
            final var bundle = fhirContext.newJsonParser().parseResource(Bundle.class, payload);
            LOG.debug("BUNDLE PAYLOAD parse -END for interactionId:{}", interactionId);

            final var hapiVR = bundleValidator.getFhirValidator().validateWithResult(bundle);
            return hapiVR;
        }

        // 2. Validate using raw JSON payload
        public ca.uhn.fhir.validation.ValidationResult validateAsRawPayload(
                String payload,
                FhirContext fhirContext,
                FhirBundleValidator bundleValidator,
                String interactionId) {

            LOG.debug("RAW PAYLOAD validation -BEGIN for interactionId:{}", interactionId);
            fhirContext.newJsonParser().parseResource(Bundle.class, payload);
            final var hapiVR = bundleValidator.getFhirValidator().validateWithResult(payload);
            ;
            LOG.debug("RAW PAYLOAD validation -END for interactionId:{}", interactionId);

            return hapiVR;
        }
        
        @Override
        public OrchestrationEngine.ValidationResult validate(@NotNull final String payload,
                final String interactionId, final String requestedIgVersion) {
            final var initiatedAt = Instant.now();
            Span span = tracer.spanBuilder("OrchestrationEngine.validate").startSpan();
            try {
                try {
                    LOG.info("VALIDATOR -BEGIN initiated At : {} for interactionid:{}", initiatedAt, interactionId);
                    String profileUrl = extractProfileUrl(payload);
                    LOG.info("Extracted Profile URL: {} for interactionId :{} ", profileUrl, interactionId);
                    FhirBundleValidator bundleValidator;
                    String shinNyPackagePath = null;
                    var headerIgVersion = requestedIgVersion;

                    if (headerIgVersion != null) {
                        if (profileUrl != null && profileUrl.toLowerCase().contains("test")) {
                            shinNyPackagePath = "ig-packages/shin-ny-ig/test-shinny/v" + headerIgVersion;
                        } else {
                            shinNyPackagePath = "ig-packages/shin-ny-ig/shinny/v" + headerIgVersion;
                        }

                        if (!CoreFHIRUtil.ensureEngineVersionMatches(shinNyPackagePath)) {
                            headerIgVersion = null;
                        }
                    }

                    if (headerIgVersion != null) {
                        LOG.info("requested IG Version : " + headerIgVersion);
                        Map<String, String> basePackages = Map.of(
                                "us-core", "ig-packages/fhir-v4/us-core/stu-7.0.0",
                                "sdoh", "ig-packages/fhir-v4/sdoh-clinicalcare/stu-2.2.0",
                                "uv-sdc", "ig-packages/fhir-v4/uv-sdc/stu-3.0.0");
                        
                        String profileBaseUrl = profileUrl;
                        if (profileUrl != null) {
                            int idx = profileUrl.indexOf("/StructureDefinition/");
                            if (idx != -1) {
                                profileBaseUrl = profileUrl.substring(0, idx);
                            }
                        }                                
                        bundleValidator = FhirBundleValidator.builder()
                                .fhirContext(FhirContext.forR4())
                                .fhirValidator(initializeFhirValidator(shinNyPackagePath, basePackages, profileBaseUrl))
                                .baseFHIRUrl(profileBaseUrl)
                                .packagePath(shinNyPackagePath)
                                .igVersion(headerIgVersion)
                                .build();
                        fhirBundleValidators.add(bundleValidator);
                    } else {
                        bundleValidator = findFhirBundleValidator(profileUrl);
                    }
                    if (bundleValidator == null) {
                        LOG.warn("No matching FhirBundleValidator found for profile URL: {} for interactionId :{}",
                                profileUrl, interactionId);
                        throw new JsonValidationException(ErrorCode.INVALID_BUNDLE_PROFILE);
                    } else {
                        LOG.info(
                                "Bundle validated against version :{} using package at path: {} for interactionId :{} ",
                                bundleValidator.getIgVersion(), bundleValidator.getPackagePath(), interactionId);
                    }
                    this.igVersion = bundleValidator.getIgVersion();
                                        this.fhirProfileUrl = bundleValidator.getFhirProfileUrl();
                    fhirContext.setParserErrorHandler(new LenientErrorHandler());

                    final var hapiVR = validateAsRawPayload(payload, fhirContext, bundleValidator, interactionId);
                    final var completedAt = Instant.now();
                    LOG.info("VALIDATOR -END completed at :{} ms for interactionId:{} with ig version :{}",
                            Duration.between(initiatedAt, completedAt).toMillis(), interactionId, igVersion);
                    this.igVersion = bundleValidator.getIgVersion();  
                    return new OrchestrationEngine.ValidationResult() {
                        @Override
                        @JsonSerialize(using = JsonTextSerializer.class)
                        public String getOperationOutcome() {
                            final var jp = FhirContext.forR4Cached().newJsonParser();
                            OperationOutcome outcome = (OperationOutcome) hapiVR.toOperationOutcome();
                            return jp.encodeResourceToString(outcome);
                        }

                        @Override
                        public boolean isValid() {
                            return hapiVR.isSuccessful();
                        }

                        @Override
                        public String getProfileUrl() {
                            LOG.info("Profile url in final outcome :{}  for interactionId :{} ",
                                    HapiValidationEngine.this.fhirProfileUrl, interactionId);
                            return HapiValidationEngine.this.fhirProfileUrl;
                        }

                        @Override
                        public String getIgVersion() {
                            LOG.info("IG version in final outcome :{}    for interactionId :{} ", igVersion,
                                    interactionId);
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
            private Map<String, FhirV4Config> igPackages;
            private String igVersion;
            private String interactionId;
            private Tracer tracer;
            private AppLogger appLogger;
            private TemplateLogger LOG;

            public Builder withInteractionId(@NotNull final String interactionId) {
                this.interactionId = interactionId;
                return this;
            }

            public Builder withFhirProfileUrl(@NotNull final String fhirProfileUrl) {
                this.fhirProfileUrl = fhirProfileUrl;
                return this;
            }

            public Builder withIgPackages(@NotNull final Map<String, FhirV4Config> igPackages) {
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
            public Builder withAppLogger(@NotNull final AppLogger appLogger) {
                this.appLogger = appLogger;
                return this;
            }
            public Builder withTemplateLogger(@NotNull final TemplateLogger LOG) {
                this.LOG = LOG;
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
        public ValidationResult validate(@NotNull final String payload, final String interactionId, final String requestedIgVersion) {
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
        public ValidationResult validate(@NotNull final String payload, final String interactionId, final String requestedIgVersion) {
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
        private String requestedIgVersion;

        private OrchestrationSession(final Builder builder) {
            this.sessionId = builder.sessionId;
            this.payloads = Collections.unmodifiableList(builder.payloads);
            this.validationEngines = Collections.unmodifiableList(builder.validationEngines);
            this.validationResults = new ArrayList<>();
            this.fhirProfileUrl = builder.fhirProfileUrl;
            this.device = builder.device;
            this.interactionId = builder.interactionId;
            this.requestedIgVersion = builder.requestedIgVersion;
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

        public void validate() {
            for (final String payload : payloads) {
                for (final ValidationEngine engine : validationEngines) {
                    final ValidationResult result = engine.validate(payload, interactionId, requestedIgVersion);
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
            private Map<String, FhirV4Config> igPackages;
            private String igVersion;
            private String sessionId;
            private String interactionId;
            private Tracer tracer;
            private AppLogger appLogger;
            private TemplateLogger LOG;
            private String requestedIgVersion;

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

            public Builder withPayloads(@NotNull final List<String> payloads) {
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

            public Builder withRequestedIgVersion(String requestedIgVersion) {
                this.requestedIgVersion = requestedIgVersion;
                return this;
            }
            
            public Builder withFhirIGPackages(@NotNull final Map<String, FhirV4Config> igPackages) {
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

            public Builder withAppLogger(@NotNull final AppLogger appLogger) {
                this.appLogger = appLogger;
                return this;
            }

            public Builder withTemplateLogger(@NotNull final TemplateLogger LOG) {
                this.LOG = LOG;
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

            public Builder addValidationEngine(@NotNull final ValidationEngine validationEngine) {
                this.validationEngines.add(validationEngine);
                return this;
            }

            public Builder addHapiValidationEngine() {
                this.validationEngines
                        .add(engine.getValidationEngine(ValidationEngineIdentifier.HAPI));
                return this;
            }

            public Builder addHl7ValidationEmbeddedEngine() {
                this.validationEngines
                        .add(engine.getValidationEngine(ValidationEngineIdentifier.HL7_EMBEDDED));
                return this;
            }

            public Builder addHl7ValidationApiEngine() {
                this.validationEngines
                        .add(engine.getValidationEngine(ValidationEngineIdentifier.HL7_API));
                return this;
            }

            public OrchestrationSession build() {
                return new OrchestrationSession(this);
            }

        }
    }
}