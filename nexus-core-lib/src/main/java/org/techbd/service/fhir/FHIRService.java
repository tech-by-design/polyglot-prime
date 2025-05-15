package org.techbd.service.fhir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.collections4.CollectionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.NonNull;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.techbd.config.AppConfig;
import org.techbd.config.AppConfig.DefaultDataLakeApiAuthn;
import org.techbd.config.AppConfig.MTlsAwsSecrets;
import org.techbd.config.AppConfig.WithApiKeyAuth;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.Interactions;
import org.techbd.config.Interactions.RequestEncountered;
import org.techbd.config.Interactions.RequestResponseEncountered;
import org.techbd.config.MirthJooqConfig;
import org.techbd.config.SourceType;
import org.techbd.exceptions.ErrorCode;
import org.techbd.exceptions.JsonValidationException;
import org.techbd.service.fhir.engine.OrchestrationEngine;
import org.techbd.service.fhir.engine.OrchestrationEngine.Device;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;
import org.techbd.util.AWSUtil;
import org.techbd.util.fhir.FHIRUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.common.util.StringUtils;
import io.netty.handler.ssl.SslContextBuilder;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

@AllArgsConstructor
@Getter
@Setter
public class FHIRService {

    private static final String START_TIME_ATTRIBUTE = "startTime";
    private static final Logger LOG = LoggerFactory.getLogger(FHIRService.class.getName());
    private AppConfig appConfig;
    private Tracer tracer;
    private OrchestrationEngine engine;

    public FHIRService() {
        try {
            this.tracer = GlobalOpenTelemetry.getTracer("FHIRService");
        } catch (NoClassDefFoundError | Exception e) {
            LOG.error("Warning: OpenTelemetry not available, using No-Op tracer.");
            this.tracer = GlobalOpenTelemetry.get().getTracer("no-op-tracer");
        }
    }

    /**
     * TODO: These parameters will be removed. Ensure they are set in the
     * {@code requestMap} from Mirth.
     *
     * Sets the following parameters in the provided {@code requestParameters}
     * map:
     *
     * @param tenantId The unique identifier for the tenant.
     * @param customDataLakeApi The custom Data Lake API endpoint.
     * @param dataLakeApiContentType The content type for Data Lake API
     * requests.
     * @param healthCheck A flag indicating whether this is a health check
     * request.
     * @param isSync A boolean flag to specify if the request is synchronous.
     * @param provenance The provenance information related to the request.
     * @param mtlsStrategy The mTLS (mutual TLS) strategy used for secure
     * communication.
     * @param interactionId A unique identifier for the interaction.
     * @param groupInteractionId A unique identifier for the group-level
     * interaction.
     * @param masterInteractionId A unique identifier for the master
     * interaction.
     * @param sourceType The type of source making the request.
     * @param requestUriToBeOverriden The request URI that should be overridden.
     * @param coRrelationId The correlation ID used for tracking requests across
     * services
     */
    public Object processBundle(final @RequestBody @Nonnull String payload, Map<String, String> requestParameters,
            Map<String, String> headerParameters,
            Map<String, Object> responseParameters)
            throws IOException {
        Span span = tracer.spanBuilder("FHIRService.processBundle").startSpan();
        try {
            final var start = Instant.now();
            var interactionId = requestParameters.get(Constants.INTERACTION_ID);
            if (null == interactionId) {
                interactionId = UUID.randomUUID().toString();
            }
            LOG.info("Bundle processing start at {} for interaction id {}.", interactionId);

            var dataLakeApiContentType = headerParameters.get(Constants.DATA_LAKE_API_CONTENT_TYPE);
            LOG.info("Retrieved dataLakeApiContentType: {}", dataLakeApiContentType); // TODO-to be removed
            var tenantId = headerParameters.get(Constants.TENANT_ID);
            LOG.info("Retrieved tenantId: {}", tenantId); // TODO-to be removed
            if (tenantId == null) {
                tenantId = "test";
            }
            var source = requestParameters.get(Constants.SOURCE_TYPE);
            LOG.info("Retrieved source: {}", source); // TODO-to be removed
            var origin = requestParameters.get(Constants.ORIGIN);
            LOG.info("Retrieved origin: {}", origin); // TODO-to be removed
            var deletesessioncookie = requestParameters.get(Constants.DELETE_USER_SESSION_COOKIE);
            LOG.info("Retrieved deletesessioncookie: {}", deletesessioncookie); // TODO-to be removed
            var customDataLakeApi = headerParameters.get(Constants.CUSTOM_DATA_LAKE_API);
            LOG.info("Retrieved customDataLakeApi: {}", customDataLakeApi); // TODO-to be removed
            final var healthCheck = headerParameters.get(Constants.HEALTH_CHECK);
            LOG.info("Retrieved healthCheck: {}", healthCheck); // TODO-to be removed
            final var isSync = requestParameters.get(Constants.IMMEDIATE);
            LOG.info("Retrieved isSync: {}", isSync); // TODO-to be removed
            var provenance = headerParameters.get(Constants.PROVENANCE);
            LOG.info("Retrieved provenance: {}", provenance); // TODO-to be removed
            final var mtlsStrategy = requestParameters.get(Constants.MTLS_STRATEGY);
            LOG.info("Retrieved mtlsStrategy: {}", mtlsStrategy); // TODO-to be removed

            final var groupInteractionId = requestParameters.get(Constants.GROUP_INTERACTION_ID);
            LOG.info("Retrieved groupInteractionId: {}", groupInteractionId); // TODO-to be removed

            final var masterInteractionId = requestParameters.get(Constants.MASTER_INTERACTION_ID);
            LOG.info("Retrieved masterInteractionId: {}", masterInteractionId); // TODO-to be removed

            final var sourceType = requestParameters.get(Constants.SOURCE_TYPE);
            LOG.info("Retrieved sourceType: {}", sourceType); // TODO-to be removed

            final var requestUriToBeOverriden = headerParameters.get(Constants.OVERRIDE_REQUEST_URI);
            LOG.info("Retrieved requestUriToBeOverriden: {}", requestUriToBeOverriden); // TODO-to be removed

            final var coRrelationId = requestParameters.get(Constants.CORRELATION_ID);
            LOG.info("Retrieved coRrelationId: {}", coRrelationId); // TODO-to be removed

            final var requestUri = headerParameters.get(Constants.REQUEST_URI);
            LOG.info("Retrieved requestUri: {}", requestUri); // TODO-to be removed

            if (null == interactionId) {
                if (StringUtils.isNotEmpty(headerParameters.get(Constants.CORRELATION_ID))) {
                    interactionId = headerParameters.get(Constants.CORRELATION_ID);
                    LOG.info("Updated interactionId from correlationId: {}", interactionId); // TODO-to be removed
                }
            }

            final var dslContext = MirthJooqConfig.dsl();
            LOG.info("Initialized dslContext"); // TODO-to be removed
            final var jooqCfg = dslContext.configuration();
            LOG.info("Configured jooqCfg"); // TODO-to be removed

            Map<String, Object> payloadWithDisposition = null;
            try {
                validateJson(payload, interactionId);
                LOG.info("JSON validated"); // TODO-to be removed
                validateBundleProfileUrl(payload, interactionId);
                LOG.info("Bundle profile URL validated"); // TODO-to be removed

                if (null == requestParameters.get(Constants.DATA_LAKE_API_CONTENT_TYPE)) {
                    dataLakeApiContentType = MediaType.APPLICATION_JSON_VALUE;
                    LOG.info("Defaulting dataLakeApiContentType to JSON"); // TODO-to be removed
                }

                Map<String, Object> immediateResult = validate(requestParameters, payload, interactionId, provenance,
                        sourceType);
                LOG.info("Validation result: {}", immediateResult); // TODO-to be removed

                final var result = Map.of("OperationOutcome", immediateResult);
                LOG.info("Final validation result wrapped in OperationOutcome"); // TODO-to be removed

                if (StringUtils.isNotEmpty(requestUri)
                        && (requestUri.equals("/Bundle/$validate") || requestUri.equals("/Bundle/$validate/"))) {
                    LOG.info("Returning validation result for /Bundle/$validate"); // TODO-to be removed
                    return result;
                }

                if ("true".equals(healthCheck)) {
                    LOG.info("Health check enabled, skipping scoring engine submission"); // TODO-to be removed
                    return result;
                }

                payloadWithDisposition = registerBundleInteraction(jooqCfg, headerParameters, requestParameters,
                        headerParameters, payload, result, interactionId, groupInteractionId, masterInteractionId,
                        sourceType, requestUriToBeOverriden, coRrelationId);
                LOG.info("Payload with disposition registered: {}", payloadWithDisposition); // TODO-to be removed

                if (isActionDiscard(payloadWithDisposition)) {
                    LOG.info("Action discard detected, returning payloadWithDisposition"); // TODO-to be removed
                    return payloadWithDisposition;
                }
                if (null == payloadWithDisposition) {
                    LOG.warn(
                            "FHIRService:: ERROR:: Disposition payload is not available.Send Bundle payload to scoring engine for interaction id {}.",
                            getBundleInteractionId(requestParameters, coRrelationId));
                    sendToScoringEngine(jooqCfg, requestParameters, customDataLakeApi, dataLakeApiContentType,
                            tenantId, payload,
                            provenance, null,
                            mtlsStrategy,
                            interactionId, groupInteractionId, masterInteractionId,
                            sourceType, requestUriToBeOverriden, coRrelationId);
                    Instant end = Instant.now();
                    Duration timeElapsed = Duration.between(start, end);
                    LOG.info("Bundle processing end for interaction id: {} Time Taken : {}  milliseconds",
                            interactionId, timeElapsed.toMillis());
                    return result;
                } else {
                    LOG.info(
                            "FHIRService:: Received Disposition payload.Send Disposition payload to scoring engine for interaction id {}.",
                            interactionId);
                    sendToScoringEngine(jooqCfg, requestParameters, customDataLakeApi, dataLakeApiContentType,
                            tenantId, payload,
                            provenance, payloadWithDisposition,
                            mtlsStrategy, interactionId, groupInteractionId,
                            masterInteractionId, sourceType, requestUriToBeOverriden, coRrelationId);
                    Instant end = Instant.now();
                    Duration timeElapsed = Duration.between(start, end);
                    LOG.info("Bundle processing end for interaction id: {} Time Taken : {}  milliseconds",
                            interactionId, timeElapsed.toMillis());
                    return payloadWithDisposition;
                }
            } catch (JsonValidationException ex) {
                payloadWithDisposition = registerBundleInteraction(jooqCfg, headerParameters, requestParameters,
                        headerParameters, payload, buildOperationOutcome(ex, interactionId), interactionId,
                        groupInteractionId, masterInteractionId, sourceType, requestUriToBeOverriden, coRrelationId);
                LOG.info("Exception occurred: {}", ex.getMessage()); // TODO-to be removed
            }

            Instant end = Instant.now();
            Duration timeElapsed = Duration.between(start, end);
            LOG.info("Bundle processing end for interaction id: {} Time Taken: {} milliseconds", interactionId,
                    timeElapsed.toMillis()); // TODO-to be removed
            return payloadWithDisposition;
        } finally {
            span.end();
            LOG.info("Span ended"); // TODO-to be removed
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean isActionDiscard(Map<String, Object> payloadWithDisposition) {
        return Optional.ofNullable(payloadWithDisposition)
                .map(map -> (Map<String, Object>) map.get("OperationOutcome"))
                .map(outcome -> (List<Map<String, Object>>) outcome.get("techByDesignDisposition"))
                .flatMap(dispositions -> dispositions.stream()
                .map(disposition -> disposition != null ? (String) disposition.get("action") : null)
                .filter(action -> TechByDesignDisposition.DISCARD.action.equals(action))
                .findFirst())
                .isPresent();
    }

    public void validateJson(String jsonString, String interactionId) {
        Span validateJsonSpan = tracer.spanBuilder("FHIRService.validateJson").startSpan();
        try {
            try {
                Configuration.objectMapper.readTree(jsonString);
            } catch (Exception e) {
                throw new JsonValidationException(ErrorCode.INVALID_JSON);
            }
        } finally {
            validateJsonSpan.end();
        }

    }

    public void validateBundleProfileUrl(String jsonString, String interactionId) {
        Span validateJsonSpan = tracer.spanBuilder("FHIRService.validateBundleProfileUrl").startSpan();
        try {
            JsonNode rootNode;
            try {
                rootNode = Configuration.objectMapper.readTree(jsonString);
                JsonNode metaNode = rootNode.path("meta").path("profile");

                List<String> profileList = Optional.ofNullable(metaNode)
                        .filter(JsonNode::isArray)
                        .map(node -> StreamSupport.stream(node.spliterator(), false)
                        .map(JsonNode::asText)
                        .collect(Collectors.toList()))
                        .orElse(List.of());

                if (CollectionUtils.isEmpty(profileList)) {
                    LOG.error("Bundle profile is not provided for interaction id: {}", interactionId);
                    throw new JsonValidationException(ErrorCode.BUNDLE_PROFILE_URL_IS_NOT_PROVIDED);
                }

                List<String> allowedProfileUrls = FHIRUtil.getAllowedProfileUrls(appConfig);
                if (profileList.stream().noneMatch(allowedProfileUrls::contains)) {
                    LOG.error("Bundle profile URL provided is not valid for interaction id: {}", interactionId);
                    throw new JsonValidationException(ErrorCode.INVALID_BUNDLE_PROFILE);
                }
            } catch (JsonProcessingException e) {
                LOG.error("Json Processing exception while extracting profile url for interaction id :{}", e);
            }

        } finally {
            validateJsonSpan.end();
        }

    }

    public static Map<String, Map<String, Object>> buildOperationOutcome(JsonValidationException ex,
            String interactionId) {
        var validationResult = Map.of(
                "valid", false,
                "issues", List.of(Map.of(
                        "message", ex.getErrorCode() + ": " + ex.getMessage(),
                        "severity", "FATAL")));
        var operationOutcome = new HashMap<String, Object>(Map.of(
                "resourceType", "OperationOutcome",
                "bundleSessionId", interactionId,
                "validationResults", List.of(validationResult)));
        return Map.of("OperationOutcome", operationOutcome);

    }

    private void addObservabilityHeadersToResponse(Map<String, String> requestParameters,
            Map<String, String> headerParameters, Map<String, Object> responsParameters) {
        final var startTime = Instant
                .parse(requestParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME));
        final var finishTime = Instant.now();
        final Duration duration = Duration.between(startTime, finishTime);
        final String startTimeText = startTime.toString();
        final String finishTimeText = finishTime.toString();
        final String durationMsText = String.valueOf(duration.toMillis());
        final String durationNsText = String.valueOf(duration.toNanos());
        headerParameters.put("X-Observability-Metric-Interaction-Start-Time", startTimeText);
        headerParameters.put("X-Observability-Metric-Interaction-Finish-Time", finishTimeText);
        headerParameters.put("X-Observability-Metric-Interaction-Duration-Nanosecs", durationMsText);
        headerParameters.put("X-Observability-Metric-Interaction-Duration-Millisecs", durationNsText);
        try {
            final var metricCookie = new Cookie("Observability-Metric-Interaction-Active",
                    URLEncoder.encode("{ \"startTime\": \"" + startTimeText
                            + "\", \"finishTime\": \"" + finishTimeText
                            + "\", \"durationMillisecs\": \"" + durationMsText
                            + "\", \"durationNanosecs\": \""
                            + durationNsText + "\" }", StandardCharsets.UTF_8.toString()));
            metricCookie.setPath("/"); // Set path as required
            metricCookie.setHttpOnly(false); // Ensure the cookie is accessible via JavaScript
            // responsParameters.put(Constants.METRIC_COOKIE,metricCookie);
        } catch (UnsupportedEncodingException ex) {
            LOG.error("Exception during setting  Observability-Metric-Interaction-Active cookie to response header",
                    ex);
        }
    }

    private Map<String, Object> registerBundleInteraction(org.jooq.Configuration jooqCfg,
            Map<String, String> headerParameters,
            Map<String, String> requestParameters, Map<String, String> responseParameters,
            String payload, Map<String, Map<String, Object>> validationResult, String interactionId,
            String groupInteractionId, String masterInteractionId, String sourceType,
            String requestUriToBeOverriden, String coRrelationId)
            throws IOException {
        Span span = tracer.spanBuilder("FHIRMapService.registerBundleInteraction").startSpan();
        try {
            final Interactions interactions = new Interactions();
            // final var mutatableReq = new ContentCachingRequestWrapper(requestParameters);
            RequestEncountered requestEncountered = null;
            if (StringUtils.isNotEmpty(coRrelationId)) {
                requestEncountered = new Interactions.RequestEncountered(requestParameters,
                        payload.getBytes(),
                        UUID.fromString(coRrelationId),
                        FHIRUtil.createRequestHeaders(headerParameters, requestParameters));
            } else if (null != interactionId) {
                // If its a converted CSV payload ,it will already have an interaction id.hence
                // do not create new interactionId
                requestEncountered = new Interactions.RequestEncountered(requestParameters,
                        payload.getBytes(),
                        UUID.fromString(interactionId),
                        FHIRUtil.createRequestHeaders(headerParameters, requestParameters));
            } else if (null != getBundleInteractionId(requestParameters, coRrelationId)) {
                // If its a converted HL7 payload ,it will already have an interaction id.hence
                // do not create new interactionId
                requestEncountered = new Interactions.RequestEncountered(requestParameters,
                        payload.getBytes(),
                        UUID.fromString(getBundleInteractionId(requestParameters, coRrelationId)),
                        FHIRUtil.createRequestHeaders(headerParameters, requestParameters));
            } else {
                requestEncountered = new Interactions.RequestEncountered(requestParameters,
                        payload.getBytes());
            }
            // final var mutatableResp = new
            // ContentCachingResponseWrapper(responseParameters);
            setActiveRequestEnc(requestParameters, requestEncountered);
            final var rre = new Interactions.RequestResponseEncountered(requestEncountered,
                    new Interactions.ResponseEncountered(responseParameters, requestEncountered,
                            Configuration.objectMapper
                                    .writeValueAsBytes(validationResult),
                            FHIRUtil.createResponseHeaders(headerParameters, requestParameters)));

            interactions.addHistory(rre);
            setActiveInteraction(requestParameters, rre);
            final var provenance = "%s.doFilterInternal".formatted(FHIRService.class.getName());
            final var rihr = new RegisterInteractionHttpRequest();

            try {
                prepareRequest(rihr, rre, provenance, requestParameters, interactionId, groupInteractionId,
                        masterInteractionId, sourceType, requestUriToBeOverriden);
                final var start = Instant.now();
                int i = rihr.execute(jooqCfg);
                final var end = Instant.now();
                LOG.info(
                        "FHIRMapService  - Time taken : {} milliseconds for DB call to REGISTER State None, Accept, Disposition: for interaction id: {} ",
                        Duration.between(start, end).toMillis(),
                        rre.interactionId().toString());
                JsonNode payloadWithDisposition = rihr.getReturnValue();
                LOG.info("REGISTER State None, Accept, Disposition: END for interaction id: {} ",
                        rre.interactionId().toString());
                return Configuration.objectMapper.convertValue(payloadWithDisposition,
                        new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception e) {
                LOG.error(
                        "ERROR:: REGISTER State None, Accept, Disposition: for interaction id: {} tenant id: {}: CALL "
                        + rihr.getName() + " error",
                        rre.interactionId().toString(),
                        rre.tenant(), e);
                e.printStackTrace();
            }
            return null;
        } finally {
            span.end();
        }
    }

    private void prepareRequest(RegisterInteractionHttpRequest rihr, RequestResponseEncountered rre,
            String provenance, Map<String, String> requestParameters, String interactionId, String groupInteractionId,
            String masterInteractionId, String sourceType, String requestUriToBeOverriden) {
        LOG.info("REGISTER State None, Accept, Disposition: BEGIN for interaction id: {} tenant id: {}",
                rre.interactionId().toString(), rre.tenant());
        rihr.setInteractionId(interactionId != null ? interactionId : rre.interactionId().toString());
        rihr.setGroupHubInteractionId(groupInteractionId);
        rihr.setSourceHubInteractionId(masterInteractionId);
        rihr.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                Map.of("nature", "org.techbd.service.http.Interactions$RequestResponseEncountered",
                        "tenant_id",
                        rre.tenant() != null ? (rre.tenant().tenantId() != null
                        ? rre.tenant().tenantId()
                        : "N/A") : "N/A")));
        rihr.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
        rihr.setInteractionKey(StringUtils.isNotEmpty(requestUriToBeOverriden) ? requestUriToBeOverriden
                : requestParameters.get(Constants.REQUEST_URI));
        rihr.setPayload((JsonNode) Configuration.objectMapper.valueToTree(rre));
        rihr.setCreatedAt(OffsetDateTime.now());
        rihr.setCreatedBy(FHIRService.class.getName());
        rihr.setSourceType(sourceType);
        rihr.setProvenance(provenance);
        setUserDetails(rihr, requestParameters);
    }

    private void setUserDetails(RegisterInteractionHttpRequest rihr, Map<String, String> requestParameters) {
        // final var sessionId = requestParameters.get(Constants.REQUESTED_SESSION_ID);
        rihr.setUserName(requestParameters.get(Constants.USER_NAME));
        rihr.setUserId(requestParameters.get(Constants.USER_ID));
        rihr.setUserSession(UUID.randomUUID().toString());// TODO -check and add from mirth
        rihr.setUserRole(requestParameters.get(Constants.USER_ROLE));
    }

    protected static final void setActiveRequestTenant(final @NonNull HttpServletRequest request,
            final @NonNull Interactions.Tenant tenant) {
        request.setAttribute("activeHttpRequestTenant", tenant);
    }

    private void setActiveRequestEnc(final @NonNull Map<String, String> requestParameters,
            final @NonNull Interactions.RequestEncountered re) {
        requestParameters.put("activeHttpRequestEncountered", re.requestId().toString()); // Store requestId as a string
        setActiveRequestTenant(requestParameters, re.tenant()); // Store tenant ID separately
    }

    protected static final void setActiveRequestTenant(final @NonNull Map<String, String> requestParameters,
            final @NonNull Interactions.Tenant tenant) {
        requestParameters.put("activeHttpRequestTenant", tenant.tenantId());
    }

    private void setActiveInteraction(final @NonNull Map<String, String> requestParameters,
            final @NonNull Interactions.RequestResponseEncountered rre) {
        requestParameters.put("activeHttpInteraction", rre.interactionId().toString()); // Store interaction ID as a
        // string
    }

    private Map<String, Object> validate(Map<String, String> requestParameters, String payload,
            String interactionId, String provenance, String sourceType) {
        Span span = tracer.spanBuilder("FhirService.validate").startSpan();
        try {
            final var start = Instant.now();
            LOG.info("FHIRService  - Validate -BEGIN for interactionId: {} ", interactionId);
            final var igPackages = appConfig.getIgPackages();
            final var igVersion = appConfig.getIgVersion();

            final var sessionBuilder = engine.session()
                    .withSessionId(UUID.randomUUID().toString())
                    .onDevice(Device.createDefault())
                    .withInteractionId(interactionId)
                    .withPayloads(List.of(payload))
                    .withFhirProfileUrl(FHIRUtil.getBundleProfileUrl())
                    .withTracer(tracer)
                    .withFhirIGPackages(igPackages)
                    .withIgVersion(igVersion)
                    .addHapiValidationEngine(); // by default
            // clearExisting is set to true so engines can be fully supplied through header

            final var session = sessionBuilder.build();
            try {
                engine.orchestrate(session);
                // TODO: if there are errors that should prevent forwarding, stop here
                // TODO: need to implement `immediate` (sync) webClient op, right now it's async
                // only
                // immediateResult is what's returned to the user while async operation
                // continues
                final var immediateResult = new HashMap<>(Map.of(
                        "resourceType", "OperationOutcome",
                        "help",
                        "If you need help understanding how to decipher OperationOutcome please see "
                        + appConfig.getOperationOutcomeHelpUrl(),
                        "bundleSessionId", interactionId, // for tracking in
                        // database, etc.
                        "isAsync", true,
                        "validationResults", session.getValidationResults(),
                        "statusUrl",
                        "/Bundle/$status/" // TODO - check and add full url
                        + interactionId.toString(),
                        "device", session.getDevice()));
                if (SourceType.CSV.name().equals(sourceType) && StringUtils.isNotEmpty(provenance)) {
                    immediateResult.put("provenance",
                            Configuration.objectMapper.readTree(provenance));
                }

                return immediateResult; // Return the validation results
            } catch (Exception e) {
                // Log the error and create a failure response
                LOG.error("FHIRService - Validate - FAILED for interactionId: {}", interactionId, e);
                return Map.of(
                        "resourceType", "OperationOutcome",
                        "interactionId", interactionId,
                        "error", "Validation failed: " + e.getMessage());
            } finally {
                // Ensure the session is cleared to avoid memory leaks
                engine.clear(session);
                Instant end = Instant.now();
                Duration timeElapsed = Duration.between(start, end);
                LOG.info("FHIRService  - Validate -END for interaction id: {} Time Taken : {}  milliseconds",
                        interactionId, timeElapsed.toMillis());
            }
        } finally {
            span.end();
        }
    }

    private void sendToScoringEngine(org.jooq.Configuration jooqCfg, Map<String, String> requestParameters,
            String scoringEngineApiURL,
            String dataLakeApiContentType,
            String tenantId,
            String payload,
            String provenance,
            Map<String, Object> validationPayloadWithDisposition,
            String mtlsStrategy, String interactionId, String groupInteractionId,
            String masterInteractionId, String sourceType, String requestUriToBeOverriden, String coRrelationId) {
        Span span = tracer.spanBuilder("FhirService.sentToScoringEngine").startSpan();
        try {
            interactionId = null != interactionId ? interactionId
                    : getBundleInteractionId(requestParameters, coRrelationId);
            if (StringUtils.isNotEmpty(coRrelationId)) {
                interactionId = coRrelationId;
            }
            LOG.info("FHIRService:: sendToScoringEngine BEGIN for interaction id: {} for", interactionId);

            try {
                Map<String, Object> bundlePayloadWithDisposition = null;
                LOG.debug("FHIRService:: sendToScoringEngine includeOperationOutcome : {} interaction id: {}",
                        interactionId);
                if (null != validationPayloadWithDisposition) { // todo
                    // -revisit
                    LOG.debug(
                            "FHIRService:: sendToScoringEngine Prepare payload with operation outcome interaction id: {}",
                            interactionId);
                    bundlePayloadWithDisposition = preparePayload(requestParameters,
                            payload,
                            validationPayloadWithDisposition, interactionId);
                } else {
                    LOG.debug(
                            "FHIRService:: sendToScoringEngine Send payload without operation outcome interaction id: {}",
                            interactionId);
                    bundlePayloadWithDisposition = Configuration.objectMapper.readValue(payload,
                            new TypeReference<Map<String, Object>>() {
                    });
                }
                final var dataLakeApiBaseURL = Optional.ofNullable(scoringEngineApiURL)
                        .filter(s -> !s.isEmpty())
                        .orElse(appConfig.getDefaultDatalakeApiUrl());
                final var defaultDatalakeApiAuthn = appConfig.getDefaultDataLakeApiAuthn();

                if (null == defaultDatalakeApiAuthn) {
                    LOG.info(
                            "###### defaultDatalakeApiAuthn is not defined #######.Hence proceeding with post to scoring engine without mTls for interaction id :{}",
                            interactionId);
                    handleNoMtls(MTlsStrategy.NO_MTLS, interactionId, tenantId, dataLakeApiBaseURL,
                            jooqCfg, requestParameters,
                            bundlePayloadWithDisposition, payload, dataLakeApiContentType,
                            provenance,
                            groupInteractionId,
                            masterInteractionId, sourceType, requestUriToBeOverriden,
                            defaultDatalakeApiAuthn != null ? defaultDatalakeApiAuthn.withApiKeyAuth() : null);
                } else {
                    handleMTlsStrategy(defaultDatalakeApiAuthn, interactionId, tenantId,
                            dataLakeApiBaseURL,
                            jooqCfg, requestParameters, bundlePayloadWithDisposition,
                            payload,
                            dataLakeApiContentType, provenance,
                            mtlsStrategy, groupInteractionId, masterInteractionId,
                            sourceType, requestUriToBeOverriden);
                }

            } catch (Exception e) {
                handleError(validationPayloadWithDisposition, e, requestParameters, interactionId);
            } finally {
                LOG.info("FHIRService:: sendToScoringEngine END for interaction id: {}", interactionId);
            }
        } finally {
            span.end();
        }
    }

    public void handleMTlsStrategy(DefaultDataLakeApiAuthn defaultDatalakeApiAuthn, String interactionId,
            String tenantId, String dataLakeApiBaseURL,
            org.jooq.Configuration jooqCfg, Map<String, String> requestParameters,
            Map<String, Object> bundlePayloadWithDisposition, String payload, String dataLakeApiContentType,
            String provenance,
            String mtlsStrategyStr,
            String groupInteractionId,
            String masterInteractionId, String sourceType, String requestUriToBeOverriden) {
        MTlsStrategy mTlsStrategy = null;

        LOG.info("FHIRService:: handleMTlsStrategy MTLS strategy from application.yml :{} for interaction id: {}",
                defaultDatalakeApiAuthn.mTlsStrategy(), interactionId);
        if (StringUtils.isNotEmpty(mtlsStrategyStr)) {
            LOG.info("FHIRService:: Proceed with mtls strategy from endpoint  :{} for interaction id: {}",
                    defaultDatalakeApiAuthn.mTlsStrategy(), interactionId);
            mTlsStrategy = MTlsStrategy.fromString(mtlsStrategyStr);
        } else {
            mTlsStrategy = MTlsStrategy.fromString(defaultDatalakeApiAuthn.mTlsStrategy());
        }
        String requestURI = StringUtils.isNotEmpty(requestUriToBeOverriden) ? requestUriToBeOverriden
                : requestParameters.get(Constants.REQUEST_URI);
        LOG.info("FHIRService:: apiKeyAuthDetails MTLS strategy from application.yml :{} for interaction id: {}",
                defaultDatalakeApiAuthn.withApiKeyAuth(), interactionId);
        switch (mTlsStrategy) {
            case AWS_SECRETS ->
                handleAwsSecrets(defaultDatalakeApiAuthn.mTlsAwsSecrets(), interactionId,
                        tenantId, dataLakeApiBaseURL, dataLakeApiContentType,
                        bundlePayloadWithDisposition, jooqCfg, provenance,
                        requestURI,
                        payload,
                        groupInteractionId, masterInteractionId, sourceType);
            default ->
                handleNoMtls(mTlsStrategy, interactionId, tenantId, dataLakeApiBaseURL, jooqCfg,
                        requestParameters,
                        bundlePayloadWithDisposition, payload, dataLakeApiContentType,
                        provenance,
                        groupInteractionId,
                        masterInteractionId, sourceType, requestUriToBeOverriden,
                        defaultDatalakeApiAuthn.withApiKeyAuth());
        }
    }

    private void handleNoMtls(MTlsStrategy mTlsStrategy, String interactionId, String tenantId,
            String dataLakeApiBaseURL,
            org.jooq.Configuration jooqCfg, Map<String, String> requestParameters,
            Map<String, Object> bundlePayloadWithDisposition, String payload, String dataLakeApiContentType,
            String provenance,
            String groupInteractionId,
            String masterInteractionId, String sourceType, String requestUriToBeOverriden,
            WithApiKeyAuth apiKeyAuthDetails) {
        if (null == apiKeyAuthDetails) {
            LOG.error("ERROR:: FHIRService:: handleApiKeyAuth apiKeyAuthDetails is not configured in application.yml");
            throw new IllegalArgumentException("apiKeyAuthDetails configuration is not defined in application.yml");
        }

        if (StringUtils.isEmpty(apiKeyAuthDetails.apiKeyHeaderName())) {
            LOG.error("ERROR:: FHIRService:: handleApiKeyAuth apiKeyHeaderName is not configured in application.yml");
            throw new IllegalArgumentException("apiKeyHeaderName is not defined in application.yml");
        }

        if (StringUtils.isEmpty(apiKeyAuthDetails.apiKeySecretName())) {
            LOG.error("ERROR:: FHIRService:: handleApiKeyAuth apiKeySecretName is not configured in application.yml");
            throw new IllegalArgumentException("apiKeySecretName is not defined in application.yml");
        }
        if (!MTlsStrategy.NO_MTLS.value.equals(mTlsStrategy.value)) {
            LOG.info(
                    "#########Invalid MTLS Strategy defined #############: Allowed values are {} .Hence proceeding with post to scoring engine without mTls for interaction id :{}",
                    MTlsStrategy.getAllValues(), interactionId);
        }
        LOG.debug("FHIRService:: handleNoMtls Build WebClient with MTLS  Disabled -BEGIN \n"
                + "with scoring Engine API URL: {} \n"
                + "dataLakeApiContentType: {} \n"
                + "bundlePayloadWithDisposition: {} \n"
                + "for interactionID: {} \n"
                + "tenant Id: {}",
                dataLakeApiBaseURL,
                dataLakeApiContentType,
                bundlePayloadWithDisposition == null ? "Payload is null"
                        : "Payload is not null",
                interactionId,
                tenantId);
        var webClient = createWebClient(dataLakeApiBaseURL, jooqCfg, requestParameters,
                tenantId, payload,
                bundlePayloadWithDisposition, provenance,
                interactionId, groupInteractionId, masterInteractionId, sourceType,
                requestUriToBeOverriden);
        LOG.debug("FHIRService:: createWebClient END for interaction id: {} tenant id :{} ", interactionId,
                tenantId);
        LOG.debug("FHIRService:: sendPostRequest BEGIN for interaction id: {} tenantid :{} ", interactionId,
                tenantId);
        sendPostRequest(webClient, tenantId, bundlePayloadWithDisposition, payload,
                dataLakeApiContentType, interactionId,
                jooqCfg, provenance,
                StringUtils.isNotEmpty(requestUriToBeOverriden) ? requestUriToBeOverriden
                : requestParameters.get(Constants.REQUEST_URI),
                dataLakeApiBaseURL, groupInteractionId,
                masterInteractionId, sourceType, apiKeyAuthDetails);
        LOG.debug("FHIRService:: sendPostRequest END for interaction id: {} tenantid :{} ", interactionId,
                tenantId);
    }

    private void handleAwsSecrets(MTlsAwsSecrets mTlsAwsSecrets, String interactionId, String tenantId,
            String dataLakeApiBaseURL, String dataLakeApiContentType,
            Map<String, Object> bundlePayloadWithDisposition,
            org.jooq.Configuration jooqCfg, String provenance, String requestURI,
            String payload, String groupInteractionId,
            String masterInteractionId,
            String sourceType) {
        try {
            LOG.info("FHIRService :: handleAwsSecrets -BEGIN for interactionId : {}",
                    interactionId);

            registerStateForward(jooqCfg, provenance, interactionId, requestURI,
                    tenantId, bundlePayloadWithDisposition, null,
                    payload, groupInteractionId, masterInteractionId, sourceType);
            if (null == mTlsAwsSecrets || null == mTlsAwsSecrets.mTlsKeySecretName()
                    || null == mTlsAwsSecrets.mTlsCertSecretName()) {
                throw new IllegalArgumentException(
                        "######## Strategy defined is aws-secrets but mTlsKeySecretName and mTlsCertSecretName is not correctly configured. ######### ");
            }
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            KeyDetails keyDetails = getSecretsFromAWSSecretManager(mTlsAwsSecrets.mTlsKeySecretName(),
                    mTlsAwsSecrets.mTlsCertSecretName());
            final String CERTIFICATE = keyDetails.cert();
            final String PRIVATE_KEY = keyDetails.key();

            if (StringUtils.isEmpty(CERTIFICATE)) {
                throw new IllegalArgumentException(
                        "Certifcate read from secrets manager with certficate secret name : {} is null "
                        + mTlsAwsSecrets.mTlsCertSecretName());
            }

            if (StringUtils.isEmpty(PRIVATE_KEY)) {
                throw new IllegalArgumentException(
                        "Private key read from secrets manager with key secret name : {} is null "
                        + mTlsAwsSecrets.mTlsKeySecretName());
            }

            LOG.debug(
                    "FHIRService :: handleAwsSecrets Certificate and Key Details fetched successfully for interactionId : {}",
                    interactionId);

            LOG.debug("FHIRService :: handleAwsSecrets Creating SSLContext  -BEGIN for interactionId : {}",
                    interactionId);

            final var sslContext = SslContextBuilder.forClient()
                    .keyManager(new ByteArrayInputStream(CERTIFICATE.getBytes()),
                            new ByteArrayInputStream(PRIVATE_KEY.getBytes()))
                    .build();
            LOG.debug("FHIRService :: handleAwsSecrets Creating SSLContext  - END for interactionId : {}",
                    interactionId);

            HttpClient httpClient = HttpClient.create()
                    .secure(sslSpec -> sslSpec.sslContext(sslContext));
            LOG.debug("FHIRService :: handleAwsSecrets HttpClient created successfully  for interactionId : {}",
                    interactionId);

            ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
            LOG.debug(
                    "FHIRService :: handleAwsSecrets ReactorClientHttpConnector created successfully  for interactionId : {}",
                    interactionId);
            LOG.info(
                    "FHIRService:: handleAwsSecrets Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN \n"
                    + "with scoring Engine API URL: {} \n"
                    + "dataLakeApiContentType: {} \n"
                    + "bundlePayloadWithDisposition: {} \n"
                    + "for interactionID: {} \n"
                    + "tenant Id: {}",
                    dataLakeApiBaseURL,
                    dataLakeApiContentType,
                    bundlePayloadWithDisposition == null ? "Payload is null"
                            : "Payload is not null",
                    interactionId,
                    tenantId);
            var webClient = WebClient.builder()
                    .baseUrl(dataLakeApiBaseURL)
                    .defaultHeader("Content-Type", dataLakeApiContentType)
                    .clientConnector(connector)
                    .build();
            LOG.debug(
                    "FHIRService :: handleAwsSecrets  Build WebClient with MTLS Enabled ReactorClientHttpConnector -END for interactionId :{}",
                    interactionId);
            LOG.debug("FHIRService:: handleAwsSecrets - sendPostRequest BEGIN for interaction id: {} tenantid :{} ",
                    interactionId,
                    tenantId);
            sendPostRequest(webClient, tenantId, bundlePayloadWithDisposition, payload,
                    dataLakeApiContentType, interactionId,
                    jooqCfg, provenance, requestURI, dataLakeApiBaseURL, groupInteractionId,
                    masterInteractionId, sourceType, null);
            LOG.debug("FHIRService:: handleAwsSecrets -sendPostRequest END for interaction id: {} tenantid :{} ",
                    interactionId,
                    tenantId);
            LOG.debug("FHIRService :: handleAwsSecrets Post to scoring engine -END for interactionId :{}",
                    interactionId);
        } catch (Exception ex) {
            LOG.error(
                    "ERROR:: FHIRService :: handleAwsSecrets Post to scoring engine FAILED with error :{} for interactionId :{} tenantId:{}",
                    ex.getMessage(),
                    interactionId, tenantId, ex);
            registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, ex.getMessage(),
                    provenance, groupInteractionId, masterInteractionId, sourceType);
        }
        LOG.info("FHIRService :: handleAwsSecrets -END for interactionId : {}",
                interactionId);
    }

    private WebClient createWebClient(String scoringEngineApiURL,
            org.jooq.Configuration jooqCfg,
            Map<String, String> requestParameters,
            String tenantId,
            String payload,
            Map<String, Object> bundlePayloadWithDisposition,
            String provenance,
            String interactionId, String groupInteractionId,
            String masterInteractionId, String sourceType, String requestUriToBeOverriden) {
        return WebClient.builder()
                .baseUrl(scoringEngineApiURL)
                .filter(ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
                    filter(clientRequest, requestParameters, jooqCfg, provenance, tenantId, payload,
                            bundlePayloadWithDisposition,
                            groupInteractionId,
                            masterInteractionId, sourceType, requestUriToBeOverriden, interactionId);
                    return Mono.just(clientRequest);
                }))
                .build();
    }

    private KeyDetails getSecretsFromAWSSecretManager(String keyName, String certName) {
        Region region = Region.US_EAST_1;
        LOG.debug(
                "FHIRService:: getSecretsFromAWSSecretManager  - Get Secrets Client Manager for region : {} BEGIN for interaction id: {}",
                Region.US_EAST_1);
        SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                .region(region)
                .build();
        KeyDetails keyDetails = new KeyDetails(getValue(secretsClient, keyName),
                getValue(secretsClient, certName));
        secretsClient.close();
        LOG.debug(
                "FHIRService:: getSecretsFromAWSSecretManager  - Get Secrets Client Manager for region : {} END for interaction id: {}",
                Region.US_EAST_1);
        return keyDetails;
    }

    public static String getValue(SecretsManagerClient secretsClient, String secretName) {
        LOG.debug("FHIRService:: getValue  - Get Value of secret with name  : {} -BEGIN", secretName);
        String secret = null;
        try {
            GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
            secret = valueResponse.secretString();
            LOG.info("FHIRService:: getValue  - Fetched value of secret with name  : {}  value  is null : {} -END",
                    secretName, secret == null ? "true" : "false");
        } catch (SecretsManagerException e) {
            LOG.error("ERROR:: FHIRService:: getValue  - Get Value of secret with name  : {} - FAILED with error "
                    + e.awsErrorDetails().errorMessage(), e);
        } catch (Exception e) {
            LOG.error("ERROR:: FHIRService:: getValue  - Get Value of secret with name  : {} - FAILED with error ",
                    e);
        }
        LOG.info("FHIRService:: getValue  - Get Value of secret with name  : {} ", secretName);
        return secret;
    }

    private void filter(ClientRequest clientRequest,
            Map<String, String> requestParameters,
            org.jooq.Configuration jooqCfg,
            String provenance,
            String tenantId,
            String payload,
            Map<String, Object> bundlePayloadWithDisposition,
            String groupInteractionId, String masterInteractionId,
            String sourceType, String requestUriToBeOverriden, String interactionId) {

        LOG.debug("FHIRService:: sendToScoringEngine Filter request before post - BEGIN interaction id: {}",
                interactionId);
        final var requestURI = StringUtils.isNotEmpty(requestUriToBeOverriden) ? requestUriToBeOverriden
                : requestParameters.get(Constants.REQUEST_URI);
        StringBuilder requestBuilder = new StringBuilder()
                .append(clientRequest.method().name()).append(" ")
                .append(clientRequest.url()).append(" HTTP/1.1").append("\n");

        clientRequest.headers().forEach((name, values) -> values
                .forEach(value -> requestBuilder.append(name).append(": ").append(value).append("\n")));

        final var outboundHttpMessage = requestBuilder.toString();

        registerStateForward(jooqCfg, provenance, getBundleInteractionId(requestParameters, interactionId), requestURI,
                tenantId,
                Optional.ofNullable(bundlePayloadWithDisposition).orElse(new HashMap<>()),
                outboundHttpMessage,
                payload, groupInteractionId,
                masterInteractionId, sourceType);

        LOG.debug("FHIRService:: sendToScoringEngine Filter request before post - END interaction id: {}",
                interactionId);
    }

    private void sendPostRequest(WebClient webClient,
            String tenantId,
            Map<String, Object> bundlePayloadWithDisposition,
            String payload,
            String dataLakeApiContentType,
            String interactionId,
            org.jooq.Configuration jooqCfg,
            String provenance,
            String requestURI, String scoringEngineApiURL, String groupInteractionId,
            String masterInteractionId, String sourceType, WithApiKeyAuth apiKeyAuthDetails) {
        Span span = tracer.spanBuilder("FhirService.sendPostRequest").startSpan();
        try {
            LOG.debug(
                    "FHIRService:: sendToScoringEngine Post to scoring engine - BEGIN interaction id: {} tenantID :{}",
                    interactionId, tenantId);
            String apiClientKey = AWSUtil.getValue(apiKeyAuthDetails.apiKeySecretName());
            LOG.info(
                    "FHIRService:: nyec api client key retrieved  : {} from secret  {} - BEGIN interaction id: {} tenantID :{}",
                    apiClientKey == null ? "Api key is null" : "Api key is not null",
                    apiKeyAuthDetails.apiKeySecretName(), interactionId, tenantId);
            LOG.info(
                    "Header : {} value {}", apiKeyAuthDetails.apiKeyHeaderName(), apiClientKey);
            webClient.post()
                    .uri("?processingAgent=" + tenantId)
                    .body(BodyInserters.fromValue(null != bundlePayloadWithDisposition
                            ? bundlePayloadWithDisposition
                            : payload))
                    .header("Content-Type", Optional.ofNullable(dataLakeApiContentType)
                            .orElse(Constants.FHIR_CONTENT_TYPE_HEADER_VALUE))
                    .header(apiKeyAuthDetails.apiKeyHeaderName(), apiClientKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(response -> {
                        handleResponse(response, jooqCfg, interactionId, requestURI, tenantId,
                                provenance, scoringEngineApiURL, groupInteractionId,
                                masterInteractionId, sourceType);
                    }, error -> {
                        registerStateFailure(jooqCfg, scoringEngineApiURL, interactionId, error,
                                requestURI, tenantId, provenance, groupInteractionId,
                                masterInteractionId, sourceType);
                    });

            LOG.info("FHIRService:: sendToScoringEngine Post to scoring engine - END interaction id: {} tenantid: {}",
                    interactionId, tenantId);
        } finally {
            span.end();
        }
    }

    private void handleResponse(String response,
            org.jooq.Configuration jooqCfg,
            String interactionId,
            String requestURI,
            String tenantId,
            String provenance,
            String scoringEngineApiURL, String groupInteractionId, String masterInteractionId,
            String sourceType) {
        Span span = tracer.spanBuilder("FhirService.handleResponse").startSpan();
        try {
            LOG.debug("FHIRService:: handleResponse BEGIN for interaction id: {}", interactionId);

            try {
                LOG.debug("FHIRService:: handleResponse Response received for :{} interaction id: {}",
                        interactionId, response);
                final var responseMap = new ObjectMapper().readValue(response,
                        new TypeReference<Map<String, String>>() {
                });

                if ("Success".equalsIgnoreCase(responseMap.get("status"))) {
                    LOG.info("FHIRService:: handleResponse SUCCESS for interaction id: {}",
                            interactionId);
                    registerStateComplete(jooqCfg, interactionId, requestURI, tenantId, response,
                            provenance, groupInteractionId, masterInteractionId,
                            sourceType);
                } else {
                    LOG.warn("FHIRService:: handleResponse FAILURE for interaction id: {}",
                            interactionId);
                    registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, response,
                            provenance, groupInteractionId, masterInteractionId,
                            sourceType);
                }
            } catch (Exception e) {
                LOG.error("FHIRService:: handleResponse unexpected error for interaction id : {}, response: {}",
                        interactionId, response, e);
                registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, e.getMessage(),
                        provenance, groupInteractionId, masterInteractionId, sourceType);
            }
            LOG.info("FHIRService:: handleResponse END for interaction id: {}", interactionId);
        } finally {
            span.end();
        }
    }

    private void handleError(Map<String, Object> validationPayloadWithDisposition,
            Exception e,
            Map<String, String> requestParameters, String interactionId) {

        validationPayloadWithDisposition.put("exception", e.toString());
        LOG.error(
                "ERROR:: FHIRService:: sendToScoringEngine Exception while sending to scoring engine payload with interaction id: {}",
                getBundleInteractionId(requestParameters, interactionId), e);
    }

    private Map<String, Object> preparePayload(Map<String, String> requestParameters, String bundlePayload,
            Map<String, Object> payloadWithDisposition, String interactionId) {
        LOG.debug("FHIRService:: addValidationResultToPayload BEGIN for interaction id : {}", interactionId);

        Map<String, Object> resultMap = null;

        try {
            Map<String, Object> extractedOutcome = Optional
                    .ofNullable(extractIssueAndDisposition(interactionId, payloadWithDisposition))
                    .filter(outcome -> !outcome.isEmpty())
                    .orElseGet(() -> {
                        LOG.warn(
                                "FHIRService:: resource type operation outcome or issues or techByDisposition is missing or empty for interaction id : {}",
                                interactionId);
                        return null;
                    });

            if (extractedOutcome == null) {
                LOG.warn("FHIRService:: extractedOutcome is null for interaction id : {}",
                        interactionId);
                return payloadWithDisposition;
            }
            Map<String, Object> bundleMap = Optional
                    .ofNullable(Configuration.objectMapper.readValue(bundlePayload,
                            new TypeReference<Map<String, Object>>() {
                    }))
                    .filter(map -> !map.isEmpty())
                    .orElseGet(() -> {
                        LOG.warn(
                                "FHIRService:: bundleMap is missing or empty after parsing bundlePayload for interaction id : {}",
                                interactionId);
                        return null;
                    });

            if (bundleMap == null) {
                LOG.warn("FHIRService:: bundleMap is null for interaction id : {}", interactionId);
                return payloadWithDisposition;
            }
            resultMap = appendToBundlePayload(interactionId, bundleMap, extractedOutcome);
            LOG.info(
                    "FHIRService:: addValidationResultToPayload END - Validation results added to Bundle payload for interaction id : {}",
                    interactionId);
            return resultMap;
        } catch (Exception ex) {
            LOG.error(
                    "ERROR :: FHIRService:: addValidationResultToPayload encountered an exception for interaction id : {}",
                    interactionId, ex);
        }
        LOG.info(
                "FHIRService:: addValidationResultToPayload END - Validation results not added - returning original payload for interaction id : {}",
                interactionId);
        return payloadWithDisposition;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> extractIssueAndDisposition(String interactionId,
            Map<String, Object> operationOutcomePayload) {
        LOG.debug("FHIRService:: extractResourceTypeAndDisposition BEGIN for interaction id : {}",
                interactionId);

        if (operationOutcomePayload == null) {
            LOG.warn("FHIRService:: operationOutcomePayload is null for interaction id : {}",
                    interactionId);
            return null;
        }

        return Optional.ofNullable(operationOutcomePayload.get("OperationOutcome"))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .flatMap(operationOutcomeMap -> {
                    List<?> validationResults = (List<?>) operationOutcomeMap
                            .get("validationResults");
                    if (validationResults == null || validationResults.isEmpty()) {
                        return Optional.empty();
                    }

                    // Extract the first validationResult
                    Map<String, Object> validationResult = (Map<String, Object>) validationResults
                            .get(0);

                    // Navigate to operationOutcome.issue
                    Map<String, Object> operationOutcome = (Map<String, Object>) validationResult
                            .get("operationOutcome");
                    List<?> issues = operationOutcome != null
                            ? (List<?>) operationOutcome.get("issue")
                            : null;

                    // Prepare the result
                    Map<String, Object> result = new HashMap<>();
                    result.put("resourceType", operationOutcomeMap.get("resourceType"));
                    result.put("issue", issues);

                    // Add techByDesignDisposition if available
                    List<?> techByDesignDisposition = (List<?>) operationOutcomeMap
                            .get("techByDesignDisposition");
                    if (techByDesignDisposition != null && !techByDesignDisposition.isEmpty()) {
                        result.put("techByDesignDisposition", techByDesignDisposition);
                    }

                    return Optional.of(result);
                })
                .orElseGet(() -> {
                    LOG.warn("FHIRService:: Missing required fields in operationOutcome for interaction id : {}",
                            interactionId);
                    return null;
                });
    }

    private Map<String, Object> appendToBundlePayload(String interactionId, Map<String, Object> payload,
            Map<String, Object> extractedOutcome) {
        LOG.debug("FHIRService:: appendToBundlePayload BEGIN for interaction id : {}", interactionId);
        if (payload == null) {
            LOG.warn("FHIRService:: payload is null for interaction id : {}", interactionId);
            return payload;
        }

        if (extractedOutcome == null || extractedOutcome.isEmpty()) {
            LOG.warn("FHIRService:: extractedOutcome is null or empty for interaction id : {}",
                    interactionId);
            return payload; // Return the original payload if no new outcome to append
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = Optional.ofNullable(payload.get("entry"))
                .filter(List.class::isInstance)
                .map(entry -> (List<Map<String, Object>>) entry)
                .orElseGet(() -> {
                    LOG.warn("FHIRService:: 'entry' field is missing or invalid for interaction id : {}",
                            interactionId);
                    return new ArrayList<>(); // if no entries in payload create new
                });

        final Map<String, Object> newEntry = Map.of("resource", extractedOutcome);
        entries.add(newEntry);

        final Map<String, Object> finalPayload = new HashMap<>(payload);
        finalPayload.put("entry", List.copyOf(entries));
        LOG.debug("FHIRService:: appendToBundlePayload END for interaction id : {}", interactionId);
        return finalPayload;
    }

    private void registerStateForward(org.jooq.Configuration jooqCfg, String provenance,
            String bundleAsyncInteractionId, String requestURI,
            String tenantId,
            Map<String, Object> payloadWithDisposition,
            String outboundHttpMessage,
            String payload,
            String groupInteractionId, String masterInteractionId, String sourceType) {
        Span span = tracer.spanBuilder("FHIRService.registerStateForward").startSpan();
        try {
            LOG.info("REGISTER State Forward : BEGIN for inteaction id  : {} tenant id : {}",
                    bundleAsyncInteractionId, tenantId);
            final var forwardedAt = OffsetDateTime.now();
            final var initRIHR = new RegisterInteractionHttpRequest();
            try {
                // TODO -check the need of includeIncomingPayloadInDB and add this later if
                // needed
                // payloadWithDisposition.put("outboundHttpMessage", outboundHttpMessage);
                // + "\n" + (includeIncomingPayloadInDB
                // ? payload
                // : "The incoming FHIR payload was not stored (to save space).\nThis is not an
                // error or warning just an FYI - if you'd like to see the incoming FHIR payload
                // `?include-incoming-payload-in-db=true` to request payload storage for each
                // request that you'd like to store."));
                initRIHR.setInteractionId(bundleAsyncInteractionId);
                initRIHR.setGroupHubInteractionId(groupInteractionId);
                initRIHR.setSourceHubInteractionId(masterInteractionId);
                initRIHR.setInteractionKey(requestURI);
                initRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                        Map.of("nature", "Forward HTTP Request", "tenant_id",
                                tenantId)));
                initRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                initRIHR.setPayload((JsonNode) Configuration.objectMapper
                        .valueToTree(payloadWithDisposition));
                initRIHR.setFromState("DISPOSITION");
                initRIHR.setToState("FORWARD");
                initRIHR.setSourceType(sourceType);
                initRIHR.setCreatedAt(forwardedAt); // don't let DB set this, use app
                // time
                initRIHR.setCreatedBy(FHIRService.class.getName());
                initRIHR.setProvenance(provenance);
                final var start = Instant.now();
                final var execResult = initRIHR.execute(jooqCfg);
                final var end = Instant.now();
                LOG.info(
                        "REGISTER State Forward : END for interaction id : {} tenant id : {} .Time taken : {} milliseconds"
                        + execResult,
                        bundleAsyncInteractionId, tenantId,
                        Duration.between(start, end).toMillis());
            } catch (Exception e) {
                LOG.error("ERROR:: REGISTER State Forward CALL for interaction id : {} tenant id : {}"
                        + initRIHR.getName() + " initRIHR error", bundleAsyncInteractionId,
                        tenantId,
                        e);
            }
        } finally {
            span.end();
        }
    }

    private void registerStateComplete(org.jooq.Configuration jooqCfg, String bundleAsyncInteractionId,
            String requestURI, String tenantId,
            String response, String provenance, String groupInteractionId, String masterInteractionId,
            String sourceType) {
        Span span = tracer.spanBuilder("FHIRService.registerStateComplete").startSpan();
        try {
            LOG.info("REGISTER State Complete : BEGIN for interaction id :  {} tenant id : {}",
                    bundleAsyncInteractionId, tenantId);
            final var forwardRIHR = new RegisterInteractionHttpRequest();
            try {
                forwardRIHR.setInteractionId(bundleAsyncInteractionId);
                forwardRIHR.setSourceHubInteractionId(masterInteractionId);
                forwardRIHR.setGroupHubInteractionId(groupInteractionId);
                forwardRIHR.setInteractionKey(requestURI);
                forwardRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                        Map.of("nature", "Forwarded HTTP Response",
                                "tenant_id", tenantId)));
                forwardRIHR.setContentType(
                        MimeTypeUtils.APPLICATION_JSON_VALUE);
                try {
                    // expecting a JSON payload from the server
                    forwardRIHR.setPayload(Configuration.objectMapper
                            .readTree(response));
                } catch (JsonProcessingException jpe) {
                    // in case the payload is not JSON store the string
                    forwardRIHR.setPayload((JsonNode) Configuration.objectMapper
                            .valueToTree(response));
                }
                forwardRIHR.setFromState("FORWARD");
                forwardRIHR.setToState("COMPLETE");
                forwardRIHR.setSourceType(sourceType);
                forwardRIHR.setCreatedAt(OffsetDateTime.now()); // don't let DB
                forwardRIHR.setCreatedBy(FHIRService.class.getName());
                forwardRIHR.setProvenance(provenance);
                final var start = Instant.now();
                final var execResult = forwardRIHR.execute(jooqCfg);
                final var end = Instant.now();
                LOG.info(
                        "REGISTER State Complete : END for interaction id : {} tenant id : {} .Time Taken : {} milliseconds"
                        + execResult,
                        bundleAsyncInteractionId, tenantId,
                        Duration.between(start, end).toMillis());
            } catch (Exception e) {
                LOG.error("ERROR:: REGISTER State Complete CALL for interaction id : {} tenant id : {} "
                        + forwardRIHR.getName()
                        + " forwardRIHR error", bundleAsyncInteractionId, tenantId, e);
            }
        } finally {
            span.end();
        }
    }

    private void registerStateFailed(org.jooq.Configuration jooqCfg, String bundleAsyncInteractionId,
            String requestURI, String tenantId,
            String response, String provenance, String groupInteractionId, String masterInteractionId,
            String sourceType) {
        Span span = tracer.spanBuilder("FHIRService.registerStateFailed").startSpan();
        try {
            LOG.info("REGISTER State Fail : BEGIN for interaction id :  {} tenant id : {}",
                    bundleAsyncInteractionId, tenantId);
            final var forwardRIHR = new RegisterInteractionHttpRequest();
            try {
                forwardRIHR.setInteractionId(bundleAsyncInteractionId);
                forwardRIHR.setInteractionKey(requestURI);
                forwardRIHR.setGroupHubInteractionId(groupInteractionId);
                forwardRIHR.setSourceHubInteractionId(masterInteractionId);
                forwardRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                        Map.of("nature", "Forwarded HTTP Response Error",
                                "tenant_id", tenantId)));
                forwardRIHR.setContentType(
                        MimeTypeUtils.APPLICATION_JSON_VALUE);
                try {
                    // expecting a JSON payload from the server
                    forwardRIHR.setPayload(Configuration.objectMapper
                            .readTree(response));
                } catch (JsonProcessingException jpe) {
                    // in case the payload is not JSON store the string
                    forwardRIHR.setPayload((JsonNode) Configuration.objectMapper
                            .valueToTree(response));
                }
                forwardRIHR.setFromState("FORWARD");
                forwardRIHR.setToState("FAIL");
                forwardRIHR.setSourceType(sourceType);
                forwardRIHR.setCreatedAt(OffsetDateTime.now()); // don't let DB
                // set this, use
                // app time
                forwardRIHR.setCreatedBy(FHIRService.class.getName());
                forwardRIHR.setProvenance(provenance);
                final var start = Instant.now();
                final var execResult = forwardRIHR.execute(jooqCfg);
                final var end = Instant.now();
                LOG.info(
                        "REGISTER State Fail : END for interaction id : {} tenant id : {} .Time Taken : {} milliseconds"
                        + execResult,
                        bundleAsyncInteractionId, tenantId,
                        Duration.between(start, end).toMillis());
            } catch (Exception e) {
                LOG.error("ERROR:: REGISTER State Fail CALL for interaction id : {} tenant id : {} "
                        + forwardRIHR.getName()
                        + " forwardRIHR error", bundleAsyncInteractionId, tenantId, e);
            }
        } finally {
            span.end();
        }
    }

    private void registerStateFailure(org.jooq.Configuration jooqCfg, String dataLakeApiBaseURL,
            String bundleAsyncInteractionId, Throwable error,
            String requestURI, String tenantId,
            String provenance, String groupInteractionId, String masterInteractionId, String sourceType) {
        Span span = tracer.spanBuilder("FhirService.registerStateFailure").startSpan();
        try {
            LOG.error(
                    "Register State Failure - Exception while sending FHIR payload to datalake URL {} for interaction id {}",
                    dataLakeApiBaseURL, bundleAsyncInteractionId, error);
            final var errorRIHR = new RegisterInteractionHttpRequest();
            try {
                errorRIHR.setInteractionId(bundleAsyncInteractionId);
                errorRIHR.setGroupHubInteractionId(groupInteractionId);
                errorRIHR.setSourceHubInteractionId(masterInteractionId);
                errorRIHR.setInteractionKey(requestURI);
                errorRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                        Map.of("nature", "Forwarded HTTP Response Error",
                                "tenant_id", tenantId)));
                errorRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                errorRIHR.setSourceType(sourceType);
                final var rootCauseThrowable = NestedExceptionUtils
                        .getRootCause(error);
                final var rootCause = rootCauseThrowable != null
                        ? rootCauseThrowable.toString()
                        : "null";
                final var mostSpecificCause = NestedExceptionUtils
                        .getMostSpecificCause(error).toString();
                final var errorMap = new HashMap<String, Object>() {
                    {
                        put("dataLakeApiBaseURL", dataLakeApiBaseURL);
                        put("error", error.toString());
                        put("message", error.getMessage());
                        put("rootCause", rootCause);
                        put("mostSpecificCause", mostSpecificCause);
                        put("tenantId", tenantId);
                    }
                };
                if (error instanceof final WebClientResponseException webClientResponseException) {
                    String responseBody = webClientResponseException
                            .getResponseBodyAsString();
                    errorMap.put("responseBody", responseBody);
                    String bundleId = "";
                    JsonNode rootNode = Configuration.objectMapper
                            .readTree(responseBody);
                    JsonNode bundleIdNode = rootNode.path("bundle_id"); // Adjust
                    // this
                    // path
                    // based
                    // on
                    // actual
                    if (!bundleIdNode.isMissingNode()) {
                        bundleId = bundleIdNode.asText();
                    }
                    LOG.error(
                            "Exception while sending FHIR payload to datalake URL {} for interaction id {} bundle id {} response from datalake {}",
                            dataLakeApiBaseURL,
                            bundleAsyncInteractionId, bundleId,
                            responseBody);
                    errorMap.put("statusCode", webClientResponseException
                            .getStatusCode().value());
                    final var responseHeaders = webClientResponseException
                            .getHeaders()
                            .entrySet()
                            .stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> String.join(
                                            ",",
                                            entry.getValue())));
                    errorMap.put("headers", responseHeaders);
                    errorMap.put("statusText", webClientResponseException
                            .getStatusText());
                }
                errorRIHR.setPayload((JsonNode) Configuration.objectMapper
                        .valueToTree(errorMap));
                errorRIHR.setFromState("FORWARD");
                errorRIHR.setToState("FAIL");
                errorRIHR.setCreatedAt(OffsetDateTime.now()); // don't let DB set this, use app time
                errorRIHR.setCreatedBy(FHIRService.class.getName());
                errorRIHR.setProvenance(provenance);
                final var start = Instant.now();
                final var execResult = errorRIHR.execute(jooqCfg);
                final var end = Instant.now();
                LOG.error("Register State Failure - END for interaction id : {} tenant id : {} forwardRIHR execResult"
                        + execResult + ". Time Taken : {} milliseconds ",
                        bundleAsyncInteractionId,
                        tenantId, Duration.between(start, end).toMillis());
            } catch (Exception e) {
                LOG.error("ERROR :: Register State Failure - for interaction id : {} tenant id : {} CALL "
                        + errorRIHR.getName() + " errorRIHR error", bundleAsyncInteractionId,
                        tenantId,
                        e);
            }
        } finally {
            span.end();
        }
    }

    private String getBundleInteractionId(Map<String, String> requestParameters, String coRrelationId) {
        if (StringUtils.isNotEmpty(coRrelationId)) {
            return coRrelationId;
        }
        return requestParameters.get(Constants.INTERACTION_ID);
    }

    // private String getBaseUrl(HttpServletRequest request) {
    // return Helpers.getBaseUrl(request);
    // }
    public enum MTlsStrategy {
        NO_MTLS("no-mTls"),
        AWS_SECRETS("aws-secrets"),
        MTLS_RESOURCES("mTlsResources"),
        POST_STDOUT_PAYLOAD_TO_NYEC_DATA_LAKE_EXTERNAL("post-stdin-payload-to-nyec-datalake-external"),
        WITH_API_KEY("with-api-key-auth");
        // AWS_SECRETS_TEMP_FILE("aws-secrets-temp-file"),
        // AWS_SECRETS_TEMP_WITHOUT_HASH("aws-secrets-without-hash"),
        // AWS_SECRETS_TEMP_WITHOUT_OPENSSL("aws-secrets-without-openssl"),
        // AWS_SECRETS_TEMP_FILE_WITHOUT_HASH("aws-secrets-temp-file-without-hash"),
        // AWS_SECRETS_TEMP_FILE_WITHOUT_OPENSSL("aws-secrets-temp-file-without-openssl"),
        // AWS_SECRETS_TEMP_FILE_WITHOUT_OPENSSLANDHASH("aws-secrets-temp-file-without-opensslandhash");

        private final String value;

        MTlsStrategy(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static String getAllValues() {
            return Arrays.stream(MTlsStrategy.values())
                    .map(MTlsStrategy::getValue)
                    .collect(Collectors.joining(", "));
        }

        public static MTlsStrategy fromString(String value) {
            for (MTlsStrategy strategy : MTlsStrategy.values()) {
                if (strategy.value.equals(value)) {
                    return strategy;
                }
            }
            throw new IllegalArgumentException("No enum constant for value: " + value);
        }
    }

    public record KeyDetails(String key, String cert) {

    }

    public record PostToNyecExternalResponse(boolean completed, String processOutput, String errorOutput) {

    }

    public enum TechByDesignDisposition {
        ACCEPT("accept"),
        REJECT("reject"),
        DISCARD("discard");

        private final String action;

        TechByDesignDisposition(String action) {
            this.action = action;
        }

        public String getAction() {
            return action;
        }
    }

}
