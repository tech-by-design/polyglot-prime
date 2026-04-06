package org.techbd.ingest.util;

import com.github.f4b6a3.uuid.UuidCreator;

public final class UuidUtil {
      private UuidUtil() {
    }

      /**
     * Generates a UUID Version 7 (time-ordered).
     * Recommended replacement for UUID.randomUUID().
     *
     * @return UUIDv7 string
     */
    public static String generateUuid() {
        return UuidCreator.getTimeOrderedEpoch().toString();
    }

}
