package org.techbd.replay;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.SourceType;
import org.techbd.service.fhir.FHIRService;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.CcdaReplayDetailsUpserted;
import org.techbd.udi.auto.jooq.ingress.routines.GetXmlContentFromMirthFdw;
import org.techbd.udi.auto.jooq.ingress.routines.MergeBundleResourceIds;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@Service
public class CcdaReplayService {

	private static final Logger LOG = LoggerFactory.getLogger(CcdaReplayService.class.getName());
	private final TaskExecutor asyncTaskExecutor;
	private final UdiPrimeJpaConfig udiPrimeJpaConfig;
	private final FHIRService fhirService;
	private final AppConfig appConfig;

	public CcdaReplayService(TaskExecutor asyncTaskExecutor,
			UdiPrimeJpaConfig udiPrimeJpaConfig, FHIRService fhirService, final AppConfig appConfig) {
		this.asyncTaskExecutor = asyncTaskExecutor;
		this.udiPrimeJpaConfig = udiPrimeJpaConfig;
		this.fhirService = fhirService;
		this.appConfig = appConfig;
	}

	public Object replayBundlesAsync(
			List<String> bundleIds,
			String replayMasterInteractionId,
			boolean trialRun,
			boolean sendToNyec,
			boolean immediate, boolean addBundleToOutput) {

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("replayMasterInteractionId", replayMasterInteractionId);
		response.put("bundleCount", bundleIds.size());
		response.put("bundleIds", bundleIds);

		if (immediate) {
			LOG.info("CCDA-REPLAY Starting synchronous processing for replayMasterInteractionId={} TechBdVersion:{} with {} bundle(s)",
					replayMasterInteractionId, appConfig.getVersion(), bundleIds.size());

			Map<String, Map<String, Object>> result = processBundles(bundleIds, replayMasterInteractionId, trialRun,
					sendToNyec,
					addBundleToOutput);
			response.put("status", "Completed");
			response.put("result", result);

			LOG.info("CCDA-REPLAY Completed synchronous processing for replayMasterInteractionId={} TechBdVersion:{}",
					replayMasterInteractionId, appConfig.getVersion());
		} else {
			LOG.info("CCDA-REPLAY Starting asynchronous processing for replayMasterInteractionId={} TechBdVersion:{} with {} bundle(s)",
					replayMasterInteractionId, appConfig.getVersion(), bundleIds.size());

			CompletableFuture.runAsync(() -> {
				try {
					processBundles(bundleIds, replayMasterInteractionId, trialRun, sendToNyec, addBundleToOutput);
					LOG.info("CCDA-REPLAY Completed asynchronous processing for replayMasterInteractionId={} TechBdVersion:{}",
							replayMasterInteractionId, appConfig.getVersion());
				} catch (Exception e) {
					LOG.error("CCDA-REPLAY Asynchronous processing failed for replayMasterInteractionId={} TechBdVersion:{} error={}",
							replayMasterInteractionId, appConfig.getVersion(), e.getMessage(), e);
				}
			}, asyncTaskExecutor);

			response.put("status", "Processing started asynchronously");
			response.put("result", null);
			LOG.info("CCDA-REPLAY Asynchronous processing submitted for replayMasterInteractionId={} TechBdVersion:{}",
					replayMasterInteractionId, appConfig.getVersion());
		}
		return response;
	}

	private Map<String, Map<String, Object>> processBundles(List<String> bundleIds,
			String replayMasterInteractionId,
			boolean trialRun,
			boolean sendToNyec, boolean addBundleToOutput) {

		Map<String, Map<String, Object>> processingDetails = new HashMap<>();

		for (String bundleId : bundleIds) {
			final String interactionId = UUID.randomUUID().toString();

			try {
				LOG.info(
						"CCDA-REPLAY PROCESSING STARTED - Replaying bundle {} with replayMasterInteractionId={} interactionId={} TechBdVersion:{}",
						bundleId, replayMasterInteractionId, interactionId, appConfig.getVersion());

				Map<String, Object> originalPayloadAndHeaders = getOriginalCCDPayload(bundleId,
						replayMasterInteractionId, interactionId);
				String tenantId = (String) originalPayloadAndHeaders.get("X-TechBD-Tenant-ID");
				String originalHubInteractionId = (String) originalPayloadAndHeaders
						.get("original_hub_interaction_id");
				boolean isAlreadyResubmitted = originalPayloadAndHeaders.get("alreadyResubmitted") != null
						&& Boolean.parseBoolean(originalPayloadAndHeaders.get("alreadyResubmitted").toString());

				if (isAlreadyResubmitted) {
					handleAlreadyResubmitted(bundleId, replayMasterInteractionId, interactionId, tenantId, trialRun,
							processingDetails, originalHubInteractionId);
					continue;
				}						
				Object originalCCDAPayload = originalPayloadAndHeaders.get("originalCCDAPayload");
				if (originalCCDAPayload == null) {
					handleMissingPayload(bundleId, replayMasterInteractionId, interactionId, tenantId,
							trialRun, processingDetails, originalHubInteractionId);
					continue;
				}

				Map<String, Object> responseJson = getGeneratedBundle(originalPayloadAndHeaders, bundleId,
						replayMasterInteractionId, interactionId);

				boolean isValid = responseJson.get("isValid") != null
						&& Boolean.parseBoolean(responseJson.get("isValid").toString());

				if (!isValid) {
					handleFailedValidation(bundleId, replayMasterInteractionId, interactionId, tenantId, trialRun,
							processingDetails, responseJson);
					continue;
				}

				Object generatedBundleObj = responseJson.get("generatedBundle");
				String generatedBundle = Configuration.objectMapper.writeValueAsString(generatedBundleObj);
				JsonNode generatedBundleNode = Configuration.objectMapper.readTree(generatedBundle);
				Map<String, Object> correctedResponse = mergeBundleResourceIds(generatedBundleNode, bundleId,
						replayMasterInteractionId, interactionId);
				final String correctedBundle = String.valueOf(correctedResponse.get("corrected_bundle"));

				boolean mergeSuccess = correctedResponse.get("merge_success") != null
						&& Boolean.parseBoolean(correctedResponse.get("merge_success").toString());

				if (!mergeSuccess) {
					handleMergeFailure(bundleId, replayMasterInteractionId, interactionId, tenantId, trialRun,
							processingDetails, correctedResponse);
					continue;
				}

				LOG.info(
						"CCDA-REPLAY PROCESSING COMPLETED - Bundle successfully replayed with replayMasterInteractionId={} interactionId={} bundleId={} tenantId={} TechBdVersion:{}",
						replayMasterInteractionId, interactionId, bundleId, tenantId, appConfig.getVersion());

				if (sendToNyec) {
					Map<String, Object> requestParametersMap = Map.of(
							Constants.SOURCE_TYPE, SourceType.CCDA.name(),
							Constants.INTERACTION_ID, interactionId,
							Constants.TENANT_ID, tenantId,
							Constants.REQUEST_URI, "/ccda/replay/");
					Map<String, Object> responseMap = new HashMap<>();
					LOG.info(
							"CCDA-REPLAY Sending replayed bundle to NYEC FHIR endpoint for replayMasterInteractionId={} interactionId={} bundleId={} tenantId={} TechBdVersion:{}",
							replayMasterInteractionId, interactionId, bundleId, tenantId, appConfig.getVersion());
					fhirService.processBundle(correctedBundle, requestParametersMap, responseMap);
				} else {
					LOG.info(
							"CCDA-REPLAY sendToNyec is false. Skipping sending replayed bundle to NYEC FHIR endpoint for replayMasterInteractionId={} interactionId={} bundleId={} tenantId={} TechBdVersion:{}",
							replayMasterInteractionId, interactionId, bundleId, tenantId, appConfig.getVersion());
				}

				handleSuccess(bundleId, replayMasterInteractionId, interactionId, tenantId, trialRun, processingDetails,
						originalHubInteractionId, addBundleToOutput, correctedBundle);

			} catch (Exception e) {
				handleException(bundleId, replayMasterInteractionId, interactionId, trialRun, e, processingDetails);
			}
		}
		return processingDetails;
	}

	private void handleSuccess(String bundleId,
			String replayMasterInteractionId,
			String interactionId,
			String tenantId,
			boolean trialRun,
			Map<String, Map<String, Object>> processingDetails,
			String originalHubInteractionId,
			boolean addBundleToOutput,
			String correctedBundle) {

		LOG.info("CCDA-REPLAY Bundle {} successfully replayed for replayMasterInteractionId={} interactionId={} TechBdVersion:{}",
				bundleId, replayMasterInteractionId, interactionId, appConfig.getVersion());

		if (!trialRun) {
			upsertCcdaReplayDetails(bundleId, originalHubInteractionId, interactionId, true, null,
					Configuration.objectMapper.createObjectNode()
							.put("message", "PROCESSING COMPLETED - Bundle successfully replayed")
							.put("status", "Success"),
					replayMasterInteractionId, tenantId);
		}

		// Build base processing details (ensures bundleId entry exists)
		processingDetails = buildProcessingDetailsMap(
				processingDetails,
				bundleId,
				null,
				"PROCESSING COMPLETED - Bundle successfully replayed",
				"Success");

		// Add corrected bundle if requested
		if (addBundleToOutput && correctedBundle != null) {
			try {
				JsonNode correctedBundleNode = Configuration.objectMapper.readTree(correctedBundle);
				processingDetails.get(bundleId).put("correctedBundle", correctedBundleNode);

			} catch (JsonProcessingException e) {
				LOG.error(
						"CCDA-REPLAY Bundle {} replayed but corrected bundle JSON parsing failed. replayMasterInteractionId={} interactionId={} TechBdVersion:{} error={}",
						bundleId, replayMasterInteractionId, interactionId, appConfig.getVersion(), e.getMessage(), e);
				processingDetails.get(bundleId).put("correctedBundle",
						"Error parsing corrected bundle JSON: " + e.getMessage());
			}
		}
	}

	// Helper for missing original CCD payload
	private void handleMissingPayload(String bundleId,
			String replayMasterInteractionId,
			String interactionId,
			String tenantId,
			boolean trialRun,
			Map<String, Map<String, Object>> processingDetails,
			String originalHubInteractionId) {

		LOG.info(
				"CCDA-REPLAY PROCESSING STARTED - Replaying bundle {} with replayMasterInteractionId={} interactionId={} TechBdVersion:{}",
				bundleId, replayMasterInteractionId, interactionId, appConfig.getVersion());

		if (!trialRun) {
			upsertCcdaReplayDetails(bundleId, originalHubInteractionId, interactionId, false, null,
					Configuration.objectMapper.createObjectNode()
							.put("message", "Not Processed - Original CCD payload is missing")
							.put("status", "Not Processed"),
					replayMasterInteractionId, tenantId);
		}

		processingDetails = buildProcessingDetailsMap(
				processingDetails,
				bundleId,
				null,
				"Not Processed - Original CCD payload is missing",
				"Not Processed");
	}

	private void handleAlreadyResubmitted(String bundleId, String replayMasterInteractionId, String interactionId,
			String tenantId, boolean trialRun, Map<String, Map<String, Object>> processingDetails,
			String originalHubInteractionId) {

		LOG.warn("Skipping already resubmitted bundle {} interactionId={} tenantId={} TechBdVersion:{}",
				bundleId, interactionId, tenantId, appConfig.getVersion());

		if (!trialRun) {
			upsertCcdaReplayDetails(bundleId, originalHubInteractionId, interactionId, false, null,
					Configuration.objectMapper.createObjectNode()
							.put("message", "Not Processed - Already resubmitted")
							.put("status", "Not Processed"),
					replayMasterInteractionId, tenantId);
		}

		processingDetails = buildProcessingDetailsMap(
				processingDetails,
				bundleId,
				null,
				"Not Processed - Already resubmitted",
				"Not Processed");
	}

	private void handleFailedValidation(String bundleId, String replayMasterInteractionId, String interactionId,
			String tenantId, boolean trialRun, Map<String, Map<String, Object>> processingDetails,
			Map<String, Object> responseJson) {

		@SuppressWarnings("unchecked")
		Map<String, Object> errorMessage = (Map<String, Object>) responseJson.getOrDefault("errorMessage",
				Map.of("message", "Unknown error during CCDA processing"));

		LOG.error("BundleId {}  ReplayInteractionId {} interactionId {} TechBDVersion {} failed validation: {}", bundleId, replayMasterInteractionId, interactionId, appConfig.getVersion(), errorMessage);

		if (!trialRun) {
			upsertCcdaReplayDetails(bundleId, null, interactionId, false,
					Configuration.objectMapper.valueToTree(errorMessage),
					Configuration.objectMapper.createObjectNode()
							.put("message",
									"PROCESSING FAILED -Bundle failed validation - Failed CCDA Schema Validation")
							.put("status", "Failed"),
					replayMasterInteractionId, tenantId);
		}

		processingDetails = buildProcessingDetailsMap(
				processingDetails,
				bundleId,
				errorMessage,
				"Bundle failed validation - Failed CCDA Schema Validation",
				"Failed");
	}

	private void handleMergeFailure(String bundleId, String replayMasterInteractionId, String interactionId,
			String tenantId, boolean trialRun, Map<String, Map<String, Object>> processingDetails,
			Map<String, Object> correctedResponse) {

		@SuppressWarnings("unchecked")
		Map<String, Object> errorMessage = (Map<String, Object>) correctedResponse.getOrDefault("error",
				Map.of("message", "Unknown error during merging bundle resource IDs"));

		LOG.error("BundleId {}  ReplayInteractionId {} interactionId {} TechBDVersion {} failed merging: {}", bundleId, replayMasterInteractionId, interactionId, appConfig.getVersion(), errorMessage);

		if (!trialRun) {
			upsertCcdaReplayDetails(bundleId, null, interactionId, true,
					Configuration.objectMapper.valueToTree(errorMessage),
					Configuration.objectMapper.createObjectNode()
							.put("message", "Failed Merging Bundle Resource IDs")
							.put("status", "Failed"),
					replayMasterInteractionId, tenantId);
		}

		processingDetails = buildProcessingDetailsMap(
				processingDetails,
				bundleId,
				errorMessage,
				"PROCESSING FAILED - Failed Merging Bundle Resource IDs",
				"Failed");
	}

	private void handleException(String bundleId, String replayMasterInteractionId, String interactionId,
			boolean trialRun, Exception e, Map<String, Map<String, Object>> processingDetails) {

		LOG.error("BundleId {}  ReplayInteractionId {} interactionId {} TechBDVersion {} processing failed: {}", bundleId, replayMasterInteractionId, interactionId, appConfig.getVersion(), e.getMessage(), e);

		if (!trialRun) {
			upsertCcdaReplayDetails(bundleId, null, interactionId, false, null,
					Configuration.objectMapper.createObjectNode()
							.put("message", "PROCESSING FAILED - Exception: " + e.getMessage())
							.put("status", "Failed"),
					replayMasterInteractionId, null);
		}

		processingDetails = buildProcessingDetailsMap(
				processingDetails,
				bundleId,
				Map.of("exception", e.getMessage()),
				"PROCESSING FAILED - Exception: " + e.getMessage(),
				"Failed");
	}

	public Map<String, Object> getReplayStatus(String replayMasterInteractionId) {
		return null;// replayStatusMap.get(replayMasterInteractionId);
	}

	private Map<String, Map<String, Object>> buildProcessingDetailsMap(
			Map<String, Map<String, Object>> processingDetails,
			String bundleId,
			Map<String, Object> errorMessage,
			String message,
			String status) {

		if (processingDetails == null) {
			processingDetails = new HashMap<>();
		}

		if (bundleId != null) {
			Map<String, Object> details = new HashMap<>();

			if (errorMessage != null && !errorMessage.isEmpty()) {
				details.put("errorMessage", errorMessage);
			}
			if (message != null) {
				details.put("message", message);
			}
			if (status != null) {
				details.put("status", status);
			}

			processingDetails.put(bundleId, details);
		}

		return processingDetails;
	}

	// private void logResponse(Map<String, Object> responseMap,
	// String replayMasterInteractionId,
	// String interactionId,
	// String bundleId) {

	// for (Map.Entry<String, Object> entry : responseMap.entrySet()) {
	// String key = entry.getKey();
	// Object value = entry.getValue();

	// if (value instanceof JsonNode jsonNode) {
	// LOG.info(
	// "Key={} JSON Payload with {} fields | replayMasterInteractionId={}
	// interactionId={} bundleId={}",
	// key, jsonNode.size(), replayMasterInteractionId, interactionId,
	// bundleId);
	// } else {
	// LOG.info(
	// "Key={} Value={} | replayMasterInteractionId={} interactionId={}
	// bundleId={}",
	// key, value, replayMasterInteractionId, interactionId, bundleId);
	// }
	// }
	// }

	/**
	 * Fetch the original CCDA XML payload for a given bundleId.
	 *
	 * @param bundleId                  the Bundle ID
	 * @param replayMasterInteractionId replay request master ID
	 * @param interactionId             individual interaction ID
	 * @return CCDA XML payload as string
	 */
	public Map<String, Object> getOriginalCCDPayload(String bundleId,
			String replayMasterInteractionId,
			String interactionId) {
		LOG.info("CCDA-REPLAY Fetching CCDA payload for replayMasterInteractionId: {} InteractionId: {} bundleId: {} TechBDVersion: {}",
				replayMasterInteractionId, interactionId, bundleId, appConfig.getVersion());
		Map<String, Object> response = new HashMap<>(3);
		try {
			final DSLContext dslContext = udiPrimeJpaConfig.dsl();
			final var jooqCfg = dslContext.configuration();
			GetXmlContentFromMirthFdw routine = new GetXmlContentFromMirthFdw();
			routine.setPBundleId(bundleId);
			routine.execute(jooqCfg);
			final var responseJson = (JsonNode) routine.getReturnValue();
			if (responseJson == null) {
				LOG.warn(
						"CCDA-REPLAY No CCDA payload found for replayMasterInteractionId: {} InteractionId: {}  bundleId={} TechBDVersion {}",
						replayMasterInteractionId, interactionId, bundleId, appConfig.getVersion());
			} else {
				response.putAll(extractFields(responseJson));
				LOG.info(
						"CCDA-REPLAY Successfully fetched CCDA payload for replayMasterInteractionId: {} InteractionId: {} TechBDVersion: {} bundleId={} ({} chars)",
						replayMasterInteractionId, interactionId, appConfig.getVersion(), bundleId,
						responseJson.size());
			}
		} catch (Exception e) {
			LOG.error(
					"CCDA-REPLAY Error fetching CCDA payload for replayMasterInteractionId: {} InteractionId: {} TechBDVersion: {} bundleId={}  : {}",
					replayMasterInteractionId, interactionId, appConfig.getVersion(), bundleId, e.getMessage(), e);
			throw e;
		}
		return response;
	}

	public static Map<String, Object> extractFields(JsonNode payload) {
		var result = new HashMap<String, Object>();

		payload.fieldNames().forEachRemaining(field -> {
			JsonNode value = payload.get(field);
			if (value.isValueNode()) {
				result.put(field, value.asText());
			} else {
				result.put(field, value);
			}
		});

		return result;
	}

	public Map<String, Object> getGeneratedBundle(Map<String, Object> orginalPayloadAndHeaders,
			String bundleId,
			String replayMasterInteractionId,
			String interactionId) {
		LOG.info(
				"CCDA-REPLAY Generating CCDA Bundle BEGIN for replayMasterInteractionId: {} InteractionId: {} bundleId: {} TechBDVersion: {}",
				replayMasterInteractionId, interactionId, bundleId,appConfig.getVersion());

		// Fetch values dynamically from orginalPayloadAndHeaders
		String cin = (String) orginalPayloadAndHeaders.get("X-TechBD-CIN");
		String orgNpi = (String) orginalPayloadAndHeaders.get("X-TechBD-OrgNPI");
		String orgTin = (String) orginalPayloadAndHeaders.get("X-TechBD-OrgTIN");
		String bundleIdHeader = (String) orginalPayloadAndHeaders.get("X-TechBD-Bundle-ID");
		String tenantIdHeader = (String) orginalPayloadAndHeaders.get("X-TechBD-Tenant-ID");
		Object originalCCDAPayload = orginalPayloadAndHeaders.get("originalCCDAPayload");
		String facilityId = (String) orginalPayloadAndHeaders.get("X-TechBD-Facility-ID");
		String baseFhirUrl = (String) orginalPayloadAndHeaders.get("X-TechBD-Base-FHIR-URL");
		String encounterType = (String) orginalPayloadAndHeaders.get("X-TechBD-Encounter-Type");
		String screeningCode = (String) orginalPayloadAndHeaders.get("X-TechBD-Screening-Code");
		LOG.info(
				"CCDA-REPLAY  CCDA Replay invoked with parameters - replayMasterInteractionId: {}, interactionId: {}, bundleId: {}, TechBDVersion: {},"
						+
						"CIN: {}, OrgNPI: {}, OrgTIN: {}, Tenant-ID: {}, Facility-ID: {}, Base FHIR URL: {}, " +
						"Encounter Type: {}, Screening Code: {}, originalCCDAPayload present: {}",
				replayMasterInteractionId,
				interactionId,
				bundleId,
				appConfig.getVersion(),
				cin,
				orgNpi,
				orgTin,
				tenantIdHeader,
				facilityId,
				baseFhirUrl,
				encounterType,
				screeningCode,
				originalCCDAPayload != null ? "YES (length=" + originalCCDAPayload.toString().length() + ")" : "NO");

		WebClient webClient = WebClient.builder()
				.baseUrl(System.getenv("TECHBD_CCDA_BASEURL"))
				.build();

		ObjectMapper objectMapper = new ObjectMapper();

		try {
			String apiResponse = webClient.post()
					.uri("/ccda/replay/")
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.header("X-TechBD-Tenant-ID", tenantIdHeader)
					.header("X-TechBD-CIN", cin)
					.header("X-TechBD-OrgNPI", orgNpi)
					.header("X-TechBD-OrgTIN", orgTin != null ? orgTin : "")
					.header("X-TechBD-Facility-ID", facilityId)
					.header("X-TechBD-Encounter-Type", encounterType)
					.header("X-TechBD-Bundle-ID", bundleIdHeader)
					.header("X-TechBD-Base-FHIR-URL", baseFhirUrl != null ? baseFhirUrl : "")
					.header("X-TechBD-Screening-Code", screeningCode)
					.body(BodyInserters.fromMultipartData("file",
							originalCCDAPayload != null
									? originalCCDAPayload.toString().getBytes(StandardCharsets.UTF_8)
									: new byte[0]))
					.retrieve()
					.onStatus(HttpStatusCode::isError,
							clientResponse -> clientResponse.bodyToMono(String.class)
									.defaultIfEmpty("Unknown error from server")
									.flatMap(errorBody -> {
										LOG.error("Error response from CCDA API (status={}): {}",
												clientResponse.statusCode(), errorBody);
										return Mono.error(new RuntimeException("CCDA API error: " + errorBody));
									}))
					.bodyToMono(String.class)
					.block();
			Map<String, Object> result = objectMapper.readValue(apiResponse, Map.class);
			LOG.info(
					"CCDA-REPLAY Received response from CCDA Bundle replay endpoint for replayMasterInteractionId: {} InteractionId: {} bundleId: {} TechBDVersion: {}",
					replayMasterInteractionId, interactionId, bundleId, appConfig.getVersion());

			return result;

		} catch (WebClientResponseException e) {
			LOG.error("CCDA-REPLAY WebClient error: status={} body={} TechBDVersion: {}", e.getStatusCode(), e.getResponseBodyAsString(),
					appConfig.getVersion());
			return Map.of(
					"isValid", false,
					"errorMessage", Map.of("message", "WebClient error: " + e.getMessage()),
					"generatedBundle", Map.of());
		} catch (Exception e) {
			LOG.error("CCDA-REPLAY Unexpected error calling CCDA API", e);
			return Map.of(
					"isValid", false,
					"errorMessage", Map.of("message", "Unexpected error: " + e.getMessage()),
					"generatedBundle", Map.of());
		}
	}

	public Map<String, Object> mergeBundleResourceIds(JsonNode newBundle,
			String bundleId,
			String replayMasterInteractionId,
			String interactionId) {
		LOG.info(
				"CCDA-REPLAY Merging bundle resources BEGIN for replayMasterInteractionId: {} InteractionId: {} bundleId: {} TechBDVersion: {}",
				replayMasterInteractionId, interactionId, bundleId, appConfig.getVersion());
		Map<String, Object> response = new HashMap<>();
		try {
			final var dslContext = udiPrimeJpaConfig.dsl();
			final var jooqCfg = dslContext.configuration();
			MergeBundleResourceIds routine = new MergeBundleResourceIds();
			routine.setPNewBundle(newBundle);
			routine.setPBundleId(bundleId);
			routine.execute(jooqCfg);
			final var responseJson = routine.getReturnValue();
			if (responseJson == null) {
				LOG.warn(
						"CCDA-REPLAY Merge returned null for replayMasterInteractionId: {} InteractionId: {} bundleId: {} TechBDVersion: {}",
						replayMasterInteractionId, interactionId, bundleId, appConfig.getVersion());
			} else {
				LOG.debug(
						"CCDA-REPLAY Successfully merged resources for replayMasterInteractionId: {} InteractionId: {} bundleId: {}  TechBDVersion: {} resultSize: {}",
						replayMasterInteractionId, interactionId, bundleId, appConfig.getVersion(),
						responseJson.toString().length());
				response = extractFields(responseJson);
			}
			LOG.info(
					"CCDA-REPLAY Merging bundle resources COMPLETED for replayMasterInteractionId: {} InteractionId: {} bundleId: {} TechBDVersion: {}",
					replayMasterInteractionId, interactionId, bundleId, appConfig.getVersion());
			return response;
		} catch (Exception e) {
			LOG.error(
					"CCDA-REPLAY Error merging bundle resources for replayMasterInteractionId: {} InteractionId: {} bundleId: {} TechBDVersion: {} error: {}",
					replayMasterInteractionId, interactionId, bundleId, appConfig.getVersion(), e);
			// throw e;
		}
		return null;
	}

	public void upsertCcdaReplayDetails(
			String bundleId,
			String interactionId,
			String retryInteractionId,
			boolean isValid,
			JsonNode errorMessage,
			JsonNode elaboration,
			String replayMasterInteractionId, String tenantId) {

		LOG.info(
				"CCDA-REPLAY Upserting CCDA replay details for replayMasterInteractionId: {} interactionId: {} bundleId: {} TechBDVersion: {}",
				replayMasterInteractionId, interactionId, bundleId, appConfig.getVersion());
		try {
			final var dslContext = udiPrimeJpaConfig.dsl();
			final var jooqCfg = dslContext.configuration();

			CcdaReplayDetailsUpserted routine = new CcdaReplayDetailsUpserted();
			routine.setPBundleId(bundleId);
			routine.setPHubInteractionId(interactionId);
			routine.setPRetryInteractionId(retryInteractionId);
			routine.setPCreatedAt(OffsetDateTime.now());
			routine.setPIsValid(isValid);
			routine.setPErrorMessage(errorMessage);
			routine.setPElaboration(elaboration);
			routine.setPProvenance(Configuration.objectMapper.createObjectNode()
					.put("tenantId", tenantId).toString());
			routine.setPRetryMasterInteractionId(replayMasterInteractionId);
			routine.execute(jooqCfg);

			final var returnValue = routine.getReturnValue();
			if (returnValue == null) {
				LOG.warn(
						"CCDA-REPLAY Upsert returned null for replayMasterInteractionId: {} interactionId: {} bundleId: {} TechBDVersion: {}",
						replayMasterInteractionId, interactionId, bundleId, appConfig.getVersion());
			} else {
				LOG.debug(
						"CCDA-REPLAY Successfully upserted CCDA replay details for replayMasterInteractionId: {} interactionId: {} bundleId: {} TechBDVersion: {} returnLength={}",
						replayMasterInteractionId, interactionId, bundleId, appConfig.getVersion(),
						returnValue.length());
			}
		} catch (Exception e) {
			LOG.error(
					"CCDA-REPLAY Error upserting CCDA replay details for replayMasterInteractionId: {} interactionId: {} bundleId: {} TechBDVersion: {} error: {}",
					replayMasterInteractionId, interactionId, bundleId, appConfig.getVersion(), e.getMessage(), e);
		}
	}

}
