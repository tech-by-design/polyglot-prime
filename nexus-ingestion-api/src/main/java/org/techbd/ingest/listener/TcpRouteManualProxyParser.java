package org.techbd.ingest.listener;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.commons.PortBasedPaths;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.feature.FeatureEnum;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.PortConfigUtil;
import org.techbd.ingest.util.ProxyProtocolParserUtil;
import org.techbd.ingest.util.TemplateLogger;

import ca.uhn.hl7v2.AcknowledgmentCode;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;

/**
 * TCP listener route that manually parses Proxy Protocol v2 headers.
 * This approach doesn't rely on HAProxy decoder and reads raw bytes directly.
 */
@Component
public class TcpRouteManualProxyParser extends RouteBuilder {

    private final TemplateLogger logger;
    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private final PortConfigUtil portConfigUtil;

    @Value("${TCP_DISPATCHER_PORT:7980}")
    private int tcpPort;

    public TcpRouteManualProxyParser(MessageProcessorService messageProcessorService,
                     AppConfig appConfig,
                     AppLogger appLogger,
                     PortConfigUtil portConfigUtil) {
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.portConfigUtil = portConfigUtil;
        this.logger = appLogger.getLogger(TcpRouteManualProxyParser.class);
    }

    @Override
    public void configure() {
        // Use plain netty endpoint without HAProxy decoder
        String endpointUri = "netty:tcp://0.0.0.0:" + tcpPort
                + "?sync=true"
                + "&allowDefaultCodec=false";

        from(endpointUri)
                .routeId("tcp-manual-proxy-" + tcpPort)
                .log("[TCP_PORT " + tcpPort + "] Message received")
                .process(this::processMessage);
    }

    /**
     * Main message processing orchestrator.
     * Coordinates the workflow but delegates specific tasks to helper methods.
     */
    private void processMessage(Exchange exchange) {
        String interactionId = UUID.randomUUID().toString();

        try {
            MessageParseResult parseResult = parseIncomingMessage(exchange, interactionId);
            if (validateParseResult(parseResult, interactionId)) {
                Optional<PortConfig.PortEntry> portEntryOpt = portConfigUtil.readPortEntry(
                        parseResult.proxyInfo.dstPort,
                        interactionId);
                if (portConfigUtil.validatePortEntry(portEntryOpt, parseResult.proxyInfo.dstPort, interactionId)) {
                    processPayload(exchange, parseResult, portEntryOpt, interactionId);
                }
            }
        } catch (Exception e) {
            logger.error("[TCP_PORT {}] Unexpected error in message processing. interactionId={}",
                    tcpPort, interactionId, e);
            exchange.getMessage().setBody("NACK: Processing error - " + e.getMessage());
        }
    }

    /**
     * Validate parse result and proxy protocol information.
     * 
     * @param parseResult   The message parse result to validate
     * @param interactionId The interaction ID for logging
     * @return true if validation passes
     * @throws IllegalArgumentException if validation fails
     */
    public boolean validateParseResult(MessageParseResult parseResult, String interactionId) {
        if (parseResult == null) {
            logger.error("[TCP_PORT {}] Failed to parse incoming message, interactionId={}", tcpPort, interactionId);
            throw new IllegalArgumentException("Failed to parse incoming message, interactionId=" + interactionId);
        }
        if (parseResult.proxyInfo == null) {
            logger.error("[TCP_PORT {}] No Proxy Protocol information detected, interactionId={}", tcpPort, interactionId);
            throw new IllegalArgumentException(
                    "No Proxy Protocol information detected, interactionId=" + interactionId);
        }
        if (parseResult.proxyInfo.dstPort <= 0) {
            logger.error("[TCP_PORT {}] Invalid Proxy Protocol destination port: {}, interactionId={}",
                    tcpPort, parseResult.proxyInfo.dstPort, interactionId);
            throw new IllegalArgumentException("Invalid Proxy Protocol destination port: "
                    + parseResult.proxyInfo.dstPort + ", interactionId=" + interactionId);
        }
        logger.info("[TCP_PORT {}] Proxy Protocol validation successful - dstPort: {}, interactionId={}",
                tcpPort, parseResult.proxyInfo.dstPort, interactionId);
        return true;
    }

    /**
     * Parse incoming message: extract Proxy Protocol header and payload.
     * Strategy Pattern: Different parsing strategy based on presence of Proxy Protocol.
     */
    private MessageParseResult parseIncomingMessage(Exchange exchange, String interactionId) {
        byte[] rawData = exchange.getIn().getBody(byte[].class);
        ProxyProtocolParserUtil.ProxyInfo proxyInfo = null;
        String payloadMessage;

        if (rawData != null && ProxyProtocolParserUtil.startsWithProxyProtocolSignature(rawData)) {
            logger.info("[TCP_PORT {}] Proxy Protocol v2 header detected, interactionId={}", tcpPort, interactionId);
            ProxyProtocolParserUtil.ParseResult result = ProxyProtocolParserUtil.parseProxyProtocolV2(rawData,
                    "TCP_PORT-" + tcpPort + "-", interactionId);
            proxyInfo = result.proxyInfo;
            payloadMessage = new String(result.payload, StandardCharsets.UTF_8);

            if (proxyInfo != null) {
                logger.info(
                        "[TCP_PORT {}] Proxy Protocol Info - Client: {}:{} → Server: {}:{}, Family: {}, interactionId={}",
                        tcpPort, proxyInfo.srcIp, proxyInfo.srcPort,
                        proxyInfo.dstIp, proxyInfo.dstPort,
                        proxyInfo.addressFamily, interactionId);
            }
        } else {
            throw new IllegalArgumentException("No Proxy Protocol v2 header detected, interactionId=" + interactionId);
        }

        return new MessageParseResult(payloadMessage, proxyInfo, isHl7Message(payloadMessage));
    }

    /**
     * Determine if payload is an HL7 message.
     */
    private boolean isHl7Message(String message) {
        return message != null && message.trim().startsWith("MSH");
    }

    /**
     * Process payload: handle HL7 or plain text accordingly.
     */
    private void processPayload(Exchange exchange, MessageParseResult parseResult,
            Optional<PortConfig.PortEntry> portEntryOpt, String interactionId) {
        
        if (parseResult.isHl7) {
            logger.info("[TCP_PORT {}] Processing as HL7 message, interactionId={}", tcpPort, interactionId);
            processHl7Message(exchange, parseResult, portEntryOpt, interactionId);
        } else {
            logger.info("[TCP_PORT {}] Processing as plain text message, interactionId={}", tcpPort, interactionId);
            processTextMessage(exchange, parseResult, portEntryOpt, interactionId);
        }
    }

    /**
     * Process plain text message.
     */
    private void processTextMessage(Exchange exchange, MessageParseResult parseResult,
            Optional<PortConfig.PortEntry> portEntryOpt, String interactionId) {
        try {
            // Build request context
            RequestContext requestContext = buildRequestContext(
                    exchange, parseResult.payloadMessage, interactionId, portEntryOpt, parseResult.proxyInfo, "txt");

            // Create simple ACK response
            String ackMessage = "ACK: Message received successfully. InteractionID: " + interactionId;

            // Process message
            messageProcessorService.processMessage(requestContext, parseResult.payloadMessage, ackMessage);

            logger.info("[TCP_PORT {}] Text message processed successfully, interactionId={}", tcpPort, interactionId);

            exchange.getMessage().setBody(ackMessage);

        } catch (Exception e) {
            logger.error("[TCP_PORT {}] Error processing text message. interactionId={} reason={}",
                    tcpPort, interactionId, e.getMessage(), e);
            
            String nack = "NACK: Processing failed. InteractionID: " + interactionId + " Error: " + e.getMessage();
            exchange.getMessage().setBody(nack);
        }
    }

    /**
     * Process HL7 message: parse, extract segments, generate ACK/NACK.
     */
    private void processHl7Message(Exchange exchange, MessageParseResult parseResult,
            Optional<PortConfig.PortEntry> portEntryOpt, String interactionId) {
        GenericParser parser = new GenericParser();
        String nack = null;

        try {
            Message hapiMsg = parser.parse(parseResult.payloadMessage);
            Message ack = hapiMsg.generateACK();
            String ackMessage = addNteWithInteractionId(ack, interactionId, appConfig.getVersion());

            // Build request context with port configuration
            RequestContext requestContext = buildRequestContext(
                    exchange, parseResult.payloadMessage, interactionId, portEntryOpt, parseResult.proxyInfo, "hl7");

            // Extract ZNT segment only if response type is "outbound"
            if (shouldProcessZntSegment(portEntryOpt)) {
                extractZntSegment(hapiMsg, requestContext, interactionId);
            }

            // Process message
            messageProcessorService.processMessage(requestContext, parseResult.payloadMessage, ackMessage);

            logger.info("[TCP_PORT {}] HL7 ACK generated for interactionId={} ackPreview={}",
                    tcpPort, interactionId, ProxyProtocolParserUtil.truncate(ackMessage, 1024));

            exchange.getMessage().setBody(ackMessage);

            logger.info("[TCP_PORT {}] Processed HL7 message successfully; ACK set for interactionId={}",
                    tcpPort, interactionId);

        } catch (Exception e) {
            logger.error("[TCP_PORT {}] Error processing HL7 message. interactionId={} reason={}",
                    tcpPort, interactionId, e.getMessage(), e);

            nack = generateNack(parser, parseResult.payloadMessage, interactionId, e);

            logger.info("[TCP_PORT {}] HL7 NACK generated for interactionId={} nackPreview={}",
                    tcpPort, interactionId, ProxyProtocolParserUtil.truncate(nack, 1024));

            exchange.getMessage().setBody(nack);

        } finally {
            logger.info("[TCP_PORT {}] Completed processing for interactionId={}", tcpPort, interactionId);
        }
    }

    /**
     * Determine if ZNT segment should be processed based on port configuration.
     */
    private boolean shouldProcessZntSegment(Optional<PortConfig.PortEntry> portEntryOpt) {
        return portEntryOpt.isPresent()
                && "outbound".equalsIgnoreCase(portEntryOpt.get().responseType);
    }

    /**
     * Extract ZNT segment from HL7 message.
     */
    private void extractZntSegment(Message hapiMsg, RequestContext requestContext, String interactionId) {
        try {
            Terser terser = new Terser(hapiMsg);
            Segment znt = terser.getSegment(".ZNT");

            if (znt != null) {
                Map<String, String> additionalDetails = requestContext.getAdditionalParameters();
                if (additionalDetails == null) {
                    additionalDetails = new HashMap<>();
                    requestContext.setAdditionalParameters(additionalDetails);
                }

                String messageCode = terser.get("/.ZNT-2-1"); // ZNT.2.1
                String deliveryType = terser.get("/.ZNT-4-1"); // ZNT.4.1
                String znt8_1 = terser.get("/.ZNT-8-1"); // ZNT.8.1 (e.g., healthelink:GHC)

                String facilityCode = null;
                String qe = null;

                if (znt8_1 != null && znt8_1.contains(":")) {
                    String[] parts = znt8_1.split(":");
                    qe = parts[0]; // part before ':', e.g., healthelink
                    facilityCode = parts.length > 1 ? parts[1] : null; // part after ':', e.g., GHC
                } else if (znt8_1 != null) {
                    facilityCode = znt8_1;
                }

                additionalDetails.put(Constants.MESSAGE_CODE, messageCode);
                additionalDetails.put(Constants.DELIVERY_TYPE, deliveryType);
                additionalDetails.put(Constants.FACILITY, facilityCode);
                additionalDetails.put(Constants.QE, qe);

                logger.info(
                        "[TCP_PORT {}] ZNT segment extracted - messageCode={}, deliveryType={}, facility={}, qe={}, interactionId={}",
                        tcpPort, messageCode, deliveryType, facilityCode, qe, interactionId);
            } else {
                logger.warn("[TCP_PORT {}] ZNT segment not found in HL7 message. interactionId={}",
                        tcpPort, interactionId);
            }
        } catch (HL7Exception e) {
            logger.error("[TCP_PORT {}] Error extracting ZNT segment: {} for interactionId={}",
                    tcpPort, e.getMessage(), interactionId);
        }
    }

    /**
     * Generate NACK message on error.
     */
    private String generateNack(GenericParser parser, String hl7Message, String interactionId, Exception e) {
        try {
            Message partial = parser.parse(hl7Message);
            Message generatedNack = partial.generateACK(AcknowledgmentCode.AE, new HL7Exception(e.getMessage()));
            return addNteWithInteractionId(generatedNack, interactionId, appConfig.getVersion());
        } catch (Exception ex2) {
            logger.error("[TCP_PORT {}] Error generating NACK. interactionId={} reason={}",
                    tcpPort, interactionId, ex2.getMessage(), ex2);
            return "MSH|^~\\&|UNKNOWN|UNKNOWN|UNKNOWN|UNKNOWN|202507181500||ACK^O01|1|P|2.3\r"
                    + "MSA|AE|1|Error: Unexpected failure\r"
                    + "NTE|1||InteractionID: " + interactionId + "\r";
        }
    }

    /**
     * Build RequestContext with port configuration support.
     */
    private RequestContext buildRequestContext(Exchange exchange, String message, String interactionId,
            Optional<PortConfig.PortEntry> portEntryOpt, ProxyProtocolParserUtil.ProxyInfo proxyInfo,
            String fileExtension) {
        ZonedDateTime uploadTime = ZonedDateTime.now();
        String timestamp = String.valueOf(uploadTime.toInstant().toEpochMilli());

        Map<String, String> headers = new HashMap<>();
        exchange.getIn().getHeaders().forEach((k, v) -> {
            if (v instanceof String) {
                headers.put(k, (String) v);
                if (FeatureEnum.isEnabled(FeatureEnum.DEBUG_LOG_REQUEST_HEADERS)) {
                    logger.info("{} -Header for the InteractionId {} : {} = {}",
                            FeatureEnum.DEBUG_LOG_REQUEST_HEADERS, interactionId, k, v);
                }
            }
        });

        String datePath = uploadTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileBaseName = "tcp-message";
        String originalFileName = fileBaseName + "." + fileExtension;

        PortBasedPaths paths = portConfigUtil.resolvePortBasedPaths(portEntryOpt, interactionId, headers,
                originalFileName, timestamp, datePath);

        return new RequestContext(
                headers,
                "/tcp",
                paths.getQueue(),
                interactionId,
                uploadTime,
                timestamp,
                originalFileName,
                message.length(),
                paths.getDataKey(),
                paths.getMetaDataKey(),
                paths.getFullS3DataPath(),
                getUserAgent(message, interactionId),
                exchange.getFromEndpoint().getEndpointUri(),
                portEntryOpt.map(pe -> pe.route).orElse(""),
                "TCP",
                proxyInfo != null ? proxyInfo.dstIp : null,
                proxyInfo != null ? proxyInfo.srcIp : null,
                proxyInfo != null ? proxyInfo.srcIp : null,
                proxyInfo != null ? proxyInfo.dstIp : null,
                proxyInfo != null ? String.valueOf(proxyInfo.dstPort) : null,
                paths.getAcknowledgementKey(),
                paths.getFullS3AcknowledgementPath(),
                paths.getFullS3MetadataPath(),
                MessageSourceType.TCP,
                paths.getDataBucketName(),
                paths.getMetadataBucketName(),
                appConfig.getVersion());
    }

    /**
     * Add NTE segment with interaction ID to ACK message.
     */
    public static String addNteWithInteractionId(Message ackMessage, String interactionId,
            String ingestionApiVersion) throws HL7Exception {
        Terser terser = new Terser(ackMessage);
        ackMessage.addNonstandardSegment("NTE");
        terser.set("/NTE(0)-1", "1");
        terser.set("/NTE(0)-3",
                "InteractionID: " + interactionId
                        + " | TechBDIngestionApiVersion: " + ingestionApiVersion);
        PipeParser parser = new PipeParser();
        return parser.encode(ackMessage);
    }

    /**
     * Extract user agent from message.
     * For HL7: extract from MSH segment
     * For text: use default
     */
    private String getUserAgent(String message, String interactionId) {
        if (message == null || !message.startsWith("MSH")) {
            return "TCP Listener";
        }

        try {
            String[] lines = message.split("\r|\n");
            String mshLine = lines[0];
            char fieldSeparator = mshLine.charAt(3);

            String[] fields = mshLine.split("\\" + fieldSeparator, -1);
            String sendingApp = (fields.length > 2) ? fields[2] : null;
            String sendingFacility = (fields.length > 3) ? fields[3] : null;

            if ((sendingApp == null || sendingApp.isBlank())
                    && (sendingFacility == null || sendingFacility.isBlank())) {
                return "TCP Listener";
            }

            if (sendingApp == null || sendingApp.isBlank()) {
                sendingApp = "UnknownApp";
            }

            if (sendingFacility == null || sendingFacility.isBlank()) {
                sendingFacility = "UnknownFacility";
            }

            return sendingApp + "@" + sendingFacility;
        } catch (Exception e) {
            logger.error("[TCP_PORT {}] Error extracting user agent: {} for interactionId={}",
                    tcpPort, e.getMessage(), interactionId);
            return "TCP Listener";
        }
    }

    // ========== Inner Classes (Data Transfer Objects) ==========

    /**
     * Immutable data class holding parsed message result.
     */
    private static class MessageParseResult {
        final String payloadMessage;
        final ProxyProtocolParserUtil.ProxyInfo proxyInfo;
        final boolean isHl7;

        MessageParseResult(String payloadMessage, ProxyProtocolParserUtil.ProxyInfo proxyInfo, boolean isHl7) {
            this.payloadMessage = payloadMessage;
            this.proxyInfo = proxyInfo;
            this.isHl7 = isHl7;
        }
    }
}