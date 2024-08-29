package org.techbd.service.http;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.techbd.service.http.hub.prime.AppConfig;

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
    private final AppConfig appConfig;

    public SwaggerConfig(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Bean
    public OpenAPI springOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Tech by Design FHIR Server")
                        .description("Public REST API Endpoints").version(appConfig.getVersion())
                        .license(new License().name("GitHub Repository")
                                .url("https://github.com/tech-by-design/polyglot-prime")))
                .externalDocs(new ExternalDocumentation().description("Tech by Design Technical Documents Microsite")
                        .url("https://tech-by-design.github.io/docs.techbd.org/"));
    }

    @Bean
    public OperationCustomizer customGlobalHeaders() {

        return (Operation operation, HandlerMethod handlerMethod) -> {

            final var interactionPersistStrategy = new Parameter()
                    .in(ParameterIn.HEADER.toString())
                    .schema(new StringSchema())
                    .name(Interactions.Servlet.HeaderName.Request.PERSISTENCE_STRATEGY)
                    .description(String.format(
                            """
                                    Instructs servlet to serialize full HTTP request/response and store it in memory, file system, SFTP, BlobStore, etc.
                                    Based on which strategy is chosen, the Response Headers will include `%s*` values. The header value may be either a single
                                    object or an array of objects (if multiple persistence strategies are desired).

                                    - Default: `{ "nature": "diagnostics" }`
                                    - File system: `{ "nature": "fs", "fsPath": "${cwd()}/TECHBD_INTERACTIONS/${formattedDateNow('yyyy/MM/dd/HH')}/${artifactId}.json" }`
                                    - VFS TempFS: `{ "nature": "vfs", "vfsUri": "tmp://techbd.org/interaction-artifacts/${formattedDateNow('yyyy/MM/dd/HH')}/${artifactId}.json" }`
                                    - Email: `{ "nature": "email", "from": "support@techbd.org", "to": "toroj11859@qiradio.com", "subject": "test FHIR email" }`
                                    - VFS SFTP: `{ "nature": "vfs", "vfsUri": "sftp://*****:******@sftp.example.com:22/log/synthetic.fhir.api.techbd.org/{{TECH_BD_FHIR_SERVICE_QE_IDENTIFIER}}/interaction-artifacts/${formattedDateNow('yyyy/MM/dd/HH')}/${artifactId}.json" }`
                                    - BlobStore: TODO `{ "nature": "aws-s3", "arg1": 1, "arg2": 2 }`
                                    - Aggregated: `[{ ... first ... }, { ... second ... }]`

                                    ${cwd()} refers to current working directory (CWD) on the API server, ${artifactId} refers to the `interactionId`.
                                    """.replace("\n", "%n"),
                            Interactions.Servlet.HeaderName.PREFIX))
                    .required(false);

                final var interactionProvenance = new Parameter()
                    .in(ParameterIn.HEADER.toString())
                    .schema(new StringSchema())
                    .name(Interactions.Servlet.HeaderName.Request.PROVENANCE)
                    .description(String.format(
                            """
                                    Instructs servlet to send a "provenance" JSON object for tracking in database.
                                    Something like this (as long as it's a JSON object, the content is arbitrary):

                                    - { "nature": "integration-test", "test-case": "fhir-fixture-shinny-impl-guide-sample.json" }
                                    - { "nature": "synthetic-scoring", "test-case": "qe-001" }
                                    """.replace("\n", "%n"),
                            Interactions.Servlet.HeaderName.PREFIX))
                    .required(false);

            operation.addParametersItem(interactionPersistStrategy);
            operation.addParametersItem(interactionProvenance);
            return operation;
        };
    }

}