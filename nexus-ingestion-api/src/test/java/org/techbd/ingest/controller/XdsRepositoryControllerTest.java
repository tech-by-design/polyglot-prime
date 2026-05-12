package org.techbd.ingest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.service.SoapForwarderService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import org.junit.jupiter.api.Test;
import org.techbd.ingest.commons.Constants;

@ExtendWith(MockitoExtension.class)
public class XdsRepositoryControllerTest {

        @Mock
        private AppConfig appConfig;

        @Mock
        private AppLogger appLogger;

        @Mock
        private TemplateLogger templateLogger;

        @Mock
        private SoapForwarderService forwarder;

        @Mock
        private HttpServletRequest request;

        private XdsRepositoryController controller;

        @BeforeEach
        void setUp() {
                when(appLogger.getLogger(any())).thenReturn(templateLogger);

                controller = new XdsRepositoryController(
                                appConfig,
                                appLogger,
                                forwarder);
        }

        @Test
        void handleXdsRequest_withValidBody_shouldForwardWithPayload() throws Exception {

                String interactionId = "test-interaction-id";
                byte[] body = "<soap>test</soap>".getBytes();

                when(request.getAttribute(Constants.INTERACTION_ID)).thenReturn(interactionId);
                when(request.getInputStream()).thenReturn(new DelegatingServletInputStream(
                                new ByteArrayInputStream(body)));

                ResponseEntity<String> expectedResponse = ResponseEntity.ok("OK");
                when(forwarder.forward(request, body, interactionId)).thenReturn(expectedResponse);

                ResponseEntity<String> response = controller.handleXdsRequest(
                                Map.of(),
                                request);

                assertEquals(expectedResponse, response);

                verify(forwarder).forward(request, body, interactionId);
                verify(templateLogger).info("XDS request received. interactionId={}", interactionId);
                verify(templateLogger).info("Forwarding XDS SOAP request to /ws. interactionId={}", interactionId);
        }

        @Test
        void handleXdsRequest_withEmptyBody_shouldReturnSoapFault() throws Exception {

        String interactionId = "test-interaction-id";

        when(request.getAttribute(Constants.INTERACTION_ID))
                .thenReturn(interactionId);

        when(request.getInputStream())
                .thenReturn(new DelegatingServletInputStream(
                        new ByteArrayInputStream(new byte[0])));

        ResponseEntity<String> response = controller.handleXdsRequest(
                Map.of(),
                request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        assertEquals(
                "text/xml; charset=utf-8",
                response.getHeaders().getFirst("Content-Type"));

        assertTrue(response.getBody().contains("soap:Fault"));
        assertTrue(response.getBody().contains("Empty SOAP request body"));

        verify(forwarder, never())
                .forward(any(), any(), any());

        verify(templateLogger)
                .warn("Empty XDS SOAP request. interactionId={}", interactionId);
        }

        @Test
        void getMessageSource_shouldReturnHttpIngest() {
                assertEquals(MessageSourceType.HTTP_INGEST, controller.getMessageSource());
        }

}
