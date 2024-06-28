package org.techbd.service.http.hub.prime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.techbd.conf.Configuration;

@org.springframework.context.annotation.Configuration
@ConfigurationProperties(prefix = "org.techbd.service.http.hub.prime")
@ConfigurationPropertiesScan
public class AppConfig {

    public class Servlet {
        public static final String FHIR_CONTENT_TYPE_HEADER_VALUE = "application/fhir+json";
        public class HeaderName {
            public class Request {
                public static final String FHIR_STRUCT_DEFN_PROFILE_URI = Configuration.Servlet.HeaderName.PREFIX
                        + "FHIR-Profile-URI";
                public static final String FHIR_VALIDATION_STRATEGY = Configuration.Servlet.HeaderName.PREFIX
                        + "FHIR-Validation-Strategy";
                public static final String DATALAKE_API_URL = Configuration.Servlet.HeaderName.PREFIX
                        + "DataLake-API-URL";
                public static final String DATALAKE_API_CONTENT_TYPE = Configuration.Servlet.HeaderName.PREFIX
                        + "DataLake-API-Content-Type";
            }

            public class Response {
                // in case they're necessary
            }
        }
    }

    private String version;
    private String defaultSdohFhirProfileUrl;
    private String defaultDatalakeApiUrl;

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

    public String getDefaultSdohFhirProfileUrl() {
        return defaultSdohFhirProfileUrl;
    }

    public void setDefaultSdohFhirProfileUrl(String defaultFhirProfileUrl) {
        this.defaultSdohFhirProfileUrl = defaultFhirProfileUrl;
    }

    public String getDefaultDatalakeApiUrl() {
        return defaultDatalakeApiUrl;
    }

    public void setDefaultDatalakeApiUrl(String defaultDatalakeApiUrl) {
        this.defaultDatalakeApiUrl = defaultDatalakeApiUrl;
    }
}
