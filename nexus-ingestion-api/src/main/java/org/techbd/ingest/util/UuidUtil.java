package org.techbd.ingest.util;

import com.fasterxml.uuid.Generators;


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
        return Generators.timeBasedEpochGenerator().generate().toString();    }

}
