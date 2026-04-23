package org.techbd.ingest.integrationtests.controller;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.integrationtests.base.BaseIntegrationTest;
import org.techbd.ingest.integrationtests.base.NexusIntegrationTest;
import org.techbd.ingest.service.MessageProcessorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

/**
 * Integration tests for DataIngestionController.
 * Tests HTTP endpoints, content type handling, and service interactions.
 */
@NexusIntegrationTest
@Tag("integration")
class DataIngestionControllerIT extends BaseIntegrationTest {

        @Autowired
        private TestRestTemplate restTemplate;

        @LocalServerPort
        private int port;

        @MockBean
        private MessageProcessorService messageProcessorService;

        private static final String BASE_URL = "/ingest/testSource/testType";

        @Test
        void ingest_rawBody_fullFlow() {
                ResponseEntity<String> response = postJson("{ \"test\": \"data\" }");
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void ingest_multipart_fullFlow() {
                ResponseEntity<String> response = postMultipart("test data");
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void ingest_shouldExecuteFailureFallback() {

                when(messageProcessorService.processMessage(any(), any(String.class)))
                                .thenThrow(new RuntimeException("Simulated failure"));

                ResponseEntity<String> response = postJson("{ \"test\": \"data\" }");

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

                verify(messageProcessorService, atLeast(2))
                                .processMessage(any(), any(String.class));
        }

        @Test
        void ingest_multipart_shouldTriggerFailureFallback() {

                when(messageProcessorService.processMessage(any(), any(MultipartFile.class)))
                                .thenThrow(new RuntimeException("Simulated failure"));

                ResponseEntity<String> response = postMultipart("test data");

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

                verify(messageProcessorService, atLeast(2))
                                .processMessage(any(), any(MultipartFile.class));
        }

        private HttpHeaders jsonHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Source-Id", "testSource");
                headers.set("X-Msg-Type", "testType");
                headers.set(Constants.REQ_X_FORWARDED_PORT, "9050");
                return headers;
        }

        private HttpHeaders multipartHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                headers.set("X-Source-Id", "testSource");
                headers.set("X-Msg-Type", "testType");
                headers.set(Constants.REQ_X_FORWARDED_PORT, "9050");
                return headers;
        }

        private ResponseEntity<String> postJson(String body) {
                HttpEntity<String> request = new HttpEntity<>(body, jsonHeaders());
                return restTemplate.postForEntity(
                                "http://localhost:" + port + BASE_URL,
                                request,
                                String.class);
        }

        private ResponseEntity<String> postMultipart(String content) {
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", new ByteArrayResource(content.getBytes()) {
                        @Override
                        public String getFilename() {
                                return "test.txt";
                        }
                });

                HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, multipartHeaders());

                return restTemplate.postForEntity(
                                "http://localhost:" + port + BASE_URL,
                                request,
                                String.class);
        }

}
