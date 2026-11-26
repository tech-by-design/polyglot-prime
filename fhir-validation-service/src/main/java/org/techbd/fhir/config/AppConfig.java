package org.techbd.fhir.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "org.techbd")
@Getter
@Setter
@Primary
public class AppConfig {
    private String version;
    private String defaultDatalakeApiUrl;
    private String operationOutcomeHelpUrl;
    private Map<String, String> structureDefinitionsUrls;
    private String baseFHIRURL;
    private String fhirVersion;
    private String igVersion;
    private CsvValidation csv;
    private Map<String, FhirV4Config> igPackages;
    private String dataLedgerApiUrl;
    private String validationSeverityLevel;
    private String dataLedgerApiKeySecretName;
    private DynamicValidatorConfig dynamicValidators = new DynamicValidatorConfig();
    private ValidationConcurrencyConfig validationConcurrency = new ValidationConcurrencyConfig();

    /**
     * Limits how many bundle validations run at the same time, across all validators. HAPI's
     * in-memory terminology support re-expands large ValueSets (SNOMED, ICD-10, LOINC, ...) for
     * each code it has not checked before, allocating hundreds of MB per validation in flight.
     */
    @Getter
    @Setter
    public static class ValidationConcurrencyConfig {
        /** Validations allowed to run at once. Raise only together with the heap size. */
        private int maxConcurrent = 2;
        /** How long a request waits for a validation slot before failing. */
        private Duration acquireTimeout = Duration.ofMinutes(5);
    }

    /**
     * Limits for "dynamic" validators: validators built on demand for an IG version requested
     * in the request header that is not one of the configured {@code shinny-packages}. Each one
     * holds roughly 0.7-0.8 GB of heap, so they are cached in a small pool, expired when idle,
     * and built and used one at a time.
     */
    @Getter
    @Setter
    public static class DynamicValidatorConfig {
        /** Maximum dynamic validators kept in memory. 0 disables them: the header IG version is
         * ignored and the bundle is validated by the configured validator matching its profile. */
        private int maxCached = 1;
        /** A dynamic validator not used for this long is evicted. */
        private Duration idleTtl = Duration.ofMinutes(30);
        /** How long a request waits for its turn to use a dynamic validator before failing. */
        private Duration acquireTimeout = Duration.ofMinutes(2);
    }

    @Getter
    @Setter
    public static class FhirV4Config {
        //private Map<String, Map<String, String>> shinnyPackages;
        private Map<String, ShinnyPackageConfig> shinnyPackages;
        private Map<String, String> basePackages;
    }

    @Getter
    @Setter
    public static class ShinnyPackageConfig {
        private String profileBaseUrl;
        private String packagePath;
        private String igVersion;
        private Map<String, String> basePackages;
    }

    public record CsvValidation(Validation validation) {
    public record Validation(String pythonScriptPath,String pythonExecutable,String packagePath,String outputPath,String inboundPath,String ingressHomePath) {
    }
    }
    
}