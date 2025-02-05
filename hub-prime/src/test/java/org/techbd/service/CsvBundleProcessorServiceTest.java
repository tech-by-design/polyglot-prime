package org.techbd.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.techbd.model.csv.*;
import org.techbd.service.converters.csv.CsvToFhirConverter;
import org.techbd.service.http.hub.prime.api.FHIRService;
import org.techbd.udi.UdiPrimeJpaConfig;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jooq.DSLContext;
import org.jooq.Configuration;

import java.lang.reflect.Method;

@ExtendWith(MockitoExtension.class)
class CsvBundleProcessorServiceTest {

    @Mock
    private CsvToFhirConverter csvToFhirConverter;
    @Mock
    private FHIRService fhirService;
    @Mock
    private UdiPrimeJpaConfig udiPrimeJpaConfig;
    @Mock
    private DSLContext dslContext;

    @Mock
    private Configuration jooqConfig;

    @InjectMocks
    private CsvBundleProcessorService csvBundleProcessorService;

    @Mock
    private PayloadAndValidationOutcome payloadAndValidationOutcome;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private String originalFileName;
    private Instant testStartTime;
    private Instant testEndTime;
    private String groupKey = "groupKey";
    private String groupInteractionId = "groupInteractionId";
    private String masterInteractionId = "masterInteractionId";
    private String tenantId = "tenantId";

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        masterInteractionId = UUID.randomUUID().toString();
        tenantId = "test-tenant";
        originalFileName = "test.zip";
        testStartTime = Instant.now();
        testEndTime = testStartTime.plusSeconds(1);
        request.setAttribute("startTime", testStartTime);

    }

    @Test
    void testProcessScreening_withValidData() throws Exception {
        // Arrange
        Method processScreeningMethod = CsvBundleProcessorService.class.getDeclaredMethod(
                "processScreening", String.class, Map.class, Map.class, Map.class, Map.class,
                HttpServletRequest.class, HttpServletResponse.class, String.class, String.class, String.class,
                boolean.class, PayloadAndValidationOutcome.class, boolean.class);
        processScreeningMethod.setAccessible(true);

        Map<String, List<DemographicData>> demographicData = new HashMap<>();
        Map<String, List<ScreeningProfileData>> screeningProfileData = new HashMap<>();
        Map<String, List<QeAdminData>> qeAdminData = new HashMap<>();
        Map<String, List<ScreeningObservationData>> screeningObservationData = new HashMap<>();

        // Act
        List<Object> result = (List<Object>) processScreeningMethod.invoke(csvBundleProcessorService,
                groupKey, demographicData, screeningProfileData, qeAdminData, screeningObservationData,
                request, response, groupInteractionId, masterInteractionId, tenantId, true, payloadAndValidationOutcome,
                true);

        // Assert
        assertNotNull(result);

    }

    @Nested
    class ProcessPayloadTests {
        @Test
        void shouldHandleUnprocessedFiles() {
            // Arrange
            Map<String, PayloadAndValidationOutcome> payloadMap = new HashMap<>();
            List<String> filesNotProcessed = Arrays.asList("file1.csv", "file2.csv");
            when(udiPrimeJpaConfig.dsl()).thenReturn(dslContext);
            when(dslContext.configuration()).thenReturn(mock(Configuration.class));
            // Act
            List<Object> result = csvBundleProcessorService.processPayload(
                    masterInteractionId, payloadMap, filesNotProcessed, request, response, tenantId, originalFileName);

            // Assert
            assertNotNull(result);
            assertFalse(result.isEmpty());
            assertTrue(result.stream()
                    .anyMatch(obj -> obj instanceof Map && ((Map<?, ?>) obj).containsKey("validationResults")));
        }

        @Test
        void shouldHandleEmptyInput() {
            when(udiPrimeJpaConfig.dsl()).thenReturn(dslContext);
            when(dslContext.configuration()).thenReturn(mock(Configuration.class));
            List<Object> result = csvBundleProcessorService.processPayload(

                    masterInteractionId, new HashMap<>(), new ArrayList<>(),
                    request, response, tenantId, originalFileName);

            assertTrue(result.isEmpty());
        }

        @Test
        void shouldHandleNullInput() {
            assertThrows(NullPointerException.class, () -> csvBundleProcessorService.processPayload(
                    null, null, null, null, null, null, null));
        }
    }

    @Nested
    class BundleProvenanceTests {
        private Map<String, Object> provenance;
        private List<String> bundleFiles;

        @BeforeEach
        void setUp() {
            provenance = new HashMap<>();
            bundleFiles = Arrays.asList("file1.csv", "file2.csv");
        }

        @Test
        void shouldAddValidProvenance() throws Exception {
            String result = csvBundleProcessorService.addBundleProvenance(
                    provenance, bundleFiles, "MRN123", "ENC456", testStartTime, testEndTime);

            assertNotNull(result);
            assertTrue(result.contains("TechByDesign"));
            assertTrue(result.contains("MRN123"));
            assertTrue(result.contains("ENC456"));
        }

        @Test
        void shouldHandleEmptyBundleGeneratedFrom() throws Exception {
            String result = csvBundleProcessorService.addBundleProvenance(
                    new HashMap<>(), Collections.emptyList(), "patientId",
                    "encounterId", testStartTime, testEndTime);

            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        void shouldHandleNullTimestamps() {
            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> csvBundleProcessorService.addBundleProvenance(
                            provenance, bundleFiles, "patientId", "encounterId", null, testEndTime)),
                    () -> assertThrows(NullPointerException.class, () -> csvBundleProcessorService.addBundleProvenance(
                            provenance, bundleFiles, "patientId", "encounterId", testStartTime, null)));
        }
    }

    @Nested
    class HapiFhirValidationTests {
        private Map<String, Object> provenance;

        @BeforeEach
        void setUp() {
            provenance = new HashMap<>();
        }

        @Test
        void shouldAddValidValidation() {
            csvBundleProcessorService.addHapiFhirValidation(provenance, "Test validation");

            assertTrue(provenance.containsKey("hapiFhirValidation"));
            Map<String, Object> validation = (Map<String, Object>) provenance.get("hapiFhirValidation");
            assertEquals("Test validation", validation.get("description"));
            verifyValidationAgent(validation);
        }

        @Test
        void shouldHandleEmptyDescription() {
            csvBundleProcessorService.addHapiFhirValidation(provenance, "");

            assertTrue(provenance.containsKey("hapiFhirValidation"));
            assertEquals("", ((Map<String, Object>) provenance.get("hapiFhirValidation")).get("description"));
        }

        @Test
        void shouldHandleNullProvenance() {
            assertThrows(NullPointerException.class,
                    () -> csvBundleProcessorService.addHapiFhirValidation(null, "Valid description"));
        }

        private void verifyValidationAgent(Map<String, Object> validation) {
            List<Map<String, Object>> agentList = (List<Map<String, Object>>) validation.get("agent");
            assertNotNull(agentList);
            assertEquals(1, agentList.size());

            Map<String, Object> agent = agentList.get(0);
            Map<String, Object> who = (Map<String, Object>) agent.get("who");
            List<Map<String, String>> coding = (List<Map<String, String>>) who.get("coding");

            assertEquals(1, coding.size());
            Map<String, String> codingMap = coding.get(0);
            assertEquals("Validator", codingMap.get("system"));
            assertEquals("HAPI FHIR Validation", codingMap.get("display"));
        }
    }

    @Nested
    class FileNameTests {
        @Test
        void shouldHandleValidFileDetails() {
            List<FileDetail> fileDetails = Arrays.asList(
                    new FileDetail("file1.csv", FileType.DEMOGRAPHIC_DATA, "content", "/path1"),
                    new FileDetail("file2.csv", FileType.SCREENING_PROFILE_DATA, "content", "/path2"));

            List<String> result = CsvBundleProcessorService.getFileNames(fileDetails);

            assertEquals(2, result.size());
            assertTrue(result.containsAll(Arrays.asList("file1.csv", "file2.csv")));
        }

        @Test
        void shouldHandleNullAndEmptyInputs() {
            assertAll(
                    () -> assertTrue(CsvBundleProcessorService.getFileNames(null).isEmpty()),
                    () -> assertTrue(CsvBundleProcessorService.getFileNames(new ArrayList<>()).isEmpty()));
        }

        @Test
        void shouldHandleMixedNullAndValidFilenames() {
            List<FileDetail> fileDetails = Arrays.asList(
                    new FileDetail("file1.csv", FileType.DEMOGRAPHIC_DATA, "content", "/path"),
                    new FileDetail(null, FileType.SCREENING_PROFILE_DATA, "content", "/path"),
                    new FileDetail("file2.csv", FileType.QE_ADMIN_DATA, "content", "/path"));

            List<String> result = CsvBundleProcessorService.getFileNames(fileDetails);

            assertEquals(3, result.size());
            assertTrue(result.contains("file1.csv"));
            assertTrue(result.contains(null));
            assertTrue(result.contains("file2.csv"));
        }
    }

    @Test
    void processPayload_WithFilesNotProcessed_ReturnsErrors() {
        when(udiPrimeJpaConfig.dsl()).thenReturn(dslContext);
        when(dslContext.configuration()).thenReturn(mock(Configuration.class));

        // Arrange
        Map<String, PayloadAndValidationOutcome> payloadMap = new HashMap<>();
        List<String> filesNotProcessed = Arrays.asList("file1.csv", "file2.csv");

        // Act
        List<Object> result = csvBundleProcessorService.processPayload(
                masterInteractionId, payloadMap, filesNotProcessed, request, response,
                tenantId, originalFileName);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.stream()
                .anyMatch(obj -> obj instanceof Map && ((Map<?, ?>) obj).containsKey("validationResults")));
    }

    @Test
    public void testAddHapiFhirValidation() {
        // Arrange
        Map<String, Object> provenance = new HashMap<>();
        String validationDescription = "Test validation description";

        // Act
        csvBundleProcessorService.addHapiFhirValidation(provenance,
                validationDescription);

        // Assert
        assertTrue(provenance.containsKey("hapiFhirValidation"));
        Map<String, Object> hapiFhirValidation = (Map<String, Object>) provenance.get("hapiFhirValidation");

        assertEquals(validationDescription, hapiFhirValidation.get("description"));

        List<Map<String, Object>> agentList = (List<Map<String, Object>>) hapiFhirValidation.get("agent");
        assertNotNull(agentList);
        assertEquals(1, agentList.size());

        Map<String, Object> agent = agentList.get(0);
        Map<String, Object> who = (Map<String, Object>) agent.get("who");
        List<Map<String, String>> coding = (List<Map<String, String>>) who.get("coding");

        assertEquals(1, coding.size());
        Map<String, String> codingMap = coding.get(0);
        assertEquals("Validator", codingMap.get("system"));
        assertEquals("HAPI FHIR Validation", codingMap.get("display"));
    }

    @Test
    void testAddHapiFhirValidation_EmptyDescription() {
        CsvBundleProcessorService service = new CsvBundleProcessorService(null, null,
                null);
        Map<String, Object> provenance = new HashMap<>();
        service.addHapiFhirValidation(provenance, "");

        assertTrue(provenance.containsKey("hapiFhirValidation"));
        Map<String, Object> hapiFhirValidation = (Map<String, Object>) provenance.get("hapiFhirValidation");
        assertEquals("", hapiFhirValidation.get("description"));
    }

    @Test
    void testAddHapiFhirValidation_EmptyProvenance() {
        CsvBundleProcessorService service = new CsvBundleProcessorService(null, null,
                null);
        Map<String, Object> provenance = new HashMap<>();
        service.addHapiFhirValidation(provenance, "Valid description");

        assertTrue(provenance.containsKey("hapiFhirValidation"));
        Map<String, Object> hapiFhirValidation = (Map<String, Object>) provenance.get("hapiFhirValidation");
        assertEquals("Valid description", hapiFhirValidation.get("description"));
    }

    @Test
    void testAddHapiFhirValidation_NullProvenance() {
        CsvBundleProcessorService service = new CsvBundleProcessorService(null, null,
                null);
        assertThrows(NullPointerException.class, () -> {
            service.addHapiFhirValidation(null, "Valid description");
        });
    }

    @Test
    void testAddHapiFhirValidation_ProvenanceWithExistingData() {
        CsvBundleProcessorService service = new CsvBundleProcessorService(null, null,
                null);
        Map<String, Object> provenance = new HashMap<>();
        provenance.put("existingKey", "existingValue");
        service.addHapiFhirValidation(provenance, "Valid description");

        assertTrue(provenance.containsKey("existingKey"));
        assertTrue(provenance.containsKey("hapiFhirValidation"));
        Map<String, Object> hapiFhirValidation = (Map<String, Object>) provenance.get("hapiFhirValidation");
        assertEquals("Valid description", hapiFhirValidation.get("description"));
    }

    @Test
    public void testCsvBundleProcessorServiceConstructor() {
        // Arrange
        CsvToFhirConverter mockConverter = mock(CsvToFhirConverter.class);
        FHIRService mockFhirService = mock(FHIRService.class);
        UdiPrimeJpaConfig mockConfig = mock(UdiPrimeJpaConfig.class);

        // Act
        CsvBundleProcessorService service = new CsvBundleProcessorService(mockConverter, mockFhirService, mockConfig);

        // Assert
        assertNotNull(service, "CsvBundleProcessorService should be created successfully");
        // Additional assertions can be added here to verify the state of the service

    }

    @Test
    public void testGetFileNamesWithNullInput() {
        List<String> result = CsvBundleProcessorService.getFileNames(null);
        assertTrue(result.isEmpty(), "Expected an empty list when input is null");
    }

    @Test
    void testGetFileNames_EmptyFilenames() {
        // Test with empty filenames
        List<FileDetail> input = List.of(
                new FileDetail("", FileType.DEMOGRAPHIC_DATA, "content", "path"),
                new FileDetail("", FileType.SCREENING_PROFILE_DATA, "content", "path"));
        List<String> result = CsvBundleProcessorService.getFileNames(input);
        assertEquals(2, result.size(), "Result should contain 2 items");
        assertTrue(result.stream().allMatch(String::isEmpty), "All filenames should be empty strings");
    }

    @Test
    void testGetFileNames_EmptyInput() {
        // Test when input is an empty list
        List<String> result = CsvBundleProcessorService.getFileNames(new ArrayList<>());
        assertTrue(result.isEmpty(), "Result should be an empty list when input is empty");
    }

    @Test
    void testGetFileNames_MixedNullAndValidFilenames() {
        // Test with a mix of null and valid filenames
        List<FileDetail> input = List.of(
                new FileDetail("file1.csv", FileType.DEMOGRAPHIC_DATA, "content", "path"),
                new FileDetail(null, FileType.SCREENING_PROFILE_DATA, "content", "path"),
                new FileDetail("file2.csv", FileType.QE_ADMIN_DATA, "content", "path"));
        List<String> result = CsvBundleProcessorService.getFileNames(input);
        assertEquals(3, result.size(), "Result should contain 3 items");
        assertTrue(result.contains("file1.csv"), "Result should contain'file1.csv'");
        assertTrue(result.contains(null), "Result should contain null");
        assertTrue(result.contains("file2.csv"), "Result should contain'file2.csv'");
    }

    @Test
    void testGetFileNames_NullFilename() {
        // Test when a FileDetail has a null filename
        List<FileDetail> input = List.of(new FileDetail(null,
                FileType.DEMOGRAPHIC_DATA, "content", "path"));
        List<String> result = CsvBundleProcessorService.getFileNames(input);
        assertTrue(result.contains(null), "Result should contain null for FileDetail with null filename");
    }

    @Test
    void testGetFileNames_NullInput() {
        // Test when input is null
        List<String> result = CsvBundleProcessorService.getFileNames(null);
        assertTrue(result.isEmpty(), "Result should be an empty list when input is null");
    }

    @Test
    public void testGetFileNames_WithNonNullFileDetails() {
        // Arrange
        List<FileDetail> fileDetails = Arrays.asList(
                new FileDetail("file1.csv", FileType.DEMOGRAPHIC_DATA, "content",
                        "/path/to/file1.csv"),
                new FileDetail("file2.csv", FileType.SCREENING_PROFILE_DATA, "content",
                        "/path/to/file2.csv"));

        // Act
        List<String> result = CsvBundleProcessorService.getFileNames(fileDetails);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("file1.csv"));
        assertTrue(result.contains("file2.csv"));
    }

    @Test
    void test_addBundleProvenance_withEmptyBundleGeneratedFrom() throws Exception {
        // Arrange
        Map<String, Object> emptyMap = new HashMap<>();
        List<String> emptyFileList = Collections.emptyList();
        String patientId = "patientId";
        String encounterId = "encounterId";
        Instant startTime = Instant.now();
        Instant endTime = startTime.plusSeconds(1); // Ensure end time is after start time

        // Act
        String result = csvBundleProcessorService.addBundleProvenance(
                emptyMap,
                emptyFileList,
                patientId,
                encounterId,
                startTime,
                endTime);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertFalse(result.isEmpty(), "Result should not be empty");
    }

    @Test
    void test_addBundleProvenance_initiatedAtAfterCompletedAt() throws Exception {
        Instant initiatedAt = Instant.now();
        Instant completedAt = initiatedAt.minusSeconds(60);
        String result = csvBundleProcessorService.addBundleProvenance(new HashMap<>(), List.of("file1.csv"),
                "patientId", "encounterId", initiatedAt, completedAt);
        assertTrue(result.contains(initiatedAt.toString()));
        assertTrue(result.contains(completedAt.toString()));
    }

    @Test
    void test_addBundleProvenance_nullCompletedAt() {
        Exception exception = assertThrows(NullPointerException.class, () -> {
            csvBundleProcessorService.addBundleProvenance(new HashMap<>(),
                    List.of("file1.csv"), "patientId",
                    "encounterId", Instant.now(), null);
        });
        // assertEquals("Cannot invoke \"java.time.Instant.toString()\"
        // because\"completedAt\" is null",
        // exception.getMessage());
        assertEquals("Cannot invoke \"java.time.Instant.toString()\" because \"completedAt\" is null",
                exception.getMessage());
    }

    @Test
    void test_addBundleProvenance_nullInitiatedAt() {
        Exception exception = assertThrows(NullPointerException.class, () -> {
            csvBundleProcessorService.addBundleProvenance(new HashMap<>(),
                    List.of("file1.csv"), "patientId",
                    "encounterId", null, Instant.now());
        });
        assertEquals("Cannot invoke \"java.time.Instant.toString()\" because \"initiatedAt\" is null",
                exception.getMessage());
    }

    @Test
    void shouldAddBundleProvenanceWithNullPatientMrnId() throws Exception {
        // Arrange
        Map<String, Object> inputMap = new HashMap<>();
        List<String> files = Collections.singletonList("file1.csv");
        String encounterId = "encounterId";
        Instant startTime = Instant.now();
        Instant endTime = Instant.now();

        // Act
        String result = csvBundleProcessorService.addBundleProvenance(
                inputMap,
                files,
                null,
                encounterId,
                startTime,
                endTime);

        // Assert
        assertNotNull(result, "Result should not be null");

    }

    @Test
    public void test_processPayload_InvalidPayloadAndUnprocessedFiles() {
        // Arrange
        Map<String, PayloadAndValidationOutcome> payloadAndValidationOutcomes = new HashMap<>();
        PayloadAndValidationOutcome invalidOutcome = new PayloadAndValidationOutcome(
                new ArrayList<>(),
                false,
                UUID.randomUUID().toString(),
                new HashMap<>(),
                Map.of("error", "Invalid payload"));
        payloadAndValidationOutcomes.put("group1", invalidOutcome);

        List<String> filesNotProcessed = Arrays.asList("file1.csv", "file2.csv");

        when(udiPrimeJpaConfig.dsl()).thenReturn(dslContext);
        when(dslContext.configuration()).thenReturn(mock(Configuration.class));

        // Act
        List<Object> result = csvBundleProcessorService.processPayload(
                masterInteractionId,
                payloadAndValidationOutcomes,
                filesNotProcessed,
                request,
                response,
                tenantId,
                originalFileName);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.get(0) instanceof Map);
        assertTrue(((Map<?, ?>) result.get(0)).containsKey("error"));
        assertTrue(result.get(1) instanceof Map);
        assertTrue(((Map<?, ?>) result.get(1)).containsKey("validationResults"));

        verify(udiPrimeJpaConfig, times(1)).dsl();
        verify(dslContext, times(1)).configuration();
    }

    @Test
    void test_processPayload_emptyInput() {
        // Arrange
        Map<String, PayloadAndValidationOutcome> emptyPayload = new HashMap<>();
        List<String> emptyFilesNotProcessed = new ArrayList<>();
        when(udiPrimeJpaConfig.dsl()).thenReturn(dslContext);
        when(dslContext.configuration()).thenReturn(mock(Configuration.class));
        // Act
        List<Object> result = csvBundleProcessorService.processPayload(
                masterInteractionId, emptyPayload, emptyFilesNotProcessed, request, response,
                tenantId,
                originalFileName);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void test_processPayload_exceptionHandling() throws Exception {
        // Arrange
        Map<String, PayloadAndValidationOutcome> payload = new HashMap<>();
        payload.put("key", new PayloadAndValidationOutcome(
                List.of(new FileDetail("test.csv", FileType.DEMOGRAPHIC_DATA, "content",
                        "path")),
                true, "groupId", new HashMap<>(), new HashMap<>()));
        when(udiPrimeJpaConfig.dsl()).thenReturn(dslContext);

        // Act
        List<Object> result = csvBundleProcessorService.processPayload(
                masterInteractionId, payload, new ArrayList<>(), request, response, tenantId,
                originalFileName);

        // Assert
        assertFalse(result.isEmpty());
        assertTrue(result.get(0) instanceof Map);
        Map<String, Object> errorMap = (Map<String, Object>) result.get(0);
        assertTrue(errorMap.containsKey("validationResults"));
    }

    @Test
    void test_processPayload_nullInput() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            csvBundleProcessorService.processPayload(
                    null, null, null, null, null, null, null);
        });
    }
}
