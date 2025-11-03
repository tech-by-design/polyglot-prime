package org.techbd.ingest.listener;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

/**
 * Plain TCP route (no MLLP framing). Sends HL7 ACK/NACK back as raw bytes on the TCP socket.
 */
public class TcpRoute extends RouteBuilder implements MessageSourceProvider {

    private final TemplateLogger logger;
    private final int port;
    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private final PortConfig portConfig;
    private Map<String, String> headers = new HashMap<>();

    public TcpRoute(int port, MessageProcessorService messageProcessorService, AppConfig appConfig, AppLogger appLogger, PortConfig portConfig) {
        this.port = port;
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.portConfig = portConfig;
        this.logger = appLogger.getLogger(TcpRoute.class);
    }

    @Override
    public void configure() throws Exception {
        // Determine available Camel TCP-like component at runtime and choose an endpoint URI.
        // If none are present, skip creating the route to avoid Camel failing to start.
        String endpointUri = null;
        // Use getComponent(...) and check for null to avoid boolean-theory issues across Camel versions.
        if (getContext().getComponent("tcp") != null) {
            endpointUri = "tcp://0.0.0.0:" + port + "?sync=true";
        } else if (getContext().getComponent("netty4") != null) {
            endpointUri = "netty4:tcp://0.0.0.0:" + port + "?sync=true";
        } else if (getContext().getComponent("netty") != null) {
            endpointUri = "netty:tcp://0.0.0.0:" + port + "?sync=true";
        } else {
            logger.warn("[TCP PORT {}] No TCP-capable Camel component (tcp/netty4/netty) found on classpath — skipping TCP route creation", port);
            return;
        }

        from(endpointUri)
                .routeId("hl7-tcp-listener-" + port)
                .log("[TCP PORT " + port + "] Received HL7 message")
                .process(exchange -> {
                    String hl7Message = exchange.getIn().getBody(String.class);
                    GenericParser parser = new GenericParser();
                    String interactionId = UUID.randomUUID().toString();
                    String response = null;

                    String remote = exchange.getIn().getHeader(Constants.CAMEL_MLLP_REMOTE_ADDRESS, String.class);
                    String local = exchange.getIn().getHeader(Constants.CAMEL_MLLP_LOCAL_ADDRESS, String.class);
                    logger.info("[TCP PORT {}] Connection opened: {} -> {} interactionId={}", port, remote, local, interactionId);

                    try {
                        Message hapiMsg = parser.parse(hl7Message);
                        Message ack = hapiMsg.generateACK();
                        String ackMessage = MllpRoute.addNteWithInteractionId(ack, interactionId, appConfig.getVersion());

                        RequestContext requestContext = buildRequestContext(exchange, hl7Message, interactionId);
                        Map<String, String> additionalDetails = requestContext.getAdditionalParameters();
                        if (additionalDetails == null) {
                            additionalDetails = new HashMap<>();
                            requestContext.setAdditionalParameters(additionalDetails);
                        }

                        try {
                            Terser terser = new Terser(hapiMsg);
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
                            }
                        } catch (HL7Exception e) {
                            logger.error("[TCP PORT {}] Error extracting ZNT: {} interactionId={}", port, e.getMessage(), interactionId);
                        }

                        messageProcessorService.processMessage(requestContext, hl7Message, ackMessage);
                        response = ackMessage;
                        // For plain TCP, set body to raw ACK (no MLLP framing)
                        // If client sent MLLP framed message, wrap the response in MLLP frame so MLLP-capable clients (eg. Mirth) will detect it.
                        boolean inboundWasMllp = hl7Message != null && hl7Message.length() > 0 && hl7Message.charAt(0) == '\u000B';
                        if (inboundWasMllp) {
                            String framed = "\u000B" + response + "\u001C\r";
                            exchange.getMessage().setBody(framed);
                            logger.info("[TCP PORT {}] Sent TCP ACK (MLLP-wrapped) for interactionId={} preview={}", port, interactionId, truncate(framed, 512));
                        } else {
                            exchange.getMessage().setBody(response);
                            logger.info("[TCP PORT {}] Sent TCP ACK for interactionId={} preview={}", port, interactionId, truncate(response, 512));
                        }
                    } catch (Exception e) {
                        logger.error("[TCP PORT {}] Error processing message interactionId={} : {}", port, interactionId, e.getMessage(), e);
                        try {
                            Message partial = parser.parse(hl7Message == null ? "" : hl7Message);
                            Message generatedNack = partial.generateACK(AcknowledgmentCode.AE, new HL7Exception(e.getMessage()));
                            response = addNteWithInteractionId(generatedNack, interactionId, appConfig.getVersion());
                        } catch (Throwable ex2) {
                            logger.error("[TCP PORT {}] Failed to generate NACK interactionId={}: {}", port, interactionId, ex2.getMessage(), ex2);
                            response = "MSH|^~\\&|UNKNOWN|UNKNOWN|UNKNOWN|UNKNOWN||ACK^O01|1|P|2.3\rMSA|AE|1\rNTE|1||InteractionID:" + interactionId + "\r";
                        }
                        exchange.getMessage().setBody(response);
                        logger.info("[TCP PORT {}] Sent TCP NACK for interactionId={} preview={}", port, interactionId, truncate(response, 512));
                    } finally {
                        logger.info("[TCP PORT {}] Connection processing completed: {} -> {} interactionId={}", port, remote, local, interactionId);
                    }
                });
    }

    private RequestContext buildRequestContext(org.apache.camel.Exchange exchange, String hl7Message, String interactionId) {
        // reuse same RequestContext builder as MllpRoute by delegating to a simplified approach
        // minimal metadata: keep headers, source type and port etc.
        // Note: keep implementation same as MllpRoute.buildRequestContext to ensure metadata is populated.
        // For brevity call into MllpRoute's helper via shared logic if available; otherwise replicate minimal required fields.
        // Here replicate required pieces similar to MllpRoute.buildRequestContext

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
                hl7Message == null ? 0 : hl7Message.length(),
                getDataKey(interactionId, headers, originalFileName, timestamp),
                getMetaDataKey(interactionId, headers, originalFileName, timestamp),
                getFullS3DataPath(interactionId, headers, originalFileName, timestamp),
                getUserAgentFromHL7(hl7Message, interactionId),
                exchange.getFromEndpoint().getEndpointUri(),
                "",
                "TCP",
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

    // Reuse helper from MllpRoute (addNteWithInteractionId and helper methods)
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

    private String getUserAgentFromHL7(String hl7Message, String interactionId) {
        if (hl7Message == null || !hl7Message.startsWith("MSH")) {
            return "TCP Listener";
        }
        try {
            String[] lines = hl7Message.split("\r|\n");
            String mshLine = lines[0];
            char fieldSeparator = mshLine.charAt(3);
            String[] fields = mshLine.split("\\" + fieldSeparator, -1);
            String sendingApp = (fields.length > 2) ? fields[2] : null;
            String sendingFacility = (fields.length > 3) ? fields[3] : null;
            if ((sendingApp == null || sendingApp.isBlank()) && (sendingFacility == null || sendingFacility.isBlank())) {
                return "TCP Listener";
            }
            if (sendingApp == null || sendingApp.isBlank()) sendingApp = "UnknownApp";
            if (sendingFacility == null || sendingFacility.isBlank()) sendingFacility = "UnknownFacility";
            return sendingApp + "@" + sendingFacility;
        } catch (Exception e) {
            logger.error("Error extracting sending facility from HL7 message: {} for interaction id :{}", e.getMessage(), interactionId);
            return "TCP Listener";
        }
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

    private static String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...(truncated)";
    }
}
