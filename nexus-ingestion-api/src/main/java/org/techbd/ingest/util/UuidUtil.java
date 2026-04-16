package org.techbd.ingest.util;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

public final class UuidUtil {

    // Single shared generator instance — critical for monotonicity within same ms
    private static final TimeBasedEpochGenerator UUID_GENERATOR = 
        Generators.timeBasedEpochGenerator();

    private UuidUtil() {}

    /**
     * Generates a UUID Version 7 (time-ordered, monotonic).
     * Uses a shared generator instance to ensure uniqueness within the same millisecond.
     *
     * @return UUIDv7 string
     */
    public static String generateUuid() {
        return UUID_GENERATOR.generate().toString();
    }
}
