package org.techbd.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.conf.Configuration;
import org.techbd.service.DataLedgerApiClient.DataLedgerPayload;
import org.techbd.service.DataLedgerApiClient.PayloadType;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.tables.SatDiagnosticDataledgerApi;
import org.techbd.udi.auto.jooq.ingress.tables.records.SatDiagnosticDataledgerApiRecord;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
public class DataLedgerApiClient {
    private final HttpClient client = HttpClient.newHttpClient();
    private final AppConfig appConfig;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;
    private static final Logger LOG = LoggerFactory.getLogger(DataLedgerApiClient.class.getName());

    public DataLedgerApiClient(AppConfig appConfig, UdiPrimeJpaConfig udiPrimeJpaConfig) {
        this.appConfig = appConfig;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
    }

    public void processRequest(DataLedgerPayload dataLedgerPayload, String interactionId, String provenance,
            String source,String validationErrors) {
        processRequest(dataLedgerPayload, interactionId, null, null, provenance, source,validationErrors);
    }

    public void processRequest(DataLedgerPayload dataLedgerPayload, String interactionId, String sourceHubInteractionId,
            String groupHubInteractionId, String provenance, String source,String validationErrors) {
        String apiUrl = appConfig.getDataLedgerApiUrl(); // Read API URL from AppConfig
        String jsonPayload = StringUtils.EMPTY;
        try {
            jsonPayload = Configuration.objectMapper.writeValueAsString(dataLedgerPayload);
        } catch (JsonProcessingException ex) {
            LOG.error("DataLedgerApiClient:: Request failed for interactionId :{}  ", interactionId, ex.getMessage());
             processActionDiagnosticData(interactionId, apiUrl, jsonPayload, null, ex.getMessage(),
                    groupHubInteractionId, sourceHubInteractionId, "sent", provenance, source);
        }
        try {
            if (appConfig.isDataLedgerTracking()) {
                LOG.info("DataLedgerApiClient:: Sending to DataLedger BEGIN for interactionId: {}", interactionId);
                sendRequestAsync(apiUrl, jsonPayload, interactionId,dataLedgerPayload.action,provenance,source);
            } else {
                LOG.info(
                        "DataLedgerApiClient:: Sending to DataLedger is disabled via feature flag for interactionId: {}",
                        interactionId);
            }
        } catch (Exception ex) {
            LOG.error("DataLedgerApiClient:: Request failed for interactionId :{}  ", interactionId, ex.getMessage());

            processActionDiagnosticData(interactionId, apiUrl, jsonPayload, null, ex.getMessage(),
                    groupHubInteractionId, sourceHubInteractionId, "sent", provenance, source);
        }
        LOG.info("DataLedgerApiClient:: Sending to DataLedger END for interactionId: {}", interactionId);
    }

    public void sendRequestAsync(String apiUrl, String jsonPayload, String interactionId, String action, String provenance, String source) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        String curlCommand = buildCurlCommand(request, jsonPayload);
        LOG.info("Equivalent CURL: " + curlCommand); // TODO -remove after testing
    
        CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    boolean isSuccess = response.statusCode() >= 200 && response.statusCode() < 300;
                    LOG.info("Data Ledger API response code : " + response.statusCode() + " for interactionId : "
                            + interactionId);
                    LOG.info("Data Ledger API response body : " + response.body() + " for interactionId : "
                            + interactionId);
                    if (appConfig.isDataLedgerDiagnostics()) {
                        processActionDiagnosticData(interactionId, apiUrl, jsonPayload, response, null,
                                null, null, action, provenance, source);
                    }
                })
                .exceptionally(ex -> {
                    LOG.error("DataLedgerApiClient:: Request failed for interactionId :{}  ", interactionId,
                            ex.getMessage());
                    if (appConfig.isDataLedgerDiagnostics()) { // Check feature flag
                        processActionDiagnosticData(interactionId, apiUrl, jsonPayload, null, ex.getMessage(),
                                null, null, action, provenance, source);
                    }
                    return null;
                });
    }

    private static String buildCurlCommand(HttpRequest request, String body) {
        StringBuilder curl = new StringBuilder("curl -X ").append(request.method());

        // Add headers
        request.headers().map().forEach((key, values) -> {
            String headerString = values.stream().map(value -> "-H \"" + key + ": " + value + "\"")
                    .collect(Collectors.joining(" "));
            curl.append(" ").append(headerString);
        });

        // Add URL
        curl.append(" \"").append(request.uri()).append("\"");

        // Add body if present
        if (body != null && !body.isEmpty()) {
            curl.append(" -d '").append(body.replace("'", "\\'")).append("'");
        }

        return curl.toString();
    }

    private void saveSentActionDiagnosticData(String interactionId, String apiUrl, String requestPayload,
            HttpResponse<String> response, String errorMessage,
            String groupHubInteractionId, String sourceHubInteractionId, String provenance, String source) {
        try {
            String diagnosticId = UUID.randomUUID().toString();
            SatDiagnosticDataledgerApiRecord record = udiPrimeJpaConfig.dsl()
                    .newRecord(SatDiagnosticDataledgerApi.SAT_DIAGNOSTIC_DATALEDGER_API);
            record.setHubInteractionId(interactionId);
            record.setDataledgerUrl(apiUrl);
            record.setCreatedBy(DataLedgerApiClient.class.getName());
            record.setProvenance(provenance);
            record.setSource(source);
            record.setCreatedAt(OffsetDateTime.now());

            if (groupHubInteractionId != null) {
                record.setGroupHubInteractionId(groupHubInteractionId);
            }
            if (sourceHubInteractionId != null) {
                record.setSourceHubInteractionId(sourceHubInteractionId);
            }

            if (requestPayload != null) {
                JsonNode jsonPayload = Configuration.objectMapper.readTree(requestPayload);
                record.setSentPayload(jsonPayload);
            }

            if (response != null) {
                record.setDataledgerSentStatusCode(String.valueOf(response.statusCode()));
                if (response.body() != null) {
                    JsonNode responseJson = Configuration.objectMapper.readTree(response.body());
                    record.setDataledgerSentResponse(responseJson);
                }
                record.setSentStatus("SUCCESS");
            } else {
                record.setSentStatus("FAILED");
            }

            if (errorMessage != null) {
                record.setSentReason(errorMessage);
                record.setSentStatus("FAILED");
            }

            record.store();
            LOG.info("Successfully saved sent action diagnostic data for interactionId: {}", interactionId);
        } catch (Exception ex) {
            LOG.error("Failed to save sent action diagnostic data for interactionId: {}", interactionId, ex);
        }
    }

    private void saveReceivedActionDiagnosticData(String interactionId, String apiUrl, String responsePayload,
            HttpResponse<String> response, String receivedReason,
            String groupHubInteractionId, String sourceHubInteractionId, String provenance, String source) {
        try {
            String diagnosticId = UUID.randomUUID().toString();
            SatDiagnosticDataledgerApiRecord record = udiPrimeJpaConfig.dsl()
                    .newRecord(SatDiagnosticDataledgerApi.SAT_DIAGNOSTIC_DATALEDGER_API);

            record.setSatDiagnosticDataledgerApiId(diagnosticId);
            record.setHubDiagnosticId(diagnosticId);
            record.setHubInteractionId(interactionId);
            record.setDataledgerUrl(apiUrl);
            record.setCreatedBy("SYSTEM");
            record.setProvenance("HUB-PRIME");
            record.setSource("DATALEDGER-API");
            record.setCreatedAt(OffsetDateTime.now());

            if (groupHubInteractionId != null) {
                record.setGroupHubInteractionId(groupHubInteractionId);
            }
            if (sourceHubInteractionId != null) {
                record.setSourceHubInteractionId(sourceHubInteractionId);
            }

            if (response != null) {
                record.setDataledgerReceivedStatusCode(String.valueOf(response.statusCode()));
                record.setReceivedStatus("SUCCESS");
                if (response.body() != null) {
                    JsonNode jsonResponse = Configuration.objectMapper.readTree(response.body());
                    record.setReceivedPayload(jsonResponse);
                }
            } else {
                record.setReceivedStatus("FAILED");
            }

            if (receivedReason != null) {
                record.setReceivedReason(receivedReason);
                record.setReceivedStatus("FAILED");
            }

            record.store();
            LOG.info("Successfully saved received action diagnostic data for interactionId: {}", interactionId);
        } catch (Exception ex) {
            LOG.error("Failed to save received action diagnostic data for interactionId: {}", interactionId, ex);
        }
    }

    public void processActionDiagnosticData(String interactionId, String apiUrl, String payload,
            HttpResponse<String> response, String errorMessage,
            String groupHubInteractionId, String sourceHubInteractionId,
            String action, String provenance, String source) {

        if (Action.SENT.getValue().equalsIgnoreCase(action)) {
            saveSentActionDiagnosticData(interactionId, apiUrl, payload, response, errorMessage,
                    groupHubInteractionId, sourceHubInteractionId, provenance, source);
        } else if (Action.RECEIVED.getValue().equalsIgnoreCase(action)) {
            saveReceivedActionDiagnosticData(interactionId, apiUrl, payload, response, errorMessage,
                    groupHubInteractionId, sourceHubInteractionId, provenance, source);
        }
    }

    public record DataLedgerPayload(
            String executedAt,
            String actor,
            String action,
            String destination,
            String dataId,
            String payloadType) {
        public static DataLedgerPayload create(String actor, String action, String destination, String dataId) {
            return new DataLedgerPayload(
                    Instant.now().toString(),
                    actor, action, destination, dataId, PayloadType.HRSN_BUNDLE.value);
        }
    }

    @Getter
    public enum PayloadType {
        HRSN_BUNDLE("hrsnBundle");

        private final String value;

        PayloadType(String value) {
            this.value = value;
        }
    }

    @Getter
    public enum Action {
        SENT("sent"),
        RECEIVED("received");

        private final String value;

        Action(String value) {
            this.value = value;
        }
    }

    @Getter
    public enum Actor {
        TECHBD("TechBD"),
        NYEC("NYeC");

        private final String value;

        Actor(String value) {
            this.value = value;
        }

    }
}
