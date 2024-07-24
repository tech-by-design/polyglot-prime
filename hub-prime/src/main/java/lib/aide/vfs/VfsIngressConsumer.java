package lib.aide.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;

import jakarta.annotation.Nonnull;

/**
 * The VfsIngressConsumer class is designed to manage and process files from
 * various locations, organizing them into groups, and handling snapshots
 * (backup copies) of those files. It works as a builder pattern to set up
 * different functions to categorize, group, and process files.
 * 
 * This class is useful for:
 * 
 * - **Ingesting Files**: It helps in collecting files from specified locations.
 * - **Snapshotting Files**: It determines if a file needs a snapshot (backup)
 * and moves it to a new location if needed.
 * - **Grouping Files**: It groups files based on a custom grouping function,
 * which can be used to categorize files logically.
 * - **Processing Files**: It allows custom processing of files, such as
 * extracting files from a ZIP archive.
 * - **Auditing**: It keeps a record of actions performed on the files, such as
 * moves, groupings, and any errors encountered.
 * 
 * This can be especially useful in scenarios where you have a directory of
 * files that need to be processed, backed up, and categorized, such as in data
 * ingestion pipelines, file management systems, or automated archival systems.
 * 
 * Suppose you have a directory of various files, some of which are ZIP files.
 * This class can:
 * 1. Identify files that need to be "processed" (such as an ETL pipeline).
 * 2. Move those files to a new "session" directory during processing in case it
 * takes time to do processing.
 * 3. Extract contents of ZIP files into a processing directory (in case an ETL
 * occurs not on the ZIP but the contents of the ZIP).
 * 4. Group files based on a naming convention or metadata in processing must be
 * done in groups or "batches" of files.
 * 5. Keep track of all these operations for auditing and troubleshooting
 * purposes.
 * 
 * This class relies heavily on Apache Commons VFS for file operations and Java
 * Streams for efficient data handling.
 * 
 * <p>
 * Example Code:
 * </p>
 * 
 * <pre>{@code
 * VfsIngressConsumer consumer = new VfsIngressConsumer.Builder()
 *         .addIngressPath(new FileObject("/path/to/files"))
 *         .isGroup(file -> file.getName().getExtension())
 *         .isGroupComplete(group -> group.getEntries().size() > 2)
 *         .isSnapshotable((file, home, snapshot, audit) -> file.getName().getExtension().equals("txt"))
 *         .populateSnapshot((file, home, snapshot, audit) -> List.of(file))
 *         .consumables(VfsIngressConsumer::consumeUnzipped)
 *         .build();
 * 
 * consumer.drain(new FileObject("/path/to/egress"), Optional.of(UUID.randomUUID()));
 * }</pre>
 */
public class VfsIngressConsumer {

    private final List<IngressPath> ingressPaths;
    private final Function<FileObject, String> isGroup;
    private final Predicate<IngressGroup> isGroupComplete;
    private final QuadFunction<IngressIndividual, FileObject, FileObject, Audit, Boolean> isSnapshotable;
    private final QuadFunction<IngressIndividual, FileObject, FileObject, Audit, List<IngressIndividual>> populateSnapshot;
    private final QuadFunction<IngressIndividual, FileObject, FileObject, Audit, List<IngressIndividual>> consumables;
    private final Audit audit = new Audit(new ArrayList<>());

    private UUID sessionId;
    private FileObject sessionHome;
    private FileObject snapshotHome;
    private List<IngressIndividual> originalEntries; // before snapshots
    private List<IngressIndividual> snapshotEntries; // after snapshots
    private List<IngressIndividual> consumeEntries; // after snapshot transforms
    private Map<String, List<IngressIndividual>> groupedEntriesMap;
    private List<IngressIndividual> individualEntries;
    private List<IngressGroup> groupedEntries;
    private List<IngressGroup> completeGroups;
    private List<IngressGroup> incompleteGroups;

    private VfsIngressConsumer(final Builder builder) throws FileSystemException {
        this.ingressPaths = builder.ingressPaths;
        this.isGroup = builder.isGroup;
        this.isGroupComplete = builder.isGroupComplete;
        this.isSnapshotable = builder.isSnapshotable;
        this.populateSnapshot = builder.populateSnapshot;
        this.consumables = builder.consumables;
    }

    public static class Builder {
        private final List<IngressPath> ingressPaths = new ArrayList<>();
        private Function<FileObject, String> isGroup;
        private Predicate<IngressGroup> isGroupComplete;
        private QuadFunction<IngressIndividual, FileObject, FileObject, Audit, Boolean> isSnapshotable = (ie,
                file, dir, audit) -> true;
        private QuadFunction<IngressIndividual, FileObject, FileObject, Audit, List<IngressIndividual>> populateSnapshot = (
                ie, file, dir, audit) -> List.of(ie);
        private QuadFunction<IngressIndividual, FileObject, FileObject, Audit, List<IngressIndividual>> consumables = (
                ie, file, dir, audit) -> List.of(ie); // use VfsIngressConsumer::consumeUnzipped to unzip

        public Builder addIngressPath(final FileObject path)
                throws FileSystemException {
            if (path.exists() && path.getType() == FileType.FOLDER) {
                ingressPaths.add(new IngressPath(path, Arrays.stream(path.getChildren())
                        .filter(file -> {
                            try {
                                return file.isFile() && file.isReadable();
                            } catch (FileSystemException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .map(file -> new IngressIndividual(file))
                        .map(IngressEntry.class::cast)
                        .collect(Collectors.toList())));
            }
            return this;
        }

        public Builder isGroup(final Function<FileObject, String> isGroup) {
            this.isGroup = isGroup;
            return this;
        }

        public Builder isGroupComplete(final Predicate<IngressGroup> isGroupComplete) {
            this.isGroupComplete = isGroupComplete;
            return this;
        }

        public Builder isSnapshotable(
                final QuadFunction<IngressIndividual, FileObject, FileObject, Audit, Boolean> transformBeforeSnapshot) {
            this.isSnapshotable = transformBeforeSnapshot;
            return this;
        }

        public Builder populateSnapshot(
                final QuadFunction<IngressIndividual, FileObject, FileObject, Audit, List<IngressIndividual>> transformAfterSnapshot) {
            this.populateSnapshot = transformAfterSnapshot;
            return this;
        }

        public Builder consumables(
                final QuadFunction<IngressIndividual, FileObject, FileObject, Audit, List<IngressIndividual>> consumables) {
            this.consumables = consumables;
            return this;
        }

        public VfsIngressConsumer build() throws FileSystemException {
            return new VfsIngressConsumer(this);
        }
    }

    public record IngressPath(FileObject path, List<IngressEntry> entries) {
    }

    public sealed interface IngressEntry permits IngressIndividual, IngressGroup {
    }

    public record IngressIndividual(@Nonnull FileObject entry) implements IngressEntry {
    }

    public record IngressGroup(@Nonnull String groupId, @Nonnull List<IngressIndividual> groupedEntries)
            implements IngressEntry {
    }

    public record AuditEvent(@Nonnull String nature, @Nonnull String message, Optional<FileObject> path,
            Optional<Exception> error) {
        AuditEvent(@Nonnull String nature, @Nonnull String message) {
            this(nature, message, Optional.empty(), Optional.empty());
        }

        AuditEvent(@Nonnull String nature, @Nonnull String message, Optional<FileObject> path) {
            this(nature, message, path, Optional.empty());
        }
    }

    public record Audit(List<AuditEvent> events) {
        public Audit addEvent(final AuditEvent event) {
            events().add(event);
            return this;
        }
    }

    public Audit getAudit() {
        return audit;
    }

    public void drain(final FileObject egressRoot, final Optional<UUID> sessionIdOpt) {
        sessionId = sessionIdOpt.orElse(UUID.randomUUID());
        try {
            sessionHome = egressRoot.resolveFile(sessionId.toString());
            snapshotHome = sessionHome.resolveFile("ingress");
            snapshotHome.createFolder();

            // workflow:
            // 1. find all files in all ingressable paths
            // 2. check if a file needs to be snapshotted (default is true)
            // 3. if a file is snapshotable, move it from the original location to snapshot
            // home
            // 4. find all the files in the snapshot and call these consumable
            // 5. for each consumable file, classify them into grouped or individual

            originalEntries = new ArrayList<>();
            snapshotEntries = new ArrayList<>();
            for (final var ingressPath : ingressPaths) {
                for (final var entry : ingressPath.entries()) {
                    if (entry instanceof IngressIndividual activeEntry) {
                        // check to see if we should ignore this entry or snapshot it
                        final var snapshot = isSnapshotable.apply(activeEntry, sessionHome, snapshotHome, audit);
                        if (!snapshot) {
                            continue;
                        }

                        // we're going to move the entry so hang on to the original for auditing
                        final var originalEntry = new IngressIndividual(activeEntry.entry());
                        originalEntries.add(originalEntry);

                        // move the file from its original location to the new location
                        final var dest = snapshotHome.resolveFile(activeEntry.entry().getName().getBaseName());
                        activeEntry.entry().moveTo(dest);
                        audit.addEvent(new AuditEvent("remove", originalEntry.entry().getPublicURIString(),
                                Optional.of(originalEntry.entry())));

                        // now see if we need to populate anything else other than the primary entry
                        final var populate = populateSnapshot.apply(activeEntry, sessionHome, snapshotHome, audit);
                        for (final var keep : populate) {
                            snapshotEntries.add(keep);
                            audit.addEvent(new AuditEvent("snapshot", keep.entry().getPublicURIString(),
                                    Optional.of(keep.entry())));
                        }
                    }
                }
            }

            consumeEntries = new ArrayList<>();
            for (final var entry : snapshotHome.getChildren()) {
                final var consume = consumables.apply(new IngressIndividual(entry),
                        sessionHome, snapshotHome, audit);
                for (final var c : consume) {
                    consumeEntries.add(c);
                    audit.addEvent(new AuditEvent("consume", c.entry().getPublicURIString(), Optional.of(c.entry())));
                }
            }

            groupedEntriesMap = new HashMap<>();
            individualEntries = new ArrayList<>();

            for (final var entry : consumeEntries) {
                final var groupId = isGroup.apply(entry.entry());
                if (groupId != null) {
                    groupedEntriesMap
                            .computeIfAbsent(groupId, k -> new ArrayList<>())
                            .add(entry);
                    audit.addEvent(new AuditEvent("grouped",
                            "[%s] %s".formatted(groupId, entry.entry().getPublicURIString(),
                                    Optional.of(entry.entry()))));
                } else {
                    individualEntries.add(entry);
                }
            }

            groupedEntries = groupedEntriesMap.entrySet().stream()
                    .map(e -> new IngressGroup(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());

            completeGroups = new ArrayList<>();
            incompleteGroups = new ArrayList<>();

            for (final var group : groupedEntries) {
                if (isGroupComplete.test(group)) {
                    completeGroups.add(group);
                } else {
                    incompleteGroups.add(group);
                    audit.addEvent(new AuditEvent("incomplete-group", group.groupId));
                }
            }

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public FileObject getSessionHome() {
        return sessionHome;
    }

    public FileObject getSnapshotHome() {
        return snapshotHome;
    }

    public List<IngressIndividual> getOriginalEntries() {
        return originalEntries;
    }

    public List<IngressIndividual> getSnapshotEntries() {
        return snapshotEntries;
    }

    public Map<String, List<IngressIndividual>> getGroupedEntriesMap() {
        return groupedEntriesMap;
    }

    public List<IngressIndividual> getIndividualEntries() {
        return individualEntries;
    }

    public List<IngressGroup> getGroupedEntries() {
        return groupedEntries;
    }

    public List<IngressGroup> getCompleteGroups() {
        return completeGroups;
    }

    public List<IngressGroup> getIncompleteGroups() {
        return incompleteGroups;
    }

    @FunctionalInterface
    public interface QuadFunction<T, U, V, W, R> {
        R apply(T t, U u, V v, W w);
    }

    /**
     * consumeUnzipped is meant to be passed into
     * Builder.consumables(VfsIngressConsumer::consumeUnzipped) and is designed to
     * unzip items in a zip file and put them into the snapshotHome for consumption.
     * It will return the entries in the ZIP file as consumable but not the ZIP file
     * itself.
     * 
     * @param individual   the ZIP file
     * @param sessionHome  where the session is being extracted
     * @param snapshotHome where the snapshots were created
     * @param audit        the audit trail
     * @return either entries of the ZIP or the original individual file if it's not
     *         a ZIP
     */
    public static List<IngressIndividual> consumeUnzipped(IngressIndividual individual, FileObject sessionHome,
            FileObject snapshotHome, Audit audit) {
        final var zipFile = individual.entry();
        if (!zipFile.getName().getBaseName().toLowerCase().endsWith(".zip")) {
            return List.of(individual);
        }

        final List<IngressIndividual> unzippedFiles = new ArrayList<>();
        FileObject fileObject = individual.entry();

        try (InputStream inputStream = fileObject.getContent().getInputStream();
                ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                // Flattening directory structure
                final var flattenedName = entry.getName().replaceAll(".*/", "");
                if (flattenedName.isEmpty()) {
                    continue; // Skip directories
                }

                final var unzippedFile = snapshotHome.resolveFile(flattenedName);
                if (unzippedFile.exists()) {
                    audit.addEvent(new AuditEvent("unzipped-will-overwrite",
                            "%s in %s overwrites %s".formatted(unzippedFile.getName().getBaseName(),
                                    zipFile.getPublicURIString(), unzippedFile.getPublicURIString())));
                }

                // Write file content using VFS
                try (var outputStream = unzippedFile.getContent().getOutputStream()) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zipInputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, len);
                    }
                }

                unzippedFiles.add(new IngressIndividual(unzippedFile));
                audit.addEvent(new AuditEvent("unzipped",
                        "%s from %s".formatted(unzippedFile.getName().getBaseName(), zipFile.getPublicURIString())));
                zipInputStream.closeEntry();
            }
        } catch (RuntimeException | IOException e) {
            audit.addEvent(new AuditEvent("exception", zipFile.getPublicURIString(), Optional.of(individual.entry()),
                    Optional.of(e)));
        } 

        return unzippedFiles;
    }
}
