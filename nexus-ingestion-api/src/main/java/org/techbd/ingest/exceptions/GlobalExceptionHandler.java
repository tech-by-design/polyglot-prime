package org.techbd.ingest.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.LogUtil;
import org.techbd.ingest.util.TemplateLogger;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    private TemplateLogger LOG;
    private ObjectMapper objectMapper;

    public GlobalExceptionHandler(AppLogger appLogger) {
        this.LOG = appLogger.getLogger(GlobalExceptionHandler.class);
        this.objectMapper = new ObjectMapper();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        return handleException(ex, "Malformed JSON request.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return handleException(ex, "Invalid parameter type in request.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return handleException(ex, "Illegal argument passed to request.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        return handleException(ex, "Required request header is missing.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingRequestValueException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestValueException(MissingRequestValueException ex) {
        return handleException(ex, "A required request value is missing.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        return handleException(ex, "Unsupported media type.", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotWritableException(HttpMessageNotWritableException ex) {
        return handleException(ex, "Failed to generate response from server.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipartException(MultipartException e) {
        String errorMessage = getMultipartErrorMessage(e.getCause());
        return handleException(e, errorMessage, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.apache.camel.CamelExecutionException.class)
    public ResponseEntity<ErrorResponse> handleCamelExecutionException(org.apache.camel.CamelExecutionException ex) {
        return handleException(ex, "Internal integration error while processing request.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(org.apache.camel.ExchangeTimedOutException.class)
    public ResponseEntity<ErrorResponse> handleCamelTimeout(org.apache.camel.ExchangeTimedOutException ex) {
        return handleException(ex, "The integration request timed out. Please try again.", HttpStatus.GATEWAY_TIMEOUT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        return handleException(ex, "An unexpected system error occurred : " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Central exception handler that generates error trace ID, creates structured responses,
     * and logs detailed information for debugging.
     */
    private ResponseEntity<ErrorResponse> handleException(Exception ex, String userMessage, HttpStatus status) {
        // Generate unique error trace ID
        String errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
        
        // Extract interaction ID from request
        String interactionId = extractInteractionId();
        
        // Create client-facing error response
        ErrorResponse.Error errorDetails = new ErrorResponse.Error(
            status.value(),
            userMessage,
            interactionId,
            errorTraceId
        );
        ErrorResponse response = new ErrorResponse(errorDetails);
        
        // Log detailed error information for debugging
        LogUtil.logDetailedError(status.value(), userMessage, interactionId, errorTraceId, ex);
        
        return new ResponseEntity<>(response, status);
    }

    /**
     * Extracts the interaction ID from the current request.
     */
    private String extractInteractionId() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            // Assuming interaction ID is stored as a request attribute
            Object interactionId = request.getAttribute("interactionId");
            return interactionId != null ? interactionId.toString() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Determines appropriate error message for multipart/file upload exceptions.
     */
    private String getMultipartErrorMessage(Throwable cause) {
        if (cause == null) return "File upload failed.";
        String msg = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";

        return switch (cause.getClass().getSimpleName()) {
            case "MaxUploadSizeExceededException" -> "File too large.";
            case "IllegalStateException" -> "Request too large or malformed.";
            case "MultipartException" -> "Multipart request error.";
            default -> {
                if (msg.contains("content-type") && msg.contains("not multipart"))
                    yield "Content-Type must be multipart/form-data.";
                if (msg.contains("missing") && msg.contains("boundary"))
                    yield "Missing multipart boundary in request.";
                if (msg.contains("stream ended unexpectedly"))
                    yield "Upload stream ended unexpectedly. Please retry.";
                if (msg.contains("disk") && msg.contains("space"))
                    yield "Server is out of disk space.";
                yield "File upload failed due to unexpected error.";
            }
        };
    }
}