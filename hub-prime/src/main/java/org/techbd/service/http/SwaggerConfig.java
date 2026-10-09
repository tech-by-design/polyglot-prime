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
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;

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
                        // .addServersItem(new Server().url(serverUrl).description("Environment-specific server URL"))
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
                    List<Server> servers = new ArrayList<>(); // Create a new modifiable list, and clear generated server
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
                .pathsToMatch("/metadata", "/Bundles/status/nyec-submission-failed",
                "/Bundles/status/operation-outcome",
                        "/Bundle", "/Bundle/**",
                        //"/historical-replay/Bundle", "/historical-replay/Bundle/**",
                        "/flatfile/csv/Bundle", "/flatfile/csv/Bundle/**",
                        "/api/expect/fhir/**","/tenants")
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
                                            .name("X-TechBD-CIN")
                                            .description(
                                                    "Mandatory header to specify the CIN (Patient Medicaid Number) for the Patient resource. It will be used in the generated FHIR")
                                            .required(true)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-Facility-ID")
                                            .description(
                                                    "Mandatory header for MRN Facility code")
                                            .required(true)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-Encounter-Type")
                                            .description(
                                                    "Mandatory header for Encounter Type Code")
                                            .required(true)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-OrgNPI")
                                            .description(
                                                    "Optional header to specify the NPI (National Provider Identifier) for the Organization resource. It will be used in the generated FHIR. Either this or X-TechBD-OrgTIN must be provided.")
                                            .required(false)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-OrgTIN")
                                            .description(
                                                    "Optional header to specify the TIN (Tax ID Number) for the Organization resource. It will be used in the generated FHIR. Either this or X-TechBD-OrgNPI must be provided.")
                                            .required(false)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-Base-FHIR-URL")
                                            .description("Optional header to specify the base FHIR URL. If provided, it will be used in the generated FHIR; otherwise, the default value will be used.")
                                            .required(false)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-Screening-Code")
                                            .description("Optional header to specify the Screening code for the Observation grouper resource. If provided, it will be used in the generated FHIR; otherwise, default value will be used.")
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
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-Part2")
                                            .description(
                                                    "If the request header variable `X-TechBD-Part2` is set to `True`, it will be mapped to `Bundle.meta.security` with the code `ETH`.")
                                            .required(false)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-OMH")
                                            .description(
                                                    "If the request header variable `X-TechBD-OMH` is set to `True`, it will be mapped to `Bundle.meta.security` with the code `MH`.")
                                            .required(false)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-OPWDD")
                                            .description(
                                                    "If the request header variable `X-TechBD-OPWDD` is set to `True`, it will be mapped to `Bundle.meta.security` with the code `DVD`.")
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

                    // Add Mirth Endpoint 3
                    openApi.getPaths().addPathItem("/hl7v2/Bundle", new PathItem()
                            .post(new Operation()
                                    .tags(List.of("Tech by Design Hub HL7 Endpoints"))
                                    .summary("HL7 endpoint to validate and convert HL7 to JSON, then store, and forward a payload to SHIN-NY. If you want to validate a payload and not store it or forward it to SHIN-NY, use /hl7v2/Bundle/$validate.")
                                    .description("HL7 endpoint to validate and convert HL7 to JSON, then store, and forward a payload to SHIN-NY.")
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-Tenant-ID")
                                            .description("Mandatory header for Tenant ID")
                                            .required(true)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-CIN")
                                            .description(
                                                    "Mandatory header to specify the CIN (Patient Medicaid Number) for the Patient resource. It will be used in the generated FHIR")
                                            .required(true)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-Facility-ID")
                                            .description(
                                                    "Mandatory header for MRN Facility code")
                                            .required(true)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-Encounter-Type")
                                            .description(
                                                    "Mandatory header for Encounter Type Code")
                                            .required(true)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-OrgNPI")
                                            .description(
                                                    "Optional header to specify the NPI (National Provider Identifier) for the Organization resource. It will be used in the generated FHIR. Either this or X-TechBD-OrgTIN must be provided.")
                                            .required(false)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-OrgTIN")
                                            .description(
                                                    "Optional header to specify the TIN (Tax ID Number) for the Organization resource. It will be used in the generated FHIR. Either this or X-TechBD-OrgNPI must be provided.")
                                            .required(false)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-Base-FHIR-URL")
                                            .description("Optional header to specify the base FHIR URL. If provided, it will be used in the generated FHIR; otherwise, the default value will be used.")
                                            .required(false)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-Organization-Name")
                                            .description("Optional header to specify the Organization-Name. If provided, it will be used else MSH.6 value will be used.")
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
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-Part2")
                                            .description(
                                                    "If the request header variable `X-TechBD-Part2` is set to `True`, it will be mapped to `Bundle.meta.security` with the code `ETH`.")
                                            .required(false)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-OMH")
                                            .description(
                                                    "If the request header variable `X-TechBD-OMH` is set to `True`, it will be mapped to `Bundle.meta.security` with the code `MH`.")
                                            .required(false)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-OPWDD")
                                            .description(
                                                    "If the request header variable `X-TechBD-OPWDD` is set to `True`, it will be mapped to `Bundle.meta.security` with the code `DVD`.")
                                            .required(false)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                                            .description("Multipart form-data containing the HL7 XML file for validation, conversion to JSON and submission to SHIN-NY.")
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

                    // Add Mirth Endpoint 4
                    openApi.getPaths().addPathItem("/hl7v2/Bundle/$validate", new PathItem()
                            .post(new Operation()
                                    .tags(List.of("Tech by Design Hub HL7 Endpoints"))
                                    .summary("HL7 endpoint to validate and convert HL7 to JSON but not store or forward a payload to SHIN-NY. If you want to validate a payload, store it and then forward it to SHIN-NY, use /hl7v2/Bundle not /hl7v2/Bundle/$validate.")
                                    .description("HL7 endpoint to validate and convert HL7 to JSON but not store or forward a payload to SHIN-NY.")
                                    .addParametersItem(new Parameter()
                                            .name("X-TechBD-Tenant-ID")
                                            .description("Tenant ID header")
                                            .required(true)
                                            .in("header")
                                            .schema(new StringSchema()))
                                    .requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                                            .description("Multipart form-data containing the HL7 file for validation.")
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


                    // Historical replay query params for POST /Bundle (handled by the Mirth FhirBundleSubmission channel)
                    final var ooSizeParam = new Parameter()
                            .name("ooSize")
                            .in("query")
                            .required(false)
                            .description("Historical replay only (applies when Bundle.id starts with `historical-`). "
                                    + "Controls the OperationOutcome returned: `full`, `lite` is the default and returns only error/fatal issues in the OperationOutcome."
                                    + "or `none` (returns only the interactionId). Ignored for non-historical bundles.")
                            .schema(new StringSchema()._enum(List.of("full", "lite", "none"))._default("lite"));

                    final var dataLedgerParam = new Parameter()
                            .name("dataLedger")
                            .in("query")
                            .required(false)
                            .description("Historical replay only. `false` (default) skips Data Ledger submission "
                                    + "for this request. Ignored for non-historical bundles.")
                            .schema(new BooleanSchema()._default(false));

                    final var existingBundlePath = openApi.getPaths().get("/Bundle");
                    if (existingBundlePath != null && existingBundlePath.getPost() != null) {
                        existingBundlePath.getPost()
                                .addParametersItem(ooSizeParam)
                                .addParametersItem(dataLedgerParam);
                    } else {
                        openApi.getPaths().addPathItem("/Bundle", new PathItem()
                                .post(new Operation()
                                        .tags(List.of("Tech by Design Hub FHIR Endpoints"))
                                        .summary("Validate, store and forward a FHIR Bundle to SHIN-NY. Use /Bundle/$validate to validate only.")
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Tenant-ID")
                                                .description("Mandatory header for Tenant ID")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(ooSizeParam)
                                        .addParametersItem(dataLedgerParam)
                                        .responses(new ApiResponses()
                                                .addApiResponse("200", new ApiResponse().description("Successful response"))
                                                .addApiResponse("400", new ApiResponse()
                                                        .description("Bad request, e.g. `Invalid ooSize. Allowed values are: full, lite, none.`"))
                                                .addApiResponse("500", new ApiResponse().description("Server error")))));
                    }
                    
                    final var existingBundleSlashPath  = openApi.getPaths().get("/Bundle/");
                    if (existingBundleSlashPath  != null && existingBundleSlashPath .getPost() != null) {
                        existingBundleSlashPath .getPost()
                                .addParametersItem(ooSizeParam)
                                .addParametersItem(dataLedgerParam);
                    } else {
                        openApi.getPaths().addPathItem("/Bundle/", new PathItem()
                                .post(new Operation()
                                        .tags(List.of("Tech by Design Hub FHIR Endpoints"))
                                        .summary("Validate, store and forward a FHIR Bundle to SHIN-NY. Use /Bundle/$validate to validate only.")
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Tenant-ID")
                                                .description("Mandatory header for Tenant ID")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(ooSizeParam)
                                        .addParametersItem(dataLedgerParam)
                                        .responses(new ApiResponses()
                                                .addApiResponse("200", new ApiResponse().description("Successful response"))
                                                .addApiResponse("400", new ApiResponse()
                                                        .description("Bad request, e.g. `Invalid ooSize. Allowed values are: full, lite, none.`"))
                                                .addApiResponse("500", new ApiResponse().description("Server error")))));
                   
                                
                   
                       // Add Flat File CSV Validation Endpoint
                        openApi.getPaths().addPathItem("/flatfile/csv/Bundle/$validate", new PathItem()
                                .post(new Operation()
                                        .tags(List.of("Tech by Design Hub CSV Endpoints"))
                                        .summary("CSV endpoint to validate a flat file CSV Bundle without storing or forwarding the payload.")
                                        .description("CSV endpoint to validate a flat file CSV Bundle. The uploaded CSV file is validated without storing or forwarding the payload.")
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Tenant-ID")
                                                .description("Mandatory header for Tenant ID")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("immediate")
                                                .description("Specifies whether the validation should be performed immediately.")
                                                .required(false)
                                                .in("query")
                                                .schema(new StringSchema()._default("true")))
                                        .requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                                                .description("Multipart form-data containing the CSV file for validation.")
                                                .required(true)
                                                .content(new io.swagger.v3.oas.models.media.Content()
                                                        .addMediaType("multipart/form-data",
                                                                new io.swagger.v3.oas.models.media.MediaType()
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


                        openApi.getPaths().addPathItem("/flatfile/csv/Bundle/$validate/", new PathItem()
                                .post(new Operation()
                                        .tags(List.of("Tech by Design Hub CSV Endpoints"))
                                        .summary("CSV endpoint to validate a flat file CSV Bundle without storing or forwarding the payload.")
                                        .description("CSV endpoint to validate a flat file CSV Bundle. The uploaded CSV file is validated without storing or forwarding the payload.")
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Tenant-ID")
                                                .description("Mandatory header for Tenant ID")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("immediate")
                                                .description("Specifies whether the validation should be performed immediately.")
                                                .required(false)
                                                .in("query")
                                                .schema(new StringSchema()._default("true")))
                                        .requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                                                .description("Multipart form-data containing the CSV file for validation.")
                                                .required(true)
                                                .content(new io.swagger.v3.oas.models.media.Content()
                                                        .addMediaType("multipart/form-data",
                                                                new io.swagger.v3.oas.models.media.MediaType()
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


                        // Add Flat File CSV Bundle Endpoint
                        openApi.getPaths().addPathItem("/flatfile/csv/Bundle", new PathItem()
                                .post(new Operation()
                                        .tags(List.of("Tech by Design Hub CSV Endpoints"))
                                        .summary("Submit a flat file CSV or ZIP file for validation and processing.")
                                        .description("Accepts a CSV or ZIP file, validates the contents, and processes the submission for the specified tenant.")
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Tenant-ID")
                                                .description("Mandatory header for Tenant ID.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("immediate")
                                                .description("Specifies whether processing should be performed immediately.")
                                                .required(false)
                                                .in("query")
                                                .schema(new io.swagger.v3.oas.models.media.BooleanSchema()._default(true)))
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-DataLake-API-URL")
                                                .description("Optional Data Lake API URL.")
                                                .required(false)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Base-FHIR-URL")
                                                .description("Optional base FHIR API URL.")
                                                .required(false)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Validation-Severity-Level")
                                                .description("Optional validation severity level: information, warning, error, or fatal.")
                                                .required(false)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                                                .description("Multipart form-data containing a CSV file or ZIP archive for processing.")
                                                .required(true)
                                                .content(new io.swagger.v3.oas.models.media.Content()
                                                        .addMediaType("multipart/form-data",
                                                                new io.swagger.v3.oas.models.media.MediaType()
                                                                        .schema(new io.swagger.v3.oas.models.media.Schema<>()
                                                                                .$ref("#/components/schemas/FileUpload")))))
                                        .responses(new ApiResponses()
                                                .addApiResponse("200",
                                                        new ApiResponse()
                                                                .description("Submission processed successfully."))
                                                .addApiResponse("400",
                                                        new ApiResponse()
                                                                .description("Bad request or validation failure."))
                                                .addApiResponse("500",
                                                        new ApiResponse()
                                                                .description("Server error.")))));

                                                                


                        // Add Flat File CSV Bundle Endpoint
                        openApi.getPaths().addPathItem("/flatfile/csv/Bundle/", new PathItem()
                                .post(new Operation()
                                        .tags(List.of("Tech by Design Hub CSV Endpoints"))
                                        .summary("Submit a flat file CSV or ZIP file for validation and processing.")
                                        .description("Accepts a CSV or ZIP file, validates the contents, and processes the submission for the specified tenant.")
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Tenant-ID")
                                                .description("Mandatory header for Tenant ID.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("immediate")
                                                .description("Specifies whether processing should be performed immediately.")
                                                .required(false)
                                                .in("query")
                                                .schema(new io.swagger.v3.oas.models.media.BooleanSchema()._default(true)))
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-DataLake-API-URL")
                                                .description("Optional Data Lake API URL.")
                                                .required(false)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Base-FHIR-URL")
                                                .description("Optional base FHIR API URL.")
                                                .required(false)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Validation-Severity-Level")
                                                .description("Optional validation severity level: information, warning, error, or fatal.")
                                                .required(false)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                                                .description("Multipart form-data containing a CSV file or ZIP archive for processing.")
                                                .required(true)
                                                .content(new io.swagger.v3.oas.models.media.Content()
                                                        .addMediaType("multipart/form-data",
                                                                new io.swagger.v3.oas.models.media.MediaType()
                                                                        .schema(new io.swagger.v3.oas.models.media.Schema<>()
                                                                                .$ref("#/components/schemas/FileUpload")))))
                                        .responses(new ApiResponses()
                                                .addApiResponse("200",
                                                        new ApiResponse()
                                                                .description("Submission processed successfully."))
                                                .addApiResponse("400",
                                                        new ApiResponse()
                                                                .description("Bad request or validation failure."))
                                                .addApiResponse("500",
                                                        new ApiResponse()
                                                                .description("Server error.")))));



                        // Add Bundle Replay Endpoint
                        openApi.getPaths().addPathItem("/Bundle/replay/", new PathItem()
                                .post(new Operation()
                                        .tags(List.of("Tech by Design Hub FHIR Endpoints"))
                                        .summary("Replay FHIR Bundles between a date or datetime range.")
                                        .description("Accepts startDate and endDate.")
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-StartDate")
                                                .description("Start date for replay in DD-MM-YYYY format.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-EndDate")
                                                .description("End date for replay in DD-MM-YYYY format.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Tenant-ID")
                                                .description("Mandatory header for Tenant ID.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                                                .description("Empty request body for the replay operation.")
                                                .required(false)
                                                .content(new io.swagger.v3.oas.models.media.Content()
                                                        .addMediaType("text/plain",
                                                                new io.swagger.v3.oas.models.media.MediaType()
                                                                        .schema(new StringSchema()))))
                                        .responses(new ApiResponses()
                                                .addApiResponse("200",
                                                        new ApiResponse()
                                                                .description("Replay request processed successfully."))
                                                .addApiResponse("400",
                                                        new ApiResponse()
                                                                .description("Bad request."))
                                                .addApiResponse("500",
                                                        new ApiResponse()
                                                                .description("Server error.")))));

                                                                
                        // Add Bundle Replay Endpoint
                        openApi.getPaths().addPathItem("/Bundle/replay", new PathItem()
                                .post(new Operation()
                                        .tags(List.of("Tech by Design Hub FHIR Endpoints"))
                                        .summary("Replay FHIR Bundles between a date or datetime range.")
                                        .description("Accepts startDate and endDate")
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-StartDate")
                                                .description("Start date for replay in DD-MM-YYYY format.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-EndDate")
                                                .description("End date for replay in DD-MM-YYYY format.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Tenant-ID")
                                                .description("Mandatory header for Tenant ID.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                                                .description("Empty request body for the replay operation.")
                                                .required(false)
                                                .content(new io.swagger.v3.oas.models.media.Content()
                                                        .addMediaType("text/plain",
                                                                new io.swagger.v3.oas.models.media.MediaType()
                                                                        .schema(new StringSchema()))))
                                        .responses(new ApiResponses()
                                                .addApiResponse("200",
                                                        new ApiResponse()
                                                                .description("Replay request processed successfully."))
                                                .addApiResponse("400",
                                                        new ApiResponse()
                                                                .description("Bad request."))
                                                .addApiResponse("500",
                                                        new ApiResponse()
                                                                .description("Server error.")))));






                        // Add FHIR Bundle Validation Endpoint
                        openApi.getPaths().addPathItem("/Bundle/$validate", new PathItem()
                                .post(new Operation()
                                        .tags(List.of("Tech by Design Hub FHIR Endpoints"))
                                        .summary("Endpoint to validate but not store or forward a payload to SHIN-NY. If you want to validate a payload, store it and then forward it to SHIN-NY, use /Bundle not /Bundle/$validate.")
                                        .description("Endpoint to validate but not store or forward a payload to SHIN-NY.")
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Tenant-ID")
                                                .description("Mandatory header for Tenant ID.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("X-SHIN-NY-IG-Version")
                                                .description("SHIN-NY Implementation Guide version used for validation.")
                                                .required(false)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                                                .description("JSON payload to validate.")
                                                .required(true)
                                                .content(new io.swagger.v3.oas.models.media.Content()
                                                        .addMediaType("application/json",
                                                                new io.swagger.v3.oas.models.media.MediaType()
                                                                        .schema(new StringSchema()))))
                                        .responses(new ApiResponses()
                                                .addApiResponse("200",
                                                        new ApiResponse()
                                                                .description("Validation response returned successfully."))
                                                .addApiResponse("400",
                                                        new ApiResponse()
                                                                .description("Bad request or validation failure."))
                                                .addApiResponse("500",
                                                        new ApiResponse()
                                                                .description("Server error.")))));






                                        
 // Add FHIR Bundle Validation Endpoint
                        openApi.getPaths().addPathItem("/Bundle/$validate/", new PathItem()
                                .post(new Operation()
                                        .tags(List.of("Tech by Design Hub FHIR Endpoints"))
                                        .summary("Endpoint to validate but not store or forward a payload to SHIN-NY. If you want to validate a payload, store it and then forward it to SHIN-NY, use /Bundle not /Bundle/$validate.")
                                        .description("Endpoint to validate but not store or forward a payload to SHIN-NY.")
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Tenant-ID")
                                                .description("Mandatory header for Tenant ID.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("X-SHIN-NY-IG-Version")
                                                .description("SHIN-NY Implementation Guide version used for validation.")
                                                .required(false)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                                                .description("JSON payload to validate.")
                                                .required(true)
                                                .content(new io.swagger.v3.oas.models.media.Content()
                                                        .addMediaType("application/json",
                                                                new io.swagger.v3.oas.models.media.MediaType()
                                                                        .schema(new StringSchema()))))
                                        .responses(new ApiResponses()
                                                .addApiResponse("200",
                                                        new ApiResponse()
                                                                .description("Validation response returned successfully."))
                                                .addApiResponse("400",
                                                        new ApiResponse()
                                                                .description("Bad request or validation failure."))
                                                .addApiResponse("500",
                                                        new ApiResponse()
                                                                .description("Server error.")))));

                                                                
                        // Add FHIR Metadata Endpoint
                        openApi.getPaths().addPathItem("/metadata", new PathItem()
                                .get(new Operation()
                                        .tags(List.of("Tech by Design Hub FHIR Endpoints"))
                                        .summary("FHIR server's conformance statement")
                                        .description("Returns the FHIR server's capability statement in XML format.")
                                        .responses(new ApiResponses()
                                                .addApiResponse("200",
                                                        new ApiResponse()
                                                                .description("FHIR server metadata returned successfully.")
                                                                .content(new io.swagger.v3.oas.models.media.Content()
                                                                        .addMediaType("application/xml",
                                                                                new io.swagger.v3.oas.models.media.MediaType()
                                                                                        .schema(new StringSchema()))))
                                                .addApiResponse("500",
                                                        new ApiResponse()
                                                                .description("Server error.")))));


                                                                
                        // Add Bundle Operation Outcome Status Endpoint
                        openApi.getPaths().addPathItem("/Bundles/status/operation-outcome", new PathItem()
                                .get(new Operation()
                                        .tags(List.of("Tech by Design Hub FHIR Endpoints"))
                                        .summary("Retrieve OperationOutcome(s) for a Bundle or Interaction")
                                        .description("Endpoint to fetch OperationOutcome resources for a given Bundle ID or Interaction ID. At least one of X-TechBD-Bundle-ID or X-TechBD-Interaction-ID must be provided.")
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Tenant-ID")
                                                .description("Mandatory header for Tenant ID.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Bundle-ID")
                                                .description("Mandatory header for FHIR Bundle ID.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Interaction-ID")
                                                .description("Mandatory header for Interaction ID.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .responses(new ApiResponses()
                                                .addApiResponse("200",
                                                        new ApiResponse()
                                                                .description("Operation outcome status retrieved successfully."))
                                                .addApiResponse("400",
                                                        new ApiResponse()
                                                                .description("Bad request."))
                                                .addApiResponse("404",
                                                        new ApiResponse()
                                                                .description("Bundle or interaction not found."))
                                                .addApiResponse("500",
                                                        new ApiResponse()
                                                                .description("Server error.")))));






                        // Add NYeC Submission Failed Status Endpoint
                        openApi.getPaths().addPathItem("/Bundles/status/nyec-submission-failed", new PathItem()
                                .get(new Operation()
                                        .tags(List.of("Tech by Design Hub FHIR Endpoints"))
                                        .summary("Retrieve FHIR Bundles that failed NYEC submission")
                                        .description("Fetches bundles that failed NYEC submission within the specified date/datetime range. Optionally filter by tenant ID.")
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-StartDate")
                                                .description("Mandatory start date in DD-MM-YYYY format.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-EndDate")
                                                .description("Mandatory end date in DD-MM-YYYY format.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-Tenant-ID")
                                                .description("Mandatory header for Tenant ID.")
                                                .required(true)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .addParametersItem(new Parameter()
                                                .name("X-TechBD-IncludeDetails")
                                                .description("Optional header to control whether additional submission details are included.")
                                                .required(false)
                                                .in("header")
                                                .schema(new StringSchema()))
                                        .responses(new ApiResponses()
                                                .addApiResponse("200",
                                                        new ApiResponse()
                                                                .description("Failed NYeC submission records retrieved successfully."))
                                                .addApiResponse("400",
                                                        new ApiResponse()
                                                                .description("Bad request or invalid date range."))
                                                .addApiResponse("500",
                                                        new ApiResponse()
                                                                .description("Server error.")))));




                        // Add Bundle Status Endpoint
                        openApi.getPaths().addPathItem("/Bundle/$status/{id}", new PathItem()
                                .get(new Operation()
                                        .tags(List.of("Tech by Design Hub FHIR Endpoints"))
                                        .summary("Check the state/status of async operation")
                                        .description("Retrieves the processing status of a FHIR Bundle using its identifier.")
                                        .addParametersItem(new Parameter()
                                                .name("id")
                                                .description("FHIR Bundle identifier.")
                                                .required(true)
                                                .in("path")
                                                .schema(new StringSchema()))
                                        .responses(new ApiResponses()
                                                .addApiResponse("200",
                                                        new ApiResponse()
                                                                .description("Bundle status retrieved successfully.")
                                                                .content(new io.swagger.v3.oas.models.media.Content()
                                                                        .addMediaType("application/json",
                                                                                new io.swagger.v3.oas.models.media.MediaType()
                                                                                        .schema(new StringSchema()))))
                                                .addApiResponse("400",
                                                        new ApiResponse()
                                                                .description("Bad request."))
                                                .addApiResponse("404",
                                                        new ApiResponse()
                                                                .description("Bundle not found."))
                                                .addApiResponse("500",
                                                        new ApiResponse()
                                                                .description("Server error.")))));



                        openApi.getPaths().addPathItem("/tenants", new PathItem()
                                        .get(new Operation()
                                                .tags(List.of("Tech by Design Hub Tenant Endpoints"))
                                                .summary("List active tenants")
                                                .description("Returns the list of tenants available in the system.")
                                                .responses(new ApiResponses()
                                                        .addApiResponse("200", new ApiResponse()
                                                                .description("Tenants retrieved successfully.")
                                                                .content(new Content()
                                                                        .addMediaType("application/json",
                                                                                new MediaType()
                                                                                        .schema(new io.swagger.v3.oas.models.media.ArraySchema()
                                                                                                .items(new io.swagger.v3.oas.models.media.Schema<>()
                                                                                                        .type("object"))))))
                                                        .addApiResponse("500", new ApiResponse()
                                                                .description("Internal server error.")))));                          
                   
                  }
                })
                .build();
    }
}
