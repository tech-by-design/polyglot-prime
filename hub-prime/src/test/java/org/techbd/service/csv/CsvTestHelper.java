package org.techbd.service.csv;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.util.CsvConversionUtil;

public class CsvTestHelper {
    public static final String BASE_FHIR_URL="http://test.shinny.org/us/ny/hrsn";
    public static ScreeningProfileData createScreeningProfileData() throws IOException {
        final String csv = """
                PATIENT_MR_ID_VALUE,ENCOUNTER_ID,ENCOUNTER_CLASS_CODE,ENCOUNTER_STATUS_CODE,ENCOUNTER_TYPE_CODE,ENCOUNTER_TYPE_CODE_DESCRIPTION,ENCOUNTER_TYPE_CODE_SYSTEM,ENCOUNTER_LAST_UPDATED,CONSENT_LAST_UPDATED,CONSENT_DATE_TIME,CONSENT_POLICY_AUTHORITY,CONSENT_PROVISION_TYPE,SCREENING_LAST_UPDATED,SCREENING_STATUS_CODE
                11223344,EncounterExample,FLD,finished,405672008,Direct questioning (procedure),http://snomed.info/sct,2024-02-23T00:00:00Z,2024-02-23T00:00:00Z,2024-02-23T00:00:00Z,urn:uuid:d1eaac1a-22b7-4bb6-9c62-cc95d6fdf1a5,permit,2024-02-23T00:00:00Z,unknown
                """;
        return CsvConversionUtil.convertCsvStringToScreeningProfileData(csv).get("EncounterExample").get(0);
    }

    public static DemographicData createDemographicData() throws IOException {
        final String csv = """
                PATIENT_MR_ID_VALUE,PATIENT_MA_ID_VALUE,PATIENT_SS_ID_VALUE,GIVEN_NAME,MIDDLE_NAME,FAMILY_NAME,GENDER,EXTENSION_SEX_AT_BIRTH_CODE_VALUE,PATIENT_BIRTH_DATE,ADDRESS1,CITY,DISTRICT,STATE,ZIP,TELECOM_VALUE,EXTENSION_PERSONAL_PRONOUNS_CODE,EXTENSION_PERSONAL_PRONOUNS_DISPLAY,EXTENSION_PERSONAL_PRONOUNS_SYSTEM,EXTENSION_GENDER_IDENTITY_CODE,EXTENSION_GENDER_IDENTITY_DISPLAY,EXTENSION_GENDER_IDENTITY_SYSTEM,PREFERRED_LANGUAGE_CODE_SYSTEM_NAME,PREFERRED_LANGUAGE_CODE_SYSTEM_CODE,EXTENSION_OMBCATEGORY_RACE_CODE,EXTENSION_OMBCATEGORY_RACE_CODE_DESCRIPTION,EXTENSION_OMBCATEGORY_RACE_CODE_SYSTEM_NAME,EXTENSION_OMBCATEGORY_ETHNICITY_CODE,EXTENSION_OMBCATEGORY_ETHNICITY_CODE_DESCRIPTION,EXTENSION_OMBCATEGORY_ETHNICITY_CODE_SYSTEM_NAME,PATIENT_LAST_UPDATED,RELATIONSHIP_PERSON_CODE,RELATIONSHIP_PERSON_DESCRIPTION,RELATIONSHIP_PERSON_GIVEN_NAME,RELATIONSHIP_PERSON_FAMILY_NAME,RELATIONSHIP_PERSON_TELECOM_VALUE,SEXUAL_ORIENTATION_VALUE_CODE,SEXUAL_ORIENTATION_VALUE_CODE_DESCRIPTION,SEXUAL_ORIENTATION_VALUE_CODE_SYSTEM_NAME,SEXUAL_ORIENTATION_LAST_UPDATED
                11223344,AA12345C,999-34-2964,Jon,Bob,Doe,male,M,1981-07-16,115 Broadway Apt2,New York,MANHATTAN,NY,10032,1234567890,LA29518-0,he/him/his/his/himself,http://loinc.org,446151000124109,Identifies as male gender (finding),http://snomed.info/sct,urn:ietf:bcp:47,en,2028-9,Asian,urn:oid:2.16.840.1.113883.6.238,2135-2,Hispanic or Latino,urn:oid:2.16.840.1.113883.6.238,2024-02-23T00:00:00.00Z,MTH,Mother,Joyce,Doe,1234567890,UNK,Unknown,http://terminology.hl7.org/CodeSystem/v3-NullFlavor,2024-02-23T00:00:00Z
                """;
        return CsvConversionUtil.convertCsvStringToDemographicData(csv).get("11223344").get(0);
    }

    public static List<ScreeningObservationData> createScreeningObservationData() throws IOException {
        final String csv = """
                PATIENT_MR_ID_VALUE,SCREENING_CODE,SCREENING_CODE_DESCRIPTION,RECORDED_TIME,QUESTION_CODE,QUESTION_CODE_DISPLAY,QUESTION_CODE_TEXT,OBSERVATION_CATEGORY_SDOH_TEXT,OBSERVATION_CATEGORY_SDOH_CODE,OBSERVATION_CATEGORY_SDOH_DISPLAY,OBSERVATION_CATEGORY_SNOMED_CODE,OBSERVATION_CATEGORY_SNOMED_DISPLAY,ANSWER_CODE,ANSWER_CODE_DESCRIPTION,DATA_ABSENT_REASON_CODE,DATA_ABSENT_REASON_DISPLAY,DATA_ABSENT_REASON_TEXT,ENCOUNTER_ID
                11223344,96777-8,Accountable health communities (AHC) health-related social needs screening (HRSN) tool,2023-07-12T16:08:00.000Z,71802-3,Housing status,What is your living situation today?,,housing-instability,Housing Instability,,,LA31993-1,I have a steady place to live,,,,EncounterExample
                11223344,96777-8,Accountable health communities (AHC) health-related social needs screening (HRSN) tool,2023-07-12T16:08:00.000Z,96778-6,Problems with place where you live,Think about the place you live. Do you have problems with any of the following?,,inadequate-housing,Inadequate Housing,,,LA28580-1,Mold,,,,EncounterExample
                11223344,96777-8,Accountable health communities (AHC) health-related social needs screening (HRSN) tool,2023-07-12T16:08:00.000Z,88122-7,Within the past 12 months we worried whether our food would run out before we got money to buy more [U.S. FSS],"Within the past 12 months, you worried that your food would run out before you got money to buy more.",,food-insecurity,Food Insecurity,,,LA28397-0,Often true,,,,EncounterExample
                11223344,96777-8,Accountable health communities (AHC) health-related social needs screening (HRSN) tool,2023-07-12T16:08:00.000Z,88123-5,Within the past 12 months the food we bought just didn't last and we didn't have money to get more [U.S. FSS],"Within the past 12 months, the food you bought just didn't last and you didn't have money to get more.",,food-insecurity,Food Insecurity,,,LA28397-0,Often true,,,,EncounterExample
                11223344,96777-8,Accountable health communities (AHC) health-related social needs screening (HRSN) tool,2023-07-12T16:08:00.000Z,93030-5,"Has lack of transportation kept you from medical appointments, meetings, work, or from getting things needed for daily living","In the past 12 months, has lack of reliable transportation kept you from medical appointments, meetings, work or from getting things needed for daily living?",,transportation-insecurity,Transportation Insecurity,,,LA32-8,No,,,,EncounterExample
                11223344,96777-8,Accountable health communities (AHC) health-related social needs screening (HRSN) tool,2023-07-12T16:08:00.000Z,96779-4,"Has the electric, gas, oil, or water company threatened to shut off services in your home in past 12 months","In the past 12 months has the electric, gas, oil, or water company threatened to shut off services in your home?",,utility-insecurity,Utility Insecurity,,,LA32-8,No,,,,EncounterExample
                11223344,96777-8,Accountable health communities (AHC) health-related social needs screening (HRSN) tool,2023-07-12T16:08:00.000Z,95618-5,Physically hurt you [HITS],"How often does anyone, including family and friends, physically hurt you?",Interpersonal Safety,sdoh-category-unspecified,SDOH Category Unspecified,,,LA6270-8,Never,,,,EncounterExample
                11223344,96777-8,Accountable health communities (AHC) health-related social needs screening (HRSN) tool,2023-07-12T16:08:00.000Z,95617-7,Insult you or talk down to you [HITS],"How often does anyone, including family and friends, insult or talk down to you?",Interpersonal Safety,sdoh-category-unspecified,SDOH Category Unspecified,,,LA6270-8,Never,,,,EncounterExample
                11223344,96777-8,Accountable health communities (AHC) health-related social needs screening (HRSN) tool,2023-07-12T16:08:00.000Z,95616-9,Threaten you with physical harm [HITS],"How often does anyone, including family and friends, threaten you with harm?",Interpersonal Safety,sdoh-category-unspecified,SDOH Category Unspecified,,,LA6270-8,Never,,,,EncounterExample
                11223344,96777-8,Accountable health communities (AHC) health-related social needs screening (HRSN) tool,2023-07-12T16:08:00.000Z,95615-1,Scream or curse at you [HITS],"How often does anyone, including family and friends, scream or curse at you?",Interpersonal Safety,sdoh-category-unspecified,SDOH Category Unspecified,,,LA6270-8,Never,,,,EncounterExample
                11223344,96777-8,Accountable health communities (AHC) health-related social needs screening (HRSN) tool,2023-07-12T16:08:00.000Z,95614-4,Total score [HITS],Total Safety Score,Interpersonal Safety,sdoh-category-unspecified,SDOH Category Unspecified,,,,4,,,,EncounterExample
                """;
        return CsvConversionUtil.convertCsvStringToScreeningObservationData(csv).get("EncounterExample");
    }

    public static QeAdminData createQeAdminData() throws IOException {
        final String csv = """
                PATIENT_MR_ID_VALUE,FACILITY_ID,FACILITY_NAME,ORGANIZATION_TYPE_DISPLAY,ORGANIZATION_TYPE_CODE,FACILITY_ADDRESS1,FACILITY_CITY,FACILITY_STATE,FACILITY_DISTRICT,FACILITY_ZIP,FACILITY_LAST_UPDATED,FACILITY_IDENTIFIER_TYPE_DISPLAY,FACILITY_IDENTIFIER_TYPE_VALUE,FACILITY_IDENTIFIER_TYPE_SYSTEM
                11223344,CUMC,Care Ridge SCN,Other,other,111 Care Ridge St,Plainview,NY,Nassau County,11803,2024-02-23T00:00:00Z,Care Ridge,SCNExample,http://www.scn.ny.gov/
                """;
        return CsvConversionUtil.convertCsvStringToQeAdminData(csv).get("11223344").get(0);
    }
    
    public static Map<String, String> getProfileMap() {
            Map<String, String> profileMap = new HashMap<>();
            profileMap.put("bundle", "/StructureDefinition/SHINNYBundleProfile");
            profileMap.put("patient", "/StructureDefinition/shinny-patient");
            profileMap.put("consent", "/StructureDefinition/shinny-Consent");
            profileMap.put("encounter", "/StructureDefinition/shinny-encounter");
            profileMap.put("organization", "/StructureDefinition/shin-ny-organization");
            profileMap.put("observation", "/StructureDefinition/shinny-observation-screening-response");
            profileMap.put("questionnaire", "/StructureDefinition/shinny-questionnaire");
            profileMap.put("practitioner", "/StructureDefinition/shin-ny-practitioner");
            profileMap.put("questionnaireResponse", "/StructureDefinition/shinny-questionnaire");
            profileMap.put("observationSexualOrientation",
                            "/StructureDefinition/shinny-observation-sexual-orientation");
            return profileMap;
    }

}
