package org.techbd.service.api.http.fhir;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;

@Configuration
public class SwaggerConfig {
    // retrieve from properties file which is injected from pom.xml
    @Value("${org.techbd.service.api.http.fhir.FhirApplication.version}")
    private String appVersion;

    public static final String REQ_HEADER_TECH_BD_FHIR_SERVICE_PERSIST_ERROR = "TECH_BD_FHIR_SERVICE_PERSIST_ERROR";

    public static final String REQ_HEADER_TECH_BD_INTERACTION_PERSISTENCE = "TECH_BD_INTERACTION_PERSISTENCE";
    public static final String REQ_HEADER_TECH_BD_FHIR_SERVICE_QE_IDENTIFIER = "TECH_BD_FHIR_SERVICE_QE_IDENTIFIER";
    public static final String REQ_HEADER_TECH_BD_FHIR_SERVICE_QE_NAME = "TECH_BD_FHIR_SERVICE_QE_NAME";

    public static final String RESP_HEADER_TECH_BD_INTERACTION_PERSISTENCE_STRATEGY_ARGS = "TECH_BD_INTERACTION_PERSISTENCE_STRATEGY_ARGS";
    public static final String RESP_HEADER_TECH_BD_INTERACTION_PERSISTENCE_STRATEGY = "TECH_BD_INTERACTION_PERSISTENCE_STRATEGY";
    public static final String RESP_HEADER_TECH_BD_INTERACTION_PERSISTENCE_STRATEGY_INSTANCE = "TECH_BD_INTERACTION_PERSISTENCE_STRATEGY_INSTANCE";

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

        @Bean
    public OperationCustomizer customGlobalHeaders() {

        return (Operation operation, HandlerMethod handlerMethod) -> {

            Parameter interactionPersistStrategy = new Parameter()
                    .in(ParameterIn.HEADER.toString())
                    .schema(new StringSchema())
                    .name(REQ_HEADER_TECH_BD_INTERACTION_PERSISTENCE)
                    .description("""
                        Instructs servlet to serialize full HTTP request/response and store it in memory, file system, SFTP, BlobStore, etc.
                        Based on which strategy is chosen, the Response Headers will include `TECH_BD_INTERACTION_PERSISTENCE_*` values.

                        - Default: `{ "nature": "diagnostics" }`
                        - File system: `{ "nature": "fs", "home": "TECHBD_INTERACTIONS" }`
                        - SFTP: TODO `{ "nature": "sftp", "arg1": 1, "arg2": 2 }`
                        - BlobStore: TODO `{ "nature": "aws-s3", "arg1": 1, "arg2": 2 }`
                        """)
                    .required(false);
                    
            operation.addParametersItem(interactionPersistStrategy);
            return operation;
        };
    }

}