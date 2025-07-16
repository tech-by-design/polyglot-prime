package org.techbd.ingest.service.handler;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;

import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MllpHandlerTest {

    @Mock
    private MessageProcessorService processorService;

    @InjectMocks
    private MllpHandler mllpHandler;

    private RequestContext context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        context = new RequestContext(
                Map.of("User-Agent", "JUnit"),
                "/hl7",
                "testTenant",
                "interaction123",
                ZonedDateTime.now(),
                "1716899999999",
                "test.hl7",
                123L,
                "objectKey",
                "metadataKey",
                "s3://bucket/hl7",
                "JUnit-Agent",
                "http://localhost/hl7",
                "",
                "HTTP/1.1",
                "127.0.0.1",
                "192.168.1.1",
                "192.168.1.1",
                "192.168.1.2",
                "8888"
        );

        mllpHandler = new MllpHandler(processorService);
    }

    @Test
    void testCanHandle_withValidHL7Message() {
        String hl7Message = "MSH|^~\\&|...";
        assertTrue(mllpHandler.canHandle(hl7Message));
    }

    @Test
    void testCanHandle_withInvalidMessage() {
        String nonHl7Message = "ABC|123";
        assertFalse(mllpHandler.canHandle(nonHl7Message));

        Object notAString = 42;
        assertFalse(mllpHandler.canHandle(notAString));

        assertFalse(mllpHandler.canHandle(null));
    }

    @Test
    void testHandleAndProcess_delegatesToProcessorService() {
        String hl7Payload = "MSH|^~\\&|Test";
        Map<String, String> expectedResult = Map.of("status", "OK");

        when(processorService.processMessage(context, hl7Payload)).thenReturn(expectedResult);

        Map<String, String> result = mllpHandler.handleAndProcess(hl7Payload, context);

        assertEquals(expectedResult, result);
        verify(processorService, times(1)).processMessage(context, hl7Payload);
    }
}