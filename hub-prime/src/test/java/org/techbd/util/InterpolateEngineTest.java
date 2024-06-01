package org.techbd.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.junit.jupiter.api.Test;

class InterpolateEngineTest {

    @Test
    void testInterpolateFormattedFilePath() {
        final var fsHome = "/home/user";
        final var artifactId = "testArtifact";
        final var formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd/HH"));
        final var expectedFilePath = String.format("%s/%s/%s.json", fsHome, formattedDate, artifactId);

        final var engine = new InterpolateEngine(Map.of("fsHome", fsHome, "artifactId", artifactId));
        assertThat(engine.interpolate("${fsHome}/${formattedDateNow('yyyy/MM/dd/HH')}/${artifactId}.json"))
                .isEqualTo(expectedFilePath);
    }

    @Test
    void testInterpolateCustomFunction() {
        final var fsHome = "/home/user";
        final var artifactId = "testArtifact";
        final var formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd/HH"));
        final var expectedFilePath = String.format("%s/CustomValue/%s/%s.json", fsHome, formattedDate, artifactId);

        final var engine = new InterpolateEngine(Map.of("fsHome", fsHome, "artifactId", artifactId)) {
            @SuppressWarnings("unused")
            public String customFunction() {
                return "CustomValue";
            }
        };

        assertThat(engine.interpolate("${fsHome}/${customFunction()}/${formattedDateNow('yyyy/MM/dd/HH')}/${artifactId}.json"))
                .isEqualTo(expectedFilePath);
    }
}
