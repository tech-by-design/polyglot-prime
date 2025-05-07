package org.techbd.config;

import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

public enum SourceType {
    FHIR,
    CSV,
    HL7,
    CCDA;

    public static List<SourceType> getAllSourceTypes() {
        return Collections.unmodifiableList(Arrays.asList(SourceType.values()));
    }

    public static String getSourceTypesAsString() {
        return Arrays.stream(SourceType.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}