package org.techbd.orchestrate.csv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.model.csv.FileDetail;
import org.techbd.model.csv.FileType;

public class FileProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(FileProcessor.class);

    public static Map<String, List<FileDetail>> processAndGroupFiles(final List<String> filePaths)
            throws IOException {
        final Map<String, List<FileDetail>> groupedFiles = new HashMap<>();
        final Set<FileType> requiredFileTypes = EnumSet.allOf(FileType.class); // Required file types for validation
        List<FileDetail> filesNotProcessed = new ArrayList<>();
        for (final String filePath : filePaths) {
            final Path path = Path.of(filePath);
            final String fileName = path.getFileName().toString();
            try {                
                final FileType fileType = FileType.fromFilename(fileName);
                String fileContent = Files.readString(path);
                String groupKey = fileName.substring(fileType.name().length(), fileName.lastIndexOf(".csv"));
                FileDetail fileDetail = new FileDetail(fileName, fileType, fileContent,filePath);
                groupedFiles.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(fileDetail);
            } catch (final IllegalArgumentException e) {
                LOG.error("Error processing file type for: " + filePath + " - " + e.getMessage());
                filesNotProcessed.add(new FileDetail(fileName, null, null, null));
            }
        }
        for (Map.Entry<String, List<FileDetail>> entry : groupedFiles.entrySet()) {
            String groupKey = entry.getKey();
            List<FileDetail> filesInGroup = entry.getValue();
            Set<FileType> presentFileTypes = filesInGroup.stream()
                    .map(FileDetail::fileType)
                    .collect(Collectors.toSet());

            for (FileType requiredFileType : requiredFileTypes) {
                if (!presentFileTypes.contains(requiredFileType)) {
                    String missingFile = requiredFileType.name().toLowerCase() + groupKey + ".csv";
                    //throw new IllegalArgumentException("Missing required file: " + missingFile);
                LOG.error("Missing required file: " + missingFile);
                }
            }
        }
        groupedFiles.put("filesNotProcessed", filesNotProcessed);
        return groupedFiles;
    }
}
