package org.techbd.ingest.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

public class GlobalExceptionHandlerTest {

    @Mock
    private AppLogger appLogger;

    @Mock
    private TemplateLogger templateLogger;

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        when(appLogger.getLogger(GlobalExceptionHandler.class))
                .thenReturn(templateLogger);

        handler = new GlobalExceptionHandler(appLogger);

    }

    private void mockRequestContext(String interactionId) {
        ServletRequestAttributes attrs = mock(ServletRequestAttributes.class);

        when(attrs.getRequest()).thenReturn(request);
        when(request.getAttribute("interactionId")).thenReturn(interactionId);

        RequestContextHolder.setRequestAttributes(attrs);
    }

    @SuppressWarnings("deprecation")
    @Test
    void shouldHandleHttpMessageNotReadableException() {

        mockRequestContext("INT-1");

        try (MockedStatic<ErrorTraceIdGenerator> traceMock = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<org.techbd.ingest.util.LogUtil> logMock = mockStatic(
                        org.techbd.ingest.util.LogUtil.class)) {

            traceMock.when(ErrorTraceIdGenerator::generateErrorTraceId)
                    .thenReturn("TRACE-1");

            ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadableException(
                    new org.springframework.http.converter.HttpMessageNotReadableException("bad"));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Malformed JSON request.",
                    response.getBody().error().message());
        }
    }

    @Test
    void shouldHandleIllegalArgumentException() {

        mockRequestContext("INT-2");

        try (MockedStatic<ErrorTraceIdGenerator> traceMock = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<org.techbd.ingest.util.LogUtil> logMock = mockStatic(
                        org.techbd.ingest.util.LogUtil.class)) {

            traceMock.when(ErrorTraceIdGenerator::generateErrorTraceId)
                    .thenReturn("TRACE-2");

            ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(
                    new IllegalArgumentException("bad arg"));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Illegal argument passed to request.",
                    response.getBody().error().message());
        }
    }

    @Test
    void shouldHandleMultipartException_defaultMessage() {

        mockRequestContext("INT-4");

        Throwable cause = new RuntimeException("random issue");

        try (MockedStatic<ErrorTraceIdGenerator> traceMock = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<org.techbd.ingest.util.LogUtil> logMock = mockStatic(
                        org.techbd.ingest.util.LogUtil.class)) {

            traceMock.when(ErrorTraceIdGenerator::generateErrorTraceId)
                    .thenReturn("TRACE-4");

            ResponseEntity<ErrorResponse> response = handler.handleMultipartException(
                    new org.springframework.web.multipart.MultipartException("error", cause));

            assertTrue(response.getBody().error().message()
                    .contains("File upload failed"));
        }
    }

    @Test
    void shouldReturnUnknown_whenNoRequestContext() {

        RequestContextHolder.resetRequestAttributes();

        try (MockedStatic<ErrorTraceIdGenerator> traceMock = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<org.techbd.ingest.util.LogUtil> logMock = mockStatic(
                        org.techbd.ingest.util.LogUtil.class)) {

            traceMock.when(ErrorTraceIdGenerator::generateErrorTraceId)
                    .thenReturn("TRACE-5");

            ResponseEntity<ErrorResponse> response = handler.handleGeneralException(new RuntimeException("boom"));

            assertEquals("unknown",
                    response.getBody().error().interactionId());
        }
    }

    @Test
    void shouldHandleGeneralException() {

        mockRequestContext("INT-6");

        try (MockedStatic<ErrorTraceIdGenerator> traceMock = mockStatic(ErrorTraceIdGenerator.class);
                MockedStatic<org.techbd.ingest.util.LogUtil> logMock = mockStatic(
                        org.techbd.ingest.util.LogUtil.class)) {

            traceMock.when(ErrorTraceIdGenerator::generateErrorTraceId)
                    .thenReturn("TRACE-6");

            ResponseEntity<ErrorResponse> response = handler.handleGeneralException(new RuntimeException("boom"));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertTrue(response.getBody().error().message()
                    .contains("unexpected system error"));
        }
    }

    @Test
    void shouldReturnFileTooLarge() throws Exception {
        Throwable cause = new MaxUploadSizeExceededException("size exceeded");

        String result = invokeGetMultipartErrorMessage(cause);

        assertEquals("File too large.", result);
    }

    @Test
    void shouldReturnIllegalStateMessage() throws Exception {
        Throwable cause = new IllegalStateException("too big");

        String result = invokeGetMultipartErrorMessage(cause);

        assertEquals("Request too large or malformed.", result);
    }

    @Test
    void shouldReturnMultipartExceptionMessage() throws Exception {
        Throwable cause = new MultipartException("error");

        String result = invokeGetMultipartErrorMessage(cause);

        assertEquals("Multipart request error.", result);
    }

    @Test
    void shouldReturnContentTypeError() throws Exception {
        Throwable cause = new RuntimeException("Content-Type is not multipart");

        String result = invokeGetMultipartErrorMessage(cause);

        assertEquals("Content-Type must be multipart/form-data.", result);
    }

    @Test
    void shouldReturnMissingBoundary() throws Exception {
        Throwable cause = new RuntimeException("missing boundary");

        String result = invokeGetMultipartErrorMessage(cause);

        assertEquals("Missing multipart boundary in request.", result);
    }

    @Test
    void shouldReturnStreamEndedMessage() throws Exception {
        Throwable cause = new RuntimeException("stream ended unexpectedly");

        String result = invokeGetMultipartErrorMessage(cause);

        assertEquals("Upload stream ended unexpectedly. Please retry.", result);
    }

    @Test
    void shouldReturnDiskSpaceMessage() throws Exception {
        Throwable cause = new RuntimeException("disk space error");

        String result = invokeGetMultipartErrorMessage(cause);

        assertEquals("Server is out of disk space.", result);
    }

    @Test
    void shouldReturnFallbackMessage() throws Exception {
        Throwable cause = new RuntimeException("random error");

        String result = invokeGetMultipartErrorMessage(cause);

        assertEquals("File upload failed due to unexpected error.", result);
    }

    @Test
    void shouldReturnDefault_whenCauseNull() throws Exception {
        String result = invokeGetMultipartErrorMessage(null);

        assertEquals("File upload failed.", result);
    }

    class MaxUploadSizeExceededException extends RuntimeException {
        public MaxUploadSizeExceededException(String msg) {
            super(msg);
        }
    }

    class MultipartException extends RuntimeException {
        public MultipartException(String msg) {
            super(msg);
        }
    }

    private String invokeGetMultipartErrorMessage(Throwable cause) throws Exception {
        Method method = GlobalExceptionHandler.class
                .getDeclaredMethod("getMultipartErrorMessage", Throwable.class);
        method.setAccessible(true);

        return (String) method.invoke(handler, cause);
    }

}
