package org.techbd.service.fhir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.techbd.config.Configuration;
import org.techbd.config.CoreAppConfig;
import org.techbd.udi.auto.jooq.ingress.routines.GetFhirBundlesToReplay;
import org.techbd.udi.auto.jooq.ingress.routines.GetFhirPayloadForNyec;
import org.techbd.udi.auto.jooq.ingress.routines.GetNyecSubmissionFailedBundles;
import org.techbd.udi.auto.jooq.ingress.routines.UpdateFhirReplayStatus;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class FhirReplayService {

    private FHIRService fhirService;

    private TemplateLogger LOG;

    private CoreAppConfig appConfig;

    private final DSLContext primaryDslContext;

    private final TaskExecutor asyncTaskExecutor;

    public FhirReplayService(FHIRService fhirService, AppLogger appLogger, CoreAppConfig appConfig,
            @Qualifier("primaryDslContext") final DSLContext primaryDslContext,
            @Qualifier("asyncTaskExecutor") final TaskExecutor asyncTaskExecutor) {
        this.fhirService = fhirService;
        this.LOG = appLogger.getLogger(FhirReplayService.class);
        this.appConfig = appConfig;
        this.primaryDslContext = primaryDslContext;
        this.asyncTaskExecutor = asyncTaskExecutor;
    }

    public Map<String, Object> replayBundles(HttpServletRequest request, String replayId, OffsetDateTime startDate,
            OffsetDateTime endDate,String tenantId) {
        LOG.info("FHIR-REPLAY Starting replayBundles for replayId={} | startDate={} | endDate={}",
                replayId, startDate, endDate);
        final var jooqCfg = primaryDslContext.configuration();
        Map<String, Object> bundlesResponse = getBundlesToReplay(jooqCfg,replayId, startDate, endDate,tenantId);
        if (bundlesResponse.isEmpty() || !bundlesResponse.containsKey("bundles")) {
            LOG.warn("FHIR-REPLAY No bundles found to replay for replayId={}", replayId);
            return Map.of(
                    "bundle_count", 0,
                    "message", "No bundles found to replay");
        }
        List<Map<String, Object>> bundlesList = (List<Map<String, Object>>) bundlesResponse.get("bundles");
        Map<String, Object> interimResponse = new HashMap<>();

        int bundleCount = Optional.ofNullable(bundlesResponse.get("bundle_count"))
        .map(obj -> {
            if (obj instanceof Number) {
                return ((Number) obj).intValue();
            } else {
                try {
                    return Integer.parseInt(obj.toString());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        })
        .orElse(0);
        interimResponse.put("total_bundles", bundleCount);
        interimResponse.put("replay_id", replayId);

        if (bundleCount == 0) {
            interimResponse.put("message", "No bundles to replay during this period.");
        } else {
            interimResponse.put("message",
                    "Replay started. Please refer to the Hub UI Interactions > FHIR Data tab for detailed status updates.");
        }

        LOG.info("FHIR-REPLAY Replay started for replayId={} | bundle_count={}",
                replayId, bundleCount);
        CompletableFuture.runAsync(() -> {
            for (Map<String, Object> bundle : bundlesList) {
                final var bundleId = (String) bundle.get("bundleid");
                final var bundleInteractionId = (String) bundle.get("interactionid");
                final var groupInteractionId = (String) bundle.get("groupInteractionId");
                final var zipInteractionId = (String) bundle.get("zipInteractionID");
                final var tenant = (String) bundle.get("tenantID");
                final var source = (String) bundle.get("source");
                final var requestUri = (String) bundle.get("uri");
                var errorMessage = (String) bundle.get("errorMessage");
                var status = "Success";
                final var provenance = "%s.replayBundles".formatted(FhirReplayService.class.getName());
                try {
                    LOG.info(
                            "FHIR-REPLAY Starting replay of bundle | replayId={} | bundleInteractionId={} | zipInteractionId={} | groupInteractionId={} | bundleId={} | tenantId={} | source={}",
                            replayId,
                            bundleInteractionId,
                            zipInteractionId,
                            groupInteractionId,
                            bundleId,
                            tenant,
                            source);
                        // Call scoring engine

                        fhirService.sendToScoringEngine(                                
                                null,
                                appConfig.getDefaultDatalakeApiUrl(),
                                MediaType.APPLICATION_JSON_VALUE,
                                tenant,
                                null,
                                provenance,
                                null,
                                null,
                                bundleInteractionId,
                                groupInteractionId,
                                zipInteractionId,
                                source,
                                requestUri,
                                null,
                                bundleId,
                                true,
                                getNyecPayload(jooqCfg, bundleInteractionId));
                        LOG.info(
                                "FHIR-REPLAY Successfully sent bundle | replayId={} | bundleInteractionId={} | zipInteractionId={} | groupInteractionId={} | bundleId={} | tenantId={} | source={}",
                                replayId,
                                bundleInteractionId,
                                zipInteractionId,
                                groupInteractionId,
                                bundleId,
                                tenant,
                                source);
                    
                } catch (Exception e) {
                    LOG.error("FHIR-REPLAY Failed sending bundleId={} for replayId={} | error={}",
                            bundleId, replayId, e.getMessage(), e);
                    status = "Failed";

                    // Capture full stack trace
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    errorMessage = sw.toString();
                } finally {
                    // Always update FHIR replay status and errorMessage
                        updateFhirStatus(jooqCfg, bundleInteractionId, status, errorMessage, replayId, bundleId);
                }
            }

            LOG.info("FHIR-REPLAY Completed asynchronous processing for replayMasterId={}",
                    replayId);
        }, asyncTaskExecutor);
        return interimResponse;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getNyecPayload(final org.jooq.Configuration jooqCfg,String interactionId) {
        LOG.info("Fetching NYEC FHIR payload for interactionId={}", interactionId);
        try {
            GetFhirPayloadForNyec getFhirPayloadForNyec = new GetFhirPayloadForNyec();
            getFhirPayloadForNyec.setPInteractionId(interactionId);
            int executeResult = getFhirPayloadForNyec.execute(jooqCfg);
            JsonNode responseJson = getFhirPayloadForNyec.getReturnValue();

            if (responseJson == null || responseJson.isEmpty()) {
                LOG.warn("No NYEC payload found for interactionId={}", interactionId);
                return Collections.emptyMap();
            }
            Map<String, Object> payloadMap = Configuration.objectMapper.convertValue(responseJson, Map.class);
            LOG.info("Fetched NYEC payload for interactionId={} | keys={}", interactionId, payloadMap.keySet());
            return payloadMap;
        } catch (Exception e) {
            LOG.error("Error fetching NYEC payload for interactionId={}: {}", interactionId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch NYEC payload for interactionId=" + interactionId, e);
        }
    }

    /**
     * Private helper method to update FHIR replay status and errorMessage
     */
    private void updateFhirStatus(final org.jooq.Configuration jooqCfg,String bundleInteractionId, String status, String errorMessage, String replayMasterId, String bundleId) {
        try {
            UpdateFhirReplayStatus updateFhirReplayStatus = new UpdateFhirReplayStatus();
            updateFhirReplayStatus.setPBundleId(bundleId);
            updateFhirReplayStatus.setPInteractionId(bundleInteractionId);
            updateFhirReplayStatus.setPReplayMasterId(replayMasterId);
            updateFhirReplayStatus.setPStatus(status);
            if (errorMessage != null && errorMessage.length() > 4000) { // adjust length as per DB field
                errorMessage = errorMessage.substring(0, 4000);
            }
            updateFhirReplayStatus.setPErrorMessage(errorMessage);
            updateFhirReplayStatus.execute(jooqCfg);

            LOG.info("FHIR-REPLAY Updated status={} for bundleId={} replayMasterId={}",
                    status, bundleId, replayMasterId);
        } catch (Exception e) {
            LOG.error("FHIR-REPLAY Failed to update FHIR replay status for bundleId={} | error={}",
                    bundleId, e.getMessage(), e);
        }
    }

    /**
     * Fetches FHIR bundles that failed NYEC submission within the specified date
     * range.
     * Optionally filters by tenant ID if provided.
     *
     * @param jooqCfg   The jOOQ configuration
     * @param startDate The start date/time of the search range
     * @param endDate   The end date/time of the search range
     * @return Map containing bundle_count and list of failed bundles
     */
    public Map<String, Object> getFailedNyecSubmissionBundles(
            final OffsetDateTime startDate,
            final OffsetDateTime endDate,final String tenantId) {

        LOG.info("Fetching failed NYEC submission bundles | startDate={} | endDate={}",
                startDate, endDate);

        try {
            final var jooqCfg = primaryDslContext.configuration();
            final var getNyecSubmissionFailedBundles = new GetNyecSubmissionFailedBundles();
            getNyecSubmissionFailedBundles.setPStartTime(startDate);
            getNyecSubmissionFailedBundles.setPEndTime(endDate);
            getNyecSubmissionFailedBundles.setPTenantId(tenantId);
            int executeResult = getNyecSubmissionFailedBundles.execute(jooqCfg);
            final var responseJson = (JsonNode) getNyecSubmissionFailedBundles.getReturnValue();
            if (responseJson == null || responseJson.isEmpty()) {
                LOG.warn("No failed NYeC submission bundles found | startDate={} | endDate={}",
                        startDate, endDate);
                return Map.of(
                        "bundle_count", 0,
                        "bundles", "\"No failed NYeC submission bundles found in the specified date range.\"");
            }
            final Map<String, Object> response = Configuration.objectMapper.convertValue(responseJson, Map.class);
            LOG.info("FHIR-REPLAY Found {} failed NYeC submission bundles",
                    response.getOrDefault("bundle_count", 0));
            
            if (response.getOrDefault("bundle_count", 0).equals(0)) {
                LOG.warn("No failed NYeC submission bundles found | startDate={} | endDate={}",
                        startDate, endDate);
                return Map.of(
                        "bundle_count", 0,
                        "bundles", "No failed NYeC submission bundles found in the specified date range.\"");
            }        
            return response;
        } catch (Exception e) {
            LOG.error("Error fetching failed NYeC submission bundles | startDate={} | endDate={} : {}",
                    startDate, endDate, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch failed NYEC submission bundles", e);
        }
    }

    private Map<String, Object> getBundlesToReplay(final org.jooq.Configuration jooqCfg,
            final String interactionId,
            final OffsetDateTime startDate,
            final OffsetDateTime endDate,String teanantId) {
        LOG.info("FHIR-REPLAY Fetching bundles to replay for interactionId={} | startDate={} | endDate={}",
                interactionId, startDate, endDate);

        try {
            // Initialize and configure stored procedure call
            final var getFhirBundlesToReplay = new GetFhirBundlesToReplay();
            getFhirBundlesToReplay.setPReplayMasterId(interactionId);
            getFhirBundlesToReplay.setStartTime(startDate);
            getFhirBundlesToReplay.setEndTime(endDate);
            getFhirBundlesToReplay.setPTenantId(teanantId);

            // Execute the stored procedure
            int executeResult = getFhirBundlesToReplay.execute(jooqCfg);
            final var responseJson = (JsonNode) getFhirBundlesToReplay.getReturnValue();

            if (responseJson == null || responseJson.isEmpty()) {
                LOG.warn("FHIR-REPLAY No bundles found for interactionId={} | startDate={} | endDate={}",
                        interactionId, startDate, endDate);
                return Map.of(
                        "bundle_count", 0,
                        "replay_master_id", interactionId,
                        "bundles", List.of());
            }

            // Convert JsonNode → Map directly
            final Map<String, Object> response = Configuration.objectMapper.convertValue(responseJson, Map.class);

            LOG.info("FHIR-REPLAY Found {} bundles to replay for replay_master_id={}",
                    response.getOrDefault("bundle_count", 0),
                    response.getOrDefault("replay_master_id", interactionId));

            return response;

        } catch (Exception e) {
            LOG.error("FHIR-REPLAY Error fetching bundles to replay for interactionId={} : {}", interactionId,
                    e.getMessage(), e);
            throw new RuntimeException("Failed to fetch bundles to replay", e);
        }
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

}
