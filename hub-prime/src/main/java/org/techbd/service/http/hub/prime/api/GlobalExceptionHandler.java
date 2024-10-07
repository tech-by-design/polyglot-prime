package org.techbd.service.http.hub.prime.api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorResponse(String status, String message) {}

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) // Set the appropriate HTTP status code
    @ApiResponse(responseCode = "400", description = "Validation Error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"Error\",\"message\":\"${message}\"}")))
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String tenantId = request.getHeader("X-Tenant-ID"); // Use your actual header name
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
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String tenantId = request.getHeader("X-Tenant-ID"); // Use your actual header name

        LOG.error("Validation Error: {}. Parameter Name: {}. Tenant ID: {}", customMessage, parameterName, tenantId, ex);
        
        // Directly creating a JSON response
        String responseBody = String.format("{\"status\":\"Error\",\"message\":\"Validation Error: %s. Parameter Name: %s\"}", customMessage, parameterName);
        return new ResponseEntity<>(responseBody, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ApiResponse(responseCode = "400", description = "Validation Error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"Error\",\"message\":\"${message}\"}")))
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return handleException(ex, ex.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ApiResponse(responseCode = "400", description = "Validation Error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"Error\",\"message\":\"${message}\"}")))
    public ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        return handleException(ex, ex.getMessage());
    }

    @ExceptionHandler(MissingRequestValueException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ApiResponse(responseCode = "400", description = "Validation Error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"Error\",\"message\":\"${message}\"}")))
    public ResponseEntity<ErrorResponse> handleMissingRequestValueException(MissingRequestValueException ex) {
        return handleException(ex, ex.getMessage());
    }

    @ExceptionHandler(Exception.class) // Catch all other exceptions
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"status\":\"Error\",\"message\":\"Internal Server Error\"}")))
    public ResponseEntity<String> handleGeneralException(Exception ex) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String tenantId = request.getHeader("X-Tenant-ID"); // Use your actual header name

        LOG.error("Internal Server Error. Tenant ID: {}", tenantId, ex);

        // Directly creating a JSON response
        String responseBody = "{\"status\":\"Error\",\"message\":\"An unexpected system error occurred.\"}";
        return new ResponseEntity<>(responseBody, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    private ResponseEntity<ErrorResponse> handleException(Exception ex, String customMessage) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String tenantId = request.getHeader("X-Tenant-ID"); // Use your actual header name

        LOG.error("Validation Error: {}. Tenant ID: {}", customMessage, tenantId, ex);
        ErrorResponse response = new ErrorResponse("Error", String.format("Validation Error: %s", customMessage));
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
}
