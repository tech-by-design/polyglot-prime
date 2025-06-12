package org.techbd.service.csv.engine;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.model.csv.FileDetail;
import org.techbd.model.csv.FileType;

public class FileProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(FileProcessor.class);

    public static Map<String, List<FileDetail>> processAndGroupFiles(final List<String> filePaths) throws IOException {
        final Map<String, List<FileDetail>> groupedFiles = new HashMap<>();
        final List<FileDetail> filesNotProcessed = new ArrayList<>();
        final Map<String, Boolean> groupHasInvalidEncoding = new HashMap<>();

        for (final String filePath : filePaths) {
            final Path path = Path.of(filePath);
            final String fileName = path.getFileName().toString();
            try {
                final FileType fileType = FileType.fromFilename(fileName);
                String groupKey = fileName.substring(fileType.name().length(), fileName.lastIndexOf(".csv"));

                String content = null;
                boolean isUtf8 = true;
                String reason = null;

                try {
                    content = Files.readString(path, StandardCharsets.UTF_8);
                } catch (MalformedInputException  e) {
                    isUtf8 = false;
                    reason = "File is not UTF-8 encoded: " + e.getMessage();
                    groupHasInvalidEncoding.put(groupKey, true);
                }

                FileDetail fileDetail = new FileDetail(fileName, fileType, content, filePath, isUtf8, reason);
                groupedFiles.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(fileDetail);

            } catch (IllegalArgumentException e) {
                String reason = "Invalid file prefix: " + e.getMessage();
                filesNotProcessed.add(new FileDetail(fileName, null, null, filePath, false, reason));
            } catch (IOException e) {
                String reason = "IOException during processing: " + e.getMessage();
                filesNotProcessed.add(new FileDetail(fileName, null, null, filePath, false, reason));
            }
        }

        for (String invalidGroupKey : groupHasInvalidEncoding.keySet()) {
            List<FileDetail> group = groupedFiles.remove(invalidGroupKey);
            if (group != null) {
                List<String> nonUtf8Files = group.stream()
                        .filter(fd -> !fd.utf8Encoded())
                        .map(FileDetail::filename)
                        .toList();

                String reason = "Not processed as other files in the group were not UTF-8 encoded. Group blocked by:"
                        + String.join(", ", nonUtf8Files);

                for (FileDetail fd : group) {
                    FileDetail failed = new FileDetail(
                            fd.filename(),
                            fd.fileType(),
                            null,
                            fd.filePath(),
                            fd.utf8Encoded(),
                            !fd.utf8Encoded() ? "File is not UTF-8 encoded" : reason);
                    filesNotProcessed.add(failed);
                }
            }
        }

        groupedFiles.put("filesNotProcessed", filesNotProcessed);
        return groupedFiles;
    }

}
