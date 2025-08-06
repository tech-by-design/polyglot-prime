package org.techbd.ingest.listener;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.router.IngestionRouter;
import org.techbd.ingest.feature.FeatureEnum;
import ca.uhn.hl7v2.AcknowledgmentCode;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;

public class MllpRoute extends RouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(MllpRoute.class);

    private final int port;
    private final IngestionRouter ingestionRouter;

    public MllpRoute(int port, IngestionRouter ingestionRouter) {
        this.port = port;
        this.ingestionRouter = ingestionRouter;
    }

    @Override
    public void configure() throws Exception {
         from("mllp://0.0.0.0:" + port + "?autoAck=false") 
                .routeId("hl7-mllp-listener-" + port)
                .log("[PORT " + port + "] Received HL7 message")
                .process(exchange -> {
                    String hl7Message = exchange.getIn().getBody(String.class);
                    GenericParser parser = new GenericParser();
                    String interactionId = UUID.randomUUID().toString();
                    String nack;
                    try {
                        Message hapiMsg = parser.parse(hl7Message);
                        ingestionRouter.routeAndProcess(hl7Message, buildRequestContext(exchange, hl7Message, interactionId));
                        Message ack = hapiMsg.generateACK();
                        String ackMessage = addNteWithInteractionId(ack, interactionId);
                        logger.info("[PORT {}] Ack message  : {} interactionId= {}", port, ackMessage, interactionId);
                        exchange.setProperty("CamelMllpAcknowledgementString", ackMessage);
                        exchange.getMessage().setBody(ackMessage);
                        logger.info("[PORT {}] Processed HL7 message successfully. Ack message  : {} interactionId= {}", port, ackMessage, interactionId);
                    } catch (Exception e) {
                        logger.error("[PORT {}] Error processing HL7 message. interactionId= {} reason={}", port, interactionId, e.getMessage(),e);
                        try {
                            Message partial = parser.parse(hl7Message);
                            Message generatedNack  = partial.generateACK(AcknowledgmentCode.AE, new HL7Exception(e.getMessage()));
                            nack = addNteWithInteractionId(generatedNack, interactionId);
                        } catch (Exception ex2) {
                            logger.error("[PORT {}] Error generating NACK. interactionId= {} reason={}", port, interactionId, ex2.getMessage(),ex2);
                            nack = "MSH|^~\\&|UNKNOWN|UNKNOWN|UNKNOWN|UNKNOWN|202507181500||ACK^O01|1|P|2.3\r" +
                            "MSA|AE|1|Error: Unexpected failure\r" +
                            "NTE|1||InteractionID: " + interactionId + "\r";
                        }
                        exchange.setProperty("CamelMllpAcknowledgementString", nack);
                        exchange.getMessage().setBody(nack);
                    }
                })
                .log("[PORT " + port + "] ACK/NAK sent");
    }

    public static String addNteWithInteractionId(Message ackMessage, String interactionId) throws HL7Exception {
        Terser terser = new Terser(ackMessage);
        ackMessage.addNonstandardSegment("NTE");
        terser.set("/NTE(0)-1", "1");
        terser.set("/NTE(0)-3", "InteractionID: " + interactionId);
        PipeParser parser = new PipeParser();
        return parser.encode(ackMessage);
    }

    private RequestContext buildRequestContext(Exchange exchange, String hl7Message, String interactionId) {
        ZonedDateTime uploadTime = ZonedDateTime.now();
        String timestamp = String.valueOf(uploadTime.toInstant().toEpochMilli());
        Map<String, String> headers = new HashMap<>();
        exchange.getIn().getHeaders().forEach((k, v) -> {
            if (v instanceof String) {
                headers.put(k, (String) v);
                // ✅ Conditional debug logging of headers
                if (FeatureEnum.isEnabled(FeatureEnum.DEBUG_LOG_REQUEST_HEADERS)) {
                    log.info("{} -Header: {} = {}", FeatureEnum.DEBUG_LOG_REQUEST_HEADERS, k, v);
                }
            }
        });
        String tenantId = headers.get(Constants.REQ_HEADER_TENANT_ID);
        String sourceIp = extractSourceIp(headers);
        String destinationIp = headers.get(Constants.REQ_X_SERVER_IP);
        String destinationPort = headers.get(Constants.REQ_X_SERVER_PORT);
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = Constants.TENANT_ID;
        }
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = Constants.DEFAULT_TENANT_ID;
        }
        log.info("Request Headers - tenantId: {}, xForwardedFor: {}, xRealIp: {}, sourceIp: {}, destinationIp: {}, destinationPort: {}, interactionId: {}",
        headers.get(Constants.REQ_HEADER_TENANT_ID),
        headers.get(Constants.REQ_HEADER_X_FORWARDED_FOR),
        headers.get(Constants.REQ_HEADER_X_REAL_IP),
        sourceIp,
        destinationIp,
        destinationPort,interactionId);
        String datePath = uploadTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileBaseName = "hl7-message";
        String fileExtension = "hl7";
        String originalFileName = fileBaseName + "." + fileExtension;
        String objectKey = String.format("data/%s/%s-%s-%s.%s",
                datePath, timestamp, interactionId, fileBaseName, fileExtension);
        String metadataKey = String.format("metadata/%s/%s-%s-%s-%s-metadata.json",
                datePath, timestamp, interactionId, fileBaseName, fileExtension);
        String fullS3Path = Constants.S3_PREFIX + Constants.BUCKET_NAME + "/" + objectKey;
        return new RequestContext(
                headers,
                "/hl7",
                tenantId,
                interactionId,
                uploadTime,
                timestamp,
                originalFileName,
                hl7Message.length(),
                objectKey,
                metadataKey,
                fullS3Path,
                getUserAgentFromHL7(hl7Message, interactionId),
                exchange.getFromEndpoint().getEndpointUri(),
                "",
                "MLLP",
                "127.0.0.1",
                exchange.getIn().getHeader("CamelMllpRemoteAddress", String.class),
                sourceIp,
                destinationIp,
                destinationPort);
    }
  
    private String extractSourceIp(Map<String, String> headers) {
        String xForwardedFor = headers.get(Constants.REQ_HEADER_X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Return the first IP in the comma-separated list
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = headers.get(Constants.REQ_HEADER_X_REAL_IP);
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return null;
    }

    /**
     * Extracts a user-agent-like identifier from the HL7 message.
     * <p>
     * This method parses the MSH (Message Header) segment of the HL7 message
     * and builds a string in the format: {@code SendingApp@SendingFacility}.
     * </p>
     *
     * <p>
     * <b>Return behavior:</b>
     * </p>
     * <ul>
     * <li>If both MSH-3 (Sending App) and MSH-4 (Sending Facility) are present:
     *
     * <pre>{@code
     * "SendingApp@SendingFacility"
     * }</pre>
     *
     * </li>
     * <li>If MSH-3 is missing but MSH-4 is present:
     *
     * <pre>{@code
     * "UnknownApp@SendingFacility"
     * }</pre>
     *
     * </li>
     * <li>If MSH-3 is present but MSH-4 is missing:
     *
     * <pre>{@code
     * "SendingApp@UnknownFacility"
     * }</pre>
     *
     * </li>
     * <li>If both fields are missing or parsing fails:
     *
     * <pre>{@code
     * "MLLP Listener"
     * }</pre>
     *
     * (default fallback)</li>
     * </ul>
     *
     * @param hl7Message The raw HL7 message as a String
     * @return A user-agent-like identifier string derived from the message
     */
    private String getUserAgentFromHL7(String hl7Message,String interactionId) {
        if (hl7Message == null || !hl7Message.startsWith("MSH")) {
            return "MLLP Listener";
        }

        try {
            String[] lines = hl7Message.split("\r|\n");
            String mshLine = lines[0];
            char fieldSeparator = mshLine.charAt(3);

            String[] fields = mshLine.split("\\" + fieldSeparator, -1);
            String sendingApp = (fields.length > 2) ? fields[2] : null;
            String sendingFacility = (fields.length > 3) ? fields[3] : null;

            if ((sendingApp == null || sendingApp.isBlank()) &&
                    (sendingFacility == null || sendingFacility.isBlank())) {
                return "MLLP Listener";
            }

            if (sendingApp == null || sendingApp.isBlank()) {
                sendingApp = "UnknownApp";
            }

            if (sendingFacility == null || sendingFacility.isBlank()) {
                sendingFacility = "UnknownFacility";
            }

            return sendingApp + "@" + sendingFacility;
        } catch (Exception e) {
            logger.error("Error extracting sending facility from HL7 message: {} for interaction id :{}", e.getMessage(), interactionId);
            return "MLLP Listener";
        }
    }
}
