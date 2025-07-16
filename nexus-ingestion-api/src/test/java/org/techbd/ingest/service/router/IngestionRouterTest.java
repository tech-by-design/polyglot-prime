package org.techbd.ingest.service.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.handler.IngestionSourceHandler;

class IngestionRouterTest {

    private IngestionSourceHandler handler1;
    private IngestionSourceHandler handler2;
    private IngestionRouter router;

    private RequestContext context;

    @BeforeEach
    void setUp() {
        handler1 = mock(IngestionSourceHandler.class);
        handler2 = mock(IngestionSourceHandler.class);

        router = new IngestionRouter(List.of(handler1, handler2));

        context = new RequestContext(
                Map.of("User-Agent", "JUnit"),
                "/upload",
                "tenant1",
                "interaction123",
                ZonedDateTime.now(),
                "1716899999999",
                "file.txt",
                123L,
                "objectKey",
                "metadataKey",
                "s3://bucket/file.txt",
                "JUnit-Agent",
                "http://localhost/upload",
                "",
                "HTTP/1.1",
                "127.0.0.1",
                "192.168.1.1",
                "192.168.1.1",
                "192.168.1.2",
                "8080");
    }

    @Test
    void testRouteAndProcess_withFirstHandlerHandling() {
        Object source = "validInput";
        Map<String, String> expected = Map.of("status", "handled-by-handler1");

        when(handler1.canHandle(source)).thenReturn(true);
        when(handler1.handleAndProcess(source, context)).thenReturn(expected);

        Map<String, String> result = router.routeAndProcess(source, context);

        assertEquals(expected, result);
        verify(handler1, times(1)).handleAndProcess(source, context);
        verify(handler2, never()).canHandle(any());
        verify(handler2, never()).handleAndProcess(any(), any());
    }

    @Test
    void testRouteAndProcess_withSecondHandlerHandling() {
        Object source = "fallbackInput";
        Map<String, String> expected = Map.of("status", "handled-by-handler2");

        when(handler1.canHandle(source)).thenReturn(false);
        when(handler2.canHandle(source)).thenReturn(true);
        when(handler2.handleAndProcess(source, context)).thenReturn(expected);

        Map<String, String> result = router.routeAndProcess(source, context);

        assertEquals(expected, result);
        verify(handler1, times(1)).canHandle(source);
        verify(handler1, never()).handleAndProcess(any(), any());
        verify(handler2, times(1)).handleAndProcess(source, context);
    }

    @Test
    void testRouteAndProcess_whenNoHandlerCanHandle() {
        Object source = "unknownInput";

        when(handler1.canHandle(source)).thenReturn(false);
        when(handler2.canHandle(source)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> router.routeAndProcess(source, context));

        assertTrue(exception.getMessage().contains("No handler for input type"));
    }

    @Test
    void testRouteAndProcess_withNullSource() {
        Object source = null;

        when(handler1.canHandle(source)).thenReturn(false);
        when(handler2.canHandle(source)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> router.routeAndProcess(source, context));

        assertTrue(exception.getMessage().contains("No handler for input type"));
    }

    @Test
    void testRouteAndProcess_withHL7String_usesMllpHandler() {
        String hl7Message = "MSH|^~\\&|LAB|...";

        IngestionSourceHandler mllpHandler = mock(IngestionSourceHandler.class);
        IngestionSourceHandler multipartHandler = mock(IngestionSourceHandler.class);

        when(mllpHandler.canHandle(hl7Message)).thenReturn(true);
        Map<String, String> response = Map.of("status", "processed-hl7");
        when(mllpHandler.handleAndProcess(hl7Message, context)).thenReturn(response);

        IngestionRouter ingestionRouter = new IngestionRouter(List.of(mllpHandler, multipartHandler));

        Map<String, String> result = ingestionRouter.routeAndProcess(hl7Message, context);

        assertEquals(response, result);
        verify(mllpHandler, times(1)).canHandle(hl7Message);
        verify(mllpHandler, times(1)).handleAndProcess(hl7Message, context);
        verify(multipartHandler, never()).canHandle(any());
        verify(multipartHandler, never()).handleAndProcess(any(), any());
    }

    @Test
    void testRouteAndProcess_withMultipartFile_usesMultipartFileHandler() {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "test-data".getBytes());

        IngestionSourceHandler mllpHandler = mock(IngestionSourceHandler.class);
        IngestionSourceHandler multipartHandler = mock(IngestionSourceHandler.class);

        when(mllpHandler.canHandle(file)).thenReturn(false);
        when(multipartHandler.canHandle(file)).thenReturn(true);
        Map<String, String> response = Map.of("status", "processed-multipart");
        when(multipartHandler.handleAndProcess(file, context)).thenReturn(response);

        IngestionRouter ingestionRouter = new IngestionRouter(List.of(mllpHandler, multipartHandler));

        Map<String, String> result = ingestionRouter.routeAndProcess(file, context);

        assertEquals(response, result);
        verify(mllpHandler, times(1)).canHandle(file);
        verify(mllpHandler, never()).handleAndProcess(any(), any());
        verify(multipartHandler, times(1)).handleAndProcess(file, context);
    }
}