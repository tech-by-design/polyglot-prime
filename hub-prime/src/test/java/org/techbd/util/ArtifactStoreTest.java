package org.techbd.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

public class ArtifactStoreTest {

    // @Test
    // void testDefaultStrategyWhenNoJsonProvided() {
    //     final var builder = new ArtifactStore.Builder();
    //     final var strategy = builder.build();

    //     assertThat(strategy).isInstanceOf(ArtifactStore.DiagnosticPersistence.class);

    //     final var artifact = createTestArtifact("testId");
    //     final var reporter = createTestReporter();
    //     strategy.persist(artifact, Optional.of(reporter));

    //     assertThat(reporter.getInfoMessages()).containsExactly("[DiagnosticPersistence] artifactId: testId");
    // }

    @Test
    void testSingleStrategyFromJson() throws Exception {
        // `fsPath` can be an expression with ${artifactId} will be replaced at runtime.
        final var builder = new ArtifactStore.Builder()
                .strategyJson("{\"nature\": \"fs\", \"fsPath\": \"/tmp/${artifactId}.json\"}");

        final var strategy = builder.build();

        assertThat(strategy).isInstanceOf(ArtifactStore.LocalFsPersistence.class);
        final var localFsStrategy = (ArtifactStore.LocalFsPersistence) strategy;
        assertThat(localFsStrategy.initArgs().get("nature")).isEqualTo("fs");

        final var artifact = createTestArtifact("testId");
        final var reporter = createTestReporter();
        strategy.persist(artifact, Optional.of(reporter));

        final var fsPath = Path.of("/tmp/testId.json");
        assertThat(Files.exists(fsPath)).isTrue();
        assertThat(reporter.getInfoMessages()).containsExactly("[persist-fs testId] /tmp/testId.json");
    }

    @Test
    void testAggregateStrategyFromJson() {
        final var builder = new ArtifactStore.Builder()
                .strategyJson(
                        "[{\"nature\": \"fs\", \"fsPath\": \"/tmp/${artifactId}.json\"}, {\"nature\": \"diagnostics\"}]");

        final var strategy = builder.build();

        assertThat(strategy).isInstanceOf(ArtifactStore.AggregatePersistence.class);
        final var aggregateStrategy = (ArtifactStore.AggregatePersistence) strategy;
        assertThat(aggregateStrategy.strategies()).hasSize(2);
        assertThat(aggregateStrategy.strategies().get(0)).isInstanceOf(ArtifactStore.LocalFsPersistence.class);
        assertThat(aggregateStrategy.strategies().get(1)).isInstanceOf(ArtifactStore.DiagnosticPersistence.class);

        final var artifact = createTestArtifact("testId");
        final var reporter = createTestReporter();
        strategy.persist(artifact, Optional.of(reporter));

        final var fsPath = Path.of("/tmp/testId.json");
        assertThat(Files.exists(fsPath)).isTrue();
        assertThat(reporter.getInfoMessages()).contains("[persist-fs testId] /tmp/testId.json",
                "[DiagnosticPersistence] artifactId: testId");
    }

    @Test
    void testInvalidJsonHandling() {
        final var builder = new ArtifactStore.Builder()
                .strategyJson("invalid json");

        final var strategy = builder.build();

        assertThat(strategy).isInstanceOf(ArtifactStore.InvalidPersistenceStrategy.class);

        final var artifact = createTestArtifact("testId");
        final var reporter = createTestReporter();
        strategy.persist(artifact, Optional.of(reporter));

        assertThat(reporter.getIssueMessages()).isNotEmpty();
    }

    @Test
    void testUnknownNatureInJson() {
        final var strategyJson = "{\"nature\": \"unknown\"}";
        final var builder = new ArtifactStore.Builder()
                .strategyJson(strategyJson);

        final var strategy = builder.build();

        assertThat(strategy).isInstanceOf(ArtifactStore.InvalidPersistenceNature.class);

        final var artifact = createTestArtifact("testId");
        final var reporter = createTestReporter();
        strategy.persist(artifact, Optional.of(reporter));

        assertThat(reporter.getIssueMessages())
                .containsExactly("[InvalidPersistenceNature unknown] artifactId: testId");
    }

    private ArtifactStore.Artifact createTestArtifact(String artifactId) {
        return ArtifactStore.jsonArtifact(Map.of("key", "value"), artifactId, "unit-test", null);
    }

    private TestReporter createTestReporter() {
        return new TestReporter();
    }

    static class TestReporter implements ArtifactStore.PersistenceReporter {
        private final List<String> infoMessages = new ArrayList<>();
        private final List<String> issueMessages = new ArrayList<>();
        private final List<String> persisted = new ArrayList<>();

        @Override
        public void persisted(ArtifactStore.Artifact artifact, String... locations) {
            persisted.addAll(List.of(locations));
        }

        @Override
        public void info(String message) {
            infoMessages.add(message);
        }

        @Override
        public void issue(String message) {
            issueMessages.add(message);
        }

        public List<String> getInfoMessages() {
            return infoMessages;
        }

        public List<String> getIssueMessages() {
            return issueMessages;
        }
    }
}
