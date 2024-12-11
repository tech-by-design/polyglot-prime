package org.techbd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.techbd.model.csv.FileDetail;
import org.techbd.orchestrate.csv.FileProcessor;


class FileProcessorTest {

        private static final String SAMPLE_CSV_CONTENT = 
        "PATIENT_MR_ID_VALUE,PATIENT_MA_ID_VALUE,PATIENT_SS_ID_VALUE,GIVEN_NAME,MIDDLE_NAME,FAMILY_NAME,GENDER,EXTENSION_SEX_AT_BIRTH_CODE_VALUE,PATIENT_BIRTH_DATE,ADDRESS1,CITY,DISTRICT,STATE,ZIP,TELECOM_VALUE,EXTENSION_PERSONAL_PRONOUNS_CODE,EXTENSION_PERSONAL_PRONOUNS_DISPLAY,EXTENSION_PERSONAL_PRONOUNS_SYSTEM,EXTENSION_GENDER_IDENTITY_CODE,EXTENSION_GENDER_IDENTITY_DISPLAY,EXTENSION_GENDER_IDENTITY_SYSTEM,PREFERRED_LANGUAGE_CODE_SYSTEM_NAME,PREFERRED_LANGUAGE_CODE_SYSTEM_CODE,EXTENSION_OMBCATEGORY_RACE_CODE,EXTENSION_OMBCATEGORY_RACE_CODE_DESCRIPTION,EXTENSION_OMBCATEGORY_RACE_CODE_SYSTEM_NAME,EXTENSION_OMBCATEGORY_ETHNICITY_CODE,EXTENSION_OMBCATEGORY_ETHNICITY_CODE_DESCRIPTION,EXTENSION_OMBCATEGORY_ETHNICITY_CODE_SYSTEM_NAME,PATIENT_LAST_UPDATED,RELATIONSHIP_PERSON_CODE,RELATIONSHIP_PERSON_DESCRIPTION,RELATIONSHIP_PERSON_GIVEN_NAME,RELATIONSHIP_PERSON_FAMILY_NAME,RELATIONSHIP_PERSON_TELECOM_VALUE,SEXUAL_ORIENTATION_VALUE_CODE,SEXUAL_ORIENTATION_VALUE_CODE_DESCRIPTION,SEXUAL_ORIENTATION_VALUE_CODE_SYSTEM_NAME,SEXUAL_ORIENTATION_LAST_UPDATED\n" +
        "11223344,AA12345C,999-34-2964,Jon,Bob,Doe,male,M,1981-07-16,115 Broadway Apt2,New York,MANHATTAN,NY,10032,1234567890,LA29518-0,he/him/his/his/himself,http://loinc.org,446151000124109,Identifies as male gender (finding),http://snomed.info/sct,urn:ietf:bcp:47,en,2028-9,Asian,urn:oid:2.16.840.1.113883.6.238,2135-2,Hispanic or Latino,urn:oid:2.16.840.1.113883.6.238,2024-02-23T00:00:00.00Z,MTH,Mother,Joyce,Doe,1234567890,UNK,Unknown,http://terminology.hl7.org/CodeSystem/v3-NullFlavor,2024-02-23T00:00:00Z";

        @BeforeEach
        void setUp() {
                try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
                        mockedFiles.when(() -> Files.readString(Mockito.any(Path.class)))
                                        .thenReturn(SAMPLE_CSV_CONTENT);
                }
        }

        @Test
        void testProcessAndGroupFiles_withAllRequiredModels() throws IOException {
                List<String> mockFilePaths = List.of(
                                "demographic_data_1.csv",
                                "qe_admin_data_1.csv",
                                "screening_consent_data_1.csv",
                                "screening_encounter_data_1.csv",
                                "screening_location_data_1.csv",
                                "screening_observation_data_1.csv",
                                "screening_resources_data_1.csv");
                Map<String, List<FileDetail>> groupedFiles = FileProcessor.processAndGroupFiles(mockFilePaths);
                assertNotNull(groupedFiles);
                groupedFiles.keySet().iterator().next();
                assertTrue(groupedFiles.containsKey("_1"));
                assertEquals(1, groupedFiles.size(), "There should be one group for _1.csv");
                assertEquals(7, groupedFiles.get("_1").size(), "There should be 7 files grouped under _1.csv");
                assertTrue(groupedFiles.get("_1").stream()
                                .anyMatch(fileDetail -> fileDetail.filename().equals("demographic_data_1.csv")));
                assertTrue(groupedFiles.get("_1").stream()
                                .anyMatch(fileDetail -> fileDetail.filename().equals("qe_admin_data_1.csv")));
                assertTrue(groupedFiles.get("_1").stream()
                                .anyMatch(fileDetail -> fileDetail.filename().equals("screening_consent_data_1.csv")));
                assertTrue(groupedFiles.get("_1").stream()
                                .anyMatch(fileDetail -> fileDetail.filename()
                                                .equals("screening_encounter_data_1.csv")));
                assertTrue(groupedFiles.get("_1").stream()
                                .anyMatch(fileDetail -> fileDetail.filename().equals("screening_location_data_1.csv")));
                assertTrue(groupedFiles.get("_1").stream()
                                .anyMatch(fileDetail -> fileDetail.filename()
                                                .equals("screening_observation_data_1.csv")));
                assertTrue(groupedFiles.get("_1").stream()
                                .anyMatch(fileDetail -> fileDetail.filename()
                                                .equals("screening_resources_data_1.csv")));
        }

        @Test
        void testProcessAndGroupFiles_withAllRequiredModelsAndAdditionalGroup() throws Throwable {
                List<String> mockFilePaths = List.of(
                                // Files for the first group (_1.csv)
                                "demographic_data_1.csv",
                                "qe_admin_data_1.csv",
                                "screening_consent_data_1.csv",
                                "screening_encounter_data_1.csv",
                                "screening_location_data_1.csv",
                                "screening_observation_data_1.csv",
                                "screening_resources_data_1.csv",
                                // Files for the second group (_testcase_1_group)
                                "demographic_data_testcase_1_group.csv",
                                "qe_admin_data_testcase_1_group.csv",
                                "screening_consent_data_testcase_1_group.csv",
                                "screening_encounter_data_testcase_1_group.csv",
                                "screening_location_data_testcase_1_group.csv",
                                "screening_observation_data_testcase_1_group.csv",
                                "screening_resources_data_testcase_1_group.csv");

                // Process the files
                Map<String, List<FileDetail>> groupedFiles = FileProcessor.processAndGroupFiles(mockFilePaths);

                // Assertions
                assertNotNull(groupedFiles, "Grouped files map should not be null");
                assertEquals(2, groupedFiles.size(), "There should be two groups in the map");

                // Check for the first group (_1.csv)
                String firstKey = "_1";
                assertTrue(groupedFiles.containsKey(firstKey), "First group key should exist in the map");
                List<FileDetail> firstGroupFiles = groupedFiles.get(firstKey);
                assertNotNull(firstGroupFiles, "First group file list should not be null");
                assertEquals(7, firstGroupFiles.size(), "First group should have 7 files");
                assertTrue(firstGroupFiles.stream().anyMatch(file -> file.filename().equals("demographic_data_1.csv")));
                assertTrue(firstGroupFiles.stream().anyMatch(file -> file.filename().equals("qe_admin_data_1.csv")));
                assertTrue(firstGroupFiles.stream()
                                .anyMatch(file -> file.filename().equals("screening_consent_data_1.csv")));
                assertTrue(firstGroupFiles.stream()
                                .anyMatch(file -> file.filename().equals("screening_encounter_data_1.csv")));
                assertTrue(firstGroupFiles.stream()
                                .anyMatch(file -> file.filename().equals("screening_location_data_1.csv")));
                assertTrue(
                                firstGroupFiles.stream().anyMatch(
                                                file -> file.filename().equals("screening_observation_data_1.csv")));
                assertTrue(firstGroupFiles.stream()
                                .anyMatch(file -> file.filename().equals("screening_resources_data_1.csv")));

                // Check for the second group (_testcase_1_group)
                String secondKey = "_testcase_1_group";
                assertTrue(groupedFiles.containsKey(secondKey), "Second group key should exist in the map");
                List<FileDetail> secondGroupFiles = groupedFiles.get(secondKey);
                assertNotNull(secondGroupFiles, "Second group file list should not be null");
                assertEquals(7, secondGroupFiles.size(), "Second group should have 7 files");
                assertTrue(secondGroupFiles.stream()
                                .anyMatch(file -> file.filename().equals("demographic_data_testcase_1_group.csv")));
                assertTrue(secondGroupFiles.stream()
                                .anyMatch(file -> file.filename().equals("qe_admin_data_testcase_1_group.csv")));
                assertTrue(secondGroupFiles.stream()
                                .anyMatch(file -> file.filename()
                                                .equals("screening_consent_data_testcase_1_group.csv")));
                assertTrue(secondGroupFiles.stream()
                                .anyMatch(file -> file.filename()
                                                .equals("screening_encounter_data_testcase_1_group.csv")));
                assertTrue(secondGroupFiles.stream()
                                .anyMatch(file -> file.filename()
                                                .equals("screening_location_data_testcase_1_group.csv")));
                assertTrue(secondGroupFiles.stream()
                                .anyMatch(file -> file.filename()
                                                .equals("screening_observation_data_testcase_1_group.csv")));
                assertTrue(secondGroupFiles.stream()
                                .anyMatch(file -> file.filename()
                                                .equals("screening_resources_data_testcase_1_group.csv")));
        }

        @Test
        void testProcessAndGroupFiles_withMissingDemographicData() {
                List<String> mockFilePaths = List.of(
                                "qe_admin_data_1.csv",
                                "screening_consent_data_1.csv",
                                "screening_encounter_data_1.csv",
                                "screening_location_data_1.csv",
                                "screening_observation_data_1.csv",
                                "screening_resources_data_1.csv");
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        FileProcessor.processAndGroupFiles(mockFilePaths);
                });
                assertTrue(exception.getMessage().contains("Missing required file: demographic_data_1.csv"));
        }

        @Test
        void testProcessAndGroupFiles_withMissingQeAdminData() {
                List<String> mockFilePaths = List.of(
                                "demographic_data_1.csv",
                                "screening_consent_data_1.csv",
                                "screening_encounter_data_1.csv",
                                "screening_location_data_1.csv",
                                "screening_observation_data_1.csv",
                                "screening_resources_data_1.csv");
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        FileProcessor.processAndGroupFiles(mockFilePaths);
                });
                assertTrue(exception.getMessage().contains("Missing required file: qe_admin_data_1.csv"));
        }

        @Test
        void testProcessAndGroupFiles_withMissingScreeningConsentData() {
                List<String> mockFilePaths = List.of(
                                "demographic_data_1.csv",
                                "qe_admin_data_1.csv",
                                "screening_encounter_data_1.csv",
                                "screening_location_data_1.csv",
                                "screening_observation_data_1.csv",
                                "screening_resources_data_1.csv");
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        FileProcessor.processAndGroupFiles(mockFilePaths);
                });

                assertTrue(exception.getMessage().contains("Missing required file: screening_consent_data_1.csv"));
        }

        @Test
        void testProcessAndGroupFiles_withMissingScreeningEncounterData() {
                List<String> mockFilePaths = List.of(
                                "demographic_data_1.csv",
                                "qe_admin_data_1.csv",
                                "screening_consent_data_1.csv",
                                "screening_location_data_1.csv",
                                "screening_observation_data_1.csv",
                                "screening_resources_data_1.csv");
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        FileProcessor.processAndGroupFiles(mockFilePaths);
                });
                assertTrue(exception.getMessage().contains("Missing required file: screening_encounter_data_1.csv"));
        }

        @Test
        void testProcessAndGroupFiles_withMissingScreeningLocationData() {
                List<String> mockFilePaths = List.of(
                                "demographic_data_1.csv",
                                "qe_admin_data_1.csv",
                                "screening_consent_data_1.csv",
                                "screening_encounter_data_1.csv",
                                "screening_observation_data_1.csv",
                                "screening_resources_data_1.csv");
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        FileProcessor.processAndGroupFiles(mockFilePaths);
                });
                assertTrue(exception.getMessage().contains("Missing required file: screening_location_data_1.csv"));
        }

        @Test
        void testProcessAndGroupFiles_withMissingScreeningObservationData() {
                List<String> mockFilePaths = List.of(
                                "demographic_data_1.csv",
                                "qe_admin_data_1.csv",
                                "screening_consent_data_1.csv",
                                "screening_encounter_data_1.csv",
                                "screening_location_data_1.csv",
                                "screening_resources_data_1.csv");
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        FileProcessor.processAndGroupFiles(mockFilePaths);
                });
                assertTrue(exception.getMessage().contains("Missing required file: screening_observation_data_1.csv"));
        }

        @Test
        void testProcessAndGroupFiles_withMissingScreeningResourceData() {
                List<String> mockFilePaths = List.of(
                                "demographic_data_1.csv",
                                "qe_admin_data_1.csv",
                                "screening_consent_data_1.csv",
                                "screening_encounter_data_1.csv",
                                "screening_location_data_1.csv",
                                "screening_observation_data_1.csv");
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        FileProcessor.processAndGroupFiles(mockFilePaths);
                });
                assertTrue(exception.getMessage().contains("Missing required file: screening_resources_data_1.csv"));
        }

        @Test
        void testProcessAndGroupFiles_withInvalidFileType() {
                List<String> mockFilePaths = List.of("invalid_data_1.csv");
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        FileProcessor.processAndGroupFiles(mockFilePaths);
                });
                assertTrue(exception.getMessage().contains("Unknown file type in filename: invalid_data_1.csv"));
        }

        @Test
        void testProcessAndGroupFiles_withMissingFile_throwsIllegalArgumentException() {
                List<String> mockFilePaths = List.of(
                                // Files for the first group (_1.csv), missing one file
                                "demographic_data_1.csv",
                                "qe_admin_data_1.csv",
                                "screening_consent_data_1.csv",
                                "screening_encounter_data_1.csv",
                                "screening_location_data_1.csv",
                                "screening_resources_data_1.csv", // Missing "screening_observation_data_1.csv"
                                // Files for the second group (_testcase_1_group)
                                "demographic_data_testcase_1_group.csv",
                                "qe_admin_data_testcase_1_group.csv",
                                "screening_consent_data_testcase_1_group.csv",
                                "screening_encounter_data_testcase_1_group.csv",
                                "screening_location_data_testcase_1_group.csv",
                                "screening_observation_data_testcase_1_group.csv",
                                "screening_resources_data_testcase_1_group.csv");

                // Assertion to check for exception
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> FileProcessor.processAndGroupFiles(mockFilePaths),
                                "An IllegalArgumentException should be thrown when a required file is missing");

                // Optionally check the exception message
                assertEquals(
                                "Missing required file: screening_observation_data_1.csv",
                                exception.getMessage(),
                                "Exception message should indicate the missing file");
        }

        @Test

        void testProcessAndGroupFiles_withMissingFile_With3Group_throwsIllegalArgumentException() {
                List<String> mockFilePaths = List.of(
                                // Files for the first group (_1.csv), missing one file
                                "demographic_data_1.csv",
                                "qe_admin_data_1.csv",
                                "screening_consent_data_1.csv",
                                "screening_encounter_data_1.csv",
                                "screening_location_data_1.csv",
                                "screening_resources_data_1.csv", // Missing "screening_observation_data_1.csv"

                                // Files for the second group (_testcase_1_group), missing one file
                                "demographic_data_testcase_1_group.csv",
                                "qe_admin_data_testcase_1_group.csv",
                                "screening_consent_data_testcase_1_group.csv",
                                "screening_encounter_data_testcase_1_group.csv",
                                "screening_location_data_testcase_1_group.csv",
                                // Missing "screening_observation_data_testcase_1_group.csv"
                                "screening_resources_data_testcase_1_group.csv",

                                // Files for the third group (_testcase_2_group), complete
                                "demographic_data_testcase_2_group.csv",
                                "qe_admin_data_testcase_2_group.csv",
                                "screening_consent_data_testcase_2_group.csv",
                                "screening_encounter_data_testcase_2_group.csv",
                                "screening_location_data_testcase_2_group.csv",
                                "screening_observation_data_testcase_2_group.csv",
                                "screening_resources_data_testcase_2_group.csv");

                // Assertion to check for exception
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> FileProcessor.processAndGroupFiles(mockFilePaths),
                                "An IllegalArgumentException should be thrown when a required file is missing");

                assertTrue(exception.getMessage().contains("Missing required file"));
        }

}
