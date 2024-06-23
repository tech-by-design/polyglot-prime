package lib.aide.vfs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;
import org.junit.jupiter.api.Test;

public class VfsIngressConsumerTest {

    @Test
    public void testVfsIngressConsumer() throws FileSystemException {
        final var fsManager = VFS.getManager();

        // Setup RAM file system
        final var sourceDir = fsManager.resolveFile("ram://sourceDir");
        sourceDir.createFolder();

        // a valid "complete" grouped files set
        createFile(sourceDir, "file1_group1.csv");
        createFile(sourceDir, "file2_group1.csv");
        createFile(sourceDir, "file3_group1.csv");

        // an ivalid "incomplete" grouped files set
        createFile(sourceDir, "file1_group2.csv");
        createFile(sourceDir, "file2_group2.csv");

        // individual (ungrouped) files
        createFile(sourceDir, "individual1.csv");
        createFile(sourceDir, "individual2.csv");
        createFile(sourceDir, "individual.zip");

        // Create egress root
        final var egressRoot = fsManager.resolveFile("ram://egress");
        egressRoot.createFolder();

        // Define grouping logic
        final Function<FileObject, String> isGroup = file -> {
            final var baseName = file.getName().getBaseName();
            if (baseName.matches("file[0-9]+_group[0-9]+\\.csv")) {
                return baseName.split("_")[1].replace(".csv", "");
            }
            return null;
        };

        // "complete" means there are three in the group
        final Predicate<VfsIngressConsumer.IngressGroup> isGroupComplete = group -> group.groupedEntries().size() == 3;

        // Initialize VfsIngressConsumer
        final var consumer = new VfsIngressConsumer.Builder()
                .addIngressPath(sourceDir)
                .isGroup(isGroup)
                .isGroupComplete(isGroupComplete)
                // TODO: figure out how to test .zip consumeUnzipped
                // .consumables(VfsIngressConsumer::consumeUnzipped)
                .build();

        // do the actual work
        consumer.drain(egressRoot, Optional.of(UUID.randomUUID()));

        assertThat(consumer.getSessionId()).isNotNull();
        assertThat(consumer.getSessionHome().getName().getURI()).isNotNull();
        assertThat(consumer.getSnapshotHome().getName().getURI()).isNotNull();

        assertThat(consumer.getGroupedEntries()).hasSize(2);
        assertThat(consumer.getCompleteGroups()).hasSize(1);
        assertThat(consumer.getIncompleteGroups()).hasSize(1);
        assertThat(consumer.getIndividualEntries()).hasSize(3); // 2 individual .csv files and 1 .zip file
        assertThat(consumer.getOriginalEntries()).hasSize(8);
        assertThat(consumer.getSnapshotEntries()).hasSize(8);

        assertThat(consumer.getSessionId()).isNotNull();

        assertThat(consumer.getAudit().events()).hasSize(30);
    }

    private void createFile(final FileObject parent, final String fileName) throws FileSystemException {
        final var file = parent.resolveFile(fileName);
        file.createFile();
    }
}
