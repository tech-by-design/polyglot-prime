package org.techbd.service.fhir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.UnexpectedException;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.CoreAppConfig;
import org.techbd.config.CoreAppConfig.DefaultDataLakeApiAuthn;
import org.techbd.config.CoreAppConfig.MTlsAwsSecrets;
import org.techbd.config.CoreAppConfig.MTlsResources;
import org.techbd.config.CoreAppConfig.PostStdinPayloadToNyecDataLakeExternal;
import org.techbd.config.CoreAppConfig.WithApiKeyAuth;
import org.techbd.config.CoreUdiPrimeJpaConfig;
import org.techbd.config.Helpers;
import org.techbd.config.Interactions;
import org.techbd.config.Nature;
import org.techbd.config.SourceType;
import org.techbd.config.State;
import org.techbd.exceptions.ErrorCode;
import org.techbd.exceptions.JsonValidationException;
import org.techbd.service.dataledger.CoreDataLedgerApiClient;
import org.techbd.service.dataledger.CoreDataLedgerApiClient.DataLedgerPayload;
import org.techbd.service.fhir.engine.OrchestrationEngine;
import org.techbd.service.fhir.engine.OrchestrationEngine.Device;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionFhirRequest;
import org.techbd.util.AWSUtil;
import org.techbd.util.fhir.CoreFHIRUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import io.micrometer.common.util.StringUtils;
import io.netty.handler.ssl.SslContextBuilder;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

@Service

@Getter
@Setter
public class FHIRService {
    private static final Logger LOG = LoggerFactory.getLogger(FHIRService.class.getName());
    private final CoreAppConfig coreAppConfig;
	private final CoreDataLedgerApiClient coreDataLedgerApiClient;
    private final OrchestrationEngine engine;
	private final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig;
	private Tracer tracer;

	public FHIRService(CoreAppConfig coreAppConfig, CoreDataLedgerApiClient coreDataLedgerApiClient,OrchestrationEngine engine,
	final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig) {
		this.coreAppConfig = coreAppConfig;
		this.coreDataLedgerApiClient = coreDataLedgerApiClient;
		this.tracer = GlobalOpenTelemetry.get().getTracer("FHIRService");
		this.engine = engine;
		this.coreUdiPrimeJpaConfig = coreUdiPrimeJpaConfig;
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
    public Object processBundle(final @RequestBody @Nonnull String payload, final Map<String, Object> requestParameters,
            final Map<String, Object> responseParameters)
            throws IOException {
        final Span span = tracer.spanBuilder("FHIRService.processBundle").startSpan();
        try {
            final var start = Instant.now();
			String interactionId = (String)requestParameters.get(Constants.INTERACTION_ID);
			final String tenantId = (String)requestParameters.get(Constants.TENANT_ID);
			final String source = (String)requestParameters.get(Constants.SOURCE_TYPE);
			String dataLakeApiContentType = (String)requestParameters.get(Constants.DATA_LAKE_API_CONTENT_TYPE);
			final String customDataLakeApi = (String)requestParameters.get(Constants.CUSTOM_DATA_LAKE_API);
			final String healthCheck = (String)requestParameters.get(Constants.HEALTH_CHECK);
			final String provenance = (String)requestParameters.get(Constants.PROVENANCE);
			final String mtlsStrategy = (String)requestParameters.get(Constants.MTLS_STRATEGY);
			final String groupInteractionId = (String)requestParameters.get(Constants.GROUP_INTERACTION_ID);
			final String masterInteractionId = (String)requestParameters.get(Constants.MASTER_INTERACTION_ID);
			final String requestUriToBeOverriden = (String)requestParameters.get(Constants.OVERRIDE_REQUEST_URI);
			final String coRrelationId = (String)requestParameters.get(Constants.CORRELATION_ID);
			final String requestUri = (String)requestParameters.get(Constants.REQUEST_URI);
			if (requestParameters.get(Constants.CORRELATION_ID) != null) {
				interactionId = (String)requestParameters.get(Constants.CORRELATION_ID);
			}
            if (tenantId == null) {
                throw new IllegalArgumentException("Tenant ID must be provided in the request headers.");
            }
            if (null == interactionId) {
                throw new IllegalArgumentException("Interaction ID must be provided in the request parameters.");
            }
				final String bundleId = CoreFHIRUtil.extractBundleId(payload, tenantId);
			if (!SourceType.CSV.name().equalsIgnoreCase(source)
					&& !SourceType.CCDA.name().equalsIgnoreCase(source)) {
				DataLedgerPayload dataLedgerPayload = null;
				if (StringUtils.isNotEmpty(bundleId)) {
					dataLedgerPayload = DataLedgerPayload.create(CoreDataLedgerApiClient.Actor.TECHBD.getValue(),
							CoreDataLedgerApiClient.Action.RECEIVED.getValue(), CoreDataLedgerApiClient.Actor.TECHBD.getValue(),
							bundleId);
				} else {
					dataLedgerPayload = DataLedgerPayload.create(CoreDataLedgerApiClient.Actor.TECHBD.getValue(),
							CoreDataLedgerApiClient.Action.RECEIVED.getValue(), CoreDataLedgerApiClient.Actor.TECHBD.getValue(),
							interactionId);
				}
				final var dataLedgerProvenance = "%s.processBundle".formatted(FHIRService.class.getName());
				coreDataLedgerApiClient.processRequest(dataLedgerPayload, interactionId, dataLedgerProvenance,
						SourceType.FHIR.name(), null);
			}
            LOG.info("Bundle processing start at {} for interaction id {}.", interactionId);
			final var dslContext = coreUdiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
			if (!"true".equalsIgnoreCase(healthCheck != null ? healthCheck.trim() : null)) {
				registerOriginalPayload(jooqCfg, requestParameters,
						payload, interactionId, groupInteractionId, masterInteractionId,
						source, requestUriToBeOverriden, coRrelationId);
			}
			Map<String, Object> payloadWithDisposition = null;			
            try {
                validateJson(payload, interactionId);
                validateBundleProfileUrl(payload, interactionId);
                if (null == requestParameters.get(Constants.DATA_LAKE_API_CONTENT_TYPE)) {
                    dataLakeApiContentType = MediaType.APPLICATION_JSON_VALUE;
                }

                                final Map<String, Object> immediateResult = validate(requestParameters, payload, interactionId, provenance,
                        source);
                                               final Map<String, Object> result = Map.of("OperationOutcome", immediateResult);
				if (!"true".equalsIgnoreCase(healthCheck != null ? healthCheck.trim() : null)) {
					payloadWithDisposition = registerValidationResults(jooqCfg, requestParameters,
							result, interactionId, groupInteractionId, masterInteractionId,
							source, requestUriToBeOverriden);
				}
                if (StringUtils.isNotEmpty(requestUri)
                        && (requestUri.equals("/Bundle/$validate") || requestUri.equals("/Bundle/$validate/"))) {
                    return result;
                }

                if ("true".equalsIgnoreCase(healthCheck != null ? healthCheck.trim() : null)) {
                    return result;
                }

                if (isActionDiscard(payloadWithDisposition)) {
                    LOG.info("Action discard detected, returning payloadWithDisposition"); // TODO-to be removed
                    return payloadWithDisposition;
                }
                if (null == payloadWithDisposition) {
                    LOG.warn(
                            "FHIRService:: ERROR:: Disposition payload is not available.Send Bundle payload to scoring engine for interaction id {}.",
                            interactionId);
                    sendToScoringEngine(jooqCfg, requestParameters,customDataLakeApi, dataLakeApiContentType,
                            tenantId, payload,
                            provenance, null,
                            mtlsStrategy,
                            interactionId, groupInteractionId, masterInteractionId,
                            source, requestUriToBeOverriden, coRrelationId,bundleId);
                    final Instant end = Instant.now();
                    final Duration timeElapsed = Duration.between(start, end);
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
                            masterInteractionId, source, requestUriToBeOverriden, coRrelationId,bundleId);
                    final Instant end = Instant.now();
                    final Duration timeElapsed = Duration.between(start, end);
                    LOG.info("Bundle processing end for interaction id: {} Time Taken : {}  milliseconds",
                            interactionId, timeElapsed.toMillis());
                    return payloadWithDisposition;
                }
            } catch (final JsonValidationException ex) {
				payloadWithDisposition = registerValidationResults(jooqCfg, requestParameters,
						buildOperationOutcome(ex, interactionId), interactionId, groupInteractionId, masterInteractionId,
						source, requestUriToBeOverriden);
                LOG.info("Exception occurred: {} while processing bundle for interaction id :{} ", ex.getMessage(),interactionId); 
            }

            final Instant end = Instant.now();
            final Duration timeElapsed = Duration.between(start, end);
            LOG.info("Bundle processing end for interaction id: {} Time Taken: {} milliseconds", interactionId,
                    timeElapsed.toMillis());
            return payloadWithDisposition;
        } finally {
            span.end();
        }
    }

	@SuppressWarnings("unchecked")
	public static boolean isActionDiscard(final Map<String, Object> payloadWithDisposition) {
		return Optional.ofNullable(payloadWithDisposition)
				.map(map -> (Map<String, Object>) map.get("OperationOutcome"))
				.map(outcome -> (List<Map<String, Object>>) outcome.get("techByDesignDisposition"))
				.flatMap(dispositions -> dispositions.stream()
						.map(disposition -> (String) disposition.get("action"))
						.filter(TechByDesignDisposition.DISCARD.action::equals)
						.findFirst())
				.isPresent();
	}

	public void validateJson(final String jsonString, final String interactionId) {
		final Span validateJsonSpan = tracer.spanBuilder("FHIRService.validateJson").startSpan();
		try {
			try {
				Configuration.objectMapper.readTree(jsonString);
			} catch (final Exception e) {
				throw new JsonValidationException(ErrorCode.INVALID_JSON);
			}
		} finally {
			validateJsonSpan.end();
		}

	}

	public void validateBundleProfileUrl(final String jsonString, final String interactionId) {
		final Span validateJsonSpan = tracer.spanBuilder("FHIRService.validateBundleProfileUrl").startSpan();
		try {
			JsonNode rootNode;
			try {
				rootNode = Configuration.objectMapper.readTree(jsonString);
				final JsonNode metaNode = rootNode.path("meta").path("profile");

				final List<String> profileList = Optional.ofNullable(metaNode)
						.filter(JsonNode::isArray)
						.map(node -> StreamSupport.stream(node.spliterator(), false)
								.map(JsonNode::asText)
								.collect(Collectors.toList()))
						.orElse(List.of());

				if (CollectionUtils.isEmpty(profileList)) {
					LOG.error("Bundle profile is not provided for interaction id: {}", interactionId);
					throw new JsonValidationException(ErrorCode.BUNDLE_PROFILE_URL_IS_NOT_PROVIDED);
				}

				final List<String> allowedProfileUrls = CoreFHIRUtil.getAllowedProfileUrls(coreAppConfig);
				if (profileList.stream().noneMatch(allowedProfileUrls::contains)) {
					LOG.error("Bundle profile URL provided is not valid for interaction id: {}", interactionId);
					throw new JsonValidationException(ErrorCode.INVALID_BUNDLE_PROFILE);
				}
			} catch (final JsonProcessingException e) {
				LOG.error("Json Processing exception while extracting profile url for interaction id :{}", e);
			}

		} finally {
			validateJsonSpan.end();
		}

	}
	

	public static Map<String, Object> buildOperationOutcome(final JsonValidationException ex,
															final String interactionId) {
		final var validationResult = Map.of(
				"valid", false,
				"issues", List.of(Map.of(
						"message", ex.getErrorCode() + ": " + ex.getMessage(),
						"severity", "FATAL")));

		final var immediateResult = Map.of(
				"resourceType", "OperationOutcome", 
				"bundleSessionId", interactionId,
				"validationResults", List.of(validationResult)
		);

		final Map<String, Object> result = Map.of("OperationOutcome", immediateResult);
		return result;
	}
	private void registerOriginalPayload(final org.jooq.Configuration jooqCfg,
			final Map<String, Object> requestParameters,
			final String payload,
			final String interactionId,
			final String groupInteractionId,
			final String masterInteractionId,
			final String sourceType,
			final String requestUriToBeOverriden,
			final String coRrelationId) throws IOException {
		final Span span = tracer.spanBuilder("FHIRService.registerOriginalPayload").startSpan();
		try {
			LOG.info(
					"FHIRService -  REGISTER Original Payload BEGIN  for interaction id: {}",interactionId);
			final var rihr = new RegisterInteractionFhirRequest();
			final var provenance = "%s.doFilterInternal".formatted(FHIRService.class.getName());
			final var start = Instant.now();
			JsonNode payloadJson;

			try {
				payloadJson = Configuration.objectMapper.readTree(payload);
			} catch (JsonProcessingException e) {
				LOG.error("Invalid JSON format. Storing raw payload. Error: {} for interactionID :{}", e.getMessage(), interactionId,e);
				payloadJson = TextNode.valueOf(payload);
			}
			prepareRequestBase(
					rihr,
					interactionId != null ? interactionId : UUID.randomUUID().toString(),
					groupInteractionId,
					masterInteractionId,
					sourceType,
					requestUriToBeOverriden,
					requestParameters,
					provenance,
					Nature.ORIGINAL_FHIR_PAYLOAD.getDescription(),
					payloadJson,State.NONE.name(),State.ACCEPT_FHIR_BUNDLE.name());
			rihr.setPAdditionalDetails((JsonNode) Configuration.objectMapper.valueToTree( Map.of("request", requestParameters)));
			if (requestParameters.get(Constants.ELABORATION) != null) {
				try {
					JsonNode elaborationNode = Configuration.objectMapper.readTree((String) requestParameters.get(Constants.ELABORATION));
					rihr.setPElaboration(elaborationNode);
				} catch (JsonProcessingException e) {
					LOG.error("Invalid elaboration JSON. Storing as string. Error: {} for interactionID :{}", e.getMessage(), interactionId, e);
				}
			}
			final int i = rihr.execute(jooqCfg);
			final var end = Instant.now();
			final JsonNode response = rihr.getReturnValue();
			final Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(response);
			LOG.info(
					"FHIRService - Time taken: {} ms for DB call to REGISTER Original Payload, interaction id: {}, error: {}, hub_nexus_interaction_id: {}",
					Duration.between(start, end).toMillis(),
					interactionId,
					responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
					responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"));
		} catch (final Exception e) {
			LOG.error("ERROR:: REGISTER Original Payload for interaction id: {}: {}",
					interactionId, e.getMessage(), e);
		} finally {
			span.end();
		}
	}

	private Map<String, Object> registerValidationResults(final org.jooq.Configuration jooqCfg,
			final Map<String, Object> requestParameters,
			final Map<String, Object> immediateResult,
			final String interactionId,
			final String groupInteractionId,
			final String masterInteractionId,
			final String sourceType,
			final String requestUriToBeOverriden) throws IOException {
		final Span span = tracer.spanBuilder("FHIRService.registerValidationResults").startSpan();
		try {
			LOG.info("FHIRService REGISTER Validation Results BEGIN  for interaction id: {}",interactionId);
			final var rihr = new RegisterInteractionFhirRequest();
			final var provenance = "%s.doFilterInternal".formatted(FHIRService.class.getName());
			final var start = Instant.now();
			prepareRequestBase(
					rihr,
					interactionId,
					groupInteractionId,
					masterInteractionId,
					sourceType,
					requestUriToBeOverriden,
					requestParameters,
					provenance,
					Nature.TECH_BY_DISPOSITION.getDescription(),
					Configuration.objectMapper.valueToTree(immediateResult),State.ACCEPT_FHIR_BUNDLE.name(),State.DISPOSITION.name());
			final int i = rihr.execute(jooqCfg);
			final var end = Instant.now();
			final JsonNode response = rihr.getReturnValue();
			final Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(response);

			LOG.info(
					"FHIRService - Time taken: {} ms for DB call to REGISTER Validation Results, interaction id: {}, error: {}, hub_nexus_interaction_id: {}",
					Duration.between(start, end).toMillis(),
					interactionId,
					responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
					responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"));
			JsonNode jsonNode = (JsonNode) responseAttributes.get(Constants.KEY_PAYLOAD);
			return Configuration.objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {});
		} catch (final Exception e) {
			LOG.error("ERROR:: REGISTER Validation Results for interaction id: {}: {}",
					interactionId, e.getMessage(), e);
			return null;
		} finally {
			span.end();
		}
	}

	private void prepareRequestBase(final RegisterInteractionFhirRequest rihr,
			final String interactionId,
			final String groupInteractionId,
			final String masterInteractionId,
			final String sourceType,
			final String requestUriToBeOverriden,
			final Map<String, Object> requestParameters,
			final String provenance,
			final String nature,
			final JsonNode payloadNode,String fromState,String toState) {
		rihr.setPInteractionId(interactionId);
		rihr.setPGroupHubInteractionId(groupInteractionId);
		rihr.setPSourceHubInteractionId(masterInteractionId);
		rihr.setPNature((JsonNode)Configuration.objectMapper.valueToTree(Map.of(
				"nature", nature,
				"tenant_id", requestParameters.getOrDefault(Constants.TENANT_ID, "N/A"))));
		rihr.setPContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
		rihr.setPInteractionKey(StringUtils.isNotEmpty(requestUriToBeOverriden)
				? requestUriToBeOverriden
				: (String) requestParameters.get(Constants.REQUEST_URI));
		rihr.setPPayload(payloadNode);
		rihr.setPCreatedBy(FHIRService.class.getName());
		rihr.setPSourceType(sourceType);
		rihr.setPProvenance(provenance);
		rihr.setPFromState(fromState);
		rihr.setPToState(toState);
		setUserDetails(rihr, requestParameters);
	}

	private void setUserDetails(RegisterInteractionFhirRequest rihr, Map<String, Object> requestParameters) {
		rihr.setPUserName(null == requestParameters.get(Constants.USER_NAME) ? Constants.DEFAULT_USER_NAME
				: (String) requestParameters.get(Constants.USER_NAME));
		rihr.setPUserId(null == requestParameters.get(Constants.USER_ID) ? Constants.DEFAULT_USER_ID
				: (String) requestParameters.get(Constants.USER_ID));
		rihr.setPUserSession(UUID.randomUUID().toString());
		rihr.setPUserRole(null == requestParameters.get(Constants.USER_ROLE) ? Constants.DEFAULT_USER_ROLE
				: (String) requestParameters.get(Constants.USER_ROLE));
	}

    protected static final void setActiveRequestTenant(final @NonNull HttpServletRequest request,
            final @NonNull Interactions.Tenant tenant) {
        request.setAttribute("activeHttpRequestTenant", tenant);
    }

	private Map<String, Object> validate(final Map<String,Object> requestParameters, final String payload,
            final String interactionId, final String provenance, final String sourceType) {
        final Span span = tracer.spanBuilder("FhirService.validate").startSpan();
		try {
			final var start = Instant.now();
			LOG.info("FHIRService  - Validate -BEGIN for interactionId: {} ", interactionId);
			final var igPackages = coreAppConfig.getIgPackages();
            var requestedIgVersion = (String) requestParameters.get(Constants.SHIN_NY_IG_VERSION);
            LOG.info("headerIgVersion : "+ requestedIgVersion);
			final var sessionBuilder = engine.session()
					.withSessionId(UUID.randomUUID().toString())
					.onDevice(Device.createDefault())
					.withInteractionId(interactionId)
					.withPayloads(List.of(payload))
					.withFhirProfileUrl(CoreFHIRUtil.getBundleProfileUrl())
					.withTracer(tracer)
					.withFhirIGPackages(igPackages)
                    .withRequestedIgVersion(requestedIgVersion)
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
								+ coreAppConfig.getOperationOutcomeHelpUrl(),
						"bundleSessionId", interactionId, // for tracking in
															// database, etc.
						"isAsync", true,
						"validationResults", session.getValidationResults(),
						"statusUrl","/Bundle/$status/"
								+ interactionId.toString(),
						"device", session.getDevice()));
				if (SourceType.CSV.name().equals(sourceType) && StringUtils.isNotEmpty(provenance)) {
					immediateResult.put("provenance",
							Configuration.objectMapper.readTree(provenance));
				}
				
				return immediateResult; // Return the validation results
			} catch (final Exception e) {
				// Log the error and create a failure response
				LOG.error("FHIRService - Validate - FAILED for interactionId: {}", interactionId, e);
				return Map.of(
						"resourceType", "OperationOutcome",
						"interactionId", interactionId,
						"error", "Validation failed: " + e.getMessage());
			} finally {
				// Ensure the session is cleared to avoid memory leaks
				engine.clear(session);
				final Instant end = Instant.now();
				final Duration timeElapsed = Duration.between(start, end);
				LOG.info("FHIRService  - Validate -END for interaction id: {} Time Taken : {}  milliseconds",
						interactionId, timeElapsed.toMillis());
			}
		} finally {
			span.end();
		}
	}

	private void sendToScoringEngine(final org.jooq.Configuration jooqCfg,
			final Map<String,Object> requestParameters,
			final String scoringEngineApiURL,
			final String dataLakeApiContentType,
			final String tenantId,
			final String payload,
			final String provenance,
			final Map<String, Object> validationPayloadWithDisposition, 
			final String mtlsStrategy, final String interactionId, final String groupInteractionId,
			final String masterInteractionId, final String sourceType, final String requestUriToBeOverriden, final String coRrelationId,final String bundleId) {
		final Span span = tracer.spanBuilder("FhirService.sentToScoringEngine").startSpan();
		try {
			LOG.info("FHIRService:: sendToScoringEngine BEGIN for interaction id: {} for", interactionId);

			try {
				Map<String, Object> bundlePayloadWithDisposition = null;
				LOG.debug("FHIRService:: sendToScoringEngine includeOperationOutcome : {} interaction id: {}",
						 interactionId);
				if ( null != validationPayloadWithDisposition) { // todo
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
						.orElse(coreAppConfig.getDefaultDatalakeApiUrl());
				final var defaultDatalakeApiAuthn = coreAppConfig.getDefaultDataLakeApiAuthn();

				if (null == defaultDatalakeApiAuthn) {
					LOG.info(
							"###### defaultDatalakeApiAuthn is not defined #######.Hence proceeding with post to scoring engine without mTls for interaction id :{}",
							interactionId);
					handleNoMtls(MTlsStrategy.NO_MTLS, interactionId, tenantId, dataLakeApiBaseURL,
							jooqCfg, requestParameters,
							bundlePayloadWithDisposition, payload, dataLakeApiContentType,
							provenance,  
                                                        groupInteractionId,
							masterInteractionId, sourceType, requestUriToBeOverriden,bundleId);
				} else {
					handleMTlsStrategy(defaultDatalakeApiAuthn, interactionId, tenantId,
							dataLakeApiBaseURL,
							jooqCfg, requestParameters, bundlePayloadWithDisposition,
							payload,
							dataLakeApiContentType, provenance, 
							mtlsStrategy, groupInteractionId, masterInteractionId,
							sourceType, requestUriToBeOverriden,bundleId);
				}

			} catch (

			final Exception e) {
				handleError(validationPayloadWithDisposition, e,  interactionId);
			} finally {
				LOG.info("FHIRService:: sendToScoringEngine END for interaction id: {}", interactionId);
			}
		} finally {
			span.end();
		}
	}

	public void handleMTlsStrategy(final DefaultDataLakeApiAuthn defaultDatalakeApiAuthn, final String interactionId,
			final String tenantId, final String dataLakeApiBaseURL,
			final org.jooq.Configuration jooqCfg, final Map<String,Object> requestParameters,
			final Map<String, Object> bundlePayloadWithDisposition, final String payload, final String dataLakeApiContentType,
			final String provenance,  
                        final String mtlsStrategyStr,
			final String groupInteractionId,
			final String masterInteractionId, final String sourceType, final String requestUriToBeOverriden,final String bundleId) {
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
		final String requestURI = StringUtils.isNotEmpty(requestUriToBeOverriden) ? requestUriToBeOverriden
				: (String) requestParameters.get(Constants.REQUEST_URI);
		switch (mTlsStrategy) {
			case AWS_SECRETS -> handleAwsSecrets(defaultDatalakeApiAuthn.mTlsAwsSecrets(), interactionId,
					tenantId, dataLakeApiBaseURL, dataLakeApiContentType,
					bundlePayloadWithDisposition, jooqCfg, provenance,
					requestURI, 
                                        payload,
					groupInteractionId, masterInteractionId, sourceType,bundleId,requestParameters);
			case POST_STDOUT_PAYLOAD_TO_NYEC_DATA_LAKE_EXTERNAL ->
				handlePostStdoutPayload(interactionId, tenantId, jooqCfg, dataLakeApiBaseURL,
						bundlePayloadWithDisposition,payload, provenance,
						defaultDatalakeApiAuthn.postStdinPayloadToNyecDataLakeExternal(),
						groupInteractionId, masterInteractionId, sourceType,
						requestUriToBeOverriden,requestParameters);
			case MTLS_RESOURCES ->
				handleMtlsResources(interactionId, tenantId, jooqCfg,
						bundlePayloadWithDisposition,payload, provenance, requestParameters,
						dataLakeApiContentType, dataLakeApiBaseURL,
						defaultDatalakeApiAuthn.mTlsResources(), groupInteractionId,
						masterInteractionId, sourceType, requestUriToBeOverriden, bundleId);
			case WITH_API_KEY ->
				handleApiKeyAuth(interactionId, tenantId, dataLakeApiBaseURL, 
				jooqCfg, requestParameters, bundlePayloadWithDisposition, payload, dataLakeApiContentType,
				provenance, groupInteractionId, masterInteractionId, sourceType, 
				requestUriToBeOverriden, defaultDatalakeApiAuthn.withApiKeyAuth(), bundleId);
			default ->
				handleNoMtls(mTlsStrategy, interactionId, tenantId, dataLakeApiBaseURL, jooqCfg,
						requestParameters,
						bundlePayloadWithDisposition, payload, dataLakeApiContentType,
						provenance, 
                                                groupInteractionId,
						masterInteractionId, sourceType, requestUriToBeOverriden,bundleId);
		}
	}

	private void handleMtlsResources(final String interactionId, final String tenantId, final org.jooq.Configuration jooqCfg,
			final Map<String, Object> bundlePayloadWithDisposition,
			final String payload, final String provenance,final Map<String,Object> requestParameters,final String dataLakeApiContentType,
			final String dataLakeApiBaseURL,
			final MTlsResources mTlsResources, final String groupInteractionId, final String masterInteractionId,
			final String sourceType, final String requestUriToBeOverriden,final String bundleId) {
		LOG.info("FHIRService:: handleMtlsResources BEGIN for interaction id: {} tenantid :{} scoring",
				interactionId,
				tenantId);
		final var requestURI = StringUtils.isNotEmpty(requestUriToBeOverriden) ? requestUriToBeOverriden
				: (String) requestParameters.get(Constants.REQUEST_URI);

		try {
			registerStateForward(jooqCfg, provenance, interactionId,
					requestURI, tenantId,
					Optional.ofNullable(bundlePayloadWithDisposition)
							.orElse(new HashMap<>()),
					null, 
                                         payload, groupInteractionId,
					masterInteractionId, sourceType);

			if (null == mTlsResources.mTlsKeyResourceName()) {
				LOG.error(
						"ERROR:: FHIRService:: handleMtlsResources Key location  `mTlsKeyResourceName` is not configured in application.yml for interaction id : {}  tenant id :{} ",
						interactionId, tenantId);
				throw new IllegalArgumentException(
						"Client key location `mTlsKeyResourceName` is not configured in application.yml");
			}
			if (null == mTlsResources.mTlsCertResourceName()) {
				LOG.error(
						"ERROR:: FHIRService:: handleMtlsResources Client certificate location `mTlsCertResourceName` not configured in application.yml  for interaction id : {} tenant id : {}",
						interactionId, tenantId);
				throw new IllegalArgumentException(
						"Client certificate location `mTlsCertResourceName` not configured in application.yml");
			}
			final String myClientKey = Files
					.readString(Paths.get(mTlsResources.mTlsKeyResourceName()));
			if (null == myClientKey) {
				LOG.error(
						"ERROR:: FHIRService:: handleMtlsResources Key not provided.Copy the key to file in location :{} for interaction id :{} tenant id :{} ",
						mTlsResources.mTlsKeyResourceName(), interactionId, tenantId);
				throw new IllegalArgumentException(
						"Client key not provided.Copy the key to file in location : "
								+ mTlsResources.mTlsKeyResourceName());
			}
			LOG.debug(
					"FHIRService:: handleMtlsResources Client key fetched successfully for interaction id: {} tenantid :{} ",
					interactionId,
					tenantId);
			final String myClientCert = Files.readString(Paths.get(mTlsResources.mTlsCertResourceName()));
			if (null == myClientCert) {
				LOG.error(
						"ERROR:: FHIRService:: handleMtlsResources Client certificate not provided.Copy the certificate to file in location :{}  for interaction id : {}  tenantId :{}",
						mTlsResources.mTlsCertResourceName(), interactionId, tenantId);
				throw new IllegalArgumentException(
						"Client certificate not provided.Copy the certificate to file in location : "
								+ mTlsResources.mTlsCertResourceName());
			}
			LOG.debug(
					"FHIRService:: handleMtlsResources Client cert fetched successfully for interaction id: {} tenantid :{} ",
					interactionId,
					tenantId);
			LOG.debug("FHIRService:: handleMtlsResources Get SSL Context -BEGIN for interaction id: {} tenantid:{}",
					interactionId, tenantId);

			final var sslContext = SslContextBuilder.forClient()
					.keyManager(new ByteArrayInputStream(myClientCert.getBytes()),
							new ByteArrayInputStream(myClientKey.getBytes()))
					.build();
			LOG.debug("FHIRService:: handleMtlsResources Get SSL Context -END for interaction id: {} tenantId:{}",
					interactionId, tenantId);
			LOG.debug("FHIRService:: handleMtlsResources Create HttpClient for interaction id: {} tenantID:{}",
					interactionId, tenantId);
			final HttpClient httpClient = HttpClient.create()
					.secure(sslSpec -> sslSpec.sslContext(sslContext));

			LOG.debug("FHIRService:: Create ReactorClientHttpConnector for interaction id: {} tenantId:{}",
					interactionId, tenantId);
			final ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
			LOG.debug(
					"FHIRService:: Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN with scoring Engine API URL : {} for interactionID :{} tenant Id:{} ",
					dataLakeApiBaseURL, interactionId, tenantId);
			final var webClient = WebClient.builder()
					.baseUrl(dataLakeApiBaseURL)
					.defaultHeader("Content-Type", dataLakeApiContentType)
					.clientConnector(connector)
					.build();
			LOG.debug(
					"FHIRService:: Build WebClient with MTLS Enabled ReactorClientHttpConnector -END with scoring Engine API URL : {} for interactionID :{} tenant Id:{} ",
					dataLakeApiBaseURL, interactionId, tenantId);
			LOG.debug(
					"FHIRService:: handleMtlsResources Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN \n"
							+
							"with scoring Engine API URL: {} \n" +
							"dataLakeApiContentType: {} \n" +
							"bundlePayloadWithDisposition: {} \n" +
							"for interactionID: {} \n" +
							"tenant Id: {}",
					dataLakeApiBaseURL,
					dataLakeApiContentType,
					bundlePayloadWithDisposition == null ? "Payload is null"
							: "Payload is not null",
					interactionId,
					tenantId);
			sendPostRequest(webClient, tenantId, bundlePayloadWithDisposition, payload,
					dataLakeApiContentType, interactionId,
					jooqCfg, provenance, (String) requestParameters.get(Constants.REQUEST_URI), dataLakeApiBaseURL,
					groupInteractionId, masterInteractionId, sourceType,bundleId,requestParameters);
			LOG.debug(
					"FHIRService:: handleMtlsResources Build WebClient with MTLS Enabled ReactorClientHttpConnector -END for interaction Id :{}",
					interactionId);
			LOG.info("FHIRService:: handleMtlsResources END for interaction id: {} tenantid :{} ",
					interactionId,
					tenantId);
		} catch (final Exception ex) {
			LOG.error(
					"ERROR:: handleMtlsResources Exception while posting to scoring engine with MTLS enabled for interactionId : {}",
					interactionId, ex);
			registerStateFailed(jooqCfg, interactionId,
					requestURI, tenantId, ex.getMessage(), provenance,
					groupInteractionId, masterInteractionId, sourceType,requestParameters);

		}
	}

	private void handleApiKeyAuth(final String interactionId, final String tenantId,
			final String dataLakeApiBaseURL,
			final org.jooq.Configuration jooqCfg, final Map<String,Object> requestParameters,
			final Map<String, Object> bundlePayloadWithDisposition, final String payload, final String dataLakeApiContentType,
			final String provenance, 
                         final String groupInteractionId,
			final String masterInteractionId, final String sourceType, final String requestUriToBeOverriden,final WithApiKeyAuth apiKeyAuthDetails,final String bundleId) {
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
		LOG.debug("FHIRService:: handleNoMtls Build WebClient with MTLS  Disabled -BEGIN \n"
				+
				"with scoring Engine API URL: {} \n" +
				"dataLakeApiContentType: {} \n" +
				"bundlePayloadWithDisposition: {} \n" +
				"for interactionID: {} \n" +
				"tenant Id: {}",
				dataLakeApiBaseURL,
				dataLakeApiContentType,
				bundlePayloadWithDisposition == null ? "Payload is null"
						: "Payload is not null",
				interactionId,
				tenantId);
		final var webClient = createWebClient(dataLakeApiBaseURL, jooqCfg, requestParameters,
				tenantId, payload,
				bundlePayloadWithDisposition, provenance, 
				interactionId, groupInteractionId, masterInteractionId, sourceType,
				requestUriToBeOverriden);
		LOG.debug("FHIRService:: createWebClient END for interaction id: {} tenant id :{} ", interactionId,
				tenantId);
		LOG.debug("FHIRService:: sendPostRequest BEGIN for interaction id: {} tenantid :{} ", interactionId,
				tenantId);
		sendPostRequestWithApiKey(webClient, tenantId, bundlePayloadWithDisposition, payload,
				dataLakeApiContentType, interactionId,
				jooqCfg, provenance,
				StringUtils.isNotEmpty(requestUriToBeOverriden) ? requestUriToBeOverriden
						: (String) requestParameters.get(Constants.REQUEST_URI),
				dataLakeApiBaseURL, groupInteractionId,
				masterInteractionId, sourceType,apiKeyAuthDetails,bundleId,requestParameters);
		LOG.debug("FHIRService:: sendPostRequest END for interaction id: {} tenantid :{} ", interactionId,
				tenantId);
	}
	private void handleNoMtls(final MTlsStrategy mTlsStrategy, final String interactionId, final String tenantId,
			final String dataLakeApiBaseURL,
			final org.jooq.Configuration jooqCfg, final Map<String,Object> requestParameters,
			final Map<String, Object> bundlePayloadWithDisposition, final String payload, final String dataLakeApiContentType,
			final String provenance, 
                         final String groupInteractionId,
			final String masterInteractionId, final String sourceType, final String requestUriToBeOverriden,final String bundleId) {
		if (!MTlsStrategy.NO_MTLS.value.equals(mTlsStrategy.value)) {
			LOG.info(
					"#########Invalid MTLS Strategy defined #############: Allowed values are {} .Hence proceeding with post to scoring engine without mTls for interaction id :{}",
					MTlsStrategy.getAllValues(), interactionId);
		}
		LOG.debug("FHIRService:: handleNoMtls Build WebClient with MTLS  Disabled -BEGIN \n"
				+
				"with scoring Engine API URL: {} \n" +
				"dataLakeApiContentType: {} \n" +
				"bundlePayloadWithDisposition: {} \n" +
				"for interactionID: {} \n" +
				"tenant Id: {}",
				dataLakeApiBaseURL,
				dataLakeApiContentType,
				bundlePayloadWithDisposition == null ? "Payload is null"
						: "Payload is not null",
				interactionId,
				tenantId);
		final var webClient = createWebClient(dataLakeApiBaseURL, jooqCfg, requestParameters,
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
						: (String) requestParameters.get(Constants.REQUEST_URI),
				dataLakeApiBaseURL, groupInteractionId,
				masterInteractionId, sourceType,bundleId,requestParameters);
		LOG.debug("FHIRService:: sendPostRequest END for interaction id: {} tenantid :{} ", interactionId,
				tenantId);
	}

	private void handleAwsSecrets(final MTlsAwsSecrets mTlsAwsSecrets, final String interactionId, final String tenantId,
			final String dataLakeApiBaseURL, final String dataLakeApiContentType,
			final Map<String, Object> bundlePayloadWithDisposition,
			final org.jooq.Configuration jooqCfg, final String provenance, final String requestURI,
			 
                        final String payload, final String groupInteractionId,
			final String masterInteractionId,
			final String sourceType,final String bundleId,Map<String,Object> requestParameters) {
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
			final KeyDetails keyDetails = getSecretsFromAWSSecretManager(mTlsAwsSecrets.mTlsKeySecretName(),
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

			final HttpClient httpClient = HttpClient.create()
					.secure(sslSpec -> sslSpec.sslContext(sslContext));
			LOG.debug("FHIRService :: handleAwsSecrets HttpClient created successfully  for interactionId : {}",
					interactionId);

			final ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
			LOG.debug(
					"FHIRService :: handleAwsSecrets ReactorClientHttpConnector created successfully  for interactionId : {}",
					interactionId);
			LOG.info(
					"FHIRService:: handleAwsSecrets Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN \n"
							+
							"with scoring Engine API URL: {} \n" +
							"dataLakeApiContentType: {} \n" +
							"bundlePayloadWithDisposition: {} \n" +
							"for interactionID: {} \n" +
							"tenant Id: {}",
					dataLakeApiBaseURL,
					dataLakeApiContentType,
					bundlePayloadWithDisposition == null ? "Payload is null"
							: "Payload is not null",
					interactionId,
					tenantId);
			final var webClient = WebClient.builder()
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
					masterInteractionId, sourceType,bundleId,requestParameters);
			LOG.debug("FHIRService:: handleAwsSecrets -sendPostRequest END for interaction id: {} tenantid :{} ",
					interactionId,
					tenantId);
			LOG.debug("FHIRService :: handleAwsSecrets Post to scoring engine -END for interactionId :{}",
					interactionId);
		} catch (final Exception ex) {
			LOG.error(
					"ERROR:: FHIRService :: handleAwsSecrets Post to scoring engine FAILED with error :{} for interactionId :{} tenantId:{}",
					ex.getMessage(),
					interactionId, tenantId, ex);
			registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, ex.getMessage(),
					provenance, groupInteractionId, masterInteractionId, sourceType, requestParameters);
		}
		LOG.info("FHIRService :: handleAwsSecrets -END for interactionId : {}",
				interactionId);
	}

	private void handlePostStdoutPayload(final String interactionId, final String tenantId, final org.jooq.Configuration jooqCfg,
			final String dataLakeApiBaseURL,
			final Map<String, Object> bundlePayloadWithDisposition,
                         final String payload, final String provenance,
			final PostStdinPayloadToNyecDataLakeExternal postStdinPayloadToNyecDataLakeExternal,
			final String groupInteractionId, final String masterInteractionId, final String sourceType,
			final String requestUriToBeOverriden,final Map<String,Object> requestParameters) {
		LOG.info("Proceed with posting payload via external process BEGIN forinteractionId : {}",
				interactionId);
		final var requestURI = StringUtils.isNotEmpty(requestUriToBeOverriden) ? requestUriToBeOverriden
				: (String) requestParameters.get(Constants.REQUEST_URI);

		try {
			registerStateForward(jooqCfg, provenance, interactionId,
					requestURI, tenantId,
					Optional.ofNullable(bundlePayloadWithDisposition)
							.orElse(new HashMap<>()),
					null, 
                                        payload, groupInteractionId,
					masterInteractionId, sourceType);
			final var postToNyecExternalResponse = postStdinPayloadToNyecDataLakeExternal(dataLakeApiBaseURL,
					tenantId, interactionId,
					bundlePayloadWithDisposition,
					postStdinPayloadToNyecDataLakeExternal);

			LOG.info("Create payload from postToNyecExternalResponse- BEGIN for interactionId : {}",
					interactionId);
			final var payloadJson = Map.of("completed",
					postToNyecExternalResponse.completed(),
					"processOutput", postToNyecExternalResponse.processOutput(),
					"errorOutput", postToNyecExternalResponse.errorOutput());
			final String responsePayload = Configuration.objectMapper
					.writeValueAsString(payloadJson);
			LOG.info("Create payload from postToNyecExternalResponse- END forinteractionId : {}",
					interactionId);
			if (postToNyecExternalResponse.completed() && null != postToNyecExternalResponse.processOutput()
					&& postToNyecExternalResponse.processOutput()
							.contains("{\"status\": \"Success\"")) {
				registerStateComplete(jooqCfg, interactionId,
						requestURI, tenantId, responsePayload,
						provenance, groupInteractionId, masterInteractionId, sourceType,requestParameters);
			} else {
				registerStateFailed(jooqCfg, interactionId,
						requestURI, tenantId, responsePayload,
						provenance, groupInteractionId, masterInteractionId, sourceType,requestParameters);
			}
		} catch (final Exception ex) {
			LOG.error("Exception while postStdinPayloadToNyecDataLakeExternal forinteractionId : {}",
					interactionId, ex);
			registerStateFailed(jooqCfg, interactionId,
					requestURI, tenantId, ex.getMessage(), provenance,
					groupInteractionId, masterInteractionId, sourceType, requestParameters);
		}
		LOG.info("Proceed with posting payload via external process END for interactionId : {}",
				interactionId);
	}

	private PostToNyecExternalResponse postStdinPayloadToNyecDataLakeExternal(final String dataLakeApiBaseURL,
			final String tenantId,
			final String interactionId,
			final Map<String, Object> bundlePayloadWithDisposition,
			final PostStdinPayloadToNyecDataLakeExternal postStdinPayloadToNyecDataLakeExternal)
			throws Exception {
		boolean completed = false;
		String processOutput = "";
		String errorOutput = "";
		LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal BEGIN for interaction id : {} tenantID :{}",
				interactionId, tenantId);
		final var bashScriptPath = postStdinPayloadToNyecDataLakeExternal.cmd();
		if (null == bashScriptPath) {
			throw new IllegalArgumentException(
					"Bash Script path not configured for the environment.Configure this in application.yml.");
		}
		LOG.debug(
				"FHIRService :: postStdinPayloadToNyecDataLakeExternal Fetched Bash Script Path :{} for interaction id : {} tenantID :{}",
				bashScriptPath, interactionId, tenantId);
		LOG.debug(
				"FHIRService :: postStdinPayloadToNyecDataLakeExternal Prepare ProcessBuilder to run the bash script for interaction id : {} tenantID :{}",
				interactionId, tenantId);
		final var processBuilder = new ProcessBuilder(bashScriptPath, tenantId, dataLakeApiBaseURL)
				.redirectErrorStream(true);
		LOG.debug(
				"FHIRService :: postStdinPayloadToNyecDataLakeExternal Start the process  for interaction id : {} tenantID :{}",
				interactionId, tenantId);
		final var process = processBuilder.start();
		LOG.debug(
				"FHIRService :: postStdinPayloadToNyecDataLakeExternal DEBUG: Capture any output from stdout or stderr immediately after starting\r\n"
						+ //
						"        // the process  for interaction id : {} tenantID :{}",
				interactionId, tenantId);
		try (var errorStream = process.getErrorStream();
				var inputStream = process.getInputStream()) {

			// DEBUG: Print any errors encountered
			errorOutput = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
			if (!errorOutput.isBlank()) {
				LOG.error(
						"FHIRService :: postStdinPayloadToNyecDataLakeExternal Error Stream Output before sending payload:{}  for interaction id : {} tenantID :{}",
						errorOutput, interactionId, tenantId);
				throw new UnexpectedException(
						"Error Stream Output before sending payload:\n" + errorOutput);
			}
			LOG.debug(
					"FHIRService :: postStdinPayloadToNyecDataLakeExternal Convert Payload map to String BEGIN for interaction id : {} tenantID :{}",
					interactionId, tenantId);

			LOG.debug(
					"FHIRService :: postStdinPayloadToNyecDataLakeExternal Pass the payload map to String END for interaction id : {} tenantID :{}",
					interactionId, tenantId);
			final String payload = Configuration.objectMapper.writeValueAsString(bundlePayloadWithDisposition);
			LOG.debug(
					"FHIRService :: postStdinPayloadToNyecDataLakeExternal Pass the payload via STDIN -BEGIN for interaction id : {} tenantID :{}",
					interactionId, tenantId);
			try (OutputStream outputStream = process.getOutputStream()) {
				outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
				outputStream.flush();
				LOG.debug(
						"FHIRService :: postStdinPayloadToNyecDataLakeExternal Payload send successfully to STDIN -END for interaction id : {} tenantID :{}",
						interactionId, tenantId);
			} catch (final IOException e) {
				LOG.debug(
						"FHIRService :: postStdinPayloadToNyecDataLakeExternal Failed to write payload to process STDIN due to error :{}  begin for interaction id : {} tenantID :{}",
						e.getMessage(), interactionId, tenantId);
				throw e;
			}
			final int timeout = Integer.valueOf(postStdinPayloadToNyecDataLakeExternal.timeout());
			LOG.debug(
					"FHIRService :: postStdinPayloadToNyecDataLakeExternal Wait for the process to complete with a timeout :{}  begin for interaction id : {} tenantID :{}",
					timeout, interactionId, tenantId);
			completed = process.waitFor(timeout, TimeUnit.SECONDS);
			LOG.debug(
					"FHIRService :: postStdinPayloadToNyecDataLakeExternal Wait elapsed ...Fetch response  begin for interaction id : {} tenantID :{}",
					interactionId, tenantId);

			processOutput = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
			LOG.debug(
					"FHIRService :: postStdinPayloadToNyecDataLakeExternal Standard Output received:{} for interaction id : {} tenantID :{}",
					processOutput, interactionId, tenantId);
			errorOutput = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
			if (!errorOutput.isBlank()) {
				LOG.debug(
						"FHIRService :: postStdinPayloadToNyecDataLakeExternal Error Stream Output after execution: {} for interaction id : {} tenantID :{}",
						errorOutput, interactionId, tenantId);
			}
		}

		LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal END for interaction id : {} tenantID :{}",
				interactionId, tenantId);
		return new PostToNyecExternalResponse(completed, processOutput, errorOutput);
	}

	private WebClient createWebClient(final String scoringEngineApiURL,
			final org.jooq.Configuration jooqCfg,
			final Map<String,Object> requestParamters,
			final String tenantId,
			final String payload,
			final Map<String, Object> bundlePayloadWithDisposition,
			final String provenance,
			
                        final String interactionId, final String groupInteractionId,
			final String masterInteractionId, final String sourceType, final String requestUriToBeOverriden) {
		return WebClient.builder()
				.baseUrl(scoringEngineApiURL)
				.filter(ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
					filter(clientRequest, requestParamters, jooqCfg, provenance, tenantId, payload,
							bundlePayloadWithDisposition,
							
                                                         groupInteractionId,
							masterInteractionId, sourceType, requestUriToBeOverriden, interactionId);
					return Mono.just(clientRequest);
				}))
				.build();
	}

	private KeyDetails getSecretsFromAWSSecretManager(final String keyName, final String certName) {
		final Region region = Region.US_EAST_1;
		LOG.debug(
				"FHIRService:: getSecretsFromAWSSecretManager  - Get Secrets Client Manager for region : {} BEGIN for interaction id: {}",
				Region.US_EAST_1);
		final SecretsManagerClient secretsClient = SecretsManagerClient.builder()
				.region(region)
				.build();
		final KeyDetails keyDetails = new KeyDetails(getValue(secretsClient, keyName),
				getValue(secretsClient, certName));
		secretsClient.close();
		LOG.debug(
				"FHIRService:: getSecretsFromAWSSecretManager  - Get Secrets Client Manager for region : {} END for interaction id: {}",
				Region.US_EAST_1);
		return keyDetails;
	}

	public static String getValue(final SecretsManagerClient secretsClient, final String secretName) {
		LOG.debug("FHIRService:: getValue  - Get Value of secret with name  : {} -BEGIN", secretName);
		String secret = null;
		try {
			final GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
					.secretId(secretName)
					.build();

			final GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
			secret = valueResponse.secretString();
			LOG.info("FHIRService:: getValue  - Fetched value of secret with name  : {}  value  is null : {} -END",
					secretName, secret == null ? "true" : "false");
		} catch (final SecretsManagerException e) {
			LOG.error("ERROR:: FHIRService:: getValue  - Get Value of secret with name  : {} - FAILED with error "
					+ e.awsErrorDetails().errorMessage(), e);
		} catch (final Exception e) {
			LOG.error("ERROR:: FHIRService:: getValue  - Get Value of secret with name  : {} - FAILED with error ",
					e);
		}
		LOG.info("FHIRService:: getValue  - Get Value of secret with name  : {} ", secretName);
		return secret;
	}

	private void filter(final ClientRequest clientRequest,
			final Map<String,Object> requestParameters,
			final org.jooq.Configuration jooqCfg,
			final String provenance,
			final String tenantId,
			final String payload,
			final Map<String, Object> bundlePayloadWithDisposition,
                        final String groupInteractionId, final String masterInteractionId,
			final String sourceType, final String requestUriToBeOverriden, final String interactionId) {

		LOG.debug("FHIRService:: sendToScoringEngine Filter request before post - BEGIN interaction id: {}",
				interactionId);
		final var requestURI = StringUtils.isNotEmpty(requestUriToBeOverriden) ? requestUriToBeOverriden
				: (String) requestParameters.get(Constants.REQUEST_URI);
		final StringBuilder requestBuilder = new StringBuilder()
				.append(clientRequest.method().name()).append(" ")
				.append(clientRequest.url()).append(" HTTP/1.1").append("\n");

		clientRequest.headers().forEach((name, values) -> values
				.forEach(value -> requestBuilder.append(name).append(": ").append(value).append("\n")));

		final var outboundHttpMessage = requestBuilder.toString();

		registerStateForward(jooqCfg, provenance, interactionId, requestURI, tenantId,
				Optional.ofNullable(bundlePayloadWithDisposition).orElse(new HashMap<>()),
				outboundHttpMessage, 
                                 payload, groupInteractionId,
				masterInteractionId, sourceType);

		LOG.debug("FHIRService:: sendToScoringEngine Filter request before post - END interaction id: {}",
				interactionId);
	}

	private void sendPostRequest(final WebClient webClient,
        final String tenantId,
        final Map<String, Object> bundlePayloadWithDisposition,
        final String payload,
        final String dataLakeApiContentType,
        final String interactionId,
        final org.jooq.Configuration jooqCfg,
        final String provenance,
        final String requestURI, final String scoringEngineApiURL, final String groupInteractionId,
        final String masterInteractionId, final String sourceType, final String bundleId,Map<String,Object> requestParameters) {
    final Span span = tracer.spanBuilder("FhirService.sendPostRequest").startSpan();
    try {
        LOG.debug(
                "FHIRService:: sendToScoringEngine Post to scoring engine - BEGIN interaction id: {} tenantID :{}",
                interactionId, tenantId);
		final DataLedgerPayload dataLedgerPayload = DataLedgerPayload.create(
				CoreDataLedgerApiClient.Actor.TECHBD.getValue(), CoreDataLedgerApiClient.Action.SENT.getValue(),
				CoreDataLedgerApiClient.Actor.NYEC.getValue(), bundleId);
        // Post request to scoring engine
        webClient.post()
                .uri("?processingAgent=" + tenantId)
                .body(BodyInserters.fromValue(
                        bundlePayloadWithDisposition != null ? bundlePayloadWithDisposition : payload))
                .header("Content-Type", Optional.ofNullable(dataLakeApiContentType)
                        .orElse(Constants.FHIR_CONTENT_TYPE_HEADER_VALUE))
                .retrieve()
                .bodyToMono(String.class)
                .doFinally(signalType -> {
                    final var dataLedgerProvenance = "%s.sendPostRequest".formatted(FHIRService.class.getName());
            		coreDataLedgerApiClient.processRequest(dataLedgerPayload,interactionId,masterInteractionId,groupInteractionId,dataLedgerProvenance,SourceType.FHIR.name(),null);
                })
                .subscribe(response -> {
                    handleResponse(response, jooqCfg, interactionId, requestURI, tenantId,
                            provenance, scoringEngineApiURL, groupInteractionId,
                            masterInteractionId, sourceType, requestParameters);
                }, error -> {
                    registerStateFailure(jooqCfg, scoringEngineApiURL, interactionId, error,
                            requestURI, tenantId, provenance, groupInteractionId,
                            masterInteractionId, sourceType, requestParameters);
                });

			LOG.info("FHIRService:: sendToScoringEngine Post to scoring engine - END interaction id: {} tenantid: {}",
					interactionId, tenantId);
		} finally {
			span.end();
		}
	}
	private void sendPostRequestWithApiKey(final WebClient webClient,
			final String tenantId,
			final Map<String, Object> bundlePayloadWithDisposition,
			final String payload,
			final String dataLakeApiContentType,
			final String interactionId,
			final org.jooq.Configuration jooqCfg,
			final String provenance,
			final String requestURI, final String scoringEngineApiURL, final String groupInteractionId,
			final String masterInteractionId, final String sourceType, final WithApiKeyAuth apiKeyAuthDetails,
			final String bundleId,Map<String,Object> requestParameters) {
		final Span span = tracer.spanBuilder("FhirService.sendPostRequest").startSpan();
		try {
			LOG.debug(
					"FHIRService:: sendPostRequestWithApiKey Post to scoring engine - BEGIN interaction id: {} tenantID :{}",
					interactionId, tenantId);
			final String apiClientKey =  AWSUtil.getValue(apiKeyAuthDetails.apiKeySecretName());	
			LOG.info(
				"FHIRService:: nyec api client key retrieved  : {} from secret  {} - BEGIN interaction id: {} tenantID :{}",
				apiClientKey == null ? "Api key is null" : "Api key is not null" ,apiKeyAuthDetails.apiKeySecretName(),interactionId, tenantId);	
			webClient.post()
					.uri("?processingAgent=" + tenantId)
					.body(BodyInserters.fromValue(null != bundlePayloadWithDisposition
							? bundlePayloadWithDisposition
							: payload))
					.header("Content-Type", Optional.ofNullable(dataLakeApiContentType)
							.orElse(Constants.FHIR_CONTENT_TYPE_HEADER_VALUE))
					.header(apiKeyAuthDetails.apiKeyHeaderName(),apiClientKey)				
					.retrieve()
					.bodyToMono(String.class)
					.doFinally(signalType -> {
						final DataLedgerPayload dataLedgerPayload = DataLedgerPayload.create(
							CoreDataLedgerApiClient.Actor.TECHBD.getValue(), CoreDataLedgerApiClient.Action.SENT.getValue(), 
								CoreDataLedgerApiClient.Actor.NYEC.getValue(), bundleId);
						final var dataLedgerProvenance = "%s.sendPostRequest".formatted(FHIRService.class.getName());
						coreDataLedgerApiClient.processRequest(dataLedgerPayload,interactionId,masterInteractionId,groupInteractionId,dataLedgerProvenance,SourceType.FHIR.name(),null);
					})
					.subscribe(response -> {
						handleResponse(response, jooqCfg, interactionId, requestURI, tenantId,
								provenance, scoringEngineApiURL, groupInteractionId,
								masterInteractionId, sourceType,requestParameters);
					}, error -> {
						registerStateFailure(jooqCfg, scoringEngineApiURL, interactionId, error,
								requestURI, tenantId, provenance, groupInteractionId,
								masterInteractionId, sourceType,requestParameters);
					});

			LOG.info("FHIRService:: sendPostRequestWithApiKey Post to scoring engine - END interaction id: {} tenantid: {}",
					interactionId, tenantId);
		} finally {
			span.end();
		}
	}

	private void handleResponse(final String response,
			final org.jooq.Configuration jooqCfg,
			final String interactionId,
			final String requestURI,
			final String tenantId,
			final String provenance,
			final String scoringEngineApiURL, final String groupInteractionId, final String masterInteractionId,
			final String sourceType,Map<String,Object> requestParameters) {
		final Span span = tracer.spanBuilder("FhirService.handleResponse").startSpan();
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
							sourceType,requestParameters);
				} else {
					LOG.warn("FHIRService:: handleResponse FAILURE for interaction id: {}",
							interactionId);
					registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, response,
							provenance, groupInteractionId, masterInteractionId,
							sourceType,requestParameters);
				}
			} catch (final Exception e) {
				LOG.error("FHIRService:: handleResponse unexpected error for interaction id : {}, response: {}",
						interactionId, response, e);
				registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, e.getMessage(),
						provenance, groupInteractionId, masterInteractionId, sourceType,requestParameters);
			}
			LOG.info("FHIRService:: handleResponse END for interaction id: {}", interactionId);
		} finally {
			span.end();
		}
	}

	private void handleError(final Map<String, Object> validationPayloadWithDisposition,
			final Exception e, final String interactionId) {

		validationPayloadWithDisposition.put("exception", e.toString());
		LOG.error(
				"ERROR:: FHIRService:: sendToScoringEngine Exception while sending to scoring engine payload with interaction id: {}",
				interactionId, e);
	}

	private Map<String, Object> preparePayload(final Map<String,Object> requestParameters, final String bundlePayload,
			final Map<String, Object> payloadWithDisposition, final String interactionId) {
		LOG.debug("FHIRService:: addValidationResultToPayload BEGIN for interaction id : {}", interactionId);

		Map<String, Object> resultMap = null;

		try {
			final Map<String, Object> extractedOutcome = Optional
					.ofNullable(extractIssueAndDisposition(interactionId, payloadWithDisposition, requestParameters))
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
			final Map<String, Object> bundleMap = Optional
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
		} catch (final Exception ex) {
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
    public Map<String, Object> extractIssueAndDisposition(final String interactionId,
            final Map<String, Object> operationOutcomePayload, final Map<String,Object> requestParameters) {
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
                    final List<?> validationResults = (List<?>) operationOutcomeMap
							.get("validationResults");
                    if (validationResults == null || validationResults.isEmpty()) {
                        return Optional.empty();
                    }

                    // Extract the first validationResult
                    final Map<String, Object> validationResult = (Map<String, Object>) validationResults
							.get(0);

                    // Navigate to operationOutcome.issue
                    final Map<String, Object> operationOutcome = (Map<String, Object>) validationResult
                            .get("operationOutcome");
                    final List<Map<String, Object>> issuesRaw = operationOutcome != null
                            ? (List<Map<String, Object>>) operationOutcome.get("issue")
                            : null;

                    final String headerSeverityLevelValue = (String) requestParameters.get(Constants.VALIDATION_SEVERITY_LEVEL);
                    String validationSeverityLevel = coreAppConfig.getValidationSeverityLevel();
                    if (headerSeverityLevelValue != null && !headerSeverityLevelValue.isEmpty()) {
                        validationSeverityLevel = headerSeverityLevelValue;
                    }
                    final List<Map<String, Object>> filteredIssues = new ArrayList<>();
                    if (issuesRaw != null) {
                        final String severityLevel = Optional.ofNullable(validationSeverityLevel)
                                .orElse("error").toLowerCase();
                        final Set<String> allowedSeverities = switch (severityLevel) {
                            case "fatal" -> Set.of("fatal");
                            case "error" -> Set.of("fatal", "error");
                            case "warning" -> Set.of("fatal", "error", "warning");
                            case "information" -> Set.of("fatal", "error", "warning", "information");
                            default -> Set.of("fatal", "error");
                        };

                        for (final Map<String, Object> issue : issuesRaw) {
                            final String severity = (String) issue.get("severity");
                            if (severity != null && allowedSeverities.contains(severity.toLowerCase())) {
                                filteredIssues.add(issue);
                            }
                        }
                    }

                    // If no issues match, add an informational entry
                    if (filteredIssues.isEmpty()) {
                        final Map<String, Object> infoIssue = new HashMap<>();
                        infoIssue.put("severity", "information");
                        infoIssue.put("diagnostics",
                                "Validation successful. No issues found at or above severity level: "
                                        + validationSeverityLevel);
                        infoIssue.put("code", "informational");
                        filteredIssues.add(infoIssue);
                    }

                    // Prepare the result
                    final Map<String, Object> result = new HashMap<>();
                    result.put("resourceType", operationOutcomeMap.get("resourceType"));
                    result.put("issue", filteredIssues);

                    // Add techByDesignDisposition if available
                    final List<?> techByDesignDisposition = (List<?>) operationOutcomeMap.get("techByDesignDisposition");
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

	private Map<String, Object> appendToBundlePayload(final String interactionId, final Map<String, Object> payload,
			final Map<String, Object> extractedOutcome) {
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
		final
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

	private void registerStateForward(final org.jooq.Configuration jooqCfg, final String provenance,
			final String bundleAsyncInteractionId, final String requestURI,
			final String tenantId,
			final Map<String, Object> payloadWithDisposition,
			final String outboundHttpMessage, 
                        final String payload,
			final String groupInteractionId, final String masterInteractionId, final String sourceType) {
		final Span span = tracer.spanBuilder("FHIRService.registerStateForward").startSpan();
		try {
			LOG.info("REGISTER State Forward : BEGIN for inteaction id  : {} tenant id : {}",
					bundleAsyncInteractionId, tenantId);
			final var forwardedAt = OffsetDateTime.now();
			final var initRIHR = new RegisterInteractionFhirRequest();
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
				initRIHR.setPInteractionId(bundleAsyncInteractionId);
				initRIHR.setPGroupHubInteractionId(groupInteractionId);
				initRIHR.setPSourceHubInteractionId(masterInteractionId);
				initRIHR.setPInteractionKey(requestURI);
				initRIHR.setPNature((JsonNode) Configuration.objectMapper.valueToTree(
						Map.of("nature", Nature.FORWARD_HTTP_REQUEST.getDescription(), "tenant_id",
								tenantId)));
				initRIHR.setPContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
				initRIHR.setPPayload((JsonNode) Configuration.objectMapper
						.valueToTree(payloadWithDisposition));
				initRIHR.setPFromState(State.DISPOSITION.name());
				initRIHR.setPToState(State.FORWARD.name());
				initRIHR.setPSourceType(sourceType);
				initRIHR.setPCreatedAt(forwardedAt); // don't let DB set this, use app
				// time
				initRIHR.setPCreatedBy(FHIRService.class.getName());
				initRIHR.setPProvenance(provenance);
				final var start = Instant.now();
				final var execResult = initRIHR.execute(jooqCfg);
				final var end = Instant.now();
				final JsonNode response = initRIHR.getReturnValue();
				final Map<String,Object> responseAttributes = CoreFHIRUtil.extractFields(response);
				LOG.info(
						"REGISTER State Forward : END for interaction id: {} tenant id: {}. Time taken: {} milliseconds | payload -> error: {}, interaction_id: {}, hub_nexus_interaction_id: {} | execResult: {}",
						bundleAsyncInteractionId,
						tenantId,
						Duration.between(start, end).toMillis(),
						responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
						responseAttributes.getOrDefault(Constants.KEY_INTERACTION_ID, "N/A"),
						responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"),
						execResult);
			} catch (final Exception e) {
				LOG.error("ERROR:: REGISTER State Forward CALL for interaction id : {} tenant id : {}"
						+ initRIHR.getName() + " initRIHR error", bundleAsyncInteractionId,
						tenantId,
						e);
			}
		} finally {
			span.end();
		}
	}

	private void registerStateComplete(final org.jooq.Configuration jooqCfg, final String bundleAsyncInteractionId,
			final String requestURI, final String tenantId,
			final String response, final String provenance, final String groupInteractionId, final String masterInteractionId,
			final String sourceType,Map<String,Object> requestParameters) {
		final Span span = tracer.spanBuilder("FHIRService.registerStateComplete").startSpan();
		try {
			LOG.info("REGISTER State Complete : BEGIN for interaction id :  {} tenant id : {}",
					bundleAsyncInteractionId, tenantId);
			final var forwardRIHR = new RegisterInteractionFhirRequest();
			try {
				requestParameters.put(Constants.OBSERVABILITY_METRIC_INTERACTION_FINISH_TIME, Instant.now().toString());
				forwardRIHR.setPAdditionalDetails((JsonNode) Configuration.objectMapper.valueToTree( Map.of("request", requestParameters)));
				forwardRIHR.setPInteractionId(bundleAsyncInteractionId);
				forwardRIHR.setPSourceHubInteractionId(masterInteractionId);
				forwardRIHR.setPGroupHubInteractionId(groupInteractionId);
				forwardRIHR.setPInteractionKey(requestURI);
				forwardRIHR.setPNature((JsonNode) Configuration.objectMapper.valueToTree(
						Map.of("nature", Nature.FORWARDED_HTTP_RESPONSE.getDescription(),
								"tenant_id", tenantId)));
				forwardRIHR.setPContentType(
						MimeTypeUtils.APPLICATION_JSON_VALUE);
				try {
					// expecting a JSON payload from the server
					forwardRIHR.setPPayload(Configuration.objectMapper
							.readTree(response));
				} catch (final JsonProcessingException jpe) {
					// in case the payload is not JSON store the string
					forwardRIHR.setPPayload((JsonNode) Configuration.objectMapper
							.valueToTree(response));
				}
				forwardRIHR.setPFromState(State.FORWARD.name());
				forwardRIHR.setPToState(State.COMPLETE.name());
				forwardRIHR.setPSourceType(sourceType);
				forwardRIHR.setPCreatedAt(OffsetDateTime.now()); // don't let DB
				forwardRIHR.setPCreatedBy(FHIRService.class.getName());
				forwardRIHR.setPProvenance(provenance);
				final var start = Instant.now();
				final var execResult = forwardRIHR.execute(jooqCfg);
				final var end = Instant.now();
				final JsonNode responseFromDB = forwardRIHR.getReturnValue();
				final Map<String,Object> responseAttributes = CoreFHIRUtil.extractFields(responseFromDB);
				LOG.info(
						"REGISTER State Complete : END for interaction id: {} tenant id: {}. Time Taken: {} milliseconds | payload -> error: {}, interaction_id: {}, hub_nexus_interaction_id: {} | execResult: {}",
						bundleAsyncInteractionId,
						tenantId,
						Duration.between(start, end).toMillis(),
						responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
						responseAttributes.getOrDefault(Constants.KEY_INTERACTION_ID, "N/A"),
						responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"),
						execResult);
			} catch (final Exception e) {
				LOG.error("ERROR:: REGISTER State Complete CALL for interaction id : {} tenant id : {} "
						+ forwardRIHR.getName()
						+ " forwardRIHR error", bundleAsyncInteractionId, tenantId, e);
			}
		} finally {
			span.end();
		}
	}

	private void registerStateFailed(final org.jooq.Configuration jooqCfg, final String bundleAsyncInteractionId,
			final String requestURI, final String tenantId,
			final String response, final String provenance, final String groupInteractionId, final String masterInteractionId,
			final String sourceType,Map<String,Object> requestParameters) {
		final Span span = tracer.spanBuilder("FHIRService.registerStateFailed").startSpan();
		try {
			LOG.info("REGISTER State Fail : BEGIN for interaction id :  {} tenant id : {}",
					bundleAsyncInteractionId, tenantId);
			final var forwardRIHR = new RegisterInteractionFhirRequest();
			try {
				requestParameters.put(Constants.OBSERVABILITY_METRIC_INTERACTION_FINISH_TIME, Instant.now().toString());
				forwardRIHR.setPAdditionalDetails((JsonNode) Configuration.objectMapper.valueToTree( Map.of("request", requestParameters)));forwardRIHR.setPInteractionId(bundleAsyncInteractionId);
				forwardRIHR.setPInteractionKey(requestURI);
				forwardRIHR.setPGroupHubInteractionId(groupInteractionId);
				forwardRIHR.setPSourceHubInteractionId(masterInteractionId);
				forwardRIHR.setPNature((JsonNode) Configuration.objectMapper.valueToTree(
						Map.of("nature", Nature.FORWARDED_HTTP_RESPONSE_ERROR.getDescription(),
								"tenant_id", tenantId)));
				forwardRIHR.setPContentType(
						MimeTypeUtils.APPLICATION_JSON_VALUE);
				try {
					// expecting a JSON payload from the server
					forwardRIHR.setPPayload(Configuration.objectMapper
							.readTree(response));
				} catch (final JsonProcessingException jpe) {
					// in case the payload is not JSON store the string
					forwardRIHR.setPPayload((JsonNode) Configuration.objectMapper
							.valueToTree(response));
				}
				forwardRIHR.setPFromState(State.FORWARD.name());
				forwardRIHR.setPToState(State.FAIL.name());
				forwardRIHR.setPSourceType(sourceType);
				forwardRIHR.setPCreatedAt(OffsetDateTime.now()); // don't let DB
				// set this, use
				// app time
				forwardRIHR.setPCreatedBy(FHIRService.class.getName());
				forwardRIHR.setPProvenance(provenance);
				final var start = Instant.now();
				final var execResult = forwardRIHR.execute(jooqCfg);
				final var end = Instant.now();
                final JsonNode responseFromDB = forwardRIHR.getReturnValue();
				final Map<String,Object> responseAttributes = CoreFHIRUtil.extractFields(responseFromDB);
				LOG.info(
					"FHIRService - Time taken: {} milliseconds for DB call to REGISTER State None, Accept, Disposition for interaction id: {}  error: {}, hub_nexus_interaction_id: {}",
					Duration.between(start, end).toMillis(),
					responseAttributes.getOrDefault(Constants.KEY_INTERACTION_ID, "N/A"),
					responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),					
					responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A")
				);
				LOG.info(
						"REGISTER State Fail : END for interaction id : {} tenant id : {} .Time Taken : {} milliseconds"
								+ execResult,
						bundleAsyncInteractionId, tenantId,
						Duration.between(start, end).toMillis());
			} catch (final Exception e) {
				LOG.error("ERROR:: REGISTER State Fail CALL for interaction id : {} tenant id : {} "
						+ forwardRIHR.getName()
						+ " forwardRIHR error", bundleAsyncInteractionId, tenantId, e);
						
			}
		} finally {
			span.end();
		}
	}

	private void registerStateFailure(final org.jooq.Configuration jooqCfg, final String dataLakeApiBaseURL,
			final String bundleAsyncInteractionId, final Throwable error,
			final String requestURI, final String tenantId,
			final String provenance, final String groupInteractionId, final String masterInteractionId,
			final String sourceType, Map<String, Object> requestParameters) {
		final Span span = tracer.spanBuilder("FhirService.registerStateFailure").startSpan();
		try {
			LOG.error(
					"Register State Failure - Exception while sending FHIR payload to datalake URL {} for interaction id {}",
					dataLakeApiBaseURL, bundleAsyncInteractionId, error);
			final var errorRIHR = new RegisterInteractionFhirRequest();
			try {
				requestParameters.put(Constants.OBSERVABILITY_METRIC_INTERACTION_FINISH_TIME, Instant.now().toString());
				errorRIHR.setPAdditionalDetails((JsonNode) Configuration.objectMapper.valueToTree( Map.of("request", requestParameters)));errorRIHR.setPInteractionId(bundleAsyncInteractionId);
				errorRIHR.setPGroupHubInteractionId(groupInteractionId);
				errorRIHR.setPSourceHubInteractionId(masterInteractionId);
				errorRIHR.setPInteractionKey(requestURI);
				errorRIHR.setPNature((JsonNode) Configuration.objectMapper.valueToTree(
						Map.of("nature", Nature.FORWARDED_HTTP_RESPONSE_ERROR.getDescription(),
								"tenant_id", tenantId)));
				errorRIHR.setPContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
				errorRIHR.setPSourceType(sourceType);
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
					final String responseBody = webClientResponseException
							.getResponseBodyAsString();
					errorMap.put("responseBody", responseBody);
					String bundleId = "";
					final JsonNode rootNode = Configuration.objectMapper
							.readTree(responseBody);
					final JsonNode bundleIdNode = rootNode.path("bundle_id"); // Adjust
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
				errorRIHR.setPPayload((JsonNode) Configuration.objectMapper
						.valueToTree(errorMap));
				errorRIHR.setPFromState(State.FORWARD.name());
				errorRIHR.setPToState(State.FAIL.name());
				errorRIHR.setPCreatedAt(OffsetDateTime.now()); // don't let DB set this, use app time
				errorRIHR.setPCreatedBy(FHIRService.class.getName());
				errorRIHR.setPProvenance(provenance);
				final var start = Instant.now();
				final var execResult = errorRIHR.execute(jooqCfg);
				final var end = Instant.now();

				final JsonNode responseFromDB = errorRIHR.getReturnValue();
				final Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(responseFromDB);

				LOG.info(
						"Register State Failure - END for interaction id: {} tenant id: {} forwardRIHR execResult: {}. Time Taken: {} milliseconds  error: {}, interaction_id: {}, hub_nexus_interaction_id: {}",
						bundleAsyncInteractionId,
						tenantId,
						execResult,
						Duration.between(start, end).toMillis(),
						responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
						responseAttributes.getOrDefault(Constants.KEY_INTERACTION_ID, "N/A"),
						responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"));
			} catch (final Exception e) {
				LOG.error("ERROR :: Register State Failure - for interaction id : {} tenant id : {} CALL "
						+ errorRIHR.getName() + " errorRIHR error", bundleAsyncInteractionId,
						tenantId,
						e);
			}
		} finally {
			span.end();
		}
	}

	private String getBaseUrl(final HttpServletRequest request) {
		return Helpers.getBaseUrl(request);
	}

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

		MTlsStrategy(final String value) {
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

		public static MTlsStrategy fromString(final String value) {
			for (final MTlsStrategy strategy : MTlsStrategy.values()) {
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

		TechByDesignDisposition(final String action) {
			this.action = action;
		}

		public String getAction() {
			return action;
		}
	}
}
