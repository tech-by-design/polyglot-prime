package org.techbd.fhir.service;

import java.io.IOException;
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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.techbd.corelib.config.Configuration;
import org.techbd.corelib.config.CoreUdiPrimeJpaConfig;
import org.techbd.corelib.config.Helpers;
import org.techbd.corelib.config.Nature;
import org.techbd.corelib.config.SourceType;
import org.techbd.corelib.config.State;
import org.techbd.corelib.service.dataledger.DataLedgerApiClient;
import org.techbd.corelib.service.dataledger.DataLedgerApiClient.DataLedgerPayload;
import org.techbd.corelib.util.AppLogger;
import org.techbd.corelib.util.TemplateLogger;
import org.techbd.fhir.config.AppConfig;
import org.techbd.fhir.config.Constants;
import org.techbd.fhir.exceptions.ErrorCode;
import org.techbd.fhir.exceptions.JsonValidationException;
import org.techbd.fhir.feature.FeatureEnum;
import org.techbd.fhir.service.engine.OrchestrationEngine;
import org.techbd.fhir.service.engine.OrchestrationEngine.Device;
import org.techbd.fhir.util.FHIRUtil;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionFhirRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.micrometer.common.util.StringUtils;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

@Service

@Getter
@Setter
public class FHIRService {
    private final TemplateLogger LOG;
    private final AppConfig appConfig;
	private final DataLedgerApiClient coreDataLedgerApiClient;
    private final OrchestrationEngine engine;
	private final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig;
	private Tracer tracer;

	public FHIRService(AppConfig appConfig, DataLedgerApiClient coreDataLedgerApiClient, OrchestrationEngine engine,
	final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig, AppLogger appLogger) {
		this.appConfig = appConfig;
		this.coreDataLedgerApiClient = coreDataLedgerApiClient;
		this.tracer = GlobalOpenTelemetry.get().getTracer("FHIRService");
		this.engine = engine;
		this.coreUdiPrimeJpaConfig = coreUdiPrimeJpaConfig;
		LOG = appLogger.getLogger(FHIRService.class);
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
				final String bundleId = FHIRUtil.extractBundleId(payload, tenantId);
			if (!SourceType.CSV.name().equalsIgnoreCase(source)
					&& !SourceType.CCDA.name().equalsIgnoreCase(source)
					&& !SourceType.HL7.name().equalsIgnoreCase(source)) {
				DataLedgerPayload dataLedgerPayload = null;
				if (StringUtils.isNotEmpty(bundleId)) {
					dataLedgerPayload = DataLedgerPayload.create(DataLedgerApiClient.Actor.TECHBD.getValue(),
							DataLedgerApiClient.Action.RECEIVED.getValue(), DataLedgerApiClient.Actor.TECHBD.getValue(),
							bundleId);
				} else {
					dataLedgerPayload = DataLedgerPayload.create(DataLedgerApiClient.Actor.TECHBD.getValue(),
							DataLedgerApiClient.Action.RECEIVED.getValue(), DataLedgerApiClient.Actor.TECHBD.getValue(),
							interactionId);
				}
				final var dataLedgerProvenance = "%s.processBundle".formatted(FHIRService.class.getName());
				coreDataLedgerApiClient.processRequest(dataLedgerPayload, interactionId, dataLedgerProvenance,
				SourceType.FHIR.name(), null, FeatureEnum.isEnabled(FeatureEnum.FEATURE_DATA_LEDGER_TRACKING), FeatureEnum.isEnabled(FeatureEnum.FEATURE_DATA_LEDGER_DIAGNOSTICS));
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
                    return payloadWithDisposition;
                }

                if ("true".equalsIgnoreCase(healthCheck != null ? healthCheck.trim() : null)) {
                    return result;
                }

                if (isActionDiscard(payloadWithDisposition)) {
                    LOG.info("Action discard detected, returning payloadWithDisposition"); // TODO-to be removed
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

				final List<String> allowedProfileUrls = FHIRUtil.getAllowedProfileUrls(appConfig);
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
	

	public Map<String, Object> buildOperationOutcome(final JsonValidationException ex,
															final String interactionId) {
		final var validationResult = Map.of(
				"valid", false,
				"issues", List.of(Map.of(
						"message", ex.getErrorCode() + ": " + ex.getMessage(),
						"severity", "FATAL")));

		final var immediateResult = Map.of(
				"resourceType", "OperationOutcome", 
				"bundleSessionId", interactionId,
				Constants.TECHBD_VERSION, appConfig.getVersion(),	
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
			final Map<String, Object> responseAttributes = FHIRUtil.extractFields(response);
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
			final Map<String, Object> responseAttributes = FHIRUtil.extractFields(response);

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
		rihr.setPTechbdVersionNumber(appConfig.getVersion());
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

	private Map<String, Object> validate(final Map<String,Object> requestParameters, final String payload,
            final String interactionId, final String provenance, final String sourceType) {
        final Span span = tracer.spanBuilder("FhirService.validate").startSpan();
		try {
			final var start = Instant.now();
			LOG.info("FHIRService  - Validate -BEGIN for interactionId: {} ", interactionId);
			final var igPackages = appConfig.getIgPackages();
            var requestedIgVersion = (String) requestParameters.get(Constants.SHIN_NY_IG_VERSION);
            LOG.info("headerIgVersion : "+ requestedIgVersion);
			final var sessionBuilder = engine.session()
					.withSessionId(UUID.randomUUID().toString())
					.onDevice(Device.createDefault())
					.withInteractionId(interactionId)
					.withPayloads(List.of(payload))
					.withFhirProfileUrl(FHIRUtil.getBundleProfileUrl())
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
								+ appConfig.getOperationOutcomeHelpUrl(),
						"bundleSessionId", interactionId, // for tracking in
															// database, etc.
							Constants.TECHBD_VERSION, appConfig.getVersion(),								
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

	

	
	

	

	

	public String getValue(final SecretsManagerClient secretsClient, final String secretName) {
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
                    String validationSeverityLevel = appConfig.getValidationSeverityLevel();
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
				initRIHR.setPTechbdVersionNumber(appConfig.getVersion());
				final var start = Instant.now();
				final var execResult = initRIHR.execute(jooqCfg);
				final var end = Instant.now();
				final JsonNode response = initRIHR.getReturnValue();
				final Map<String,Object> responseAttributes = FHIRUtil.extractFields(response);
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
				forwardRIHR.setPTechbdVersionNumber(appConfig.getVersion());
				final var start = Instant.now();
				final var execResult = forwardRIHR.execute(jooqCfg);
				final var end = Instant.now();
				final JsonNode responseFromDB = forwardRIHR.getReturnValue();
				final Map<String,Object> responseAttributes = FHIRUtil.extractFields(responseFromDB);
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
				forwardRIHR.setPTechbdVersionNumber(appConfig.getVersion());
				final var start = Instant.now();
				final var execResult = forwardRIHR.execute(jooqCfg);
				final var end = Instant.now();
                final JsonNode responseFromDB = forwardRIHR.getReturnValue();
				final Map<String,Object> responseAttributes = FHIRUtil.extractFields(responseFromDB);
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
				errorRIHR.setPTechbdVersionNumber(appConfig.getVersion());
				final var start = Instant.now();
				final var execResult = errorRIHR.execute(jooqCfg);
				final var end = Instant.now();

				final JsonNode responseFromDB = errorRIHR.getReturnValue();
				final Map<String, Object> responseAttributes = FHIRUtil.extractFields(responseFromDB);

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