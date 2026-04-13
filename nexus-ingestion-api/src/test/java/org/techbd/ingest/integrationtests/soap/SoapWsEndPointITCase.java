package org.techbd.ingest.integrationtests.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.techbd.ingest.integrationtests.util.SoapTestFixtures.extractXPath;
import static org.techbd.ingest.integrationtests.util.SoapTestFixtures.loadFixture;
import org.techbd.ingest.NexusIngestionApiApplication;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import javax.xml.soap.SOAPConstants;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.integrationtests.base.BaseIntegrationTest;
import org.techbd.ingest.NexusIngestionApiApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.techbd.ingest.NexusIngestionApiApplication;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.test.context.SpringBootTest;
import org.techbd.ingest.integrationtests.base.NexusIntegrationTest;

/**
 * Full-stack integration tests for SOAP endpoints.
 *
 * <p>
 * LocalStack (S3 + SQS), bucket/queue creation, and port-config upload are
 * all handled <em>once</em> by {@link BaseIntegrationTest}. Between every test
 * method, {@code BaseIntegrationTest.cleanS3AndSqsState()} purges data buckets
 * and SQS queues, so each test starts with a clean slate.
 *
 * <h3>Common assertion helpers</h3>
 * <ul>
 * <li>{@link #assertDefaultFlowS3AndSqs} — validates S3 + SQS for the
 * default port-9000 flow (two separate buckets, main queue).</li>
 * <li>{@link #assertHoldFlowS3AndSqs} — validates S3 + SQS for the HOLD
 * port-5555 flow (single hold bucket, test.fifo queue).</li>
 * <li>{@link #assertSoap12Envelope} / {@link #assertSoap11Envelope} — verify
 * the envelope namespace via XPath rather than fragile string contains.</li>
 * </ul>
 */
@NexusIntegrationTest
@Tag("integration")
class SoapWsEndPointITCase extends BaseIntegrationTest {

        @Autowired
        private TestRestTemplate restTemplate;

        @LocalServerPort
        private int port;

        @Autowired
        private ApplicationContext context;

        private static final ObjectMapper MAPPER = new ObjectMapper();

        // ═══════════════════════════════════════════════════════════════════════════
        // Diagnostic tests
        // ═══════════════════════════════════════════════════════════════════════════

        @Test
        @DisplayName("DIAG: Health check returns 200")
        void healthCheck() {
                ResponseEntity<String> response = restTemplate.getForEntity(
                                "http://localhost:" + port + "/actuator/health", String.class);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("DIAG: Port 9000 config is present in list.json on S3")
        void portConfigContainsPort9000() throws Exception {
                String json = new String(
                                s3Client.getObject(GetObjectRequest.builder()
                                                .bucket(PORT_CONFIG_BUCKET)
                                                .key(PORT_CONFIG_KEY)
                                                .build())
                                                .readAllBytes(),
                                StandardCharsets.UTF_8);

                JsonNode root = MAPPER.readTree(json);
                for (JsonNode node : root) {
                        if (node.has("port") && node.get("port").asInt() == 9000) {
                                assertThat(node.get("protocol").asText()).isEqualTo("HTTP");
                                return;
                        }
                }
                throw new AssertionError("Port 9000 config not found in list.json");
        }

        @Test
        @DisplayName("DIAG: messageDispatcher beans are registered")
        void messageDispatcherBeansRegistered() {
                long count = java.util.Arrays.stream(context.getBeanDefinitionNames())
                                .filter(name -> name.contains("messageDispatcher"))
                                .peek(System.out::println)
                                .count();
                assertThat(count).isPositive();
        }

        @Test
        @DisplayName("DIAG: Servlet registration beans are mapped")
        void servletMappingsRegistered() {
                var beans = context.getBeansOfType(
                                org.springframework.boot.web.servlet.ServletRegistrationBean.class);
                assertThat(beans).isNotEmpty();
                beans.forEach((name, bean) -> System.out.println(name + " -> " + bean.getUrlMappings()));
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // PIX Add
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Integration Test: PIX ADD SOAP 1.2 — default flow (port 9000).
         *
         * <p>
         * <b>Port config (port 9000):</b>
         * 
         * <pre>
         * { "port": 9000, "protocol": "HTTP", "whitelistIps": ["0.0.0.0/0"],
         *   "execType": "sync", "trafficTo": "util" }
         * </pre>
         *
         * No custom route/dataDir/metadataDir, so default key structure applies:
         * <ul>
         * <li>Payload: {@code data/YYYY/MM/DD/{id_timestamp}}</li>
         * <li>ACK: {@code data/YYYY/MM/DD/{id_timestamp}_ack}</li>
         * <li>Metadata: {@code metadata/YYYY/MM/DD/{id_timestamp}_metadata.json}</li>
         * </ul>
         * Data bucket: {@code local-sbx-nexus-ingestion-s3-bucket}<br>
         * Metadata bucket: {@code local-sbx-nexus-ingestion-s3-metadata-bucket}
         *
         * <p>
         * <b>IMPORTANT:</b> Update this test if {@code list.json} changes route,
         * dataDir, metadataDir, queue, or bucket resolver logic.
         */
        @Test
        @DisplayName("IT: PIX-ADD SOAP 1.2 — full flow (HTTP + S3 + SQS)")
        void pixAdd_soap12_fullFlow() throws Exception {
                String expectedRequest = loadFixture("pix-add-request_1_2.txt");
                String expectedAck = loadFixture("pix-add-response_1_2.txt");

                SoftAssertions softly = new SoftAssertions();

                String response = sendSoapRequest(expectedRequest, SOAPConstants.SOAP_1_2_PROTOCOL, "9000", softly);

                softly.assertThat(response).as("Response contains PIX ACK").contains("MCCI_IN000002UV01");
                assertSoap12Envelope(response, softly);

                assertDefaultFlowS3AndSqs(expectedRequest, expectedAck, "127.0.0.1_9000", softly);

                softly.assertAll();
        }

        /**
         * Integration Test: PIX ADD SOAP 1.2 — HOLD flow (port 5555).
         *
         * <p>
         * <b>Port config (port 5555):</b>
         * 
         * <pre>
         * { "port": 5555, "route": "/hold", "dataDir": "/http",
         *   "metadataDir": "/outbound", "queue": "test.fifo", "keepAliveTimeout": 20 }
         * </pre>
         *
         * HOLD key structure:
         * <ul>
         * <li>Payload: {@code http/hold/5555/YYYY/MM/DD/{ts}_soap-message.xml}</li>
         * <li>ACK: {@code http/hold/5555/YYYY/MM/DD/{ts}_soap-message.xml_ack}</li>
         * <li>Metadata:
         * {@code outbound/hold/metadata/5555/YYYY/MM/DD/{ts}_soap-message.xml_metadata.json}</li>
         * </ul>
         * Both data and metadata live in {@code local-pdr-txd-sbx-hold}.
         *
         * <p>
         * <b>IMPORTANT:</b> Update this test if {@code list.json} changes route,
         * dataDir, metadataDir, queue, or bucket resolver logic.
         */
        @Test
        @DisplayName("IT: PIX-ADD SOAP 1.2 — HOLD flow (HTTP + S3 Hold Bucket + SQS)")
        void pixAdd_soap12_holdFlow() throws Exception {
                String expectedRequest = loadFixture("pix-add-request_1_2.txt");
                String expectedAck = loadFixture("pix-add-response_1_2.txt");

                SoftAssertions softly = new SoftAssertions();

                String response = sendSoapRequest(expectedRequest, SOAPConstants.SOAP_1_2_PROTOCOL, "5555", softly);

                softly.assertThat(response).as("Response contains PIX ACK").contains("MCCI_IN000002UV01");
                assertSoap12Envelope(response, softly);

                assertHoldFlowS3AndSqs(expectedRequest, expectedAck, "127.0.0.1_5555", softly);

                softly.assertAll();
        }

        @Test
        @DisplayName("IT: PIX-ADD SOAP 1.1 — full flow (HTTP + S3 + SQS)")
        void pixAdd_soap11_fullflow() throws Exception {
                String expectedRequest = loadFixture("pix-add-request_1_1.txt");
                String expectedAck = loadFixture("pix-add-response_1_1.txt");

                SoftAssertions softly = new SoftAssertions();

                String response = sendSoapRequest(expectedRequest, SOAPConstants.SOAP_1_1_PROTOCOL, "9000", softly);

                softly.assertThat(response).as("Response contains PIX ACK").contains("MCCI_IN000002UV01");
                assertSoap11Envelope(response, softly);

                assertDefaultFlowS3AndSqs(expectedRequest, expectedAck, "127.0.0.1_9000", softly);

                softly.assertAll();
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // PIX Merge
        // ═══════════════════════════════════════════════════════════════════════════

        @Test
        @DisplayName("IT: PIX-MERGE SOAP 1.2 — full flow (HTTP + S3 + SQS)")
        void pixMerge_soap12_fullflow() throws Exception {
                String expectedRequest = loadFixture("pix-merge-request_1_2.txt");
                String expectedAck = loadFixture("pix-merge-response_1_2.txt");

                SoftAssertions softly = new SoftAssertions();

                String response = sendSoapRequest(expectedRequest, SOAPConstants.SOAP_1_2_PROTOCOL, "9000", softly);

                softly.assertThat(response).as("Response contains PIX ACK").contains("MCCI_IN000002UV01");
                assertSoap12Envelope(response, softly);

                assertDefaultFlowS3AndSqs(expectedRequest, expectedAck, "127.0.0.1_9000", softly);

                softly.assertAll();
        }

        @Test
        @DisplayName("IT: PIX-MERGE SOAP 1.1 — full flow (HTTP + S3 + SQS)")
        void pixMerge_soap11_fullflow() throws Exception {
                String expectedRequest = loadFixture("pix-merge-request_1_1.txt");
                String expectedAck = loadFixture("pix-merge-response_1_1.txt");

                SoftAssertions softly = new SoftAssertions();

                String response = sendSoapRequest(expectedRequest, SOAPConstants.SOAP_1_1_PROTOCOL, "9000", softly);

                softly.assertThat(response).as("Response contains MERGE-12345").contains("MERGE-12345");
                assertSoap11Envelope(response, softly);

                assertDefaultFlowS3AndSqs(expectedRequest, expectedAck, "127.0.0.1_9000", softly);

                softly.assertAll();
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // PIX Update
        // ═══════════════════════════════════════════════════════════════════════════

        @Test
        @DisplayName("IT: PIX-UPDATE SOAP 1.2 — full flow (HTTP + S3 + SQS)")
        void pixUpdate_soap12_fullflow() throws Exception {
                String expectedRequest = loadFixture("pix-update-request_1_2.txt");
                String expectedAck = loadFixture("pix-update-response_1_2.txt");

                SoftAssertions softly = new SoftAssertions();

                String response = sendSoapRequest(expectedRequest, SOAPConstants.SOAP_1_2_PROTOCOL, "9000", softly);

                softly.assertThat(response).as("Response contains PIX ACK").contains("MCCI_IN000002UV01");
                assertSoap12Envelope(response, softly);

                assertDefaultFlowS3AndSqs(expectedRequest, expectedAck, "127.0.0.1_9000", softly);

                softly.assertAll();
        }

        @Test
        @DisplayName("IT: PIX-UPDATE SOAP 1.1 — full flow (HTTP + S3 + SQS)")
        void pixUpdate_soap11_fullflow() throws Exception {
                String expectedRequest = loadFixture("pix-update-request_1_1.txt");
                String expectedAck = loadFixture("pix-update-response_1_1.txt");

                SoftAssertions softly = new SoftAssertions();

                String response = sendSoapRequest(expectedRequest, SOAPConstants.SOAP_1_1_PROTOCOL, "9000", softly);

                // pix-update-request has an empty SOAP Header — app falls back to this ID
                softly.assertThat(response).as("Response contains fallback message ID")
                                .contains("unknown-incoming-message-id");
                assertSoap11Envelope(response, softly);

                assertDefaultFlowS3AndSqs(expectedRequest, expectedAck, "127.0.0.1_9000", softly);

                softly.assertAll();
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // PNR
        // ═══════════════════════════════════════════════════════════════════════════

        @Test
        @DisplayName("IT: PNR SOAP 1.1 — full flow (HTTP + S3 + SQS)")
        void pnr_soap11_FullFlow() throws Exception {
                String expectedRequest = loadFixture("pnr-request_1_1.txt");

                SoftAssertions softly = new SoftAssertions();

                String response = sendSoapRequest(expectedRequest, SOAPConstants.SOAP_1_1_PROTOCOL, "9000", softly);

                softly.assertThat(response).as("Response contains RegistryResponse").contains("RegistryResponse");
                softly.assertThat(response).as("Response contains Success status")
                                .contains("ResponseStatusType:Success");
                assertSoap11Envelope(response, softly);

                // PNR has no ACK fixture — pass null; helper skips ACK XPath assertions when
                // null
                assertDefaultFlowS3AndSqs(expectedRequest, null, "127.0.0.1_9000", softly);

                softly.assertAll();
        }

        @Test
        @DisplayName("IT: PNR SOAP 1.2 — full flow (HTTP + S3 + SQS)")
        void pnr_soap12_FullFlow() throws Exception {
                String expectedRequest = loadFixture("pnr-request_1_2.txt");

                SoftAssertions softly = new SoftAssertions();

                String response = sendSoapRequest(expectedRequest, SOAPConstants.SOAP_1_2_PROTOCOL, "9000", softly);

                softly.assertThat(response).as("Response contains RegistryResponse").contains("RegistryResponse");
                softly.assertThat(response).as("Response contains Success status")
                                .contains("ResponseStatusType:Success");
                assertSoap12Envelope(response, softly);

                assertDefaultFlowS3AndSqs(expectedRequest, null, "127.0.0.1_9000", softly);

                softly.assertAll();
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // PNR MTOM
        // ═══════════════════════════════════════════════════════════════════════════

        @Test
        @DisplayName("IT: PNR MTOM — RegistryResponse Success returned")
        void pnrMtom_successReturned() throws Exception {
                String expectedRequest = loadFixture("pnr-xdsb-mtom-request.txt");

                SoftAssertions softly = new SoftAssertions();

                String response = sendMtomRequest(expectedRequest, "9000", softly);

                softly.assertThat(response).as("Response contains RegistryResponse").contains("RegistryResponse");
                softly.assertThat(response).as("Response contains Success status")
                                .contains("ResponseStatusType:Success");

                assertDefaultFlowS3AndSqs(expectedRequest, null, "127.0.0.1_9000", softly);

                softly.assertAll();
        }

        @Test
        @DisplayName("IT: PNR MTOM — wsa:RelatesTo echoes MTOM request MessageID")
        void pnrMtom_relatesTo_matchesMtomMessageId() throws Exception {
                String expectedRequest = loadFixture("pnr-xdsb-mtom-request.txt");

                SoftAssertions softly = new SoftAssertions();

                String response = sendMtomRequest(expectedRequest, "9000", softly);

                softly.assertThat(response).as("Response echoes MTOM MessageID")
                                .contains("f19a3e9e-324d-4c6c-8a96-6747955d86f5");

                assertDefaultFlowS3AndSqs(expectedRequest, null, "127.0.0.1_9000", softly);

                softly.assertAll();
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // Common S3 + SQS assertion helpers
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Validates the complete S3 → metadata → SQS chain for the <em>default</em>
         * (port 9000) flow.
         *
         * <p>
         * Expectations:
         * <ul>
         * <li>Payload stored in {@link #DEFAULT_DATA_BUCKET} under
         * {@code data/YYYY/MM/DD/…}</li>
         * <li>ACK stored in same bucket with {@code _ack} suffix</li>
         * <li>Metadata stored in {@link #DEFAULT_METADATA_BUCKET} under
         * {@code metadata/YYYY/MM/DD/…_metadata.json}</li>
         * <li>SQS message on main queue matches all S3 paths</li>
         * <li>{@code messageGroupId} equals the supplied {@code expectedGroupId}</li>
         * </ul>
         *
         * @param expectedPayload the raw XML that was sent (used for payload
         *                        comparison)
         * @param expectedAck     the fixture ACK (used for sender/receiver XPath
         *                        check);
         *                        pass {@code null} to skip ACK XPath assertions
         * @param expectedGroupId expected {@code messageGroupId} value (e.g.
         *                        {@code "127.0.0.1_9000"})
         * @param softly          collector for soft assertion failures
         */
        private void assertDefaultFlowS3AndSqs(
                        String expectedPayload,
                        String expectedAck,
                        String expectedGroupId,
                        SoftAssertions softly) throws Exception {

                // ── S3 data bucket ──────────────────────────────────────────────────────
                ListObjectsV2Response dataObjects = s3Client.listObjectsV2(
                                ListObjectsV2Request.builder().bucket(DEFAULT_DATA_BUCKET).build());

                softly.assertThat(dataObjects.contents())
                                .as("Data bucket should contain payload + ACK").isNotEmpty();

                String payloadKey = dataObjects.contents().stream()
                                .map(S3Object::key).filter(k -> !k.contains("_ack"))
                                .findFirst().orElseThrow(() -> new AssertionError("Payload key not found"));

                String ackKey = dataObjects.contents().stream()
                                .map(S3Object::key).filter(k -> k.contains("_ack"))
                                .findFirst().orElseThrow(() -> new AssertionError("ACK key not found"));

                String actualPayload = readS3(DEFAULT_DATA_BUCKET, payloadKey);
                String actualAck = readS3(DEFAULT_DATA_BUCKET, ackKey);

                softly.assertThat(normalizeXml(actualPayload))
                                .as("Stored payload must match sent request")
                                .isEqualTo(normalizeXml(expectedPayload));

                if (expectedAck != null) {
                        softly.assertThat(extractXPath(actualAck, "//sender/device/id/@root"))
                                        .isEqualTo(extractXPath(expectedAck, "//sender/device/id/@root"));
                        softly.assertThat(extractXPath(actualAck, "//receiver/device/id/@root"))
                                        .isEqualTo(extractXPath(expectedAck, "//receiver/device/id/@root"));
                }

                // ── S3 metadata bucket ──────────────────────────────────────────────────
                ListObjectsV2Response metadataObjects = s3Client.listObjectsV2(
                                ListObjectsV2Request.builder().bucket(DEFAULT_METADATA_BUCKET).build());

                softly.assertThat(metadataObjects.contents())
                                .as("Metadata bucket should contain objects").isNotEmpty();

                String metadataKey = metadataObjects.contents().get(0).key();
                String metadataContent = readS3(DEFAULT_METADATA_BUCKET, metadataKey);

                JsonNode meta = MAPPER.readTree(metadataContent);
                String key = meta.get("key").asText();
                JsonNode jsonMeta = meta.get("json_metadata");
                String s3DataPath = jsonMeta.get("s3DataObjectPath").asText();
                String metaPath = jsonMeta.get("fullS3MetaDataPath").asText();
                String ackPath = jsonMeta.get("fullS3AcknowledgementPath").asText();

                // Date-partitioned prefix
                String expectedPrefix = todayDataPrefix();
                softly.assertThat(key).as("Key must start with date prefix").startsWith(expectedPrefix);

                // Suffix consistency
                String datePrefix = expectedPrefix + "/";
                String metaPrefix = "metadata/" + expectedPrefix.substring("data/".length()) + "/";
                String keySuffix = key.substring(datePrefix.length());

                softly.assertThat(extractSuffix(s3DataPath, datePrefix))
                                .as("Data path suffix").isEqualTo(keySuffix);
                softly.assertThat(extractSuffix(ackPath, datePrefix))
                                .as("ACK path suffix").isEqualTo(keySuffix + "_ack");
                softly.assertThat(extractSuffix(metaPath, metaPrefix))
                                .as("Metadata path suffix").isEqualTo(keySuffix + "_metadata.json");

                // ── SQS ─────────────────────────────────────────────────────────────────
                assertSqsConsistency(mainQueueUrl, s3DataPath, metaPath, ackPath, key, expectedGroupId, softly);
        }

        /**
         * Validates the complete S3 → metadata → SQS chain for the <em>HOLD</em>
         * (port 5555) flow.
         *
         * <p>
         * Expectations:
         * <ul>
         * <li>All objects (payload, ACK, metadata) in {@link #HOLD_BUCKET}</li>
         * <li>Payload key starts with {@code http/hold/5555/YYYY/MM/DD/…}</li>
         * <li>Metadata key starts with
         * {@code outbound/hold/metadata/5555/YYYY/MM/DD/…}</li>
         * <li>SQS message on {@code test.fifo} matches all S3 paths</li>
         * <li>{@code messageGroupId} equals the supplied {@code expectedGroupId}</li>
         * </ul>
         *
         * @param expectedPayload the raw XML that was sent
         * @param expectedAck     the fixture ACK; {@code null} to skip ACK XPath
         *                        assertions
         * @param expectedGroupId expected {@code messageGroupId} (e.g.
         *                        {@code "127.0.0.1_5555"})
         * @param softly          collector for soft assertion failures
         */
        private void assertHoldFlowS3AndSqs(
                        String expectedPayload,
                        String expectedAck,
                        String expectedGroupId,
                        SoftAssertions softly) throws Exception {

                ListObjectsV2Response objects = s3Client.listObjectsV2(
                                ListObjectsV2Request.builder().bucket(HOLD_BUCKET).build());

                softly.assertThat(objects.contents())
                                .as("HOLD bucket should contain objects").isNotEmpty();

                String metadataKey = objects.contents().stream().map(S3Object::key)
                                .filter(k -> k.contains("_metadata.json")).findFirst()
                                .orElseThrow(() -> new AssertionError("Metadata key not found in HOLD bucket"));

                String payloadKey = objects.contents().stream().map(S3Object::key)
                                .filter(k -> k.contains("soap-message.xml") && !k.contains("_ack")).findFirst()
                                .orElseThrow(() -> new AssertionError("Payload key not found in HOLD bucket"));

                String ackKey = objects.contents().stream().map(S3Object::key)
                                .filter(k -> k.contains("_ack")).findFirst()
                                .orElseThrow(() -> new AssertionError("ACK key not found in HOLD bucket"));

                String actualPayload = readS3(HOLD_BUCKET, payloadKey);
                String actualAck = readS3(HOLD_BUCKET, ackKey);
                String metadataContent = readS3(HOLD_BUCKET, metadataKey);

                // ── Payload / ACK ────────────────────────────────────────────────────────
                softly.assertThat(normalizeXml(actualPayload))
                                .as("Payload must match request").isEqualTo(normalizeXml(expectedPayload));

                if (expectedAck != null) {
                        softly.assertThat(extractXPath(actualAck, "//sender/device/id/@root"))
                                        .isEqualTo(extractXPath(expectedAck, "//sender/device/id/@root"));
                        softly.assertThat(extractXPath(actualAck, "//receiver/device/id/@root"))
                                        .isEqualTo(extractXPath(expectedAck, "//receiver/device/id/@root"));
                }

                // ── Metadata ────────────────────────────────────────────────────────────
                JsonNode meta = MAPPER.readTree(metadataContent);
                String key = meta.get("key").asText();
                JsonNode jsonMeta = meta.get("json_metadata");
                String s3DataPath = jsonMeta.get("s3DataObjectPath").asText();
                String metaPath = jsonMeta.get("fullS3MetaDataPath").asText();
                String ackPath = jsonMeta.get("fullS3AcknowledgementPath").asText();

                String datePath = todayDatePath();
                String expectedDataPrefix = "http/hold/5555/" + datePath;
                String expectedMetaPrefix = "outbound/hold/metadata/5555/" + datePath;

                softly.assertThat(key).as("Key must follow HOLD data structure").startsWith(expectedDataPrefix);
                softly.assertThat(metadataKey).as("Metadata key must follow HOLD metadata structure")
                                .startsWith(expectedMetaPrefix);

                // Full S3 path validation
                softly.assertThat(s3DataPath).as("Full data path")
                                .isEqualTo("s3://" + HOLD_BUCKET + "/" + key);
                softly.assertThat(metaPath).as("Full metadata path")
                                .isEqualTo("s3://" + HOLD_BUCKET + "/" + metadataKey);
                softly.assertThat(ackPath).as("ACK path")
                                .isEqualTo("s3://" + HOLD_BUCKET + "/" + ackKey);

                // Suffix consistency
                String suffix = key.substring(expectedDataPrefix.length() + 1);
                softly.assertThat(ackKey).as("ACK key suffix").endsWith(suffix + "_ack");
                softly.assertThat(metadataKey).as("Metadata key suffix").endsWith(suffix + "_metadata.json");

                // ── SQS ─────────────────────────────────────────────────────────────────
                assertSqsConsistency(
                                queueUrls.get("test.fifo"),
                                s3DataPath, metaPath, ackPath,
                                key, expectedGroupId, softly);
        }

        /**
         * Validates that the SQS message received from {@code queueUrl} is consistent
         * with the corresponding S3 metadata.
         *
         * <p>
         * Checks: {@code s3DataObjectPath}, {@code fullS3MetaDataPath},
         * {@code fullS3AcknowledgementPath}, {@code s3ObjectId}, and
         * {@code messageGroupId}.
         */
        private void assertSqsConsistency(
                        String queueUrl,
                        String expectedDataPath,
                        String expectedMetaPath,
                        String expectedAckPath,
                        String expectedObjectId,
                        String expectedGroupId,
                        SoftAssertions softly) throws Exception {

                ReceiveMessageResponse sqsResponse = waitForSqsMessage(queueUrl);
                softly.assertThat(sqsResponse.messages()).as("SQS queue should have a message").isNotEmpty();

                if (sqsResponse.messages().isEmpty()) {
                        return; // soft — remaining checks would NPE
                }

                Message msg = sqsResponse.messages().get(0);
                JsonNode sqsJson = MAPPER.readTree(msg.body());

                softly.assertThat(sqsJson.get("s3DataObjectPath").asText())
                                .as("SQS s3DataObjectPath").isEqualTo(expectedDataPath);
                softly.assertThat(sqsJson.get("fullS3MetaDataPath").asText())
                                .as("SQS fullS3MetaDataPath").isEqualTo(expectedMetaPath);
                softly.assertThat(sqsJson.get("fullS3AcknowledgementPath").asText())
                                .as("SQS fullS3AcknowledgementPath").isEqualTo(expectedAckPath);
                softly.assertThat(sqsJson.get("s3ObjectId").asText())
                                .as("SQS s3ObjectId").isEqualTo(expectedObjectId);
                softly.assertThat(sqsJson.get("messageGroupId").asText())
                                .as("SQS messageGroupId").isEqualTo(expectedGroupId);
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // Envelope assertion helpers (XPath-based)
        // ═══════════════════════════════════════════════════════════════════════════

        /**
         * Asserts that the response uses a SOAP 1.2 envelope namespace, verified via
         * XPath rather than fragile string-contains matching.
         *
         * <p>
         * XPath expression:
         * {@code //*[local-name()='Envelope']/@*[local-name()='Envelope']}
         * is unreliable with prefix aliasing; we instead verify the namespace URI
         * is present in the serialised response, but anchor it to the {@code Envelope}
         * element to avoid false positives from body content.
         */
        private void assertSoap12Envelope(String response, SoftAssertions softly) {
                String soap12Uri = "http://www.w3.org/2003/05/soap-envelope";

                String namespace = extractXPath(response, "namespace-uri(/*)");
                String localName = extractXPath(response, "local-name(/*)");

                softly.assertThat(localName)
                                .as("Root element must be 'Envelope'")
                                .isEqualTo("Envelope");

                softly.assertThat(namespace)
                                .as("Envelope must declare SOAP 1.2 namespace")
                                .isEqualTo(soap12Uri);
        }

        private void assertSoap11Envelope(String response, SoftAssertions softly) {
                String soap11Uri = "http://schemas.xmlsoap.org/soap/envelope/";

                String namespace = extractXPath(response, "namespace-uri(/*)");
                String localName = extractXPath(response, "local-name(/*)");

                softly.assertThat(localName)
                                .as("Root element must be 'Envelope'")
                                .isEqualTo("Envelope");

                softly.assertThat(namespace)
                                .as("Envelope must declare SOAP 1.1 namespace")
                                .isEqualTo(soap11Uri);
        }
        // ═══════════════════════════════════════════════════════════════════════════
        // Utility helpers
        // ═══════════════════════════════════════════════════════════════════════════

        /** Reads an S3 object and returns its content as a UTF-8 string. */
        private String readS3(String bucket, String key) {
                return s3Client.getObjectAsBytes(
                                GetObjectRequest.builder().bucket(bucket).key(key).build())
                                .asUtf8String();
        }

        /**
         * Returns the expected S3 key prefix for the <em>default</em> flow:
         * {@code data/YYYY/MM/DD}.
         */
        private static String todayDataPrefix() {
                LocalDate d = LocalDate.now();
                return String.format("data/%d/%02d/%02d", d.getYear(), d.getMonthValue(), d.getDayOfMonth());
        }

        /**
         * Returns today's date as {@code YYYY/MM/DD}, used inside HOLD key paths.
         */
        private static String todayDatePath() {
                LocalDate d = LocalDate.now();
                return String.format("%d/%02d/%02d", d.getYear(), d.getMonthValue(), d.getDayOfMonth());
        }

        /**
         * Extracts the suffix of a full S3 path after the given prefix.
         * <p>
         * Example:
         * {@code extractSuffix("s3://bucket/data/2025/01/01/foo", "data/2025/01/01/")}
         * returns {@code "foo"}.
         */
        private static String extractSuffix(String fullPath, String prefix) {
                int idx = fullPath.indexOf(prefix);
                if (idx < 0) {
                        throw new AssertionError("Prefix '" + prefix + "' not found in path: " + fullPath);
                }
                return fullPath.substring(idx + prefix.length());
        }

        private String normalizeXml(String xml) {
                return xml
                                .replaceAll(">\\s+<", "><")
                                .replaceAll("\\s+", " ")
                                .trim();
        }

        // ═══════════════════════════════════════════════════════════════════════════
        // SOAP / MTOM request helpers
        // ═══════════════════════════════════════════════════════════════════════════

        private String sendSoapRequest(String xml, String soapProtocol, String forwardedPort,
                        SoftAssertions softly) {
                HttpHeaders headers = buildSoapHeaders(soapProtocol, forwardedPort);
                ResponseEntity<String> response = restTemplate.postForEntity(
                                "http://localhost:" + port + "/ws",
                                new HttpEntity<>(xml, headers),
                                String.class);
                softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                return response.getBody();
        }

        private String sendMtomRequest(String mtomRaw, String forwardedPort, SoftAssertions softly) {
                String boundary = extractMtomBoundary(mtomRaw);
                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.CONTENT_TYPE,
                                "multipart/related; type=\"application/xop+xml\"; boundary=\"" + boundary + "\"; "
                                                + "start=\"<request@meditech.com>\"; start-info=\"application/soap+xml\"");
                headers.set(Constants.REQ_X_FORWARDED_PORT, forwardedPort);
                headers.set(Constants.REQ_X_SERVER_IP, "127.0.0.1");

                ResponseEntity<String> response = restTemplate.postForEntity(
                                "http://localhost:" + port + "/ws",
                                new HttpEntity<>(mtomRaw, headers),
                                String.class);
                softly.assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                return response.getBody();
        }

        private HttpHeaders buildSoapHeaders(String soapProtocol, String forwardedPort) {
                HttpHeaders headers = new HttpHeaders();
                if (SOAPConstants.SOAP_1_2_PROTOCOL.equalsIgnoreCase(soapProtocol)) {
                        headers.setContentType(MediaType.valueOf("application/soap+xml"));
                } else if (SOAPConstants.SOAP_1_1_PROTOCOL.equalsIgnoreCase(soapProtocol)) {
                        headers.setContentType(MediaType.TEXT_XML);
                        headers.add("SOAPAction", "");
                } else {
                        throw new IllegalArgumentException("Unsupported SOAP protocol: " + soapProtocol);
                }
                headers.set(Constants.REQ_X_SERVER_IP, "127.0.0.1");
                headers.set(Constants.REQ_X_FORWARDED_PORT, forwardedPort);
                return headers;
        }

        // ── SQS polling ─────────────────────────────────────────────────────────

        /**
         * Polls the given queue URL until a message arrives or retries are exhausted.
         *
         * @throws AssertionError if no message is received after all retry attempts
         */
        private ReceiveMessageResponse waitForSqsMessage(String queueUrl) throws InterruptedException {
                if (queueUrl == null) {
                        throw new IllegalArgumentException("queueUrl must not be null");
                }
                for (int i = 0; i < 5; i++) {
                        ReceiveMessageResponse resp = sqsClient.receiveMessage(
                                        ReceiveMessageRequest.builder()
                                                        .queueUrl(queueUrl)
                                                        .waitTimeSeconds(2)
                                                        .build());
                        if (!resp.messages().isEmpty()) {
                                return resp;
                        }
                        Thread.sleep(1000);
                }
                throw new AssertionError("No SQS message received after retries for queue: " + queueUrl);
        }

        /** Convenience overload that polls the main FIFO queue. */
        private ReceiveMessageResponse waitForSqsMessage() throws InterruptedException {
                return waitForSqsMessage(mainQueueUrl);
        }

        private static String extractMtomBoundary(String mtomRaw) {
                for (String line : mtomRaw.split("\r?\n")) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("--") && !trimmed.equals("--")) {
                                return trimmed.substring(2);
                        }
                }
                throw new IllegalArgumentException("Could not extract MTOM boundary from fixture");
        }
}