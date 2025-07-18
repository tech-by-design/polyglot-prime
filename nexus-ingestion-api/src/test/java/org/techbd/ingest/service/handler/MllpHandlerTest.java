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
                "8888");

        mllpHandler = new MllpHandler(processorService);
    }

    @Test
    void testCanHandle_withValidHL7Message() {
        String hl7Message = "MSH|^~\\&|SendingApp|SendingFacility|ReceivingApp|ReceivingFacility|202507171330||ADT^A01|123456|P|2.5\r"
                +
                "EVN|A01|202507171330\r" +
                "PID|1||123456^^^HospitalMRN||Doe^John^A^^Mr.||19800101|M|||123 Main St^^Metropolis^NY^10001||(123)456-7890|||S||123456789|987654321\r"
                +
                "NK1|1|Doe^Jane^A|SPO||(123)456-7890\r" +
                "PV1|1|I|2000^201^01||||004777^Smith^Jack^A|||MED|||||||004777^Smith^Jack^A||I||||||||||||||||||||||||202507171330\r";

        assertTrue(mllpHandler.canHandle(hl7Message, context));
    }

    @Test
    void testCanHandle_withInvalidMessage() {
        String nonHl7Message = "ABC|123";
        assertFalse(mllpHandler.canHandle(nonHl7Message, context));

        Object notAString = 42;
        assertFalse(mllpHandler.canHandle(notAString, context));

        assertFalse(mllpHandler.canHandle(null, context));
    }

    @Test
    void testHandleAndProcess_delegatesToProcessorService() {
        String hl7Payload = "MSH|^~\\&|SendingApp|SendingFacility|ReceivingApp|ReceivingFacility|202507171330||ADT^A01|123456|P|2.5\r"
                +
                "EVN|A01|202507171330\r" +
                "PID|1||123456^^^HospitalMRN||Doe^John^A^^Mr.||19800101|M|||123 Main St^^Metropolis^NY^10001||(123)456-7890|||S||123456789|987654321\r"
                +
                "NK1|1|Doe^Jane^A|SPO||(123)456-7890\r" +
                "PV1|1|I|2000^201^01||||004777^Smith^Jack^A|||MED|||||||004777^Smith^Jack^A||I||||||||||||||||||||||||202507171330\r";

        Map<String, String> expectedResult = Map.of("status", "OK");
        when(processorService.processMessage(context, hl7Payload)).thenReturn(expectedResult);
        Map<String, String> result = mllpHandler.handleAndProcess(hl7Payload, context);
        assertEquals(expectedResult, result);
        verify(processorService, times(1)).processMessage(context, hl7Payload);
    }
}