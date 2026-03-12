package org.techbd.ingest.exceptions;
/**
 * Structured error response for API clients.
 * Contains generic error information without exposing internal details.
 */
public record ErrorResponse(
    Error error
) {
    public record Error(
        int code,
        String message,
        String interactionId,
        String errorTraceId
    ) {}
}
