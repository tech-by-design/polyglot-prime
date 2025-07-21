package org.techbd.ingest;

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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.techbd.ingest.commons.Constants;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorResponse(String status, String message) {}

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
        return handleException(ex, "An unexpected system error occurred.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> handleException(Exception ex, String userMessage, HttpStatus status) {
        logErrorWithRequestContext(userMessage, ex);
        ErrorResponse response = new ErrorResponse("Error", userMessage);
        return new ResponseEntity<>(response, status);
    }

    private void logErrorWithRequestContext(String contextMessage, Exception ex) {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            HttpSession session = request.getSession(false);
            String tenantId = request.getHeader(Constants.REQ_HEADER_TENANT_ID);
            String sessionId = session != null ? session.getId() : "No session";
            String userAgent = request.getHeader("User-Agent");
            String remoteAddr = request.getRemoteAddr();
            String forwardedFor = request.getHeader(Constants.REQ_HEADER_X_FORWARDED_FOR);
            String realIp = request.getHeader(Constants.REQ_HEADER_X_REAL_IP);
            String serverIp = request.getHeader(Constants.REQ_X_SERVER_IP);
            String serverPort = request.getHeader(Constants.REQ_X_SERVER_PORT);
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String queryString = request.getQueryString();
            LOG.error("""
                    Exception occurred:
                    -> Context Message         : {}
                    -> Tenant ID               : {}
                    -> Session ID              : {}
                    -> HTTP Method             : {}
                    -> URI                     : {}
                    -> Query String            : {}
                    -> User-Agent              : {}
                    -> Remote Address          : {}
                    -> X-Forwarded-For (source): {}
                    -> X-Real-IP (source)      : {}
                    -> X-Server-IP (destination IDP) : {}
                    -> X-Server-Port (destination port): {}
                    -> Exception               : {}: {}
                    """,
                    contextMessage, tenantId, sessionId, method, uri, queryString,
                    userAgent, remoteAddr,
                    forwardedFor, realIp,
                    serverIp, serverPort,
                    ex.getClass().getSimpleName(), ex.getMessage(), ex);
        } catch (Exception loggingEx) {
            LOG.error("Failed to log error context: {}", loggingEx.getMessage(), loggingEx);
        }
    }


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
