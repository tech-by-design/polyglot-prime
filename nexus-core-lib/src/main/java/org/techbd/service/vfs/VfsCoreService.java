package org.techbd.service.vfs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;
import org.springframework.stereotype.Service;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;

@Service
public class VfsCoreService {
    private final TemplateLogger logger;
    public VfsCoreService(AppLogger appLogger) {
        this.logger = appLogger.getLogger(VfsCoreService.class);
    }
    /**
     * Creates a VFS consumer with the specified configuration.
     *
     * @param ingressPath      The path where ingress files are located.
     * @param groupingFunction A function to group files within the VFS consumer.
     * @param groupValidator   A function to validate the created groups.
     * @return A configured VfsIngressConsumer instance.
     * @throws FileSystemException If there is an error creating the consumer.
     */
    public VfsIngressConsumer createConsumer(
            FileObject ingressPath,
            Function<FileObject, String> groupingFunction,
            Function<VfsIngressConsumer.IngressGroup, Boolean> groupValidator) throws FileSystemException {
        try {
            return new VfsIngressConsumer.Builder()
                    .addIngressPath(ingressPath)
                    .isGroup(groupingFunction)
                    .isGroupComplete(this::isGroupComplete)
                    .isSnapshotable(this::isZipFile)
                    .populateSnapshot((ingressEntry, file, dir, audit) -> List.of())
                    .consumables(VfsIngressConsumer::consumeUnzipped)
                    .build();
        } catch (FileSystemException e) {
            logger.error("Error creating VFS consumer: {}", e.getMessage());
            throw e;
        }
    }
    // return new VfsIngressConsumer.Builder()
    // .addIngressPath(ingressPath)
    // .isGroup(groupingFunction)
    // .isGroupComplete(this::isGroupComplete)
    // .isSnapshotable(this::isZipFile)
    // .populateSnapshot((ingressEntry, file, dir, audit) -> List.of())
    // .consumables(VfsIngressConsumer::consumeUnzipped)
    // .build();
    // }

    /**
     * Resolves a file path to a FileObject.
     *
     * @param path The path to resolve.
     * @return The resolved FileObject.
     * @throws FileSystemException If there is an error during resolution.
     */
    public FileObject resolveFile(String path) throws FileSystemException {
        return VFS.getManager().resolveFile(path);
    }

    /**
     * Validates and creates directories if they do not already exist.
     *
     * @param directories The directories to validate and create.
     * @throws FileSystemException If there is an error creating the directories.
     */
    public void validateAndCreateDirectories(FileObject... directories) throws FileSystemException {
        try {
            for (FileObject dir : directories) {
                if (!dir.exists()) {
                    logger.info("Creating directory: {}", dir.getName().getPath());
                    dir.createFolder();
                }
            }
        } catch (FileSystemException e) {
            logger.error("Error creating directories: {}", e.getMessage());
            throw e;
        }

    }

    /**
     * Determines if a group of files is complete.
     *
     * @param group The group to check.
     * @return True if the group is complete; false otherwise.
     */
    private boolean isGroupComplete(VfsIngressConsumer.IngressGroup group) {
        if (group == null || group.groupedEntries().isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * Processes files using the specified VFS consumer and saves results to the
     * target directory.
     *
     * @param consumer        The VFS consumer to use for processing files.
     * @param targetDirectory The directory where processed files will be stored.
     * @return A UUID representing the session ID for the processing session.
     * @throws FileSystemException If there is an error during file processing.
     */
    public UUID processFiles(VfsIngressConsumer consumer, FileObject targetDirectory,String zipFileInteractionId) throws FileSystemException {

        UUID sessionId = UUID.fromString(zipFileInteractionId);
        try {
            consumer.drain(targetDirectory, Optional.of(sessionId));
            logAuditEvents(consumer);
            return sessionId;
        } catch (Exception e) {
            logger.error("File processing failed for session {}", sessionId, e);
            throw new FileSystemException("File processing failed", e);
        }
    }

    /**
     * Checks if a specified file exists.
     *
     * @param file The file to check for existence.
     * @return True if the file exists; false otherwise.
     * @throws FileSystemException If there is an error accessing the file.
     */
    public boolean fileExists(FileObject file) throws FileSystemException {
        try {
            return file != null && file.exists();
        } catch (FileSystemException e) {
            logger.error("Error checking if file exists: {}", e.getMessage());
            throw e;
        }

    }

    /**
     * Determines if a file is a zip file based on its extension.
     *
     * @param ingressEntry The file entry to check.
     * @param file         The file to examine.
     * @param dir          The directory containing the file.
     * @param audit        The audit context.
     * @return True if the file is a zip file; false otherwise.
     */
    private boolean isZipFile(VfsIngressConsumer.IngressIndividual ingressEntry,
            FileObject file,
            FileObject dir,
            VfsIngressConsumer.Audit audit) {
        return "zip".equalsIgnoreCase(ingressEntry.entry().getName().getExtension());
    }

    private void logAuditEvents(VfsIngressConsumer consumer) {
        consumer.getAudit().events().forEach(event -> {
            if (event.path().isPresent()) {
                logger.info("Audit event: {} - {} - {}",
                        event.nature(),
                        event.message(),
                        event.path().get().getName().getBaseName());
            } else {
                logger.info("Audit event: {} - {}",
                        event.nature(),
                        event.message());
            }
        });
    }
}
