package org.techbd.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.conf.Configuration;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.SatDiagnosticDataledgerApiUpserted;

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
            String source, Map<String, Object> additionalDetails) {
        processRequest(dataLedgerPayload, interactionId, null, null, provenance, source, additionalDetails);
    }

    public void processRequest(DataLedgerPayload dataLedgerPayload, String interactionId, String sourceHubInteractionId,
            String groupHubInteractionId, String provenance, String source, Map<String, Object> additionalDetails) {
        if (appConfig.isDataLedgerTracking()) {
            String apiUrl = appConfig.getDataLedgerApiUrl(); // Read API URL from AppConfig
            String jsonPayload = StringUtils.EMPTY;
            try {
                jsonPayload = Configuration.objectMapper.writeValueAsString(dataLedgerPayload);
            } catch (JsonProcessingException ex) {
                LOG.error("DataLedgerApiClient:: Request failed for interactionId :{}  ", interactionId,
                        ex.getMessage());
                if (appConfig.isDataLedgerDiagnostics()) {
                    processActionDiagnosticData(interactionId, apiUrl, jsonPayload, null, ex.getMessage(),
                            groupHubInteractionId, sourceHubInteractionId, dataLedgerPayload.action, provenance, source,
                            additionalDetails);
                }
            }
            try {
                LOG.info("DataLedgerApiClient:: Sending to DataLedger BEGIN for interactionId: {}", interactionId);
                sendRequestAsync(apiUrl, jsonPayload, interactionId, sourceHubInteractionId, groupHubInteractionId,
                        dataLedgerPayload.action, provenance, source, additionalDetails);
            } catch (Exception ex) {
                LOG.error("DataLedgerApiClient:: Request failed for interactionId :{}  ", interactionId,
                        ex.getMessage());
                if (appConfig.isDataLedgerDiagnostics()) {
                    processActionDiagnosticData(interactionId, apiUrl, jsonPayload, null,
                            ex.getMessage() + "\n" + java.util.Arrays.stream(ex.getStackTrace())
                                    .map(StackTraceElement::toString)
                                    .collect(Collectors.joining("\n")),
                            groupHubInteractionId, sourceHubInteractionId, dataLedgerPayload.action, provenance, source,
                            additionalDetails);
                }
            }
            LOG.info("DataLedgerApiClient:: Sending to DataLedger END for interactionId: {}", interactionId);
        } else {
            LOG.info(
                    "DataLedgerApiClient:: Sending to DataLedger is disabled via feature flag for interactionId: {}",
                    interactionId);
        }
    }

    public void sendRequestAsync(String apiUrl, String jsonPayload, String interactionId, String sourceHubInteractionId,
            String groupHubInteractionId, String action, String provenance, String source,
            Map<String, Object> additionalDetails) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        String curlCommand = buildCurlCommand(request, jsonPayload);
        LOG.info("Equivalent CURL: " + curlCommand); // TODO -remove after testing

        CompletableFuture<Void> future = HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    boolean isSuccess = response.statusCode() >= 200 && response.statusCode() < 300;
                    LOG.info("Data Ledger API response code : " + response.statusCode() + " for interactionId : "
                            + interactionId);
                    LOG.info("Data Ledger API response body : " + response.body() + " for interactionId : "
                            + interactionId);
                    if (appConfig.isDataLedgerDiagnostics()) {
                        processActionDiagnosticData(interactionId, apiUrl, jsonPayload, response, null,
                                groupHubInteractionId, sourceHubInteractionId, action, provenance, source,
                                additionalDetails);
                    }
                })
                .exceptionally(ex -> {
                    LOG.error("DataLedgerApiClient:: Request failed for interactionId :{}  ", interactionId,
                            ex.getMessage());
                    if (appConfig.isDataLedgerDiagnostics()) {
                        processActionDiagnosticData(interactionId, apiUrl, jsonPayload, null, ex.getMessage(),
                                groupHubInteractionId, sourceHubInteractionId, action, provenance, source,
                                additionalDetails);
                    }
                    return null;
                });
    }

    private static String buildCurlCommand(HttpRequest request, String body) {
        StringBuilder curl = new StringBuilder("curl -X ").append(request.method());
        request.headers().map().forEach((key, values) -> {
            String headerString = values.stream().map(value -> "-H \"" + key + ": " + value + "\"")
                    .collect(Collectors.joining(" "));
            curl.append(" ").append(headerString);
        });
        curl.append(" \"").append(request.uri()).append("\"");
        if (body != null && !body.isEmpty()) {
            curl.append(" -d '").append(body.replace("'", "\\'")).append("'");
        }

        return curl.toString();
    }

    private void saveSentActionDiagnosticData(String interactionId, String apiUrl, String requestPayload,
            HttpResponse<String> response, String errorMessage,
            String groupHubInteractionId, String sourceHubInteractionId, String provenance, String source,
            Map<String, Object> additionalDetails) {
        try {
            SatDiagnosticDataledgerApiUpserted satDiagnosticDataledgerApiUpserted = new SatDiagnosticDataledgerApiUpserted();
            final var dslContext = udiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            satDiagnosticDataledgerApiUpserted.setHubInteractionId(interactionId);
            satDiagnosticDataledgerApiUpserted.setDataledgerUrl(apiUrl);
            satDiagnosticDataledgerApiUpserted.setCreatedBy(DataLedgerApiClient.class.getName());
            satDiagnosticDataledgerApiUpserted.setProvenance(provenance);
            satDiagnosticDataledgerApiUpserted.setSource(source);
            satDiagnosticDataledgerApiUpserted.setCreatedAt(OffsetDateTime.now());

            if (groupHubInteractionId != null) {
                satDiagnosticDataledgerApiUpserted.setGroupHubInteractionId(groupHubInteractionId);
            }
            if (sourceHubInteractionId != null) {
                satDiagnosticDataledgerApiUpserted.setSourceHubInteractionId(sourceHubInteractionId);
            }

            if (requestPayload != null) {
                JsonNode jsonPayload = Configuration.objectMapper.readTree(requestPayload);
                satDiagnosticDataledgerApiUpserted.setSentPayload(jsonPayload);
            }

            if (response != null) {
                satDiagnosticDataledgerApiUpserted.setDataledgerSentStatusCode(String.valueOf(response.statusCode()));
                if (response.body() != null) {
                    JsonNode dataLedgerSentActionResponse = Configuration.objectMapper.readTree(response.body());
                    satDiagnosticDataledgerApiUpserted.setDataledgerSentResponse(dataLedgerSentActionResponse);
                }
                satDiagnosticDataledgerApiUpserted.setSentStatus("SUCCESS");
            }

            if (errorMessage != null) {
                satDiagnosticDataledgerApiUpserted.setSentReason(errorMessage);
                satDiagnosticDataledgerApiUpserted.setSentStatus("FAILED");
            }
            satDiagnosticDataledgerApiUpserted
                    .setAdditionalDetails((JsonNode) Configuration.objectMapper.valueToTree(additionalDetails));
            satDiagnosticDataledgerApiUpserted.execute(jooqCfg);
            LOG.info("Successfully saved sent action diagnostic data for interactionId: {}", interactionId);
        } catch (Exception ex) {
            LOG.error("Failed to save sent action diagnostic data for interactionId: {}", interactionId, ex);
        }
    }

    private void saveReceivedActionDiagnosticData(String interactionId, String apiUrl, String responsePayload,
            HttpResponse<String> response, String receivedReason,
            String groupHubInteractionId, String sourceHubInteractionId, String provenance, String source,
            Map<String, Object> additionalDetails) {
        try {
            SatDiagnosticDataledgerApiUpserted satDiagnosticDataledgerApiUpserted = new SatDiagnosticDataledgerApiUpserted();
            final var dslContext = udiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();

            satDiagnosticDataledgerApiUpserted.setHubInteractionId(interactionId);
            satDiagnosticDataledgerApiUpserted.setDataledgerUrl(apiUrl);
            satDiagnosticDataledgerApiUpserted.setCreatedBy(DataLedgerApiClient.class.getName());
            satDiagnosticDataledgerApiUpserted.setProvenance(provenance);
            satDiagnosticDataledgerApiUpserted.setSource(source);
            satDiagnosticDataledgerApiUpserted.setCreatedAt(OffsetDateTime.now());
            JsonNode jsonResponse = Configuration.objectMapper.readTree(responsePayload);
            satDiagnosticDataledgerApiUpserted.setReceivedPayload(jsonResponse);

            if (groupHubInteractionId != null) {
                satDiagnosticDataledgerApiUpserted.setGroupHubInteractionId(groupHubInteractionId);
            }
            if (sourceHubInteractionId != null) {
                satDiagnosticDataledgerApiUpserted.setSourceHubInteractionId(sourceHubInteractionId);
            }

            if (response != null) {
                satDiagnosticDataledgerApiUpserted
                        .setDataledgerReceivedStatusCode(String.valueOf(response.statusCode()));
                satDiagnosticDataledgerApiUpserted.setReceivedStatus("SUCCESS");
                if (response.body() != null) {
                    JsonNode dataLedgerResponse = Configuration.objectMapper.readTree(response.body());
                    satDiagnosticDataledgerApiUpserted.setDataledgerReceivedResponse(dataLedgerResponse);
                }
            }

            if (receivedReason != null) {
                satDiagnosticDataledgerApiUpserted.setReceivedReason(receivedReason);
                satDiagnosticDataledgerApiUpserted.setReceivedStatus("FAILED");
            }
            satDiagnosticDataledgerApiUpserted
                    .setAdditionalDetails((JsonNode) Configuration.objectMapper.valueToTree(additionalDetails));
            satDiagnosticDataledgerApiUpserted.execute(jooqCfg);
            LOG.info("Successfully saved received action diagnostic data for interactionId: {}", interactionId);
        } catch (Exception ex) {
            LOG.error("Failed to save received action diagnostic data for interactionId: {}", interactionId, ex);
        }
    }

    public void processActionDiagnosticData(String interactionId, String apiUrl, String payload,
            HttpResponse<String> response, String errorMessage,
            String groupHubInteractionId, String sourceHubInteractionId,
            String action, String provenance, String source, Map<String, Object> additionalDetails) {

        if (Action.SENT.getValue().equalsIgnoreCase(action)) {
            saveSentActionDiagnosticData(interactionId, apiUrl, payload, response, errorMessage,
                    groupHubInteractionId, sourceHubInteractionId, provenance, source, additionalDetails);
        } else if (Action.RECEIVED.getValue().equalsIgnoreCase(action)) {
            saveReceivedActionDiagnosticData(interactionId, apiUrl, payload, response, errorMessage,
                    groupHubInteractionId, sourceHubInteractionId, provenance, source, additionalDetails);
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

        @com.fasterxml.jackson.annotation.JsonValue
        public String getValue() {
            return value;
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
        TECHBD("TechBD-devl"),
        NYEC("NYeC"),
        INVALID_CSV("Invalid - Csv Conversion Failed"),
        INVALID_CCDA("Invalid - CCDA Conversion Failed");

        private final String value;

        Actor(String value) {
            this.value = value;
        }

    }
}
