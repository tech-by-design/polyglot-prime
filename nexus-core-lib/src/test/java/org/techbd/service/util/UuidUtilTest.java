package org.techbd.service.util;

import org.junit.jupiter.api.Test;
import org.techbd.corelib.util.UuidUtil;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UuidUtilTest {

    @Test
    void shouldGenerateUniqueUuids() {
        int count = 100_000;

        Set<String> uuids = ConcurrentHashMap.newKeySet();

        IntStream.range(0, count)
                .parallel()
                .forEach(i -> uuids.add(UuidUtil.generateUuid()));

        assertEquals(count, uuids.size(), "Duplicate UUIDs detected!");
    }
}
