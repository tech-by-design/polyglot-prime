package org.techbd.ingest.listener;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.techbd.ingest.MessageSourceProvider;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.feature.FeatureEnum;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import ca.uhn.hl7v2.AcknowledgmentCode;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;

public class MllpRoute extends RouteBuilder implements MessageSourceProvider {

    private TemplateLogger logger;

    private final int port;
    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private Map<String, String> headers;
    public MllpRoute(int port, MessageProcessorService messageProcessorService, AppConfig appConfig, AppLogger appLogger) {
        this.port = port;
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.headers = new HashMap<>();
        this.logger = appLogger.getLogger(MllpRoute.class);
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
                        Message ack = hapiMsg.generateACK();
                        String ackMessage = addNteWithInteractionId(ack, interactionId,appConfig.getVersion());
                        Terser terser = new Terser(hapiMsg);
                        Segment znt = terser.getSegment(".ZNT");
                        // Extract individual fields
                        String messageCode = terser.get("/.ZNT-2");
                        String facility = terser.get("/.ZNT-8");
                        String deliveryType = terser.get("/.ZNT-4");
                        String facilityCode = null;
                        if (facility != null && facility.contains(":")) {
                            String[] parts = facility.split(":");
                            facilityCode = parts.length > 1 ? parts[1] : parts[0];
                        } else if (facility != null) {
                            facilityCode = facility;
                        }   
                        RequestContext requestContext = buildRequestContext(exchange, hl7Message, interactionId);
                        Map<String, String> additionalDetails = requestContext.getAdditionalParameters();
                        if (additionalDetails == null) {
                            additionalDetails = new HashMap<>();
                            requestContext.setAdditionalParameters(additionalDetails);
                        }
                        additionalDetails.put(Constants.MESSAGE_CODE, messageCode);
                        additionalDetails.put(Constants.DELIVERY_TYPE, deliveryType);
                        additionalDetails.put(Constants.FACILITY, facilityCode);
                        messageProcessorService.processMessage(requestContext, hl7Message, ackMessage);
                        logger.info("[PORT {}] Ack message  : {} interactionId= {}", port, ackMessage, interactionId);
                        exchange.setProperty("CamelMllpAcknowledgementString", ackMessage);
                        exchange.getMessage().setBody(ackMessage);
                        logger.info("[PORT {}] Processed HL7 message successfully. Ack message  : {} interactionId= {}", port, ackMessage, interactionId);
                    } catch (Exception e) {
                        logger.error("[PORT {}] Error processing HL7 message. interactionId= {} reason={}", port, interactionId, e.getMessage(),e);
                        try {
                            Message partial = parser.parse(hl7Message);
                            Message generatedNack  = partial.generateACK(AcknowledgmentCode.AE, new HL7Exception(e.getMessage()));
                            nack = addNteWithInteractionId(generatedNack, interactionId,appConfig.getVersion());
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
    
    public static String addNteWithInteractionId(Message ackMessage, String interactionId,String ingestionApiVersion) throws HL7Exception {
        Terser terser = new Terser(ackMessage);
        ackMessage.addNonstandardSegment("NTE");
        terser.set("/NTE(0)-1", "1");
        terser.set("/NTE(0)-3",
                "InteractionID: " + interactionId +
                        " | TechBDIngestionApiVersion: " + ingestionApiVersion);
        PipeParser parser = new PipeParser();
        return parser.encode(ackMessage);
    }

    private RequestContext buildRequestContext(Exchange exchange, String hl7Message, String interactionId) {
        ZonedDateTime uploadTime = ZonedDateTime.now();
        String timestamp = String.valueOf(uploadTime.toInstant().toEpochMilli());
        exchange.getIn().getHeaders().forEach((k, v) -> {
            if (v instanceof String) {
                this.headers.put(k, (String) v);
                if (FeatureEnum.isEnabled(FeatureEnum.DEBUG_LOG_REQUEST_HEADERS)) {
                    logger.info("{} -Header for the InteractionId {} :  {} = {}", FeatureEnum.DEBUG_LOG_REQUEST_HEADERS,interactionId , k, v);
                }
            }
        });
      
        String datePath = uploadTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileBaseName = "hl7-message";
        String fileExtension = "hl7";
        String originalFileName = fileBaseName + "." + fileExtension;

        return new RequestContext(
                headers,
                "/hl7",
                null,
                interactionId,
                uploadTime,
                timestamp,
                originalFileName,
                hl7Message.length(),
                getDataKey(interactionId, headers, originalFileName,timestamp),
                getMetaDataKey(interactionId, headers, originalFileName,timestamp),
                getFullS3DataPath(interactionId, headers, originalFileName,timestamp),
                getUserAgentFromHL7(hl7Message, interactionId),
                exchange.getFromEndpoint().getEndpointUri(),
                "",
                "MLLP",
                getSourceIp(headers),
                getDestinationIp(headers),
                null,
                null,
                getDestinationPort(headers),
                getAcknowledgementKey(interactionId, headers, originalFileName,timestamp),
                getFullS3AcknowledgementPath(interactionId, headers, originalFileName,timestamp),
                getFullS3MetadataPath(interactionId, headers, originalFileName,timestamp),
                MessageSourceType.MLLP,getDataBucketName(),getMetadataBucketName(),appConfig.getVersion());
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

    @Override
    public MessageSourceType getMessageSource() {
        return MessageSourceType.MLLP;
    }

    @Override
    public String getDataBucketName() {
       return appConfig.getAws().getS3().getDefaultConfig().getBucket();
    }

    @Override
    public String getMetadataBucketName() {
        return appConfig.getAws().getS3().getDefaultConfig().getMetadataBucket();
    }

    @Override
    public String getTenantId(Map<String, String> headers) {
        return null;
    }

    @Override
    public String getSourceIp(Map<String, String> headers) {
        return headers.get(Constants.CAMEL_MLLP_LOCAL_ADDRESS);
    }

    @Override
    public String getDestinationIp(Map<String, String> headers) {
        return headers.get(Constants.CAMEL_MLLP_REMOTE_ADDRESS);
    }

   @Override
    public String getDestinationPort(Map<String, String> headers) {
        String localAddress = headers.get(Constants.CAMEL_MLLP_LOCAL_ADDRESS);
        if (localAddress != null && localAddress.contains(":")) {
            return localAddress.substring(localAddress.lastIndexOf(':') + 1);
        }
        return null; 
    }
}
