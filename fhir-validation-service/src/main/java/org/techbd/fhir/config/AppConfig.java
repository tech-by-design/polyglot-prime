package org.techbd.fhir.config;

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
    private boolean dataLedgerTracking;
    private boolean dataLedgerDiagnostics;    
    private String validationSeverityLevel;
    private String dataLedgerApiKeySecretName;
    @Getter
    @Setter
    public static class FhirV4Config {
        private Map<String, Map<String, String>> shinnyPackages;
        private Map<String, String> basePackages;
    }

    public record CsvValidation(Validation validation) {
    public record Validation(String pythonScriptPath,String pythonExecutable,String packagePath,String outputPath,String inboundPath,String ingressHomePath) {
    }
    }
    
}