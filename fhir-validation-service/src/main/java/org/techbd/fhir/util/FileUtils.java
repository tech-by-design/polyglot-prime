package org.techbd.fhir.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class FileUtils {
    public static List<String> readFile(String file) {
        return new BufferedReader(new InputStreamReader(
                ConceptReaderUtils.class.getClassLoader().getResourceAsStream(file), StandardCharsets.UTF_8))
                .lines().collect(Collectors.toList());
    }

    public static String readFile1(String file) {
        try {
            return new String(ConceptReaderUtils.class.getClassLoader().getResourceAsStream(file).readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
