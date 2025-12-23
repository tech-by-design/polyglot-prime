package org.techbd.ingest.exceptions;


import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Detailed error log model for internal logging (CloudWatch).
 * Contains full exception details and stack traces for debugging.
 */
public record DetailedErrorLog(
    Error error
) {
    public record Error(
        int code,
        String message,
        String interactionId,
        String errorTraceId,
        ExceptionDetails exception,
        List<String> stackTrace
    ) {}

    public record ExceptionDetails(
        String type,
        String message,
        String cause
    ) {}

    /**
     * Creates a DetailedErrorLog from an exception.
     */
    public static DetailedErrorLog fromException(
            int code, 
            String message, 
            String interactionId, 
            String errorTraceId, 
            Exception ex) {
        
        ExceptionDetails exceptionDetails = new ExceptionDetails(
            ex.getClass().getName(),
            ex.getMessage(),
            ex.getCause() != null ? ex.getCause().toString() : null
        );

        List<String> stackTrace = Arrays.stream(ex.getStackTrace())
            .map(StackTraceElement::toString)
            .collect(Collectors.toList());

        return new DetailedErrorLog(
            new Error(code, message, interactionId, errorTraceId, exceptionDetails, stackTrace)
        );
    }
}
