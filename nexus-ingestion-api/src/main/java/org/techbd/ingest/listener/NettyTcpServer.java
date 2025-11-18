package org.techbd.ingest.listener;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.techbd.ingest.MessageSourceProvider;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.commons.PortBasedPaths;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.PortConfigUtil;
import org.techbd.ingest.util.TemplateLogger;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.NoValidation;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import jakarta.annotation.PostConstruct;

@Component
public class NettyTcpServer implements MessageSourceProvider {

    private final TemplateLogger logger;
    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private final PortConfigUtil portConfigUtil;

    @Value("${TCP_DISPATCHER_PORT:6001}")
    private int tcpPort;

    @Value("${TCP_READ_TIMEOUT_SECONDS:30}")
    private int readTimeoutSeconds;

    private static final AttributeKey<String> CLIENT_IP_KEY = AttributeKey.valueOf("CLIENT_IP");
    private static final AttributeKey<Integer> CLIENT_PORT_KEY = AttributeKey.valueOf("CLIENT_PORT");
    private static final AttributeKey<String> DESTINATION_IP_KEY = AttributeKey.valueOf("DESTINATION_IP_KEY");
    private static final AttributeKey<Integer> DESTINATION_PORT_KEY = AttributeKey.valueOf("DESTINATION_PORT_KEY");
    private static final AttributeKey<UUID> INTERACTION_ATTRIBUTE_KEY = AttributeKey
            .valueOf("INTERACTION_ATTRIBUTE_KEY");

    // MLLP protocol markers
    private static final char MLLP_START = '\u000B'; // <VT> Vertical Tab
    private static final char MLLP_END_1 = '\u001C'; // <FS> File Separator
    private static final char MLLP_END_2 = '\r'; // <CR> Carriage Return

    public NettyTcpServer(MessageProcessorService messageProcessorService,
            AppConfig appConfig,
            AppLogger appLogger,
            PortConfigUtil portConfigUtil) {
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.portConfigUtil = portConfigUtil;
        this.logger = appLogger.getLogger(NettyTcpServer.class);
    }

    @PostConstruct
    public void startServer() {
        new Thread(() -> {
            EventLoopGroup boss = new NioEventLoopGroup(1);
            EventLoopGroup worker = new NioEventLoopGroup();

            try {
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(boss, worker)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                // Add read timeout handler
                                ch.pipeline().addLast(new ReadTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS));
                                String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");

                                // HAProxy protocol support
                                if (!"sandbox".equals(activeProfile)) {
                                    ch.pipeline().addLast(new HAProxyMessageDecoder());
                                }
                                // String encoding/decoding
                                ch.pipeline().addLast(new StringDecoder());
                                ch.pipeline().addLast(new StringEncoder());

                                // Main message handler
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                        UUID interactionId = ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).get();
                                        if (interactionId == null) {
                                            interactionId = UUID.randomUUID();
                                            ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).set(interactionId);
                                        }
                                        String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
                                        if ("sandbox".equals(activeProfile)) {
                                            handleSandboxProxy(ctx, interactionId);
                                        } else {
                                            if (msg instanceof HAProxyMessage proxyMsg) {
                                                handleProxyHeader(ctx, proxyMsg, interactionId);
                                                return; // Wait for next frame (actual message)
                                            }
                                        }

                                        if (msg instanceof String messageContent) {
                                            handleMessage(ctx, messageContent, interactionId);
                                        }
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                        UUID interactionId = ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).get();
                                        logger.error("Exception in TCP handler for interactionId {}: {}",
                                                interactionId, cause.getMessage(), cause);
                                        ctx.close();
                                    }

                                    @Override
                                    public void channelInactive(ChannelHandlerContext ctx) {
                                        UUID interactionId = ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).get();
                                        logger.debug("Channel closed for interactionId {}", interactionId);
                                    }
                                });
                            }
                        });

                ChannelFuture future = bootstrap.bind(tcpPort).sync();
                logger.info("TCP Server listening on port {} (MLLP=HL7 with ACK, non-MLLP=Generic with simple ACK)",
                        tcpPort);
                future.channel().closeFuture().sync();
            } catch (Exception e) {
                logger.error("Failed to start TCP Server on port {}", tcpPort, e);
            } finally {
                boss.shutdownGracefully();
                worker.shutdownGracefully();
            }
        }).start();
    }

    /**
     * Handle HAProxy protocol header to extract real client IP/port
     */
    private void handleProxyHeader(ChannelHandlerContext ctx, HAProxyMessage proxyMsg, UUID interactionId) {
        if (proxyMsg.command() == HAProxyCommand.PROXY) {
            logger.info("PROXY_HEADER [interactionId={}] sourceAddress={}, sourcePort={}, destAddress={}, destPort={}",
                    interactionId,
                    proxyMsg.sourceAddress(),
                    proxyMsg.sourcePort(),
                    proxyMsg.destinationAddress(),
                    proxyMsg.destinationPort());

            ctx.channel().attr(CLIENT_IP_KEY).set(proxyMsg.sourceAddress());
            ctx.channel().attr(CLIENT_PORT_KEY).set(proxyMsg.sourcePort());
            ctx.channel().attr(DESTINATION_IP_KEY).set(proxyMsg.destinationAddress());
            ctx.channel().attr(DESTINATION_PORT_KEY).set(proxyMsg.destinationPort());
        }
    }

    /**
     * Assign dummy client and destination IP/port for sandbox profile
     */
    private void handleSandboxProxy(ChannelHandlerContext ctx, UUID interactionId) {
        // Assign dummy values
        String dummyClientIp = "127.0.0.1";
        int dummyClientPort = 12345;
        String dummyDestinationIp = "127.0.0.1";
        int dummyDestinationPort = 5555;

        // Log for debugging
        logger.info("SANDBOX_PROXY [interactionId={}] sourceAddress={}, sourcePort={}, destAddress={}, destPort={}",
                interactionId, dummyClientIp, dummyClientPort, dummyDestinationIp, dummyDestinationPort);

        // Set as channel attributes
        ctx.channel().attr(CLIENT_IP_KEY).set(dummyClientIp);
        ctx.channel().attr(CLIENT_PORT_KEY).set(dummyClientPort);
        ctx.channel().attr(DESTINATION_IP_KEY).set(dummyDestinationIp);
        ctx.channel().attr(DESTINATION_PORT_KEY).set(dummyDestinationPort);
    }

    /**
     * Main message handler - routes to HL7 or generic handler based on MLLP
     * detection
     */
    private void handleMessage(ChannelHandlerContext ctx, String rawMessage, UUID interactionId) {
        String clientIP = ctx.channel().attr(CLIENT_IP_KEY).get();
        Integer clientPort = ctx.channel().attr(CLIENT_PORT_KEY).get();
        String destinationIP = ctx.channel().attr(DESTINATION_IP_KEY).get();
        Integer destinationPort = ctx.channel().attr(DESTINATION_PORT_KEY).get();

        // Use direct connection info if proxy headers not present
        if (clientIP == null) {
            clientIP = ctx.channel().remoteAddress().toString();
        }
        if (destinationIP == null) {
            destinationIP = ctx.channel().localAddress().toString();
        }

        boolean isMllpWrapped = detectMllpWrapper(rawMessage);
        logger.info("MESSAGE_RECEIVED [interactionId={}] from={}:{}, size={} bytes MLLP_WRAPPED={}",
                interactionId, clientIP, clientPort, rawMessage.length(), isMllpWrapped ? "YES" : "NO");

        Optional<PortConfig.PortEntry> portEntryOpt = portConfigUtil.readPortEntry(
                destinationPort, interactionId.toString());
        if (detectMllp(portEntryOpt)) {
            logger.info("MLLP_DETECTED [interactionId={}] - Using HL7 processing with proper ACK", interactionId);
            handleHL7Message(ctx, rawMessage, interactionId, clientIP, clientPort,
                    destinationIP, destinationPort, portEntryOpt);
        } else {
            logger.info("NON_MLLP_DETECTED [interactionId={}] - Using generic processing with simple ACK",
                    interactionId);
            handleGenericMessage(ctx, rawMessage, interactionId, clientIP, clientPort,
                    destinationIP, destinationPort, portEntryOpt);
        }
    }

    private void handleHL7Message(
            ChannelHandlerContext ctx,
            String rawMessage,
            UUID interactionId,
            String clientIP,
            Integer clientPort,
            String destinationIP,
            Integer destinationPort,
            Optional<PortConfig.PortEntry> portEntryOpt) {
        String cleanMsg = null;
        String ackMessage = null;
        Message hl7Message = null;
        boolean nackGenerated = false;
        try {
            try {
                cleanMsg = unwrapMllp(rawMessage);
                logger.info("HL7_MESSAGE_UNWRAPPED [interactionId={}] size={} bytes",
                        interactionId, cleanMsg.length());
                HapiContext context = new DefaultHapiContext();
                context.setValidationContext(new NoValidation());
                GenericParser parser = context.getGenericParser();
                hl7Message = parser.parse(cleanMsg);
                Message ack = hl7Message.generateACK();
                ackMessage = addNteWithInteractionId(ack, interactionId.toString(),
                        appConfig.getVersion());
                ackMessage = new PipeParser().encode(ack);
                logger.info("HL7_ACK_GENERATED [interactionId={}]", interactionId);
            } catch (HL7Exception e) {
                logger.error("HL7_PARSE_ERROR [interactionId={}]: Parsing failed due to error {} .. Continue generating manual ACK", interactionId, e.getMessage(), e);
                ackMessage = createHL7AckFromMsh(cleanMsg, "AE", e.getMessage(), interactionId.toString());
                nackGenerated = true;
            }

            if (!portConfigUtil.validatePortEntry(portEntryOpt, destinationPort, interactionId.toString())) {
                logger.warn("INVALID_PORT_CONFIG [interactionId={}] port={}", interactionId, destinationPort);
            }

            RequestContext requestContext = buildRequestContext(
                    cleanMsg,
                    interactionId.toString(),
                    portEntryOpt,
                    String.valueOf(clientPort),
                    clientIP,
                    destinationIP,
                    String.valueOf(destinationPort),
                    MessageSourceType.MLLP);

            // Extract ZNT always if MLLP responseType. If missing, immediately send a NACK.
            if (detectMllp(portEntryOpt)) {
                boolean zntPresent = true;
                if (hl7Message != null) {
                    zntPresent = extractZntSegment(hl7Message, requestContext, interactionId.toString());
                } else {
                    zntPresent = extractZntSegmentManually(cleanMsg, requestContext, interactionId.toString());
                }

                if (!zntPresent) {
                    logger.warn("MISSING_ZNT_NACK [interactionId={}] - sending NACK due to missing ZNT segment", interactionId);
                    String nack = createHL7AckFromMsh(cleanMsg, "AR", "Missing ZNT segment", interactionId.toString());
                    sendResponseAndClose(ctx, wrapMllp(nack), interactionId, "HL7_NACK_MISSING_ZNT");
                    return;
                }
            }
            if (!nackGenerated) {
                messageProcessorService.processMessage(requestContext, cleanMsg, ackMessage);
            }

            // Send MLLP-wrapped ACK
            String response = wrapMllp(ackMessage);
            sendResponseAndClose(ctx, response, interactionId, "HL7_ACK");

        } catch (Exception e) {
            logger.error("PROCESSING_ERROR Sending Reject NACK(AR) [interactionId={}]: {}", interactionId, e.getMessage(), e);
            // Final fallback NACK (AR)
            String errorAck = createHL7AckFromMsh(
                    cleanMsg != null ? cleanMsg : unwrapMllp(rawMessage),
                    "AR",
                    e.getMessage(),
                    interactionId.toString());
            sendResponseAndClose(ctx, wrapMllp(errorAck), interactionId, "HL7_NACK");
        }
    }

    /**
     * Handle non-MLLP messages with simple acknowledgment
     */
    private void handleGenericMessage(ChannelHandlerContext ctx, String rawMessage, UUID interactionId,
            String clientIP, Integer clientPort, String destinationIP, Integer destinationPort, Optional<PortConfig.PortEntry> portEntryOpt) {
        try {
            String cleanMsg = rawMessage.trim();
            if (!portConfigUtil.validatePortEntry(portEntryOpt, destinationPort, interactionId.toString())) {
                logger.warn("INVALID_PORT_CONFIG [interactionId={}] port={}", interactionId, destinationPort);
            }

            RequestContext requestContext = buildRequestContext(
                    cleanMsg,
                    interactionId.toString(),
                    portEntryOpt,
                    String.valueOf(clientPort),
                    clientIP,
                    destinationIP,
                    String.valueOf(destinationPort),
                    MessageSourceType.TCP);

            // Add detected format to metadata
            Map<String, String> additionalParams = requestContext.getAdditionalParameters();
            if (additionalParams == null) {
                additionalParams = new HashMap<>();
                requestContext.setAdditionalParameters(additionalParams);
            }

            // Generate simple acknowledgment
            String ackMessage = generateSimpleAck(interactionId.toString());

            // Process message asynchronously
            messageProcessorService.processMessage(requestContext, cleanMsg, ackMessage);

            // Send simple ACK (with newline, no MLLP wrapping)
            sendResponseAndClose(ctx, ackMessage + "\n", interactionId, "SIMPLE_ACK");

        } catch (Exception e) {
            logger.error("GENERIC_PROCESSING_ERROR [interactionId={}]: {}",
                    interactionId, e.getMessage(), e);
            String errorResponse = generateSimpleNack(interactionId.toString(), e.getMessage()) + "\n";
            sendResponseAndClose(ctx, errorResponse, interactionId, "SIMPLE_NACK");
        }
    }

    /**
     * Send response and close connection
     */
    private void sendResponseAndClose(ChannelHandlerContext ctx, String response,
            UUID interactionId, String responseType) {
        logger.info("SENDING_RESPONSE [interactionId={}] type={}", interactionId, responseType);

        ctx.writeAndFlush(response).addListener(future -> {
            if (future.isSuccess()) {
                logger.info("RESPONSE_SENT_SUCCESS [interactionId={}] type={}, closing connection",
                        interactionId, responseType);
            } else {
                logger.error("RESPONSE_SEND_FAILED [interactionId={}] type={}: {}",
                        interactionId, responseType, future.cause().getMessage());
            }
            ctx.close();
        });
    }

    /**
     * Detect if message is wrapped in MLLP protocol
     */
    private boolean detectMllpWrapper(String message) {
        return message.length() >= 3
                && message.charAt(0) == MLLP_START
                && message.charAt(message.length() - 2) == MLLP_END_1
                && message.charAt(message.length() - 1) == MLLP_END_2;
    }

    /**
     * Remove MLLP wrapper from message
     */
    private String unwrapMllp(String message) {
        if (message.charAt(0) == MLLP_START) {
            message = message.substring(1);
        }
        if (message.length() >= 2
                && message.charAt(message.length() - 2) == MLLP_END_1
                && message.charAt(message.length() - 1) == MLLP_END_2) {
            message = message.substring(0, message.length() - 2);
        }
        return message.trim();
    }

    /**
     * Wrap message in MLLP protocol
     */
    private String wrapMllp(String message) {
        return MLLP_START + message + MLLP_END_1 + MLLP_END_2;
    }

    /**
     * Add NTE segment with interaction ID to HL7 ACK message
     */
    private String addNteWithInteractionId(Message ackMessage, String interactionId,
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
     * Parse MSH segment from HL7 message
     */
    private Map<String, String> parseMshSegment(String hl7Message) {
        Map<String, String> mshFields = new HashMap<>();

        try {
            String[] lines = hl7Message.split("\r|\n");
            String mshLine = null;

            // Find MSH segment
            for (String line : lines) {
                if (line.trim().startsWith("MSH|")) {
                    mshLine = line.trim();
                    break;
                }
            }

            if (mshLine == null) {
                logger.warn("MSH segment not found in message");
                return mshFields;
            }

            // MSH segment format: MSH|^~\&|SendingApp|SendingFacility|ReceivingApp|ReceivingFacility|Timestamp|Security|MessageType|MessageControlId|ProcessingId|Version
            String[] fields = mshLine.split("\\|", -1);

            if (fields.length > 0) {
                mshFields.put("fieldSeparator", "|");
                mshFields.put("encodingCharacters", fields.length > 1 ? fields[1] : "^~\\&");
                mshFields.put("sendingApplication", fields.length > 2 ? fields[2] : "");
                mshFields.put("sendingFacility", fields.length > 3 ? fields[3] : "");
                mshFields.put("receivingApplication", fields.length > 4 ? fields[4] : "");
                mshFields.put("receivingFacility", fields.length > 5 ? fields[5] : "");
                mshFields.put("timestamp", fields.length > 6 ? fields[6] : "");
                mshFields.put("messageType", fields.length > 8 ? fields[8] : "");
                mshFields.put("messageControlId", fields.length > 9 ? fields[9] : "");
                mshFields.put("processingId", fields.length > 10 ? fields[10] : "");
                mshFields.put("version", fields.length > 11 ? fields[11] : "2.5");

                logger.debug("MSH_PARSED messageControlId={}, sendingApp={}, receivingApp={}",
                        mshFields.get("messageControlId"),
                        mshFields.get("sendingApplication"),
                        mshFields.get("receivingApplication"));
            }

        } catch (Exception e) {
            logger.error("Error parsing MSH segment: {}", e.getMessage());
        }

        return mshFields;
    }

    private String createHL7AckFromMsh(String originalMessage, String ackCode, String errorText, String interactionId) {

        Map<String, String> msh = parseMshSegment(originalMessage);

        // If ackCode=AA but there is an error → override to AE (unexpected error)
        String finalAckCode;
        if ("AA".equals(ackCode) && errorText != null && !errorText.trim().isEmpty()) {
            finalAckCode = "AE";
            errorText = "Unexpected error: " + errorText;
            logger.warn("ACK_OVERRIDE [interactionId={}] - AA overridden to AE due to error", interactionId);
        } else {
            finalAckCode = ackCode;
        }

        String fieldSep = msh.getOrDefault("fieldSeparator", "|");
        String encoding = msh.getOrDefault("encodingCharacters", "^~\\&");

        String sendingApp = msh.getOrDefault("sendingApplication", "");
        String sendingFacility = msh.getOrDefault("sendingFacility", "");
        String receivingApp = msh.getOrDefault("receivingApplication", "");
        String receivingFacility = msh.getOrDefault("receivingFacility", "");
        String messageControlId = msh.getOrDefault("messageControlId", "UNKNOWN");
        String version = msh.getOrDefault("version", "2.5");

        String err = (errorText == null)
                ? ""
                : errorText.replace("|", " ").replace("\r", " ").replace("\n", " ");

        // If MSH was completely unreadable → generate minimal NACK
        if (msh.isEmpty()) {
            logger.warn("MSH_MISSING [interactionId={}] - generating minimal NACK", interactionId);

            String genericError = "Message cannot be processed";
            StringBuilder nack = new StringBuilder();

            nack.append("MSH|^~\\&|SERVER|LOCAL|CLIENT|REMOTE|")
                    .append(Instant.now()).append("||ACK|")
                    .append(UUID.randomUUID().toString().substring(0, 20))
                    .append("|P|2.5\r");

            nack.append("MSA|AR|UNKNOWN|")
                    .append(genericError)
                    .append("\r");

            nack.append("ERR|||207^Application internal error^HL70357||E|||")
                    .append(genericError.substring(0, Math.min(80, genericError.length())))
                    .append("\r");

            nack.append("NTE|1||InteractionID: ").append(interactionId)
                    .append(" | TechBDIngestionApiVersion: ").append(appConfig.getVersion())
                    .append("\r");
            return nack.toString();
        }

        // ---------------------------------------------------------
        // Build Standard ACK/NACK using MSH fields available
        // ---------------------------------------------------------
        StringBuilder ack = new StringBuilder();

        // MSH (swap sender & receiver)
        ack.append("MSH").append(fieldSep)
                .append(encoding).append(fieldSep)
                .append(receivingApp.isEmpty() ? "SERVER" : receivingApp).append(fieldSep)
                .append(receivingFacility.isEmpty() ? "LOCAL" : receivingFacility).append(fieldSep)
                .append(sendingApp.isEmpty() ? "CLIENT" : sendingApp).append(fieldSep)
                .append(sendingFacility.isEmpty() ? "REMOTE" : sendingFacility).append(fieldSep)
                .append(Instant.now()).append(fieldSep)
                .append(fieldSep)
                .append("ACK").append(fieldSep)
                .append(UUID.randomUUID().toString().substring(0, 20)).append(fieldSep)
                .append("P").append(fieldSep)
                .append(version).append("\r");

        // MSA
        ack.append("MSA").append(fieldSep)
                .append(finalAckCode).append(fieldSep)
                .append(messageControlId);

        if (!err.isEmpty()) {
            ack.append(fieldSep).append(err);
        }
        ack.append("\r");

        // ERR only if NACK
        if (!"AA".equals(finalAckCode)) {
            ack.append("ERR").append(fieldSep)
                    .append(fieldSep)
                    .append("207^Application error^HL70357").append(fieldSep)
                    .append(fieldSep)
                    .append("E").append(fieldSep)
                    .append(fieldSep)
                    .append(fieldSep)
                    .append(err.substring(0, Math.min(80, err.length())))
                    .append("\r");
        }

        // NTE
        ack.append("NTE").append(fieldSep)
                .append("1").append(fieldSep)
                .append(fieldSep)
                .append("InteractionID: ").append(interactionId)
                .append(" | TechBDIngestionApiVersion: ").append(appConfig.getVersion())
                .append("\r");

        return ack.toString();
    }

    /**
     * Determine if incoming message is based on port configuration
     */
    private boolean detectMllp(Optional<PortConfig.PortEntry> portEntryOpt) {
        return portEntryOpt.isPresent()
                && Optional.ofNullable(portEntryOpt.get().responseType)
                        .map(rt -> rt.equalsIgnoreCase("outbound") || rt.equalsIgnoreCase("mllp"))
                        .orElse(false);
    }

    /**
     * Extract ZNT segment from HL7 message using HAPI parser
     */
    private boolean extractZntSegment(Message hapiMsg, RequestContext requestContext, String interactionId) {
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
                        "ZNT_SEGMENT_EXTRACTED [interactionId={}] messageCode={}, deliveryType={}, facility={}, qe={}",
                        interactionId, messageCode, deliveryType, facilityCode, qe);
                return true;
            } else {
                logger.warn("ZNT_SEGMENT_NOT_FOUND [interactionId={}]", interactionId);
                return false;
            }
        } catch (HL7Exception e) {
            logger.error("ZNT_EXTRACTION_ERROR -- This could be as the ZNT segment is not available [interactionId={}]: Detailed error message {}", interactionId, e.getMessage());
            return false;
        }
    }

    /**
     * Extract ZNT segment manually from HL7 message when HAPI parsing fails
     */
    private boolean extractZntSegmentManually(String hl7Message, RequestContext requestContext, String interactionId) {
        try {
            String[] lines = hl7Message.split("\r|\n");
            String zntLine = null;

            // Find ZNT segment
            for (String line : lines) {
                if (line.trim().startsWith("ZNT|")) {
                    zntLine = line.trim();
                    break;
                }
            }

            if (zntLine == null) {
                logger.warn("ZNT_SEGMENT_NOT_FOUND_MANUAL [interactionId={}]", interactionId);
                return false;
            }

            // ZNT segment format: ZNT|field1|field2|field3|field4|field5|field6|field7|field8...
            String[] fields = zntLine.split("\\|", -1);

            Map<String, String> additionalDetails = requestContext.getAdditionalParameters();
            if (additionalDetails == null) {
                additionalDetails = new HashMap<>();
                requestContext.setAdditionalParameters(additionalDetails);
            }

            // Extract fields based on typical ZNT structure
            String messageCode = null;
            String deliveryType = null;
            String znt8_1 = null;

            // ZNT-2 (component 1) - Message Code
            if (fields.length > 2 && !fields[2].isEmpty()) {
                String[] components = fields[2].split("\\^", -1);
                messageCode = components.length > 0 ? components[0] : null;
            }

            // ZNT-4 (component 1) - Delivery Type
            if (fields.length > 4 && !fields[4].isEmpty()) {
                String[] components = fields[4].split("\\^", -1);
                deliveryType = components.length > 0 ? components[0] : null;
            }

            // ZNT-8 (component 1) - e.g., healthelink:GHC
            if (fields.length > 8 && !fields[8].isEmpty()) {
                String[] components = fields[8].split("\\^", -1);
                znt8_1 = components.length > 0 ? components[0] : null;
            }

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
                    "ZNT_SEGMENT_EXTRACTED_MANUALLY [interactionId={}] messageCode={}, deliveryType={}, facility={}, qe={}",
                    interactionId, messageCode, deliveryType, facilityCode, qe);
            return true;

        } catch (Exception e) {
            logger.error("ZNT_MANUAL_EXTRACTION_ERROR [interactionId={}]: {}", interactionId, e.getMessage());
        }
        return false;
    }

    /**
     * Detect message format based on content (for non-MLLP messages)
     */
    private String detectMessageFormat(String message) {
        if (message == null || message.isBlank()) {
            return "empty";
        }

        String trimmed = message.trim();

        // HL7 detection (starts with MSH segment) - shouldn't happen in non-MLLP
        if (trimmed.startsWith("MSH|") || trimmed.startsWith("MSH^")) {
            return "hl7";
        }

        // JSON detection
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return "json";
        }

        // XML detection
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            if (trimmed.contains("<ClinicalDocument") || trimmed.contains("urn:hl7-org:v3")) {
                return "ccd";
            }
            return "xml";
        }

        // CSV detection (simple heuristic)
        String[] lines = trimmed.split("\n", 3);
        if (lines.length >= 2 && lines[0].contains(",") && lines[1].contains(",")) {
            int firstCommas = lines[0].split(",").length;
            int secondCommas = lines[1].split(",").length;
            if (Math.abs(firstCommas - secondCommas) <= 1) {
                return "csv";
            }
        }

        return "text";
    }

    /**
     * Generate simple acknowledgment for non-MLLP messages
     */
    private String generateSimpleAck(String interactionId) {
        return String.format("ACK|%s|%s|%s",
                interactionId,
                appConfig.getVersion(),
                Instant.now().toString());
    }

    /**
     * Generate simple NACK for non-MLLP messages
     */
    private String generateSimpleNack(String interactionId, String errorMessage) {
        String sanitizedError = errorMessage.replace("|", " ").replace("\n", " ");
        return String.format("NACK|%s|ERROR|%s|%s",
                interactionId,
                sanitizedError,
                Instant.now().toString());
    }

    /**
     * Build RequestContext with port configuration support
     */
    private RequestContext buildRequestContext(String message, String interactionId,
            Optional<PortConfig.PortEntry> portEntryOpt, String sourcePort, String sourceIp,
            String destinationIp, String destinationPort, MessageSourceType messageSourceType) {

        ZonedDateTime uploadTime = ZonedDateTime.now();
        String timestamp = String.valueOf(uploadTime.toInstant().toEpochMilli());

        Map<String, String> headers = new HashMap<>();
        headers.put("SourceIp", sourceIp);
        headers.put("SourcePort", sourcePort);
        headers.put("DestinationIp", destinationIp);
        headers.put("DestinationPort", destinationPort);

        String datePath = uploadTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileBaseName = "tcp-message";
        //String fileExtension = getFileExtension(detectedFormat);
        String originalFileName = fileBaseName;

        PortBasedPaths paths = portConfigUtil.resolvePortBasedPaths(
                portEntryOpt,
                interactionId,
                headers,
                originalFileName,
                timestamp,
                datePath);

        String userAgent = "";

        return new RequestContext(
                headers,
                "",
                paths.getQueue(),
                interactionId,
                uploadTime,
                timestamp,
                originalFileName,
                message.length(),
                paths.getDataKey(),
                paths.getMetaDataKey(),
                paths.getFullS3DataPath(),
                userAgent,
                "",
                portEntryOpt.map(pe -> pe.route).orElse(""),
                "TCP",
                destinationIp,
                sourceIp,
                sourceIp,
                destinationIp,
                destinationPort,
                paths.getAcknowledgementKey(),
                paths.getFullS3AcknowledgementPath(),
                paths.getFullS3MetadataPath(),
                messageSourceType,
                paths.getDataBucketName(),
                paths.getMetadataBucketName(),
                appConfig.getVersion());
    }

    /**
     * Convert control characters to visible representation for logging
     */
    private String toVisibleChars(String message) {
        if (message == null) {
            return null;
        }
        return message
                .replace("\u000B", "<VT>")
                .replace("\u001C", "<FS>")
                .replace("\r", "<CR>")
                .replace("\n", "<LF>");
    }

    // MessageSourceProvider interface methods
    @Override
    public MessageSourceType getMessageSource() {
        return MessageSourceType.TCP;
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
        return headers.get("SourceIp");
    }

    @Override
    public String getDestinationIp(Map<String, String> headers) {
        return headers.get("DestinationIp");
    }

    @Override
    public String getDestinationPort(Map<String, String> headers) {
        return headers.get("DestinationPort");
    }
}
