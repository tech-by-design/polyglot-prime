package org.techbd.service.api.http.fhir;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

@Configuration
public class SwaggerConfig {
    // retrieve from properties file which is injected from pom.xml
    @Value("${org.techbd.service.api.http.fhir.FhirApplication.version}")
    private String appVersion;

    @Bean
    public OpenAPI springOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("TechBD FHIR Server")
                        .description("Public REST API Endpoints").version(appVersion)
                        .license(new License().name("GitHub Repository")
                                .url("https://github.com/tech-by-design/polyglot-prime")))
                .externalDocs(new ExternalDocumentation().description("TechBD Technical Documents Microsite")
                        .url("https://tech-by-design.github.io/docs.techbd.org/"));
    }
}