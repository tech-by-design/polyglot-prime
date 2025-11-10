package org.techbd.ingest.listener;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
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

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;
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
import io.netty.util.AttributeKey;
import jakarta.annotation.PostConstruct;

@Component
public class NettyTcpServer {

    private final TemplateLogger logger;
    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private final PortConfigUtil portConfigUtil;

    @Value("${TCP_DISPATCHER_PORT:6001}")
    private int tcpPort;

    private static final AttributeKey<String> CLIENT_IP_KEY = AttributeKey.valueOf("CLIENT_IP");
    private static final AttributeKey<Integer> CLIENT_PORT_KEY = AttributeKey.valueOf("CLIENT_PORT");
    private static final AttributeKey<String> DESTINATION_IP_KEY = AttributeKey.valueOf("DESTINATION_IP_KEY");
    private static final AttributeKey<Integer> DESTINATION_PORT_KEY = AttributeKey.valueOf("DESTINATION_PORT_KEY");

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
                String interactionId = UUID.randomUUID().toString();
                String sourceIp = null;
                String soucePort = null;
                String destinationPort = null;
                String destinationIp = null;
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(boss, worker)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(new HAProxyMessageDecoder());
                                ch.pipeline().addLast(new StringDecoder());
                                ch.pipeline().addLast(new StringEncoder());

                                ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Object msg)
                                            throws IOException {
                                        if (msg instanceof HAProxyMessage proxyMsg) {
                                            handleProxyHeader(ctx, proxyMsg);
                                            return; // skip to next frame
                                        }

                                        if (msg instanceof String hl7Msg) {
                                            handleHL7Message(ctx, hl7Msg);
                                        }
                                    }

                                    private void handleProxyHeader(ChannelHandlerContext ctx, HAProxyMessage proxyMsg) {
                                        if (proxyMsg.command() == HAProxyCommand.PROXY) {
                                            logger.info(
                                                    "NETTY_TCP_SERVER [PROXY] ======================================== interactionId : {}",
                                                    interactionId);
                                            logger.info(
                                                    "NETTY_TCP_SERVER [PROXY] Detected HAProxy Protocol Header interactionId : {}",
                                                    interactionId);
                                            logger.info(
                                                    "NETTY_TCP_SERVER [PROXY] Protocol Version: {} interactionId : {} ",
                                                    proxyMsg.protocolVersion(), interactionId);
                                            logger.info("NETTY_TCP_SERVER [PROXY] Command: {} interactionId : {}",
                                                    proxyMsg.command(), interactionId);
                                            logger.info(
                                                    "NETTY_TCP_SERVER [PROXY] Proxied Protocol: {} interactionId : {}",
                                                    proxyMsg.proxiedProtocol(), interactionId);
                                            logger.info(
                                                    "NETTY_TCP_SERVER [PROXY] Source Address: {} interactionId : {}",
                                                    proxyMsg.sourceAddress(), interactionId);
                                            logger.info("NETTY_TCP_SERVER [PROXY] Source Port: {} interactionId : {}",
                                                    proxyMsg.sourcePort(), interactionId);
                                            logger.info(
                                                    "NETTY_TCP_SERVER [PROXY] Destination Address: {} interactionId : {}",
                                                    proxyMsg.destinationAddress(), interactionId);
                                            logger.info(
                                                    "NETTY_TCP_SERVER [PROXY] Destination Port: {} interactionId : {}",
                                                    proxyMsg.destinationPort(), interactionId);
                                            logger.info("NETTY_TCP_SERVER [PROXY] Full Message: {} interactionId : {}",
                                                    proxyMsg, interactionId);
                                            logger.info(
                                                    "NETTY_TCP_SERVER [PROXY] ======================================== interactionId : {}",
                                                    interactionId);
                                            ctx.channel().attr(CLIENT_IP_KEY).set(proxyMsg.sourceAddress());
                                            ctx.channel().attr(CLIENT_PORT_KEY).set(proxyMsg.sourcePort());

                                            // Store destination IP and port in channel attributes
                                            ctx.channel().attr(DESTINATION_IP_KEY).set(proxyMsg.destinationAddress());
                                            ctx.channel().attr(DESTINATION_PORT_KEY).set(proxyMsg.destinationPort());
                                        }
                                    }

                                    private void handleHL7Message(ChannelHandlerContext ctx, String msg)
                                            throws IOException {
                                        // Retrieve client/source IP and port
                                        String clientIP = ctx.channel().attr(CLIENT_IP_KEY).get();
                                        Integer clientPort = ctx.channel().attr(CLIENT_PORT_KEY).get();

                                        // Retrieve destination IP and port
                                        String destinationIP = ctx.channel().attr(DESTINATION_IP_KEY).get();
                                        Integer destinationPort = ctx.channel().attr(DESTINATION_PORT_KEY).get();
                                        logger.info("Received raw message from {}:{}:\n{}", clientIP, clientPort,
                                                toVisible(msg));
                                        boolean isMllp = msg.startsWith("\u000B") && msg.endsWith("\u001C\r");
                                        String cleanMsg = removeMllp(msg.trim());
                                        logger.info("Sanitized HL7 Message:\n{}", cleanMsg);
                                        String response;
                                        try {
                                            GenericParser parser = new GenericParser();
                                            Message hl7Message = parser.parse(cleanMsg);
                                            Message ack = hl7Message.generateACK();
                                            String ackMessage = addNteWithInteractionId(ack, interactionId,
                                                    appConfig.getVersion());
                                            // Build request context with port configuration
                                            Optional<PortConfig.PortEntry> portEntryOpt = portConfigUtil.readPortEntry(
                                                    destinationPort,
                                                    interactionId);
                                            if (!portConfigUtil.validatePortEntry(portEntryOpt, destinationPort,
                                                    interactionId)) {
                                                // TODO -handle invalid port entry case
                                            }
                                            RequestContext requestContext = buildRequestContext(
                                                    cleanMsg, interactionId, portEntryOpt, String.valueOf(soucePort), sourceIp,
                                                    destinationIP, String.valueOf(destinationPort), "hl7");

                                            // Extract ZNT segment only if response type is "outbound"
                                            if (shouldProcessZntSegment(portEntryOpt)) {
                                                extractZntSegment(hl7Message, requestContext, interactionId);
                                            }

                                            // Process message
                                            messageProcessorService.processMessage(requestContext,
                                                    cleanMsg, ackMessage);
                                            response = new PipeParser().encode(ack);
                                            logger.info("Generated ACK:\n{}", response);
                                        } catch (HL7Exception e) {
                                            logger.error("Failed to parse HL7 message: {}", e.getMessage());
                                            response = createNack("AE", e.getMessage());
                                        }

                                        if (isMllp) {
                                            response = "\u000B" + response + "\u001C\r";
                                            logger.info("Sending MLLP ACK");
                                        } else {
                                            logger.info("Sending plain TCP ACK");
                                        }

                                        ctx.writeAndFlush(response + "\n");
                                    }

                                    private String removeMllp(String msg) {
                                        return msg.replaceAll("^[\\u000B]", "").replaceAll("[\\u001C\\r]+$", "");
                                    }

                                    private String createNack(String code, String error) {
                                        return "MSH|^~\\&|SERVER|LOCAL|CLIENT|REMOTE|" + Instant.now()
                                                + "||ACK^A01|1|P|2.5\r"
                                                + "MSA|" + code + "|1|" + error + "\r";
                                    }

                                    private String toVisible(String msg) {
                                        return msg.replace("\u000B", "<VT>")
                                                .replace("\u001C", "<FS>")
                                                .replace("\r", "<CR>");
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                        logger.error("Exception caught in TCP handler", cause);
                                        ctx.close();
                                    }
                                });
                            }
                        });

                ChannelFuture future = bootstrap.bind(tcpPort).sync();
                logger.info("Netty TCP Server listening on port {}", tcpPort);
                future.channel().closeFuture().sync();
            } catch (Exception e) {
                logger.error("Failed to start Netty TCP Server", e);
            } finally {
                boss.shutdownGracefully();
                worker.shutdownGracefully();
            }
        }).start();
    }

    /**
     * Build RequestContext with port configuration support.
     */
    private RequestContext buildRequestContext(String message, String interactionId,
            Optional<PortConfig.PortEntry> portEntryOpt, String sourcePort, String sourceIp, String destinationIp,
            String destinationPort,
            String fileExtension) {
        ZonedDateTime uploadTime = ZonedDateTime.now();
        String timestamp = String.valueOf(uploadTime.toInstant().toEpochMilli());
        // TODO-Extract Headers
        // Map<String, String> headers = new HashMap<>();
        // exchange.getIn().getHeaders().forEach((k, v) -> {
        // if (v instanceof String) {
        // headers.put(k, (String) v);
        // if (FeatureEnum.isEnabled(FeatureEnum.DEBUG_LOG_REQUEST_HEADERS)) {
        // logger.info("{} -Header for the InteractionId {} : {} = {}",
        // FeatureEnum.DEBUG_LOG_REQUEST_HEADERS, interactionId, k, v);
        // }
        // }
        // });
        Map<String, String> nettyHeaders = new HashMap<>();
        nettyHeaders.put("SourceIp", sourceIp);
        nettyHeaders.put("SourcePort", sourcePort);
        nettyHeaders.put("DestinationIp", destinationIp);
        nettyHeaders.put("DestinationPort", destinationPort);

        String datePath = uploadTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileBaseName = "tcp-message";
        String originalFileName = fileBaseName + "." + fileExtension;

        PortBasedPaths paths = portConfigUtil.resolvePortBasedPaths(portEntryOpt, interactionId, nettyHeaders,
                originalFileName, timestamp, datePath);

        return new RequestContext(
                nettyHeaders,
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
                "NettyTcpRoute",
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
}
