package org.techbd.util;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import jakarta.validation.constraints.NotNull;

public class ArtifactStore {
    private static final JsonText jsonText = new JsonText();
    private static final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    public interface Artifact {
        String getArtifactId();

        Reader getReader();
    }

    public interface PersistenceReporter {
        void info(String message);

        void issue(String message);
    }

    public sealed interface PersistenceStrategy
            permits DiagnosticPersistence, BlobStorePersistence, FileSysPersistence {
        void persist(@NotNull Artifact artifact, @NotNull Optional<PersistenceReporter> reporter);
    }

    public record DiagnosticPersistence(String identifier) implements PersistenceStrategy {
        public DiagnosticPersistence() {
            this("DiagnosticPersistence");
        }

        @Override
        public void persist(@NotNull Artifact artifact, @NotNull Optional<PersistenceReporter> reporter) {
            String message = MessageFormat.format("[{0}] interactionId: {1}", identifier, artifact.getArtifactId());
            reporter.ifPresent(r -> r.info(message));
        }
    }

    public record BlobStorePersistence(Map<String, Object> args, String origJson) implements PersistenceStrategy {
        @Override
        public void persist(@NotNull Artifact artifact, @NotNull Optional<PersistenceReporter> reporter) {
            // TODO: implement this to store asynchronously via jClouds API
            reporter.ifPresent(r -> r.info("Result-AWS-S3-ID: AWS-object-id"));
        }
    }

    public record FileSysPersistence(String fsHome) implements PersistenceStrategy {
        public FileSysPersistence(final String fsHomeDefault, final Map<String, Object> args) {
            this(Optional.ofNullable(args.get("home"))
                    .map(Object::toString)
                    .orElse(fsHomeDefault));
        }

        @Override
        public void persist(@NotNull Artifact artifact, @NotNull Optional<PersistenceReporter> reporter) {
            String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd/HH"));
            String formattedFilePath = String.format("%s/%s/%s.json", fsHome, formattedDate,
                    artifact.getArtifactId());

            Path path = Path.of(formattedFilePath);
            try {
                Files.createDirectories(path.getParent());
                try (Reader reader = artifact.getReader();
                        Writer writer = Files.newBufferedWriter(path)) {
                    reader.transferTo(writer);
                } catch (IOException e) {
                    reporter.ifPresent(r -> r.issue("Failed to read artifact content: " + e.toString()));
                    return;
                }
            } catch (IOException e) {
                reporter.ifPresent(r -> r.issue("Unable to write JSON to " + formattedFilePath + ": " + e.toString()));
                return;
            }

            reporter.ifPresent(r -> r.info("fs-artifact: " + formattedFilePath));
        }
    }

    public static class Builder {
        private static final Map<String, PersistenceStrategy> CACHED = new HashMap<>();
        private static final PersistenceStrategy DIAGNOSTIC_DEFAULT = new DiagnosticPersistence();

        private String strategyJson;
        private String defaultFsHome;

        public Builder strategyJson(String strategyJson) {
            this.strategyJson = strategyJson;
            return this;
        }

        public Builder defaultFsHome(String defaultFsHome) {
            this.defaultFsHome = defaultFsHome;
            return this;
        }

        public PersistenceStrategy instance() {
            if (strategyJson == null) {
                return DIAGNOSTIC_DEFAULT;
            }

            final var result = jsonText.getJsonObject(strategyJson);
            switch (result) {
                case JsonText.JsonObjectResult.ValidUntypedResult validUntypedResult -> {
                    final var natureO = validUntypedResult.jsonObject().get("nature");
                    if (natureO instanceof String) {
                        final var nature = (String) natureO;
                        return switch (nature) {
                            case "diags", "diagnostics" -> DIAGNOSTIC_DEFAULT;
                            case "fs" -> new FileSysPersistence(defaultFsHome, validUntypedResult.jsonObject());
                            case "aws-s3" -> new BlobStorePersistence(validUntypedResult.jsonObject(),
                                    validUntypedResult.originalText());
                            default -> new DiagnosticPersistence("unknown nature: " + nature);
                        };
                    }
                    return new DiagnosticPersistence("natureO is not a string: " + natureO.getClass().getName());
                }
                case JsonText.JsonObjectResult.ValidResult<?> validResult -> {
                    if (validResult.instance() instanceof PersistenceStrategy) {
                        return (PersistenceStrategy) validResult.instance();
                    } else {
                        return new DiagnosticPersistence(
                                "instance is not a persistence strategy implementation:"
                                        + validResult.instance().getClass().getName());
                    }
                }
                default -> new DiagnosticPersistence("result is not valid: " + result.getClass().getName());
            }

            return new DiagnosticPersistence("incomprehensible suggestion: " + result.getClass().getName());
        }

        public PersistenceStrategy build() {
            return strategyJson == null ? DIAGNOSTIC_DEFAULT
                    : CACHED.computeIfAbsent("strategyJson", key -> instance());
        }
    }

    public static Artifact jsonArtifact(Object object, String artifactId) {
        return new Artifact() {
            private final String jsonString;

            {
                try {
                    jsonString = objectMapper.writeValueAsString(object);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to convert object to JSON", e);
                }
            }

            @Override
            public String getArtifactId() {
                return artifactId;
            }

            @Override
            public Reader getReader() {
                return new StringReader(jsonString);
            }
        };
    }
}
