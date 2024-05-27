package org.techbd.service.api.http.fhir;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.techbd.service.api.http.Interactions;

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
    private final FhirAppConfiguration appConfig;        

    public SwaggerConfig(final FhirAppConfiguration appConfig) {
        this.appConfig = appConfig;
    }

    @Bean
    public OpenAPI springOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("TechBD FHIR Server")
                        .description("Public REST API Endpoints").version(appConfig.getVersion())
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
                    .name(Interactions.Servlet.HeaderName.Request.PERSISTENCE_STRATEGY)
                    .description(String.format("""
                        Instructs servlet to serialize full HTTP request/response and store it in memory, file system, SFTP, BlobStore, etc.
                        Based on which strategy is chosen, the Response Headers will include `%s*` values.

                        - Default: `{ "nature": "diagnostics" }`
                        - File system: `{ "nature": "fs", "fsPath": "${cwd()}/TECHBD_INTERACTIONS/${formattedDateNow('yyyy/MM/dd/HH')}/${artifactId}.json" }`
                        - VFS TempFS: `{ "nature": "vfs", "vfsUri": "tmp://techbd.org/interaction-artifacts/${formattedDateNow('yyyy/MM/dd/HH')}/${artifactId}.json" }`
                        - Email: `{ "nature": "email", "from": "toroj11859@qiradio.com", "to": "toroj11859@qiradio.com", "subject": "test FHIR email" }`
                        - VFS SFTP: `{ "nature": "vfs", "vfsUri": "sftp://*****:******@sftp.example.com:22/log/synthetic.fhir.api.techbd.org/{{TECH_BD_FHIR_SERVICE_QE_IDENTIFIER}}/interaction-artifacts/${formattedDateNow('yyyy/MM/dd/HH')}/${artifactId}.json" }`
                        - BlobStore: TODO `{ "nature": "aws-s3", "arg1": 1, "arg2": 2 }`

                        ${cwd()} refers to current working directory (CWD) on the API server, ${artifactId} refers to the `interactionId`.
                        """, Interactions.Servlet.HeaderName.PREFIX))
                    .required(false);
                    
            operation.addParametersItem(interactionPersistStrategy);
            return operation;
        };
    }

}