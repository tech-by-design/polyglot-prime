package org.techbd.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.techbd.conf.Configuration;

import com.fasterxml.jackson.core.JsonProcessingException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.Nonnull;
import jakarta.mail.MessagingException;
import jakarta.validation.constraints.NotNull;

public class ArtifactStore {
    private static final Logger LOG = LoggerFactory.getLogger(ArtifactStore.class);

    public interface Artifact {
        @Nonnull
        String getArtifactId();

        @Nonnull
        String getNamespace();

        @Nonnull
        Reader getReader();

        Optional<String> getJsonString();

        Map<String, Object> getProvenance();
    }

    public static String toJsonText(final Map<String, Object> object) {
        try {
            return Configuration.objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            return e.toString();
        }
    }

    public interface PersistenceReporter {
        void persisted(Artifact artifact, String... location);

        void info(String message);

        void issue(String message);
    }

    public sealed interface PersistenceStrategy
            permits DiagnosticPersistence, InvalidPersistenceStrategy, InvalidPersistenceNature, BlobStorePersistence,
            LocalFsPersistence, VirtualFsPersistence, EmailPersistence, AggregatePersistence {
        String REQUIRED_ARG_ARTIFACT_ID = "artifactId";

        void persist(@NotNull Artifact artifact, @NotNull Optional<PersistenceReporter> reporter);
    }

    public record DiagnosticPersistence(String identifier) implements PersistenceStrategy {
        public DiagnosticPersistence() {
            this("DiagnosticPersistence");
        }

        @Override
        public void persist(@NotNull Artifact artifact, @NotNull Optional<PersistenceReporter> reporter) {
            String message = MessageFormat.format("[{0}] artifactId: {1}", identifier, artifact.getArtifactId());
            reporter.ifPresent(r -> r.info(message));
        }
    }

    public record InvalidPersistenceStrategy(String strategyJson, Exception error) implements PersistenceStrategy {
        @Override
        public void persist(@NotNull Artifact artifact, @NotNull Optional<PersistenceReporter> reporter) {
            String message = String.format("[InvalidPersistenceStrategy %s] artifactId: %s", strategyJson,
                    artifact.getArtifactId());
            reporter.ifPresent(r -> r.issue(message));
        }
    }

    public record InvalidPersistenceNature(String nature) implements PersistenceStrategy {
        @Override
        public void persist(@NotNull Artifact artifact, @NotNull Optional<PersistenceReporter> reporter) {
            String message = String.format("[InvalidPersistenceNature %s] artifactId: %s", nature,
                    artifact.getArtifactId());
            reporter.ifPresent(r -> r.issue(message));
        }
    }

    public record BlobStorePersistence(Map<String, Object> args, String origJson) implements PersistenceStrategy {
        @Override
        public void persist(@NotNull Artifact artifact, @NotNull Optional<PersistenceReporter> reporter) {
            // TODO: implement this to store asynchronously via jClouds API
            reporter.ifPresent(r -> r.info("Result-AWS-S3-ID: AWS-object-id"));
        }
    }

    public record LocalFsPersistence(Map<String, Object> initArgs, InterpolateEngine ie)
            implements PersistenceStrategy {
        public static final String ARG_FS_PATH = "fsPath";
        public static final String FS_PATH_DEFAULT = String.format(
                "${cwd()}/ArtifactStore-LocalFsPersistence/${formattedDateNow('yyyy/MM/dd/HH')}/${%s}.json",
                REQUIRED_ARG_ARTIFACT_ID);

        LocalFsPersistence(Map<String, Object> initArgs) {
            this(initArgs, new InterpolateEngine(initArgs, REQUIRED_ARG_ARTIFACT_ID));
        }

        @Override
        public void persist(@NotNull Artifact artifact, @NotNull Optional<PersistenceReporter> reporter) {
            final var artifactId = artifact.getArtifactId();
            final var fsPath = ie.withValues(REQUIRED_ARG_ARTIFACT_ID, artifactId).interpolate(initArgs
                    .getOrDefault(ARG_FS_PATH, FS_PATH_DEFAULT)
                    .toString());

            Path path = Path.of(fsPath);
            try {
                final var parentPath = path.getParent();
                if (parentPath != null) {
                    Files.createDirectories(parentPath);
                }
                try (Reader reader = artifact.getReader();
                        Writer writer = Files.newBufferedWriter(path)) {
                    reader.transferTo(writer);
                } catch (IOException e) {
                    reporter.ifPresent(r -> r.issue("Failed to read artifact content: " + e.toString()));
                    return;
                }
            } catch (IOException e) {
                reporter.ifPresent(r -> r.issue("Unable to write JSON to " + fsPath + ": " + e.toString()));
                return;
            }

            reporter.ifPresent(r -> {
                r.persisted(artifact, fsPath);
                r.info(String.format("[persist-fs %s] %s", artifactId, path.toAbsolutePath()));
            });
        }
    }

    public record VirtualFsPersistence(Map<String, Object> initArgs, InterpolateEngine ie)
            implements PersistenceStrategy {
        static final String REQUIRED_ARG_VFS_URI = "vfsUri";
        public static final String VFS_URI_DEFAULT = String.format(
                "tmp://ArtifactStore-VirtualFsPersistence-${%s}.json",
                REQUIRED_ARG_ARTIFACT_ID);

        VirtualFsPersistence(Map<String, Object> initArgs) {
            this(initArgs, new InterpolateEngine(initArgs, REQUIRED_ARG_VFS_URI, REQUIRED_ARG_ARTIFACT_ID));
        }

        @Override
        public void persist(@NotNull Artifact artifact, @NotNull Optional<PersistenceReporter> reporter) {
            final var artifactId = artifact.getArtifactId();
            final var vfsUri = ie.withValues(REQUIRED_ARG_ARTIFACT_ID, artifactId).interpolate(initArgs
                    .getOrDefault(REQUIRED_ARG_VFS_URI, VFS_URI_DEFAULT)
                    .toString());

            try {
                final FileObject file = VFS.getManager().resolveFile(vfsUri);
                file.getParent().createFolder();
                try (var reader = artifact.getReader();
                        var writer = new OutputStreamWriter(file.getContent().getOutputStream(),
                                StandardCharsets.UTF_8)) {
                    reader.transferTo(writer);
                } catch (IOException e) {
                    reporter.ifPresent(r -> r.issue("Failed to read artifact content: " + e.toString()));
                    return;
                }
            } catch (FileSystemException e) {
                reporter.ifPresent(r -> r.issue("Unable to write JSON to " + vfsUri + ": " + e.toString()));
                return;
            }

            reporter.ifPresent(r -> {
                r.persisted(artifact, vfsUri);
                r.info(String.format("[persist-vfs %s] %s", artifactId, vfsUri));
            });
        }
    }

    public record EmailPersistence(Map<String, Object> initArgs, InterpolateEngine ie, JavaMailSender mailSender)
            implements ArtifactStore.PersistenceStrategy {

        public static final String ARG_FROM = "from";
        public static final String ARG_TO = "to";
        public static final String ARG_CC = "cc";
        public static final String ARG_BCC = "bcc";
        public static final String ARG_SUBJECT = "subject";
        public static final String ARG_BODY = "body";
        public static final String ARG_ATTACHMENT_NAME = "attachmentName";

        public EmailPersistence(Map<String, Object> initArgs, JavaMailSender mailSender) {
            this(initArgs, new InterpolateEngine(initArgs, REQUIRED_ARG_ARTIFACT_ID), mailSender);
        }

        @Override
        public void persist(@NotNull ArtifactStore.Artifact artifact,
                @NotNull Optional<ArtifactStore.PersistenceReporter> reporter) {
            if (mailSender == null) {
                reporter.ifPresent(r -> r.issue("'mailSender' not available in ArtifactStore.EmailPersistence"));
                return;
            }

            try {
                final var artifactId = artifact.getArtifactId();
                final var ie = this.ie().withValues(REQUIRED_ARG_ARTIFACT_ID, artifactId);
                final var message = mailSender.createMimeMessage();
                final var helper = new MimeMessageHelper(message, true);

                final var from = (String) initArgs.get(ARG_FROM);
                final var to = (String) initArgs.get(ARG_TO);
                final var subject = (String) initArgs.get(ARG_SUBJECT);
                if (to == null || subject == null || from == null) {
                    reporter.ifPresent(r -> r.issue(
                            "'from', 'to' and 'subject' arguments required to send emails in ArtifactStore.EmailPersistence"));
                    return;
                }

                final var cc = (String) initArgs.get(ARG_CC);
                final var bcc = (String) initArgs.get(ARG_BCC);
                final var body = (String) initArgs.get(ARG_BODY);
                final var attachmentName = (String) initArgs.get(ARG_ATTACHMENT_NAME);

                helper.setFrom(ie.interpolate(from));
                helper.setTo(ie.interpolate(to));
                helper.setSubject(ie.interpolate(subject));
                if (cc != null) {
                    helper.setCc(ie.interpolate(cc));
                }
                if (bcc != null) {
                    helper.setBcc(ie.interpolate(bcc));
                }

                if (attachmentName != null) {
                    if (body != null) {
                        helper.setText(ie.interpolate(body));
                    }
                    try (var reader = artifact.getReader();
                            var byteArrayOutputStream = new ByteArrayOutputStream();
                            var zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
                            var zipOutputWriter = new OutputStreamWriter(zipOutputStream, StandardCharsets.UTF_8)) {

                        zipOutputStream.putNextEntry(new ZipEntry(artifactId + ".json"));
                        reader.transferTo(zipOutputWriter);
                        zipOutputStream.closeEntry();

                        ByteArrayResource byteArrayResource = new ByteArrayResource(
                                byteArrayOutputStream.toByteArray());
                        var attachment = ie.interpolate(attachmentName);
                        helper.addAttachment(attachment, byteArrayResource);

                        mailSender.send(message);

                        var report = String.format(
                                "Email with attachment %s sent successfully from %s to %s in ArtifactStore.EmailPersistence",
                                attachment, from, to);
                        reporter.ifPresent(r -> {
                            r.persisted(artifact, report);
                            r.info(report);
                        });
                    } catch (IOException e) {
                        reporter.ifPresent(r -> r.issue(
                                "Failed to read artifact as Zip entry in ArtifactStore.EmailPersistence attachment: "
                                        + e.toString()));
                    }
                } else {
                    try (var reader = artifact.getReader();
                            var bodyWriter = new StringWriter()) {

                        reader.transferTo(bodyWriter);
                        helper.setText(bodyWriter.toString());
                        mailSender.send(message);

                        var report = String.format(
                                "Email sent successfully from %s to %s in ArtifactStore.EmailPersistence",
                                from, to);
                        reporter.ifPresent(r -> {
                            r.persisted(artifact, report);
                            r.info(report);
                        });
                    } catch (IOException e) {
                        reporter.ifPresent(r -> r.issue(
                                "Failed to read artifact content in ArtifactStore.EmailPersistence: "
                                        + e.toString()));
                    }
                }
            } catch (RuntimeException | MessagingException e) {
                reporter.ifPresent(
                        r -> r.issue("Failed to send email in ArtifactStore.EmailPersistence: " + e.toString()));
            }
        }
    }

    public record AggregatePersistence(List<PersistenceStrategy> strategies) implements PersistenceStrategy {
        @Override
        public void persist(@NotNull Artifact artifact, @NotNull Optional<PersistenceReporter> reporter) {
            for (PersistenceStrategy strategy : strategies) {
                strategy.persist(artifact, reporter);
            }
        }
    }

    public static class Builder {
        private static final Map<String, PersistenceStrategy> CACHED = new HashMap<>();
        private static final PersistenceStrategy DIAGNOSTIC_DEFAULT = new DiagnosticPersistence();

        private String strategyJson;
        private String provenanceJson;
        private Map<String, Object> provenance;
        private PersistenceStrategy always;
        private JavaMailSender mailSender;
        @SuppressFBWarnings("URF_UNREAD_FIELD")
        private ApplicationContext appCtx;

        public Builder strategyJson(String strategyJson) {
            this.strategyJson = strategyJson;
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder provenanceJson(String provenanceJson) {
            if (provenanceJson != null && provenanceJson.trim().length() > 0) {
                this.provenanceJson = provenanceJson;
                try {
                    final var parsedJson = Configuration.objectMapper.readValue(provenanceJson, Object.class);
                    if (parsedJson instanceof Map) {
                        this.provenance = (Map<String, Object>) parsedJson;
                    }
                } catch (Exception e) {
                    LOG.error("Unable to parse provenanceJson: " + provenanceJson, e);
                }
            } else {
                provenance = null;
            }
            return this;
        }

        public Map<String, Object> getProvenance() {
            return provenance;
        }

        public Builder always(final PersistenceStrategy always) {
            this.always = always;
            return this;
        }

        public Builder mailSender(final JavaMailSender mailSender) {
            this.mailSender = mailSender;
            return this;
        }

        public Builder appContext(final ApplicationContext appCtx) {
            this.appCtx = appCtx;
            return this;
        }

        private PersistenceStrategy createStrategy(final Map<String, Object> args, final String origJson) {
            if (provenanceJson != null) {
                args.put("provenance", provenance != null
                        ? provenance
                        : Map.of("invalid-json", provenanceJson));
            }
            final var natureO = args.get("nature");
            if (natureO instanceof String nature) {
                return switch (nature) {
                    case "aws-s3" -> new BlobStorePersistence(args, origJson);
                    case "diags", "diagnostics" -> DIAGNOSTIC_DEFAULT;
                    case "email" -> new EmailPersistence(args, mailSender);
                    case "fs" -> new LocalFsPersistence(args);
                    case "vfs" -> new VirtualFsPersistence(args);
                    default -> new InvalidPersistenceNature(nature);
                };
            } else {
                return new DiagnosticPersistence(
                        "nature is not a string: " + (natureO != null ? natureO.getClass().getName() : "null"));
            }
        }

        private PersistenceStrategy createAggregateStrategy(List<Map<String, Object>> strategyList) {
            final var strategies = new ArrayList<PersistenceStrategy>();
            for (var strategy : strategyList) {
                strategies.add(createStrategy(strategy, ""));
            }
            if (always != null) {
                strategies.add(always);
            }
            return new AggregatePersistence(strategies);
        }

        public PersistenceStrategy instance() {
            if (strategyJson == null) {
                return Optional.of(always).orElse(DIAGNOSTIC_DEFAULT);
            }

            try {
                Object parsedJson = Configuration.objectMapper.readValue(strategyJson, Object.class);
                if (parsedJson instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> args = (Map<String, Object>) parsedJson;
                    return always != null
                            ? new AggregatePersistence(List.of(createStrategy(args, strategyJson), always))
                            : createStrategy(args, strategyJson);
                } else if (parsedJson instanceof List) {
                    @SuppressWarnings("unchecked")
                    final var strategyList = (List<Map<String, Object>>) parsedJson;
                    return createAggregateStrategy(strategyList);
                } else {
                    return new DiagnosticPersistence(
                            "strategyJson is neither a Map nor a List: " + parsedJson.getClass().getName());
                }
            } catch (IOException e) {
                return new InvalidPersistenceStrategy(strategyJson, e);
            }
        }

        public PersistenceStrategy build() {
            return CACHED.computeIfAbsent(strategyJson, key -> instance());
        }
    }

    private static final class JsonArtifact implements Artifact {
        private final String jsonString;
        private final String artifactId;
        private final String namespace;
        private final Map<String, Object> provenance;

        // Constructor for JsonArtifact
        private JsonArtifact(final Object object, final String artifactId, final String namespace,
                final Map<String, Object> provenance) {
            this.artifactId = artifactId;
            this.namespace = namespace;
            // Ensure immutability
            this.provenance = provenance == null ? Collections.emptyMap() : Collections.unmodifiableMap(provenance);
            try {
                this.jsonString = Configuration.objectMapper.writeValueAsString(object);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to convert object to JSON", e);
            }
        }

        @Override
        public String getArtifactId() {
            return artifactId;
        }

        @Override
        public String getNamespace() {
            return namespace;
        }

        @Override
        public Map<String, Object> getProvenance() {
            return provenance;
        }

        @Override
        public Reader getReader() {
            return new StringReader(jsonString);
        }

        public Optional<String> getJsonString() {
            return Optional.of(jsonString);
        }
    }

    public static Artifact jsonArtifact(final Object object, final String artifactId, final String namespace,
            final Map<String, Object> provenance) {
        return new ArtifactStore.JsonArtifact(object, artifactId, namespace, provenance);
    }
}
