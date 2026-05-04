package org.techbd.ingest.integrationtests.soap;


import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.integrationtests.base.BaseIntegrationTest;
import org.techbd.ingest.integrationtests.base.IngestionAssertionHelper;
import org.techbd.ingest.integrationtests.base.IngestionAssertionHelper.FlowAssertionParams;
import static org.techbd.ingest.integrationtests.util.XdsTestFixtures.extractXPath;
import static org.techbd.ingest.integrationtests.util.XdsTestFixtures.loadFixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;

/**
 * Integration tests for XDS (eXtensible Document Sharing) Repository endpoint.
 *
 * <p>Tests the full flow: XDS client → /xds/XDSbRepositoryWS endpoint
 * → XdsRepositoryController → SoapForwarderService → /ws (internal SOAP endpoint)
 * → S3 persistence + SQS publishing.
 *
 * <p>LocalStack (S3 + SQS) setup is handled by {@link BaseIntegrationTest}.
 * Between each test, {@code cleanS3AndSqsState()} ensures a clean slate.
 *

 * <p>Key aspects tested:
 * <ul>
 *   <li>XDS request reception and routing to /ws</li>
 *   <li>Multipart/related content handling</li>
 *   <li>Content-Type reconstruction from raw bytes</li>
 *   <li>SOAP forwarding with header propagation</li>
 *   <li>End-to-end persistence (S3 + SQS)</li>
 * </ul>
 */
class XdsRepositoryControllerITCase extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    
    // ═══════════════════════════════════════════════════════════════════════════
    // Shared helper instance
    // ═══════════════════════════════════════════════════════════════════════════
    /**
     * Lazily-created helper for S3/SQS assertions.
     */
    private IngestionAssertionHelper assertionHelper() {
        return new IngestionAssertionHelper(s3Client, sqsClient);
    }


 @Test
    @DisplayName("DIAG: Port 9000 config is present in list.json on S3")
    void shouldLoadHttpPortConfiguration() throws Exception {
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


// ═══════════════════════════════════════════════════════════════════════════
    // Diagnostic tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DIAG: XDS endpoint is accessible")
    void shouldAccessXdsEndpoint() throws Exception{
        String request = loadFixture("xds-repository-request.txt");
        // Not a full POST, just verifies endpoint exists and routes correctly
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        headers.add("SOAPAction", "urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b");
        headers.set(Constants.REQ_X_FORWARDED_PORT, String.valueOf(9000)); // OR "9000"
        headers.set(Constants.REQ_X_SERVER_IP, "127.0.0.1");

        HttpEntity<String> entity = new HttpEntity<>(request, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/xds/XDSbRepositoryWS",
                entity,
                String.class);
        // Should not be 404; may be 400/500 due to empty body, but endpoint exists
        assertThat(response.getStatusCode().value()).isNotEqualTo(404);
    }
    

        @Test
        @DisplayName("IT: XDS Repository SOAP forward — full flow (/xds → /ws)")
        void shouldForwardXdsRequestToWs_andReturnResponse() throws Exception {

         String request = loadFixture("xds-repository-request.txt");

        SoftAssertions softly = new SoftAssertions();

         String response = sendXdsRequest(request, "9000", softly);
                
                softly.assertThat(response).isNotBlank();
                softly.assertThat(response).contains("RegistryResponse");

            assertionHelper().assertDefaultFlow(
                    defaultFlowParams(request, null, "127.0.0.1_9000", false),
                    softly
            );

            softly.assertAll();

        }


        @Test
        @DisplayName("IT: XDS Repository handles empty body safely")
        void shouldHandleEmptyXdsRequest() {

            HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.TEXT_XML);
                headers.add("SOAPAction", "urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b");
                headers.set(Constants.REQ_X_FORWARDED_PORT, String.valueOf(9000)); // OR "9000"
                headers.set(Constants.REQ_X_SERVER_IP, "127.0.0.1");
            HttpEntity<String> entity = new HttpEntity<>("", headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "http://localhost:" + port + "/xds/XDSbRepositoryWS",
                    entity,
                    String.class);

            assertThat(response.getStatusCode())
            .isEqualTo(HttpStatus.ACCEPTED);
        }

        /**
     * IT: XDS-PNR forwarding through SoapForwarderService.
     *
     * <p>Tests:
     * 1. XDS request reaches /xds/XDSbRepositoryWS
     * 2. XdsRepositoryController reads raw bytes
     * 3. SoapForwarderService forwards to /ws with proper headers
     * 4. Response is RegistryResponse with Success status
     * 5. Payload persists to S3
     * 6. Message published to SQS
     */
    @Test
    @DisplayName("IT: XDS-PNR SOAP 1.1 — forwarded via SoapForwarderService, persisted to S3+SQS")
    void shouldForwardXdsPnrSoap11_andPersistToS3AndSqs() throws Exception {
        String request = loadFixture("xds-repository-request.txt");
        SoftAssertions softly = new SoftAssertions();

        String response = sendXdsRequest(request, "9000", softly);
        
        softly.assertThat(response).as("Response contains RegistryResponse")
                .contains("RegistryResponse");
        softly.assertThat(response).as("Response contains Success status")
                .contains("ResponseStatusType:Success");
        assertSoap12Envelope(response, softly);

        // Verify end-to-end persistence (S3 + SQS)
        assertionHelper().assertDefaultFlow(
                defaultFlowParams(request, null, "127.0.0.1_9000", false),
                softly);

        softly.assertAll();
    }


    @Test
    @DisplayName("IT: XDS — reconstructs Content-Type when missing multipart/related")
    void shouldReconstructContentType_forXdsRequest() throws Exception {

        String request = loadFixture("xds-repository-request.txt"); // contains MTOM boundary
        SoftAssertions softly = new SoftAssertions();

        HttpHeaders headers = new HttpHeaders();

       
        headers.setContentType(MediaType.TEXT_XML);

        headers.add("SOAPAction", "urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b");
        headers.set(Constants.REQ_X_SERVER_IP, "127.0.0.1");
        headers.set(Constants.REQ_X_FORWARDED_PORT, "9000");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/xds/XDSbRepositoryWS",
                new HttpEntity<>(request, headers),
                String.class);

       
        softly.assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        softly.assertThat(response.getBody())
                .as("MTOM should be parsed →  Content-Type reconstructed")
                .contains("RegistryResponse");

        softly.assertAll();
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // XDS with Hold Flow (port 5555)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * IT: XDS-PNR routed to HOLD flow (port 5555).
     *
     * <p>Tests hold-specific routing:
     * - Route: /hold
     * - Data bucket: local-pdr-txd-sbx-hold
     * - Queue: test.fifo
     */
    @Test
    @DisplayName("IT: XDS-PNR SOAP 1.1 — HOLD flow (port 5555, route=/hold)")
    void shouldForwardXdsPnrToHoldFlow_andPersistWithHoldRouting() throws Exception {
        String request = loadFixture("xds-repository-request.txt");
        SoftAssertions softly = new SoftAssertions();

        String response = sendXdsRequest(request, "5555", softly);
        
        softly.assertThat(response).as("Response contains RegistryResponse")
                .contains("RegistryResponse");
        assertSoap12Envelope(response, softly);

        // Verify hold-flow specific assertions
        assertionHelper().assertHoldFlow(
                holdFlowParams(request, null, "127.0.0.1_5555",
                        5555, "/http", "/outbound", null, queueUrls.get("test.fifo")),
                softly);

        softly.assertAll();
    }




    // ═══════════════════════════════════════════════════════════════════════════
    // FlowAssertionParams factories
    // ═══════════════════════════════════════════════════════════════════════════

    private FlowAssertionParams defaultFlowParams(String payload, String ackFixture, String groupId) {
        return defaultFlowParams(payload, ackFixture, groupId, true);
    }

    private FlowAssertionParams defaultFlowParams(
            String payload, String ackFixture, String groupId, boolean ackExpected) {
        FlowAssertionParams.Builder b = FlowAssertionParams.builder()
                .dataBucket(DEFAULT_DATA_BUCKET)
                .metadataBucket(DEFAULT_METADATA_BUCKET)
                .queueUrl(mainQueueUrl)
                .expectedMessageGroupId(groupId)
                .expectedPayload(payload)
                .payloadNormalizer(IngestionAssertionHelper::normalizeXml)
                .ackExpected(ackExpected);

        if (ackFixture != null) {
            b.ackXPathAssertions((ackXml, softly) -> {
                softly.assertThat(extractXPath(ackXml, "//sender/device/id/@root"))
                        .isEqualTo(extractXPath(ackFixture, "//sender/device/id/@root"));
                softly.assertThat(extractXPath(ackXml, "//receiver/device/id/@root"))
                        .isEqualTo(extractXPath(ackFixture, "//receiver/device/id/@root"));
            });
        }
        return b.build();
    }

    private FlowAssertionParams holdFlowParams(
            String payload, String ackFixture, String groupId,
            int portNum, String dataDir, String metadataDir,
            String tenantId, String queueUrl) {

        FlowAssertionParams.Builder b = FlowAssertionParams.builder()
                .dataBucket(HOLD_BUCKET)
                .metadataBucket(null)
                .holdFlow(true)
                .port(portNum)
                .dataDir(dataDir)
                .metadataDir(metadataDir)
                .tenantId(tenantId)
                .queueUrl(queueUrl)
                .expectedMessageGroupId(groupId)
                .expectedPayload(payload)
                .payloadNormalizer(IngestionAssertionHelper::normalizeXml)
                .ackExpected(true);

        if (ackFixture != null) {
            b.ackXPathAssertions((ackXml, softly) -> {
                softly.assertThat(extractXPath(ackXml, "//sender/device/id/@root"))
                        .isEqualTo(extractXPath(ackFixture, "//sender/device/id/@root"));
                softly.assertThat(extractXPath(ackXml, "//receiver/device/id/@root"))
                        .isEqualTo(extractXPath(ackFixture, "//receiver/device/id/@root"));
            });
        }
        return b.build();
    }


   



    // ═══════════════════════════════════════════════════════════════════════════
    // SOAP Envelope assertions
    // ═══════════════════════════════════════════════════════════════════════════

    private void assertSoap12Envelope(String response, SoftAssertions softly) {
        String soap12Uri = "http://www.w3.org/2003/05/soap-envelope";
        softly.assertThat(extractXPath(response, "local-name(/*)"))
                .as("Root element must be 'Envelope'").isEqualTo("Envelope");
        softly.assertThat(extractXPath(response, "namespace-uri(/*)"))
                .as("Envelope must declare SOAP 1.2 namespace").isEqualTo(soap12Uri);
    }

    private void assertSoap11Envelope(String response, SoftAssertions softly) {
        String soap11Uri = "http://schemas.xmlsoap.org/soap/envelope/";
        softly.assertThat(extractXPath(response, "local-name(/*)"))
                .as("Root element must be 'Envelope'").isEqualTo("Envelope");
        softly.assertThat(extractXPath(response, "namespace-uri(/*)"))
                .as("Envelope must declare SOAP 1.1 namespace").isEqualTo(soap11Uri);
    }

     // ═══════════════════════════════════════════════════════════════════════════
    // Request helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sends a SOAP request to XDS endpoint and returns the response body.
     */
   private String sendXdsRequest(String xml, String forwardedPort,
        SoftAssertions softly) {

    HttpHeaders headers = new HttpHeaders();

    headers.setContentType(MediaType.TEXT_XML);

    headers.setAccept(List.of(MediaType.TEXT_XML, MediaType.APPLICATION_XML));

    headers.add("SOAPAction",
            "urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b");

    headers.set(Constants.REQ_X_SERVER_IP, "127.0.0.1");
    headers.set(Constants.REQ_X_FORWARDED_PORT, forwardedPort);

    // IMPORTANT: helps forwarder pick correct routing context
    headers.add("x-request-source", "XDS");

    ResponseEntity<String> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/xds/XDSbRepositoryWS",
            new HttpEntity<>(xml, headers),
            String.class);

    softly.assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    return response.getBody();
}

}
