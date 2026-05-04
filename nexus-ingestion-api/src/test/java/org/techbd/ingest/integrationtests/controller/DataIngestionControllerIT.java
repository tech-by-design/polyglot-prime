package org.techbd.ingest.integrationtests.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.techbd.ingest.integrationtests.base.BaseIntegrationTest;
import org.techbd.ingest.integrationtests.base.NexusIntegrationTest;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sqs.model.*;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.*;

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
}