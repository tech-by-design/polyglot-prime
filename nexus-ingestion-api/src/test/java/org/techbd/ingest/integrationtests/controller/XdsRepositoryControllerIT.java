package org.techbd.ingest.integrationtests.controller;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.techbd.ingest.integrationtests.base.BaseIntegrationTest;
import org.techbd.ingest.integrationtests.base.NexusIntegrationTest;
import org.techbd.ingest.service.SoapForwarderService;
import org.techbd.ingest.commons.Constants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.http.*;

@NexusIntegrationTest
@Tag("integration")
class XdsRepositoryControllerIT extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @MockBean
    private SoapForwarderService forwarder;

    private static final String BASE_URL = "/xds/XDSbRepositoryWS";

    @Test
    void xdsRequest_shouldForwardSoapRequest() {

        when(forwarder.forward(any(), any(byte[].class), any()))
                .thenReturn(ResponseEntity.ok("SUCCESS"));

        String soapBody = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                    <soapenv:Body>
                        <test>data</test>
                    </soapenv:Body>
                </soapenv:Envelope>
                """;

        ResponseEntity<String> response = post(soapBody);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("SUCCESS");

        verify(forwarder).forward(any(), any(byte[].class), any());
    }

    @Test
    void xdsRequest_emptyBody_shouldForwardEmptyPayload() {

        when(forwarder.forward(any(), eq(new byte[0]), any()))
                .thenReturn(ResponseEntity.ok("EMPTY_OK"));

        ResponseEntity<String> response = post("");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("EMPTY_OK");

        verify(forwarder).forward(any(), eq(new byte[0]), any());
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        headers.set(Constants.REQ_X_FORWARDED_PORT, "9050");
        return headers;
    }

    private ResponseEntity<String> post(String body) {
        HttpEntity<String> request = new HttpEntity<>(body, defaultHeaders());
        return restTemplate.postForEntity(
                "http://localhost:" + port + BASE_URL,
                request,
                String.class);
    }
}
