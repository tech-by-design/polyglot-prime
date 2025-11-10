package org.techbd.ingest.listener;

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
import org.techbd.ingest.MessageSourceProvider;
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

@Component
public class MllpRoute extends RouteBuilder implements MessageSourceProvider {

    private final TemplateLogger logger;
    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private final PortConfig portConfig;
    private final PortConfigUtil portConfigUtil;

    @Value("${MLLP_DISPATCHER_PORT:2575}")
    private int port;

    public MllpRoute(MessageProcessorService messageProcessorService,
            AppConfig appConfig,
            AppLogger appLogger,
            PortConfig portConfig,
            PortConfigUtil portConfigUtil) {
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.portConfig = portConfig;
        this.portConfigUtil = portConfigUtil;
        this.logger = appLogger.getLogger(MllpRoute.class);
    }

    @Override
    public void configure() throws Exception {
        from("mllp://0.0.0.0:" + port + "?autoAck=false&allowDefaultCodec=false")
                .routeId("hl7-mllp-listener-" + port)
                .log("[MLLP_PORT " + port + "] Received HL7 message")
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
                    processHl7Message(exchange, parseResult, portEntryOpt, interactionId);
                }
            }
        } catch (Exception e) {
            logger.error("[MLLP_PORT {}] Unexpected error in message processing. interactionId={}",
                    port, interactionId, e);
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
            logger.error("[MLLP_PORT {}] Failed to parse incoming message, interactionId={}", port, interactionId);
            throw new IllegalArgumentException("Failed to parse incoming message, interactionId=" + interactionId);
        }
        if (parseResult.proxyInfo == null) {
            logger.error("[MLLP_PORT {}] No Proxy Protocol information detected, interactionId={}", port, interactionId);
            throw new IllegalArgumentException(
                    "No Proxy Protocol information detected, interactionId=" + interactionId);
        }
        if (parseResult.proxyInfo.dstPort <= 0) {
            logger.error("[MLLP_PORT {}] Invalid Proxy Protocol destination port: {}, interactionId={}",
                    port, parseResult.proxyInfo.dstPort, interactionId);
            throw new IllegalArgumentException("Invalid Proxy Protocol destination port: "
                    + parseResult.proxyInfo.dstPort + ", interactionId=" + interactionId);
        }
        logger.info("[MLLP_PORT {}] Proxy Protocol validation successful - dstPort: {}, interactionId={}",
                port, parseResult.proxyInfo.dstPort, interactionId);
        return true;
    }

    /**
     * Parse incoming message: extract Proxy Protocol header and HL7 payload.
     * Strategy Pattern: Different parsing strategy based on presence of Proxy
     * Protocol.
     */
    private MessageParseResult parseIncomingMessage(Exchange exchange, String interactionId) {
        byte[] rawData = exchange.getIn().getBody(byte[].class);
        ProxyProtocolParserUtil.ProxyInfo proxyInfo = null;
        String hl7Message;

        if (rawData != null && ProxyProtocolParserUtil.startsWithProxyProtocolSignature(rawData)) {
            logger.info("[MLLP_PORT {}] Proxy Protocol v2 header detected, interactionId={}", port, interactionId);
            ProxyProtocolParserUtil.ParseResult result = ProxyProtocolParserUtil.parseProxyProtocolV2(rawData,
                    "MLLP_PORT-" + port + "-", interactionId);
            proxyInfo = result.proxyInfo;
            hl7Message = new String(result.payload, java.nio.charset.StandardCharsets.UTF_8);

            if (proxyInfo != null) {
                logger.info(
                        "[MLLP_PORT {}] Proxy Protocol Info - Client: {}:{} → Server: {}:{}, Family: {}, interactionId={}",
                        port, proxyInfo.srcIp, proxyInfo.srcPort,
                        proxyInfo.dstIp, proxyInfo.dstPort,
                        proxyInfo.addressFamily, interactionId);
            }
        } else {
            throw new IllegalArgumentException("No  Proxy Protocol v2 header detected, interactionId=" + interactionId);
        }
        return new MessageParseResult(hl7Message, proxyInfo);
    }

    /**
     * Process HL7 message: parse, extract segments, generate ACK/NACK.
     * Open/Closed Principle: Behavior can be extended through port configuration.
     */
    private void processHl7Message(Exchange exchange, MessageParseResult parseResult,
            Optional<PortConfig.PortEntry> portEntryOpt, String interactionId) {
        GenericParser parser = new GenericParser();
        String nack = null;

        try {
            Message hapiMsg = parser.parse(parseResult.hl7Message);
            Message ack = hapiMsg.generateACK();
            String ackMessage = addNteWithInteractionId(ack, interactionId, appConfig.getVersion());

            // Build request context with port configuration
            RequestContext requestContext = buildRequestContext(
                    exchange, parseResult.hl7Message, interactionId, portEntryOpt,parseResult.proxyInfo);

            // Extract ZNT segment only if response type is "outbound"
            if (shouldProcessZntSegment(portEntryOpt)) {
                extractZntSegment(hapiMsg, requestContext, interactionId);
            }

            // Process message
            messageProcessorService.processMessage(requestContext, parseResult.hl7Message, ackMessage);

            logger.info("[MLLP_PORT {}] Ack generated for interactionId={} ackPreview={}",
                    port, interactionId, ProxyProtocolParserUtil.truncate(ackMessage, 1024));

            exchange.setProperty("CamelMllpAcknowledgementString", ackMessage);
            exchange.getMessage().setBody(ackMessage);

            logger.info("[MLLP_PORT {}] Processed HL7 message successfully; ACK set for interactionId={}",
                    port, interactionId);

        } catch (Exception e) {
            logger.error("[MLLP_PORT {}] Error processing HL7 message. interactionId={} reason={}",
                    port, interactionId, e.getMessage(), e);

            nack = generateNack(parser, parseResult.hl7Message, interactionId, e);

            logger.info("[MLLP_PORT {}] NACK generated for interactionId={} nackPreview={}",
                    port, interactionId, ProxyProtocolParserUtil.truncate(nack, 1024));

            exchange.setProperty("CamelMllpAcknowledgementString", nack);
            exchange.getMessage().setBody(nack);

        } finally {
            logger.info("[MLLP_PORT {}] Completed processing for interactionId={}", port, interactionId);
        }
    }

    /**
     * Determine if ZNT segment should be processed based on port configuration.
     * Open/Closed Principle: Decision based on configuration, not hardcoded.
     */
    private boolean shouldProcessZntSegment(Optional<PortConfig.PortEntry> portEntryOpt) {
        return portEntryOpt.isPresent()
                && "outbound".equalsIgnoreCase(portEntryOpt.get().responseType);
    }

    /**
     * Extract ZNT segment from HL7 message.
     * Single Responsibility: Only handles ZNT extraction.
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
                        "[MLLP_PORT {}] ZNT segment extracted - messageCode={}, deliveryType={}, facility={}, qe={}, interactionId={}",
                        port, messageCode, deliveryType, facilityCode, qe, interactionId);
            } else {
                logger.warn("[MLLP_PORT {}] ZNT segment not found in HL7 message. interactionId={}",
                        port, interactionId);
            }
        } catch (HL7Exception e) {
            logger.error("[MLLP_PORT {}] Error extracting ZNT segment: {} for interactionId={}",
                    port, e.getMessage(), interactionId);
        }
    }

    /**
     * Generate NACK message on error.
     * Single Responsibility: Only handles NACK generation.
     */
    private String generateNack(GenericParser parser, String hl7Message, String interactionId, Exception e) {
        try {
            Message partial = parser.parse(hl7Message);
            Message generatedNack = partial.generateACK(AcknowledgmentCode.AE, new HL7Exception(e.getMessage()));
            return addNteWithInteractionId(generatedNack, interactionId, appConfig.getVersion());
        } catch (Exception ex2) {
            logger.error("[MLLP_PORT {}] Error generating NACK. interactionId={} reason={}",
                    port, interactionId, ex2.getMessage(), ex2);
            return "MSH|^~\\&|UNKNOWN|UNKNOWN|UNKNOWN|UNKNOWN|202507181500||ACK^O01|1|P|2.3\r"
                    + "MSA|AE|1|Error: Unexpected failure\r"
                    + "NTE|1||InteractionID: " + interactionId + "\r";
        }
    }

    /**
     * Build RequestContext with port configuration support.
     * Builder Pattern: Constructs complex RequestContext object.
     * Dependency Inversion: Uses abstractions (PortConfig) rather than concrete
     * implementations.
     */
    private RequestContext buildRequestContext(Exchange exchange, String hl7Message, String interactionId, Optional<PortConfig.PortEntry> portEntryOpt, ProxyProtocolParserUtil.ProxyInfo proxyInfo) {
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
        String fileBaseName = "hl7-message";
        String fileExtension = "hl7";
        String originalFileName = fileBaseName + "." + fileExtension;

        PortBasedPaths paths = portConfigUtil.resolvePortBasedPaths(portEntryOpt, interactionId, headers,
                originalFileName, timestamp, datePath);
        return new RequestContext(
            headers,
            "/hl7",
            portEntryOpt.map(pe -> pe.queue).orElse(null),
            interactionId,
            uploadTime,
            timestamp,
            originalFileName,
            hl7Message.length(),
            paths.getDataKey(),
            paths.getMetaDataKey(),
            paths.getFullS3DataPath(),
            getUserAgentFromHL7(hl7Message, interactionId),
            exchange.getFromEndpoint().getEndpointUri(),
            portEntryOpt.map(pe -> pe.route).orElse(""),
            "MLLP",
            proxyInfo != null ? proxyInfo.dstIp : null,
            proxyInfo != null ? proxyInfo.srcIp : null,
            proxyInfo != null ? proxyInfo.srcIp : null,
            proxyInfo != null ? proxyInfo.dstIp : null,
            proxyInfo != null ? String.valueOf(proxyInfo.dstPort) : null,
            paths.getAcknowledgementKey(),
            paths.getFullS3AcknowledgementPath(),
            paths.getFullS3MetadataPath(),
            MessageSourceType.MLLP,
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
     * Extract user agent from HL7 message (MSH segment).
     * Single Responsibility: Only handles user agent extraction.
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
            logger.error("[MLLP_PORT {}] Error extracting user agent from HL7: {} for interactionId={}",
                    port, e.getMessage(), interactionId);
            return "MLLP Listener";
        }
    }

    // ========== MessageSourceProvider Implementation ==========

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

    // ========== Inner Classes (Data Transfer Objects) ==========

    /**
     * Immutable data class holding parsed message result.
     * Encapsulates parsing outcome for better code organization.
     */
    private static class MessageParseResult {
        final String hl7Message;
        final ProxyProtocolParserUtil.ProxyInfo proxyInfo;

        MessageParseResult(String hl7Message, ProxyProtocolParserUtil.ProxyInfo proxyInfo) {
            this.hl7Message = hl7Message;
            this.proxyInfo = proxyInfo;
        }
    }

 
}