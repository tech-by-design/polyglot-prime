package org.techbd.service.csv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.assertj.core.api.SoftAssertions;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningData;
import org.techbd.model.csv.ScreeningResourceData;
import org.techbd.service.converters.shinny.LocationConverter;
import org.techbd.util.CsvConversionUtil;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

@ExtendWith(MockitoExtension.class)
class LocationConverterTest {
    private static final Logger LOG = LoggerFactory.getLogger(LocationConverterTest.class.getName());

    @InjectMocks
    private LocationConverter locationConverter;

    @Test
    void testConvert() throws Exception {
        // Create necessary data objects for the test
        final Bundle bundle = new Bundle(); // Ensure this is populated with relevant data
        final DemographicData demographicData = createDemographicData(); // Create DemographicData instance
        final List<ScreeningData> screeningDataList = createScreeningData(); // Create list of ScreeningData
        final QeAdminData qrAdminData = createQeAdminData(); // Create QeAdminData instance
        final ScreeningResourceData screeningResourceData = createScreeningResourceData(); // Create
                                                                                           // ScreeningResourceData

        // Instantiate the LocationConverter
        LocationConverter locationConverter = new LocationConverter();

        // Call the convert method of the location converter
        final BundleEntryComponent result = locationConverter.convert(bundle, demographicData, screeningDataList,
                qrAdminData, screeningResourceData, "interactionId");

        // Create soft assertions to verify the result
        final SoftAssertions softly = new SoftAssertions();

        // Assert that the result is not null
        softly.assertThat(result).isNotNull();

        // Assert that the result resource is an instance of Location
        softly.assertThat(result.getResource()).isInstanceOf(Location.class);

        // Cast the result to Location and assert various properties
        final Location location = (Location) result.getResource();

        // Assert that the location ID is not null or empty and matches expected
        softly.assertThat(location.getId()).isNotEmpty();
        softly.assertThat(location.getId()).isEqualTo("Location-interactionId");

        // Assert that the location physical type is correctly set
        softly.assertThat(location.getPhysicalType().getCodingFirstRep().getCode()).isEqualTo("bu");

        // Assert all soft assertions
        softly.assertAll();
    }

    @Test
    void testGeneratedJson() throws Exception {
        final var bundle = new Bundle();
        final var demographicData = createDemographicData();
        final var screeningDataList = createScreeningData();
        final var qrAdminData = createQeAdminData();
        final ScreeningResourceData screeningResourceData = createScreeningResourceData();

        final var result = locationConverter.convert(bundle, demographicData, screeningDataList, qrAdminData,
                screeningResourceData,
                "interactionId");

        final Location location = (Location) result.getResource();
        final var filePath = "src/test/resources/org/techbd/csv/generated-json/location.json";
        final FhirContext fhirContext = FhirContext.forR4();
        final IParser fhirJsonParser = fhirContext.newJsonParser().setPrettyPrint(true);
        final String fhirResourceJson = fhirJsonParser.encodeResourceToString(location);
        final Path outputPath = Paths.get(filePath);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, fhirResourceJson);
    }

    private ScreeningResourceData createScreeningResourceData() throws IOException {
        final String csv = "PATIENT_MR_ID_VALUE|FACILITY_ID|CONSENT_PROFILE|CONSENT_LAST_UPDATED|CONSENT_TEXT_STATUS|CONSENT_STATUS|CONSENT_SCOPE_CODE|CONSENT_SCOPE_TEXT|CONSENT_CATEGORY_IDSCL_CODE|CONSENT_CATEGORY_IDSCL_SYSTEM|CONSENT_CATEGORY_LOINC_CODE|CONSENT_CATEGORY_LOINC_SYSTEM|CONSENT_CATEGORY_LOINC_DISPLAY|CONSENT_DATE_TIME|CONSENT_POLICY_AUTHORITY|CONSENT_PROVISION_TYPE|ENCOUNTER_ID|ENCOUNTER_CLASS_CODE|ENCOUNTER_CLASS_CODE_DESCRIPTION|ENCOUNTER_CLASS_CODE_SYSTEM|ENCOUNTER_STATUS_CODE|ENCOUNTER_STATUS_CODE_DESCRIPTION|ENCOUNTER_STATUS_CODE_SYSTEM|ENCOUNTER_TYPE_CODE|ENCOUNTER_TYPE_CODE_DESCRIPTION|ENCOUNTER_TYPE_CODE_SYSTEM|ENCOUNTER_START_TIME|ENCOUNTER_END_TIME|ENCOUNTER_LAST_UPDATED|ENCOUNTER_PROFILE|ENCOUNTER_TEXT_STATUS|LOCATION_NAME|LOCATION_STATUS|LOCATION_TYPE_CODE|LOCATION_TYPE_SYSTEM|LOCATION_ADDRESS1|LOCATION_ADDRESS2|LOCATION_CITY|LOCATION_DISTRICT|LOCATION_STATE|LOCATION_ZIP|LOCATION_PHYSICAL_TYPE_CODE|LOCATION_PHYSICAL_TYPE_SYSTEM|LOCATION_TEXT_STATUS|LOCATION_LAST_UPDATED|SCREENING_LAST_UPDATED|SCREENING_PROFILE|SCREENING_LANGUAGE|SCREENING_TEXT_STATUS|SCREENING_CODE_SYSTEM_NAME|QUESTION_CODE_SYSTEM_NAME|OBSERVATION_CATEGORY_SDOH_SYSTEM|OBSERVATION_CATEGORY_SOCIAL_HISTORY_CODE|OBSERVATION_CATEGORY_SOCIAL_HISTORY_SYSTEM|OBSERVATION_CATEGORY_SURVEY_CODE|OBSERVATION_CATEGORY_SURVEY_SYSTEM|OBSERVATION_CATEGORY_SNOMED_SYSTEM|ANSWER_CODE_SYSTEM_NAME\n"
                + "11223344|CUMC|http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-Consent|2024-02-23T00:00:00Z|generated|active|treatment|treatment|IDSCL|http://terminology.hl7.org/CodeSystem/v3-ActCode|59284-0|http://loinc.org|Patient Consent|2024-02-23T00:00:00Z|urn:uuid:d1eaac1a-22b7-4bb6-9c62-cc95d6fdf1a5|permit|EncounterExample|FLD|field|http://terminology.hl7.org/CodeSystem/v3-ActCode|finished|Finished|http://terminology.hl7.org/CodeSystem/v3-ActCode|405672008|Direct questioning (procedure)|http://snomed.info/sct|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-encounter|generated|downtown location|active|CSC|http://terminology.hl7.org/CodeSystem/v3-RoleCode|115 Broadway Suite #1601||New York|MANHATTAN|NY|10006|bu|http://terminology.hl7.org/CodeSystem/location-physical-type|generated|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-observation-screening-response|en|generated|http://loinc.org|http://loinc.org|http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes|social-history|http://terminology.hl7.org/CodeSystem/observation-category|survey|http://terminology.hl7.org/CodeSystem/observation-category|http://snomed.info/sct|http://loinc.org";
        return CsvConversionUtil.convertCsvStringToScreeningResourceData(csv).get(0);
    }

    private DemographicData createDemographicData() throws IOException {
        final String csv = """
                PATIENT_MR_ID_VALUE|PATIENT_MR_ID_SYSTEM|PATIENT_MR_ID_TYPE_CODE|PATIENT_MR_ID_TYPE_SYSTEM|PATIENT_MA_ID_VALUE|PATIENT_MA_ID_SYSTEM|PATIENT_MA_ID_TYPE_CODE|PATIENT_MA_ID_TYPE_SYSTEM|PATIENT_SS_ID_VALUE|PATIENT_SS_ID_SYSTEM|PATIENT_SS_ID_TYPE_CODE|PATIENT_SS_ID_TYPE_SYSTEM|GIVEN_NAME|MIDDLE_NAME|MIDDLE_NAME_EXTENSION_URL|FAMILY_NAME|PREFIX_NAME|SUFFIX_NAME|GENDER|EXTENSION_SEX_AT_BIRTH_CODE_VALUE|EXTENSION_SEX_AT_BIRTH_CODE_URL|PATIENT_BIRTH_DATE|ADDRESS1|ADDRESS2|CITY|DISTRICT|STATE|ZIP|TELECOM_VALUE|TELECOM_SYSTEM|TELECOM_USE|SSN|EXTENSION_PERSONAL_PRONOUNS_URL|EXTENSION_PERSONAL_PRONOUNS_CODE|EXTENSION_PERSONAL_PRONOUNS_DISPLAY|EXTENSION_PERSONAL_PRONOUNS_SYSTEM|EXTENSION_GENDER_IDENTITY_URL|EXTENSION_GENDER_IDENTITY_CODE|EXTENSION_GENDER_IDENTITY_DISPLAY|EXTENSION_GENDER_IDENTITY_SYSTEM|PREFERRED_LANGUAGE_CODE_SYSTEM_NAME|PREFERRED_LANGUAGE_CODE_SYSTEM_CODE|EXTENSION_RACE_URL|EXTENSION_OMBCATEGORY_RACE_URL|EXTENSION_OMBCATEGORY_RACE_CODE|EXTENSION_OMBCATEGORY_RACE_CODE_DESCRIPTION|EXTENSION_OMBCATEGORY_RACE_CODE_SYSTEM_NAME|EXTENSION_TEXT_RACE_URL|EXTENSION_TEXT_RACE_CODE_VALUE|EXTENSION_ETHNICITY_URL|EXTENSION_OMBCATEGORY_ETHNICITY_URL|EXTENSION_OMBCATEGORY_ETHNICITY_CODE|EXTENSION_OMBCATEGORY_ETHNICITY_CODE_DESCRIPTION|EXTENSION_OMBCATEGORY_ETHNICITY_CODE_SYSTEM_NAME|EXTENSION_TEXT_ETHNICITY_URL|EXTENSION_TEXT_ETHNICITY_CODE_VALUE|MEDICAID_CIN|PATIENT_LAST_UPDATED|RELATIONSHIP_PERSON_CODE|RELATIONSHIP_PERSON_DESCRIPTION|RELATIONSHIP_PERSON_SYSTEM|RELATIONSHIP_PERSON_GIVEN_NAME|RELATIONSHIP_PERSON_FAMILY_NAME|RELATIONSHIP_PERSON_TELECOM_SYSTEM|RELATIONSHIP_PERSON_TELECOM_VALUE|PATIENT_TEXT_STATUS|SEXUAL_ORIENTATION_VALUE_CODE|SEXUAL_ORIENTATION_VALUE_CODE_DESCRIPTION|SEXUAL_ORIENTATION_VALUE_CODE_SYSTEM_NAME|SEXUAL_ORIENTATION_LAST_UPDATED|SEXUAL_ORIENTATION_PROFILE|SEXUAL_ORIENTATION_STATUS|SEXUAL_ORIENTATION_TEXT_STATUS|SEXUAL_ORIENTATION_CODE_CODE|SEXUAL_ORIENTATION_CODE_DISPLAY|SEXUAL_ORIENTATION_CODE_SYSTEM_NAME
                11223344|http://www.scn.gov/facility/CUMC|MR|http://terminology.hl7.org/CodeSystem/v2-0203|AA12345C|http://www.medicaid.gov/|MA|http://terminology.hl7.org/CodeSystem/v2-0203|999-34-2964|http://www.ssa.gov/|SS|http://terminology.hl7.org/CodeSystem/v2-0203|Jon|Bob|http://shinny.org/us/ny/hrsn/StructureDefinition/middle-name|Doe|Mr., Dr., PhD, CCNA|Jr., III|male|M|http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex|1981-07-16|115 Broadway Apt2|dummy_address2|New York|MANHATTAN|NY|10032|1234567890|phone|home|999-34-2964|http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-personal-pronouns|LA29518-0|he/him/his/his/himself|http://loinc.org|http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-gender-identity|446151000124109|Identifies as male gender (finding)|http://snomed.info/sct|urn:ietf:bcp:47|en|http://hl7.org/fhir/us/core/StructureDefinition/us-core-race|ombCategory|2028-9|Asian|urn:oid:2.16.840.1.113883.6.238|text|Asian|http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity|ombCategory|2135-2|Hispanic or Latino|urn:oid:2.16.840.1.113883.6.238|text|Hispanic or Latino|AA12345C|2024-02-23T00:00:00.00Z|MTH|Mother|http://terminology.hl7.org/CodeSystem/v2-0063|Joyce|Doe|Phone|1234567890|generated|UNK|Unknown|http://terminology.hl7.org/CodeSystem/v3-NullFlavor|2024-02-23T00:00:00Z|http://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-observation-sexual-orientation|final|generated|76690-7|Sexual orientation|http://loinc.org
                """;
        return CsvConversionUtil.convertCsvStringToDemographicData(csv).get(0);
    }

    private List<ScreeningData> createScreeningData() throws IOException {
        final String csv = """
                PATIENT_MR_ID|FACILITY_ID|ENCOUNTER_ID|ENCOUNTER_CLASS_CODE|ENCOUNTER_CLASS_CODE_DESCRIPTION|ENCOUNTER_CLASS_CODE_SYSTEM|ENCOUNTER_STATUS_CODE|ENCOUNTER_STATUS_CODE_DESCRIPTION|ENCOUNTER_STATUS_CODE_SYSTEM|ENCOUNTER_TYPE_CODE|ENCOUNTER_TYPE_CODE_DESCRIPTION|ENCOUNTER_TYPE_CODE_SYSTEM|ENCOUNTER_START_TIME|ENCOUNTER_END_TIME|ENCOUNTER_LAST_UPDATED|LOCATION_NAME|LOCATION_STATUS|LOCATION_TYPE_CODE|LOCATION_TYPE_SYSTEM|LOCATION_ADDRESS1|LOCATION_ADDRESS2|LOCATION_CITY|LOCATION_DISTRICT|LOCATION_STATE|LOCATION_ZIP|LOCATION_PHYSICAL_TYPE_CODE|LOCATION_PHYSICAL_TYPE_SYSTEM|SCREENING_STATUS_CODE|SCREENING_CODE|SCREENING_CODE_DESCRIPTION|SCREENING_CODE_SYSTEM_NAME|RECORDED_TIME|QUESTION_CODE|QUESTION_CODE_DESCRIPTION|QUESTION_CODE_SYSTEM_NAME|UCUM_UNITS|SDOH_DOMAIN|PARENT_QUESTION_CODE|ANSWER_CODE|ANSWER_CODE_DESCRIPTION|ANSWER_CODE_SYSTEM_NAME
                11223344|CUMC|EncounterExample|FLD|field|http://terminology.hl7.org/CodeSystem/v3-ActCode|finished|Finished|http://terminology.hl7.org/CodeSystem/v3-ActCode|405672008|Direct questioning (procedure)|http://snomed.info/sct|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|downtown location|active|CSC|http://terminology.hl7.org/CodeSystem/v3-RoleCode|115 Broadway Suite #1601||New York|MANHATTAN|NY|10006|bu|http://terminology.hl7.org/CodeSystem/location-physical-type|unknown|96777-8|Accountable health communities (AHC) health-related social needs screening (HRSN) tool|http://loinc.org|2023-07-12T16:08:00.000Z|71802-3|What is your living situation today?|http://loinc.org||Homelessness, Housing Instability||LA31993-1|I have a steady place to live|http://loinc.org
                """;
        return CsvConversionUtil.convertCsvStringToScreeningData(csv);
    }

    private QeAdminData createQeAdminData() throws IOException {
        final String csv = """
                PATIENT_MR_ID_VALUE|FACILITY_ACTIVE|FACILITY_ID|FACILITY_NAME|ORGANIZATION_TYPE_DISPLAY|ORGANIZATION_TYPE_CODE|ORGANIZATION_TYPE_SYSTEM|FACILITY_ADDRESS1|FACILITY_ADDRESS2|FACILITY_CITY|FACILITY_STATE|FACILITY_DISTRICT|FACILITY_ZIP|FACILITY_LAST_UPDATED|FACILITY_PROFILE|FACILITY_SCN_IDENTIFIER_TYPE_DISPLAY|FACILITY_SCN_IDENTIFIER_TYPE_VALUE|FACILITY_SCN_IDENTIFIER_TYPE_SYSTEM|FACILITY_NPI_IDENTIFIER_TYPE_CODE|FACILITY_NPI_IDENTIFIER_TYPE_VALUE|FACILITY_NPI_IDENTIFIER_TYPE_SYSTEM|FACILITY_CMS_IDENTIFIER_TYPE_CODE|FACILITY_CMS_IDENTIFIER_TYPE_VALUE|FACILITY_CMS_IDENTIFIER_TYPE_SYSTEM|FACILITY_TEXT_STATUS
                11223344|TRUE|CUMC|Care Ridge SCN|Other|other|http://terminology.hl7.org/CodeSystem/organization-type|111 Care Ridge St|Suite 1|Plainview|NY|Nassau County|11803|2024-02-23T00:00:00Z|http://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-organization|Care Ridge|SCNExample|http://www.scn.gov/scn_1|NPITypeCodeDummy|NPIValueDummy|http://example.org/npi-system|CMSTypeCodeDummy|CMSValueDummy|http://example.org/cms-system|generated
                """;
        return CsvConversionUtil.convertCsvStringToQeAdminData(csv).get(0);
    }
}
