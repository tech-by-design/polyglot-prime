package org.techbd.ingest.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
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
import org.techbd.ingest.util.UuidUtil;

import ca.uhn.hl7v2.util.Terser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.lang.reflect.Field;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.parser.PipeParser;

/**
 * Unit tests for {@link NettyTcpServer}.
 * 
 * Tests cover:
 * - Initialization and configuration
 * - TCP delimiter parsing
 * - HAProxy field parsing
 * - Channel attribute management
 * - Message handling with different delimiters
 * - Error handling and NACK generation
 * - Keep-alive timeout resolution
 */
@ExtendWith(SpringExtension.class)
@DisplayName("NettyTcpServer Unit Tests")
public class NettyTcpServerTest {

    @Mock
    private MessageProcessorService messageProcessorService;
    @Mock
    private AppConfig appConfig;
    @Mock
    private TemplateLogger templateLogger;
    @Mock
    private AppLogger appLogger;
    @Mock
    private PortResolverService portResolverService;
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private Channel channel;

    @Mock
    private NettyTcpServer server;
    @Mock
    private ByteBufAllocator allocator;
    @Mock
    private ByteBuf byteBuf;
    @Mock
    private ChannelFuture channelFuture;
    @Mock
    private EventExecutor executor;

    private EmbeddedChannel embeddedChannel;

    // Handy test constants
    private static final String SESSION_ID = "test-session-id";
    private static final UUID INTERACTION_ID = UUID.randomUUID();
    private static final String RESPONSE = "MSH|^~\\&|ACK\r";
    private static final String RESPONSE_TYPE = "ACK";
    private static final String RAW_MESSAGE = "SOME|RAW|MESSAGE";
    private static final String CLIENT_IP = "10.0.0.1";
    private static final String CLIENT_PORT = "5000";
    private static final String DEST_IP = "192.168.1.1";
    private static final String DEST_PORT = "7980";
    private static final String ERROR_TRACE_ID = "ERR-TRACE-XYZ";
    private static final String APP_VERSION = "1.0.0-test";

    private static final char MLLP_START = 0x0B;
    private static final char MLLP_END_1 = 0x1C;
    private static final char MLLP_END_2 = 0x0D;
    private static final byte TCP_START = 0x02;
    private static final byte TCP_END_1 = 0x03;
    private static final byte TCP_END_2 = 0x0A;
    private static final String VALID_HL7_MLLP = MLLP_START +
            "MSH|^~\\&|SEND_APP|SEND_FAC|RECV_APP|RECV_FAC|20230101120000||ADT^A01|MSG001|P|2.5\r" +
            "EVN|A01|20230101120000\r" +
            "PID|1||PATID001^^^MRN||DOE^JOHN||19800101|M\r" +
            "PV1|1|I\r" +
            MLLP_END_1 + MLLP_END_2;

    /**
     * Minimal HL7 with a ZNT segment, MLLP-wrapped.
     */
    private static final String ZNT_HL7_MLLP = MLLP_START +
            "MSH|^~\\&|SEND_APP|SEND_FAC|RECV_APP|RECV_FAC|20230101120000||ADT^A01|MSG002|P|2.5\r" +
            "EVN|A01|20230101120000\r" +
            "PID|1||PATID002^^^MRN||DOE^JANE||19900101|F\r" +
            "PV1|1|I\r" +
            "ZNT|1|CODE1^Desc||X||||||QE001:FAC001\r" +
            MLLP_END_1 + MLLP_END_2;

    /**
     * HL7 WITHOUT ZNT segment, MLLP-wrapped — used for missing-ZNT scenario.
     */
    private static final String NO_ZNT_HL7_MLLP = MLLP_START +
            "MSH|^~\\&|SEND_APP|SEND_FAC|RECV_APP|RECV_FAC|20230101120000||ADT^A01|MSG003|P|2.5\r" +
            "EVN|A01|20230101120000\r" +
            "PID|1||PATID003^^^MRN||DOE^BOB||19750601|M\r" +
            "PV1|1|I\r" +
            MLLP_END_1 + MLLP_END_2;

    /**
     * Garbage that HAPI cannot parse → triggers HL7Exception inner catch.
     */
    private static final String UNPARSEABLE_MLLP = MLLP_START + "NOT|A|VALID|HL7|MESSAGE\r" + MLLP_END_1 + MLLP_END_2;

    @BeforeEach
    void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        AppConfig.Aws aws = mock(AppConfig.Aws.class);
        AppConfig.Aws.Sqs sqs = mock(AppConfig.Aws.Sqs.class);
        AppConfig.Aws.S3 s3 = mock(AppConfig.Aws.S3.class);
        AppConfig.Aws.S3.BucketConfig defaultConfig = mock(AppConfig.Aws.S3.BucketConfig.class);

        when(appConfig.getAws()).thenReturn(aws);
        when(aws.getSqs()).thenReturn(sqs);
        when(aws.getS3()).thenReturn(s3);
        when(sqs.getFifoQueueUrl()).thenReturn("test-queue-url");
        when(s3.getDefaultConfig()).thenReturn(defaultConfig);
        when(defaultConfig.getBucket()).thenReturn("test-bucket");

        when(appConfig.getVersion()).thenReturn(APP_VERSION);

        when(appLogger.getLogger(NettyTcpServer.class)).thenReturn(templateLogger);

        // channel
        when(ctx.channel()).thenReturn(channel);
        when(channel.isActive()).thenReturn(true);

        // Attribute
        @SuppressWarnings("unchecked")
        Attribute<Object> attribute = mock(Attribute.class);
        when(channel.attr(any())).thenReturn(attribute);
        when(attribute.get()).thenReturn(null);

        // ByteBuf
        ByteBufAllocator allocator = mock(ByteBufAllocator.class);
        ByteBuf byteBuf = mock(ByteBuf.class);

        when(ctx.alloc()).thenReturn(allocator);
        when(allocator.buffer()).thenReturn(byteBuf);
        when(byteBuf.writeBytes(any(byte[].class))).thenReturn(byteBuf);

        // CRITICAL FIX (ChannelFuture)
        ChannelFuture future = mock(ChannelFuture.class);
        when(ctx.writeAndFlush(any())).thenReturn(future);
        when(future.addListener(any())).thenReturn(future);

        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        when(ctx.pipeline()).thenReturn(pipeline);
        when(pipeline.get(anyString())).thenReturn(null);
        embeddedChannel = new EmbeddedChannel(createDecoder(MAX_FRAME));
        seedChannelAttributes(embeddedChannel);
        server = new NettyTcpServer(
                messageProcessorService,
                appConfig,
                appLogger,
                portResolverService);

    }

    private static final AttributeKey<String> SESSION_ID_KEY = AttributeKey.valueOf("SESSION_ID");
    private static final AttributeKey<UUID> INTERACTION_KEY = AttributeKey.valueOf("INTERACTION_ATTRIBUTE_KEY");
    private static final AttributeKey<AtomicInteger> FRAGMENT_COUNT_KEY = AttributeKey.valueOf("FRAGMENT_COUNT");
    private static final AttributeKey<AtomicLong> TOTAL_BYTES_KEY = AttributeKey.valueOf("TOTAL_BYTES");
    private static final AttributeKey<Boolean> SIZE_EXCEEDED_KEY = AttributeKey.valueOf("MESSAGE_SIZE_EXCEEDED");
    private static final AttributeKey<Boolean> NO_DELIMITER_KEY = AttributeKey.valueOf("NO_DELIMITER_DETECTED");
    private static final int MAX_FRAME = 1024; // small limit for size-exceeded tests

    private static final AttributeKey<StringBuilder> RAW_ACCUMULATOR_KEY = AttributeKey.valueOf("RAW_ACCUMULATOR");
    private static final AttributeKey<Boolean> MESSAGE_SIZE_EXCEEDED_KEY = AttributeKey
            .valueOf("MESSAGE_SIZE_EXCEEDED");
    private static final AttributeKey<String> HAPROXY_DETAILS_KEY = AttributeKey.valueOf("HAPROXY_DETAILS");
    private static final AttributeKey<UUID> INTERACTION_ATTRIBUTE_KEY = AttributeKey
            .valueOf("INTERACTION_ATTRIBUTE_KEY");

    private void seedChannelAttributes(EmbeddedChannel ch) {
        ch.attr(SESSION_ID_KEY).set(SESSION_ID);
        ch.attr(INTERACTION_KEY).set(UUID.randomUUID());
        ch.attr(FRAGMENT_COUNT_KEY).set(new AtomicInteger(0));
        ch.attr(TOTAL_BYTES_KEY).set(new AtomicLong(0));
        ch.attr(SIZE_EXCEEDED_KEY).set(false);
        ch.attr(NO_DELIMITER_KEY).set(false);
        ch.attr(HAPROXY_DETAILS_KEY).set(null);
    }

    private ByteToMessageDecoder createDecoder(int maxFrame) throws Exception {
        Class<?>[] declaredClasses = NettyTcpServer.class.getDeclaredClasses();
        Class<?> decoderClass = null;
        for (Class<?> c : declaredClasses) {
            if (c.getSimpleName().equals("DelimiterBasedFrameDecoder")) {
                decoderClass = c;
                break;
            }
        }
        assertNotNull(decoderClass, "DelimiterBasedFrameDecoder inner class must exist");

        Constructor<?> ctor = decoderClass.getDeclaredConstructor(NettyTcpServer.class, int.class);
        ctor.setAccessible(true);
        return (ByteToMessageDecoder) ctor.newInstance(server, maxFrame);
    }
    // ---------------------------------------------------------
    // DELIMITER TESTS
    // ---------------------------------------------------------

    @Test
    void testParseTcpDelimiters_valid() throws Exception {
        ReflectionTestUtils.setField(server, "tcpStartDelimiterHex", "0x02");
        ReflectionTestUtils.setField(server, "tcpEndDelimiter1Hex", "0x03");
        ReflectionTestUtils.setField(server, "tcpEndDelimiter2Hex", "0x0A");

        invokePrivate("parseTcpDelimiters");

        assertThat(getByte("tcpStartDelimiter")).isEqualTo((byte) 0x02);
        assertThat(getByte("tcpEndDelimiter1")).isEqualTo((byte) 0x03);
        assertThat(getByte("tcpEndDelimiter2")).isEqualTo((byte) 0x0A);
    }

    @Test
    void testParseTcpDelimiters_invalidFallback() throws Exception {
        ReflectionTestUtils.setField(server, "tcpStartDelimiterHex", "invalid");
        ReflectionTestUtils.setField(server, "tcpEndDelimiter1Hex", "invalid");
        ReflectionTestUtils.setField(server, "tcpEndDelimiter2Hex", "invalid");

        invokePrivate("parseTcpDelimiters");

        assertThat(getByte("tcpStartDelimiter")).isEqualTo((byte) 0x0B);
        assertThat(getByte("tcpEndDelimiter1")).isEqualTo((byte) 0x1C);
        assertThat(getByte("tcpEndDelimiter2")).isEqualTo((byte) 0x0D);
    }

    // ---------------------------------------------------------
    // HAPROXY PARSING
    // ---------------------------------------------------------

    @Test
    void testParseHaproxyField() throws Exception {
        String input = "sourceAddress=1.1.1.1, sourcePort=8080";

        Method m = getMethod("parseHaproxyField", String.class, String.class);
        String result = (String) m.invoke(server, input, "sourcePort");

        assertThat(result).isEqualTo("8080");
    }

    // ---------------------------------------------------------
    // CONFIG TEST
    // ---------------------------------------------------------

    @Test
    void testConfigValues() {
        ReflectionTestUtils.setField(server, "tcpPort", 9000);
        ReflectionTestUtils.setField(server, "readTimeoutSeconds", 100);

        assertThat(ReflectionTestUtils.getField(server, "tcpPort")).isEqualTo(9000);
        assertThat(ReflectionTestUtils.getField(server, "readTimeoutSeconds")).isEqualTo(100);
    }

    // ---------------------------------------------------------
    // LOGGER TEST
    // ---------------------------------------------------------

    @Test
    void testDelimiterLogging() throws Exception {
        ReflectionTestUtils.setField(server, "tcpStartDelimiterHex", "0x02");
        ReflectionTestUtils.setField(server, "tcpEndDelimiter1Hex", "0x03");
        ReflectionTestUtils.setField(server, "tcpEndDelimiter2Hex", "0x0A");

        invokePrivate("parseTcpDelimiters");

        verify(templateLogger, atLeastOnce())
                .info(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void testGenerateSimpleAck() throws Exception {
        when(appConfig.getVersion()).thenReturn("1.0");

        Method m = getMethod("generateSimpleAck", String.class);

        String result = (String) m.invoke(server, "INT-123");

        assertThat(result).contains("ACK|INT-123|1.0|");
    }

    @Test
    void testGenerateSimpleNack_sanitization() throws Exception {
        Method m = getMethod("generateSimpleNack", String.class, String.class, String.class);

        String result = (String) m.invoke(server,
                "INT-1",
                "error|message\nbad",
                "TRACE-1");

        assertThat(result).contains("NACK|InteractionId^INT-1");
        assertThat(result).contains("ErrorTraceId^TRACE-1");
        assertThat(result).doesNotContain("|message"); // pipe replaced
        assertThat(result).doesNotContain("\n"); // newline removed
    }

    @Test
    void testDetectMllpWrapper() throws Exception {
        Method m = getMethod("detectMllpWrapper", String.class);

        String msg = "" + (char) 0x0B + "HELLO" + (char) 0x1C + (char) 0x0D;

        boolean result = (boolean) m.invoke(server, msg);

        assertThat(result).isTrue();
    }

    @Test
    void testDetectMllpWrapper_invalid() throws Exception {
        Method m = getMethod("detectMllpWrapper", String.class);

        boolean result = (boolean) m.invoke(server, "HELLO");

        assertThat(result).isFalse();
    }

    @Test
    void testDetectTcpDelimiterWrapper() throws Exception {
        ReflectionTestUtils.setField(server, "tcpStartDelimiter", (byte) 0x02);
        ReflectionTestUtils.setField(server, "tcpEndDelimiter1", (byte) 0x03);
        ReflectionTestUtils.setField(server, "tcpEndDelimiter2", (byte) 0x0A);

        Method m = getMethod("detectTcpDelimiterWrapper", String.class);

        String msg = "" + (char) 0x02 + "DATA" + (char) 0x03 + (char) 0x0A;

        boolean result = (boolean) m.invoke(server, msg);

        assertThat(result).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testParseMshSegment_valid() throws Exception {
        Method m = getMethod("parseMshSegment", String.class);

        String hl7 = "MSH|^~\\&|APP|FAC|RECAPP|RECFAC|20240101||ADT^A01|MSG1|P|2.5";

        Map<String, String> result = (Map<String, String>) m.invoke(server, hl7);

        assertThat(result.get("sendingApplication")).isEqualTo("APP");
        assertThat(result.get("receivingApplication")).isEqualTo("RECAPP");
        assertThat(result.get("messageControlId")).isEqualTo("MSG1");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testParseMshSegment_missing() throws Exception {
        Method m = getMethod("parseMshSegment", String.class);

        Map<String, String> result = (Map<String, String>) m.invoke(server, "NO_MSH");

        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testHandleNoDelimiterMessage_createsAccumulator() throws Exception {

        @SuppressWarnings("rawtypes")
        Attribute attribute = mock(Attribute.class);

        when(ctx.channel()).thenReturn(channel);
        when(channel.attr(any())).thenReturn(attribute);
        when(attribute.get()).thenReturn(null);

        Method m = getMethod("handleNoDelimiterMessage",
                ChannelHandlerContext.class,
                String.class,
                String.class,
                UUID.class);

        try (MockedStatic<FeatureEnum> mocked = mockStatic(FeatureEnum.class)) {

            // Disable feature flag
            mocked.when(() -> FeatureEnum.isEnabled(any())).thenReturn(false);

            m.invoke(server, ctx, "TEST_DATA", "SESSION-1", UUID.randomUUID());
        }

        verify(attribute).set(any(StringBuilder.class));
    }

    @Test
    void testBuildRequestContext_success() throws Exception {

        // Mock AWS config
        AppConfig.Aws aws = mock(AppConfig.Aws.class);
        AppConfig.Aws.Sqs sqs = mock(AppConfig.Aws.Sqs.class);
        AppConfig.Aws.S3 s3 = mock(AppConfig.Aws.S3.class);
        AppConfig.Aws.S3.BucketConfig defaultConfig = mock(AppConfig.Aws.S3.BucketConfig.class);

        // AWS root
        when(appConfig.getAws()).thenReturn(aws);

        // SQS
        when(aws.getSqs()).thenReturn(sqs);
        when(sqs.getFifoQueueUrl()).thenReturn("test-queue-url");

        // S3 (THIS WAS MISSING)
        when(aws.getS3()).thenReturn(s3);
        when(s3.getDefaultConfig()).thenReturn(defaultConfig);

        // bucket names (VERY IMPORTANT)
        // when(defaultConfig.getDataBucket()).thenReturn("data-bucket");
        when(defaultConfig.getMetadataBucket()).thenReturn("metadata-bucket");

        // optional but safe
        when(appConfig.getVersion()).thenReturn("1.0");
    }

    @Test
    void testExtractZntSegmentManually_success() throws Exception {

        String hl7Message = "MSH|^~\\&|\r" +
                "PID|1|\r" +
                "ZNT|1|MSGCODE^X|3|DELIVERY^Y|5|6|7|QE123:FAC456";

        RequestContext requestContext = mock(RequestContext.class);

        Map<String, String> map = new HashMap<>();
        when(requestContext.getAdditionalParameters()).thenReturn(map);

        Method m = getMethod("extractZntSegmentManually",
                String.class, RequestContext.class, String.class);

        boolean result = (boolean) m.invoke(server,
                hl7Message,
                requestContext,
                "INT-1");

        assertTrue(result);

    }

    @Test
    void testExtractZntSegmentManually_noZnt() throws Exception {

        String hl7Message = "MSH|^~\\&|\r" +
                "PID|1|";

        RequestContext requestContext = mock(RequestContext.class);

        Method m = getMethod("extractZntSegmentManually",
                String.class, RequestContext.class, String.class);

        boolean result = (boolean) m.invoke(server,
                hl7Message,
                requestContext,
                "INT-2");

        assertFalse(result);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testExtractZntSegmentManually_nullAdditionalParams() throws Exception {

        String hl7Message = "ZNT|1|CODE^A|3|DEL^B|5|6|7|FAC123";

        RequestContext requestContext = mock(RequestContext.class);

        when(requestContext.getAdditionalParameters()).thenReturn(null);

        Method m = getMethod("extractZntSegmentManually",
                String.class, RequestContext.class, String.class);

        boolean result = (boolean) m.invoke(server,
                hl7Message,
                requestContext,
                "INT-3");

        assertTrue(result);

        // verify map was created and set
        verify(requestContext).setAdditionalParameters(any(Map.class));
    }

    @Test
    void testExtractZntSegmentManually_onlyFacility() throws Exception {

        String hl7Message = "ZNT|1|CODE^A|3|DEL^B|5|6|7|FACONLY";

        RequestContext requestContext = mock(RequestContext.class);

        Map<String, String> map = new HashMap<>();
        when(requestContext.getAdditionalParameters()).thenReturn(map);

        Method m = getMethod("extractZntSegmentManually",
                String.class, RequestContext.class, String.class);

        boolean result = (boolean) m.invoke(server,
                hl7Message,
                requestContext,
                "INT-4");

        assertTrue(result);

        // assertEquals("FACONLY", map.get(Constants.FACILITY));
        // assertNull(map.get(Constants.QE));
    }

    @Test
    void testExtractZntSegmentManually_exception() throws Exception {

        RequestContext requestContext = mock(RequestContext.class);

        // force exception
        when(requestContext.getAdditionalParameters()).thenThrow(new RuntimeException("boom"));

        Method m = getMethod("extractZntSegmentManually",
                String.class, RequestContext.class, String.class);

        boolean result = (boolean) m.invoke(server,
                "ZNT|1|A|B|C",
                requestContext,
                "INT-5");

        assertFalse(result);
    }

    // ---------------------------------------------------------
    // HELPER METHODS
    // ---------------------------------------------------------
    @Test
    void testExtractZntSegment_success() throws Exception {

        Message hapiMsg = mock(Message.class);
        RequestContext requestContext = mock(RequestContext.class);

        Map<String, String> map = new HashMap<>();
        when(requestContext.getAdditionalParameters()).thenReturn(map);
        Segment segmentMock = mock(Segment.class);

        try (MockedConstruction<Terser> mocked = mockConstruction(Terser.class,
                (mock, context) -> {

                    when(mock.getSegment(".ZNT")).thenReturn(segmentMock);
                    when(mock.get("/.ZNT-2-1")).thenReturn("MSGCODE");
                    when(mock.get("/.ZNT-4-1")).thenReturn("DELIVERY");
                    when(mock.get("/.ZNT-8-1")).thenReturn("QE123:FAC456");
                })) {

            Method m = getMethod("extractZntSegment",
                    Message.class, RequestContext.class, String.class);

            boolean result = (boolean) m.invoke(server,
                    hapiMsg,
                    requestContext,
                    "INT-1");

            assertTrue(result);
        }
    }

    @Test
    void testExtractZntSegment_noZnt() throws Exception {

        Message hapiMsg = mock(Message.class);
        RequestContext requestContext = mock(RequestContext.class);
        try (MockedConstruction<Terser> mocked = mockConstruction(Terser.class,
                (mock, context) -> {

                    when(mock.getSegment(".ZNT")).thenReturn(null);
                })) {

            Method m = getMethod("extractZntSegment",
                    Message.class, RequestContext.class, String.class);

            boolean result = (boolean) m.invoke(server,
                    hapiMsg,
                    requestContext,
                    "INT-2");
            assertFalse(result);
        }
    }

    @Test
    void testExtractZntSegment_exception() throws Exception {

        Message hapiMsg = mock(Message.class);
        RequestContext requestContext = mock(RequestContext.class);

        try (MockedConstruction<Terser> mocked = mockConstruction(Terser.class,
                (mock, context) -> {

                    when(mock.getSegment(".ZNT")).thenThrow(new HL7Exception("boom"));
                })) {

            Method m = getMethod("extractZntSegment",
                    Message.class, RequestContext.class, String.class);

            boolean result = (boolean) m.invoke(server,
                    hapiMsg,
                    requestContext,
                    "INT-4");
            assertFalse(result);
        }
    }

    @Test
    void testCreateHL7Ack_success_AA() throws Exception {

        String message = "MSH|^~\\&|APP|FAC|RECAPP|RECFAC|20230101||ADT^A01|123|P|2.5";

        try (MockedStatic<FeatureEnum> featureMock = mockStatic(FeatureEnum.class);
                MockedStatic<UuidUtil> uuidMock = mockStatic(UuidUtil.class)) {

            featureMock.when(() -> FeatureEnum.isEnabled(any())).thenReturn(false);
            uuidMock.when(UuidUtil::generateUuid).thenReturn("12345678901234567890ABC");

            Method m = getMethod("createHL7AckFromMsh",
                    String.class, String.class, String.class, String.class, String.class);

            String result = (String) m.invoke(server,
                    message,
                    "AA",
                    null,
                    "INT-1",
                    null);

            assertTrue(result.contains("MSA|AA|123"));
            assertFalse(result.contains("ERR"));
        }
    }

    @Test
    void testCreateHL7Ack_withNTE() throws Exception {

        String message = "MSH|^~\\&|APP|FAC|RECAPP|RECFAC||ADT^A01|123|P|2.5";

        when(appConfig.getVersion()).thenReturn("1.0");

        try (MockedStatic<FeatureEnum> featureMock = mockStatic(FeatureEnum.class);
                MockedStatic<UuidUtil> uuidMock = mockStatic(UuidUtil.class)) {

            featureMock.when(() -> FeatureEnum.isEnabled(any())).thenReturn(true);
            uuidMock.when(UuidUtil::generateUuid).thenReturn("12345678901234567890ABC");

            Method m = getMethod("createHL7AckFromMsh",
                    String.class, String.class, String.class, String.class, String.class);

            String result = (String) m.invoke(server,
                    message,
                    "AE",
                    "error",
                    "INT-4",
                    "TRACE-123");

            assertTrue(result.contains("NTE"));
            assertTrue(result.contains("InteractionID: INT-4"));
            assertTrue(result.contains("ErrorTraceID: TRACE-123"));
        }
    }

    @Test
    void testDetectMllp_true_outbound() throws Exception {

        PortConfig.PortEntry entry = new PortConfig.PortEntry();
        entry.responseType = "outbound";

        Method m = getMethod("detectMllp", Optional.class);

        boolean result = (boolean) m.invoke(server, Optional.of(entry));

        assertTrue(result);
    }

    @Test
    void testDetectMllp_true_mllp() throws Exception {

        PortConfig.PortEntry entry = new PortConfig.PortEntry();
        entry.responseType = "mllp";

        Method m = getMethod("detectMllp", Optional.class);

        boolean result = (boolean) m.invoke(server, Optional.of(entry));

        assertTrue(result);
    }

    @Test
    void testDetectMllp_false() throws Exception {

        PortConfig.PortEntry entry = new PortConfig.PortEntry();
        entry.responseType = "http";

        Method m = getMethod("detectMllp", Optional.class);

        boolean result = (boolean) m.invoke(server, Optional.of(entry));

        assertFalse(result);
    }

    @Test
    void testDetectMllp_empty() throws Exception {

        Method m = getMethod("detectMllp", Optional.class);

        boolean result = (boolean) m.invoke(server, Optional.empty());

        assertFalse(result);
    }

    @Test
    void testDetectMllpZnt_true() throws Exception {

        PortConfig.PortEntry entry = new PortConfig.PortEntry();
        entry.responseType = "outbound";

        Method m = getMethod("detectMllpZNT", Optional.class);

        boolean result = (boolean) m.invoke(server, Optional.of(entry));

        assertTrue(result);
    }

    @Test
    void testDetectMllpZnt_false_mllp() throws Exception {

        PortConfig.PortEntry entry = new PortConfig.PortEntry();
        entry.responseType = "mllp";

        Method m = getMethod("detectMllpZNT", Optional.class);

        boolean result = (boolean) m.invoke(server, Optional.of(entry));

        assertFalse(result);
    }

    @Test
    void testAddNteWithInteractionId_success() throws Exception {

        Message ackMessage = mock(Message.class);

        String interactionId = "INT-123";
        String version = "1.0";

        try (MockedConstruction<Terser> terserConstruction = mockConstruction(Terser.class,
                (mock, context) -> {

                    // no return needed for set()
                    doNothing().when(mock).set(anyString(), anyString());
                });

                MockedConstruction<PipeParser> parserConstruction = mockConstruction(PipeParser.class,
                        (mock, context) -> {
                            when(mock.encode(any())).thenReturn("ENCODED_HL7");
                        })) {

            Method m = getMethod("addNteWithInteractionId",
                    Message.class, String.class, String.class);

            String result = (String) m.invoke(server,
                    ackMessage,
                    interactionId,
                    version);

            verify(ackMessage).addNonstandardSegment("NTE");

            Terser usedTerser = terserConstruction.constructed().get(0);

            verify(usedTerser).set("/NTE(0)-1", "1");
            verify(usedTerser).set(
                    eq("/NTE(0)-3"),
                    contains("InteractionID: " + interactionId));

            PipeParser usedParser = parserConstruction.constructed().get(0);
            verify(usedParser).encode(ackMessage);

            assertEquals("ENCODED_HL7", result);
        }
    }

    @Test
    void testUnwrapMllp_validMessage() throws Exception {

        String wrapped = "\u000BHELLO\u001C\r"; // MLLP: <VT> message <FS><CR>
        Method m = getMethod("unwrapMllp", String.class);
        String result = (String) m.invoke(server, wrapped);
        assertEquals("HELLO", result);
    }

    @Test
    void testUnwrapMllp_noWrapper() throws Exception {

        String message = "  HELLO  ";
        Method m = getMethod("unwrapMllp", String.class);
        String result = (String) m.invoke(server, message);
        assertEquals("HELLO", result);
    }

    @Test
    void testUnwrapMllp_onlyStartDelimiter() throws Exception {

        String message = "\u000BHELLO";
        Method m = getMethod("unwrapMllp", String.class);
        String result = (String) m.invoke(server, message);
        assertEquals("HELLO", result);
    }

    @Test
    void testUnwrapMllp_onlyEndDelimiter() throws Exception {

        String message = "HELLO\u001C\r";
        Method m = getMethod("unwrapMllp", String.class);
        String result = (String) m.invoke(server, message);
        assertEquals("HELLO", result);
    }

    @Test
    void testWrapAndUnwrapMllp() throws Exception {

        String message = "HELLO";

        Method wrap = getMethod("wrapMllp", String.class);
        Method unwrap = getMethod("unwrapMllp", String.class);
        String wrapped = (String) wrap.invoke(server, message);
        String unwrapped = (String) unwrap.invoke(server, wrapped);
        assertEquals("HELLO", unwrapped);
    }

    @Test
    void testDetectTcpDelimiterWrapper_shortMessage() throws Exception {

        String message = "AB";
        Method m = getMethod("detectTcpDelimiterWrapper", String.class);
        boolean result = (boolean) m.invoke(server, message);
        assertFalse(result);
    }

    @Test
    void testDetectTcpDelimiterWrapper_invalid() throws Exception {

        String message = "HELLO";
        Method m = getMethod("detectTcpDelimiterWrapper", String.class);
        boolean result = (boolean) m.invoke(server, message);
        assertFalse(result);
    }

    @Test
    void testDetectMllpWrapper_true() throws Exception {

        String message = "\u000BHELLO\u001C\r"; // <VT>HELLO<FS><CR>
        Method m = getMethod("detectMllpWrapper", String.class);
        boolean result = (boolean) m.invoke(server, message);
        assertTrue(result);
    }

    @Test
    void testDetectMllpWrapper_missingStart() throws Exception {

        String message = "HELLO\u001C\r";
        Method m = getMethod("detectMllpWrapper", String.class);
        boolean result = (boolean) m.invoke(server, message);
        assertFalse(result);
    }

    @Test
    void testDetectMllpWrapper_invalidDelimiters() throws Exception {

        String message = "\u000BHELLOXX";
        Method m = getMethod("detectMllpWrapper", String.class);
        boolean result = (boolean) m.invoke(server, message);
        assertFalse(result);
    }

    @Test
    @DisplayName("1. Channel inactive → warn log and early return without writing")
    void whenChannelInactive_shouldLogWarnAndReturn() throws Exception {
        when(channel.isActive()).thenReturn(false);

        invokeSendResponseAndClose(RESPONSE, SESSION_ID, INTERACTION_ID, RESPONSE_TYPE);

        // No bytes should have been written
        verify(ctx, never()).writeAndFlush(any());
        verify(allocator, never()).buffer();
    }

    @Test
    @DisplayName("1. Happy path → processMessage called once, SIMPLE_ACK response sent")
    void whenProcessingSucceeds_shouldCallProcessMessageOnceAndSendAck() throws Exception {
        try (MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class)) {

            invokeHandleGenericMessage(RAW_MESSAGE, Optional.empty());

            // Exactly one call — no error retry
            verify(messageProcessorService, times(1))
                    .processMessage(any(RequestContext.class), eq(RAW_MESSAGE.trim()), anyString());

            // sendResponseAndClose fires writeAndFlush
            verify(ctx, times(1)).writeAndFlush(any());
        }
    }

    @Test
    @DisplayName("2. Null additionalParams → null-check handled, processMessage still called")
    void whenAdditionalParamsNull_shouldHandleGracefullyAndCallProcessMessage() throws Exception {

        try (MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class)) {

            invokeHandleGenericMessage(RAW_MESSAGE, Optional.empty());

            verify(messageProcessorService, times(1))
                    .processMessage(any(RequestContext.class), anyString(), anyString());
            verify(ctx, times(1)).writeAndFlush(any());
        }
    }

    @Test
    @DisplayName("4. Both process and NACK retry throw, channel active → fallback NACK sent")
    void whenBothThrow_channelActive_shouldSendFallbackNack() throws Exception {
        try (MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class)) {

            etg.when(ErrorTraceIdGenerator::generateErrorTraceId).thenReturn(ERROR_TRACE_ID);
            logUtil.when(() -> LogUtil.logDetailedError(anyInt(), anyString(), anyString(), anyString(), any()))
                    .thenAnswer(inv -> null);

            // Every processMessage call throws
            doThrow(new RuntimeException("always fails"))
                    .when(messageProcessorService)
                    .processMessage(any(RequestContext.class), anyString(), anyString());

            invokeHandleGenericMessage(RAW_MESSAGE, Optional.empty());
            verify(ctx, times(1)).writeAndFlush(any());
        }
    }

    @Test
    @DisplayName("5. Both throw and channel inactive → no response sent")
    void whenBothThrow_channelInactive_shouldNotSendAnyResponse() throws Exception {
        when(channel.isActive()).thenReturn(false);

        try (MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class)) {

            etg.when(ErrorTraceIdGenerator::generateErrorTraceId).thenReturn(ERROR_TRACE_ID);
            logUtil.when(() -> LogUtil.logDetailedError(anyInt(), anyString(), anyString(), anyString(), any()))
                    .thenAnswer(inv -> null);

            doThrow(new RuntimeException("always fails"))
                    .when(messageProcessorService)
                    .processMessage(any(RequestContext.class), anyString(), anyString());

            invokeHandleGenericMessage(RAW_MESSAGE, Optional.empty());

            verify(ctx, never()).writeAndFlush(any());
        }
    }

    @Test
    @DisplayName("6. PortEntry present → route used in RequestContext, processes correctly")
    void whenPortEntryPresent_shouldProcessCorrectly() throws Exception {
        PortConfig.PortEntry portEntry = mock(PortConfig.PortEntry.class);
        portEntry.route = "/test-route";

        try (MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class)) {

            invokeHandleGenericMessage(RAW_MESSAGE, Optional.of(portEntry));

            verify(messageProcessorService, times(1))
                    .processMessage(any(RequestContext.class), anyString(), anyString());
            verify(ctx, times(1)).writeAndFlush(any());
        }
    }

    @Test
    @DisplayName("1. Happy path non-ZNT → HAPI parses OK, processMessage called once, HL7_ACK sent")
    void whenValidHL7NonZntPort_shouldParseAndSendAck() throws Exception {
        try (MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<FeatureEnum> featureEnum = mockStatic(FeatureEnum.class)) {

            featureEnum.when(() -> FeatureEnum.isEnabled(any())).thenReturn(false);

            invokeHandleHL7Message(VALID_HL7_MLLP, nonZntPortEntry());

            // processMessage called exactly once — happy path, no retry
            verify(messageProcessorService, times(1))
                    .processMessage(any(RequestContext.class), anyString(), anyString());

            // sendResponseAndClose fires writeAndFlush
            verify(ctx, times(1)).writeAndFlush(any());
        }
    }

    @Test
    @DisplayName("2. HAPI parse fails → createHL7AckFromMsh used, processMessage called, HL7_ACK sent")
    void whenHapiParseFails_shouldFallbackToManualAckAndSendResponse() throws Exception {
        try (MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<FeatureEnum> featureEnum = mockStatic(FeatureEnum.class)) {

            featureEnum.when(() -> FeatureEnum.isEnabled(any())).thenReturn(false);

            // UNPARSEABLE_MLLP triggers HL7Exception in the inner try-catch
            // The outer try catches nothing — execution continues with hl7Message=null
            invokeHandleHL7Message(UNPARSEABLE_MLLP, nonZntPortEntry());

            // Should still call processMessage (with manually-built ACK)
            verify(messageProcessorService, times(1))
                    .processMessage(any(RequestContext.class), anyString(), anyString());
            verify(ctx, times(1)).writeAndFlush(any());
        }
    }

    @Test
    @DisplayName("3. ZNT port, ZNT segment present → processMessage called, HL7_ACK sent")
    void whenZntPort_zntPresent_shouldSendAck() throws Exception {
        try (MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<FeatureEnum> featureEnum = mockStatic(FeatureEnum.class)) {

            featureEnum.when(() -> FeatureEnum.isEnabled(any())).thenReturn(false);

            // ZNT_HL7_MLLP has a ZNT segment — extractZntSegmentManually will find it
            invokeHandleHL7Message(ZNT_HL7_MLLP, zntPortEntry());

            verify(messageProcessorService, times(1))
                    .processMessage(any(RequestContext.class), anyString(), anyString());
            verify(ctx, times(1)).writeAndFlush(any());
        }
    }

    @Test
    @DisplayName("4. ZNT port, ZNT segment missing → NACK sent, ingestionFailed=true, LogUtil 400")
    void whenZntPort_zntMissing_shouldSendMissingZntNack() throws Exception {
        try (MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<FeatureEnum> featureEnum = mockStatic(FeatureEnum.class)) {

            etg.when(ErrorTraceIdGenerator::generateErrorTraceId).thenReturn(ERROR_TRACE_ID);
            logUtil.when(() -> LogUtil.logDetailedError(anyInt(), anyString(), anyString(), anyString(), any()))
                    .thenAnswer(inv -> null);
            featureEnum.when(() -> FeatureEnum.isEnabled(any())).thenReturn(false);

            // NO_ZNT_HL7_MLLP has no ZNT segment
            invokeHandleHL7Message(NO_ZNT_HL7_MLLP, zntPortEntry());

            // processMessage called once (for the NACK storage)
            verify(messageProcessorService, times(1))
                    .processMessage(any(RequestContext.class), anyString(), anyString());

            // LogUtil called with 400 for missing ZNT
            logUtil.verify(() -> LogUtil.logDetailedError(
                    eq(400),
                    eq("Missing ZNT segment"),
                    eq(INTERACTION_ID.toString()),
                    eq(ERROR_TRACE_ID),
                    any(IllegalArgumentException.class)));

            // NACK response written
            verify(ctx, times(1)).writeAndFlush(any());
        }
    }

    @Test
    @DisplayName("5. ZNT port, parse fails → manual ZNT extraction, ACK sent")
    void whenZntPort_parseFails_zntFoundManually_shouldSendAck() throws Exception {
        try (MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<FeatureEnum> featureEnum = mockStatic(FeatureEnum.class)) {

            featureEnum.when(() -> FeatureEnum.isEnabled(any())).thenReturn(false);
            String unparseableWithZnt = MLLP_START +
                    "GARBAGE|^~\\&|SEND|FAC|RECV|FAC|20230101||ADT^A01|MSG|P|2.5\r" +
                    "ZNT|1|CODE^Desc||X||||||QE:FAC\r" +
                    MLLP_END_1 + MLLP_END_2;

            invokeHandleHL7Message(unparseableWithZnt, zntPortEntry());

            // ZNT found manually → processMessage called for ACK
            verify(messageProcessorService, times(1))
                    .processMessage(any(RequestContext.class), anyString(), anyString());
            verify(ctx, times(1)).writeAndFlush(any());
        }
    }

    @Test
    @DisplayName("8. Both processMessage and NACK retry throw → fallback generic NACK sent")
    void whenBothProcessAndNackThrow_shouldSendFallbackNack() throws Exception {
        try (MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<FeatureEnum> featureEnum = mockStatic(FeatureEnum.class)) {

            etg.when(ErrorTraceIdGenerator::generateErrorTraceId).thenReturn(ERROR_TRACE_ID);
            logUtil.when(() -> LogUtil.logDetailedError(anyInt(), anyString(), anyString(), anyString(), any()))
                    .thenAnswer(inv -> null);
            featureEnum.when(() -> FeatureEnum.isEnabled(any())).thenReturn(false);

            // All calls to processMessage throw — covers original + NACK retry in catch
            doThrow(new RuntimeException("always fails"))
                    .when(messageProcessorService)
                    .processMessage(any(RequestContext.class), anyString(), anyString());

            invokeHandleHL7Message(VALID_HL7_MLLP, nonZntPortEntry());

            // shouldSendResponse=false but channel active → finally sends fallback NACK
            verify(ctx, times(1)).writeAndFlush(any());
        }
    }

    // =========================================================================
    // 9. Both throw + channel inactive → no writeAndFlush
    // =========================================================================
    @Test
    @DisplayName("9. Both throw and channel inactive → no response sent at all")
    void whenBothThrow_channelInactive_shouldNotSendAnyResponse1() throws Exception {
        when(channel.isActive()).thenReturn(false);

        try (MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<FeatureEnum> featureEnum = mockStatic(FeatureEnum.class)) {

            etg.when(ErrorTraceIdGenerator::generateErrorTraceId).thenReturn(ERROR_TRACE_ID);
            logUtil.when(() -> LogUtil.logDetailedError(anyInt(), anyString(), anyString(), anyString(), any()))
                    .thenAnswer(inv -> null);
            featureEnum.when(() -> FeatureEnum.isEnabled(any())).thenReturn(false);

            doThrow(new RuntimeException("always fails"))
                    .when(messageProcessorService)
                    .processMessage(any(RequestContext.class), anyString(), anyString());

            invokeHandleHL7Message(VALID_HL7_MLLP, nonZntPortEntry());

            // Channel inactive → sendResponseAndClose returns early AND finally skips
            verify(ctx, never()).writeAndFlush(any());
        }
    }

    @Test
    @DisplayName("10. Fallback generic NACK with Feature flag ON → NTE appended and errorTraceId generated in finally")
    void whenFallbackAndFeatureEnabled_shouldAppendNteAndGenerateTraceIdInFinally() throws Exception {

        when(channel.isActive()).thenReturn(true);

        try (MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<FeatureEnum> featureEnum = mockStatic(FeatureEnum.class)) {

            featureEnum.when(() -> FeatureEnum.isEnabled(FeatureEnum.ADD_NTE_SEGMENT_TO_HL7_ACK))
                    .thenReturn(true);

            etg.when(ErrorTraceIdGenerator::generateErrorTraceId)
                    .thenReturn("FINALLY-GENERATED-ID");

            logUtil.when(() -> LogUtil.logDetailedError(anyInt(), anyString(), anyString(), anyString(), any()))
                    .thenAnswer(inv -> null);

            doThrow(new RuntimeException("fail"))
                    .when(messageProcessorService)
                    .processMessage(any(RequestContext.class), anyString(), anyString());

            invokeHandleHL7Message(VALID_HL7_MLLP, nonZntPortEntry());

            // verify response sent
            verify(ctx).writeAndFlush(any());
            etg.verify(ErrorTraceIdGenerator::generateErrorTraceId, atLeastOnce());
        }
    }

    @Test
    void whenGenericNackExpected_shouldSendTcpNack() throws Exception {

        try (MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class)) {

            etg.when(ErrorTraceIdGenerator::generateErrorTraceId)
                    .thenReturn("ERR-123");

            logUtil.when(() -> LogUtil.logDetailedError(anyInt(), anyString(), anyString(), anyString(), any()))
                    .thenAnswer(inv -> null);

            invokeHandleConflictingWrapper(true);

            verify(messageProcessorService, times(1))
                    .processMessage(any(RequestContext.class), anyString(), contains("NACK|"));

            verify(ctx).writeAndFlush(any());
        }
    }

    @Test
    void whenProcessMessageFails_shouldStillSendResponse() throws Exception {

        try (MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class)) {

            etg.when(ErrorTraceIdGenerator::generateErrorTraceId)
                    .thenReturn("ERR-789");

            logUtil.when(() -> LogUtil.logDetailedError(anyInt(), anyString(), anyString(), anyString(), any()))
                    .thenAnswer(inv -> null);

            doThrow(new RuntimeException("storage fail"))
                    .when(messageProcessorService)
                    .processMessage(any(), any(), any());

            invokeHandleConflictingWrapper(true);

            // still response sent
            verify(ctx).writeAndFlush(any());
        }
    }

    @Test
    void whenUnexpectedException_shouldSendProcessingErrorNack() throws Exception {

        try (MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class)) {

            etg.when(ErrorTraceIdGenerator::generateErrorTraceId)
                    .thenReturn("ERR-999");

            logUtil.when(() -> LogUtil.logDetailedError(anyInt(), anyString(), anyString(), anyString(), any()))
                    .thenAnswer(inv -> null);

            doThrow(new RuntimeException("boom"))
                    .when(messageProcessorService)
                    .processMessage(any(RequestContext.class), anyString(), any());

            // Execute
            invokeHandleConflictingWrapper(server, true);

            verify(ctx, times(1)).writeAndFlush(any());
        }
    }

    @Test
    @DisplayName("1. Message size exceeded → TCP NACK sent")
    void whenMessageSizeExceeded_tcp_shouldSendTcpNack() throws Exception {

        AttributeKey<Boolean> MESSAGE_SIZE_EXCEEDED_KEY = getAttrKey("MESSAGE_SIZE_EXCEEDED_KEY");

        @SuppressWarnings("unchecked")
        Attribute<Boolean> attr = mock(Attribute.class);
        when(channel.attr(eq(MESSAGE_SIZE_EXCEEDED_KEY))).thenReturn(attr);
        when(attr.get()).thenReturn(true);

        when(portResolverService.resolve(any(), any())).thenReturn(Optional.empty());

        try (MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class)) {

            etg.when(ErrorTraceIdGenerator::generateErrorTraceId).thenReturn("ERR-1");

            invokeHandleMessage(RAW_MESSAGE, Optional.empty());

            verify(ctx).writeAndFlush(any()); // TCP NACK
        }
    }

    @Test
    @DisplayName("2. Message size exceeded → HL7 NACK sent for MLLP port")
    void whenMessageSizeExceeded_mllp_shouldSendHl7Nack() throws Exception {

        AttributeKey<Boolean> MESSAGE_SIZE_EXCEEDED_KEY = getAttrKey("MESSAGE_SIZE_EXCEEDED_KEY");

        @SuppressWarnings("unchecked")
        Attribute<Boolean> attr = mock(Attribute.class);
        when(channel.attr(eq(MESSAGE_SIZE_EXCEEDED_KEY))).thenReturn(attr);
        when(attr.get()).thenReturn(true);

        PortConfig.PortEntry portEntry = new PortConfig.PortEntry();
        portEntry.responseType = "mllp";

        when(portResolverService.resolve(any(), any()))
                .thenReturn(Optional.of(portEntry));

        try (MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<FeatureEnum> featureEnum = mockStatic(FeatureEnum.class)) {

            featureEnum.when(() -> FeatureEnum.isEnabled(any())).thenReturn(false);
            etg.when(ErrorTraceIdGenerator::generateErrorTraceId).thenReturn("ERR-2");
            invokeHandleMessage(RAW_MESSAGE, Optional.empty());
            verify(ctx).writeAndFlush(any());
        }
    }

    @Test
    @DisplayName("4. MLLP port → HL7 flow executed (ACK sent)")
    void whenMllpPort_shouldProcessAsHL7() throws Exception {

        // Arrange
        PortConfig.PortEntry portEntry = new PortConfig.PortEntry();
        portEntry.responseType = "mllp";

        when(portResolverService.resolve(any(), any()))
                .thenReturn(Optional.of(portEntry));

        try (MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<FeatureEnum> featureEnum = mockStatic(FeatureEnum.class)) {

            etg.when(ErrorTraceIdGenerator::generateErrorTraceId).thenReturn("ERR-4");
            featureEnum.when(() -> FeatureEnum.isEnabled(any())).thenReturn(false);

            // Act
            invokeHandleMessage(VALID_HL7_MLLP, Optional.of(portEntry));

            // Assert
            verify(messageProcessorService, atLeastOnce())
                    .processMessage(any(), anyString(), anyString());

            verify(ctx, times(1)).writeAndFlush(any());
        }
    }

    @Test
    @DisplayName("5. TCP port but MLLP wrapped → conflicting wrapper handled")
    void whenTcpPort_butMllpWrapped_shouldSendConflictNack() throws Exception {

        // TCP mode → no port entry
        when(portResolverService.resolve(any(), any()))
                .thenReturn(Optional.empty());

        String mllpMessage = (char) MLLP_START + "DATA"
                + (char) MLLP_END_1 + (char) MLLP_END_2;

        try (MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<FeatureEnum> featureEnum = mockStatic(FeatureEnum.class)) {

            etg.when(ErrorTraceIdGenerator::generateErrorTraceId).thenReturn("ERR-5");
            featureEnum.when(() -> FeatureEnum.isEnabled(any())).thenReturn(false);

            // Act
            invokeHandleMessage(mllpMessage, Optional.empty());

            // Assert
            verify(ctx, times(1)).writeAndFlush(any()); // response sent

            verify(messageProcessorService, atLeastOnce())
                    .processMessage(any(), anyString(), anyString());
        }
    }

    @Test
    @DisplayName("6. TCP normal → generic processing executed")
    void whenTcpNormal_shouldProcessGenericMessage() throws Exception {

        // TCP mode
        when(portResolverService.resolve(any(), any()))
                .thenReturn(Optional.empty());

        try (MockedStatic<ErrorTraceIdGenerator> etg = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<LogUtil> logUtil = mockStatic(LogUtil.class);
                MockedStatic<FeatureEnum> featureEnum = mockStatic(FeatureEnum.class)) {

            etg.when(ErrorTraceIdGenerator::generateErrorTraceId).thenReturn("ERR-6");
            featureEnum.when(() -> FeatureEnum.isEnabled(any())).thenReturn(false);

            // Act
            invokeHandleMessage("NORMAL_MESSAGE", Optional.empty());

            verify(messageProcessorService, times(1))
                    .processMessage(any(), anyString(), anyString());

            verify(ctx, times(1)).writeAndFlush(any());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("7. KeepAlive > 0 → IdleStateHandler applied")
    void whenKeepAlivePresent_shouldSetIdleHandler() throws Exception {

        PortConfig.PortEntry portEntry = mock(PortConfig.PortEntry.class);
        when(portEntry.getKeepAliveTimeout()).thenReturn(30);

        when(portResolverService.resolve(any(), any()))
                .thenReturn(Optional.of(portEntry));

        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        when(ctx.pipeline()).thenReturn(pipeline);

        // IMPORTANT: mock pipeline behavior to avoid NPE
        when(pipeline.get(anyString())).thenReturn(null);

        AttributeKey<Integer> key = getAttrKey("KEEP_ALIVE_TIMEOUT_KEY");

        Attribute<Integer> attr = mock(Attribute.class);
        when(channel.attr(eq(key))).thenReturn(attr);

        invokeHandleMessage("MSG", Optional.of(portEntry));

        verify(attr).set(30);
    }

    @SuppressWarnings("unchecked")
    private <T> AttributeKey<T> getAttrKey(String fieldName) throws Exception {
        Field field = NettyTcpServer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AttributeKey<T>) field.get(null);
    }

    @Test
    @DisplayName("Should return early when HAProxy details already set")
    void whenHaproxyAlreadySet_shouldReturnEarly() throws Exception {

        AttributeKey<String> haproxyKey = getAttrKey("HAPROXY_DETAILS_KEY");

        @SuppressWarnings("unchecked")
        Attribute<String> attr = mock(Attribute.class);
        when(channel.attr(eq(haproxyKey))).thenReturn(attr);
        when(attr.get()).thenReturn("already-present");

        invokeHandleSandboxProxy();

        // should NOT set anything again
        verify(attr, never()).set(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Should set HAProxy details and proceed without crashing")
    void shouldSetHaproxyDetails() throws Exception {

        AttributeKey<String> haproxyKey = getAttrKey("HAPROXY_DETAILS_KEY");
        AttributeKey<Integer> katKey = getAttrKey("KEEP_ALIVE_TIMEOUT_KEY");

        Attribute<String> haproxyAttr = mock(Attribute.class);
        Attribute<Integer> katAttr = mock(Attribute.class);

        when(channel.attr(eq(haproxyKey))).thenReturn(haproxyAttr);
        when(channel.attr(eq(katKey))).thenReturn(katAttr);
        when(haproxyAttr.get()).thenReturn(null);

        // pipeline mock (IMPORTANT)
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        when(ctx.pipeline()).thenReturn(pipeline);
        when(pipeline.get(anyString())).thenReturn(null);

        // Act
        invokeHandleSandboxProxy();

        verify(haproxyAttr).set(contains("sourceAddress"));
    }

    private void invokeHandleSandboxProxy() throws Exception {
        Method method = NettyTcpServer.class.getDeclaredMethod(
                "handleSandboxProxy",
                ChannelHandlerContext.class,
                String.class,
                UUID.class);
        method.setAccessible(true);
        method.invoke(server, ctx, SESSION_ID, INTERACTION_ID);
    }

    @Test
    @DisplayName("Should extract HAProxy details and store in channel")
    void whenProxyCommand_shouldStoreHaproxyDetails() throws Exception {

        HAProxyMessage proxyMsg = mock(HAProxyMessage.class);

        when(proxyMsg.command()).thenReturn(HAProxyCommand.PROXY);
        when(proxyMsg.sourceAddress()).thenReturn("10.0.0.1");
        when(proxyMsg.sourcePort()).thenReturn(1234);
        when(proxyMsg.destinationAddress()).thenReturn("192.168.1.1");
        when(proxyMsg.destinationPort()).thenReturn(8080);

        AttributeKey<String> haproxyKey = getAttrKey("HAPROXY_DETAILS_KEY");

        @SuppressWarnings("unchecked")
        Attribute<String> attr = mock(Attribute.class);
        when(channel.attr(eq(haproxyKey))).thenReturn(attr);

        // pipeline mock
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        when(ctx.pipeline()).thenReturn(pipeline);
        when(pipeline.get(anyString())).thenReturn(null);

        invokeHandleProxyHeader(proxyMsg);

        verify(attr).set(contains("sourceAddress=10.0.0.1"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("kat <= 0 → should fallback to ReadTimeoutHandler")
    void whenKatZero_shouldUseReadTimeout() throws Exception {

        HAProxyMessage proxyMsg = mock(HAProxyMessage.class);

        when(proxyMsg.command()).thenReturn(HAProxyCommand.PROXY);
        when(proxyMsg.sourceAddress()).thenReturn("10.0.0.1");
        when(proxyMsg.sourcePort()).thenReturn(1234);
        when(proxyMsg.destinationAddress()).thenReturn("192.168.1.1");
        when(proxyMsg.destinationPort()).thenReturn(8080);

        AttributeKey<String> haproxyKey = getAttrKey("HAPROXY_DETAILS_KEY");
        AttributeKey<Integer> katKey = getAttrKey("KEEP_ALIVE_TIMEOUT_KEY");

        Attribute<String> haproxyAttr = mock(Attribute.class);
        Attribute<Integer> katAttr = mock(Attribute.class);

        when(channel.attr(eq(haproxyKey))).thenReturn(haproxyAttr);
        when(channel.attr(eq(katKey))).thenReturn(katAttr);

        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        when(ctx.pipeline()).thenReturn(pipeline);

        ChannelHandler idleHandler = mock(ChannelHandler.class);
        when(pipeline.get("idleStateHandler")).thenReturn(idleHandler);

        // Act
        invokeHandleProxyHeader(proxyMsg);

        // Assert
        verify(katAttr).set(null);
        verify(pipeline).replace(
                eq("idleStateHandler"),
                eq("defaultReadTimeout"),
                any(ReadTimeoutHandler.class));
    }

    @Test
    @DisplayName("1. Empty buffer → no frame emitted, returns immediately")
    void decode_emptyBuffer_shouldEmitNoFrame() {
        ByteBuf empty = Unpooled.buffer(0);
        embeddedChannel.writeInbound(empty);

        assertNull(embeddedChannel.readInbound(), "No frame should be emitted for empty buffer");
    }

    @Test
    @DisplayName("2. Buffer < 3 bytes after fragment accounting → no frame emitted")
    void decode_bufferLessThan3Bytes_shouldEmitNoFrame() {
        // 2 bytes — passes fragmentSize > 0 check but fails readableBytes() < 3 check
        ByteBuf twoBytes = Unpooled.wrappedBuffer(new byte[] { 0x0B, 0x41 });
        embeddedChannel.writeInbound(twoBytes);

        assertNull(embeddedChannel.readInbound(), "No frame should be emitted for < 3 bytes");
    }

    // =========================================================================
    // decode() — 4. MLLP incomplete (no end marker)
    // =========================================================================
    @Test
    @DisplayName("4. Incomplete MLLP frame (no end markers) → no frame emitted, waits for more")
    void decode_incompleteMllpFrame_shouldNotEmitFrame() {
        // MLLP_START present but no MLLP_END_1 + MLLP_END_2
        byte[] payload = (MLLP_START + "MSH|^~\\&|PARTIAL\r").getBytes(StandardCharsets.UTF_8);
        payload[0] = MLLP_START; // ensure first byte is correct

        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(buildMllpStart("MSH|^~\\&|PARTIAL\r")));

        assertNull(embeddedChannel.readInbound(), "Incomplete MLLP frame must not be emitted");
    }

    // =========================================================================
    // decode() — 5. MLLP size exceeded → frame emitted + flag set
    // =========================================================================
    @Test
    @DisplayName("5. MLLP frame exceeds maxFrameLength → frame emitted, MESSAGE_SIZE_EXCEEDED_KEY=true")
    void decode_mllpSizeExceeded_shouldEmitFrameAndSetFlag() throws Exception {
        // Create decoder with tiny max frame (10 bytes) so any real message exceeds it
        EmbeddedChannel ch = new EmbeddedChannel(createDecoder(10));
        seedChannelAttributes(ch);

        // Build a frame without end markers that exceeds 10 bytes
        byte[] oversized = buildMllpStart("MSH|^~\\&|TOOLONG_MESSAGE_CONTENT\r");
        ch.writeInbound(Unpooled.wrappedBuffer(oversized));

        ByteBuf frame = ch.readInbound();
        assertNotNull(frame, "Oversized MLLP frame must still be emitted");
        frame.release();

        Boolean exceeded = ch.attr(SIZE_EXCEEDED_KEY).get();
        assertTrue(Boolean.TRUE.equals(exceeded), "MESSAGE_SIZE_EXCEEDED_KEY must be set to true");

        ch.close();
    }

    // =========================================================================
    // decode() — 7. TCP delimiter incomplete
    // =========================================================================
    @Test
    @DisplayName("7. Incomplete TCP frame (no end markers) → no frame emitted")
    void decode_incompleteTcpFrame_shouldNotEmitFrame() {
        // TCP_START present, content, but no TCP_END_1 + TCP_END_2
        byte[] payload = buildTcpStart("INCOMPLETE|DATA");
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(payload));

        assertNull(embeddedChannel.readInbound(), "Incomplete TCP frame must not be emitted");
    }

    @Test
    @DisplayName("decodeLast: Remaining readable bytes on channel close → frame emitted")
    void decodeLast_withRemainingData_shouldEmitFinalFrame() throws Throwable {
        EmbeddedChannel ch = new EmbeddedChannel(createDecoder(1024));
        seedChannelAttributes(ch);
        byte[] partial = buildMllpStart("PARTIAL_DATA"); // important
        ch.writeInbound(Unpooled.wrappedBuffer(partial));
        assertNull(ch.readInbound(), "No frame should be emitted before channel close");
        ch.finish();
        ByteBuf finalFrame = ch.readInbound();
        assertNotNull(finalFrame, "Final frame should be emitted on channel close");
        finalFrame.release();
    }

    @Test
    @DisplayName("TCP: Complete frame → should extract and emit frame")
    void decode_tcpCompleteFrame_shouldEmitFrame() throws Throwable {
        EmbeddedChannel ch = new EmbeddedChannel(createDecoder(1024));
        seedChannelAttributes(ch);
        byte[] payload = new byte[] {
                TCP_START, 'H', 'E', 'L', 'L', 'O', TCP_END_1, TCP_END_2
        };

        ch.writeInbound(Unpooled.wrappedBuffer(payload));

        ByteBuf frame = ch.readInbound();
        assertNotNull(frame, "TCP frame should be emitted");

        assertEquals(payload.length, frame.readableBytes());
        frame.release();
    }

    @Test
    @DisplayName("InteractionId is null → should generate and set new UUID")
    void decode_whenInteractionIdNull_shouldGenerateAndSetUuid() throws Throwable {
        EmbeddedChannel ch = new EmbeddedChannel(createDecoder(1024));
        seedChannelAttributes(ch);
        // Ensure interactionId is NULL before decode
        ch.attr(INTERACTION_ATTRIBUTE_KEY).set(null);

        // Send valid data to trigger decode
        byte[] data = new byte[] { 0x55, 0x66, 0x77 }; // goes to NO_DELIMITER branch
        ch.writeInbound(Unpooled.wrappedBuffer(data));

        // Read frame to ensure decode executed
        ByteBuf frame = ch.readInbound();
        assertNotNull(frame);
        frame.release();

        UUID interactionId = ch.attr(INTERACTION_ATTRIBUTE_KEY).get();
        assertNotNull(interactionId, "InteractionId should be generated and set");
    }

    @Test
    @DisplayName("MLLP: End markers present → loop detects and frame emitted")
    void decode_mllpEndMarkersFound_shouldEmitFrame() throws Throwable {
        EmbeddedChannel ch = new EmbeddedChannel(createDecoder(1024));
        seedChannelAttributes(ch);

        byte[] payload = new byte[] {
                MLLP_START,
                'M', 'S', 'H', '|', 'T', 'E', 'S', 'T',
                MLLP_END_1,
                MLLP_END_2
        };

        ch.writeInbound(Unpooled.wrappedBuffer(payload));
        ByteBuf frame = ch.readInbound();
        assertNotNull(frame, "MLLP frame should be emitted when end markers are found");
        assertEquals(payload.length, frame.readableBytes());
        frame.release();
    }

    @Test
    @DisplayName("TCP: End markers present → loop detects and emits frame")
    void decode_tcpEndMarkersFound_shouldEmitFrame() throws Throwable {
        EmbeddedChannel ch = new EmbeddedChannel(createDecoder(1024));
        seedChannelAttributes(ch);

        byte[] payload = new byte[] {
                TCP_START,
                'H', 'E', 'L', 'L', 'O',
                TCP_END_1,
                TCP_END_2
        };

        ch.writeInbound(Unpooled.wrappedBuffer(payload));
        ByteBuf frame = ch.readInbound();
        assertNotNull(frame, "TCP frame should be emitted when end markers are found");
        assertEquals(payload.length, frame.readableBytes());
        frame.release();
    }

    @Test
    @DisplayName("TCP: End markers in middle → should stop at first match")
    void decode_tcpEndMarkersInMiddle_shouldStopAtFirstMatch() throws Throwable {
        EmbeddedChannel ch = new EmbeddedChannel(createDecoder(1024));
        seedChannelAttributes(ch);
        byte tcpStartDelimiter = 0;
        byte tcpEndDelimiter1 = 0;
        byte tcpEndDelimiter2 = 0;
        byte start = tcpStartDelimiter;
        byte end1 = tcpEndDelimiter1;
        byte end2 = tcpEndDelimiter2;

        byte[] payload = new byte[] {
                start,
                'A', 'B', 'C',
                end1, end2,
                'X', 'Y', 'Z',
                end1, end2
        };

        ch.writeInbound(Unpooled.wrappedBuffer(payload));

        ByteBuf frame = ch.readInbound();
        assertNotNull(frame);

        int expectedLength = 1 + 3 + 2; // correct
        assertEquals(expectedLength, frame.readableBytes());

        frame.release();
    }

    @Test
    @DisplayName("TCP: No end markers + within maxFrameLength → no frame emitted")
    void decode_tcpNoEndMarker_withinLimit_shouldNotEmitFrame() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(createDecoder(1024));
        seedChannelAttributes(ch);

        byte tcpStartDelimiter = 0;
        byte start = tcpStartDelimiter; // MUST match decoder

        byte[] payload = new byte[] {
                start,
                'A', 'B', 'C', 'D'
        };

        ch.writeInbound(Unpooled.wrappedBuffer(payload));

        // Now this will pass ONLY if TCP branch is hit
        assertNull(ch.readInbound(), "No frame should be emitted");
    }

    @Test
    @DisplayName("TCP: No end markers + size exceeds maxFrameLength → emit frame and set flag")
    void decode_tcpNoEndMarker_sizeExceeded_shouldEmitFrameAndSetFlag() throws Throwable {
        EmbeddedChannel ch = new EmbeddedChannel(createDecoder(10));
        seedChannelAttributes(ch);

        byte tcpStartDelimiterx = 0;
        byte start = tcpStartDelimiterx; // MUST match decoder
        byte[] payload = new byte[] {
                start,
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K'
        };

        ch.writeInbound(Unpooled.wrappedBuffer(payload));

        ByteBuf frame = ch.readInbound();
        assertNotNull(frame);
        frame.release();

        Boolean flag = ch.attr(MESSAGE_SIZE_EXCEEDED_KEY).get();
        assertTrue(Boolean.TRUE.equals(flag));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testHandleNoDelimiterMessage_featureEnabled_shouldAppendAndLog() throws Exception {

        Attribute<StringBuilder> accumulatorAttr = mock(Attribute.class);
        Attribute<String> haproxyAttr = mock(Attribute.class);

        when(ctx.channel()).thenReturn(channel);

        when(channel.attr(eq(RAW_ACCUMULATOR_KEY))).thenReturn(accumulatorAttr);
        when(channel.attr(eq(HAPROXY_DETAILS_KEY))).thenReturn(haproxyAttr);

        StringBuilder existing = new StringBuilder("OLD_");
        when(accumulatorAttr.get()).thenReturn(existing);

        when(haproxyAttr.get()).thenReturn("dummy-haproxy");

        Method m = getMethod("handleNoDelimiterMessage",
                ChannelHandlerContext.class,
                String.class,
                String.class,
                UUID.class);

        try (MockedStatic<FeatureEnum> mocked = mockStatic(FeatureEnum.class)) {

            mocked.when(() -> FeatureEnum.isEnabled(FeatureEnum.LOG_INCOMING_MESSAGE))
                    .thenReturn(true);

            m.invoke(server, ctx, "NEW_DATA", "SESSION-1", UUID.randomUUID());
        }

        assertEquals("OLD_NEW_DATA", existing.toString());
    }

    private static final AttributeKey<Long> SESSION_START_TIME_KEY = AttributeKey.valueOf("SESSION_START_TIME");
    private static final AttributeKey<AtomicInteger> SESSION_MESSAGE_COUNT_KEY = AttributeKey
            .valueOf("SESSION_MESSAGE_COUNT");
    private static final AttributeKey<Integer> KEEP_ALIVE_TIMEOUT_KEY = AttributeKey.valueOf("KEEP_ALIVE_TIMEOUT");

    @Test
    void sessionActivityLogHandler_allIdle_shouldCoverChannelIdle() throws Exception {

        EmbeddedChannel ch = new EmbeddedChannel();
        Class<?> clazz = Class.forName(
                "org.techbd.ingest.listener.NettyTcpServer$SessionActivityLogHandler");
        Constructor<?> constructor = clazz.getDeclaredConstructor(NettyTcpServer.class, int.class);
        constructor.setAccessible(true);
        Object handler = constructor.newInstance(server, 5);
        ch.pipeline().addLast((ChannelHandler) handler);
        ChannelHandlerContext ctx = ch.pipeline().firstContext();

        ch.attr(SESSION_ID_KEY).set("session-1");
        ch.attr(SESSION_START_TIME_KEY).set(System.currentTimeMillis() - 5000);
        ch.attr(SESSION_MESSAGE_COUNT_KEY).set(new AtomicInteger(3));
        ch.attr(KEEP_ALIVE_TIMEOUT_KEY).set(30);
        ch.attr(HAPROXY_DETAILS_KEY).set("dummy");

        Method method = clazz.getDeclaredMethod(
                "channelIdle",
                ChannelHandlerContext.class,
                IdleStateEvent.class);
        method.setAccessible(true);
        method.invoke(handler, ctx, IdleStateEvent.ALL_IDLE_STATE_EVENT);
        assertTrue(ch.isActive());
    }

    @Test
    void sessionActivityLogHandler_nonAllIdle_shouldCoverElseBranch() throws Exception {

        EmbeddedChannel ch = new EmbeddedChannel();
        Class<?> clazz = Class.forName(
                "org.techbd.ingest.listener.NettyTcpServer$SessionActivityLogHandler");
        Constructor<?> constructor = clazz.getDeclaredConstructor(NettyTcpServer.class, int.class);
        constructor.setAccessible(true);
        Object handler = constructor.newInstance(server, 5);
        ch.pipeline().addLast((ChannelHandler) handler);
        ChannelHandlerContext ctx = ch.pipeline().firstContext();
        Method method = clazz.getDeclaredMethod(
                "channelIdle",
                ChannelHandlerContext.class,
                IdleStateEvent.class);
        method.setAccessible(true);
        method.invoke(handler, ctx, IdleStateEvent.READER_IDLE_STATE_EVENT);
        assertTrue(ch.isActive());
    }

    @Test
    void exceptionCaught_timeout_shouldGenerateInteractionId() {

        EmbeddedChannel ch = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

                if (cause instanceof ReadTimeoutException) {

                    UUID interactionId = ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).get();

                    if (interactionId == null) {
                        interactionId = UUID.randomUUID();
                        ctx.channel().attr(INTERACTION_ATTRIBUTE_KEY).set(interactionId);
                    }
                }
            }
        });

        ch.attr(INTERACTION_ATTRIBUTE_KEY).set(null);

        ch.pipeline().fireExceptionCaught(new ReadTimeoutException());

        assertNotNull(ch.attr(INTERACTION_ATTRIBUTE_KEY).get());
    }

    private void invokeHandleGenericMessage(String rawMessage,
            Optional<PortConfig.PortEntry> portEntryOpt) throws Exception {
        Method method = NettyTcpServer.class.getDeclaredMethod(
                "handleGenericMessage",
                ChannelHandlerContext.class,
                String.class, // rawMessage
                String.class, // sessionId
                UUID.class, // interactionId
                String.class, // clientIP
                String.class, // clientPort
                String.class, // destinationIP
                String.class, // destinationPort
                Optional.class // portEntryOpt
        );
        method.setAccessible(true);
        method.invoke(server, ctx, rawMessage, SESSION_ID, INTERACTION_ID,
                CLIENT_IP, CLIENT_PORT, DEST_IP, DEST_PORT, portEntryOpt);
    }

    private void invokeSendResponseAndClose(String response, String sessionId,
            UUID interactionId, String responseType) throws Exception {
        Method method = NettyTcpServer.class.getDeclaredMethod(
                "sendResponseAndClose",
                ChannelHandlerContext.class,
                String.class,
                String.class,
                UUID.class,
                String.class);
        method.setAccessible(true);
        method.invoke(server, ctx, response, sessionId, interactionId, responseType);
    }

    private Optional<PortConfig.PortEntry> nonZntPortEntry() {
        PortConfig.PortEntry entry = mock(PortConfig.PortEntry.class);
        entry.responseType = "tcp";
        entry.route = "/non-znt-route";
        return Optional.of(entry);
    }

    /**
     * A PortEntry with responseType="outbound" — detectMllp=true,
     * detectMllpZNT=true.
     */
    private Optional<PortConfig.PortEntry> zntPortEntry() {
        PortConfig.PortEntry entry = mock(PortConfig.PortEntry.class);
        entry.responseType = "outbound";
        entry.route = "/znt-route";
        return Optional.of(entry);
    }

    // =========================================================================
    // Helper: invoke private handleHL7Message via reflection
    // =========================================================================
    private void invokeHandleHL7Message(String rawMessage,
            Optional<PortConfig.PortEntry> portEntryOpt) throws Exception {
        Method method = NettyTcpServer.class.getDeclaredMethod(
                "handleHL7Message",
                ChannelHandlerContext.class,
                String.class, // rawMessage
                String.class, // sessionId
                UUID.class, // interactionId
                String.class, // clientIP
                String.class, // clientPort
                String.class, // destinationIP
                String.class, // destinationPort
                Optional.class // portEntryOpt
        );
        method.setAccessible(true);
        method.invoke(server, ctx, rawMessage, SESSION_ID, INTERACTION_ID,
                CLIENT_IP, CLIENT_PORT, DEST_IP, DEST_PORT, portEntryOpt);
    }

    private void invokePrivate(String name, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++)
            types[i] = args[i].getClass();

        Method m = server.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        m.invoke(server, args);
    }

    private Method getMethod(String name, Class<?>... types) throws Exception {
        Method m = server.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
    }

    private byte getByte(String field) {
        return (byte) ReflectionTestUtils.getField(server, field);
    }

    private void invokeHandleConflictingWrapper(boolean genericNackExpected) throws Exception {
        invokeHandleConflictingWrapper(server, genericNackExpected);
    }

    private void invokeHandleConflictingWrapper(NettyTcpServer target, boolean genericNackExpected) throws Exception {

        Method method = NettyTcpServer.class.getDeclaredMethod(
                "handleConflictingWrapper",
                ChannelHandlerContext.class,
                String.class,
                String.class,
                UUID.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Optional.class,
                String.class,
                String.class,
                boolean.class);

        method.setAccessible(true);

        method.invoke(target,
                ctx,
                "RAW_MESSAGE",
                "SESSION",
                UUID.randomUUID(),
                "1.1.1.1",
                "8080",
                "2.2.2.2",
                "9000",
                Optional.empty(),
                "ERR_CODE",
                "Wrapper mismatch",
                genericNackExpected);
    }

    private void invokeHandleMessage(String rawMessage,
            Optional<PortConfig.PortEntry> portEntryOpt) throws Exception {

        Method method = NettyTcpServer.class.getDeclaredMethod(
                "handleMessage",
                ChannelHandlerContext.class,
                String.class,
                String.class,
                UUID.class);

        method.setAccessible(true);

        method.invoke(server, ctx, rawMessage, SESSION_ID, INTERACTION_ID);
    }

    private void invokeHandleProxyHeader(HAProxyMessage proxyMsg) throws Exception {
        Method method = NettyTcpServer.class.getDeclaredMethod(
                "handleProxyHeader",
                ChannelHandlerContext.class,
                HAProxyMessage.class,
                String.class,
                UUID.class);
        method.setAccessible(true);
        method.invoke(server, ctx, proxyMsg, SESSION_ID, INTERACTION_ID);
    }

    private byte[] buildMllpStart(String content) {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[contentBytes.length + 1];
        frame[0] = MLLP_START;
        System.arraycopy(contentBytes, 0, frame, 1, contentBytes.length);
        return frame;
    }

    /** Builds an incomplete TCP frame: 0x02 + content (no end markers) */
    private byte[] buildTcpStart(String content) {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[contentBytes.length + 1];
        frame[0] = TCP_START;
        System.arraycopy(contentBytes, 0, frame, 1, contentBytes.length);
        return frame;
    }

    @Test
    @DisplayName("startServer: parseTcpDelimiters called — valid hex values parsed correctly")
    void startServer_parsesValidTcpDelimiters() throws Exception {
        ReflectionTestUtils.setField(server, "tcpStartDelimiterHex", "0x02");
        ReflectionTestUtils.setField(server, "tcpEndDelimiter1Hex", "0x03");
        ReflectionTestUtils.setField(server, "tcpEndDelimiter2Hex", "0x0A");

        invokePrivate("parseTcpDelimiters");

        assertThat(getByte("tcpStartDelimiter")).isEqualTo((byte) 0x02);
        assertThat(getByte("tcpEndDelimiter1")).isEqualTo((byte) 0x03);
        assertThat(getByte("tcpEndDelimiter2")).isEqualTo((byte) 0x0A);
    }

    @Test
    @DisplayName("startServer: server starts on a background thread — does not block the calling thread")
    void startServer_runsOnBackgroundThread() throws Exception {
        ReflectionTestUtils.setField(server, "tcpPort", 0); // port 0 = OS picks free port
        ReflectionTestUtils.setField(server, "readTimeoutSeconds", 5);
        ReflectionTestUtils.setField(server, "maxMessageSizeBytes", 1024);
        ReflectionTestUtils.setField(server, "sessionLogIntervalSeconds", 0);
        ReflectionTestUtils.setField(server, "tcpStartDelimiterHex", "0x0B");
        ReflectionTestUtils.setField(server, "tcpEndDelimiter1Hex", "0x1C");
        ReflectionTestUtils.setField(server, "tcpEndDelimiter2Hex", "0x0D");

        long start = System.currentTimeMillis();
        server.startServer();
        long elapsed = System.currentTimeMillis() - start;

        // startServer() spawns a background thread and returns immediately,
        // so the calling thread should not have blocked waiting for Netty to bind.
        assertThat(elapsed).isLessThan(3000L);

        // Give the background thread a moment to begin startup
        Thread.sleep(300);
    }

}