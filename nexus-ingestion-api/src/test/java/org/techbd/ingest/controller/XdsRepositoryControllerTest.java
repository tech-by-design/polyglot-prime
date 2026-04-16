package org.techbd.ingest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        void handleXdsRequest_withEmptyBody_shouldForwardEmptyPayload() throws Exception {

                String interactionId = "test-interaction-id";

                when(request.getAttribute(Constants.INTERACTION_ID)).thenReturn(interactionId);
                when(request.getInputStream()).thenReturn(new DelegatingServletInputStream(
                                new ByteArrayInputStream(new byte[0])));

                ResponseEntity<String> expectedResponse = ResponseEntity.ok("EMPTY");
                when(forwarder.forward(eq(request), eq(new byte[0]), eq(interactionId)))
                                .thenReturn(expectedResponse);

                ResponseEntity<String> response = controller.handleXdsRequest(
                                Map.of(),
                                request);

                assertEquals(expectedResponse, response);

                verify(forwarder).forward(eq(request), eq(new byte[0]), eq(interactionId));
                verify(templateLogger).warn("Empty XDS SOAP request. interactionId={}", interactionId);
        }

        @Test
        void getMessageSource_shouldReturnHttpIngest() {
                assertEquals(MessageSourceType.HTTP_INGEST, controller.getMessageSource());
        }

}
