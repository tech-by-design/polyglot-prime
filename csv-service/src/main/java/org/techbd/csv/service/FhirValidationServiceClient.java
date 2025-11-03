
package org.techbd.csv.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.common.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Client for interacting with the FHIR validation service.
 * Handles bundle validation with comprehensive header support and error
 * handling.
 */
@Service
public class FhirValidationServiceClient {

    private static final Logger LOG = LoggerFactory.getLogger(FhirValidationServiceClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public FhirValidationServiceClient(@Value("${TECHBD_BL_BASEURL}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = new ObjectMapper();
        LOG.info("FhirValidationServiceClient initialized with baseUrl: {}", baseUrl);
    }

    /**
     * Simple validation method for backward compatibility
     * Extracts parameters from the map and applies them to the validation request
     * 
     * @param bundle            The FHIR bundle JSON string
     * @param interactionId     The interaction ID
     * @param tenantId          The tenant ID
     * @param requestParameters Map containing all headers and parameters
     */
    public Object validateBundle(String bundle, String interactionId, String tenantId,
            Map<String, Object> requestParameters) {
        ValidationRequest.Builder builder = ValidationRequest.builder()
                .bundle(bundle)
                .interactionId(interactionId)
                .tenantId(tenantId);

        // Extract and apply parameters from the map if present
        if (requestParameters != null && !requestParameters.isEmpty()) {
            if (requestParameters.containsKey("groupInteractionId")) {
                builder.groupInteractionId((String) requestParameters.get("groupInteractionId"));
            }
            if (requestParameters.containsKey("masterInteractionId")) {
                builder.masterInteractionId((String) requestParameters.get("masterInteractionId"));
            }
            if (requestParameters.containsKey("sourceType")) {
                builder.sourceType((String) requestParameters.get("sourceType"));
            }
            if (requestParameters.containsKey("sessionId")) {
                builder.sessionId((String) requestParameters.get("sessionId"));
            }
            if (requestParameters.containsKey("userId")) {
                builder.userId((String) requestParameters.get("userId"));
            }
            if (requestParameters.containsKey("userName")) {
                builder.userName((String) requestParameters.get("userName"));
            }
            if (requestParameters.containsKey("userRole")) {
                builder.userRole((String) requestParameters.get("userRole"));
            }
            if (requestParameters.containsKey("correlationId")) {
                builder.correlationId((String) requestParameters.get("correlationId"));
            }
            if (requestParameters.containsKey("requestUri")) {
                builder.requestUri((String) requestParameters.get("requestUri"));
            }
            if (requestParameters.containsKey("overrideRequestUri")) {
                builder.overrideRequestUri((String) requestParameters.get("overrideRequestUri"));
            }
            if (requestParameters.containsKey("provenance")) {
                builder.provenance((String) requestParameters.get("provenance"));
            }
            if (requestParameters.containsKey("healthCheck")) {
                builder.healthCheck((String) requestParameters.get("healthCheck"));
            }
            if (requestParameters.containsKey("customDataLakeApi")) {
                builder.customDataLakeApi((String) requestParameters.get("customDataLakeApi"));
            }
            if (requestParameters.containsKey("dataLakeApiContentType")) {
                builder.dataLakeApiContentType((String) requestParameters.get("dataLakeApiContentType"));
            }
            if (requestParameters.containsKey("mtlsStrategy")) {
                builder.mtlsStrategy((String) requestParameters.get("mtlsStrategy"));
            }
            if (requestParameters.containsKey("elaboration")) {
                builder.elaboration((String) requestParameters.get("elaboration"));
            }
            if (requestParameters.containsKey("shinNyIgVersion")) {
                builder.shinNyIgVersion((String) requestParameters.get("shinNyIgVersion"));
            }
            if (requestParameters.containsKey("validationSeverityLevel")) {
                builder.validationSeverityLevel((String) requestParameters.get("validationSeverityLevel"));
            }
        }

        return validateBundle(builder.build());
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
        LOG.info("Calling FHIR validation service for bundle validation - interaction Id: {}",
                request.interactionId);

        validateRequest(request);

        try {
            // to-do : needs to change to /Bundle in future
            WebClient.RequestBodySpec requestBuilder = webClient.post()
                    .uri("/Bundle/")
                    .contentType(MediaType.APPLICATION_JSON);

            // Add all headers from request
            addHeaders(requestBuilder, request);

            // Log outgoing request details
            logRequest(request);

            String response = requestBuilder
                    .body(BodyInserters.fromValue(request.bundle))
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("Unknown error from FHIR validation service")
                                    .flatMap(errorBody -> {
                                        LOG.error(
                                                "Error response from FHIR validation service (status={}): {} - interaction Id: {}",
                                                clientResponse.statusCode(), errorBody, request.interactionId);
                                        return Mono.error(
                                                new RuntimeException("FHIR validation service error: " + errorBody));
                                    }))
                    .bodyToMono(String.class)
                    .block();

            LOG.info("Successfully received response from FHIR validation service - interaction Id: {}",
                    request.interactionId);

            return objectMapper.readValue(response, Object.class);

        } catch (WebClientResponseException e) {
            LOG.error("WebClient error while calling FHIR validation service: status={} body={} - interaction Id: {}",
                    e.getStatusCode(), e.getResponseBodyAsString(), request.interactionId);
            throw new RuntimeException("FHIR validation service error: " + e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("Unexpected error while calling FHIR validation service: {} - interaction Id: {}",
                    e.getMessage(), request.interactionId, e);
            throw new RuntimeException("FHIR validation service error: " + e.getMessage(), e);
        }
    }

    private void validateRequest(ValidationRequest request) {
        if (StringUtils.isEmpty(request.bundle)) {
            throw new IllegalArgumentException("Bundle payload cannot be null or empty");
        }
        if (StringUtils.isEmpty(request.interactionId)) {
            throw new IllegalArgumentException("Interaction ID cannot be null or empty");
        }
        if (StringUtils.isEmpty(request.tenantId)) {
            throw new IllegalArgumentException("Tenant ID cannot be null or empty");
        }
    }

    private WebClient.RequestHeadersSpec<?> addHeaders(WebClient.RequestHeadersSpec<?> spec,
            ValidationRequest request) {
        // Required headers
        spec = spec.header("X-TechBD-Tenant-ID", request.tenantId)
                .header("X-TechBD-Interaction-ID", request.interactionId);

        // Optional headers - only add if not null/empty
        if (StringUtils.isNotEmpty(request.groupInteractionId)) {
            spec = spec.header("X-TechBD-Group-Interaction-ID", request.groupInteractionId);
        }
        if (StringUtils.isNotEmpty(request.masterInteractionId)) {
            spec = spec.header("X-TechBD-Master-Interaction-ID", request.masterInteractionId);
        }
        if (StringUtils.isNotEmpty(request.sourceType)) {
            spec = spec.header("X-TechBD-Source-Type", request.sourceType);
        }
        if (StringUtils.isNotEmpty(request.sessionId)) {
            spec = spec.header("X-TechBD-Session-ID", request.sessionId);
        }
        if (StringUtils.isNotEmpty(request.userId)) {
            spec = spec.header("X-TechBD-User-ID", request.userId);
        }
        if (StringUtils.isNotEmpty(request.userName)) {
            spec = spec.header("X-TechBD-User-Name", request.userName);
        }
        if (StringUtils.isNotEmpty(request.userRole)) {
            spec = spec.header("X-TechBD-User-Role", request.userRole);
        }
        if (StringUtils.isNotEmpty(request.correlationId)) {
            spec = spec.header("X-TechBD-Correlation-ID", request.correlationId);
        }
        if (StringUtils.isNotEmpty(request.requestUri)) {
            spec = spec.header("X-TechBD-Request-URI", request.requestUri);
        }
        if (StringUtils.isNotEmpty(request.overrideRequestUri)) {
            spec = spec.header("X-TechBD-Override-Request-URI", request.overrideRequestUri);
        }
        if (StringUtils.isNotEmpty(request.provenance)) {
            spec = spec.header("X-TechBD-Provenance", request.provenance);
        }
        if (StringUtils.isNotEmpty(request.healthCheck)) {
            spec = spec.header("X-TechBD-Health-Check", request.healthCheck);
        }
        if (StringUtils.isNotEmpty(request.customDataLakeApi)) {
            spec = spec.header("X-TechBD-Custom-DataLake-API", request.customDataLakeApi);
        }
        if (StringUtils.isNotEmpty(request.dataLakeApiContentType)) {
            spec = spec.header("X-TechBD-DataLake-API-Content-Type", request.dataLakeApiContentType);
        }
        if (StringUtils.isNotEmpty(request.mtlsStrategy)) {
            spec = spec.header("X-TechBD-MTLS-Strategy", request.mtlsStrategy);
        }
        if (StringUtils.isNotEmpty(request.elaboration)) {
            spec = spec.header("X-TechBD-Elaboration", request.elaboration);
        }
        if (StringUtils.isNotEmpty(request.shinNyIgVersion)) {
            spec = spec.header("X-TechBD-SHIN-NY-IG-Version", request.shinNyIgVersion);
        }
        if (StringUtils.isNotEmpty(request.validationSeverityLevel)) {
            spec = spec.header("X-TechBD-Validation-Severity-Level", request.validationSeverityLevel);
        }

        return spec;
    }

    private void logRequest(ValidationRequest request) {
        Map<String, String> logDetails = new HashMap<>();
        logDetails.put("interactionId", request.interactionId);
        logDetails.put("tenantId", request.tenantId);
        logDetails.put("groupInteractionId", request.groupInteractionId);
        logDetails.put("masterInteractionId", request.masterInteractionId);
        logDetails.put("sourceType", request.sourceType);
        logDetails.put("correlationId", request.correlationId);

        LOG.debug("FHIR validation request details: {}", logDetails);
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
