package org.techbd.ingest.listener;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
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
import org.techbd.ingest.feature.FeatureEnum;
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
import io.netty.channel.ChannelInboundHandlerAdapter;
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
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import jakarta.annotation.PostConstruct;

@Component
public class NettyTcpServer implements MessageSourceProvider {

    private final TemplateLogger logger;
    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private final PortResolverService portResolverService;
    @Value("${TCP_DISPATCHER_PORT:7980}")
    private int tcpPort;

    @Value("${TCP_READ_TIMEOUT_SECONDS:180}")
    private int readTimeoutSeconds;

    @Value("${TCP_MAX_MESSAGE_SIZE_BYTES:52428800}") // 50MB default
    private int maxMessageSizeBytes;

    // MLLP protocol markers
    private static final byte MLLP_START = 0x0B; // <VT> Vertical Tab
    private static final byte MLLP_END_1 = 0x1C; // <FS> File Separator
    private static final byte MLLP_END_2 = 0x0D; // <CR> Carriage Return

    // TCP delimiter configuration from environment variables
    @Value("${TCP_MESSAGE_START_DELIMITER:0x02}") // STX - Start of Text
    private String tcpStartDelimiterHex;
    
    @Value("${TCP_MESSAGE_END_DELIMITER_1:0x03}") // ETX - End of Text
    private String tcpEndDelimiter1Hex;
    
    @Value("${TCP_MESSAGE_END_DELIMITER_2:0x0A}") // LF - Line Feed
    private String tcpEndDelimiter2Hex;

    // Time interval (in seconds) to log a "session still active" message for long-running connections.
// Set to 0 to turn off periodic session logging.
    @Value("${TCP_SESSION_LOG_INTERVAL_SECONDS:60}")
    private int sessionLogIntervalSeconds;

    // Parsed TCP delimiter bytes
    private byte tcpStartDelimiter;
    private byte tcpEndDelimiter1;
    private byte tcpEndDelimiter2;

    // -------------------------------------------------------------------------
    // Channel Attribute Keys
    // -------------------------------------------------------------------------

    private static final AttributeKey<String> CLIENT_IP_KEY = AttributeKey.valueOf("CLIENT_IP");
    private static final AttributeKey<Integer> CLIENT_PORT_KEY = AttributeKey.valueOf("CLIENT_PORT");
    private static final AttributeKey<String> DESTINATION_IP_KEY = AttributeKey.valueOf("DESTINATION_IP_KEY");
    private static final AttributeKey<Integer> DESTINATION_PORT_KEY = AttributeKey.valueOf("DESTINATION_PORT_KEY");
    private static final AttributeKey<UUID> INTERACTION_ATTRIBUTE_KEY = AttributeKey.valueOf("INTERACTION_ATTRIBUTE_KEY");
    private static final AttributeKey<Long> MESSAGE_START_TIME_KEY = AttributeKey.valueOf("MESSAGE_START_TIME");
    private static final AttributeKey<AtomicInteger> FRAGMENT_COUNT_KEY = AttributeKey.valueOf("FRAGMENT_COUNT");
    private static final AttributeKey<AtomicLong> TOTAL_BYTES_KEY = AttributeKey.valueOf("TOTAL_BYTES");
    private static final AttributeKey<Boolean> MESSAGE_SIZE_EXCEEDED_KEY = AttributeKey.valueOf("MESSAGE_SIZE_EXCEEDED");
    private static final AttributeKey<Boolean> ERROR_NACK_SENT_KEY = AttributeKey.valueOf("ERROR_NACK_SENT");
    // Stores the resolved keepAliveTimeout (seconds) for this channel; null means use default ReadTimeoutHandler behaviour
    private static final AttributeKey<Integer> KEEP_ALIVE_TIMEOUT_KEY = AttributeKey.valueOf("KEEP_ALIVE_TIMEOUT");
    // Flag: set when no recognized delimiter was found in the incoming frame
    private static final AttributeKey<Boolean> NO_DELIMITER_DETECTED_KEY = AttributeKey.valueOf("NO_DELIMITER_DETECTED");
    // Accumulator for raw bytes received when no delimiter is present (for diagnostic logging)
    private static final AttributeKey<StringBuilder> RAW_ACCUMULATOR_KEY = AttributeKey.valueOf("RAW_ACCUMULATOR");
    // Persists the formatted HAProxy header string for the lifetime of the channel so it
    // can be re-logged on every message, idle event, and timeout — not just on first receipt.
    private static final AttributeKey<String> HAPROXY_DETAILS_KEY = AttributeKey.valueOf("HAPROXY_DETAILS");

    /**
     * SESSION_ID_KEY — assigned once when the TCP connection is established
     * (in initChannel) and never changes for the lifetime of that connection.
     * A single TCP session can carry multiple messages (keep-alive / persistent
     * connections).  Every log line in this class includes the sessionId so that
     * all activity for a given TCP connection can be correlated in the logs even
     * when multiple messages share the same channel.
     *
     * Contrast with INTERACTION_ATTRIBUTE_KEY (interactionId) which is reset after
     * each successfully-processed message on a keep-alive connection.
     */
    private static final AttributeKey<String> SESSION_ID_KEY = AttributeKey.valueOf("SESSION_ID");

    /**
     * Tracks the wall-clock time (epoch millis) when this TCP connection was
     * accepted so that session duration can be logged at close / idle events.
     */
    private static final AttributeKey<Long> SESSION_START_TIME_KEY = AttributeKey.valueOf("SESSION_START_TIME");

    /**
     * Counts how many HL7/TCP messages have been processed on this session
     * (incremented after each successful response is sent on a keep-alive channel).
     */
    private static final AttributeKey<AtomicInteger> SESSION_MESSAGE_COUNT_KEY = AttributeKey.valueOf("SESSION_MESSAGE_COUNT");

    public NettyTcpServer(MessageProcessorService messageProcessorService,
            AppConfig appConfig,
            AppLogger appLogger,
            PortResolverService portResolverService) {
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.portResolverService = portResolverService;
        this.logger = appLogger.getLogger(NettyTcpServer.class);
    }

    // -------------------------------------------------------------------------
    // Helpers: read / parse HAPROXY_DETAILS_KEY
    // -------------------------------------------------------------------------

    /** Returns the raw haproxyDetails string for this channel, or "" if not yet set. */
    private String haproxyDetails(ChannelHandlerContext ctx) {
        String details = ctx.channel().attr(HAPROXY_DETAILS_KEY).get();
        return details != null ? details : "";
    }

    /**
     * Parses a single named field out of a haproxyDetails string of the form:
     * {@code sourceAddress=x, sourcePort=n, destAddress=y, destPort=m}
     * Returns "" if the string is null/empty or the key is not found.
     */
    private String parseHaproxyField(String haproxyDetails, String key) {
        if (haproxyDetails == null || haproxyDetails.isEmpty()) return "";
        for (String part : haproxyDetails.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(key + "=")) {
                return trimmed.substring(key.length() + 1).trim();
            }
        }
        return "";
    }

    /**
     * Convenience: extract sourceAddress from haproxyDetails on a channel.
     * Returns "" if HAPROXY_DETAILS_KEY is not yet populated.
     */
    private String haproxySourceAddress(ChannelHandlerContext ctx) {
        return parseHaproxyField(haproxyDetails(ctx), "sourceAddress");
    }

    /**
     * Convenience: extract sourcePort from haproxyDetails on a channel.
     * Returns "" if HAPROXY_DETAILS_KEY is not yet populated.
     */
    private String haproxySourcePort(ChannelHandlerContext ctx) {
        return parseHaproxyField(haproxyDetails(ctx), "sourcePort");
    }

    /**
     * Convenience: extract destAddress from haproxyDetails on a channel.
     * Returns "" if HAPROXY_DETAILS_KEY is not yet populated.
     */
    private String haproxyDestAddress(ChannelHandlerContext ctx) {
        return parseHaproxyField(haproxyDetails(ctx), "destAddress");
    }

    /**
     * Convenience: extract destPort from haproxyDetails on a channel.
     * Returns "" if HAPROXY_DETAILS_KEY is not yet populated.
     */
    private String haproxyDestPort(ChannelHandlerContext ctx) {
        return parseHaproxyField(haproxyDetails(ctx), "destPort");
    }

    @PostConstruct
    public void startServer() {
        // Parse TCP delimiters from hex strings
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
                                // -----------------------------------------------------------------
                                // SESSION initialisation — done ONCE per TCP connection.
                                // The sessionId never changes for the lifetime of this channel.
                                // -----------------------------------------------------------------
                                String sessionId = UUID.randomUUID().toString();
                                long sessionStartTime = System.currentTimeMillis();

                                ch.attr(SESSION_ID_KEY).set(sessionId);
                                ch.attr(SESSION_START_TIME_KEY).set(sessionStartTime);
                                ch.attr(SESSION_MESSAGE_COUNT_KEY).set(new AtomicInteger(0));

                                // -----------------------------------------------------------------
                                // KAT resolution is deferred — HAPROXY_DETAILS_KEY is not yet
                                // populated at initChannel time (the HAProxy header has not arrived).
                                // Always install ReadTimeoutHandler as a safe default; it will be
                                // replaced by IdleStateHandler inside handleProxyHeader /
                                // handleSandboxProxy once the real destPort is known.
                                // -----------------------------------------------------------------
                                logger.info("TCP_SESSION_CONNECTING [sessionId={}] " +
                                        "timeoutHandler=ReadTimeoutHandler readTimeout={}s " +
                                        "(KAT resolution deferred until HAProxy header received)",
                                        sessionId, readTimeoutSeconds);

                                // Initialize per-message tracking attributes
                                ch.attr(MESSAGE_START_TIME_KEY).set(sessionStartTime);
                                ch.attr(FRAGMENT_COUNT_KEY).set(new AtomicInteger(0));
                                ch.attr(TOTAL_BYTES_KEY).set(new AtomicLong(0));
                                ch.attr(MESSAGE_SIZE_EXCEEDED_KEY).set(false);
                                ch.attr(ERROR_NACK_SENT_KEY).set(false);
                                ch.attr(NO_DELIMITER_DETECTED_KEY).set(false);
                                ch.attr(RAW_ACCUMULATOR_KEY).set(new StringBuilder());
                                ch.attr(HAPROXY_DETAILS_KEY).set(null);

                                // Always start with ReadTimeoutHandler; handleProxyHeader /
                                // handleSandboxProxy will replace it with IdleStateHandler if
                                // the resolved PortEntry has a keepAliveTimeout configured.
                                ch.pipeline().addLast("defaultReadTimeout",
                                        new ReadTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS));

                                String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");

                                // HAProxy protocol support
                                if (!"sandbox".equals(activeProfile)) {
                                    ch.pipeline().addLast(new HAProxyMessageDecoder());
                                }

                                // Delimiter-based frame decoder for both MLLP and TCP
                                ch.pipeline().addLast(new DelimiterBasedFrameDecoder(maxMessageSizeBytes));

                                // Optional periodic session-activity logger.
                                // Uses an all-idle handler firing every sessionLogIntervalSeconds so
                                // that long-lived / keep-alive connections emit a heartbeat log even
                                // when no message is in flight.  This handler is added BEFORE the
                                // main message handler so its userEventTriggered fires first.
                                if (sessionLogIntervalSeconds > 0) {
                                    ch.pipeline().addLast("sessionActivityLogger",
                                            new SessionActivityLogHandler(sessionLogIntervalSeconds));
                                }

                                // Main message handler - handles both HAProxyMessage and ByteBuf
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                        String sessionId = ctx.channel().attr(SESSION_ID_KEY).get();
                                        UUID interactionId = ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).get();
                                        if (interactionId == null) {
                                            interactionId = UUID.randomUUID();
                                            ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).set(interactionId);
                                        }

                                        String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
                                        if ("sandbox".equals(activeProfile)) {
                                            handleSandboxProxy(ctx, sessionId, interactionId);
                                        } else {
                                            // Handle HAProxy header if present
                                            if (msg instanceof HAProxyMessage proxyMsg) {
                                                handleProxyHeader(ctx, proxyMsg, sessionId, interactionId);
                                                return; // Wait for next frame (actual message)
                                            }
                                        }

                                        // Handle actual message content
                                        if (msg instanceof ByteBuf byteBuf) {
                                            String messageContent = byteBuf.toString(StandardCharsets.UTF_8);

                                            // Always log HAProxy details for every inbound frame so
                                            // keep-alive messages and probes are traceable.
                                            logProxyDetails(ctx, sessionId, interactionId);

                                            // If no-delimiter flag was set by the decoder, accumulate and log only
                                            Boolean noDelimiter = ctx.channel().attr(NO_DELIMITER_DETECTED_KEY).get();
                                            if (Boolean.TRUE.equals(noDelimiter)) {
                                                handleNoDelimiterMessage(ctx, messageContent, sessionId, interactionId);
                                                return;
                                            }

                                            long startTime = ctx.channel().attr(MESSAGE_START_TIME_KEY).get();
                                            long receiveTime = System.currentTimeMillis() - startTime;
                                            int fragmentCount = ctx.channel().attr(FRAGMENT_COUNT_KEY).get().get();
                                            long totalBytes = ctx.channel().attr(TOTAL_BYTES_KEY).get().get();
                                            int sessionMsgCount = ctx.channel().attr(SESSION_MESSAGE_COUNT_KEY).get().incrementAndGet();
                                            
                                            logger.info("MESSAGE_FULLY_RECEIVED [sessionId={}] [interactionId={}] [haproxyDetails={}] sessionMessageCount={} totalSize={} bytes, fragments={}, receiveTimeMs={}, avgFragmentSize={} bytes",
                                                    sessionId, interactionId, haproxyDetails(ctx), sessionMsgCount,
                                                    totalBytes, fragmentCount, receiveTime, 
                                                    fragmentCount > 0 ? (totalBytes / fragmentCount) : totalBytes);
                                            
                                            handleMessage(ctx, messageContent, sessionId, interactionId);
                                        }
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                        String sessionId = ctx.channel().attr(SESSION_ID_KEY).get();
                                        UUID interactionId = ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).get();
                                        if (interactionId == null) {
                                            interactionId = UUID.randomUUID();
                                            ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).set(interactionId);
                                        }
                                        
                                        // Check if we've already sent a NACK for this channel
                                        Boolean nackAlreadySent = ctx.channel().attr(ERROR_NACK_SENT_KEY).get();
                                        if (nackAlreadySent != null && nackAlreadySent) {
                                            return;
                                        }
                                        
                                        // Mark that we're sending a NACK to prevent duplicates
                                        ctx.channel().attr(ERROR_NACK_SENT_KEY).set(true);
                                        
                                        String errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
                                        
                                        logger.error("Exception in TCP handler [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]: {}",
                                                sessionId, interactionId, haproxyDetails(ctx), errorTraceId, cause.getMessage(), cause);
                                        
                                        // Convert Throwable to Exception for logging if needed
                                        Exception exceptionForLogging;
                                        if (cause instanceof Exception) {
                                            exceptionForLogging = (Exception) cause;
                                        } else {
                                            exceptionForLogging = new Exception(cause);
                                        }
                                        
                                        try {
                                            LogUtil.logDetailedError(
                                                500,
                                                "Channel exception caught",
                                                interactionId.toString(),
                                                errorTraceId,
                                                exceptionForLogging
                                            );
                                        } catch (Exception logException) {
                                            logger.warn("Failed to log detailed error [sessionId={}] [interactionId={}] [haproxyDetails={}]: {}", 
                                                    sessionId, interactionId, haproxyDetails(ctx), logException.getMessage());
                                        }
                                        
                                        if (ctx.channel().isActive()) {
                                            try {
                                                // Try to send NACK before closing
                                                String errorMsg = cause.getMessage() != null ? cause.getMessage() : "Unknown error";
                                                String sanitizedError = errorMsg.replace("|", " ").replace("\r", " ").replace("\n", " ");
                                                
                                                String genericNack = "MSH|^~\\&|SERVER|LOCAL|CLIENT|REMOTE|" + Instant.now() + "||ACK|" +
                                                        UUID.randomUUID().toString().substring(0, 20) + "|P|2.5\r" +
                                                        "MSA|AR|UNKNOWN|Channel exception: " + sanitizedError + "\r" +
                                                        "ERR|||207^Application internal error^HL70357||E|||Channel exception occurred\r" +
                                                        "NTE|1||InteractionID: " + interactionId + " | TechBDIngestionApiVersion: " + 
                                                        appConfig.getVersion() + " | ErrorTraceID: " + errorTraceId + "\r";
                                                
                                                String wrappedNack = String.valueOf((char)MLLP_START) + genericNack + (char)MLLP_END_1 + (char)MLLP_END_2;
                                                
                                                ByteBuf responseBuf = ctx.alloc().buffer();
                                                responseBuf.writeBytes(wrappedNack.getBytes(StandardCharsets.UTF_8));
                                                
                                                logger.info("SENDING_NACK_ON_EXCEPTION [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]", 
                                                        sessionId, interactionId, haproxyDetails(ctx), errorTraceId);
                                                
                                                final UUID finalInteractionId = interactionId;
                                                final String finalSessionId = sessionId;
                                                final String finalHaproxyDetails = haproxyDetails(ctx);
                                                // Synchronous write with delay before close
                                                ctx.writeAndFlush(responseBuf).addListener(future -> {
                                                    if (future.isSuccess()) {
                                                        logger.info("NACK_SENT_ON_EXCEPTION [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]", 
                                                                finalSessionId, finalInteractionId, finalHaproxyDetails, errorTraceId);
                                                        // Delay close to ensure NACK is transmitted
                                                        ctx.executor().schedule(() -> {
                                                            logger.debug("CLOSING_CONNECTION_AFTER_EXCEPTION [sessionId={}] [interactionId={}] [haproxyDetails={}]",
                                                                    finalSessionId, finalInteractionId, finalHaproxyDetails);
                                                            clearChannelAttributes(ctx);
                                                            ctx.close();
                                                        }, 100, TimeUnit.MILLISECONDS);
                                                    } else {
                                                        logger.error("NACK_SEND_FAILED_ON_EXCEPTION [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]: {}", 
                                                                finalSessionId, finalInteractionId, finalHaproxyDetails, errorTraceId, 
                                                                future.cause() != null ? future.cause().getMessage() : "unknown");
                                                        clearChannelAttributes(ctx);
                                                        ctx.close();
                                                    }
                                                });
                                            } catch (Exception e) {
                                                logger.error("FAILED_TO_SEND_NACK_ON_EXCEPTION [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]: {}", 
                                                        sessionId, interactionId, haproxyDetails(ctx), errorTraceId, e.getMessage(), e);
                                                clearChannelAttributes(ctx);
                                                ctx.close();
                                            }
                                        } else {
                                            clearChannelAttributes(ctx);
                                        }
                                    }

                                    @Override
                                    public void channelInactive(ChannelHandlerContext ctx) {
                                        // Read session attributes FIRST — clearChannelAttributes does NOT
                                        // touch session keys, so these are always valid here even if
                                        // clearChannelAttributes was called just before ctx.close().
                                        String sessionId = ctx.channel().attr(SESSION_ID_KEY).get();
                                        Long sessionStart = ctx.channel().attr(SESSION_START_TIME_KEY).get();
                                        AtomicInteger msgCount = ctx.channel().attr(SESSION_MESSAGE_COUNT_KEY).get();
                                        long sessionDurationMs = sessionStart != null
                                                ? System.currentTimeMillis() - sessionStart : -1;
                                        int totalMessages = msgCount != null ? msgCount.get() : 0;

                                        logger.info("TCP_SESSION_CLOSED [sessionId={}] [haproxyDetails={}] " +
                                                "sessionDurationMs={} sessionDurationSec={} totalMessagesProcessed={}",
                                                sessionId,
                                                haproxyDetails(ctx),
                                                sessionDurationMs,
                                                sessionDurationMs >= 0 ? sessionDurationMs / 1000.0 : -1,
                                                totalMessages);

                                        // Clear all remaining attributes (including session keys —
                                        // this is the ONLY place session attributes are nulled out).
                                        clearChannelAttributes(ctx);
                                        ctx.channel().attr(SESSION_ID_KEY).set(null);
                                        ctx.channel().attr(SESSION_START_TIME_KEY).set(null);
                                        ctx.channel().attr(SESSION_MESSAGE_COUNT_KEY).set(null);
                                    }
                                });
                                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {

                                    @Override
                                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                        if (evt instanceof IdleStateEvent idleEvt && idleEvt.state() == IdleState.READER_IDLE) {
                                            String sessionId = ctx.channel().attr(SESSION_ID_KEY).get();
                                            UUID interactionId = ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).get();
                                            if (interactionId == null) {
                                                interactionId = UUID.randomUUID();
                                                ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).set(interactionId);
                                            }

                                            Boolean nackAlreadySent = ctx.channel().attr(ERROR_NACK_SENT_KEY).get();
                                            if (nackAlreadySent != null && nackAlreadySent) {
                                                ctx.close();
                                                return;
                                            }
                                            ctx.channel().attr(ERROR_NACK_SENT_KEY).set(true);

                                            Integer kat = ctx.channel().attr(KEEP_ALIVE_TIMEOUT_KEY).get();
                                            int effectiveTimeout = kat != null ? kat : readTimeoutSeconds;
                                            String errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();

                                            Long sessionStart = ctx.channel().attr(SESSION_START_TIME_KEY).get();
                                            long sessionDurationMs = sessionStart != null
                                                    ? System.currentTimeMillis() - sessionStart : -1;
                                            AtomicInteger msgCount = ctx.channel().attr(SESSION_MESSAGE_COUNT_KEY).get();
                                            int totalMessages = msgCount != null ? msgCount.get() : 0;

                                            logProxyDetails(ctx, sessionId, interactionId);
                                            logger.warn("IDLE_TIMEOUT_EXCEEDED [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}] " +
                                                    "idleTimeout={}s sessionDurationMs={} sessionDurationSec={} totalMessagesInSession={} - closing connection",
                                                    sessionId, interactionId, haproxyDetails(ctx), errorTraceId, effectiveTimeout,
                                                    sessionDurationMs,
                                                    sessionDurationMs >= 0 ? sessionDurationMs / 1000.0 : -1,
                                                    totalMessages);

                                            try {
                                                LogUtil.logDetailedError(
                                                        408,
                                                        String.format("Idle timeout exceeded after %d seconds", effectiveTimeout),
                                                        interactionId.toString(),
                                                        errorTraceId,
                                                        new Exception("IdleStateHandler reader idle"));
                                            } catch (Exception logException) {
                                                logger.warn("Failed to log idle timeout error [sessionId={}] [interactionId={}] [haproxyDetails={}]: {}",
                                                        sessionId, interactionId, haproxyDetails(ctx), logException.getMessage());
                                            }

                                            if (ctx.channel().isActive()) {
                                                try {
                                                    String timeoutError = String.format(
                                                            "Read idle timeout: No data received within %d seconds", effectiveTimeout);

                                                    String timeoutNack = "MSH|^~\\&|SERVER|LOCAL|CLIENT|REMOTE|"
                                                            + Instant.now() + "||ACK|" +
                                                            UUID.randomUUID().toString().substring(0, 20) + "|P|2.5\r" +
                                                            "MSA|AR|UNKNOWN|" + timeoutError + "\r" +
                                                            "ERR|||207^Application internal error^HL70357||E|||Idle timeout occurred\r" +
                                                            "NTE|1||InteractionID: " + interactionId +
                                                            " | TechBDIngestionApiVersion: " + appConfig.getVersion() +
                                                            " | ErrorTraceID: " + errorTraceId + "\r";

                                                    String wrappedNack = String.valueOf((char) MLLP_START) + timeoutNack
                                                            + (char) MLLP_END_1 + (char) MLLP_END_2;

                                                    ByteBuf responseBuf = ctx.alloc().buffer();
                                                    responseBuf.writeBytes(wrappedNack.getBytes(StandardCharsets.UTF_8));

                                                    logger.info("SENDING_NACK_ON_IDLE [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]",
                                                            sessionId, interactionId, haproxyDetails(ctx), errorTraceId);

                                                    final UUID finalInteractionId = interactionId;
                                                    final String finalErrorTraceId = errorTraceId;
                                                    final String finalSessionId = sessionId;
                                                    final String finalHaproxyDetails = haproxyDetails(ctx);

                                                    ctx.writeAndFlush(responseBuf).addListener(future -> {
                                                        if (future.isSuccess()) {
                                                            logger.info("NACK_SENT_ON_IDLE [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]",
                                                                    finalSessionId, finalInteractionId, finalHaproxyDetails, finalErrorTraceId);
                                                        } else {
                                                            logger.error("NACK_SEND_FAILED_ON_IDLE [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]: {}",
                                                                    finalSessionId, finalInteractionId, finalHaproxyDetails, finalErrorTraceId,
                                                                    future.cause() != null ? future.cause().getMessage() : "unknown");
                                                        }
                                                        logger.info("CLOSING_CONNECTION_AFTER_IDLE [sessionId={}] [interactionId={}] [haproxyDetails={}]",
                                                                finalSessionId, finalInteractionId, finalHaproxyDetails);
                                                        clearChannelAttributes(ctx);
                                                        ctx.close();
                                                    });
                                                } catch (Exception e) {
                                                    logger.error("FAILED_TO_SEND_NACK_ON_IDLE [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]: {}",
                                                            sessionId, interactionId, haproxyDetails(ctx), errorTraceId, e.getMessage(), e);
                                                    clearChannelAttributes(ctx);
                                                    ctx.close();
                                                }
                                            } else {
                                                clearChannelAttributes(ctx);
                                            }
                                        } else {
                                            super.userEventTriggered(ctx, evt);
                                        }
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                            throws Exception {
                                        if (cause instanceof ReadTimeoutException) {
                                            String sessionId = ctx.channel().attr(SESSION_ID_KEY).get();
                                            UUID interactionId = ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).get();
                                            if (interactionId == null) {
                                                interactionId = UUID.randomUUID();
                                                ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).set(interactionId);
                                            }

                                            // Check if we've already sent a NACK for this channel
                                            Boolean nackAlreadySent = ctx.channel().attr(ERROR_NACK_SENT_KEY).get();
                                            if (nackAlreadySent != null && nackAlreadySent) {
                                                ctx.close();
                                                return;
                                            }

                                            // Mark that we're sending a NACK
                                            ctx.channel().attr(ERROR_NACK_SENT_KEY).set(true);

                                            String errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();

                                            Long sessionStart = ctx.channel().attr(SESSION_START_TIME_KEY).get();
                                            long sessionDurationMs = sessionStart != null
                                                    ? System.currentTimeMillis() - sessionStart : -1;

                                            logProxyDetails(ctx, sessionId, interactionId);
                                            logger.error(
                                                    "READ_TIMEOUT_EXCEEDED [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}] " +
                                                    "timeout={}s sessionDurationMs={} sessionDurationSec={} - sending NACK",
                                                    sessionId, interactionId, haproxyDetails(ctx), errorTraceId,
                                                    readTimeoutSeconds,
                                                    sessionDurationMs,
                                                    sessionDurationMs >= 0 ? sessionDurationMs / 1000.0 : -1);

                                            try {
                                                LogUtil.logDetailedError(
                                                        408,
                                                        String.format("Read timeout exceeded after %d seconds",
                                                                readTimeoutSeconds),
                                                        interactionId.toString(),
                                                        errorTraceId,
                                                        new ReadTimeoutException());
                                            } catch (Exception logException) {
                                                logger.warn("Failed to log timeout error [sessionId={}] [interactionId={}] [haproxyDetails={}]: {}",
                                                        sessionId, interactionId, haproxyDetails(ctx), logException.getMessage());
                                            }

                                            if (ctx.channel().isActive()) {
                                                try {
                                                    String timeoutError = String.format(
                                                            "Read timeout: No complete message received within %d seconds",
                                                            readTimeoutSeconds);

                                                    // Generate HL7 NACK for timeout
                                                    String timeoutNack = "MSH|^~\\&|SERVER|LOCAL|CLIENT|REMOTE|"
                                                            + Instant.now() + "||ACK|" +
                                                            UUID.randomUUID().toString().substring(0, 20) + "|P|2.5\r" +
                                                            "MSA|AR|UNKNOWN|" + timeoutError + "\r" +
                                                            "ERR|||207^Application internal error^HL70357||E|||Read timeout occurred\r"
                                                            +
                                                            "NTE|1||InteractionID: " + interactionId +
                                                            " | TechBDIngestionApiVersion: " + appConfig.getVersion() +
                                                            " | ErrorTraceID: " + errorTraceId + "\r";

                                                    String wrappedNack = String.valueOf((char) MLLP_START) + timeoutNack
                                                            + (char) MLLP_END_1 + (char) MLLP_END_2;

                                                    ByteBuf responseBuf = ctx.alloc().buffer();
                                                    responseBuf
                                                            .writeBytes(wrappedNack.getBytes(StandardCharsets.UTF_8));

                                                    logger.info(
                                                            "SENDING_NACK_ON_TIMEOUT [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]",
                                                            sessionId, interactionId, haproxyDetails(ctx), errorTraceId);

                                                    final UUID finalInteractionId = interactionId;
                                                    final String finalErrorTraceId = errorTraceId;
                                                    final String finalSessionId = sessionId;
                                                    final String finalHaproxyDetails = haproxyDetails(ctx);

                                                    ctx.writeAndFlush(responseBuf).addListener(future -> {
                                                        if (future.isSuccess()) {
                                                            logger.info(
                                                                    "NACK_SENT_ON_TIMEOUT [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]",
                                                                    finalSessionId, finalInteractionId, finalHaproxyDetails, finalErrorTraceId);
                                                        } else {
                                                            logger.error(
                                                                    "NACK_SEND_FAILED_ON_TIMEOUT [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]: {}",
                                                                    finalSessionId, finalInteractionId, finalHaproxyDetails, finalErrorTraceId,
                                                                    future.cause() != null ? future.cause().getMessage()
                                                                            : "unknown");
                                                        }
                                                        // Close connection after attempting to send NACK
                                                        logger.info(
                                                                "CLOSING_CONNECTION_AFTER_TIMEOUT [sessionId={}] [interactionId={}] [haproxyDetails={}]",
                                                                finalSessionId, finalInteractionId, finalHaproxyDetails);
                                                        clearChannelAttributes(ctx);
                                                        ctx.close();
                                                    });
                                                } catch (Exception e) {
                                                    logger.error(
                                                            "FAILED_TO_SEND_NACK_ON_TIMEOUT [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]: {}",
                                                            sessionId, interactionId, haproxyDetails(ctx), errorTraceId, e.getMessage(), e);
                                                    clearChannelAttributes(ctx);
                                                    ctx.close();
                                                }
                                            } else {
                                                clearChannelAttributes(ctx);
                                            }
                                        } else {
                                            // Pass other exceptions to the next handler
                                            super.exceptionCaught(ctx, cause);
                                        }
                                    }
                                });
                            }
                        });

                ChannelFuture future = bootstrap.bind(tcpPort).sync();
                logger.info("TCP Server listening on port {} (MLLP=HL7 with ACK, TCP with delimiters=Generic with ACK). Max message size: {} bytes. TCP Delimiters: START=0x{}, END1=0x{}, END2=0x{}. SessionLogInterval={}s",
                        tcpPort, maxMessageSizeBytes, 
                        String.format("%02X", tcpStartDelimiter),
                        String.format("%02X", tcpEndDelimiter1),
                        String.format("%02X", tcpEndDelimiter2),
                        sessionLogIntervalSeconds);
                future.channel().closeFuture().sync();
            } catch (Exception e) {
                logger.error("Failed to start TCP Server on port {}", tcpPort, e);
            } finally {
                boss.shutdownGracefully();
                worker.shutdownGracefully();
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // SessionActivityLogHandler
    // -------------------------------------------------------------------------

    /**
     * Logs a periodic "session still active" message at a configurable interval.
     * Useful for long-lived TCP connections where no data is exchanged for a while.
     * The log confirms the connection is still open and shows how long it has been
     * active and how many messages were processed.
     */
    private class SessionActivityLogHandler extends IdleStateHandler {

        SessionActivityLogHandler(int allIdleSeconds) {
            // readerIdle=0 means disabled; writerIdle=0 means disabled; allIdle=configured
            super(0, 0, allIdleSeconds, TimeUnit.SECONDS);
        }

        @Override
        protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
            // Only act on ALL_IDLE events; let others propagate normally
            if (evt.state() == IdleState.ALL_IDLE) {
                String sessionId = ctx.channel().attr(SESSION_ID_KEY).get();
                Long sessionStart = ctx.channel().attr(SESSION_START_TIME_KEY).get();
                long sessionDurationMs = sessionStart != null
                        ? System.currentTimeMillis() - sessionStart : -1;
                AtomicInteger msgCount = ctx.channel().attr(SESSION_MESSAGE_COUNT_KEY).get();
                int totalMessages = msgCount != null ? msgCount.get() : 0;
                Integer kat = ctx.channel().attr(KEEP_ALIVE_TIMEOUT_KEY).get();

                logger.info("TCP_SESSION_ACTIVE [sessionId={}] [haproxyDetails={}] sessionDurationMs={} sessionDurationSec={} totalMessagesInSession={} " +
                        "keepAliveTimeoutConfigured={}",
                        sessionId,
                        haproxyDetails(ctx),
                        sessionDurationMs,
                        sessionDurationMs >= 0 ? sessionDurationMs / 1000.0 : -1,
                        totalMessages,
                        kat != null ? kat + "s" : "no");
                // Do NOT close the channel — this is purely a diagnostic heartbeat.
                // The actual read-timeout or keepAliveTimeout handler will close it.
            } else {
                // Propagate all other idle events down the pipeline
                super.channelIdle(ctx, evt);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Early keepAlive resolution (called at connection time, before any message)
    // -------------------------------------------------------------------------

    /**
     * Resolves keepAliveTimeout using the destPort extracted from the HAProxy
     * details string ({@code "sourceAddress=x, sourcePort=n, destAddress=y, destPort=m"}).
     *
     * <p>Returns 0 (fall back to ReadTimeoutHandler) when:
     * <ul>
     *   <li>{@code haproxyDetails} is null or empty — HAProxy header not yet received</li>
     *   <li>destPort cannot be parsed</li>
     *   <li>no matching {@link PortConfig.PortEntry} is found</li>
     * </ul>
     */
    private int resolveKeepAliveTimeoutAtConnect(String sessionId, String haproxyDetails) {
        if (haproxyDetails == null || haproxyDetails.isEmpty()) {
            logger.debug("KAT_RESOLUTION_SKIPPED [sessionId={}] haproxyDetails not yet available",
                    sessionId);
            return 0;
        }

        String destPortStr = parseHaproxyField(haproxyDetails, "destPort");
        if (destPortStr.isEmpty()) {
            logger.warn("KAT_RESOLUTION_SKIPPED [sessionId={}] destPort not found in haproxyDetails={}",
                    sessionId, haproxyDetails);
            return 0;
        }

        try {
            int destPort = Integer.parseInt(destPortStr);
            String sourceAddress = parseHaproxyField(haproxyDetails, "sourceAddress");
            String sourcePort    = parseHaproxyField(haproxyDetails, "sourcePort");
            String destAddress   = parseHaproxyField(haproxyDetails, "destAddress");

            Map<String, String> minimalHeaders = new HashMap<>();
            minimalHeaders.put("DestinationPort", destPortStr);
            minimalHeaders.put("DestinationIp",   destAddress);
            minimalHeaders.put("SourceIp",         sourceAddress);
            minimalHeaders.put("SourcePort",       sourcePort);

            ZonedDateTime now = ZonedDateTime.now();
            String timestamp = String.valueOf(now.toInstant().toEpochMilli());

            RequestContext minimalCtx = new RequestContext(
                    minimalHeaders,
                    "",
                    appConfig.getAws().getSqs().getFifoQueueUrl(),
                    UUID.randomUUID().toString(),
                    now, timestamp,
                    "tcp-message", 0,
                    "", "", "", "", "",
                    "", "TCP",
                    destPortStr, sourceAddress, sourceAddress, destAddress, destPortStr,
                    "", "", "",
                    MessageSourceType.TCP,
                    getDataBucketName(), getMetadataBucketName(),
                    appConfig.getVersion());

            Optional<PortConfig.PortEntry> portEntryOpt = portResolverService.resolve(minimalCtx);
            int kat = portEntryOpt.map(pe -> pe.getKeepAliveTimeout()).orElse(0);

            if (kat > 0) {
                logger.info("KAT_RESOLVED [sessionId={}] haproxyDetails={} destPort={} keepAliveTimeout={}s",
                        sessionId, haproxyDetails, destPort, kat);
            } else {
                logger.debug("KAT_NOT_CONFIGURED [sessionId={}] haproxyDetails={} destPort={} - will use ReadTimeoutHandler",
                        sessionId, haproxyDetails, destPort);
            }
            return kat;
        } catch (Exception e) {
            logger.warn("KAT_RESOLUTION_FAILED [sessionId={}] haproxyDetails={} - falling back to ReadTimeoutHandler: {}",
                    sessionId, haproxyDetails, e.getMessage());
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Parse TCP delimiters
    // -------------------------------------------------------------------------

    /**
     * Parse TCP delimiter hex strings to byte values
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

    // -------------------------------------------------------------------------
    // No-delimiter message handler
    // -------------------------------------------------------------------------

    /**
     * Handle messages received with no recognized delimiter.
     */
    private void handleNoDelimiterMessage(ChannelHandlerContext ctx, String content, String sessionId, UUID interactionId) {
        StringBuilder accumulator = ctx.channel().attr(RAW_ACCUMULATOR_KEY).get();
        if (accumulator == null) {
            accumulator = new StringBuilder();
            ctx.channel().attr(RAW_ACCUMULATOR_KEY).set(accumulator);
        }
        accumulator.append(content);

        logger.warn("NO_DELIMITER_MESSAGE [sessionId={}] [interactionId={}] [haproxyDetails={}] NO_DELIMITER_DETECTED - message will NOT be processed or uploaded. " +
                "accumulatedSize={} bytes",
                sessionId, interactionId, haproxyDetails(ctx), accumulator.length());

        if (FeatureEnum.isEnabled(FeatureEnum.LOG_INCOMING_MESSAGE)) {
            logger.warn("FULL_MESSAGE_LOGGING [sessionId={}] [interactionId={}] [haproxyDetails={}] rawContent=[{}]",
                    sessionId, interactionId, haproxyDetails(ctx), accumulator.toString());
        }
    }

    // -------------------------------------------------------------------------
    // Delimiter-based frame decoder
    // -------------------------------------------------------------------------

    /**
     * Delimiter-based frame decoder for Netty TCP server.
     */
    private class DelimiterBasedFrameDecoder extends ByteToMessageDecoder {
        private final int maxFrameLength;

        public DelimiterBasedFrameDecoder(int maxFrameLength) {
            this.maxFrameLength = maxFrameLength;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            String sessionId = ctx.channel().attr(SESSION_ID_KEY).get();
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
            
            logger.info("FRAGMENTED_MESSAGE [sessionId={}] [interactionId={}] [haproxyDetails={}] fragment={}, fragmentSize={} bytes, cumulativeSize={} bytes, bufferReadable={} bytes",
                    sessionId, interactionId, haproxyDetails(ctx), currentFragment, fragmentSize, currentTotalBytes, in.readableBytes());

            // Check if we have minimum bytes for delimiter detection
            if (in.readableBytes() < 3) {
                return;
            }

            int startIndex = in.readerIndex();
            byte firstByte = in.getByte(startIndex);

            // Check for MLLP delimiters first (for HL7 messages)
            if (firstByte == MLLP_START) {
                logger.info("MLLP_START_DETECTED [sessionId={}] [interactionId={}] [haproxyDetails={}] searching for MLLP end markers in {} bytes",
                        sessionId, interactionId, haproxyDetails(ctx), in.readableBytes());
                
                int endIndex = -1;
                for (int i = startIndex + 1; i < in.writerIndex() - 1; i++) {
                    if (in.getByte(i) == MLLP_END_1 && in.getByte(i + 1) == MLLP_END_2) {
                        endIndex = i + 2;
                        logger.debug("MLLP_END_MARKERS_FOUND [sessionId={}] [interactionId={}] [haproxyDetails={}] at position={}",
                                sessionId, interactionId, haproxyDetails(ctx), i);
                        break;
                    }
                }

                if (endIndex == -1) {
                    if (in.readableBytes() > maxFrameLength) {
                        logger.warn("MLLP_MESSAGE_SIZE_LIMIT_EXCEEDED [sessionId={}] [interactionId={}] [haproxyDetails={}] size={} bytes exceeds max={} bytes",
                                sessionId, interactionId, haproxyDetails(ctx), in.readableBytes(), maxFrameLength);
                        ctx.channel().attr(MESSAGE_SIZE_EXCEEDED_KEY).set(true);
                        endIndex = startIndex + 1;
                    } else {
                        return;
                    }
                }

                int frameLength = endIndex - startIndex;
                ByteBuf frame = in.readRetainedSlice(frameLength);
                out.add(frame);
                
                logger.info("MLLP_FRAME_COMPLETE [sessionId={}] [interactionId={}] [haproxyDetails={}] totalLength={} bytes, assembled from {} fragments, avgFragmentSize={} bytes",
                        sessionId, interactionId, haproxyDetails(ctx), frameLength, currentFragment,
                        currentFragment > 0 ? (frameLength / currentFragment) : frameLength);

            }
            // Check for TCP delimiters (for non-HL7 messages)
            else if (firstByte == tcpStartDelimiter) {
                logger.debug("TCP_DELIMITER_START_DETECTED [sessionId={}] [interactionId={}] [haproxyDetails={}] searching for TCP end markers (0x{}, 0x{}) in {} bytes",
                        sessionId, interactionId, haproxyDetails(ctx),
                        String.format("%02X", tcpEndDelimiter1), String.format("%02X", tcpEndDelimiter2),
                        in.readableBytes());
                
                int endIndex = -1;
                for (int i = startIndex + 1; i < in.writerIndex() - 1; i++) {
                    if (in.getByte(i) == tcpEndDelimiter1 && in.getByte(i + 1) == tcpEndDelimiter2) {
                        endIndex = i + 2;
                        logger.info("TCP_END_MARKERS_FOUND [sessionId={}] [interactionId={}] [haproxyDetails={}] at position={}",
                                sessionId, interactionId, haproxyDetails(ctx), i);
                        break;
                    }
                }

                if (endIndex == -1) {
                    if (in.readableBytes() > maxFrameLength) {
                        logger.warn("TCP_DELIMITED_MESSAGE_SIZE_LIMIT_EXCEEDED [sessionId={}] [interactionId={}] [haproxyDetails={}] size={} bytes exceeds max={} bytes",
                                sessionId, interactionId, haproxyDetails(ctx), in.readableBytes(), maxFrameLength);
                        ctx.channel().attr(MESSAGE_SIZE_EXCEEDED_KEY).set(true);
                        endIndex = startIndex + 1;
                    } else {
                        return;
                    }
                }

                int frameLength = endIndex - startIndex;
                ByteBuf frame = in.readRetainedSlice(frameLength);
                out.add(frame);
                
                logger.info("TCP_DELIMITED_FRAME_COMPLETE [sessionId={}] [interactionId={}] [haproxyDetails={}] totalLength={} bytes, assembled from {} fragments, avgFragmentSize={} bytes",
                        sessionId, interactionId, haproxyDetails(ctx), frameLength, currentFragment,
                        currentFragment > 0 ? (frameLength / currentFragment) : frameLength);

            }
            else {
                logger.warn("NO_DELIMITER_DETECTED [sessionId={}] [interactionId={}] [haproxyDetails={}] firstByte=0x{}, readableBytes={}",
                        sessionId, interactionId, haproxyDetails(ctx),
                        String.format("%02X", firstByte),
                        in.readableBytes());

                ctx.channel().attr(NO_DELIMITER_DETECTED_KEY).set(true);

                int remaining = in.readableBytes();
                ByteBuf raw = in.readRetainedSlice(remaining);
                out.add(raw);
            }
        }

        @Override
        protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            String sessionId = ctx.channel().attr(SESSION_ID_KEY).get();
            UUID interactionId = ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).get();
            
            if (in.isReadable()) {
                int frameLength = in.readableBytes();
                ByteBuf frame = in.readRetainedSlice(frameLength);
                out.add(frame);
                
                AtomicInteger fragmentCount = ctx.channel().attr(FRAGMENT_COUNT_KEY).get();
                int currentFragment = fragmentCount != null ? fragmentCount.get() : 0;
                
                logger.info("FINAL_FRAME_ON_CLOSE [sessionId={}] [interactionId={}] [haproxyDetails={}] length={} bytes, totalFragments={}",
                        sessionId, interactionId, haproxyDetails(ctx), frameLength, currentFragment);
            } else {
                logger.info("CHANNEL_CLOSED_NO_REMAINING_DATA [sessionId={}] [interactionId={}] [haproxyDetails={}]",
                        sessionId, interactionId, haproxyDetails(ctx));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            String sessionId = ctx.channel().attr(SESSION_ID_KEY).get();
            UUID interactionId = ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).get();
            logger.error("DECODER_EXCEPTION [sessionId={}] [interactionId={}] [haproxyDetails={}]: {}",
                    sessionId, interactionId, haproxyDetails(ctx), cause.getMessage(), cause);
            super.exceptionCaught(ctx, cause);
        }
    }

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    /**
     * Clear per-message channel attributes to prevent memory leaks.
     *
     * <p><b>Session attributes ({@code SESSION_ID_KEY}, {@code SESSION_START_TIME_KEY},
     * {@code SESSION_MESSAGE_COUNT_KEY}) are intentionally NOT cleared here.</b>
     * They must survive until {@code channelInactive} fires so the final
     * {@code TCP_SESSION_CLOSED} log line always has valid values, even when this
     * method is called just before {@code ctx.close()}.  Session attributes are
     * cleared exclusively inside {@code channelInactive}.
     */
    private void clearChannelAttributes(ChannelHandlerContext ctx) {
        ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).set(null);
        ctx.channel().attr(MESSAGE_START_TIME_KEY).set(null);
        ctx.channel().attr(FRAGMENT_COUNT_KEY).set(null);
        ctx.channel().attr(TOTAL_BYTES_KEY).set(null);
        ctx.channel().attr(MESSAGE_SIZE_EXCEEDED_KEY).set(null);
        ctx.channel().attr(ERROR_NACK_SENT_KEY).set(null);
        ctx.channel().attr(KEEP_ALIVE_TIMEOUT_KEY).set(null);
        ctx.channel().attr(CLIENT_IP_KEY).set(null);
        ctx.channel().attr(CLIENT_PORT_KEY).set(null);
        ctx.channel().attr(DESTINATION_IP_KEY).set(null);
        ctx.channel().attr(DESTINATION_PORT_KEY).set(null);
        ctx.channel().attr(NO_DELIMITER_DETECTED_KEY).set(null);
        ctx.channel().attr(RAW_ACCUMULATOR_KEY).set(null);
        ctx.channel().attr(HAPROXY_DETAILS_KEY).set(null);
        // SESSION_ID_KEY, SESSION_START_TIME_KEY, SESSION_MESSAGE_COUNT_KEY
        // are deliberately left intact — cleared only in channelInactive.
    }

    /**
     * Logs the HAProxy details stored on the channel for the current interaction.
     */
    private void logProxyDetails(ChannelHandlerContext ctx, String sessionId, UUID interactionId) {
        String details = ctx.channel().attr(HAPROXY_DETAILS_KEY).get();
        if (details != null) {
            logger.info("HAPROXY_DETAILS [sessionId={}] [interactionId={}] {}",
                    sessionId, interactionId, details);
        } else {
            logger.info("HAPROXY_DETAILS [sessionId={}] [interactionId={}] noProxyHeader",
                    sessionId, interactionId);
        }
    }

    /**
     * Handle HAProxy protocol header to extract real client IP/port.
     *
     * <p>After storing the HAProxy source/dest coordinates we <b>re-resolve the
     * keepAliveTimeout</b> using the real destination port carried in the header.
     * At {@code initChannel} time only the local socket port was available; the
     * HAProxy {@code destPort} is the authoritative value and may map to a
     * different {@link PortConfig.PortEntry} (e.g. when a load-balancer rewrites
     * the destination port).  The pipeline timeout handler is replaced in-place
     * so the correct idle/read-timeout is active before the first message frame
     * arrives on this channel.
     */
    private void handleProxyHeader(ChannelHandlerContext ctx, HAProxyMessage proxyMsg,
            String sessionId, UUID interactionId) {
        if (proxyMsg.command() == HAProxyCommand.PROXY) {
            String sourceAddress = proxyMsg.sourceAddress();
            int    sourcePort    = proxyMsg.sourcePort();
            String destAddress   = proxyMsg.destinationAddress();
            int    destPort      = proxyMsg.destinationPort();

            logger.info("PROXY_HEADER [sessionId={}] [interactionId={}] sourceAddress={}, sourcePort={}, destAddress={}, destPort={}",
                    sessionId, interactionId, sourceAddress, sourcePort, destAddress, destPort);

            String details = String.format("sourceAddress=%s, sourcePort=%d, destAddress=%s, destPort=%d",
                    sourceAddress, sourcePort, destAddress, destPort);
            ctx.channel().attr(HAPROXY_DETAILS_KEY).set(details);

            // -----------------------------------------------------------------
            // Resolve keepAliveTimeout from the HAProxy details string.
            // HAPROXY_DETAILS_KEY is the single source of truth for all port/IP
            // resolution — no other channel address APIs are consulted.
            // -----------------------------------------------------------------
            int kat = resolveKeepAliveTimeoutAtConnect(sessionId, details);

            if (kat > 0) {
                ctx.channel().attr(KEEP_ALIVE_TIMEOUT_KEY).set(kat);
                if (ctx.pipeline().get("idleStateHandler") != null) {
                    ctx.pipeline().replace("idleStateHandler", "idleStateHandler",
                            new IdleStateHandler(kat, 0, 0, TimeUnit.SECONDS));
                } else if (ctx.pipeline().get("defaultReadTimeout") != null) {
                    ctx.pipeline().replace("defaultReadTimeout", "idleStateHandler",
                            new IdleStateHandler(kat, 0, 0, TimeUnit.SECONDS));
                }
                logger.info("TCP_SESSION_OPENED [sessionId={}] [haproxyDetails={}] sourceAddress={} sourcePort={} destAddress={} destPort={} " +
                        "timeoutHandler=IdleStateHandler keepAliveTimeout={}s (resolved from haproxy destPort)",
                        sessionId, details, sourceAddress, sourcePort, destAddress, destPort, kat);
            } else {
                ctx.channel().attr(KEEP_ALIVE_TIMEOUT_KEY).set(null);
                if (ctx.pipeline().get("idleStateHandler") != null) {
                    ctx.pipeline().replace("idleStateHandler", "defaultReadTimeout",
                            new ReadTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS));
                }
                logger.info("TCP_SESSION_OPENED [sessionId={}] [haproxyDetails={}] sourceAddress={} sourcePort={} destAddress={} destPort={} " +
                        "timeoutHandler=ReadTimeoutHandler readTimeout={}s (resolved from haproxy destPort)",
                        sessionId, details, sourceAddress, sourcePort, destAddress, destPort, readTimeoutSeconds);
            }
        }
    }

    /**
     * Assign dummy HAProxy details for sandbox profile and resolve KAT from them.
     * HAPROXY_DETAILS_KEY is set here so it is the single source of truth for
     * all subsequent port/IP usage — no other channel address APIs are consulted.
     */
    private void handleSandboxProxy(ChannelHandlerContext ctx, String sessionId, UUID interactionId) {
        if (ctx.channel().attr(HAPROXY_DETAILS_KEY).get() != null) {
            return; // Already set
        }

        String dummyClientIp      = "127.0.0.1";
        String dummyClientPort    = "12345";
        String dummyDestinationIp = "127.0.0.1";
        String dummyDestinationPort = "5555";

        String details = String.format("sourceAddress=%s, sourcePort=%s, destAddress=%s, destPort=%s",
                dummyClientIp, dummyClientPort, dummyDestinationIp, dummyDestinationPort);
        ctx.channel().attr(HAPROXY_DETAILS_KEY).set(details);

        logger.info("SANDBOX_PROXY [sessionId={}] [interactionId={}] [haproxyDetails={}]",
                sessionId, interactionId, details);

        // Resolve KAT using the dummy HAProxy details — same code path as production.
        int kat = resolveKeepAliveTimeoutAtConnect(sessionId, details);

        if (kat > 0) {
            ctx.channel().attr(KEEP_ALIVE_TIMEOUT_KEY).set(kat);
            if (ctx.pipeline().get("idleStateHandler") != null) {
                ctx.pipeline().replace("idleStateHandler", "idleStateHandler",
                        new IdleStateHandler(kat, 0, 0, TimeUnit.SECONDS));
            } else if (ctx.pipeline().get("defaultReadTimeout") != null) {
                ctx.pipeline().replace("defaultReadTimeout", "idleStateHandler",
                        new IdleStateHandler(kat, 0, 0, TimeUnit.SECONDS));
            }
            logger.info("TCP_SESSION_OPENED [sessionId={}] [haproxyDetails={}] " +
                    "timeoutHandler=IdleStateHandler keepAliveTimeout={}s (sandbox)",
                    sessionId, details, kat);
        } else {
            ctx.channel().attr(KEEP_ALIVE_TIMEOUT_KEY).set(null);
            logger.info("TCP_SESSION_OPENED [sessionId={}] [haproxyDetails={}] " +
                    "timeoutHandler=ReadTimeoutHandler readTimeout={}s (sandbox)",
                    sessionId, details, readTimeoutSeconds);
        }
    }

    // -------------------------------------------------------------------------
    // Main message router
    // -------------------------------------------------------------------------

    /**
     * Main message handler — routes to HL7 or generic handler based on MLLP detection.
     * Also installs IdleStateHandler in place of the default ReadTimeoutHandler when
     * the resolved PortEntry carries a keepAliveTimeout value.
     */
    private void handleMessage(ChannelHandlerContext ctx, String rawMessage,
            String sessionId, UUID interactionId) {
        // All IP/port values come exclusively from HAPROXY_DETAILS_KEY.
        // No fallback to ch.remoteAddress() / ch.localAddress() — if the HAProxy
        // header has not arrived yet these will be empty strings.
        String clientIP       = haproxySourceAddress(ctx);
        String clientPort     = haproxySourcePort(ctx);
        String destinationIP  = haproxyDestAddress(ctx);
        String destinationPort = haproxyDestPort(ctx);

        // Check if message size exceeded limit
        Boolean messageSizeExceeded = ctx.channel().attr(MESSAGE_SIZE_EXCEEDED_KEY).get();
        if (messageSizeExceeded != null && messageSizeExceeded) {
            String errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
            String errorMessage = String.format("Message size %d bytes exceeds maximum allowed size of %d bytes", 
                    rawMessage.length(), maxMessageSizeBytes);
            
            logger.error("MESSAGE_SIZE_LIMIT_EXCEEDED [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}] size={} bytes, max={} bytes",
                    sessionId, interactionId, haproxyDetails(ctx), errorTraceId, rawMessage.length(), maxMessageSizeBytes);
            
            LogUtil.logDetailedError(
                413, 
                errorMessage, 
                interactionId.toString(), 
                errorTraceId,
                new IllegalArgumentException("Message size limit exceeded")
            );
            
            boolean isMllpWrapped = detectMllpWrapper(rawMessage);
            boolean isTcpDelimited = detectTcpDelimiterWrapper(rawMessage);
            
            RequestContext requestContext = buildRequestContext(
                    rawMessage.trim(),
                    interactionId.toString(),
                    Optional.empty(),
                    clientPort,
                    clientIP,
                    destinationIP,
                    destinationPort,
                    isMllpWrapped ? MessageSourceType.MLLP : MessageSourceType.TCP);
            
            requestContext.setIngestionFailed(true);
            
            String nackMessage;
            if (isMllpWrapped) {
                nackMessage = createHL7AckFromMsh(
                        rawMessage, "AR", errorMessage, interactionId.toString(), errorTraceId);
                sendResponseAndClose(ctx, wrapMllp(nackMessage), sessionId, interactionId, "HL7_NACK_SIZE_EXCEEDED");
            } else {
                nackMessage = generateSimpleNack(interactionId.toString(), errorMessage, errorTraceId);
                sendResponseAndClose(ctx, nackMessage + "\n", sessionId, interactionId, "TCP_NACK_SIZE_EXCEEDED");
            }
            return;
        }

        boolean isMllpWrapped = detectMllpWrapper(rawMessage);
        boolean isTcpDelimited = detectTcpDelimiterWrapper(rawMessage);
        
        logger.info("COMPLETE_MESSAGE_RECEIVED [sessionId={}] [interactionId={}] [haproxyDetails={}] from={}:{}, size={} bytes MLLP_WRAPPED={} TCP_DELIMITED={}",
                sessionId, interactionId, haproxyDetails(ctx), clientIP, clientPort, rawMessage.length(),
                isMllpWrapped ? "YES" : "NO", isTcpDelimited ? "YES" : "NO");

        RequestContext initialContext = buildRequestContext(
                rawMessage.trim(),
                interactionId.toString(),
                Optional.empty(),
                clientPort,
                clientIP,
                destinationIP,
                destinationPort,
                isMllpWrapped ? MessageSourceType.MLLP : MessageSourceType.TCP);

        Optional<PortConfig.PortEntry> portEntryOpt = portResolverService.resolve(initialContext);

        // --- keepAliveTimeout override ---
        int resolvedKat = portEntryOpt
                .map(pe -> pe.getKeepAliveTimeout())
                .orElse(0);

        if (resolvedKat > 0) {
            ctx.channel().attr(KEEP_ALIVE_TIMEOUT_KEY).set(resolvedKat);

            if (ctx.pipeline().get("idleStateHandler") != null) {
                ctx.pipeline().replace("idleStateHandler", "idleStateHandler",
                        new IdleStateHandler(resolvedKat, 0, 0, TimeUnit.SECONDS));
            } else if (ctx.pipeline().get("defaultReadTimeout") != null) {
                ctx.pipeline().replace("defaultReadTimeout", "idleStateHandler",
                        new IdleStateHandler(resolvedKat, 0, 0, TimeUnit.SECONDS));
            }
            logger.info("TIMEOUT_OVERRIDE [sessionId={}] [interactionId={}] [haproxyDetails={}] keepAliveTimeout={}s - IdleStateHandler active",
                    sessionId, interactionId, haproxyDetails(ctx), resolvedKat);
        } else {
            ctx.channel().attr(KEEP_ALIVE_TIMEOUT_KEY).set(null);

            if (ctx.pipeline().get("idleStateHandler") != null) {
                ctx.pipeline().replace("idleStateHandler", "defaultReadTimeout",
                        new ReadTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS));
                logger.info("TIMEOUT_RESTORE [sessionId={}] [interactionId={}] [haproxyDetails={}] no keepAliveTimeout - restored ReadTimeoutHandler ({}s)",
                        sessionId, interactionId, haproxyDetails(ctx), readTimeoutSeconds);
            }
        }
        // --- end keepAliveTimeout override ---

        if (detectMllp(portEntryOpt)) {
            if (isTcpDelimited) {
                handleConflictingWrapper(ctx, rawMessage, sessionId, interactionId,
                        clientIP, clientPort, destinationIP, destinationPort, portEntryOpt,
                        "TCP_DELIMITER_FOUND_EXPECTED_MLLP",
                        "As per port configuration, expecting HL7 message with MLLP wrappers. Received message delimited with TCP delimiters instead.",
                        isTcpDelimited);
                logger.warn("CONFLICTING_WRAPPERS_DETECTED [sessionId={}] [interactionId={}] [haproxyDetails={}] - As per port configuration, expecting HL7 message with MLLP wrappers. Received message delimited with TCP delimiters instead.",
                        sessionId, interactionId, haproxyDetails(ctx));
                return;
            }
            logger.info("MLLP_DETECTED [sessionId={}] [interactionId={}] [haproxyDetails={}] - Using HL7 processing with proper ACK",
                    sessionId, interactionId, haproxyDetails(ctx));
            handleHL7Message(ctx, rawMessage, sessionId, interactionId, clientIP, clientPort,
                    destinationIP, destinationPort, portEntryOpt);
        } else {
            if (isMllpWrapped) {
                handleConflictingWrapper(ctx, rawMessage, sessionId, interactionId,
                        clientIP, clientPort, destinationIP, destinationPort, portEntryOpt,
                        "MLLP_WRAPPER_FOUND_EXPECTED_TCP",
                        "As per port configuration, expecting TCP delimited message. Received message with MLLP wrappers instead.",
                        false);
                logger.warn("CONFLICTING_WRAPPERS_DETECTED [sessionId={}] [interactionId={}] [haproxyDetails={}] - As per port configuration, expecting TCP delimited message. Received message with MLLP wrappers instead.",
                        sessionId, interactionId, haproxyDetails(ctx));
                return;
            }
            logger.info("TCP_MODE_DETECTED [sessionId={}] [interactionId={}] [haproxyDetails={}] - Using generic processing with simple ACK",
                    sessionId, interactionId, haproxyDetails(ctx));
            handleGenericMessage(ctx, rawMessage, sessionId, interactionId, clientIP, clientPort,
                    destinationIP, destinationPort, portEntryOpt);
        }
    }

    // -------------------------------------------------------------------------
    // Conflicting wrapper handler
    // -------------------------------------------------------------------------

    private void handleConflictingWrapper(
            ChannelHandlerContext ctx,
            String rawMessage,
            String sessionId,
            UUID interactionId,
            String clientIP,
            String clientPort,
            String destinationIP,
            String destinationPort,
            Optional<PortConfig.PortEntry> portEntryOpt,
            String errorCode,
            String errorMessage,
            boolean genericNackExpected) {
        
        String errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
        RequestContext requestContext = null;
        
        try {               
            logger.error("CONFLICTING_WRAPPERS_DETECTED [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}] {}",
                    sessionId, interactionId, haproxyDetails(ctx), errorTraceId, errorMessage);
            
            LogUtil.logDetailedError(
                400, errorMessage, interactionId.toString(), errorTraceId,
                new IllegalArgumentException("Wrapper mismatch: " + errorCode));
            
            requestContext = buildRequestContext(
                    rawMessage, interactionId.toString(), portEntryOpt,
                    clientPort, clientIP, destinationIP,
                    destinationPort, MessageSourceType.MLLP);
            requestContext.setIngestionFailed(true);
            
            String nackMessage;
            if (genericNackExpected) {
                nackMessage = String.format("NACK|%s|%s|%s|%s",
                        interactionId, errorTraceId, errorCode, errorMessage);
            } else {
                nackMessage = createHL7AckFromMsh(rawMessage, "AR", errorMessage,
                        interactionId.toString(), errorTraceId);
            }
            
            try {
                messageProcessorService.processMessage(requestContext, rawMessage, nackMessage);
                logger.info("CONFLICTING_WRAPPER_PAYLOAD_STORED [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}] - Original payload stored for troubleshooting",
                        sessionId, interactionId, haproxyDetails(ctx), errorTraceId);
            } catch (Exception storageException) {
                logger.error("FAILED_TO_STORE_CONFLICTING_WRAPPER_PAYLOAD [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]",
                        sessionId, interactionId, haproxyDetails(ctx), errorTraceId, storageException);
            }

            if (genericNackExpected) {
                sendResponseAndClose(ctx, nackMessage, sessionId, interactionId, "TCP_NACK_WRAPPER_CONFLICT");
            } else {
                sendResponseAndClose(ctx, nackMessage, sessionId, interactionId, "HL7_NACK_WRAPPER_CONFLICT");
            }
            
        } catch (Exception e) {
            logger.error("ERROR_HANDLING_WRAPPER_CONFLICT [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]: {}",
                    sessionId, interactionId, haproxyDetails(ctx), errorTraceId, e.getMessage(), e);
            
            if (requestContext != null && rawMessage != null) {
                try {
                    requestContext.setIngestionFailed(true);
                    messageProcessorService.processMessage(requestContext, rawMessage, null);
                } catch (Exception storageException) {
                    logger.error("FINAL_STORAGE_ATTEMPT_FAILED [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]",
                            sessionId, interactionId, haproxyDetails(ctx), errorTraceId, storageException);
                }
            }
            
            String genericNack = String.format("NACK|%s|%s|PROCESSING_ERROR|%s",
                    interactionId, errorTraceId, e.getMessage());
            sendResponseAndClose(ctx, genericNack, sessionId, interactionId, "NACK_PROCESSING_ERROR");
        }
    }

    // -------------------------------------------------------------------------
    // HL7 message handler
    // -------------------------------------------------------------------------

    private void handleHL7Message(
            ChannelHandlerContext ctx,
            String rawMessage,
            String sessionId,
            UUID interactionId,
            String clientIP,
            String clientPort,
            String destinationIP,
            String destinationPort,
            Optional<PortConfig.PortEntry> portEntryOpt) {
        String cleanMsg = null;
        String ackMessage = null;
        Message hl7Message = null;
        String errorTraceId = null;
        RequestContext requestContext = null;
        boolean shouldSendResponse = true;
        String responseToSend = null;
        String responseType = null;
        
        try {
            try {
                cleanMsg = unwrapMllp(rawMessage);
                logger.info("HL7_MESSAGE_UNWRAPPED [sessionId={}] [interactionId={}] [haproxyDetails={}] size={} bytes",
                        sessionId, interactionId, haproxyDetails(ctx), cleanMsg.length());
                HapiContext context = new DefaultHapiContext();
                context.setValidationContext(new NoValidation());
                GenericParser parser = context.getGenericParser();
                hl7Message = parser.parse(cleanMsg);
                Message ack = hl7Message.generateACK();
                ackMessage = addNteWithInteractionId(ack, interactionId.toString(), appConfig.getVersion());
                ackMessage = new PipeParser().encode(ack);
                logger.info("HL7_ACK_GENERATED [sessionId={}] [interactionId={}] [haproxyDetails={}]",
                        sessionId, interactionId, haproxyDetails(ctx));
            } catch (HL7Exception e) {
                logger.error("HL7_PARSE_ERROR [sessionId={}] [interactionId={}] [haproxyDetails={}]: Parsing failed due to error {} .. Continue generating manual ACK",
                        sessionId, interactionId, haproxyDetails(ctx), e.getMessage(), e);
            }

            requestContext = buildRequestContext(
                    cleanMsg, interactionId.toString(), portEntryOpt,
                    clientPort, clientIP, destinationIP,
                    destinationPort, MessageSourceType.MLLP);

            if (detectMllpZNT(portEntryOpt)) {
                boolean zntPresent = true;
                if (hl7Message != null) {
                    zntPresent = extractZntSegment(hl7Message, requestContext, interactionId.toString());
                    if (!zntPresent) {
                        logger.warn("ZNT_EXTRACTION_FAILED_USING_TERSER [sessionId={}] [interactionId={}] [haproxyDetails={}] - proceed extracting manually",
                                sessionId, interactionId, haproxyDetails(ctx));
                        zntPresent = extractZntSegmentManually(cleanMsg, requestContext, interactionId.toString());
                    }
                } else {
                    zntPresent = extractZntSegmentManually(cleanMsg, requestContext, interactionId.toString());
                    ackMessage = createHL7AckFromMsh(cleanMsg, "AA", null,
                            interactionId.toString(), null);
                }

                if (!zntPresent) {
                    requestContext.setIngestionFailed(true);
                    errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
                    
                    logger.warn("MISSING_ZNT_NACK [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}] - sending NACK due to missing ZNT segment",
                            sessionId, interactionId, haproxyDetails(ctx), errorTraceId);
                    
                    LogUtil.logDetailedError(400, "Missing ZNT segment", interactionId.toString(), errorTraceId,
                            new IllegalArgumentException("Required ZNT segment not found in HL7 message"));
                    
                    String nack = createHL7AckFromMsh(cleanMsg, "AR", "Missing ZNT segment",
                            interactionId.toString(), errorTraceId);
                    requestContext.setIngestionFailed(true);
                    messageProcessorService.processMessage(requestContext, cleanMsg, nack);
                    responseToSend = wrapMllp(nack);
                    responseType = "HL7_NACK_MISSING_ZNT";
                    return;
                }
            }
            messageProcessorService.processMessage(requestContext, cleanMsg, ackMessage);
            responseToSend = wrapMllp(ackMessage);
            responseType = "HL7_ACK";

        } catch (Exception e) {
            errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
            
            logger.error("PROCESSING_ERROR [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}] Sending Reject NACK(AR): {}",
                    sessionId, interactionId, haproxyDetails(ctx), errorTraceId, e.getMessage(), e);
            
            LogUtil.logDetailedError(500, "Internal processing error", interactionId.toString(), errorTraceId, e);
            
            try {
                String errorAck = createHL7AckFromMsh(
                        cleanMsg != null ? cleanMsg : unwrapMllp(rawMessage),
                        "AR", e.getMessage(), interactionId.toString(), errorTraceId);
                
                if (requestContext != null) {
                    requestContext.setIngestionFailed(true);
                    messageProcessorService.processMessage(requestContext, cleanMsg, errorAck);
                }
                
                responseToSend = wrapMllp(errorAck);
                responseType = "HL7_NACK";
            } catch (Exception nackException) {
                logger.error("FAILED_TO_PREPARE_NACK [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]: {}",
                        sessionId, interactionId, haproxyDetails(ctx), errorTraceId, nackException.getMessage(), nackException);
                shouldSendResponse = false;
            }
        } finally {
            if (shouldSendResponse && responseToSend != null && responseType != null) {
                sendResponseAndClose(ctx, responseToSend, sessionId, interactionId, responseType);
            } else if (ctx.channel().isActive()) {
                if (errorTraceId == null) {
                    errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
                }
                logger.error("NO_RESPONSE_PREPARED [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}] - sending generic NACK",
                        sessionId, interactionId, haproxyDetails(ctx), errorTraceId);
                
                String genericNack = "MSH|^~\\&|SERVER|LOCAL|CLIENT|REMOTE|" + Instant.now() + "||ACK|" +
                        UUID.randomUUID().toString().substring(0, 20) + "|P|2.5\r" +
                        "MSA|AR|UNKNOWN|Unexpected error occurred\r" +
                        "ERR|||207^Application internal error^HL70357||E|||Unexpected error occurred\r" +
                        "NTE|1||InteractionID: " + interactionId + " | TechBDIngestionApiVersion: " +
                        appConfig.getVersion() + " | ErrorTraceID: " + errorTraceId + "\r";
                
                sendResponseAndClose(ctx, wrapMllp(genericNack), sessionId, interactionId,
                        "HL7_NACK_UNEXPECTED_ERROR");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Generic (TCP) message handler
    // -------------------------------------------------------------------------

    private void handleGenericMessage(ChannelHandlerContext ctx, String rawMessage,
            String sessionId, UUID interactionId,
            String clientIP, String clientPort, String destinationIP, String destinationPort,
            Optional<PortConfig.PortEntry> portEntryOpt) {
        String errorTraceId = null;
        RequestContext requestContext = null;
        boolean shouldSendResponse = true;
        String responseToSend = null;
        String responseType = null;

        try {
            String cleanMsg = rawMessage.trim();
            requestContext = buildRequestContext(
                    cleanMsg, interactionId.toString(), portEntryOpt,
                    clientPort, clientIP, destinationIP,
                    destinationPort, MessageSourceType.TCP);

            Map<String, String> additionalParams = requestContext.getAdditionalParameters();
            if (additionalParams == null) {
                additionalParams = new HashMap<>();
                requestContext.setAdditionalParameters(additionalParams);
            }

            String ackMessage = generateSimpleAck(interactionId.toString());
            messageProcessorService.processMessage(requestContext, cleanMsg, ackMessage);

            responseToSend = ackMessage + "\n";
            responseType = "SIMPLE_ACK";

        } catch (Exception e) {
            errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
            
            logger.error("GENERIC_PROCESSING_ERROR [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]: {}",
                    sessionId, interactionId, haproxyDetails(ctx), errorTraceId, e.getMessage(), e);
            
            LogUtil.logDetailedError(500, "Generic message processing error",
                    interactionId.toString(), errorTraceId, e);
            
            try {
                if (requestContext != null) {
                    requestContext.setIngestionFailed(true);
                }
                
                String errorResponse = generateSimpleNack(interactionId.toString(), e.getMessage(), errorTraceId) + "\n";
                
                if (requestContext != null) {
                    messageProcessorService.processMessage(requestContext, rawMessage.trim(), errorResponse);
                }
                
                responseToSend = errorResponse;
                responseType = "SIMPLE_NACK";
            } catch (Exception nackException) {
                logger.error("FAILED_TO_PREPARE_NACK [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}]: {}",
                        sessionId, interactionId, haproxyDetails(ctx), errorTraceId, nackException.getMessage(), nackException);
                shouldSendResponse = false;
            }
        } finally {
            if (shouldSendResponse && responseToSend != null && responseType != null) {
                sendResponseAndClose(ctx, responseToSend, sessionId, interactionId, responseType);
            } else if (ctx.channel().isActive()) {
                if (errorTraceId == null) {
                    errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
                }
                logger.error("NO_RESPONSE_PREPARED [sessionId={}] [interactionId={}] [haproxyDetails={}] [errorTraceId={}] - sending generic NACK",
                        sessionId, interactionId, haproxyDetails(ctx), errorTraceId);
                
                String genericNack = String.format("NACK|InteractionId^%s|ErrorTraceId^%s|ERROR|%s|%s\n",
                        interactionId, errorTraceId, "Unexpected error occurred", Instant.now().toString());
                
                sendResponseAndClose(ctx, genericNack, sessionId, interactionId,
                        "SIMPLE_NACK_UNEXPECTED_ERROR");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Send response and (conditionally) close
    // -------------------------------------------------------------------------

    /**
     * Send response and conditionally close the channel.
     *
     * <p>When {@code keepAliveTimeout} is set for this channel the connection is kept
     * open after flushing the response so the client can send subsequent messages.
     * Per-message tracking attributes are reset so the next message on this channel
     * gets a fresh interactionId and counters.  The sessionId and session-level
     * counters are intentionally preserved across messages.
     *
     * <p>When {@code keepAliveTimeout} is not set the channel is closed ~50 ms after
     * the response is flushed (original behaviour).
     */
    private void sendResponseAndClose(ChannelHandlerContext ctx, String response,
            String sessionId, UUID interactionId, String responseType) {
        if (!ctx.channel().isActive()) {
            logger.warn("CANNOT_SEND_RESPONSE [sessionId={}] [interactionId={}] [haproxyDetails={}] type={} - CHANNEL_ALREADY_INACTIVE",
                    sessionId, interactionId, haproxyDetails(ctx), responseType);
            return;
        }

        logger.info("SENDING_RESPONSE [sessionId={}] [interactionId={}] [haproxyDetails={}] type={} size={} bytes",
                sessionId, interactionId, haproxyDetails(ctx), responseType, response.length());

        boolean keepAlive = ctx.channel().attr(KEEP_ALIVE_TIMEOUT_KEY).get() != null;
        final String capturedHaproxyDetails = haproxyDetails(ctx);

        ByteBuf responseBuf = ctx.alloc().buffer();
        responseBuf.writeBytes(response.getBytes(StandardCharsets.UTF_8));

        if (keepAlive) {
            ctx.writeAndFlush(responseBuf).addListener(future -> {
                if (future.isSuccess()) {
                    logger.info("RESPONSE_SENT_KEEP_ALIVE [sessionId={}] [interactionId={}] [haproxyDetails={}] type={} - connection kept open for next message",
                            sessionId, interactionId, capturedHaproxyDetails, responseType);
                } else {
                    logger.error("RESPONSE_SEND_FAILED_KEEP_ALIVE [sessionId={}] [interactionId={}] [haproxyDetails={}] type={}: {}",
                            sessionId, interactionId, capturedHaproxyDetails, responseType,
                            future.cause() != null ? future.cause().getMessage() : "unknown");
                    clearChannelAttributes(ctx);
                    ctx.close();
                    return;
                }
                // Reset per-message state; session-level attributes are preserved
                ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).set(null);
                ctx.channel().attr(MESSAGE_START_TIME_KEY).set(System.currentTimeMillis());
                ctx.channel().attr(FRAGMENT_COUNT_KEY).set(new AtomicInteger(0));
                ctx.channel().attr(TOTAL_BYTES_KEY).set(new AtomicLong(0));
                ctx.channel().attr(MESSAGE_SIZE_EXCEEDED_KEY).set(false);
                ctx.channel().attr(ERROR_NACK_SENT_KEY).set(false);
                ctx.channel().attr(NO_DELIMITER_DETECTED_KEY).set(false);
                ctx.channel().attr(RAW_ACCUMULATOR_KEY).set(new StringBuilder());
            });
        } else {
            ctx.writeAndFlush(responseBuf).addListener(future -> {
                if (future.isSuccess()) {
                    logger.info("RESPONSE_SENT_SUCCESS [sessionId={}] [interactionId={}] [haproxyDetails={}] type={}, scheduling connection close",
                            sessionId, interactionId, capturedHaproxyDetails, responseType);
                    ctx.executor().schedule(() -> ctx.close(), 50, TimeUnit.MILLISECONDS);
                } else {
                    logger.error("RESPONSE_SEND_FAILED [sessionId={}] [interactionId={}] [haproxyDetails={}] type={}: {}",
                            sessionId, interactionId, capturedHaproxyDetails, responseType,
                            future.cause() != null ? future.cause().getMessage() : "unknown");
                    ctx.close();
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // MLLP helpers
    // -------------------------------------------------------------------------

    private boolean detectMllpWrapper(String message) {
        if (message == null || message.length() < 3) return false;
        return message.charAt(0) == MLLP_START
                && message.charAt(message.length() - 2) == MLLP_END_1
                && message.charAt(message.length() - 1) == MLLP_END_2;
    }

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

    private String wrapMllp(String message) {
        return String.valueOf((char) MLLP_START) + message + (char) MLLP_END_1 + (char) MLLP_END_2;
    }

    private boolean detectTcpDelimiterWrapper(String message) {
        if (message == null || message.length() < 3) return false;
        return message.charAt(0) == tcpStartDelimiter
                && message.charAt(message.length() - 2) == tcpEndDelimiter1
                && message.charAt(message.length() - 1) == tcpEndDelimiter2;
    }

    // -------------------------------------------------------------------------
    // HL7 helpers
    // -------------------------------------------------------------------------

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

    private Map<String, String> parseMshSegment(String hl7Message) {
        Map<String, String> mshFields = new HashMap<>();
        try {
            String[] lines = hl7Message.split("\r|\n");
            String mshLine = null;
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

    private String createHL7AckFromMsh(String originalMessage, String ackCode, String errorText,
            String interactionId, String errorTraceId) {

        Map<String, String> msh = parseMshSegment(originalMessage);

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
                ? "" : errorText.replace("|", " ").replace("\r", " ").replace("\n", " ");

        if (msh.isEmpty()) {
            logger.warn("MSH_MISSING [interactionId={}] - generating minimal NACK", interactionId);
            String genericError = "Message cannot be processed";
            StringBuilder nack = new StringBuilder();
            nack.append("MSH|^~\\&|SERVER|LOCAL|CLIENT|REMOTE|")
                    .append(Instant.now()).append("||ACK|")
                    .append(UUID.randomUUID().toString().substring(0, 20)).append("|P|2.5\r");
            nack.append("MSA|AR|UNKNOWN|").append(genericError).append("\r");
            nack.append("ERR|||207^Application internal error^HL70357||E|||")
                    .append(genericError.substring(0, Math.min(80, genericError.length()))).append("\r");
            nack.append("NTE|1||InteractionID: ").append(interactionId)
                    .append(" | TechBDIngestionApiVersion: ").append(appConfig.getVersion());
            if (errorTraceId != null) {
                nack.append(" | ErrorTraceID: ").append(errorTraceId);
            }
            nack.append("\r");
            return nack.toString();
        }

        StringBuilder ack = new StringBuilder();
        ack.append("MSH").append(fieldSep)
                .append(encoding).append(fieldSep)
                .append(receivingApp.isEmpty() ? "SERVER" : receivingApp).append(fieldSep)
                .append(receivingFacility.isEmpty() ? "LOCAL" : receivingFacility).append(fieldSep)
                .append(sendingApp.isEmpty() ? "CLIENT" : sendingApp).append(fieldSep)
                .append(sendingFacility.isEmpty() ? "REMOTE" : sendingFacility).append(fieldSep)
                .append(Instant.now()).append(fieldSep).append(fieldSep)
                .append("ACK").append(fieldSep)
                .append(UUID.randomUUID().toString().substring(0, 20)).append(fieldSep)
                .append("P").append(fieldSep).append(version).append("\r");
        ack.append("MSA").append(fieldSep).append(finalAckCode).append(fieldSep)
                .append(messageControlId);
        if (!err.isEmpty()) {
            ack.append(fieldSep).append(err);
        }
        ack.append("\r");
        if (!"AA".equals(finalAckCode)) {
            ack.append("ERR").append(fieldSep).append(fieldSep)
                    .append("207^Application error^HL70357").append(fieldSep).append(fieldSep)
                    .append("E").append(fieldSep).append(fieldSep).append(fieldSep)
                    .append(err.substring(0, Math.min(80, err.length()))).append("\r");
        }
        ack.append("NTE").append(fieldSep).append("1").append(fieldSep).append(fieldSep)
                .append("InteractionID: ").append(interactionId)
                .append(" | TechBDIngestionApiVersion: ").append(appConfig.getVersion());
        if (errorTraceId != null && !"AA".equals(finalAckCode)) {
            ack.append(" | ErrorTraceID: ").append(errorTraceId);
        }
        ack.append("\r");
        return ack.toString();
    }

    private boolean detectMllp(Optional<PortConfig.PortEntry> portEntryOpt) {
        return portEntryOpt.isPresent()
                && Optional.ofNullable(portEntryOpt.get().responseType)
                        .map(rt -> rt.equalsIgnoreCase("outbound") || rt.equalsIgnoreCase("mllp"))
                        .orElse(false);
    }

    private boolean detectMllpZNT(Optional<PortConfig.PortEntry> portEntryOpt) {
        return portEntryOpt.isPresent()
                && Optional.ofNullable(portEntryOpt.get().responseType)
                        .map(rt -> rt.equalsIgnoreCase("outbound"))
                        .orElse(false);
    }

    // -------------------------------------------------------------------------
    // ZNT extraction
    // -------------------------------------------------------------------------

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
                String messageCode = terser.get("/.ZNT-2-1");
                String deliveryType = terser.get("/.ZNT-4-1");
                String znt8_1 = terser.get("/.ZNT-8-1");
                String facilityCode = null;
                String qe = null;
                if (znt8_1 != null && znt8_1.contains(":")) {
                    String[] parts = znt8_1.split(":");
                    qe = parts[0];
                    facilityCode = parts.length > 1 ? parts[1] : null;
                } else if (znt8_1 != null) {
                    facilityCode = znt8_1;
                }
                additionalDetails.put(Constants.MESSAGE_CODE, messageCode);
                additionalDetails.put(Constants.DELIVERY_TYPE, deliveryType);
                additionalDetails.put(Constants.FACILITY, facilityCode);
                additionalDetails.put(Constants.QE, qe);
                logger.info("ZNT_SEGMENT_EXTRACTED [interactionId={}] messageCode={}, deliveryType={}, facility={}, qe={}",
                        interactionId, messageCode, deliveryType, facilityCode, qe);
                return true;
            } else {
                logger.warn("ZNT_SEGMENT_NOT_FOUND [interactionId={}]", interactionId);
                return false;
            }
        } catch (HL7Exception e) {
            logger.error("ZNT_EXTRACTION_ERROR -- This could be as the ZNT segment is not available [interactionId={}]: Detailed error message {}",
                    interactionId, e.getMessage());
            return false;
        }
    }

    private boolean extractZntSegmentManually(String hl7Message, RequestContext requestContext, String interactionId) {
        try {
            String[] lines = hl7Message.split("\r|\n");
            String zntLine = null;
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
            String[] fields = zntLine.split("\\|", -1);
            Map<String, String> additionalDetails = requestContext.getAdditionalParameters();
            if (additionalDetails == null) {
                additionalDetails = new HashMap<>();
                requestContext.setAdditionalParameters(additionalDetails);
            }
            String messageCode = null;
            String deliveryType = null;
            String znt8_1 = null;
            if (fields.length > 2 && !fields[2].isEmpty()) {
                String[] components = fields[2].split("\\^", -1);
                messageCode = components.length > 0 ? components[0] : null;
            }
            if (fields.length > 4 && !fields[4].isEmpty()) {
                String[] components = fields[4].split("\\^", -1);
                deliveryType = components.length > 0 ? components[0] : null;
            }
            if (fields.length > 8 && !fields[8].isEmpty()) {
                String[] components = fields[8].split("\\^", -1);
                znt8_1 = components.length > 0 ? components[0] : null;
            }
            String facilityCode = null;
            String qe = null;
            if (znt8_1 != null && znt8_1.contains(":")) {
                String[] parts = znt8_1.split(":");
                qe = parts[0];
                facilityCode = parts.length > 1 ? parts[1] : null;
            } else if (znt8_1 != null) {
                facilityCode = znt8_1;
            }
            additionalDetails.put(Constants.MESSAGE_CODE, messageCode);
            additionalDetails.put(Constants.DELIVERY_TYPE, deliveryType);
            additionalDetails.put(Constants.FACILITY, facilityCode);
            additionalDetails.put(Constants.QE, qe);
            logger.info("ZNT_SEGMENT_EXTRACTED_MANUALLY [interactionId={}] messageCode={}, deliveryType={}, facility={}, qe={}",
                    interactionId, messageCode, deliveryType, facilityCode, qe);
            return true;
        } catch (Exception e) {
            logger.error("ZNT_MANUAL_EXTRACTION_ERROR [interactionId={}]: {}", interactionId, e.getMessage());
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Simple ACK/NACK generators
    // -------------------------------------------------------------------------

    private String generateSimpleAck(String interactionId) {
        return String.format("ACK|%s|%s|%s",
                interactionId, appConfig.getVersion(), Instant.now().toString());
    }

    private String generateSimpleNack(String interactionId, String errorMessage, String errorTraceId) {
        String sanitizedError = errorMessage.replace("|", " ").replace("\n", " ");
        return String.format("NACK|InteractionId^%s|ErrorTraceId^%s|ERROR|%s|%s",
                interactionId, errorTraceId, sanitizedError, Instant.now().toString());
    }

    // -------------------------------------------------------------------------
    // RequestContext builder
    // -------------------------------------------------------------------------

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

        String fileBaseName = "tcp-message";
        String originalFileName = fileBaseName;
        String userAgent = "";

        return new RequestContext(
                headers, "",
                appConfig.getAws().getSqs().getFifoQueueUrl(),
                interactionId, uploadTime, timestamp, originalFileName,
                message.length(),
                getDataKey(interactionId, headers, originalFileName, timestamp),
                getMetaDataKey(interactionId, headers, originalFileName, timestamp),
                getFullS3DataPath(interactionId, headers, originalFileName, timestamp),
                userAgent, "",
                portEntryOpt.map(pe -> pe.route).orElse(""),
                "TCP", destinationIp, sourceIp, sourceIp, destinationIp, destinationPort,
                getAcknowledgementKey(interactionId, headers, originalFileName, timestamp),
                getFullS3AcknowledgementPath(interactionId, headers, originalFileName, timestamp),
                getFullS3MetadataPath(interactionId, headers, originalFileName, timestamp),
                messageSourceType,
                getDataBucketName(), getMetadataBucketName(), appConfig.getVersion());
    }

    // -------------------------------------------------------------------------
    // MessageSourceProvider
    // -------------------------------------------------------------------------

    @Override
    public MessageSourceType getMessageSource() { return MessageSourceType.TCP; }

    @Override
    public String getDataBucketName() {
        return appConfig.getAws().getS3().getDefaultConfig().getBucket();
    }

    @Override
    public String getMetadataBucketName() {
        return appConfig.getAws().getS3().getDefaultConfig().getMetadataBucket();
    }

    @Override
    public String getTenantId(Map<String, String> headers) { return null; }

    @Override
    public String getSourceIp(Map<String, String> headers) { return headers.get("SourceIp"); }

    @Override
    public String getDestinationIp(Map<String, String> headers) { return headers.get("DestinationIp"); }

    @Override
    public String getDestinationPort(Map<String, String> headers) { return headers.get("DestinationPort"); }
}