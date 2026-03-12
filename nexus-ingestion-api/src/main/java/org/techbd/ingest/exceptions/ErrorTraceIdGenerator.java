package org.techbd.ingest.exceptions;


import java.util.UUID;

/**
 * Utility class for generating unique error trace identifiers.
 * Uses UUID v4 for generating globally unique error trace IDs.
 */
public class ErrorTraceIdGenerator {

    /**
     * Generates a new UUID v4 error trace ID.
     * 
     * @return A string representation of a UUID v4
     */
    public static String generateErrorTraceId() {
        return UUID.randomUUID().toString();
    }
}
