package org.techbd.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.FileDetail;
import org.techbd.model.csv.FileType;
import org.techbd.model.csv.PayloadAndValidationOutcome;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.converters.csv.CsvToFhirConverter;
import org.techbd.service.http.hub.prime.api.FHIRService;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.util.CsvConversionUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class CsvBundleProcessorServiceTest {

    @Mock
    private CsvToFhirConverter csvToFhirConverter;

    @Mock
    private FHIRService fhirService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private UdiPrimeJpaConfig udiPrimeJpaConfig;
    private CsvBundleProcessorService csvBundleProcessorService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        csvBundleProcessorService = new CsvBundleProcessorService(csvToFhirConverter, fhirService,
                udiPrimeJpaConfig);
    }

    @Test
    void shouldProcessPayloadAndPopulatePatients() throws Exception {
        String masterInteractionId = "master-int-id";
        Map<String, PayloadAndValidationOutcome> payloadAndValidationOutcomes = new HashMap<>();
        FileDetail demographicFile = createFileDetail(
                "src/test/resources/org/techbd/csv/data/multipatient_singleencounter/DEMOGRAPHIC_DATA_partner1-test-20241128-testcase1.csv",
                FileType.DEMOGRAPHIC_DATA);
        FileDetail screeningProfileFile = createFileDetail(
                "src/test/resources/org/techbd/csv/data/multipatient_singleencounter/SCREENING_PROFILE_DATA_partner1-test-20241128-testcase1.csv",
                FileType.SCREENING_PROFILE_DATA);
        FileDetail qeAdminFile = createFileDetail(
                "src/test/resources/org/techbd/csv/data/multipatient_singleencounter/QE_ADMIN_DATA_partner1-test-20241128-testcase1.csv",
                FileType.QE_ADMIN_DATA);
        FileDetail screeningObservationFile = createFileDetail(
                "src/test/resources/org/techbd/csv/data/multipatient_singleencounter/SCREENING_OBSERVATION_DATA_partner1-test-20241128-testcase1.csv",
                FileType.SCREENING_OBSERVATION_DATA);

        List<FileDetail> fileDetails = List.of(demographicFile, screeningProfileFile, qeAdminFile,
                screeningObservationFile);
        PayloadAndValidationOutcome outcome = new PayloadAndValidationOutcome(fileDetails, true,
                "group-int-id",new HashMap<>(),new HashMap<>());
        payloadAndValidationOutcomes.put("key1", outcome);
        try (MockedStatic<CsvConversionUtil> mockedStatic = mockStatic(CsvConversionUtil.class)) {
            mockedStatic.when(() -> CsvConversionUtil.convertCsvStringToDemographicData(anyString()))
                    .thenReturn(Map.of(
                            "patient1", List.of(new DemographicData()),
                            "patient2", List.of(new DemographicData())));
            mockedStatic.when(() -> CsvConversionUtil.convertCsvStringToScreeningProfileData(anyString()))
                    .thenReturn(Map.of(
                            "patient1", List.of(new ScreeningProfileData()),
                            "patient2", List.of(new ScreeningProfileData())));
            mockedStatic.when(() -> CsvConversionUtil.convertCsvStringToQeAdminData(anyString()))
                    .thenReturn(Map.of(
                            "patient1_encounter1", List.of(new QeAdminData()),
                            "patient2_encounter1", List.of(new QeAdminData())));
            mockedStatic.when(
                    () -> CsvConversionUtil.convertCsvStringToScreeningObservationData(anyString()))
                    .thenReturn(Map.of(
                            "patient1_encounter1", List.of(new ScreeningObservationData()),
                            "patient2_encounter1",
                            List.of(new ScreeningObservationData())));
        }
        String mockBundle = getMockBundleJson();
        when(csvToFhirConverter.convert(any(DemographicData.class), any(QeAdminData.class),
                any(ScreeningProfileData.class), anyList(), anyString()))
                .thenReturn(mockBundle);

        OperationOutcome mockOutcome = new OperationOutcome();
        when(fhirService.processBundle(eq(mockBundle), anyString(), any(), any(), any(), any(), any(),
                anyString(),
                eq(false), eq(false), eq(false), eq(request), eq(response), any(), eq(true), any(), anyString(), anyString(),anyString(),anyString(),anyString(),anyString()))
                .thenReturn(mockOutcome);
        List<Object> result = csvBundleProcessorService.processPayload(masterInteractionId,
                payloadAndValidationOutcomes,new ArrayList<>(),
                request, response, "tenantId","test.zip");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(2, result.size());
        assertEquals(mockOutcome, result.get(0));
        verify(csvToFhirConverter, times(2)).convert(any(DemographicData.class), any(QeAdminData.class),
                any(ScreeningProfileData.class), anyList(), anyString());
        verify(fhirService, times(2)).processBundle(eq(mockBundle), anyString(), any(), any(), any(), any(),
                any(),
                anyString(), eq(false), eq(false), eq(false), eq(request), eq(response), any(),
                eq(true), any(), anyString(), anyString(),anyString(),anyString(),anyString(),anyString());
    }

    @Test
    void shouldProcessPayloadAndPopulatePatientsWithMultipleEncounters() throws Exception {
        String masterInteractionId = "master-int-id";
        Map<String, PayloadAndValidationOutcome> payloadAndValidationOutcomes = new HashMap<>();

        // Define file paths for test case with multiple encounters
        FileDetail demographicFile = createFileDetail(
                "src/test/resources/org/techbd/csv/data/singlepatient_multiencounter/DEMOGRAPHIC_DATA_partner1-test-20241128-testcase1.csv",
                FileType.DEMOGRAPHIC_DATA);
        FileDetail screeningProfileFile = createFileDetail(
                "src/test/resources/org/techbd/csv/data/singlepatient_multiencounter/SCREENING_PROFILE_DATA_partner1-test-20241128-testcase1.csv",
                FileType.SCREENING_PROFILE_DATA);
        FileDetail qeAdminFile = createFileDetail(
                "src/test/resources/org/techbd/csv/data/singlepatient_multiencounter/QE_ADMIN_DATA_partner1-test-20241128-testcase1.csv",
                FileType.QE_ADMIN_DATA);
        FileDetail screeningObservationFile = createFileDetail(
                "src/test/resources/org/techbd/csv/data/singlepatient_multiencounter/SCREENING_OBSERVATION_DATA_partner1-test-20241128-testcase1.csv",
                FileType.SCREENING_OBSERVATION_DATA);

        List<FileDetail> fileDetails = List.of(demographicFile, screeningProfileFile, qeAdminFile,
                screeningObservationFile);
        PayloadAndValidationOutcome outcome = new PayloadAndValidationOutcome(fileDetails, true,
                "group-int-id",new HashMap<>(),new HashMap<>());
        payloadAndValidationOutcomes.put("key1", outcome);

        try (MockedStatic<CsvConversionUtil> mockedStatic = mockStatic(CsvConversionUtil.class)) {
            mockedStatic.when(() -> CsvConversionUtil.convertCsvStringToDemographicData(anyString()))
                    .thenReturn(Map.of(
                            "patient1", List.of(new DemographicData())));
            mockedStatic.when(() -> CsvConversionUtil.convertCsvStringToScreeningProfileData(anyString()))
                    .thenReturn(Map.of(
                            "patient1", List.of(new ScreeningProfileData())));
            mockedStatic.when(() -> CsvConversionUtil.convertCsvStringToScreeningProfileData(anyString()))
                    .thenReturn(Map.of(
                            "patient1_encounter1", List.of(new ScreeningProfileData()),
                            "patient1_encounter2", List.of(new ScreeningProfileData())));
            mockedStatic.when(
                    () -> CsvConversionUtil.convertCsvStringToScreeningObservationData(anyString()))
                    .thenReturn(Map.of(
                            "patient1_encounter1", List.of(new ScreeningObservationData()),
                            "patient1_encounter2",
                            List.of(new ScreeningObservationData())));
        }

        String mockBundle = getMockBundleJson();
        when(csvToFhirConverter.convert(any(DemographicData.class), any(QeAdminData.class),
                any(ScreeningProfileData.class), anyList(), anyString()))
                .thenReturn(mockBundle);

        OperationOutcome mockOutcome = new OperationOutcome();
        when(fhirService.processBundle(eq(mockBundle), anyString(), any(), any(), any(), any(), any(),
                anyString(),
                eq(false), eq(false), eq(false), eq(request), eq(response), any(), eq(true), any(), anyString(), anyString(),anyString(),anyString(),anyString(),anyString()))
                .thenReturn(mockOutcome);

        List<Object> result = csvBundleProcessorService.processPayload(masterInteractionId,
                payloadAndValidationOutcomes,new ArrayList<>(),
                request, response, "tenantId","test.zip");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(2, result.size());
        verify(csvToFhirConverter, times(2)).convert(any(DemographicData.class), any(QeAdminData.class),
                any(ScreeningProfileData.class), anyList(), anyString());
        verify(fhirService, times(2)).processBundle(eq(mockBundle), anyString(), any(), any(), any(), any(),
                any(),
                anyString(), eq(false), eq(false), eq(false), eq(request), eq(response), any(),
                eq(true), any(), anyString(), anyString(),anyString(),anyString(),anyString(),anyString());
    }

    @Test
    void shouldProcessPayloadAndHandleFutureFailures() throws Exception {
        String masterInteractionId = "master-int-id";
        Map<String, PayloadAndValidationOutcome> payloadAndValidationOutcomes = new HashMap<>();
        FileDetail demographicFile = createFileDetail(
                "src/test/resources/org/techbd/csv/data/multipatient_singleencounter/DEMOGRAPHIC_DATA_partner1-test-20241128-testcase1.csv",
                FileType.DEMOGRAPHIC_DATA);
        FileDetail screeningProfileFile = createFileDetail(
                "src/test/resources/org/techbd/csv/data/multipatient_singleencounter/SCREENING_PROFILE_DATA_partner1-test-20241128-testcase1.csv",
                FileType.SCREENING_PROFILE_DATA);
        FileDetail qeAdminFile = createFileDetail(
                "src/test/resources/org/techbd/csv/data/multipatient_singleencounter/QE_ADMIN_DATA_partner1-test-20241128-testcase1.csv",
                FileType.QE_ADMIN_DATA);
        FileDetail screeningObservationFile = createFileDetail(
                "src/test/resources/org/techbd/csv/data/multipatient_singleencounter/SCREENING_OBSERVATION_DATA_partner1-test-20241128-testcase1.csv",
                FileType.SCREENING_OBSERVATION_DATA);

        List<FileDetail> fileDetails = List.of(demographicFile, screeningProfileFile, qeAdminFile,
                screeningObservationFile);
        PayloadAndValidationOutcome outcome = new PayloadAndValidationOutcome(fileDetails, true,
                "group-int-id",new HashMap<>(),new HashMap<>());
        payloadAndValidationOutcomes.put("key1", outcome);

        try (MockedStatic<CsvConversionUtil> mockedStatic = mockStatic(CsvConversionUtil.class)) {
            mockedStatic.when(() -> CsvConversionUtil.convertCsvStringToDemographicData(anyString()))
                    .thenReturn(Map.of("patient1", List.of(new DemographicData())));
            mockedStatic.when(() -> CsvConversionUtil.convertCsvStringToScreeningProfileData(anyString()))
                    .thenReturn(Map.of("patient1", List.of(new ScreeningProfileData())));
            mockedStatic.when(() -> CsvConversionUtil.convertCsvStringToQeAdminData(anyString()))
                    .thenReturn(Map.of("patient1", List.of(new QeAdminData())));
            mockedStatic.when(
                    () -> CsvConversionUtil.convertCsvStringToScreeningObservationData(anyString()))
                    .thenReturn(Map.of("encounter1", List.of(new ScreeningObservationData())));
        }
        // when(UUID.randomUUID()).thenReturn(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

        String mockBundle = getMockBundleJson();
        when(csvToFhirConverter.convert(any(DemographicData.class), any(QeAdminData.class),
                any(ScreeningProfileData.class), anyList(), anyString()))
                .thenReturn(mockBundle);
        OperationOutcome successfulOutcome = new OperationOutcome();
        when(fhirService.processBundle(eq(mockBundle), anyString(), any(), any(), any(), any(), any(),
                anyString(),
                eq(false), eq(false), eq(false), eq(request), eq(response), any(), eq(true), any(), anyString(), anyString(),anyString(),anyString(),anyString(),anyString()))
                .thenReturn(successfulOutcome)
                .thenThrow(new RuntimeException("Mock failure"));
        List<Object> result = csvBundleProcessorService.processPayload(masterInteractionId,
                payloadAndValidationOutcomes,new ArrayList<>(),
                request, response, "tenantId","test.zip");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(2, result.size());
        verify(csvToFhirConverter, times(2)).convert(any(DemographicData.class), any(QeAdminData.class),
                any(ScreeningProfileData.class), anyList(), anyString());
        verify(fhirService, times(2)).processBundle(eq(mockBundle), anyString(), any(), any(), any(), any(),
                any(),
                anyString(), eq(false), eq(false), eq(false), eq(request), eq(response), any(),
                eq(true), any(), anyString(), anyString(),anyString(),anyString(),anyString(),anyString());
    }

    private String getMockBundleJson() {
        return """
                {
                    "resourceType": "Bundle",
                    "id": "mock-bundle-id",
                    "type": "transaction",
                    "entry": [
                        {
                            "resource": {
                                "resourceType": "Patient",
                                "id": "mock-patient-id",
                                "name": [
                                    {
                                        "family": "Doe",
                                        "given": ["John"]
                                    }
                                ]
                            }
                        }
                    ]
                }
                """;
    }

    private String getMockOperationOutcomeJson() {
        return """
                {
                    "resourceType": "OperationOutcome",
                    "id": "mock-operation-outcome-id",
                    "issue": [
                        {
                            "severity": "error",
                            "code": "invalid",
                            "details": {
                                "text": "Mock error message."
                            }
                        }
                    ]
                }
                """;
    }

    private FileDetail createFileDetail(String filePath, FileType fileType) throws IOException {
        String content = Files.readString(Paths.get(filePath));
        String filename = Paths.get(filePath).getFileName().toString();
        return new FileDetail(filename, fileType, content, filePath);
    }

}
