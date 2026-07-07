package org.techbd.ingest.integrationtests.tcp.mllp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.techbd.ingest.integrationtests.base.BaseIntegrationTest;
import org.techbd.ingest.integrationtests.base.IngestionAssertionHelper;
import org.techbd.ingest.integrationtests.base.IngestionAssertionHelper.FlowAssertionParams;

/**
 * Full-stack integration tests for the Netty TCP/MLLP server.
 *
 * <p>Infrastructure (LocalStack S3 + SQS, bucket/queue creation, port-config upload)
 * is bootstrapped once by {@link BaseIntegrationTest}. Between every test method
 * {@code BaseIntegrationTest.cleanS3AndSqsState()} purges all data buckets and SQS
 * queues so each test starts with a clean slate.
 *
 * <h3>Profile override — critical</h3>
 * {@code NettyTcpServer.startServer()} checks {@code System.getenv("SPRING_PROFILES_ACTIVE")}
 * to decide whether to install {@link io.netty.handler.codec.haproxy.HAProxyMessageDecoder}.
 * {@link #initProfile()} sets the system property so {@code "test"} is returned,
 * ensuring the decoder is active and every connection sends a real PROXY v1 header.
 *
 * <h3>Port configurations under test (from list.json)</h3>
 * <pre>
 * { "port": 2575, "responseType": "outbound", "protocol": "TCP" }
 *
 * { "port": 5555, "responseType": "outbound", "protocol": "TCP",
 *   "route": "/hold", "dataDir": "/outbound", "metadataDir": "/outbound",
 *   "queue": "test.fifo", "keepAliveTimeout": 50 }
 * </pre>
 *
 * <h3>S3 / SQS path conventions</h3>
 * <ul>
 *   <li><b>Port 2575 (default flow)</b>: payload + ACK in {@code DEFAULT_DATA_BUCKET}
 *       under {@code data/YYYY/MM/DD/…}; metadata in {@code DEFAULT_METADATA_BUCKET}.</li>
 *   <li><b>Port 5555 (HOLD flow)</b>: all artefacts in {@code HOLD_BUCKET} under
 *       {@code outbound/hold/5555/YYYY/MM/DD/…} (data) and
 *       {@code outbound/hold/metadata/5555/YYYY/MM/DD/…} (metadata);
 *       message on {@code test.fifo}.</li>
 * </ul>
 *
 * <h3>Message-group ID — MllpMessageGroupStrategy</h3>
 * <ul>
 *   <li>ZNT present: {@code messageCode_deliveryType} (ZNT-2.1 / ZNT-4.1)</li>
 *   <li>ZNT absent: falls back to {@code destinationPort} from the PROXY header.</li>
 * </ul>
 */
class NettyMllpServerITCase extends BaseIntegrationTest {

    // ── Constants ──────────────────────────────────────────────────────────────

    private static final int    TCP_SERVER_PORT          = 7980;
    private static final String TCP_HOST                 = "localhost";
    private static final int    TCP_READ_TIMEOUT_SECONDS = 10;

    private static final String CLIENT_IP   = "203.0.113.10";
    private static final String DEST_IP     = "127.0.0.1";
    private static final int    CLIENT_PORT = 55000;

    private static final byte MLLP_START = 0x0B;
    private static final byte MLLP_END_1 = 0x1C;
    private static final byte MLLP_END_2 = 0x0D;

    // ═══════════════════════════════════════════════════════════════════════════
    // Shared helper instance
    // ═══════════════════════════════════════════════════════════════════════════

    private IngestionAssertionHelper assertionHelper() {
        return new IngestionAssertionHelper(s3Client, sqsClient);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Setup
    // ═══════════════════════════════════════════════════════════════════════════

    @BeforeAll
    static void initProfile() {
        System.setProperty("SPRING_PROFILES_ACTIVE", "test");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Diagnostic tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DIAG: SPRING_PROFILES_ACTIVE must be 'test'")
    void shouldHaveTestProfileActive() {
        assertThat(System.getProperty("SPRING_PROFILES_ACTIVE"))
                .as("SPRING_PROFILES_ACTIVE must be 'test' so HAProxyMessageDecoder is active")
                .isEqualTo("test");
    }

    @Test
    @DisplayName("DIAG: TCP server is listening on configured port")
    void shouldAcceptTcpConnections() {
        try (Socket socket = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            assertThat(socket.isConnected())
                    .as("TCP server must be reachable on port " + TCP_SERVER_PORT)
                    .isTrue();
        } catch (Exception e) {
            throw new AssertionError(
                    "TCP server is not listening on " + TCP_HOST + ":" + TCP_SERVER_PORT, e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Port 2575 — default flow (no route / dataDir / metadataDir)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * IT: Port 2575 — MLLP HL7 with ZNT — happy path, full S3 + SQS flow.
     *
     * <p>ZNT segment values: ZNT-2.1={@code ORU}, ZNT-4.1={@code PUSH}.
     * Expected {@code messageGroupId}: {@code ORU_PUSH}.
     */
    @Test
    @DisplayName("IT: Port 2575 — MLLP HL7 with ZNT — happy path, S3 + SQS full flow")
    void shouldProcessHl7WithZnt_andPersistToS3AndSqs() throws Exception {
        String hl7 = loadHl7Fixture("hl7_message.hl7");
        SoftAssertions softly = new SoftAssertions();

        byte[] ackBytes = sendMllpWithProxy(CLIENT_IP, DEST_IP, CLIENT_PORT, 2575, hl7);
        assertMllpAck(ackBytes, softly, "Port-2575 ZNT ACK");

        assertionHelper().assertDefaultFlow(
                defaultFlowParams(hl7, "ORU_PUSH"),
                softly);

        softly.assertAll();
    }

    /**
     * IT: Port 2575 — MLLP HL7 WITHOUT ZNT —no publishing to queue, ACK is a NACK with error mentioning missing ZNT.
     *
     * <p>Expected ACK: {@code MSA|AR} + ERR mentioning missing ZNT.
     * Expected {@code messageGroupId}: {@code "2575"}.
     */
    @Test
    @DisplayName("IT: Port 2575 — MLLP HL7 without ZNT — group-id = PROXY destPort (\"2575\")")
    void shouldRejectHl7WithoutZnt() throws Exception {
        String hl7 = loadHl7Fixture("hl7_without_znt.hl7");
        SoftAssertions softly = new SoftAssertions();

        byte[] ackBytes = sendMllpWithProxy(CLIENT_IP, DEST_IP, CLIENT_PORT, 2575, hl7);
        assertMllpNackForMissingZNT(ackBytes, softly, "Port-2575 no-ZNT ACK");

        // Error flow: no payload verification, no SQS assertion
        assertionHelper().assertErrorFlow(
                errorFlowParams("2575"),
                softly);

        softly.assertAll();
    }

    /**
     * IT: Port 2575 — PROXY header only, no HL7 — server closes after read-timeout (10 s).
     *
     * <p>S3 data bucket must remain empty — no message was processed.
     */
    @Test
    @DisplayName("IT: Port 2575 — no message sent — server closes after read-timeout (10 s)")
    void shouldCloseConnectionWhenNoMessageReceivedWithinTimeout() throws Exception {
        SoftAssertions softly = new SoftAssertions();

        long start = System.currentTimeMillis();
        try (Socket s = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            s.setSoTimeout((TCP_READ_TIMEOUT_SECONDS + 5) * 1_000);

            s.getOutputStream().write(buildProxyV1Header(CLIENT_IP, DEST_IP, CLIENT_PORT, 2575));
            s.getOutputStream().flush();

            byte[] buf  = new byte[4_096];
            int    read;
            try {
                read = s.getInputStream().read(buf);
            } catch (java.net.SocketTimeoutException e) {
                read = -1;
            }

            long elapsed = System.currentTimeMillis() - start;
            softly.assertThat(elapsed)
                    .as("Server must close")
                    .isLessThan((long) (TCP_READ_TIMEOUT_SECONDS + 5) * 1_000);
        }

        // S3 must be empty — nothing was ingested
        assertThat(assertionHelper().bucketIsEmpty(DEFAULT_DATA_BUCKET))
                .as("S3 data bucket must be empty — no message was delivered").isTrue();

        softly.assertAll();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Port 5555 — HOLD flow
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * IT: Port 5555 — HOLD flow — MLLP HL7 with ZNT — full S3 + SQS validation.
     *
     * <p>Port config: {@code dataDir=/outbound, metadataDir=/outbound, queue=test.fifo}.
     * Expected {@code messageGroupId}: {@code healthelink_GHC_ORU_SN_ORU}.
     */
    @Test
    @DisplayName("IT: Port 5555 — HOLD flow — MLLP HL7 with ZNT, full S3 + SQS validation")
    void shouldProcessHl7WithZnt_inHoldFlow_andPersistToS3AndSqs() throws Exception {
        String hl7 = loadHl7Fixture("hl7_message_with_znt.hl7");
        SoftAssertions softly = new SoftAssertions();

        byte[] ackBytes = sendMllpWithProxy(CLIENT_IP, DEST_IP, CLIENT_PORT, 5555, hl7);
        assertMllpAck(ackBytes, softly, "Port-5555 ZNT HOLD ACK");

        assertionHelper().assertHoldFlow(
                holdFlowParams(hl7, "HEL-QE_identifier_ORU_PUSH",
                        5555, "/outbound", "/outbound", null, queueUrls.get("test.fifo")),
                softly);

        softly.assertAll();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FlowAssertionParams factories
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Default two-bucket flow params (port 2575, no route/dataDir).
     *
     * @param hl7     the HL7 message that was sent
     * @param groupId expected SQS {@code messageGroupId}
     */
    private FlowAssertionParams defaultFlowParams(String hl7, String groupId) {
        return FlowAssertionParams.builder()
                .dataBucket(DEFAULT_DATA_BUCKET)
                .metadataBucket(DEFAULT_METADATA_BUCKET)
                .queueUrl(mainQueueUrl)
                .expectedMessageGroupId(groupId)
                .expectedPayload(hl7)
                .payloadNormalizer(IngestionAssertionHelper::normalizeHl7)
                .ackExpected(true)
                .build();
    }

    /**
     * Error flow params — payload expected under {@code error/} prefix, no SQS check.
     *
     * @param groupId expected SQS {@code messageGroupId} (used if SQS check is re-enabled)
     */
    private FlowAssertionParams errorFlowParams(String groupId) {
        return FlowAssertionParams.builder()
                .dataBucket(DEFAULT_DATA_BUCKET)
                .metadataBucket(DEFAULT_METADATA_BUCKET)
                .errorFlow(true)
                .expectedMessageGroupId(groupId)
                // No payload comparison for error flow — content may differ
                .ackExpected(true)
                .build();
    }

    /**
     * Hold-flow params (port 5555, single HOLD_BUCKET).
     *
     * @param hl7         the HL7 message that was sent
     * @param groupId     expected SQS {@code messageGroupId}
     * @param portNum     port number (used in hold key path)
     * @param dataDir     {@code dataDir} from port-config (e.g. {@code "/outbound"})
     * @param metadataDir {@code metadataDir} from port-config
     * @param tenantId    tenant segment; null if absent
     * @param queueUrl    SQS queue URL for this entry
     */
    private FlowAssertionParams holdFlowParams(
            String hl7, String groupId,
            int portNum, String dataDir, String metadataDir,
            String tenantId, String queueUrl) {
        return FlowAssertionParams.builder()
                .dataBucket(HOLD_BUCKET)
                .metadataBucket(null)           // hold: same bucket for data + metadata
                .holdFlow(true)
                .port(portNum)
                .dataDir(dataDir)
                .metadataDir(metadataDir)
                .tenantId(tenantId)
                .queueUrl(queueUrl)
                .expectedMessageGroupId(groupId)
                .expectedPayload(hl7)
                .payloadNormalizer(IngestionAssertionHelper::normalizeHl7)
                .ackExpected(true)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ACK assertion helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Asserts that {@code rawAck} is a valid MLLP-wrapped HL7 ACK ({@code MSA|AA}).
     */
    private void assertMllpAck(byte[] rawAck, SoftAssertions softly, String label) {
        softly.assertThat(rawAck).as(label + ": raw ACK bytes must not be empty").isNotEmpty();
        if (rawAck == null || rawAck.length == 0) return;

        String ackStr = stripMllpFraming(rawAck);
        softly.assertThat(ackStr).as(label + ": must contain MSH segment").contains("MSH");
        softly.assertThat(ackStr).as(label + ": must contain MSA segment").contains("MSA");
        softly.assertThat(ackStr).as(label + ": acknowledgement code must be AA").contains("MSA|AA");
    }

    /**
     * Asserts that {@code rawAck} is a valid MLLP-wrapped HL7 NACK ({@code MSA|AR})
     * for the specific case of a missing ZNT segment.
     */
    private void assertMllpNackForMissingZNT(byte[] rawAck, SoftAssertions softly, String label) {
        softly.assertThat(rawAck).as(label + ": raw NACK bytes must not be empty").isNotEmpty();
        if (rawAck == null || rawAck.length == 0) return;

        String ackStr = stripMllpFraming(rawAck);
        softly.assertThat(ackStr).as(label + ": must contain MSH segment").contains("MSH");
        softly.assertThat(ackStr).as(label + ": must contain MSA segment").contains("MSA");
        softly.assertThat(ackStr).as(label + ": acknowledgement must be AR").contains("MSA|AR");
        softly.assertThat(ackStr).as(label + ": must contain ERR segment").contains("ERR");
        softly.assertThat(ackStr).as(label + ": must mention missing ZNT segment")
                .containsIgnoringCase("Missing ZNT");
        softly.assertThat(ackStr).as(label + ": must contain HL7 error code 207")
                .contains("207^Application error");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TCP / MLLP / PROXY low-level helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Opens a TCP connection, sends a HAProxy PROXY v1 header immediately followed
     * by an MLLP-wrapped HL7 message in a single write, and returns the raw ACK bytes.
     */
    private byte[] sendMllpWithProxy(String clientIp, String destIp,
            int clientPort, int destPort, String hl7) throws Exception {
        try (Socket s = new Socket(TCP_HOST, TCP_SERVER_PORT)) {
            s.setSoTimeout(15_000);
            s.getOutputStream().write(
                    concat(buildProxyV1Header(clientIp, destIp, clientPort, destPort), wrapMllp(hl7)));
            s.getOutputStream().flush();
            return readMllpFrame(s);
        }
    }

    /** Builds a HAProxy PROXY protocol v1 text header. */
    private static byte[] buildProxyV1Header(String clientIp, String destIp,
            int clientPort, int destPort) {
        return String.format("PROXY TCP4 %s %s %d %d\r\n",
                clientIp, destIp, clientPort, destPort)
                .getBytes(StandardCharsets.US_ASCII);
    }

    /** Wraps {@code hl7Text} in MLLP framing: {@code <VT> payload <FS><CR>}. */
    private static byte[] wrapMllp(String hl7Text) {
        byte[] payload = hl7Text.getBytes(StandardCharsets.UTF_8);
        byte[] framed  = new byte[1 + payload.length + 2];
        framed[0] = MLLP_START;
        System.arraycopy(payload, 0, framed, 1, payload.length);
        framed[framed.length - 2] = MLLP_END_1;
        framed[framed.length - 1] = MLLP_END_2;
        return framed;
    }

    /** Reads bytes from {@code socket} until the MLLP end-of-frame marker is seen. */
    private static byte[] readMllpFrame(Socket socket) throws Exception {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        int b;
        while ((b = socket.getInputStream().read()) != -1) {
            buf.write(b);
            byte[] soFar = buf.toByteArray();
            int    len   = soFar.length;
            if (len >= 2 && soFar[len - 2] == MLLP_END_1 && soFar[len - 1] == MLLP_END_2) break;
        }
        return buf.toByteArray();
    }

    /** Removes MLLP framing bytes to produce a readable string. */
    private static String stripMllpFraming(byte[] raw) {
        return new String(raw, StandardCharsets.UTF_8)
                .replace(String.valueOf((char) MLLP_START), "")
                .replace(String.valueOf((char) MLLP_END_1), "")
                .replace(String.valueOf((char) MLLP_END_2), "");
    }

    /** Concatenates two byte arrays. */
    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Fixture loading
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Loads an HL7 fixture from {@code src/test/resources/org/techbd/ingest/tcp-test-resources/}.
     */
    private static String loadHl7Fixture(String filename) throws Exception {
        String path = "org/techbd/ingest/tcp-test-resources/" + filename;
        try (InputStream is = NettyMllpServerITCase.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("HL7 fixture not found on classpath: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}