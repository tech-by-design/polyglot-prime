package org.techbd.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.techbd.service.http.hub.prime.AppConfig;
import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CsvValidationServiceTest {

        private CsvValidationService csvValidationService;
        private AppConfig appConfig;

        @BeforeEach
        void setUp() {
                // Create and configure mock AppConfig
                appConfig = mock(AppConfig.class);

                // Create the nested Validation record
                AppConfig.CsvValidation.Validation validation = new AppConfig.CsvValidation.Validation(
                                "/path/to/mock-script.py", // pythonScriptPath
                                "python3", // pythonExecutable
                                "/mock/package", // packagePath
                                "/mock/output", // outputPath
                                "/mock/base", // basePath
                                "file1.csv", // file1
                                "file2.csv", // file2
                                "file3.csv", // file3
                                "file4.csv", // file4
                                "file5.csv", // file5
                                "file6.csv", // file6
                                "file7.csv" // file7
                );

                // Create CsvValidation with the Validation record
                AppConfig.CsvValidation csvValidation = new AppConfig.CsvValidation(validation);

                // Mock the AppConfig to return the CsvValidation record
                when(appConfig.getCsv()).thenReturn(csvValidation);

                // Create service with mock config
                csvValidationService = Mockito.spy(new CsvValidationService(appConfig));
        }

        @Test
        void testValidateCsvGroup_success() throws Exception {
                // Mock the ProcessBuilder and Process
                ProcessBuilder mockProcessBuilder = mock(ProcessBuilder.class);
                Process mockProcess = mock(Process.class);

                when(csvValidationService.createProcessBuilder()).thenReturn(mockProcessBuilder);
                when(mockProcessBuilder.start()).thenReturn(mockProcess);
                when(mockProcess.getInputStream())
                                .thenReturn(new ByteArrayInputStream("Validation succeeded\n".getBytes()));
                when(mockProcess.getErrorStream()).thenReturn(new ByteArrayInputStream("".getBytes()));
                when(mockProcess.waitFor()).thenReturn(0);

                // Execute and verify
                Map<String, Object> result = csvValidationService.validateCsvGroup();

                assertNotNull(result);
                assertEquals("Validation succeeded", result.get("result").toString().trim());
                verify(appConfig).getCsv();
                verify(mockProcess).waitFor();
                verify(mockProcess, times(1)).getInputStream();
        }

        @Test
        void testValidateCsvGroup_InvalidConfig() {
                when(appConfig.getCsv().validation()).thenReturn(null);

                Exception exception = assertThrows(
                                Exception.class,
                                () -> csvValidationService.validateCsvGroup());

                assertEquals("Failed to validate CSV files", exception.getMessage());
                verify(appConfig).getCsv();
        }

        @Test
        void testCreateProcessBuilder() {
                ProcessBuilder pb = csvValidationService.createProcessBuilder();
                assertNotNull(pb);
        }

        @Test
        void testValidateCsvGroup_NullConfig() {
                // Mock null validation configuration
                when(appConfig.getCsv().validation()).thenReturn(null);

                // Verify that an IllegalStateException is thrown
                assertThrows(Exception.class, () -> {
                        csvValidationService.validateCsvGroup();
                });
        }
}
