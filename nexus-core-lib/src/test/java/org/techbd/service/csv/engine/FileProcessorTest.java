package org.techbd.service.csv.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.techbd.model.csv.FileDetail;
import org.techbd.model.csv.FileType;

class FileProcessorTest {
    @TempDir
    Path tempDir;
    
    private Path demographicFile;
    private Path qeAdminFile;
    private Path screeningProfileFile;
    private Path screeningObservationFile;
    private Path invalidFile;

    // @BeforeEach
    // void setUp() throws Exception {
    //     demographicFile = createFile("SDOH_PtInfo_group1.csv", readFileContent("src/test/resources/org/techbd/csv/data/latestResources/DEMOGRAPHIC_DATA_Care_Ridge_SCN_SDOH_PtInfo_20240223102001.csv"));
    //     qeAdminFile = createFile("SDOH_QEadmin_group1.csv", readFileContent("src/test/resources/org/techbd/csv/data/latestResources/QE_ADMIN_DATA_Care_Ridge_SCN_SDOH_QEadmin_20240223102001.csv"));
    //     screeningProfileFile = createFile("SDOH_ScreeningProf_group1.csv", readFileContent("src/test/resources/org/techbd/csv/data/latestResources/SCREENING_PROFILE_DATA_Care_Ridge_SCN_ScreeningProf_20240223102001.csv"));
    //     screeningObservationFile = createFile("SDOH_ScreeningObs_group1.csv", readFileContent("src/test/resources/org/techbd/csv/data/latestResources/SCREENING_OBSERVATION_DATA_Care_Ridge_SCN_SDOH_ScreeningObs_20240223102001.csv"));
    //     invalidFile = createFile("INVALID_FILE_group1.csv", readFileContent("src/test/resources/org/techbd/csv/data/latestResources/SCREENING_OBSERVATION_DATA_Care_Ridge_SCN_SDOH_ScreeningObs_20240223102001.csv"));
    // }
    @BeforeEach
    void setUp() throws IOException {
    // Create test files with sample content using new naming
    demographicFile = createFile("SDOH_PtInfo_group1.csv", "demographic data");
    qeAdminFile = createFile("SDOH_QEadmin_group1.csv", "qe admin data");
    screeningProfileFile = createFile("SDOH_ScreeningProf_group1.csv", "screening profile data");
    screeningObservationFile = createFile("SDOH_ScreeningObs_group1.csv", "screening observation data");
    invalidFile = createFile("INVALID_FILE_group1.csv", "invalid data");
}

    // Method to read the content from the resources
    private String readFileContent(String relativePath) throws Exception {
        Path path = Paths.get(getClass().getClassLoader().getResource(relativePath).toURI());
        return Files.readString(path);
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
        assertTrue(group1Files.stream().anyMatch(fd -> fd.fileType() == FileType.SDOH_PtInfo));
        assertTrue(group1Files.stream().anyMatch(fd -> fd.fileType() == FileType.SDOH_QEadmin));
        assertTrue(group1Files.stream().anyMatch(fd -> fd.fileType() == FileType.SDOH_ScreeningProf));
        assertTrue(group1Files.stream().anyMatch(fd -> fd.fileType() == FileType.SDOH_ScreeningObs));
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
        Path demographicFile2 = createFile("SDOH_PtInfo_group2.csv", "demographic data 2");
        Path qeAdminFile2 = createFile("SDOH_QEadmin_group2.csv", "qe admin data 2");

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
    @Test
    void testProcessAndGroupFilesWithoutUtf8Encoding() throws IOException {
        String testFilePath = "src/test/resources/org/techbd/csv/SDOH_PtInfo-ANSI.csv";
        Map<String, List<FileDetail>> groupedFiles = FileProcessor.processAndGroupFiles(List.of(testFilePath));
        List<FileDetail> processedFiles = groupedFiles.get("filesNotProcessed");        
        assertNotNull(processedFiles, "Files should be processed and grouped.");
        assertEquals(1, processedFiles.size(), "There should be one file in the 'filesNotProcessed' group.");        
        FileDetail fileDetail = processedFiles.get(0);
        assertFalse(fileDetail.utf8Encoded(), "File should not be UTF-8 encoded.");
        assertEquals("File is not UTF-8 encoded", fileDetail.reason(), "The reason should indicate the file is not UTF-8 encoded.");
    }
    @Test
    void testProcessAndGroupFilesWithUtf8Encoding() throws IOException {
        String testFilePath = "src/test/resources/org/techbd/csv/SDOH_PtInfo.csv";
        Map<String, List<FileDetail>> groupedFiles = FileProcessor.processAndGroupFiles(List.of(testFilePath));        
        List<FileDetail> processedFiles = groupedFiles.get("filesNotProcessed");        
        assertNotNull(processedFiles, "Files should be processed and grouped.");
        assertEquals(0, processedFiles.size(), "There should be not be any  file in the 'filesNotProcessed' group.");        
    
    }
}