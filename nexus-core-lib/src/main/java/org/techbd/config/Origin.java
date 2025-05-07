package org.techbd.config;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum Origin {
    HTTP,
    SFTP;

    public static String getOriginsAsString() {
        return Arrays.stream(Origin.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}
