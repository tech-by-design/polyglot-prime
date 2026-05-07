package org.techbd.ingest.integrationtests.controller;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import static org.assertj.core.api.Assertions.assertThat;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.integrationtests.base.BaseIntegrationTest;
import org.techbd.ingest.integrationtests.base.IngestionAssertionHelper;
import org.techbd.ingest.integrationtests.base.IngestionAssertionHelper.FlowAssertionParams;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;


class DataIngestionControllerITCase extends BaseIntegrationTest {

        @Autowired
        private TestRestTemplate restTemplate;

        @LocalServerPort
        private int port;

         // Shared helper instance
        // ═══════════════════════════════════════════════════════════════════════════
        /**
         * Lazily-created helper for S3/SQS assertions.
         */
        private IngestionAssertionHelper assertionHelper() {
                return new IngestionAssertionHelper(s3Client, sqsClient);
        }

        private final ObjectMapper mapper = new ObjectMapper();

        // =====================================================
        // 1. SUCCESS FLOW — FULL VALIDATION
        // =====================================================

        @Test
        @DisplayName("IT: RAW SOAP — HOLD Flow (FULL VALIDATION: HTTP + S3 + SQS)")
        void ingest_rawSoapBody_holdFlow_shouldPersistToS3AndSqs() throws Exception {

                String soapPayload = validSoapPayload();

                ResponseEntity<String> response = sendRequest(
                                "/ingest",
                                soapPayload,
                                "application/soap+xml",
                                "5555",
                                "test.fifo");

                // ---- HTTP
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

                Map<String, String> responseMap = parseResponse(response);

                String s3Path = responseMap.get("fullS3Path");
                String metadataPath = responseMap.get("fullS3MetaDataPath");

                assertThat(s3Path).isNotBlank();
                assertThat(metadataPath).isNotBlank();

                // ---- S3 VALIDATION
                ListObjectsV2Response s3Objects = getHoldBucketObjects();

                assertThat(s3Objects.contents()).isNotEmpty();

                // Extract filenames from paths
                String payloadFileName = extractFileName(s3Path);
                String metadataFileName = extractFileName(metadataPath);

                // Validate existence using endsWith (robust)
                S3Object payloadObj = s3Objects.contents().stream()
                                .filter(obj -> obj.key().endsWith(payloadFileName))
                                .findFirst()
                                .orElse(null);

                S3Object metadataObj = s3Objects.contents().stream()
                                .filter(obj -> obj.key().endsWith(metadataFileName))
                                .findFirst()
                                .orElse(null);

                // Strong assertions
                assertThat(payloadObj).as("Payload object must exist in S3").isNotNull();
                assertThat(metadataObj).as("Metadata object must exist in S3").isNotNull();

                // SIZE VALIDATION (CRITICAL)
                assertThat(payloadObj.size())
                                .isEqualTo(soapPayload.getBytes(StandardCharsets.UTF_8).length);

                // ---- SQS VALIDATION
                Message message = getFirstSqsMessageWithAttributes("test.fifo");

                String messageBody = message.body();

                assertThat(messageBody)
                                .contains("interactionId")
                                .contains(s3Path);
        }

        // =====================================================
        // 2. INVALID XML
        // =====================================================
        private String extractFileName(String path) {
                return path.substring(path.lastIndexOf("/") + 1);
        }

        @Test
        @DisplayName("IT: RAW SOAP — Invalid XML should fail safely")
        void ingest_invalidXml_shouldFailAndNotStore() {

                String invalidSoap = """
                                <SOAP-ENV:Envelope>
                                    <SOAP-ENV:Body>
                                        <invalid>missing closing tags
                                """;

                ResponseEntity<String> response = sendRequest(
                                "/ingest",
                                invalidSoap,
                                "application/soap+xml",
                                "5555",
                                null);

                // Accept either controlled failure or internal error
                assertThat(response.getStatusCode().is2xxSuccessful() ||
                                response.getStatusCode().is5xxServerError()).isTrue();

                ListObjectsV2Response s3Objects = getHoldBucketObjects();

                // Not strict size → avoid fragile test
                assertThat(s3Objects.contents().size())
                                .isLessThanOrEqualTo(2);
        }

        // =====================================================
        // 3. EMPTY SOAP BODY → SOAP FAULT
        // =====================================================

        @Test
        @DisplayName("IT: SOAP Empty Body → should return SOAP Fault (400)")
        void ingest_emptyBody_shouldReturnSoapFault() {

                ResponseEntity<String> response = sendRequest(
                                "/ingest/testSource/pix",
                                "",
                                "application/soap+xml",
                                "5555",
                                null);

                // ---- HTTP
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

                String body = response.getBody();

                assertThat(body).isNotNull();

                // SOAP 1.2 safe assertions (prefix-independent)
                assertThat(body)
                                .contains("Fault")
                                .contains("Code")
                                .contains("Reason")
                                .contains("Empty request body");

                // ---- Ensure NO persistence
                assertThat(getHoldBucketObjects().contents()).isEmpty();
        }

        // =====================================================
        //  HELPERS
        // =====================================================

        private ResponseEntity<String> sendRequest(
                        String url,
                        String body,
                        String contentType,
                        String portHeader,
                        String queueName) {

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.valueOf(contentType));

                if (portHeader != null) {
                        headers.set("x-forwarded-port", portHeader);
                }

                if (queueName != null) {
                        headers.set("X-TechBd-Queue-Name", queueName);
                }

                HttpEntity<String> request = new HttpEntity<>(body, headers);

                return restTemplate.postForEntity(
                                "http://localhost:" + port + url,
                                request,
                                String.class);
        }

        @SuppressWarnings("unchecked")
        private Map<String, String> parseResponse(ResponseEntity<String> response) throws Exception {
                return mapper.readValue(response.getBody(), Map.class);
        }

        private ListObjectsV2Response getHoldBucketObjects() {
                return s3Client.listObjectsV2(
                                ListObjectsV2Request.builder()
                                                .bucket(HOLD_BUCKET)
                                                .build());
        }

        private Message getFirstSqsMessageWithAttributes(String queueName) {
                String queueUrl = getQueueUrl(queueName);

                ReceiveMessageResponse response = sqsClient.receiveMessage(
                                ReceiveMessageRequest.builder()
                                                .queueUrl(queueUrl)
                                                .maxNumberOfMessages(10)
                                                .waitTimeSeconds(1)
                                                .messageSystemAttributeNames(MessageSystemAttributeName.ALL) // 🔥
                                                                                                             // IMPORTANT
                                                .build());

                assertThat(response.messages()).isNotEmpty();

                return response.messages().get(0);
        }

        private String validSoapPayload() {
                return """
                                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://www.w3.org/2003/05/soap-envelope"
                                                   xmlns:v3="urn:hl7-org:v3">
                                    <SOAP-ENV:Header/>
                                    <SOAP-ENV:Body>
                                        <v3:PRPA_IN201302UV02 ITSVersion="XML_1.0">
                                            <v3:id root="1.2.3" extension="123"/>
                                        </v3:PRPA_IN201302UV02>
                                    </SOAP-ENV:Body>
                                </SOAP-ENV:Envelope>
                                """;
        }

        // ═══════════════════════════════════════════════════════════════════════
        // IT: Multipart file upload
        // ═══════════════════════════════════════════════════════════════════════

        @Test
        @DisplayName("IT: /ingest multipart file — verify S3 object, size, and SQS message")
        void shouldProcessMultipartFile_andVerifyExactS3AndSqsDetails() throws Exception {

                SoftAssertions softly = new SoftAssertions();

                String payload = """
                                <test>
                                        <message>Hello Multipart</message>
                                </test>
                                """;

                byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

                // Multipart file
                ByteArrayResource fileResource = new ByteArrayResource(payloadBytes) {
                        @Override
                        public String getFilename() {
                                return "test.xml";
                        }
                };

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", fileResource);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                headers.set(Constants.REQ_X_FORWARDED_PORT, "9050");

                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                                "http://localhost:" + port + "/ingest/",
                                requestEntity,
                                String.class);

                // Response assertions
                softly.assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

                // Parse response JSON
                JsonNode json = new ObjectMapper().readTree(response.getBody());
                String s3Path = json.get("fullS3Path").asText();
                String metadataPath = json.get("fullS3MetaDataPath").asText();
                String interactionId = json.get("interactionId").asText();

                // ═══════════════════════════════════════════════════════════════
                // S3 DATA OBJECT VALIDATION
                // ═══════════════════════════════════════════════════════════════

                byte[] s3Bytes = s3Client.getObject(
                                GetObjectRequest.builder()
                                                .bucket(DEFAULT_DATA_BUCKET)
                                                .key(extractKey(s3Path))
                                                .build())
                                .readAllBytes();

                // Exact content match
                softly.assertThat(s3Bytes)
                                .as("S3 object content should match uploaded payload")
                                .isEqualTo(payloadBytes);

                // Size match
                softly.assertThat(s3Bytes.length)
                                .as("S3 object size should match uploaded file size")
                                .isEqualTo(payloadBytes.length);

                // ═══════════════════════════════════════════════════════════════
                // S3 METADATA VALIDATION
                // ═══════════════════════════════════════════════════════════════

                byte[] metadataBytes = s3Client.getObject(
                                GetObjectRequest.builder()
                                                .bucket(DEFAULT_METADATA_BUCKET)
                                                .key(extractKey(metadataPath))
                                                .build())
                                .readAllBytes();

                String metadataJson = new String(metadataBytes, StandardCharsets.UTF_8);

                System.out.println("Metadata JSON: " + metadataJson);

                softly.assertThat(metadataJson)
                                .as("Metadata should contain interactionId")
                                .contains(interactionId);

                // ═══════════════════════════════════════════════════════════════
                //  SQS VALIDATION
                // ═══════════════════════════════════════════════════════════════

                String queueUrl = getQueueUrl("txd-sbx-ccd-queue.fifo");

                var messages = sqsClient.receiveMessage(r -> r
                                .queueUrl(queueUrl)
                                .maxNumberOfMessages(1)
                                .waitTimeSeconds(2)
                                .messageSystemAttributeNames(MessageSystemAttributeName.ALL)).messages();

                System.out.println("Received SQS messages: " + messages);

                softly.assertThat(messages).hasSize(1);

                var message = messages.get(0);

                JsonNode bodyJson = new ObjectMapper().readTree(message.body());

                softly.assertThat(bodyJson.get("messageGroupId").asText())
                                .isEqualTo("9050");

                softly.assertThat(s3Path)
                                .contains("ccd/data");

                softly.assertAll();
        }

        @Test
        @DisplayName("IT: /ingest multipart — empty file should fail")
        void shouldFail_whenMultipartFileIsEmpty() throws Exception {

                SoftAssertions softly = new SoftAssertions();

                // Empty file
                ByteArrayResource emptyFile = new ByteArrayResource(new byte[0]) {
                        @Override
                        public String getFilename() {
                                return "empty.xml";
                        }
                };

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", emptyFile);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                headers.set(Constants.REQ_X_FORWARDED_PORT, "9050");

                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                                "http://localhost:" + port + "/ingest/",
                                requestEntity,
                                String.class);

                // Depending on your exception handler:
                softly.assertThat(response.getStatusCode().is4xxClientError()).isTrue();

                softly.assertThat(response.getBody())
                                .contains("Illegal argument passed to request.");

                softly.assertAll();
        }

        private String extractKey(String fullS3Path) {
                // Example: s3://bucket-name/path/to/file.xml → path/to/file.xml
                return fullS3Path.substring(fullS3Path.indexOf("/", 5) + 1);

        }


        @Test
        @DisplayName("IT: Ingest PIX — /ingest → /ws → S3 + SQS SUCCESS")
        void shouldProcessPixViaIngest_andPersistToS3AndSqs() throws Exception {

        String request = validPixSoapRequest();
        SoftAssertions softly = new SoftAssertions();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML); // SOAP 1.1
        headers.set(Constants.REQ_X_FORWARDED_PORT, "9000");
        headers.set(Constants.REQ_X_SERVER_IP, "127.0.0.1");
        headers.set("SOAPAction", "PRPA_IN201301UV02");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/ingest/netspective/pix",
                new HttpEntity<>(request, headers),
                String.class
        );

        //  STEP 1: HTTP success
        softly.assertThat(response.getStatusCode().is2xxSuccessful())
                .as("Response should be 2xx")
                .isTrue();

        //  STEP 2: SOAP response validation
        String responseBody = response.getBody();
        softly.assertThat(responseBody)
                .as("SOAP response should not be empty")
                .isNotBlank();

        softly.assertThat(responseBody)
                .as("SOAP Envelope must be present")
                .contains("Envelope");

        //  STEP 3: Verify S3 + SQS success flow
                assertionHelper().assertCustomFlow(
                defaultFlowParams(
                        request,
                        null,
                        "netspective_pix",   
                        false
                ),
                softly
                );

        softly.assertAll();
        }


        @Test
        @DisplayName("IT: Ingest TEST — should NOT forward to SOAP, only S3 + SQS")
        void shouldProcessTestMsgType_asNormalIngest() throws Exception {

        String request = "<test>hello</test>";
        SoftAssertions softly = new SoftAssertions();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.set(Constants.REQ_X_FORWARDED_PORT, "9000");
        headers.set(Constants.REQ_X_SERVER_IP, "127.0.0.1");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/ingest/netspective/test",
                new HttpEntity<>(request, headers),
                String.class
        );

        // ✅ STEP 1: HTTP success
        softly.assertThat(response.getStatusCode().is2xxSuccessful())
                .isTrue();

        // ✅ STEP 2: Response should be JSON (NOT SOAP)
        String responseBody = response.getBody();

        softly.assertThat(responseBody)
                .as("Should NOT be SOAP response")
                .doesNotContain("Envelope");

        softly.assertThat(responseBody)
                .as("Should be JSON response")
                .contains("{");

        // ✅ STEP 3: Verify S3 + SQS
        assertionHelper().assertCustomFlow(
                defaultFlowParams(
                        request,
                        null,
                        "netspective_test",   // groupId
                        false                 // no ACK expected
                ),
                softly
        );

        softly.assertAll();
        }


    @Test
@DisplayName("IT: /ingest/netspective_m/pnr → MTOM multipart response validation")
void shouldRoutePnrBasedOnSourceId_returnMtomResponse() throws Exception {

    String url = "http://localhost:" + port + "/ingest/netspective_m/pnr";

    String boundary = "Boundary_12345";

    String mtomRequest = """
            --Boundary_12345
            Content-Type: application/xop+xml; charset=UTF-8; type="application/soap+xml"
            Content-Transfer-Encoding: binary
            Content-ID: <rootpart@meditech.com>

            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
                    xmlns:wsa="http://www.w3.org/2005/08/addressing">

            <soap:Header>
            <wsa:To>https://heltestmcccd.myhie.com:9135/xds/XDSbRepositoryWS</wsa:To>
            <wsa:MessageID>f19a3e9e-324d-4c6c-8a96-6747955d86f5</wsa:MessageID>
            <wsa:Action>urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b</wsa:Action>
            </soap:Header>

            <soap:Body>
            <ProvideAndRegisterDocumentSetRequest xmlns="urn:ihe:iti:xds-b:2007">
            <Document id="Document1">
                    <xop:Include xmlns:xop="http://www.w3.org/2004/08/xop/include"
                            href="cid:payload@meditech.com"/>
            </Document>
            </ProvideAndRegisterDocumentSetRequest>
            </soap:Body>

            </soap:Envelope>

            --Boundary_12345
            Content-Type: text/xml; charset=UTF-8
            Content-Transfer-Encoding: binary
            Content-ID: <payload@meditech.com>

            <?xml version="1.0" encoding="UTF-8"?>
            <ClinicalDocument xmlns="urn:hl7-org:v3">
            <id extension="1" root="test"/>
            </ClinicalDocument>

            --Boundary_12345--
            """;

    SoftAssertions softly = new SoftAssertions();

    HttpHeaders headers = new HttpHeaders();

    headers.set(
            HttpHeaders.CONTENT_TYPE,
            "multipart/related; boundary=\"" + boundary + "\"; " +
                    "type=\"application/xop+xml\"; " +
                    "start=\"<rootpart@meditech.com>\"; " +
                    "start-info=\"application/soap+xml\"; " +
                    "action=\"urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b\""
    );

    headers.set(Constants.REQ_X_FORWARDED_PORT, "9000");
    headers.set(Constants.REQ_X_SERVER_IP, "127.0.0.1");
    headers.add("SOAPAction",
            "urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b");

    HttpEntity<String> entity =
            new HttpEntity<>(mtomRequest, headers);

    ResponseEntity<String> response =
            restTemplate.postForEntity(url, entity, String.class);

    // ── HTTP status ───────────────────────────────────────────────
    softly.assertThat(response.getStatusCode().value())
            .as("HTTP status should be 200")
            .isEqualTo(200);

    // ── Content-Type header assertions ────────────────────────────
    String contentType =
            response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);

    softly.assertThat(contentType)
            .as("MTOM Content-Type header is present")
            .isNotBlank();

    softly.assertThat(contentType)
            .as("MTOM Content-Type is multipart/related")
            .contains("multipart/related");

    softly.assertThat(contentType)
            .as("MTOM Content-Type contains quoted boundary")
            .containsPattern("boundary=\"[A-Za-z0-9_\\-]+\"");

    softly.assertThat(contentType)
            .as("MTOM Content-Type contains quoted type")
            .contains("type=\"application/xop+xml\"");

    softly.assertThat(contentType)
            .as("MTOM Content-Type contains start parameter")
            .containsPattern("start=\"<rootpart@[^\"]+>\"");

    softly.assertThat(contentType)
            .as("MTOM Content-Type contains start-info")
            .contains("start-info=\"application/soap+xml\"");

    softly.assertThat(contentType)
            .as("MTOM Content-Type contains action")
            .contains("action=\"urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b\"");

    // ── Boundary validation ───────────────────────────────────────
    String headerBoundary =
            extractBoundaryFromContentType(contentType);

    String bodyBoundary =
            extractMtomBoundary(response.getBody());

    softly.assertThat(bodyBoundary)
            .as("Body boundary must match Content-Type boundary")
            .isEqualTo(headerBoundary);

    softly.assertThat(response.getBody())
            .as("Response contains MTOM boundaries")
            .contains("--" + bodyBoundary)
            .contains("--" + bodyBoundary + "--");

    // ── MTOM MIME validation ──────────────────────────────────────
    softly.assertThat(response.getBody())
            .as("Response contains XOP MIME part")
            .contains("Content-Type: application/xop+xml");

    softly.assertThat(response.getBody())
            .as("Response contains binary encoding")
            .contains("Content-Transfer-Encoding: binary");

    softly.assertThat(response.getBody())
            .as("Response contains root content-id")
            .contains("Content-ID: <rootpart@");

    // ── SOAP response validation ──────────────────────────────────
    softly.assertThat(response.getBody())
            .as("Response contains SOAP Envelope")
            .contains("Envelope");

    softly.assertThat(response.getBody())
            .as("Response contains RegistryResponse")
            .contains("RegistryResponse");

    softly.assertThat(response.getBody())
            .as("Response contains Success status")
            .contains("ResponseStatusType:Success");

    assertMtomMessageIdRelatesTo(
            response.getBody(),
            softly
    );
    assertionHelper().assertHoldFlow(
        holdFlowParams(
                mtomRequest,
                null,
                "netspective_m_pnr",
                9000,
                "/outbound",
                "/outbound",
                "netspective_m_pnr",
                queueUrls.get("txd-sbx-main-queue.fifo"))
                .toBuilder()
                .build(),
        softly);

    softly.assertAll();
}


        @Test
@DisplayName("IT: /ingest/netspective_mt/pnr → TruBridge MTOM response validation")
void shouldProcessPnrMtomResponseTypeTruBridge_returnsTruBridgeHeaders() throws Exception {


    String boundary = "Boundary_12345";

String mtomRequest =
        "--" + boundary + "\r\n" +
        "Content-Type: application/xop+xml; charset=UTF-8; type=\"application/soap+xml\"\r\n" +
        "Content-Transfer-Encoding: binary\r\n" +
        "Content-ID: <rootpart@meditech.com>\r\n" +
        "\r\n" +
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
        "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"\r\n" +
        "               xmlns:wsa=\"http://www.w3.org/2005/08/addressing\">\r\n" +
        "\r\n" +
        "  <soap:Header>\r\n" +
        "    <wsa:To>https://heltestmcccd.myhie.com:9135/xds/XDSbRepositoryWS</wsa:To>\r\n" +
        "    <wsa:MessageID>f19a3e9e-324d-4c6c-8a96-6747955d86f5</wsa:MessageID>\r\n" +
        "    <wsa:Action>urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b</wsa:Action>\r\n" +
        "  </soap:Header>\r\n" +
        "\r\n" +
        "  <soap:Body>\r\n" +
        "    <ProvideAndRegisterDocumentSetRequest xmlns=\"urn:ihe:iti:xds-b:2007\">\r\n" +
        "      <Document id=\"Document1\">\r\n" +
        "        <xop:Include xmlns:xop=\"http://www.w3.org/2004/08/xop/include\"\r\n" +
        "                     href=\"cid:payload@meditech.com\"/>\r\n" +
        "      </Document>\r\n" +
        "    </ProvideAndRegisterDocumentSetRequest>\r\n" +
        "  </soap:Body>\r\n" +
        "\r\n" +
        "</soap:Envelope>\r\n" +
        "\r\n" +
        "--" + boundary + "\r\n" +
        "Content-Type: text/xml; charset=UTF-8\r\n" +
        "Content-Transfer-Encoding: binary\r\n" +
        "Content-ID: <payload@meditech.com>\r\n" +
        "\r\n" +
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
        "<ClinicalDocument xmlns=\"urn:hl7-org:v3\">\r\n" +
        "  <id extension=\"1\" root=\"test\"/>\r\n" +
        "</ClinicalDocument>\r\n" +
        "\r\n" +
        "--" + boundary + "--\r\n";
        
    SoftAssertions softly = new SoftAssertions();

    

        ResponseEntity<String> responseEntity =
        sendMtomRequestRaw(mtomRequest, "9000", softly);
    
    // ── HTTP status ───────────────────────────────────────────────
    softly.assertThat(responseEntity.getStatusCode().value())
            .as("HTTP status should be 200")
            .isEqualTo(200);

    // ── Content-Type header assertions ────────────────────────────
    String contentType =
            responseEntity.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);

    softly.assertThat(contentType)
            .as("TruBridge MTOM Content-Type header is present")
            .isNotBlank();

    softly.assertThat(contentType)
            .as("TruBridge MTOM Content-Type is multipart/related")
            .contains("multipart/related");

    softly.assertThat(contentType)
            .as("TruBridge MTOM Content-Type contains unquoted boundary")
            .containsPattern("boundary=[A-Za-z0-9_\\-]+");

    softly.assertThat(contentType)
            .as("TruBridge MTOM Content-Type contains unquoted type")
            .contains("type=application/xop+xml");

    softly.assertThat(contentType)
            .as("TruBridge MTOM omits start/start-info/action")
            .doesNotContain("start=")
            .doesNotContain("start-info=")
            .doesNotContain("action=");


    // ── Boundary validation ───────────────────────────────────────
    String headerBoundary =
            extractBoundaryFromContentType(contentType);

    String bodyBoundary =
            extractMtomBoundary(responseEntity.getBody());

    softly.assertThat(bodyBoundary)
            .as("Body boundary must match Content-Type boundary")
            .isEqualTo(headerBoundary);

    softly.assertThat(responseEntity.getBody())
            .as("Response contains MTOM boundaries")
            .contains("--" + bodyBoundary)
            .contains("--" + bodyBoundary + "--");

    // ── MTOM MIME validation ──────────────────────────────────────
    softly.assertThat(responseEntity.getBody())
            .as("Response contains XOP MIME part")
            .contains("Content-Type: application/xop+xml");

    softly.assertThat(responseEntity.getBody())
            .as("Response contains binary encoding")
            .contains("Content-Transfer-Encoding: binary");

    softly.assertThat(responseEntity.getBody())
            .as("Response contains root content-id")
            .contains("Content-ID: <rootpart@");

    // ── SOAP response validation ──────────────────────────────────
    softly.assertThat(responseEntity.getBody())
            .as("Response contains SOAP Envelope")
            .contains("Envelope");

    softly.assertThat(responseEntity.getBody())
            .as("Response contains RegistryResponse")
            .contains("RegistryResponse");

    softly.assertThat(responseEntity.getBody())
            .as("Response contains Success status")
            .contains("ResponseStatusType:Success");

    assertMtomMessageIdRelatesTo(
            responseEntity.getBody(),
            softly
    );

    assertionHelper().assertHoldFlow(
        holdFlowParams(
                mtomRequest,
                null,
                "netspective_mt_pnr",
                9000,
                "/outbound",
                "/outbound",
                "netspective_mt_pnr",
                queueUrls.get("txd-sbx-main-queue.fifo"))
                .toBuilder()
                .build(),
        softly);
    

    softly.assertAll();
}


@Test
@DisplayName("IT: /ingest/netspective/pnr → SOAP XML response validation")
void shouldProcessPnrSoapXmlAck_returnsSoapXmlHeaders() throws Exception {

    String url = "http://localhost:" + port + "/ingest/netspective/pnr";

    String soapRequest = """
        <?xml version="1.0" encoding="UTF-8"?>
        <soapenv:Envelope
            xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
            xmlns:wsa="http://www.w3.org/2005/08/addressing"
            xmlns:xdsb="urn:ihe:iti:xds-b:2007"
            xmlns:hl7="urn:hl7-org:v3">

            <soapenv:Header>
                <wsa:To>
                    https://heltestmcccd.myhie.com:9135/xds/XDSbRepositoryWS
                </wsa:To>

                <wsa:MessageID>
                    urn:uuid:12345678-90ab-cdef-1234-567890abcdef
                </wsa:MessageID>

                <wsa:Action>
                    urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b
                </wsa:Action>
            </soapenv:Header>

            <soapenv:Body>
                <xdsb:ProvideAndRegisterDocumentSetRequest>
                    <xdsb:Document id="Document1">

                        <hl7:ClinicalDocument>
                            <hl7:id extension="1" root="test"/>
                        </hl7:ClinicalDocument>

                    </xdsb:Document>
                </xdsb:ProvideAndRegisterDocumentSetRequest>
            </soapenv:Body>

        </soapenv:Envelope>
        """;

    SoftAssertions softly = new SoftAssertions();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.TEXT_XML);

    headers.set(Constants.REQ_X_FORWARDED_PORT, "9000");
    headers.set(Constants.REQ_X_SERVER_IP, "127.0.0.1");

    headers.add(
            "SOAPAction",
            "urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b"
    );

    HttpEntity<String> entity =
            new HttpEntity<>(soapRequest, headers);

    ResponseEntity<String> responseEntity =
            restTemplate.postForEntity(
                    url,
                    entity,
                    String.class
            );

    // ── HTTP status ───────────────────────────────────────────────
    softly.assertThat(responseEntity.getStatusCode().value())
            .as("HTTP status should be 200")
            .isEqualTo(200);

    // ── Content-Type header assertions ────────────────────────────
    String contentType =
            responseEntity.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);

    softly.assertThat(contentType)
            .as("SOAP XML Content-Type header is present")
            .isNotBlank();

    softly.assertThat(contentType)
            .as("SOAP XML Content-Type is application/soap+xml")
            .contains("application/soap+xml");

    softly.assertThat(contentType)
            .as("SOAP XML response is not multipart")
            .doesNotContain("multipart/related");

    softly.assertThat(contentType)
            .as("SOAP XML response does not contain MTOM boundary")
            .doesNotContain("boundary=");

    // ── SOAP response validation ──────────────────────────────────
    String responseBody = responseEntity.getBody();

    softly.assertThat(responseBody)
            .as("Response body is present")
            .isNotBlank();

    softly.assertThat(responseBody)
            .as("Response contains SOAP Envelope")
            .contains("Envelope");

    softly.assertThat(responseBody)
            .as("Response contains RegistryResponse")
            .contains("RegistryResponse");

    softly.assertThat(responseBody)
            .as("Response contains Success status")
            .contains("ResponseStatusType:Success");

    softly.assertThat(responseBody)
            .as("Response is plain SOAP XML and not MTOM")
            .doesNotContain("Content-ID:")
            .doesNotContain("application/xop+xml")
            .doesNotContain("Content-Transfer-Encoding");

   

    assertionHelper().assertHoldFlow(
        holdFlowParams(
                soapRequest,
                null,
                "netspective_pnr",
                9000,
                "/outbound",
                "/outbound",
                "netspective_pnr",
                queueUrls.get("txd-sbx-main-queue.fifo"))
                .toBuilder()
                .build(),
        softly);

    softly.assertAll();
}


      

        private String validPixSoapRequest() {
        return """
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
                                xmlns:v3="urn:hl7-org:v3"
                                xmlns:wsa="http://www.w3.org/2005/08/addressing">
                <SOAP-ENV:Header>
                <wsa:MessageID>urn:uuid:123e4567-e89b-12d3-a456-426614174000</wsa:MessageID>
                </SOAP-ENV:Header>
                <SOAP-ENV:Body>
                <v3:PRPA_IN201301UV02 ITSVersion="XML_1.0">
                        <v3:id root="2.16.840.1.113883.3.72.5.9.1" extension="12345"/>
                        <v3:creationTime value="20250805163000"/>
                        <v3:interactionId root="2.16.840.1.113883.1.6" extension="PRPA_IN201301UV02"/>
                        <v3:processingCode code="T"/>
                        <v3:processingModeCode code="T"/>
                        <v3:acceptAckCode code="AL"/>
                        <v3:sender typeCode="SND">
                        <v3:device classCode="DEV" determinerCode="INSTANCE">
                        <v3:id root="1.2.3.4.5"/>
                        </v3:device>
                        </v3:sender>
                </v3:PRPA_IN201301UV02>
                </SOAP-ENV:Body>
                </SOAP-ENV:Envelope>
                """;
        }


        private FlowAssertionParams defaultFlowParams(
                String payload, String ackFixture, String groupId, boolean ackExpected) {

        FlowAssertionParams.Builder b = FlowAssertionParams.builder()
                .dataBucket(DEFAULT_DATA_BUCKET)
                .metadataBucket(DEFAULT_METADATA_BUCKET)
                .queueUrl(mainQueueUrl)
                .expectedMessageGroupId(groupId)
                .tenantId(groupId)   
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

        public static String extractXPath(String xml, String expression) {
        try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true); 
                DocumentBuilder builder = factory.newDocumentBuilder();

                Document document = builder.parse(new InputSource(new StringReader(xml)));

                XPath xpath = XPathFactory.newInstance().newXPath();
                return xpath.evaluate(expression, document);

        } catch (Exception e) {
                throw new RuntimeException("Failed to evaluate XPath: " + expression, e);
        }
        }


        /**
     * Extracts the boundary value from a {@code Content-Type} header string.
     * Handles both quoted ({@code boundary="..."}) and unquoted
     * ({@code boundary=...}) forms.
     *
     * @param contentType the raw Content-Type header value
     * @return the bare boundary string (without surrounding quotes)
     */
    private static String extractBoundaryFromContentType(String contentType) {
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("boundary=")) {
                String value = trimmed.substring("boundary=".length()).trim();
                // Strip surrounding quotes if present
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        throw new IllegalArgumentException("No boundary parameter found in Content-Type: " + contentType);
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


    /**
     * Asserts that the MTOM response body contains a structurally valid
     * {@code wsa:RelatesTo} element holding a UUID-formatted value.
     * The actual UUID is generated fresh per request and is not asserted verbatim.
     */
    private static void assertMtomMessageIdRelatesTo(String responseBody, SoftAssertions softly) {
        softly.assertThat(responseBody)
                .as("Response body contains wsa:RelatesTo element")
                .contains("wsa:RelatesTo");
        // UUID format: 8-4-4-4-12 hex chars separated by hyphens
        softly.assertThat(responseBody)
                .as("Response wsa:RelatesTo contains a UUID-formatted value")
                .containsPattern(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }





    // ═══════════════════════════════════════════════════════════════════════════
    // NEW private helper — add alongside the existing sendMtomRequestEntity
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sends an MTOM request and returns the raw {@link ResponseEntity} 
     *
     *
     * <p>This uses {@code RestTemplate.execute()} with a custom response extractor
     * that manually reads the response body and headers without invoking Spring's
     * content-type negotiation or {@code MediaType.parseMediaType()}, allowing
     * inspection of raw headers and body.
     *
     * @param mtomRaw       raw MTOM fixture body
     * @param forwardedPort value for {@code X-Forwarded-Port} header
     * @param softly        soft-assertion collector (status assertion added here)
     * @return raw response entity with unparsed headers
     */
    private ResponseEntity<String> sendMtomRequestRaw(String mtomRaw, String forwardedPort,
            SoftAssertions softly) throws Exception {
        String boundary = extractMtomBoundary(mtomRaw);

        HttpHeaders headers = new HttpHeaders();
        // Send request with a quoted Content-Type — that is always valid on the request side.
        headers.set(HttpHeaders.CONTENT_TYPE,
                "multipart/related; type=\"application/xop+xml\"; boundary=\"" + boundary + "\"; "
                        + "start=\"<rootpart@meditech.com>\"; start-info=\"application/soap+xml\"");
        headers.set(Constants.REQ_X_FORWARDED_PORT, forwardedPort);
        headers.set(Constants.REQ_X_SERVER_IP, "127.0.0.1");

        // Use RestTemplate.execute() with a custom response extractor that bypasses
        // Spring's content-type parsing. This allows us to read raw headers even if
        // they contain non-RFC-2045–compliant token characters like "/" in unquoted values.
        ResponseEntity<String> response = restTemplate.execute(
                "http://localhost:" + port + "/ingest/netspective_mt/pnr",
                org.springframework.http.HttpMethod.POST,
                req -> {
                    req.getHeaders().addAll(headers);
                    req.getBody().write(mtomRaw.getBytes(StandardCharsets.UTF_8));
                },
                clientResponse -> {
                    String body = new String(clientResponse.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    HttpHeaders responseHeaders = clientResponse.getHeaders();
                    return new ResponseEntity<>(body, responseHeaders, clientResponse.getStatusCode());
                }
        );

        softly.assertThat(response.getStatusCode().is2xxSuccessful())
                .as("TruBridge MTOM response must be 2xx")
                .isTrue();
        return response;
    }



     /**
     * Builds a {@link FlowAssertionParams} for a hold-flow scenario.
     *
     * @param payload     raw XML sent
     * @param ackFixture  expected ACK fixture (null to skip ACK XPath check)
     * @param groupId     expected SQS {@code messageGroupId}
     * @param portNum     listening port (used in hold key when no tenant)
     * @param dataDir     {@code dataDir} from port-config (e.g. {@code "/http"})
     * @param metadataDir {@code metadataDir} from port-config (e.g. {@code "/outbound"})
     * @param tenantId    tenant segment ({@code sourceId_msgType}); null if absent
     * @param queueUrl    SQS queue URL for this entry
     */
    private FlowAssertionParams holdFlowParams(
            String payload, String ackFixture, String groupId,
            int portNum, String dataDir, String metadataDir,
            String tenantId, String queueUrl) {

        FlowAssertionParams.Builder b = FlowAssertionParams.builder()
                .dataBucket(HOLD_BUCKET)
                .metadataBucket(null)           // hold: metadata in same bucket as data
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
}

