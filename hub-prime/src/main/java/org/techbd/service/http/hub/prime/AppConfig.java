package org.techbd.service.http.hub.prime;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.techbd.conf.Configuration;

import lombok.Getter;
import lombok.Setter;

@org.springframework.context.annotation.Configuration
@ConfigurationProperties(prefix = "org.techbd.service.http.hub.prime")
@ConfigurationPropertiesScan
public class AppConfig {

    public static class Servlet {

        public static final String FHIR_CONTENT_TYPE_HEADER_VALUE = "application/fhir+json";

        public static class HeaderName {

            public static class Request {

                public static final String FHIR_STRUCT_DEFN_PROFILE_URI = Configuration.Servlet.HeaderName.PREFIX
                        + "FHIR-Profile-URI";
                public static final String FHIR_VALIDATION_STRATEGY = Configuration.Servlet.HeaderName.PREFIX
                        + "FHIR-Validation-Strategy";
                public static final String DATALAKE_API_URL = Configuration.Servlet.HeaderName.PREFIX
                        + "DataLake-API-URL";
                public static final String DATALAKE_API_CONTENT_TYPE = Configuration.Servlet.HeaderName.PREFIX
                        + "DataLake-API-Content-Type";
                public static final String HEALTH_CHECK_HEADER = Configuration.Servlet.HeaderName.PREFIX
                        + "HealthCheck";
            }

            public static class Response {
                // in case they're necessary
            }
        }
    }

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

    @Getter
    @Setter
    public static class FhirV4Config {
        private Map<String, Map<String,String>> shinnyPackages; 
        private Map<String, String> basePackages;
    }

    public Map<String, FhirV4Config> getIgPackages() {
        return igPackages;
    }

    public void setIgPackages(Map<String, FhirV4Config> igPackages) {
        this.igPackages = igPackages;
    }

    public String getVersion() {
        return version;
    }

    /**
     * Spring Boot will retrieve required value from properties file which is
     * injected from pom.xml.
     *
     * @param version the version of the application
     */
    public void setVersion(String version) {
        this.version = version;
    }

    public String getDefaultDatalakeApiUrl() {
        return defaultDatalakeApiUrl;
    }

    public void setDefaultDatalakeApiUrl(String defaultDatalakeApiUrl) {
        this.defaultDatalakeApiUrl = defaultDatalakeApiUrl;
    }
    public String getOperationOutcomeHelpUrl() {
        return operationOutcomeHelpUrl;
    }

    public void setOperationOutcomeHelpUrl(String operationOutcomeHelpUrl) {
        this.operationOutcomeHelpUrl = operationOutcomeHelpUrl;
    }
    public void setStructureDefinitionsUrls(Map<String, String> structureDefinitionsUrls) {
        this.structureDefinitionsUrls = structureDefinitionsUrls;
    }

    public Map<String, String> getStructureDefinitionsUrls() {
        return structureDefinitionsUrls;
    }

    public DefaultDataLakeApiAuthn getDefaultDataLakeApiAuthn() {
        return defaultDataLakeApiAuthn;
    }

    public void setDefaultDataLakeApiAuthn(DefaultDataLakeApiAuthn defaultDataLakeApiAuthn) {
        this.defaultDataLakeApiAuthn = defaultDataLakeApiAuthn;
    }
    public String getBaseFHIRURL() {
        return baseFHIRURL;
    }

    public void setBaseFHIRURL(String baseFHIRURL) {
        this.baseFHIRURL = baseFHIRURL;
    }
    public record DefaultDataLakeApiAuthn(
            String mTlsStrategy,
            MTlsAwsSecrets mTlsAwsSecrets,
            PostStdinPayloadToNyecDataLakeExternal postStdinPayloadToNyecDataLakeExternal,
            MTlsResources mTlsResources) {
    }

    public record MTlsResources(String mTlsKeyResourceName, String mTlsCertResourceName) {
    }

    public record MTlsAwsSecrets(String mTlsKeySecretName, String mTlsCertSecretName) {
    }

    public record PostStdinPayloadToNyecDataLakeExternal(String cmd, int timeout) {
    }
    public record CsvValidation(Validation validation) { public record Validation(String pythonScriptPath, String pythonExecutable, String packagePath, String outputPath,  String inboundPath, String ingessHomePath) {} }

    public CsvValidation getCsv() {
        return csv;
    }

    public void setCsv(CsvValidation csv) {
        this.csv = csv;
    }
    public String getFhirVersion() {
        return fhirVersion;
    }

    public void setFhirVersion(String fhirVersion) {
        this.fhirVersion = fhirVersion;
    }

    public void setIgVersion(String igVersion) {
        this.igVersion = igVersion;
    }

    public String getIgVersion() {
        return igVersion;
    }
}
