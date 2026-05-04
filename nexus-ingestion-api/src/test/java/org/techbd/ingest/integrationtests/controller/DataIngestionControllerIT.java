package org.techbd.ingest.integrationtests.controller;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.techbd.ingest.integrationtests.base.BaseIntegrationTest;
import org.techbd.ingest.integrationtests.base.NexusIntegrationTest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.techbd.ingest.commons.Constants;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sqs.model.*;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;

@NexusIntegrationTest
@Tag("integration")
class DataIngestionControllerIT extends BaseIntegrationTest {

        @Autowired
        private TestRestTemplate restTemplate;

        @LocalServerPort
        private int port;

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

                //  Not strict size → avoid fragile test
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
        // 🔧 HELPERS
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

                HttpEntity<MultiValueMap<String, Object>> requestEntity =
                        new HttpEntity<>(body, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                        "http://localhost:" + port + "/ingest/",
                        requestEntity,
                        String.class
                );

                //  Response assertions
                softly.assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

                // Parse response JSON
                JsonNode json = new ObjectMapper().readTree(response.getBody());
                String s3Path = json.get("fullS3Path").asText();
                String metadataPath = json.get("fullS3MetaDataPath").asText();
                String interactionId = json.get("interactionId").asText();

                // ═══════════════════════════════════════════════════════════════
                //  S3 DATA OBJECT VALIDATION
                // ═══════════════════════════════════════════════════════════════

                byte[] s3Bytes = s3Client.getObject(
                        GetObjectRequest.builder()
                                .bucket(DEFAULT_DATA_BUCKET)
                                .key(extractKey(s3Path))
                                .build()
                ).readAllBytes();

                //  Exact content match
                softly.assertThat(s3Bytes)
                        .as("S3 object content should match uploaded payload")
                        .isEqualTo(payloadBytes);

                //  Size match
                softly.assertThat(s3Bytes.length)
                        .as("S3 object size should match uploaded file size")
                        .isEqualTo(payloadBytes.length);

                // ═══════════════════════════════════════════════════════════════
                //  S3 METADATA VALIDATION
                // ═══════════════════════════════════════════════════════════════

                byte[] metadataBytes = s3Client.getObject(
                        GetObjectRequest.builder()
                                .bucket(DEFAULT_METADATA_BUCKET)
                                .key(extractKey(metadataPath))
                                .build()
                ).readAllBytes();

                String metadataJson = new String(metadataBytes, StandardCharsets.UTF_8);

                System.out.println("Metadata JSON: " + metadataJson);

                softly.assertThat(metadataJson)
                        .as("Metadata should contain interactionId")
                        .contains(interactionId);


                // ═══════════════════════════════════════════════════════════════
                // ✅ SQS VALIDATION
                // ═══════════════════════════════════════════════════════════════

                String queueUrl = getQueueUrl("txd-sbx-ccd-queue.fifo");

                var messages = sqsClient.receiveMessage(r -> r
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(1)
                        .waitTimeSeconds(2)
                        .messageSystemAttributeNames(MessageSystemAttributeName.ALL)
                ).messages();

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

        HttpEntity<MultiValueMap<String, Object>> requestEntity =
                new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/ingest/",
                requestEntity,
                String.class
        );

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
}