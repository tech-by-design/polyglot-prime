package org.techbd.csv.service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;

/**
 * Optimized client for interacting with the FHIR validation service.
 * Handles bundle validation with comprehensive header support and error handling.
 * 
 * Key improvements:
 * - Configurable buffer size to handle large responses
 * - Proper timeout configuration
 * - More efficient parameter handling
 * - Better error messages
 */
@Service
public class FhirValidationServiceClient {

    private static final Logger LOG = LoggerFactory.getLogger(FhirValidationServiceClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    // Configuration properties with defaults
    @Value("${FHIR_CLIENT_MAX_BUFFER_SIZE:10485760}") // 10MB default
    private final int maxBufferSize;
    
    @Value("${FHIR_CLIENT_CONNECT_TIMEOUT_MS:5000}") // 5 seconds default
    private final int connectTimeoutMs;
    
    @Value("${FHIR_CLIENT_READ_TIMEOUT_SECONDS:60}") // 60 seconds default
    private final int readTimeoutSeconds;
    
    @Value("${FHIR_CLIENT_WRITE_TIMEOUT_SECONDS:60}") // 60 seconds default
    private final int writeTimeoutSeconds;
    
    @Value("${FHIR_CLIENT_BLOCK_TIMEOUT_SECONDS:90}") // 90 seconds default
    private final int blockTimeout;

    public FhirValidationServiceClient(
            @Value("${TECHBD_BL_BASEURL}") String baseUrl,
            @Value("${FHIR_CLIENT_MAX_BUFFER_SIZE:10485760}") int maxBufferSize,
            @Value("${FHIR_CLIENT_CONNECT_TIMEOUT_MS:5000}") int connectTimeoutMs,
            @Value("${FHIR_CLIENT_READ_TIMEOUT_SECONDS:60}") int readTimeoutSeconds,
            @Value("${FHIR_CLIENT_WRITE_TIMEOUT_SECONDS:60}") int writeTimeoutSeconds,
            @Value("${FHIR_CLIENT_BLOCK_TIMEOUT_SECONDS:90}") int blockTimeoutSeconds) {
        
        this.maxBufferSize = maxBufferSize;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.writeTimeoutSeconds = writeTimeoutSeconds;
        this.blockTimeout = blockTimeoutSeconds;
        
        this.webClient = createConfiguredWebClient(baseUrl);
        this.objectMapper = new ObjectMapper();
        
        LOG.info("FhirValidationServiceClient initialized - baseUrl: {}, maxBufferSize: {}MB, " +
                 "connectTimeout: {}ms, readTimeout: {}s, writeTimeout: {}s, blockTimeout: {}s", 
                 baseUrl, maxBufferSize / (1024 * 1024), connectTimeoutMs, 
                 readTimeoutSeconds, writeTimeoutSeconds, blockTimeoutSeconds);
    }

    /**
     * Creates a WebClient with optimized configuration for handling large responses
     */
    private WebClient createConfiguredWebClient(String baseUrl) {
        // Configure HTTP client with timeouts
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(writeTimeoutSeconds, TimeUnit.SECONDS)));

        // Configure exchange strategies with larger buffer
        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(maxBufferSize))
            .build();

        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(strategies)
            .build();
    }

    /**
     * Simplified validation method for backward compatibility
     * Extracts parameters from the map and applies them to the validation request
     */
    public Object validateBundle(String bundle, String interactionId, String tenantId,
                                Map<String, Object> requestParameters) {
        ValidationRequest.Builder builder = ValidationRequest.builder()
            .bundle(bundle)
            .interactionId(interactionId)
            .tenantId(tenantId);

        applyParametersFromMap(builder, requestParameters);
        return validateBundle(builder.build());
    }

    /**
     * Efficiently applies parameters from map to builder
     */
    private void applyParametersFromMap(ValidationRequest.Builder builder, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return;
        }

        getStringParam(params, "groupInteractionId").ifPresent(builder::groupInteractionId);
        getStringParam(params, "masterInteractionId").ifPresent(builder::masterInteractionId);
        getStringParam(params, "sourceType").ifPresent(builder::sourceType);
        getStringParam(params, "sessionId").ifPresent(builder::sessionId);
        getStringParam(params, "userId").ifPresent(builder::userId);
        getStringParam(params, "userName").ifPresent(builder::userName);
        getStringParam(params, "userRole").ifPresent(builder::userRole);
        getStringParam(params, "correlationId").ifPresent(builder::correlationId);
        getStringParam(params, "requestUri").ifPresent(builder::requestUri);
        getStringParam(params, "overrideRequestUri").ifPresent(builder::overrideRequestUri);
        getStringParam(params, "provenance").ifPresent(builder::provenance);
        getStringParam(params, "healthCheck").ifPresent(builder::healthCheck);
        getStringParam(params, "X-TechBD-DataLake-API-URL").ifPresent(builder::customDataLakeApi);
        getStringParam(params, "dataLakeApiContentType").ifPresent(builder::dataLakeApiContentType);
        getStringParam(params, "mtlsStrategy").ifPresent(builder::mtlsStrategy);
        getStringParam(params, "elaboration").ifPresent(builder::elaboration);
        getStringParam(params, "shinNyIgVersion").ifPresent(builder::shinNyIgVersion);
        getStringParam(params, "validationSeverityLevel").ifPresent(builder::validationSeverityLevel);
    }

    /**
     * Safely extracts string parameter with type checking
     */
    private Optional<String> getStringParam(Map<String, Object> params, String key) {
        return Optional.ofNullable(params.get(key))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(s -> !s.trim().isEmpty());
    }

    /**
     * Validation method with extended parameters
     */
    public Object validateBundle(String bundle, String interactionId, String tenantId,
                                String groupInteractionId, String masterInteractionId, String sourceType,
                                String sessionId, String userId) {
        return validateBundle(ValidationRequest.builder()
            .bundle(bundle)
            .interactionId(interactionId)
            .tenantId(tenantId)
            .groupInteractionId(groupInteractionId)
            .masterInteractionId(masterInteractionId)
            .sourceType(sourceType)
            .sessionId(sessionId)
            .userId(userId)
            .build());
    }

    /**
     * Primary validation method using builder pattern
     */
    public Object validateBundle(ValidationRequest request) {
        LOG.info("Calling FHIR validation service - interactionId: {}, tenantId: {}", 
                 request.interactionId, request.tenantId);

        validateRequest(request);

        try {
            String response = webClient.post()
                .uri("/Bundle/")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> addHeaders(headers, request))
                .body(BodyInserters.fromValue(request.bundle))
                .retrieve()
                .onStatus(status -> status.isError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("Unknown error from FHIR validation service")
                        .flatMap(errorBody -> {
                            LOG.error("FHIR validation service error - status: {}, interactionId: {}, error: {}", 
                                     clientResponse.statusCode(), request.interactionId, errorBody);
                            return reactor.core.publisher.Mono.error(
                                new FhirValidationException(
                                    String.format("FHIR validation failed with status %s: %s", 
                                                clientResponse.statusCode(), errorBody),
                                    request.interactionId));
                        }))
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(blockTimeout))
                .block(Duration.ofSeconds(blockTimeout + 30));

            LOG.info("Successfully received response from FHIR validation service - interactionId: {}", 
                     request.interactionId);

            return objectMapper.readValue(response, Object.class);

        } catch (WebClientResponseException e) {
            String errorMsg = String.format(
                "FHIR validation WebClient error - status: %s, interactionId: %s, response: %s",
                e.getStatusCode(), request.interactionId, e.getResponseBodyAsString());
            LOG.error(errorMsg, e);
            throw new FhirValidationException(errorMsg, request.interactionId, e);
            
        } catch (Exception e) {
            String errorMsg = String.format(
                "Unexpected FHIR validation error - interactionId: %s, message: %s",
                request.interactionId, e.getMessage());
            LOG.error(errorMsg, e);
            throw new FhirValidationException(errorMsg, request.interactionId, e);
        }
    }

    private void validateRequest(ValidationRequest request) {
        if (request.bundle == null || request.bundle.trim().isEmpty()) {
            throw new IllegalArgumentException("Bundle payload cannot be null or empty");
        }
        if (request.interactionId == null || request.interactionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Interaction ID cannot be null or empty");
        }
        if (request.tenantId == null || request.tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID cannot be null or empty");
        }
    }

    /**
     * Adds headers to the request - more efficient implementation
     */
    private void addHeaders(org.springframework.http.HttpHeaders headers, ValidationRequest request) {
        // Required headers
        headers.add("X-TechBD-Tenant-ID", request.tenantId);
        headers.add("X-TechBD-Interaction-ID", request.interactionId);

        // Optional headers - only add if not null/empty
        addOptionalHeader(headers, "X-TechBD-Group-Interaction-ID", request.groupInteractionId);
        addOptionalHeader(headers, "X-TechBD-Master-Interaction-ID", request.masterInteractionId);
        addOptionalHeader(headers, "X-TechBD-Source-Type", request.sourceType);
        addOptionalHeader(headers, "X-TechBD-Session-ID", request.sessionId);
        addOptionalHeader(headers, "X-TechBD-User-ID", request.userId);
        addOptionalHeader(headers, "X-TechBD-User-Name", request.userName);
        addOptionalHeader(headers, "X-TechBD-User-Role", request.userRole);
        addOptionalHeader(headers, "X-TechBD-Correlation-ID", request.correlationId);
        addOptionalHeader(headers, "X-TechBD-Request-URI", request.requestUri);
        addOptionalHeader(headers, "X-TechBD-Override-Request-URI", request.overrideRequestUri);
        addOptionalHeader(headers, "X-TechBD-Provenance", request.provenance);
        addOptionalHeader(headers, "X-TechBD-Health-Check", request.healthCheck);
        addOptionalHeader(headers, "X-TechBD-Custom-DataLake-API", request.customDataLakeApi);
        addOptionalHeader(headers, "X-TechBD-DataLake-API-URL", request.customDataLakeApi);
        addOptionalHeader(headers, "X-TechBD-DataLake-API-Content-Type", request.dataLakeApiContentType);
        addOptionalHeader(headers, "X-TechBD-MTLS-Strategy", request.mtlsStrategy);
        addOptionalHeader(headers, "X-TechBD-Elaboration", request.elaboration);
        addOptionalHeader(headers, "X-TechBD-SHIN-NY-IG-Version", request.shinNyIgVersion);
        addOptionalHeader(headers, "X-TechBD-Validation-Severity-Level", request.validationSeverityLevel);
        
        LOG.debug("Added {} headers to FHIR validation request", headers.size());
    }

    private void addOptionalHeader(org.springframework.http.HttpHeaders headers, String name, String value) {
        if (value != null && !value.trim().isEmpty()) {
            headers.add(name, value);
        }
    }

    /**
     * Custom exception for FHIR validation failures
     */
    public static class FhirValidationException extends RuntimeException {
        private final String interactionId;

        public FhirValidationException(String message, String interactionId) {
            super(message);
            this.interactionId = interactionId;
        }

        public FhirValidationException(String message, String interactionId, Throwable cause) {
            super(message, cause);
            this.interactionId = interactionId;
        }

        public String getInteractionId() {
            return interactionId;
        }
    }

    /**
     * Builder-based request object for validation
     */
    public static class ValidationRequest {
        // Required fields
        private final String bundle;
        private final String interactionId;
        private final String tenantId;

        // Optional fields
        private final String groupInteractionId;
        private final String masterInteractionId;
        private final String sourceType;
        private final String sessionId;
        private final String userId;
        private final String userName;
        private final String userRole;
        private final String correlationId;
        private final String requestUri;
        private final String overrideRequestUri;
        private final String provenance;
        private final String healthCheck;
        private final String customDataLakeApi;
        private final String dataLakeApiContentType;
        private final String mtlsStrategy;
        private final String elaboration;
        private final String shinNyIgVersion;
        private final String validationSeverityLevel;

        private ValidationRequest(Builder builder) {
            this.bundle = builder.bundle;
            this.interactionId = builder.interactionId;
            this.tenantId = builder.tenantId;
            this.groupInteractionId = builder.groupInteractionId;
            this.masterInteractionId = builder.masterInteractionId;
            this.sourceType = builder.sourceType;
            this.sessionId = builder.sessionId;
            this.userId = builder.userId;
            this.userName = builder.userName;
            this.userRole = builder.userRole;
            this.correlationId = builder.correlationId;
            this.requestUri = builder.requestUri;
            this.overrideRequestUri = builder.overrideRequestUri;
            this.provenance = builder.provenance;
            this.healthCheck = builder.healthCheck;
            this.customDataLakeApi = builder.customDataLakeApi;
            this.dataLakeApiContentType = builder.dataLakeApiContentType;
            this.mtlsStrategy = builder.mtlsStrategy;
            this.elaboration = builder.elaboration;
            this.shinNyIgVersion = builder.shinNyIgVersion;
            this.validationSeverityLevel = builder.validationSeverityLevel;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String bundle;
            private String interactionId;
            private String tenantId;
            private String groupInteractionId;
            private String masterInteractionId;
            private String sourceType;
            private String sessionId;
            private String userId;
            private String userName;
            private String userRole;
            private String correlationId;
            private String requestUri;
            private String overrideRequestUri;
            private String provenance;
            private String healthCheck;
            private String customDataLakeApi;
            private String dataLakeApiContentType;
            private String mtlsStrategy;
            private String elaboration;
            private String shinNyIgVersion;
            private String validationSeverityLevel;

            public Builder bundle(String bundle) {
                this.bundle = bundle;
                return this;
            }

            public Builder interactionId(String interactionId) {
                this.interactionId = interactionId;
                return this;
            }

            public Builder tenantId(String tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public Builder groupInteractionId(String groupInteractionId) {
                this.groupInteractionId = groupInteractionId;
                return this;
            }

            public Builder masterInteractionId(String masterInteractionId) {
                this.masterInteractionId = masterInteractionId;
                return this;
            }

            public Builder sourceType(String sourceType) {
                this.sourceType = sourceType;
                return this;
            }

            public Builder sessionId(String sessionId) {
                this.sessionId = sessionId;
                return this;
            }

            public Builder userId(String userId) {
                this.userId = userId;
                return this;
            }

            public Builder userName(String userName) {
                this.userName = userName;
                return this;
            }

            public Builder userRole(String userRole) {
                this.userRole = userRole;
                return this;
            }

            public Builder correlationId(String correlationId) {
                this.correlationId = correlationId;
                return this;
            }

            public Builder requestUri(String requestUri) {
                this.requestUri = requestUri;
                return this;
            }

            public Builder overrideRequestUri(String overrideRequestUri) {
                this.overrideRequestUri = overrideRequestUri;
                return this;
            }

            public Builder provenance(String provenance) {
                this.provenance = provenance;
                return this;
            }

            public Builder healthCheck(String healthCheck) {
                this.healthCheck = healthCheck;
                return this;
            }

            public Builder customDataLakeApi(String customDataLakeApi) {
                this.customDataLakeApi = customDataLakeApi;
                return this;
            }

            public Builder dataLakeApiContentType(String dataLakeApiContentType) {
                this.dataLakeApiContentType = dataLakeApiContentType;
                return this;
            }

            public Builder mtlsStrategy(String mtlsStrategy) {
                this.mtlsStrategy = mtlsStrategy;
                return this;
            }

            public Builder elaboration(String elaboration) {
                this.elaboration = elaboration;
                return this;
            }

            public Builder shinNyIgVersion(String shinNyIgVersion) {
                this.shinNyIgVersion = shinNyIgVersion;
                return this;
            }

            public Builder validationSeverityLevel(String validationSeverityLevel) {
                this.validationSeverityLevel = validationSeverityLevel;
                return this;
            }

            public ValidationRequest build() {
                return new ValidationRequest(this);
            }
        }
    }
}