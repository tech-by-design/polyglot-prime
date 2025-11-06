package org.techbd.ingest.listener;

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

import io.netty.handler.codec.haproxy.HAProxyMessage;

/**
 * Plain TCP route (no MLLP framing). Sends HL7 ACK/NACK back as raw bytes on the TCP socket.
 * 
 * Enhanced with support for Proxy Protocol (HAProxy Protocol) which allows load balancers
 * and proxies to pass the original client connection information to backend servers.
 * 
 * Features:
 * - Standard TCP connection handling via Apache Camel
 * - Optional Proxy Protocol v1/v2 support for real client IP extraction
 * - Comprehensive connection logging (direct vs proxy connections)
 * - HL7 message processing with ACK/NACK responses
 * - Configurable MLLP framing based on port configuration
 */
public class TcpRoute extends RouteBuilder implements MessageSourceProvider {

    private final TemplateLogger logger;
    private final int port;
    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private final PortConfig portConfig;
    private final Map<String, String> headers = new HashMap<>();
    private final boolean proxyProtocolEnabled;

    public TcpRoute(int port, MessageProcessorService messageProcessorService, AppConfig appConfig, AppLogger appLogger, PortConfig portConfig) {
        this(port, messageProcessorService, appConfig, appLogger, portConfig, false);
    }

    public TcpRoute(int port, MessageProcessorService messageProcessorService, AppConfig appConfig, AppLogger appLogger, PortConfig portConfig, boolean proxyProtocolEnabled) {
        this.port = port;
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.portConfig = portConfig;
        this.proxyProtocolEnabled = proxyProtocolEnabled;
        this.logger = appLogger.getLogger(TcpRoute.class);
    }

    /**
     * Factory method to create a TcpRoute with Proxy Protocol support enabled.
     * 
     * @param port TCP port to listen on
     * @param messageProcessorService Service for processing HL7 messages
     * @param appConfig Application configuration
     * @param appLogger Application logger factory
     * @param portConfig Port configuration for routing decisions
     * @return TcpRoute instance with Proxy Protocol support enabled
     */
    public static TcpRoute withProxyProtocol(int port, MessageProcessorService messageProcessorService, 
                                           AppConfig appConfig, AppLogger appLogger, PortConfig portConfig) {
        return new TcpRoute(port, messageProcessorService, appConfig, appLogger, portConfig, true);
    }

    /**
     * Factory method to create a standard TcpRoute without Proxy Protocol support.
     * 
     * @param port TCP port to listen on
     * @param messageProcessorService Service for processing HL7 messages
     * @param appConfig Application configuration
     * @param appLogger Application logger factory
     * @param portConfig Port configuration for routing decisions
     * @return TcpRoute instance with standard TCP connection handling
     */
    public static TcpRoute standard(int port, MessageProcessorService messageProcessorService, 
                                  AppConfig appConfig, AppLogger appLogger, PortConfig portConfig) {
        return new TcpRoute(port, messageProcessorService, appConfig, appLogger, portConfig, false);
    }

    /**
     * Check if Proxy Protocol support is enabled for this route.
     * 
     * @return true if Proxy Protocol is enabled, false otherwise
     */
    public boolean isProxyProtocolEnabled() {
        return proxyProtocolEnabled;
    }

    @Override
    public void configure() throws Exception {
        // Determine available Camel TCP-like component at runtime and choose an endpoint URI.
        // If none are present, skip creating the route to avoid Camel failing to start.
        String endpointUri = null;
        // Use getComponent(...) and check for null to avoid boolean-theory issues across Camel versions.
        if (getContext().getComponent("tcp") != null) {
            endpointUri = "tcp://0.0.0.0:" + port + "?sync=true";
        } else if (getContext().getComponent("netty") != null) {
            // Configure Netty with optional Proxy Protocol support (Camel 4.x uses 'netty' component)
            if (proxyProtocolEnabled) {
                endpointUri = "netty://tcp://0.0.0.0:" + port + "?sync=true&decoders=#haProxyDecoder";
                logger.info("[TCP PORT {}] Proxy Protocol support enabled", port);
            } else {
                endpointUri = "netty://tcp://0.0.0.0:" + port + "?sync=true";
            }
        } else if (getContext().getComponent("netty4") != null) {
            // Legacy support for older Camel versions with netty4 component
            if (proxyProtocolEnabled) {
                endpointUri = "netty4://tcp://0.0.0.0:" + port + "?sync=true&decoders=#haProxyDecoder";
                logger.info("[TCP PORT {}] Proxy Protocol support enabled (netty4)", port);
            } else {
                endpointUri = "netty4://tcp://0.0.0.0:" + port + "?sync=true";
            }
        } else {
            logger.warn("[TCP PORT {}] No TCP-capable Camel component (tcp/netty/netty4) found on classpath — skipping TCP route creation", port);
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
                    
                    // Normalize exchange headers (Map<String,Object>) to Map<String,String> expected by helpers
                    try {
                        this.headers.clear();
                        Map<String, Object> rawHeaders = exchange.getIn().getHeaders();
                        if (rawHeaders != null) {
                            rawHeaders.forEach((k, v) -> {
                                if (k == null) return;
                                if (v == null) return;
                                if (v instanceof String) {
                                    this.headers.put(k, (String) v);
                                } else {
                                    this.headers.put(k, String.valueOf(v));
                                }
                            });
                        }
                        
                        // Log all available headers for debugging connection details
                        if (this.headers.isEmpty()) {
                            logger.info("[TCP PORT {}] No headers available for interactionId={}", port, interactionId);
                        } else {
                            logger.info("[TCP PORT {}] All available headers for interactionId={} (count={})", port, interactionId, this.headers.size());
                            this.headers.forEach((key, value) -> {
                                logger.info("[TCP PORT {}] Header [{}] = [{}] interactionId={}", port, key, value, interactionId);
                            });
                        }
                    } catch (Exception ex) {
                        logger.warn("[TCP PORT {}] Failed to normalize request headers: {}", port, ex.getMessage());
                    }

                    // Extract connection details from Camel headers
                    String remote = exchange.getIn().getHeader(Constants.CAMEL_MLLP_REMOTE_ADDRESS, String.class);
                    String local = exchange.getIn().getHeader(Constants.CAMEL_MLLP_LOCAL_ADDRESS, String.class);
                    
                    // Initialize connection details
                    String clientIP = null;
                    Integer clientPort = null;
                    String serverIP = null;
                    Integer serverPort = null;
                    boolean isProxyProtocol = false;
                    
                    // Check for Proxy Protocol information first
                    if (proxyProtocolEnabled) {
                        Object proxyMsg = exchange.getIn().getHeader("CamelNettyProxyMessage");
                        if (proxyMsg instanceof HAProxyMessage) {
                            HAProxyMessage haProxy = (HAProxyMessage) proxyMsg;
                            clientIP = haProxy.sourceAddress();
                            clientPort = haProxy.sourcePort();
                            serverIP = haProxy.destinationAddress();
                            serverPort = haProxy.destinationPort();
                            isProxyProtocol = true;
                            
                            logger.info("[TCP PORT {}] Proxy Protocol - Real Client: {}:{} -> Real Server: {}:{} (via Proxy: {}) interactionId={}", 
                                port, clientIP, clientPort, serverIP, serverPort, remote, interactionId);
                        }
                    }
                    
                    // Fallback to standard connection parsing if no Proxy Protocol info
                    if (!isProxyProtocol) {
                        // Parse IP and port from remote address
                        if (remote != null && remote.contains(":")) {
                            int lastColon = remote.lastIndexOf(':');
                            clientIP = remote.substring(0, lastColon);
                            try {
                                clientPort = Integer.parseInt(remote.substring(lastColon + 1));
                            } catch (NumberFormatException e) {
                                logger.warn("[TCP PORT {}] Failed to parse client port from remote address: {}", port, remote);
                            }
                        }
                        
                        // Parse IP and port from local address  
                        if (local != null && local.contains(":")) {
                            int lastColon = local.lastIndexOf(':');
                            serverIP = local.substring(0, lastColon);
                            try {
                                serverPort = Integer.parseInt(local.substring(lastColon + 1));
                            } catch (NumberFormatException e) {
                                logger.warn("[TCP PORT {}] Failed to parse server port from local address: {}", port, local);
                            }
                        }
                    }
                    
                    // Log extracted connection details with Proxy Protocol awareness
                    if (isProxyProtocol) {
                        logger.info("[TCP PORT {}] Proxy Protocol Connection - Real Client: {}:{} -> Real Server: {}:{} interactionId={}", 
                            port, clientIP, clientPort, serverIP, serverPort, interactionId);
                        logger.info("[TCP PORT {}] Proxy Connection - Via: {} Local: {} interactionId={}", 
                            port, remote, local, interactionId);
                    } else {
                        logger.info("[TCP PORT {}] Direct Connection - Client: {}:{} -> Server: {}:{} interactionId={}", 
                            port, clientIP, clientPort, serverIP, serverPort, interactionId);
                        logger.info("[TCP PORT {}] Raw Connection - Remote: {} Local: {} interactionId={}", 
                            port, remote, local, interactionId);
                    }

                    // determine destination port from headers (x-forwarded-port) or endpoint local address
                    String portHeader = exchange.getIn().getHeader(Constants.REQ_X_FORWARDED_PORT, String.class);
                    if (portHeader == null || portHeader.isBlank()) {
                        // use normalized headers map (Map<String,String>) to avoid incompatible Map<String,Object> -> Map<String,String>
                        portHeader = HttpUtil.extractDestinationPort(this.headers);
                    }
                    Integer requestPort = null;
                    if (portHeader != null) {
                        try {
                            requestPort = Integer.parseInt(portHeader);
                        } catch (NumberFormatException nfe) {
                            logger.warn("[TCP PORT {}] Invalid port header value: '{}' ; falling back to route listen port", port, portHeader);
                        }
                    }
                    // lookup config for the destination port (if found); else use listen port
                    PortConfig.PortEntry portEntry = null;
                    if (requestPort != null) {
                        portEntry = portConfig.findEntryForPort(requestPort).orElse(null);
                    }
                    boolean configuredAsMllp = false;
                    if (portEntry != null) {
                        String proto = portEntry.protocol == null ? "" : portEntry.protocol.trim();
                        String resp = portEntry.responseType == null ? "" : portEntry.responseType.trim();
                        configuredAsMllp = "tcp".equalsIgnoreCase(proto) && "mllp".equalsIgnoreCase(resp);
                    }

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

                        // Decide framing based on portConfig entry for destination port
                        if (configuredAsMllp) {
                            String framed = "\u000B" + response + "\u001C\r";
                            exchange.getMessage().setBody(framed);
                            logger.info("[TCP PORT {}] Sent ACK (MLLP-framed) for interactionId={} preview={}", port, interactionId, truncate(framed, 512));
                        } else {
                            // plain TCP response (no MLLP framing)
                            exchange.getMessage().setBody(response);
                            logger.info("[TCP PORT {}] Sent ACK (plain TCP) for interactionId={} preview={}", port, interactionId, truncate(response, 512));
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

                        // decide framing for NACK as well
                        if (configuredAsMllp) {
                            String framedNack = "\u000B" + response + "\u001C\r";
                            exchange.getMessage().setBody(framedNack);
                            logger.info("[TCP PORT {}] Sent NACK (MLLP-framed) for interactionId={} preview={}", port, interactionId, truncate(framedNack, 512));
                        } else {
                            exchange.getMessage().setBody(response);
                            logger.info("[TCP PORT {}] Sent NACK (plain TCP) for interactionId={} preview={}", port, interactionId, truncate(response, 512));
                        }
                    } finally {
                        logger.info("[TCP PORT {}] Connection processing completed: {} -> {} interactionId={}", port, remote, local, interactionId);
                    }
                });
    }

    private RequestContext buildRequestContext(org.apache.camel.Exchange exchange, String hl7Message, String interactionId) {

        ZonedDateTime uploadTime = ZonedDateTime.now();
        String timestamp = String.valueOf(uploadTime.toInstant().toEpochMilli());

        // Normalize headers once (exchange headers are Map<String,Object>)
        this.headers.clear();
        Map<String, Object> raw = exchange.getIn().getHeaders();
        if (raw != null) {
            raw.forEach((k, v) -> {
                if (k == null || v == null) return;
                String sval = v instanceof String ? (String) v : String.valueOf(v);
                this.headers.put(k, sval);
                if (FeatureEnum.isEnabled(FeatureEnum.DEBUG_LOG_REQUEST_HEADERS)) {
                    logger.info("{} -Header for the InteractionId {} :  {} = {}", FeatureEnum.DEBUG_LOG_REQUEST_HEADERS, interactionId, k, sval);
                }
            });
        }

        // Use the normalized Map<String,String> (this.headers) for all downstream helpers
        String datePath = uploadTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileBaseName = "hl7-message";
        String fileExtension = "hl7";
        String originalFileName = fileBaseName + "." + fileExtension;

        return new RequestContext(
                this.headers,
                "/hl7",
                null,
                interactionId,
                uploadTime,
                timestamp,
                originalFileName,
                hl7Message == null ? 0 : hl7Message.length(),
                getDataKey(interactionId, this.headers, originalFileName, timestamp),
                getMetaDataKey(interactionId, this.headers, originalFileName, timestamp),
                getFullS3DataPath(interactionId, this.headers, originalFileName, timestamp),
                getUserAgentFromHL7(hl7Message, interactionId),
                exchange.getFromEndpoint().getEndpointUri(),
                "",
                "TCP",
                getSourceIp(this.headers),
                getDestinationIp(this.headers),
                null,
                null,
                getDestinationPort(this.headers),
                getAcknowledgementKey(interactionId, this.headers, originalFileName, timestamp),
                getFullS3AcknowledgementPath(interactionId, this.headers, originalFileName, timestamp),
                getFullS3MetadataPath(interactionId, this.headers, originalFileName, timestamp),
                MessageSourceType.MLLP,
                getDataBucketName(),
                getMetadataBucketName(),
                appConfig.getVersion()
        );
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
