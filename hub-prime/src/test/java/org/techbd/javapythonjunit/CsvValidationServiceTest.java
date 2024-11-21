package org.techbd.javapythonjunit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CsvValidationServiceTest {

    private CsvValidationService csvValidationService;

    @BeforeEach
    void setUp() {
        csvValidationService = Mockito.spy(new CsvValidationService());
        // Set up mock values for @Value fields
        ReflectionTestUtils.setField(csvValidationService, "pythonScriptPath", "/path/to/mock-script.py");
        ReflectionTestUtils.setField(csvValidationService, "pythonExecutable", "python3");
        ReflectionTestUtils.setField(csvValidationService, "packagePath", "/mock/package");
        ReflectionTestUtils.setField(csvValidationService, "outputPath", "/mock/output");
        ReflectionTestUtils.setField(csvValidationService, "basePath", "/mock/base");
        ReflectionTestUtils.setField(csvValidationService, "file1", "file1.csv");
        ReflectionTestUtils.setField(csvValidationService, "file2", "file2.csv");
        ReflectionTestUtils.setField(csvValidationService, "file3", "file3.csv");
    }

    @Test
    void testValidateCsvGroup_success() throws Exception {
        // Mock the ProcessBuilder
        ProcessBuilder mockProcessBuilder = mock(ProcessBuilder.class);
        when(csvValidationService.createProcessBuilder()).thenReturn(mockProcessBuilder);

        // Mock the Process
        Process mockProcess = mock(Process.class);
        when(mockProcessBuilder.start()).thenReturn(mockProcess);

        // Mock the process output
        when(mockProcess.getInputStream()).thenReturn(
                new ByteArrayInputStream("Validation succeeded\n".getBytes()));
        when(mockProcess.getErrorStream()).thenReturn(
                new ByteArrayInputStream("".getBytes()));
        when(mockProcess.waitFor()).thenReturn(0);

        // Call the method
        Map<String, Object> result = csvValidationService.validateCsvGroup();

        // Debug the result
        System.out.println("Actual result: '" + result.get("result") + "'");

        // Verify the result
        assertNotNull(result);
        assertEquals("Validation succeeded", result.get("result").toString().trim());
    }

    @Test
    public void testValidateCsvGroup_InvalidInput_ThrowsException() {

        Exception exception = assertThrows(
                Exception.class,
                () -> csvValidationService.validateCsvGroup(),
                "Should throw IllegalArgumentException when fewer than 3 files are provided");

        assertEquals(
                "Failed to validate CSV files",
                exception.getMessage(),
                "Exception message should match expected");

    }

    @Test
    public void testValidateCsvGroup_NonExistentFiles() {

        Exception exception = assertThrows(
                Exception.class,
                () -> csvValidationService.validateCsvGroup(),
                "Should throw an exception for non-existent files");

        assertTrue(
                exception.getMessage().contains("Failed to validate CSV files"),
                "Exception message should indicate validation failure");
    }

    @Test
    void testValidateCsvGroup_NonZeroExitCode2() throws Exception {
        // Mock the ProcessBuilder
        ProcessBuilder mockProcessBuilder = mock(ProcessBuilder.class);
        when(csvValidationService.createProcessBuilder()).thenReturn(mockProcessBuilder);

        // Mock the Process
        Process mockProcess = mock(Process.class);
        when(mockProcessBuilder.start()).thenReturn(mockProcess);

        // Mock the process output and non-zero exit code
        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(mockProcess.getErrorStream()).thenReturn(
                new ByteArrayInputStream("Some error occurred".getBytes()));
        when(mockProcess.waitFor()).thenReturn(1);

        // Assert exception
        Exception exception = assertThrows(Exception.class, csvValidationService::validateCsvGroup);
        System.out.println("Actual exception message: " + exception.getMessage());

        verify(mockProcessBuilder, times(1)).start();
    }

    @Test
    void testValidateCsvGroup_NonZeroExitCode1() throws Exception {
        // Mock the ProcessBuilder
        ProcessBuilder mockProcessBuilder = mock(ProcessBuilder.class);
        when(csvValidationService.createProcessBuilder()).thenReturn(mockProcessBuilder);

        // Mock the Process
        Process mockProcess = mock(Process.class);
        when(mockProcessBuilder.start()).thenReturn(mockProcess);

        // Mock the process output and non-zero exit code
        when(mockProcess.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(mockProcess.getErrorStream()).thenReturn(
                new ByteArrayInputStream("Some error occurred".getBytes()));
        when(mockProcess.waitFor()).thenReturn(1);

        // Assert exception
        Exception exception = assertThrows(Exception.class, csvValidationService::validateCsvGroup);
        System.out.println("Actual exception message: " + exception.getMessage());

        // Assertions for exception message
        assertNotNull(exception.getMessage(), "Exception message should not be null");

        // Verifications
        verify(mockProcessBuilder, times(1)).start();
        verify(mockProcess, times(1)).waitFor();
        verify(mockProcess, times(1)).getErrorStream();
    }

}
