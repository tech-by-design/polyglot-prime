package org.techbd.ingest.service.handler;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;

import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultipartFileHandlerTest {

    @Mock
    private MessageProcessorService processorService;

    @InjectMocks
    private MultipartFileHandler multipartFileHandler;

    private RequestContext context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        context = new RequestContext(
            Map.of("User-Agent", "JUnit"),
            "/upload",
            "testTenant",
            "interaction123",
            ZonedDateTime.now(),
            "1716899999999",
            "test.csv",
            123L,
            "objectKey",
            "metadataKey",
            "s3://bucket/test.csv",
            "JUnit-Agent",
            "http://localhost/upload",
            "",
            "HTTP/1.1",
            "127.0.0.1",
            "192.168.1.1",
            "192.168.1.1",
            "192.168.1.2",
            "8080"
        );

        multipartFileHandler = new MultipartFileHandler(processorService);
    }

    @Test
    void testCanHandle_withMultipartFile() {
        MultipartFile mockFile = new MockMultipartFile("file", "test.csv", "text/csv", "data".getBytes());
        assertTrue(multipartFileHandler.canHandle(mockFile, context));
    }

    @Test
    void testCanHandle_withInvalidSource() {
        assertFalse(multipartFileHandler.canHandle("Not a MultipartFile", context));
        assertFalse(multipartFileHandler.canHandle(null, context));
    }

    @Test
    void testHandleAndProcess_delegatesToProcessorService() {
        MultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "data".getBytes());
        Map<String, String> expected = Map.of("status", "SUCCESS");

        when(processorService.processMessage(context, file)).thenReturn(expected);

        Map<String, String> result = multipartFileHandler.handleAndProcess(file, context);

        assertEquals(expected, result);
        verify(processorService, times(1)).processMessage(context, file);
    }
}