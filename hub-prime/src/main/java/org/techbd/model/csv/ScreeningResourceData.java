package org.techbd.model.csv;

import com.opencsv.bean.CsvBindByName;

import lombok.Getter;

@Getter
public class ScreeningResourceData {

    @CsvBindByName(column = "PATIENT_MR_ID_VALUE")
    private String patientMrIdValue;

    @CsvBindByName(column = "FACILITY_ID")
    private String facilityId;

    @CsvBindByName(column = "CONSENT_PROFILE")
    private String consentProfile;

    @CsvBindByName(column = "CONSENT_LAST_UPDATED")
    private String consentLastUpdated;

    @CsvBindByName(column = "CONSENT_TEXT_STATUS")
    private String consentTextStatus;

    @CsvBindByName(column = "CONSENT_STATUS")
    private String consentStatus;

    @CsvBindByName(column = "CONSENT_SCOPE_CODE")
    private String consentScopeCode;

    @CsvBindByName(column = "CONSENT_SCOPE_TEXT")
    private String consentScopeText;

    @CsvBindByName(column = "CONSENT_CATEGORY_IDSCL_CODE")
    private String consentCategoryIdsclCode;

    @CsvBindByName(column = "CONSENT_CATEGORY_IDSCL_SYSTEM")
    private String consentCategoryIdsclSystem;

    @CsvBindByName(column = "CONSENT_CATEGORY_LOINC_CODE")
    private String consentCategoryLoincCode;

    @CsvBindByName(column = "CONSENT_CATEGORY_LOINC_SYSTEM")
    private String consentCategoryLoincSystem;

    @CsvBindByName(column = "CONSENT_CATEGORY_LOINC_DISPLAY")
    private String consentCategoryLoincDisplay;

    @CsvBindByName(column = "CONSENT_DATE_TIME")
    private String consentDateTime;

    @CsvBindByName(column = "CONSENT_POLICY_AUTHORITY")
    private String consentPolicyAuthority;

    @CsvBindByName(column = "CONSENT_PROVISION_TYPE")
    private String consentProvisionType;

    @CsvBindByName(column = "ENCOUNTER_ID")
    private String encounterId;

    @CsvBindByName(column = "ENCOUNTER_CLASS_CODE")
    private String encounterClassCode;

    @CsvBindByName(column = "ENCOUNTER_CLASS_CODE_DESCRIPTION")
    private String encounterClassCodeDescription;

    @CsvBindByName(column = "ENCOUNTER_CLASS_CODE_SYSTEM")
    private String encounterClassCodeSystem;

    @CsvBindByName(column = "ENCOUNTER_STATUS_CODE")
    private String encounterStatusCode;

    @CsvBindByName(column = "ENCOUNTER_STATUS_CODE_DESCRIPTION")
    private String encounterStatusCodeDescription;

    @CsvBindByName(column = "ENCOUNTER_STATUS_CODE_SYSTEM")
    private String encounterStatusCodeSystem;

    @CsvBindByName(column = "ENCOUNTER_TYPE_CODE")
    private String encounterTypeCode;

    @CsvBindByName(column = "ENCOUNTER_TYPE_CODE_DESCRIPTION")
    private String encounterTypeCodeDescription;

    @CsvBindByName(column = "ENCOUNTER_TYPE_CODE_SYSTEM")
    private String encounterTypeCodeSystem;

    @CsvBindByName(column = "ENCOUNTER_START_TIME")
    private String encounterStartTime;

    @CsvBindByName(column = "ENCOUNTER_END_TIME")
    private String encounterEndTime;

    @CsvBindByName(column = "ENCOUNTER_LAST_UPDATED")
    private String encounterLastUpdated;

    @CsvBindByName(column = "ENCOUNTER_PROFILE")
    private String encounterProfile;

    @CsvBindByName(column = "ENCOUNTER_TEXT_STATUS")
    private String encounterTextStatus;

    @CsvBindByName(column = "LOCATION_NAME")
    private String locationName;

    @CsvBindByName(column = "LOCATION_STATUS")
    private String locationStatus;

    @CsvBindByName(column = "LOCATION_TYPE_CODE")
    private String locationTypeCode;

    @CsvBindByName(column = "LOCATION_TYPE_SYSTEM")
    private String locationTypeSystem;

    @CsvBindByName(column = "LOCATION_ADDRESS1")
    private String locationAddress1;

    @CsvBindByName(column = "LOCATION_ADDRESS2")
    private String locationAddress2;

    @CsvBindByName(column = "LOCATION_CITY")
    private String locationCity;

    @CsvBindByName(column = "LOCATION_DISTRICT")
    private String locationDistrict;

    @CsvBindByName(column = "LOCATION_STATE")
    private String locationState;

    @CsvBindByName(column = "LOCATION_ZIP")
    private String locationZip;

    @CsvBindByName(column = "LOCATION_PHYSICAL_TYPE_CODE")
    private String locationPhysicalTypeCode;

    @CsvBindByName(column = "LOCATION_PHYSICAL_TYPE_SYSTEM")
    private String locationPhysicalTypeSystem;

    @CsvBindByName(column = "LOCATION_TEXT_STATUS")
    private String locationTextStatus;

    @CsvBindByName(column = "LOCATION_LAST_UPDATED")
    private String locationLastUpdated;

    @CsvBindByName(column = "SCREENING_LAST_UPDATED")
    private String screeningLastUpdated;

    @CsvBindByName(column = "SCREENING_PROFILE")
    private String screeningProfile;

    @CsvBindByName(column = "SCREENING_LANGUAGE")
    private String screeningLanguage;

    @CsvBindByName(column = "SCREENING_TEXT_STATUS")
    private String screeningTextStatus;

    @CsvBindByName(column = "SCREENING_CODE_SYSTEM_NAME")
    private String screeningCodeSystemName;

    @CsvBindByName(column = "QUESTION_CODE_SYSTEM_NAME")
    private String questionCodeSystemName;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SDOH_SYSTEM")
    private String observationCategorySdoHSystem;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SOCIAL_HISTORY_CODE")
    private String observationCategorySocialHistoryCode;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SOCIAL_HISTORY_SYSTEM")
    private String observationCategorySocialHistorySystem;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SURVEY_CODE")
    private String observationCategorySurveyCode;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SURVEY_SYSTEM")
    private String observationCategorySurveySystem;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SNOMED_SYSTEM")
    private String observationCategorySnomedSystem;

    @CsvBindByName(column = "ANSWER_CODE_SYSTEM_NAME")
    private String answerCodeSystemName;

    /**
     * Default constructor for OpenCSV to create an instance of
     * ScreeningResourceData.
     */
    public ScreeningResourceData() {
    }
}
