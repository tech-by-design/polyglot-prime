package org.techbd.config;

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
public class CoreAppConfig {
    private String version;
    private String defaultDatalakeApiUrl;
    private String operationOutcomeHelpUrl;
    private Map<String, String> structureDefinitionsUrls;
    private String baseFHIRURL;
    private DefaultDataLakeApiAuthn defaultDataLakeApiAuthn;
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

    public record DefaultDataLakeApiAuthn(String mTlsStrategy,
            MTlsAwsSecrets mTlsAwsSecrets,
            PostStdinPayloadToNyecDataLakeExternal postStdinPayloadToNyecDataLakeExternal,
            MTlsResources mTlsResources,
            WithApiKeyAuth withApiKeyAuth) {
    }

    public record WithApiKeyAuth(String apiKeyHeaderName,String apiKeySecretName) {
    }

    public record MTlsResources( String mTlsKeyResourceName, String mTlsCertResourceName) {
    }

    public record MTlsAwsSecrets(String mTlsKeySecretName, String mTlsCertSecretName) {
    }

    public record PostStdinPayloadToNyecDataLakeExternal(String cmd, int timeout) {
    }

    public record CsvValidation(Validation validation) {
    public record Validation(String pythonScriptPath,String pythonExecutable,String packagePath,String outputPath,String inboundPath,String ingressHomePath) {
    }
    }
    
}