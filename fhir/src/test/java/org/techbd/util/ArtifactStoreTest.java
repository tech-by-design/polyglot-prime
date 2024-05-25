package org.techbd.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void testDiagnosticPersistence() {
        ArtifactStore.PersistenceStrategy strategy = new ArtifactStore.DiagnosticPersistence();

        Map<String, String> report = new HashMap<>();
        strategy.persist(ArtifactStore.jsonArtifact(Map.of("key", "value"), "testArtifactId"), Optional.of(new ArtifactStore.PersistenceReporter() {
            @Override
            public void info(String message) {
                report.put("info", message);
            }

            @Override
            public void issue(String message) {
                report.put("issue", message);
            }
        }));

        assertThat(report).containsKey("info");
    }

    @Test
    void testFileSysPersistence() throws IOException {
        Path ramDisk = Files.createTempDirectory(tempDir, "ramdisk");
        ArtifactStore.PersistenceStrategy strategy = new ArtifactStore.FileSysPersistence(ramDisk.toString(), Map.of());

        Map<String, String> report = new HashMap<>();
        strategy.persist(ArtifactStore.jsonArtifact(Map.of("key", "value"), "testArtifactId"), Optional.of(new ArtifactStore.PersistenceReporter() {
            @Override
            public void info(String message) {
                report.put("info", message);
            }

            @Override
            public void issue(String message) {
                report.put("issue", message);
            }
        }));

        assertThat(report).containsKey("info");

        String filePath = report.get("info").split(": ")[1];
        assertThat(Files.exists(Path.of(filePath))).isTrue();

        String content = Files.readString(Path.of(filePath));
        assertThat(content).contains("\"key\" : \"value\"");
    }

    @Test
    void testBuilderWithDiagnosticPersistence() {
        ArtifactStore.Builder builder = new ArtifactStore.Builder();
        ArtifactStore.PersistenceStrategy strategy = builder.build();

        Map<String, String> report = new HashMap<>();
        strategy.persist(ArtifactStore.jsonArtifact(Map.of("key", "value"), "testArtifactId"), Optional.of(new ArtifactStore.PersistenceReporter() {
            @Override
            public void info(String message) {
                report.put("info", message);
            }

            @Override
            public void issue(String message) {
                report.put("issue", message);
            }
        }));

        assertThat(report).containsKey("info");
    }

    @Test
    void testBuilderWithFileSysPersistence() throws IOException {
        String fsHome = tempDir.toString();
        String strategyJson = "{\"nature\": \"fs\", \"home\": \"" + fsHome + "\"}";

        ArtifactStore.Builder builder = new ArtifactStore.Builder()
                .strategyJson(strategyJson)
                .defaultFsHome(fsHome);

        ArtifactStore.PersistenceStrategy strategy = builder.build();

        Map<String, String> report = new HashMap<>();
        strategy.persist(ArtifactStore.jsonArtifact(Map.of("key", "value"), "testArtifactId"), Optional.of(new ArtifactStore.PersistenceReporter() {
            @Override
            public void info(String message) {
                report.put("info", message);
            }

            @Override
            public void issue(String message) {
                report.put("issue", message);
            }
        }));

        assertThat(report).containsKey("info");

        String filePath = report.get("info").split(": ")[1];
        assertThat(Files.exists(Path.of(filePath))).isTrue();

        String content = Files.readString(Path.of(filePath));
        assertThat(content).contains("\"key\" : \"value\"");
    }
}
