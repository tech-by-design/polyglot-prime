package org.techbd.service.http;

import java.util.ArrayList;
import java.util.List;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.techbd.service.http.hub.prime.AppConfig;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class SwaggerConfig {

    private final AppConfig appConfig;

    @Value("${TECHBD_HUB_PRIME_FHIR_UI_BASE_URL:#{null}}")
    private String hubApiUrl;

    @Value("${TECHBD_HUB_PRIME_FHIR_API_BASE_URL:#{null}}")
    private String fhirApiUrl;

    public SwaggerConfig(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Bean
    public OpenAPI springOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Tech by Design FHIR Server")
                        .description("Public REST API Endpoints")
                        .version(appConfig.getVersion())
                        .license(new License().name("GitHub Repository")
                                .url("https://github.com/tech-by-design/polyglot-prime")))
                .externalDocs(new ExternalDocumentation()
                        .description("Tech by Design Technical Documents Microsite")
                        .url("https://tech-by-design.github.io/docs.techbd.org/"))
        // .addServersItem(new Server().url(serverUrl).description("Environment-specific
        // server URL"))
        ;
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
                                    """
                                    .replace("\n", "%n"),
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
                                    """
                                    .replace("\n", "%n"),
                            Interactions.Servlet.HeaderName.PREFIX))
                    .required(false);

            operation.addParametersItem(interactionPersistStrategy);
            operation.addParametersItem(interactionProvenance);
            return operation;
        };
    }

    @Bean
    public GroupedOpenApi techByDesignHubApiGroup() {
        return GroupedOpenApi.builder()
                .group("Hub Self-Service UI API")
                .pathsToMatch("/api/ux/**",
                        "/actuator", "/actuator/**",
                        "/presentation/shell/**",
                        "/support/interaction/**",
                        "/interactions/**",
                        "/mock/shinny-data-lake/**")
                .addOpenApiCustomizer(openApi -> {
                    List<Server> servers = new ArrayList<>(); // Create a new modifiable list, and
                                                              // clear generated server
                    servers.add(new Server()
                            .url(hubApiUrl)
                            .description("Tech by Design Hub Self-Service UI API Server"));
                    openApi.setServers(servers);
                })
                .build();
    }

    @Bean
    public GroupedOpenApi techByDesignFhirApiGroup() {
        return GroupedOpenApi.builder()
                .group("FHIR API")
                .pathsToMatch("/metadata",
                        "/Bundle", "/Bundle/**",
                        "/flatfile/csv/Bundle", "/flatfile/csv/Bundle/**",
                        "/api/expect/fhir/**")
                .addOpenApiCustomizer(openApi -> {
                    // Set custom servers
                    List<Server> servers = new ArrayList<>();
                    servers.add(new Server()
                            .url(fhirApiUrl)
                            .description("Tech by Design FHIR API Server"));
                    openApi.setServers(servers);
    
                    // Add reusable FileUpload schema
                    openApi.getComponents().addSchemas("FileUpload", new io.swagger.v3.oas.models.media.Schema<>()
                            .type("object")
                            .addProperties("file", new io.swagger.v3.oas.models.media.Schema<>()
                                    .type("file")
                                    .format("binary")));
    
                    // Add Mirth Endpoint 1
                    openApi.getPaths().addPathItem("/ccda/Bundle", new PathItem()
                            .post(new Operation()
                                    .tags(List.of("Tech by Design Hub CCDA Endpoints"))
                                    .summary("CCDA endpoint to validate and convert XML to JSON, then store, and forward a payload to SHIN-NY. If you want to validate a payload and not store it or forward it to SHIN-NY, use /ccda/Bundle/$validate.")
                                    .description("CCDA endpoint to validate and convert XML to JSON, then store, and forward a payload to SHIN-NY.")
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-Tenant-ID")
                                            .description("Mandatory header for Tenant ID")
                                            .required(true)
                                            .in("header")
                                            .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                            .name("X-TechBD-Base-FHIR-URL")
                                            .description("Optional header to specify the base FHIR URL. If provided, it will be used in the generated FHIR; otherwise, the default value will be used.")
                                            .required(false)
                                            .in("header")
                                            .schema(new StringSchema()))
                                       .addParametersItem(new Parameter()
                                            .name("X-TechBD-Validation-Severity-Level")
                                            .description(
                                                    "Optional header to set validation severity level (`information`, `warning`, `error`, `fatal`).")
                                            .required(false)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                                            .description("Multipart form-data containing the CCDA XML file for validation, conversion to JSON and submission to SHIN-NY.")
                                            .required(true)
                                            .content(new io.swagger.v3.oas.models.media.Content()
                                                    .addMediaType("multipart/form-data", new io.swagger.v3.oas.models.media.MediaType()
                                                            .schema(new io.swagger.v3.oas.models.media.Schema<>()
                                                                    .$ref("#/components/schemas/FileUpload")))))
                                    .responses(new ApiResponses()
                                            .addApiResponse("200",
                                                    new ApiResponse()
                                                            .description("Successful response"))
                                            .addApiResponse("400",
                                                    new ApiResponse()
                                                            .description("Bad request"))
                                            .addApiResponse("500",
                                                    new ApiResponse()
                                                            .description("Server error")))));
    
                    // Add Mirth Endpoint 2
                    openApi.getPaths().addPathItem("/ccda/Bundle/$validate", new PathItem()
                            .post(new Operation()
                                    .tags(List.of("Tech by Design Hub CCDA Endpoints"))
                                    .summary("CCDA endpoint to validate and convert XML to JSON but not store or forward a payload to SHIN-NY. If you want to validate a payload, store it and then forward it to SHIN-NY, use /ccda/Bundle not /ccda/Bundle/$validate.")
                                    .description("CCDA endpoint to validate and convert XML to JSON but not store or forward a payload to SHIN-NY.")
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-Tenant-ID")
                                            .description("Tenant ID header")
                                            .required(true)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                                            .description("Multipart form-data containing the CCDA XML file for validation.")
                                            .required(true)
                                            .content(new io.swagger.v3.oas.models.media.Content()
                                                    .addMediaType("multipart/form-data", new io.swagger.v3.oas.models.media.MediaType()
                                                            .schema(new io.swagger.v3.oas.models.media.Schema<>()
                                                                    .$ref("#/components/schemas/FileUpload")))))
                                    .responses(new ApiResponses()
                                            .addApiResponse("200",
                                                    new ApiResponse()
                                                            .description("Successful validation response"))
                                            .addApiResponse("400",
                                                    new ApiResponse()
                                                            .description("Bad request"))
                                            .addApiResponse("500",
                                                    new ApiResponse()
                                                            .description("Server error")))));
                })
                .build();
    }
}
