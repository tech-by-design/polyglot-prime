package org.techbd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.techbd.model.csv.FileDetail;
import org.techbd.model.csv.FileType;
import org.techbd.orchestrate.csv.FileProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileProcessorTest {
    @TempDir
    Path tempDir;
    
    private Path demographicFile;
    private Path qeAdminFile;
    private Path screeningProfileFile;
    private Path screeningObservationFile;
    private Path invalidFile;

    @BeforeEach
    void setUp() throws IOException {
        // Create test files with sample content
        demographicFile = createFile("DEMOGRAPHIC_DATA_group1.csv", "demographic data");
        qeAdminFile = createFile("QE_ADMIN_DATA_group1.csv", "qe admin data");
        screeningProfileFile = createFile("SCREENING_PROFILE_DATA_group1.csv", "screening profile data");
        screeningObservationFile = createFile("SCREENING_OBSERVATION_DATA_group1.csv", "screening observation data");
        invalidFile = createFile("INVALID_FILE_group1.csv", "invalid data");
    }

    private Path createFile(String fileName, String content) throws IOException {
        Path filePath = tempDir.resolve(fileName);
        Files.writeString(filePath, content);
        return filePath;
    }

    @Test
    void testSuccessfulFileProcessing() throws IOException {
        // Arrange
        List<String> filePaths = Arrays.asList(
            demographicFile.toString(),
            qeAdminFile.toString(),
            screeningProfileFile.toString(),
            screeningObservationFile.toString()
        );

        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(filePaths);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("_group1"));
        List<FileDetail> group1Files = result.get("_group1");
        assertEquals(4, group1Files.size());
        
        // Verify all file types are present
        assertTrue(group1Files.stream().anyMatch(fd -> fd.fileType() == FileType.DEMOGRAPHIC_DATA));
        assertTrue(group1Files.stream().anyMatch(fd -> fd.fileType() == FileType.QE_ADMIN_DATA));
        assertTrue(group1Files.stream().anyMatch(fd -> fd.fileType() == FileType.SCREENING_PROFILE_DATA));
        assertTrue(group1Files.stream().anyMatch(fd -> fd.fileType() == FileType.SCREENING_OBSERVATION_DATA));
    }

    @Test
    void testInvalidFileHandling() throws IOException {
        // Arrange
        List<String> filePaths = Arrays.asList(
            invalidFile.toString(),
            demographicFile.toString()
        );

        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(filePaths);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("filesNotProcessed"));
        List<FileDetail> notProcessed = result.get("filesNotProcessed");
        assertEquals(1, notProcessed.size());

    }

    @Test
    void testMissingRequiredFiles() throws IOException {
        // Arrange
        List<String> filePaths = Arrays.asList(
            demographicFile.toString(),
            qeAdminFile.toString()
        );

        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(filePaths);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("_group1"));
        List<FileDetail> group1Files = result.get("_group1");
        assertEquals(2, group1Files.size());
        
    }

    @Test
    void testEmptyFileList() throws IOException {
        // Arrange
        List<String> filePaths = List.of();

        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(filePaths);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("filesNotProcessed"));
        assertTrue(result.get("filesNotProcessed").isEmpty());
    }

    @Test
    void testMultipleGroups() throws IOException {
        // Arrange
        Path demographicFile2 = createFile("DEMOGRAPHIC_DATA_group2.csv", "demographic data 2");
        Path qeAdminFile2 = createFile("QE_ADMIN_DATA_group2.csv", "qe admin data 2");

        List<String> filePaths = Arrays.asList(
            demographicFile.toString(),
            qeAdminFile.toString(),
            demographicFile2.toString(),
            qeAdminFile2.toString()
        );

        // Act
        Map<String, List<FileDetail>> result = FileProcessor.processAndGroupFiles(filePaths);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("_group1"));
        assertTrue(result.containsKey("_group2"));
        assertEquals(2, result.get("_group1").size());
        assertEquals(2, result.get("_group2").size());
    }
}