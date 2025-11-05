package org.techbd.ingest.listener;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.techbd.ingest.MessageSourceProvider;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.feature.FeatureEnum;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.HttpUtil;
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
    private final PortConfig portConfig;
    private Map<String, String> headers;

    public MllpRoute(int port, MessageProcessorService messageProcessorService, AppConfig appConfig, AppLogger appLogger, PortConfig portConfig) {
        this.port = port;
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.portConfig = portConfig;
        this.headers = new HashMap<>();
        this.logger = appLogger.getLogger(MllpRoute.class);
    }

    @Override
    public void configure() throws Exception {
        from("mllp://0.0.0.0:" + port + "?autoAck=false")
                .routeId("hl7-mllp-listener-" + port)
                .log("[PORT " + port + "] Received HL7 message")
                .process(exchange -> {
                    // Wrap entire processor to guarantee a safe NACK on any unexpected error and to log acknowledgement presence
                    String hl7Message = null;
                    GenericParser parser = new GenericParser();
                    String interactionId = UUID.randomUUID().toString();
                    String nack = null;
                    String remote = exchange.getIn().getHeader(Constants.CAMEL_MLLP_REMOTE_ADDRESS, String.class);
                    String local = exchange.getIn().getHeader(Constants.CAMEL_MLLP_LOCAL_ADDRESS, String.class);
                    
                    // Extract client IP and port from remote address
                    String clientIp = null;
                    String clientPort = null;
                    if (remote != null && remote.contains(":")) {
                        int lastColonIndex = remote.lastIndexOf(':');
                        clientIp = remote.substring(0, lastColonIndex);
                        clientPort = remote.substring(lastColonIndex + 1);
                        // Remove leading slash if present
                        if (clientIp.startsWith("/")) {
                            clientIp = clientIp.substring(1);
                        }
                    }
                    
                    // Extract server IP and port from local address
                    String serverIp = null;
                    String serverPort = null;
                    if (local != null && local.contains(":")) {
                        int lastColonIndex = local.lastIndexOf(':');
                        serverIp = local.substring(0, lastColonIndex);
                        serverPort = local.substring(lastColonIndex + 1);
                        // Remove leading slash if present
                        if (serverIp.startsWith("/")) {
                            serverIp = serverIp.substring(1);
                        }
                    }
                    
                    logger.info("[PORT {}] Connection opened: {} -> {} interactionId={}", port, remote, local, interactionId);
                    logger.info("[PORT {}] Client details - IP: {}, Port: {}, interactionId={}", port, clientIp, clientPort, interactionId);
                    logger.info("[PORT {}] Server details - IP: {}, Port: {}, interactionId={}", port, serverIp, serverPort, interactionId);
                    
                    // Log all MLLP headers and their values
                    logger.info("[PORT {}] ===== MLLP Headers Start ===== interactionId={}", port, interactionId);
                    Map<String, Object> allHeaders = exchange.getIn().getHeaders();
                    if (allHeaders != null && !allHeaders.isEmpty()) {
                        allHeaders.forEach((headerName, headerValue) -> {
                            logger.info("[PORT {}] Header: {} = {} (type: {}) interactionId={}", 
                                port, headerName, headerValue, 
                                headerValue != null ? headerValue.getClass().getSimpleName() : "null",
                                interactionId);
                        });
                    } else {
                        logger.info("[PORT {}] No headers found in exchange, interactionId={}", port, interactionId);
                    }
                    logger.info("[PORT {}] ===== MLLP Headers End ===== interactionId={}", port, interactionId);
                    
                    // Log endpoint information
                    logger.info("[PORT {}] Endpoint URI: {}, interactionId={}", port, 
                        exchange.getFromEndpoint().getEndpointUri(), interactionId);
                    
                    // Log message size and preview
                    logger.info("[PORT {}] Message size: {} bytes, interactionId={}", port, 
                        hl7Message != null ? hl7Message.length() : 0, interactionId);
                    logger.info("[PORT {}] Message preview: {}, interactionId={}", port, 
                        truncate(hl7Message, 200), interactionId);

                    try {
                        hl7Message = exchange.getIn().getBody(String.class);

                        // MLLP route: only handle MLLP framing/ACK/NACK logic here.
                        // Selection of whether this port is MLLP vs plain TCP is done in MllpRouteRegistrar.
                        Message hapiMsg = parser.parse(hl7Message);
                        Message ack = hapiMsg.generateACK();
                        String ackMessage = addNteWithInteractionId(ack, interactionId, appConfig.getVersion());
                        Terser terser = new Terser(hapiMsg);
                        RequestContext requestContext = buildRequestContext(exchange, hl7Message, interactionId);
                        Map<String, String> additionalDetails = requestContext.getAdditionalParameters();
                        if (additionalDetails == null) {
                            additionalDetails = new HashMap<>();
                            requestContext.setAdditionalParameters(additionalDetails);
                        }
                        try {
                            Segment znt = terser.getSegment(".ZNT");
                            if (znt != null) {
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
                                additionalDetails.put(Constants.MESSAGE_CODE, messageCode);
                                additionalDetails.put(Constants.DELIVERY_TYPE, deliveryType);
                                additionalDetails.put(Constants.FACILITY, facilityCode);
                            } else {
                                logger.warn("ZNT segment not found in HL7 message. interactionId={}", interactionId);
                            }
                        } catch (HL7Exception e) {
                            logger.error("Error extracting ZNT segment from HL7 message: {} for interaction id :{}", e.getMessage(), interactionId);
                        }
                        messageProcessorService.processMessage(requestContext, hl7Message, ackMessage);
                        logger.info("[PORT {}] Ack generated for interactionId={} ackPreview={}", port, interactionId, truncate(ackMessage, 1024));
                        // Ensure Camel MLLP component will use this ACK
                        exchange.setProperty("CamelMllpAcknowledgementString", ackMessage);
                        exchange.getMessage().setBody(ackMessage);
                        exchange.getIn().setBody(ackMessage);
                        logger.info("[PORT {}] Processed HL7 message successfully; ACK set for interactionId={}", port, interactionId);
                    } catch (Throwable t) {
                        logger.error("[PORT {}] Unexpected error processing connection interactionId={}. Will send safe NACK. error={}", port, interactionId, t.getMessage(), t);
                        try {
                            if (nack == null) {
                                String timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                                String baseAck = "MSH|^~\\&|MLLP_LISTENER|LOCAL|BRIDGE|REMOTE|" + timestamp + "||ACK^O01|1|P|2.3\r";
                                String msa = "MSA|AE|1\r";
                                String nte = "NTE|1||Internal error processing message\r";
                                nack = baseAck + msa + nte;
                            }
                            exchange.setProperty("CamelMllpAcknowledgementString", nack);
                            exchange.getMessage().setBody(nack);
                            exchange.getIn().setBody(nack);
                            logger.info("[PORT {}] Safe NACK set for interactionId={} preview={}", port, interactionId, truncate(nack, 256));
                        } catch (Exception ex2) {
                            logger.error("[PORT {}] Failed to set NACK after unexpected error interactionId={}: {}", port, interactionId, ex2.getMessage(), ex2);
                        }
                    } finally {
                        // Always log whether CamelMllpAcknowledgementString is present
                        Object ackProp = exchange.getProperty("CamelMllpAcknowledgementString");
                        logger.info("[PORT {}] Ack property present={} interactionId={}", port, ackProp != null, interactionId);
                        logger.info("[PORT {}] Connection processing completed: {} -> {} interactionId={}", port, remote, local, interactionId);
                        logger.info("[PORT {}] Final connection summary - Client: {}:{}, Server: {}:{}, interactionId={}", 
                            port, clientIp, clientPort, serverIp, serverPort, interactionId);
                    }
                 })
                 .log("[PORT " + port + "] ACK/NAK sent");
     }

    public static String addNteWithInteractionId(Message ackMessage, String interactionId, String ingestionApiVersion) throws HL7Exception {
        Terser terser = new Terser(ackMessage);
        ackMessage.addNonstandardSegment("NTE");
        terser.set("/NTE(0)-1", "1");
        terser.set("/NTE(0)-3",
                "InteractionID: " + interactionId
                + " | TechBDIngestionApiVersion: " + ingestionApiVersion);
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
                    logger.info("{} -Header for the InteractionId {} :  {} = {}", FeatureEnum.DEBUG_LOG_REQUEST_HEADERS, interactionId, k, v);
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
                getDataKey(interactionId, headers, originalFileName, timestamp),
                getMetaDataKey(interactionId, headers, originalFileName, timestamp),
                getFullS3DataPath(interactionId, headers, originalFileName, timestamp),
                getUserAgentFromHL7(hl7Message, interactionId),
                exchange.getFromEndpoint().getEndpointUri(),
                "",
                "MLLP",
                getSourceIp(headers),
                getDestinationIp(headers),
                null,
                null,
                getDestinationPort(headers),
                getAcknowledgementKey(interactionId, headers, originalFileName, timestamp),
                getFullS3AcknowledgementPath(interactionId, headers, originalFileName, timestamp),
                getFullS3MetadataPath(interactionId, headers, originalFileName, timestamp),
                MessageSourceType.MLLP, getDataBucketName(), getMetadataBucketName(), appConfig.getVersion());
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
    private String getUserAgentFromHL7(String hl7Message, String interactionId) {
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

            if ((sendingApp == null || sendingApp.isBlank())
                    && (sendingFacility == null || sendingFacility.isBlank())) {
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

    /**
     * Build the metadata key
     */
    @Override
    public String getDataKey(String interactionId, Map<String, String> headers, String originalFileName, String timestamp) {
        String prefix = "";
        try {
            String portHeader = headers != null ? headers.getOrDefault(Constants.REQ_X_FORWARDED_PORT, headers.getOrDefault(Constants.REQ_X_FORWARDED_PORT, null)) : null;
            if (portHeader == null) {
                portHeader = HttpUtil.extractDestinationPort(headers);
            }
            if (portHeader != null) {
                try {
                    int requestPort = Integer.parseInt(portHeader);
                    if (!portConfig.isLoaded()) {
                        portConfig.loadConfig();
                    }
                    if (portConfig.isLoaded()) {
                        for (PortConfig.PortEntry entry : portConfig.getPortConfigurationList()) {
                            if (entry.port == requestPort) {
                                String dataDir = entry.dataDir;
                                if (dataDir != null && !dataDir.isBlank()) {
                                    String normalized = dataDir.replaceAll("^/+", "").replaceAll("/+$", "");
                                    if (!normalized.isEmpty()) {
                                        prefix = normalized + "/";
                                    }
                                }
                                break;
                            }
                        }
                    } else {
                        logger.warn("PortConfig not loaded; using default metadata key and no prefix");
                    }
                } catch (NumberFormatException nfe) {
                    logger.warn("Invalid x-forwarded-port header value: {} — using default metadata key and no prefix", portHeader);
                }
            }
        } catch (Exception e) {
            logger.error("Error resolving per-port metadataDir prefix for metadata key", e);
        }

        // build metadata relative key (same semantics as previous default)
        Instant now = Instant.now();
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);
        String datePath = uploadTime.format(Constants.DATE_PATH_FORMATTER);
        String dataKey = String.format("data/%s/%s_%s_ack", datePath, interactionId, timestamp);

        return (prefix.isEmpty() ? "" : prefix) + dataKey;
    }

    /**
     * Build the metadata key
     */
    @Override
    public String getMetaDataKey(String interactionId, Map<String, String> headers, String originalFileName, String timestamp) {
        String prefix = "";
        try {
            String portHeader = headers != null ? headers.getOrDefault(Constants.REQ_X_FORWARDED_PORT, headers.getOrDefault(Constants.REQ_X_FORWARDED_PORT, null)) : null;
            if (portHeader == null) {
                portHeader = HttpUtil.extractDestinationPort(headers);
            }
            if (portHeader != null) {
                try {
                    int requestPort = Integer.parseInt(portHeader);
                    if (!portConfig.isLoaded()) {
                        portConfig.loadConfig();
                    }
                    if (portConfig.isLoaded()) {
                        for (PortConfig.PortEntry entry : portConfig.getPortConfigurationList()) {
                            if (entry.port == requestPort) {
                                String metadataDir = entry.metadataDir;
                                if (metadataDir != null && !metadataDir.isBlank()) {
                                    String normalized = metadataDir.replaceAll("^/+", "").replaceAll("/+$", "");
                                    if (!normalized.isEmpty()) {
                                        prefix = normalized + "/";
                                    }
                                }
                                break;
                            }
                        }
                    } else {
                        logger.warn("PortConfig not loaded; using default metadata key and no prefix");
                    }
                } catch (NumberFormatException nfe) {
                    logger.warn("Invalid x-forwarded-port header value: {} — using default metadata key and no prefix", portHeader);
                }
            }
        } catch (Exception e) {
            logger.error("Error resolving per-port metadataDir prefix for metadata key", e);
        }

        // build metadata relative key (same semantics as previous default)
        Instant now = Instant.now();
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);
        String datePath = uploadTime.format(Constants.DATE_PATH_FORMATTER);
        String metaKey = String.format("metadata/%s/%s_%s_metadata.json", datePath, interactionId, timestamp);

        return (prefix.isEmpty() ? "" : prefix) + metaKey;
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

    // small helper for safe, truncated logging of ACK/NACK bodies
    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...(truncated)";
    }
}
