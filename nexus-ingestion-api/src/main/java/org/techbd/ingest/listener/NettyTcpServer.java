package org.techbd.ingest.listener;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.techbd.ingest.MessageSourceProvider;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.exceptions.ErrorTraceIdGenerator;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.service.portconfig.PortResolverService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.LogUtil;
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
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import jakarta.annotation.PostConstruct;

@Component
public class NettyTcpServer implements MessageSourceProvider {

    private final TemplateLogger logger;
    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private final PortResolverService portResolverService;
    @Value("${TCP_DISPATCHER_PORT:6001}")
    private int tcpPort;

    @Value("${TCP_READ_TIMEOUT_SECONDS:30}")
    private int readTimeoutSeconds;

    @Value("${TCP_MAX_MESSAGE_SIZE_BYTES:10485760}") // 10MB default
    private int maxMessageSizeBytes;

    // MLLP protocol markers (unchanged)
    private static final byte MLLP_START = 0x0B; // <VT> Vertical Tab
    private static final byte MLLP_END_1 = 0x1C; // <FS> File Separator
    private static final byte MLLP_END_2 = 0x0D; // <CR> Carriage Return

    // NEW: TCP delimiter configuration from environment variables
    @Value("${TCP_MESSAGE_START_DELIMITER:0x02}") // STX - Start of Text
    private String tcpStartDelimiterHex;
    
    @Value("${TCP_MESSAGE_END_DELIMITER_1:0x03}") // ETX - End of Text
    private String tcpEndDelimiter1Hex;
    
    @Value("${TCP_MESSAGE_END_DELIMITER_2:0x0A}") // LF - Line Feed
    private String tcpEndDelimiter2Hex;

    // Parsed TCP delimiter bytes
    private byte tcpStartDelimiter;
    private byte tcpEndDelimiter1;
    private byte tcpEndDelimiter2;

    private static final AttributeKey<String> CLIENT_IP_KEY = AttributeKey.valueOf("CLIENT_IP");
    private static final AttributeKey<Integer> CLIENT_PORT_KEY = AttributeKey.valueOf("CLIENT_PORT");
    private static final AttributeKey<String> DESTINATION_IP_KEY = AttributeKey.valueOf("DESTINATION_IP_KEY");
    private static final AttributeKey<Integer> DESTINATION_PORT_KEY = AttributeKey.valueOf("DESTINATION_PORT_KEY");
    private static final AttributeKey<UUID> INTERACTION_ATTRIBUTE_KEY = AttributeKey.valueOf("INTERACTION_ATTRIBUTE_KEY");
    private static final AttributeKey<Long> MESSAGE_START_TIME_KEY = AttributeKey.valueOf("MESSAGE_START_TIME");
    private static final AttributeKey<AtomicInteger> FRAGMENT_COUNT_KEY = AttributeKey.valueOf("FRAGMENT_COUNT");
    private static final AttributeKey<AtomicLong> TOTAL_BYTES_KEY = AttributeKey.valueOf("TOTAL_BYTES");

    public NettyTcpServer(MessageProcessorService messageProcessorService,
            AppConfig appConfig,
            AppLogger appLogger,
            PortResolverService portResolverService) {
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.portResolverService = portResolverService;
        this.logger = appLogger.getLogger(NettyTcpServer.class);
    }

    @PostConstruct
    public void startServer() {
        // NEW: Parse TCP delimiters from hex strings
        parseTcpDelimiters();
        
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
                                // Initialize tracking attributes
                                ch.attr(MESSAGE_START_TIME_KEY).set(System.currentTimeMillis());
                                ch.attr(FRAGMENT_COUNT_KEY).set(new AtomicInteger(0));
                                ch.attr(TOTAL_BYTES_KEY).set(new AtomicLong(0));
                                
                                // Add read timeout handler
                                ch.pipeline().addLast(new ReadTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS));
                                String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");

                                // HAProxy protocol support
                                if (!"sandbox".equals(activeProfile)) {
                                    ch.pipeline().addLast(new HAProxyMessageDecoder());
                                }

                                // RENAMED: Delimiter-based frame decoder for both MLLP and TCP
                                ch.pipeline().addLast(new DelimiterBasedFrameDecoder(maxMessageSizeBytes));

                                // Main message handler - handles both HAProxyMessage and ByteBuf
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
                                            // Handle HAProxy header if present
                                            if (msg instanceof HAProxyMessage proxyMsg) {
                                                handleProxyHeader(ctx, proxyMsg, interactionId);
                                                return; // Wait for next frame (actual message)
                                            }
                                        }

                                        // Handle actual message content
                                        if (msg instanceof ByteBuf byteBuf) {
                                            // Convert ByteBuf to String
                                            String messageContent = byteBuf.toString(StandardCharsets.UTF_8);
                                            
                                            // Log complete message reception with timing
                                            long startTime = ctx.channel().attr(MESSAGE_START_TIME_KEY).get();
                                            long receiveTime = System.currentTimeMillis() - startTime;
                                            int fragmentCount = ctx.channel().attr(FRAGMENT_COUNT_KEY).get().get();
                                            long totalBytes = ctx.channel().attr(TOTAL_BYTES_KEY).get().get();
                                            
                                            logger.info("MESSAGE_FULLY_RECEIVED [interactionId={}] totalSize={} bytes, fragments={}, receiveTimeMs={}, avgFragmentSize={} bytes",
                                                    interactionId, totalBytes, fragmentCount, receiveTime, 
                                                    fragmentCount > 0 ? (totalBytes / fragmentCount) : totalBytes);
                                            
                                            handleMessage(ctx, messageContent, interactionId);
                                        }
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                        UUID interactionId = ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).get();
                                        logger.error("Exception in TCP handler for interactionId {}: {}",
                                                interactionId, cause.getMessage(), cause);
                                        if (ctx.channel().isActive()) {
                                            ctx.close();
                                        }
                                    }

                                    @Override
                                    public void channelInactive(ChannelHandlerContext ctx) {
                                        UUID interactionId = ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).get();
                                        logger.info("Channel closed for interactionId {}", interactionId);
                                        // Clear attributes to prevent memory leaks
                                        clearChannelAttributes(ctx);
                                    }
                                });
                            }
                        });

                ChannelFuture future = bootstrap.bind(tcpPort).sync();
                logger.info("TCP Server listening on port {} (MLLP=HL7 with ACK, TCP with delimiters=Generic with ACK). Max message size: {} bytes. TCP Delimiters: START=0x{}, END1=0x{}, END2=0x{}",
                        tcpPort, maxMessageSizeBytes, 
                        String.format("%02X", tcpStartDelimiter),
                        String.format("%02X", tcpEndDelimiter1),
                        String.format("%02X", tcpEndDelimiter2));
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
     * NEW: Parse TCP delimiter hex strings to byte values
     */
    private void parseTcpDelimiters() {
        try {
            tcpStartDelimiter = (byte) Integer.parseInt(tcpStartDelimiterHex.replace("0x", ""), 16);
            tcpEndDelimiter1 = (byte) Integer.parseInt(tcpEndDelimiter1Hex.replace("0x", ""), 16);
            tcpEndDelimiter2 = (byte) Integer.parseInt(tcpEndDelimiter2Hex.replace("0x", ""), 16);
            
            logger.info("TCP_DELIMITERS_CONFIGURED START=0x{} ({}), END1=0x{} ({}), END2=0x{} ({})",
                    String.format("%02X", tcpStartDelimiter), (int) tcpStartDelimiter,
                    String.format("%02X", tcpEndDelimiter1), (int) tcpEndDelimiter1,
                    String.format("%02X", tcpEndDelimiter2), (int) tcpEndDelimiter2);
        } catch (NumberFormatException e) {
            logger.error("Failed to parse TCP delimiters, using defaults (MLLP delimiters): {}", e.getMessage());
            tcpStartDelimiter = MLLP_START;
            tcpEndDelimiter1 = MLLP_END_1;
            tcpEndDelimiter2 = MLLP_END_2;
        }
    }

    /**
     * Delimiter-based frame decoder for Netty TCP server.
     * <p>
     * This decoder reads incoming bytes from the TCP channel and assembles complete
     * messages
     * based on configurable start and end delimiters. It supports both MLLP (HL7)
     * and
     * generic TCP message formats. Messages can arrive fragmented across multiple
     * TCP frames,
     * and this decoder ensures proper reassembly before passing them to the next
     * handler.
     * <p>
     * Features:
     * <ul>
     * <li>Buffers partial messages until complete delimiters are received</li>
     * <li>Tracks fragment count and total bytes for logging purposes</li>
     * <li>Supports MLLP (STX/ETX) and custom TCP start/end delimiters</li>
     * <li>Handles messages exceeding max frame length safely</li>
     * </ul>
     */
    private class DelimiterBasedFrameDecoder extends ByteToMessageDecoder {
        private final int maxFrameLength;

        public DelimiterBasedFrameDecoder(int maxFrameLength) {
            this.maxFrameLength = maxFrameLength;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            UUID interactionId = ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).get();
            if (interactionId == null) {
                interactionId = UUID.randomUUID();
                ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).set(interactionId);
            }

            int fragmentSize = in.readableBytes();
            if (fragmentSize == 0) {
                return;
            }

            // Track fragment metrics
            AtomicInteger fragmentCount = ctx.channel().attr(FRAGMENT_COUNT_KEY).get();
            AtomicLong totalBytes = ctx.channel().attr(TOTAL_BYTES_KEY).get();
            
            int currentFragment = fragmentCount.incrementAndGet();
            long currentTotalBytes = totalBytes.addAndGet(fragmentSize);
            
            logger.info("FRAGMENTED_MESSAGE [interactionId={}] fragment={}, fragmentSize={} bytes, cumulativeSize={} bytes, bufferReadable={} bytes",
                    interactionId, currentFragment, fragmentSize, currentTotalBytes, in.readableBytes());

            // Check if we have minimum bytes for delimiter detection
            if (in.readableBytes() < 3) {
                logger.info("BUFFER_TOO_SMALL [interactionId={}] readable={} bytes, waiting for more data", 
                        interactionId, in.readableBytes());
                return; // Wait for more data
            }

            int startIndex = in.readerIndex();
            byte firstByte = in.getByte(startIndex);

            // MODIFIED: Check for MLLP delimiters first (for HL7 messages)
            if (firstByte == MLLP_START) {
                logger.info("MLLP_START_DETECTED [interactionId={}] searching for MLLP end markers in {} bytes",
                        interactionId, in.readableBytes());
                
                // Look for MLLP end markers: <FS><CR>
                int endIndex = -1;
                for (int i = startIndex + 1; i < in.writerIndex() - 1; i++) {
                    if (in.getByte(i) == MLLP_END_1 && in.getByte(i + 1) == MLLP_END_2) {
                        endIndex = i + 2; // Include both end markers
                        logger.debug("MLLP_END_MARKERS_FOUND [interactionId={}] at position={}", 
                                interactionId, i);
                        break;
                    }
                }

                if (endIndex == -1) {
                    // End markers not found yet
                    if (in.readableBytes() > maxFrameLength) {
                        logger.error("MLLP_MESSAGE_TOO_LARGE [interactionId={}] size={} bytes exceeds max={} bytes",
                                interactionId, in.readableBytes(), maxFrameLength);
                        in.skipBytes(in.readableBytes());
                        throw new IllegalStateException("MLLP message exceeds max size: " + maxFrameLength + " bytes");
                    }
                    logger.info("MLLP_END_NOT_FOUND [interactionId={}] buffered={} bytes, waiting for more data",
                            interactionId, in.readableBytes());
                    return; // Wait for more data
                }

                // Complete MLLP message found
                int frameLength = endIndex - startIndex;
                ByteBuf frame = in.readRetainedSlice(frameLength);
                out.add(frame);
                
                logger.info("MLLP_FRAME_COMPLETE [interactionId={}] totalLength={} bytes, assembled from {} fragments, avgFragmentSize={} bytes",
                        interactionId, frameLength, currentFragment, currentFragment > 0 ? (frameLength / currentFragment) : frameLength);

            } 
            // NEW: Check for TCP delimiters (for non-HL7 messages)
            else if (firstByte == tcpStartDelimiter) {
                logger.debug("TCP_DELIMITER_START_DETECTED [interactionId={}] searching for TCP end markers (0x{}, 0x{}) in {} bytes",
                        interactionId, String.format("%02X", tcpEndDelimiter1), String.format("%02X", tcpEndDelimiter2), in.readableBytes());
                
                // Look for TCP end markers
                int endIndex = -1;
                for (int i = startIndex + 1; i < in.writerIndex() - 1; i++) {
                    if (in.getByte(i) == tcpEndDelimiter1 && in.getByte(i + 1) == tcpEndDelimiter2) {
                        endIndex = i + 2; // Include both end markers
                        logger.info("TCP_END_MARKERS_FOUND [interactionId={}] at position={}", 
                                interactionId, i);
                        break;
                    }
                }

                if (endIndex == -1) {
                    // End markers not found yet
                    if (in.readableBytes() > maxFrameLength) {
                        logger.error("TCP_DELIMITED_MESSAGE_TOO_LARGE [interactionId={}] size={} bytes exceeds max={} bytes",
                                interactionId, in.readableBytes(), maxFrameLength);
                        in.skipBytes(in.readableBytes());
                        throw new IllegalStateException("TCP delimited message exceeds max size: " + maxFrameLength + " bytes");
                    }
                    logger.debug("TCP_END_NOT_FOUND [interactionId={}] buffered={} bytes, waiting for more data",
                            interactionId, in.readableBytes());
                    return; // Wait for more data
                }

                // Complete TCP delimited message found
                int frameLength = endIndex - startIndex;
                ByteBuf frame = in.readRetainedSlice(frameLength);
                out.add(frame);
                
                logger.info("TCP_DELIMITED_FRAME_COMPLETE [interactionId={}] totalLength={} bytes, assembled from {} fragments, avgFragmentSize={} bytes",
                        interactionId, frameLength, currentFragment, currentFragment > 0 ? (frameLength / currentFragment) : frameLength);

            } 
            else {
                throw new IllegalStateException("Non-delimited message received, unable to process");
            }
        }

        @Override
        protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            UUID interactionId = ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).get();
            
            // Handle any remaining data when channel closes
            if (in.isReadable()) {
                int frameLength = in.readableBytes();
                ByteBuf frame = in.readRetainedSlice(frameLength);
                out.add(frame);
                
                AtomicInteger fragmentCount = ctx.channel().attr(FRAGMENT_COUNT_KEY).get();
                int currentFragment = fragmentCount.get();
                
                logger.info("FINAL_FRAME_ON_CLOSE [interactionId={}] length={} bytes, totalFragments={}",
                        interactionId, frameLength, currentFragment);
            } else {
                logger.info("CHANNEL_CLOSED_NO_REMAINING_DATA [interactionId={}]", interactionId);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            UUID interactionId = ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).get();
            logger.error("DECODER_EXCEPTION [interactionId={}]: {}", 
                    interactionId, cause.getMessage(), cause);
            super.exceptionCaught(ctx, cause);
        }
    }

    /**
     * Clear channel attributes to prevent memory leaks
     */
    private void clearChannelAttributes(ChannelHandlerContext ctx) {
        ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).set(null);
        ctx.channel().attr(MESSAGE_START_TIME_KEY).set(null);
        ctx.channel().attr(FRAGMENT_COUNT_KEY).set(null);
        ctx.channel().attr(TOTAL_BYTES_KEY).set(null);
        ctx.channel().attr(CLIENT_IP_KEY).set(null);
        ctx.channel().attr(CLIENT_PORT_KEY).set(null);
        ctx.channel().attr(DESTINATION_IP_KEY).set(null);
        ctx.channel().attr(DESTINATION_PORT_KEY).set(null);
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
        if (ctx.channel().attr(CLIENT_IP_KEY).get() != null) {
            return; // Already set
        }

        String dummyClientIp = "127.0.0.1";
        int dummyClientPort = 12345;
        String dummyDestinationIp = "127.0.0.1";
        int dummyDestinationPort = 5555;

        logger.info("SANDBOX_PROXY [interactionId={}] sourceAddress={}, sourcePort={}, destAddress={}, destPort={}",
                interactionId, dummyClientIp, dummyClientPort, dummyDestinationIp, dummyDestinationPort);

        ctx.channel().attr(CLIENT_IP_KEY).set(dummyClientIp);
        ctx.channel().attr(CLIENT_PORT_KEY).set(dummyClientPort);
        ctx.channel().attr(DESTINATION_IP_KEY).set(dummyDestinationIp);
        ctx.channel().attr(DESTINATION_PORT_KEY).set(dummyDestinationPort);
    }

    /**
     * Main message handler - routes to HL7 or generic handler based on MLLP detection
     * MODIFIED: Added error trace ID generation and structured logging
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
        // NEW: Also detect TCP delimiter wrapper
        boolean isTcpDelimited = detectTcpDelimiterWrapper(rawMessage);
        
        logger.info("COMPLETE_MESSAGE_RECEIVED [interactionId={}] from={}:{}, size={} bytes MLLP_WRAPPED={} TCP_DELIMITED={}",
                interactionId, clientIP, clientPort, rawMessage.length(), 
                isMllpWrapped ? "YES" : "NO", isTcpDelimited ? "YES" : "NO");

         RequestContext initialContext = buildRequestContext(
                rawMessage.trim(),
                interactionId.toString(),
                Optional.empty(), // No port entry yet
                String.valueOf(clientPort),
                clientIP,
                destinationIP,
                String.valueOf(destinationPort),
                isMllpWrapped ? MessageSourceType.MLLP : MessageSourceType.TCP);

        // Resolve port entry using the new service
        Optional<PortConfig.PortEntry> portEntryOpt = portResolverService.resolve(initialContext);
        
        if (detectMllp(portEntryOpt)) {
            if (isTcpDelimited) {
                 handleConflictingWrapper(
                    ctx, 
                    rawMessage, 
                    interactionId, 
                    clientIP, 
                    clientPort, 
                    destinationIP, 
                    destinationPort, 
                    portEntryOpt,
                    "TCP_DELIMITER_FOUND_EXPECTED_MLLP",
                    "As per port configuration, expecting HL7 message with MLLP wrappers. Received message delimited with TCP delimiters instead.",
                    isTcpDelimited
                );
                logger.warn("CONFLICTING_WRAPPERS_DETECTED [interactionId={}] - As per port configuration, expecting HL7 message with MLLP wrappers. Received message delimited with TCP delimiters instead.",
                        interactionId);
                        
                return;
            }
            logger.info("MLLP_DETECTED [interactionId={}] - Using HL7 processing with proper ACK", interactionId);
            handleHL7Message(ctx, rawMessage, interactionId, clientIP, clientPort,
                    destinationIP, destinationPort, portEntryOpt);
        } else {
            if(isMllpWrapped) {
                  handleConflictingWrapper(
                    ctx, 
                    rawMessage, 
                    interactionId, 
                    clientIP, 
                    clientPort, 
                    destinationIP, 
                    destinationPort, 
                    portEntryOpt,
                    "MLLP_WRAPPER_FOUND_EXPECTED_TCP",
                    "As per port configuration, expecting TCP delimited message. Received message with MLLP wrappers instead.",
                    false
                );
                logger.warn("CONFLICTING_WRAPPERS_DETECTED [interactionId={}] - As per port configuration, expecting TCP delimited message. Received message with MLLP wrappers instead.",
                        interactionId);
                return;        
            }
            logger.info("TCP_MODE_DETECTED [interactionId={}] - Using generic processing with simple ACK",
                    interactionId);
            handleGenericMessage(ctx, rawMessage, interactionId, clientIP, clientPort,
                    destinationIP, destinationPort, portEntryOpt);
        }
    }

    /**
     * Handles cases where the message wrapper type conflicts with port configuration.
     * Generates a negative acknowledgment, sets ingestionFailed flag, and stores the original payload.
     */
    private void handleConflictingWrapper(
            ChannelHandlerContext ctx,
            String rawMessage,
            UUID interactionId,
            String clientIP,
            Integer clientPort,
            String destinationIP,
            Integer destinationPort,
            Optional<PortConfig.PortEntry> portEntryOpt,
            String errorCode,
            String errorMessage,
            boolean genericNackExpected) {
        
        String errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
        RequestContext requestContext = null;
        
        try {               
            logger.error("CONFLICTING_WRAPPERS_DETECTED [interactionId={}] [errorTraceId={}] {}", 
                    interactionId, errorTraceId, errorMessage);
            
            // Log detailed error
            LogUtil.logDetailedError(
                400, 
                errorMessage, 
                interactionId.toString(), 
                errorTraceId,
                new IllegalArgumentException("Wrapper mismatch: " + errorCode)
            );
            
            // Build request context with ingestionFailed flag
            requestContext = buildRequestContext(
                    rawMessage,
                    interactionId.toString(),
                    portEntryOpt,
                    String.valueOf(clientPort),
                    clientIP,
                    destinationIP,
                    String.valueOf(destinationPort),
                    MessageSourceType.MLLP);
            
            requestContext.setIngestionFailed(true);
            
            // Generate appropriate negative acknowledgment based on wrapper type
            String nackMessage;
            if (genericNackExpected) {
                // TCP delimiter was used, send TCP-style NACK
                nackMessage = String.format(
                    "NACK|%s|%s|%s|%s",
                    interactionId,
                    errorTraceId,
                    errorCode,
                    errorMessage
                );
            } else {
                // MLLP wrapper was used, send HL7 NACK
                nackMessage = createHL7AckFromMsh(
                        rawMessage,
                        "AR",
                        errorMessage,
                        interactionId.toString(),
                        errorTraceId);
            }
            
            // Store original payload even though it has wrong wrapper
            try {
                messageProcessorService.processMessage(requestContext, rawMessage, nackMessage);
                logger.info("CONFLICTING_WRAPPER_PAYLOAD_STORED [interactionId={}] [errorTraceId={}] - Original payload stored for troubleshooting", 
                        interactionId, errorTraceId);
            } catch (Exception storageException) {
                logger.error("FAILED_TO_STORE_CONFLICTING_WRAPPER_PAYLOAD [interactionId={}] [errorTraceId={}] - Could not store original payload", 
                        interactionId, errorTraceId, storageException);
            }
            if (genericNackExpected) {
                sendResponseAndClose(ctx, nackMessage, interactionId, "TCP_NACK_WRAPPER_CONFLICT");
            } else {
                sendResponseAndClose(ctx, nackMessage, interactionId, "HL7_NACK_WRAPPER_CONFLICT");
            }
            
        } catch (Exception e) {
            logger.error("ERROR_HANDLING_WRAPPER_CONFLICT [interactionId={}] [errorTraceId={}]: {}", 
                    interactionId, errorTraceId, e.getMessage(), e);
            
            // Try to store whatever we can
            if (requestContext != null && rawMessage != null) {
                try {
                    requestContext.setIngestionFailed(true);
                    messageProcessorService.processMessage(requestContext, rawMessage, null);
                } catch (Exception storageException) {
                    logger.error("FINAL_STORAGE_ATTEMPT_FAILED [interactionId={}] [errorTraceId={}]", 
                            interactionId, errorTraceId, storageException);
                }
            }
            
            // Send generic error response
            String genericNack = String.format("NACK|%s|%s|PROCESSING_ERROR|%s", 
                    interactionId, errorTraceId, e.getMessage());
            sendResponseAndClose(ctx, genericNack, interactionId, "NACK_PROCESSING_ERROR");
        }
    }
    private boolean detectTcpDelimiterWrapper(String message) {
        if (message == null || message.length() < 3) {
            return false;
        }
        return message.charAt(0) == tcpStartDelimiter
                && message.charAt(message.length() - 2) == tcpEndDelimiter1
                && message.charAt(message.length() - 1) == tcpEndDelimiter2;
    }

    /**
     * Handle HL7 messages with MLLP protocol
     * MODIFIED: Added error trace ID generation for all NACK scenarios
     */
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
        String errorTraceId = null;
        RequestContext requestContext = null;
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
                logger.error("HL7_PARSE_ERROR [interactionId={}]: Parsing failed due to error {} .. Continue generating manual ACK", 
                        interactionId, e.getMessage(), e);
            }

            requestContext = buildRequestContext(
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
                    if (!zntPresent) {
                        logger.warn("ZNT_EXTRACTION_FAILED_USING_TERSER [interactionId={}] - proceed extracting manually", 
                                interactionId);
                        zntPresent = extractZntSegmentManually(cleanMsg, requestContext, interactionId.toString());
                    }
                } else {
                    zntPresent = extractZntSegmentManually(cleanMsg, requestContext, interactionId.toString());
                    ackMessage = createHL7AckFromMsh(
                            cleanMsg,
                            "AA",
                            null,
                            interactionId.toString(),
                            null); // No errorTraceId for successful ACK
                    nackGenerated = false;
                }

                if (!zntPresent) {
                    requestContext.setIngestionFailed(true);
                    // Generate error trace ID for missing ZNT
                    errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
                    
                    logger.warn("MISSING_ZNT_NACK [interactionId={}] [errorTraceId={}] - sending NACK due to missing ZNT segment", 
                            interactionId, errorTraceId);
                    
                    // Log detailed error
                    LogUtil.logDetailedError(
                        400, 
                        "Missing ZNT segment", 
                        interactionId.toString(), 
                        errorTraceId,
                        new IllegalArgumentException("Required ZNT segment not found in HL7 message")
                    );
                    
                    String nack = createHL7AckFromMsh(
                            cleanMsg, 
                            "AR", 
                            "Missing ZNT segment", 
                            interactionId.toString(),
                            errorTraceId);
                    requestContext.setIngestionFailed(true);
                    messageProcessorService.processMessage(requestContext, cleanMsg, nack);        
                    sendResponseAndClose(ctx, wrapMllp(nack), interactionId, "HL7_NACK_MISSING_ZNT");
                    return;
                }
            }
            messageProcessorService.processMessage(requestContext, cleanMsg, ackMessage);
            // Send MLLP-wrapped ACK
            String response = wrapMllp(ackMessage);
            sendResponseAndClose(ctx, response, interactionId, "HL7_ACK");

        } catch (Exception e) {
            // Generate error trace ID for processing errors
            errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
            
            logger.error("PROCESSING_ERROR [interactionId={}] [errorTraceId={}] Sending Reject NACK(AR): {}", 
                    interactionId, errorTraceId, e.getMessage(), e);
            
            // Log detailed error
            LogUtil.logDetailedError(
                500, 
                "Internal processing error", 
                interactionId.toString(), 
                errorTraceId,
                e
            );
            
            // Final fallback NACK (AR)
            String errorAck = createHL7AckFromMsh(
                    cleanMsg != null ? cleanMsg : unwrapMllp(rawMessage),
                    "AR",
                    e.getMessage(),
                    interactionId.toString(),
                    errorTraceId);
            if (requestContext != null) {
                requestContext.setIngestionFailed(true);
                messageProcessorService.processMessage(requestContext, cleanMsg, errorAck);
            }        
            sendResponseAndClose(ctx, wrapMllp(errorAck), interactionId, "HL7_NACK");
        }
    }

    /**
     * Handle non-MLLP messages with simple acknowledgment
     * MODIFIED: Added error trace ID generation for NACK scenarios
     */
    private void handleGenericMessage(ChannelHandlerContext ctx, String rawMessage, UUID interactionId,
            String clientIP, Integer clientPort, String destinationIP, Integer destinationPort, 
            Optional<PortConfig.PortEntry> portEntryOpt) {
        String errorTraceId = null;
        RequestContext requestContext = null;

        try {
            String cleanMsg = rawMessage.trim();
            requestContext = buildRequestContext(
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
            // Generate error trace ID for processing errors
            errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
            if (requestContext != null) {
                requestContext.setIngestionFailed(true);
            }
            logger.error("GENERIC_PROCESSING_ERROR [interactionId={}] [errorTraceId={}]: {}",
                    interactionId, errorTraceId, e.getMessage(), e);
            
            // Log detailed error
            LogUtil.logDetailedError(
                500, 
                "Generic message processing error", 
                interactionId.toString(), 
                errorTraceId,
                e
            );
            
            String errorResponse = generateSimpleNack(interactionId.toString(), e.getMessage(), errorTraceId) + "\n";
            if (requestContext != null) {
                messageProcessorService.processMessage(requestContext, rawMessage.trim(), errorResponse);
            }
            sendResponseAndClose(ctx, errorResponse, interactionId, "SIMPLE_NACK");
        }
    }

    private void sendResponseAndClose(ChannelHandlerContext ctx, String response,
            UUID interactionId, String responseType) {
        if (!ctx.channel().isActive()) {
            logger.warn("CANNOT_SEND_RESPONSE [interactionId={}] type={} - CHANNEL_ALREADY_INACTIVE",
                    interactionId, responseType);
            return;
        }

        logger.info("SENDING_RESPONSE [interactionId={}] type={} size={} bytes", 
                interactionId, responseType, response.length());

        ByteBuf responseBuf = ctx.alloc().buffer();
        responseBuf.writeBytes(response.getBytes(StandardCharsets.UTF_8));

        // Schedule close after flush to ensure response is sent
        ctx.writeAndFlush(responseBuf).addListener(future -> {
            if (future.isSuccess()) {
                logger.info("RESPONSE_SENT_SUCCESS [interactionId={}] type={}, scheduling connection close",
                        interactionId, responseType);
                
                // Schedule close to allow network flush
                ctx.executor().schedule(() -> {
                    logger.info("CLOSING_CONNECTION [interactionId={}]", interactionId);
                    ctx.close();
                }, 50, TimeUnit.MILLISECONDS);
            } else {
                logger.error("RESPONSE_SEND_FAILED [interactionId={}] type={}: {}",
                        interactionId, responseType, 
                        future.cause() != null ? future.cause().getMessage() : "unknown");
                // Close immediately on failure
                ctx.close();
            }
        });
    }

    /**
     * Detect if message is wrapped in MLLP protocol
     */
    private boolean detectMllpWrapper(String message) {
        if (message == null || message.length() < 3) {
            return false;
        }
        return message.charAt(0) == MLLP_START
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
        return String.valueOf((char)MLLP_START) + message + (char)MLLP_END_1 + (char)MLLP_END_2;
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

                logger.info("MSH_PARSED messageControlId={}, sendingApp={}, receivingApp={}",
                        mshFields.get("messageControlId"),
                        mshFields.get("sendingApplication"),
                        mshFields.get("receivingApplication"));
            }

        } catch (Exception e) {
            logger.error("Error parsing MSH segment: {}", e.getMessage());
        }

        return mshFields;
    }

    /**
     * Create HL7 ACK/NACK from MSH segment
     * MODIFIED: Added errorTraceId parameter and included in NTE segment
     */
    private String createHL7AckFromMsh(String originalMessage, String ackCode, String errorText, 
            String interactionId, String errorTraceId) {

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

            // NTE with errorTraceId if NACK
            nack.append("NTE|1||InteractionID: ").append(interactionId)
                    .append(" | TechBDIngestionApiVersion: ").append(appConfig.getVersion());
            if (errorTraceId != null) {
                nack.append(" | ErrorTraceID: ").append(errorTraceId);
            }
            nack.append("\r");
            
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

        // NTE with errorTraceId
        ack.append("NTE").append(fieldSep)
                .append("1").append(fieldSep)
                .append(fieldSep)
                .append("InteractionID: ").append(interactionId)
                .append(" | TechBDIngestionApiVersion: ").append(appConfig.getVersion());
        
        // Add ErrorTraceID only for NACKs (non-AA acknowledgments)
        if (errorTraceId != null && !"AA".equals(finalAckCode)) {
            ack.append(" | ErrorTraceID: ").append(errorTraceId);
        }
        ack.append("\r");

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
     * MODIFIED: Added errorTraceId parameter with caret separator format
     */
    private String generateSimpleNack(String interactionId, String errorMessage, String errorTraceId) {
        String sanitizedError = errorMessage.replace("|", " ").replace("\n", " ");
        return String.format("NACK|InteractionId^%s|ErrorTraceId^%s|ERROR|%s|%s",
                interactionId,
                errorTraceId,
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
        String originalFileName = fileBaseName;
        String userAgent = "";

        return new RequestContext(
                headers,
                "",
                appConfig.getAws().getSqs().getFifoQueueUrl(),
                interactionId,
                uploadTime,
                timestamp,
                originalFileName,
                message.length(),
                getDataKey(interactionId, headers, originalFileName,timestamp),
                getMetaDataKey(interactionId, headers, originalFileName,timestamp),
                getFullS3DataPath(interactionId, headers, originalFileName,timestamp),
                userAgent,
                "",
                portEntryOpt.map(pe -> pe.route).orElse(""),
                "TCP",
                destinationIp,
                sourceIp,
                sourceIp,
                destinationIp,
                destinationPort,
                getAcknowledgementKey(interactionId, headers, originalFileName,timestamp),
                getFullS3AcknowledgementPath(interactionId, headers, originalFileName,timestamp),
                getFullS3MetadataPath(interactionId, headers, originalFileName,timestamp),
                messageSourceType,
                getDataBucketName(),
                getMetadataBucketName(),
                appConfig.getVersion());
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