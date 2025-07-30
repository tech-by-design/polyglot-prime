package org.techbd.service.http.hub.prime.api;

import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.techbd.util.SystemDiagnosticsLogger;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorResponse(String status, String message) {
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) // Set the appropriate HTTP status code
    @ApiResponse(responseCode = "400", description = "Validation Error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"Error\",\"message\":\"${message}\"}")))
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String tenantId = request.getHeader("X-TechBD-Tenant-ID"); // Use your actual header name
        LOG.error("Validation Error: Required request body is missing. Tenant ID: {}", tenantId, ex);
        ErrorResponse response = new ErrorResponse("Error", "Validation Error: Required request body is missing");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ApiResponse(responseCode = "400", description = "Validation Error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"Error\",\"message\":\"${message}\"}")))
    public ResponseEntity<String> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        String customMessage = ex.getMessage();
        String parameterName = ex.getName(); // Get the parameter name from ex.getName()
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String tenantId = request.getHeader("X-TechBD-Tenant-ID"); // Use your actual header name

        LOG.error("Validation Error: {}. Parameter Name: {}. Tenant ID: {}", customMessage, parameterName, tenantId,
                ex);

        // Directly creating a JSON response
        String responseBody = String.format(
                "{\"status\":\"Error\",\"message\":\"Validation Error: %s. Parameter Name: %s\"}", customMessage,
                parameterName);
        return new ResponseEntity<>(responseBody, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ApiResponse(responseCode = "400", description = "Validation Error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"Error\",\"message\":\"${message}\"}")))
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return handleException(ex, ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ApiResponse(responseCode = "400", description = "Validation Error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"Error\",\"message\":\"${message}\"}")))
    public ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        return handleException(ex, ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingRequestValueException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ApiResponse(responseCode = "400", description = "Validation Error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"Error\",\"message\":\"${message}\"}")))
    public ResponseEntity<ErrorResponse> handleMissingRequestValueException(MissingRequestValueException ex) {
        return handleException(ex, ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    @ApiResponse(responseCode = "400", description = "Unsupported Media Type", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"Error\",\"message\":\"Unsupported media type\"}")))
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        return handleException(ex, "Unsupported media type", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"Error\",\"message\":\"An unexpected system error occurred.\"}")))
    public ResponseEntity<String> handleGeneralException(Exception ex) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        HttpSession session = request.getSession(false); // Retrieve session if it exists, else null
        String tenantId = request.getHeader("X-TechBD-Tenant-ID");
        String sessionId = session != null ? session.getId() : "No session";
        String userAgent = request.getHeader("User-Agent");
        String remoteAddress = request.getRemoteAddr();
        String method = request.getMethod();
        String queryString = request.getQueryString();
        String requestUri = request.getRequestURI();
        LOG.error(
                "Internal Server Error occurred. Tenant ID: {}, Session ID: {},  User-Agent: {}, Remote Address: {}, Method: {}, Query: {}, URI: {}",
                tenantId, sessionId, userAgent, remoteAddress, method, queryString, requestUri, ex);
        SystemDiagnosticsLogger.logBasicSystemStats();
        String responseBody = "{\"status\":\"Error\",\"message\":\"An unexpected system error occurred.\"}";
        return new ResponseEntity<>(responseBody, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"Error\",\"message\":\"Unable to process the response due to an internal error\"}")))
    public ResponseEntity<ErrorResponse> handleHttpMessageNotWritableException(HttpMessageNotWritableException ex) {
        return handleException(ex, "We encountered an error while processing your file.",
                HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<ErrorResponse> handleException(Exception ex, String customMessage, HttpStatus status) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String tenantId = request.getHeader("X-TechBD-Tenant-ID");
        LOG.error("Validation Error: {}. Tenant ID: {}", customMessage, tenantId, ex);
        HttpSession session = request.getSession(false); // Retrieve session if it exists, else null
        String sessionId = session != null ? session.getId() : "No session";
        String userAgent = request.getHeader("User-Agent");
        String remoteAddress = request.getRemoteAddr();
        String method = request.getMethod();
        String queryString = request.getQueryString();
        String requestUri = request.getRequestURI();

        LOG.error(
                "Error occurred. Tenant ID: {}, Session ID: {},  User-Agent: {}, Remote Address: {}, Method: {}, Query: {}, URI: {}",
                tenantId, sessionId, userAgent, remoteAddress, method, queryString, requestUri, ex);
        ErrorResponse response = new ErrorResponse("Error", String.format("Validation Error: %s", customMessage));
        return new ResponseEntity<>(response, status);
    }

    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ApiResponse(responseCode = "400", description = "File Upload Error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"Error\",\"message\":\"File upload failed. Please upload the file again.\"}")))
    public ResponseEntity<String> handleMultipartException(MultipartException e) {
        String errorMessage = "File upload failed.";

        if (e.getCause() != null) {
            Throwable cause = e.getCause();
            String causeClass = cause.getClass().getSimpleName();
            String causeMessage = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";

            if ("MaxUploadSizeExceededException".equals(causeClass)) {
                errorMessage = "File upload failed: Uploaded file is too large.";
            } else if ("IllegalStateException".equals(causeClass)) {
                errorMessage = "File upload failed: The request was too large or malformed.";
            } else if ("MultipartException".equals(causeClass)) {
                errorMessage = "File upload failed: Multipart request error.";
            } else if (causeMessage.contains("content-type") && causeMessage.contains("not multipart")) {
                errorMessage = "File upload failed: Content-Type is not multipart/form-data.";
            } else if (causeMessage.contains("missing") && causeMessage.contains("boundary")) {
                errorMessage = "File upload failed: Missing multipart boundary in request.";
            } else if (causeMessage.contains("ioexception") || cause instanceof java.io.IOException) {
                errorMessage = "File upload failed: I/O error during file upload.";
            } else if (causeMessage.contains("disk") && causeMessage.contains("space")) {
                errorMessage = "File upload failed: Server is out of disk space.";
            } else if (causeMessage.contains("connection") && causeMessage.contains("reset")) {
                errorMessage = "File upload failed: Client connection was reset.";
            } else if (causeMessage.contains("stream ended unexpectedly")) {
                errorMessage = "File upload failed: Stream ended unexpectedly. Please retry.";
            }
        }
        SystemDiagnosticsLogger.logBasicSystemStats();
        LOG.error("Multipart request parsing failed", e);
        String responseBody = String.format("{\"status\":\"Error\",\"message\":\"%s\"}", errorMessage);
        return new ResponseEntity<>(responseBody, HttpStatus.BAD_REQUEST);
    }
  
    @ExceptionHandler(TimeoutException.class)
    @ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
    @ApiResponse(responseCode = "408", description = "Request Timeout", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"Error\",\"message\":\"Request timed out. Please try again.\"}")))
    public ResponseEntity<ErrorResponse> handleTimeoutException(TimeoutException ex) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String tenantId = request.getHeader("X-TechBD-Tenant-ID");
        LOG.error("Request timed out. Tenant ID: {}", tenantId, ex);
        SystemDiagnosticsLogger.logBasicSystemStats();
        ErrorResponse response = new ErrorResponse("Error", "Request timed out. Please try again.");
        return new ResponseEntity<>(response, HttpStatus.REQUEST_TIMEOUT);
    }
}