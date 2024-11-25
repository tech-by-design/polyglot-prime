package org.techbd.model.csv;

import com.opencsv.bean.CsvBindByName;

import lombok.Getter;

@Getter
public class ScreeningResourceData {

    @CsvBindByName
    private String PATIENT_MR_ID_VALUE;

    @CsvBindByName
    private String FACILITY_ID;

    @CsvBindByName
    private String CONSENT_PROFILE;

    @CsvBindByName
    private String CONSENT_LAST_UPDATED;

    @CsvBindByName
    private String CONSENT_TEXT_STATUS;

    @CsvBindByName
    private String CONSENT_STATUS;

    @CsvBindByName
    private String CONSENT_SCOPE_CODE;

    @CsvBindByName
    private String CONSENT_SCOPE_TEXT;

    @CsvBindByName
    private String CONSENT_CATEGORY_IDSCL_CODE;

    @CsvBindByName
    private String CONSENT_CATEGORY_IDSCL_SYSTEM;

    @CsvBindByName
    private String CONSENT_CATEGORY_LOINC_CODE;

    @CsvBindByName
    private String CONSENT_CATEGORY_LOINC_SYSTEM;

    @CsvBindByName
    private String CONSENT_CATEGORY_LOINC_DISPLAY;

    @CsvBindByName
    private String CONSENT_DATE_TIME;

    @CsvBindByName
    private String CONSENT_POLICY_AUTHORITY;

    @CsvBindByName
    private String CONSENT_PROVISION_TYPE;

    @CsvBindByName
    private String ENCOUNTER_ID;

    @CsvBindByName
    private String ENCOUNTER_CLASS_CODE;

    @CsvBindByName
    private String ENCOUNTER_CLASS_CODE_DESCRIPTION;

    @CsvBindByName
    private String ENCOUNTER_CLASS_CODE_SYSTEM;

    @CsvBindByName
    private String ENCOUNTER_STATUS_CODE;

    @CsvBindByName
    private String ENCOUNTER_STATUS_CODE_DESCRIPTION;

    @CsvBindByName
    private String ENCOUNTER_STATUS_CODE_SYSTEM;

    @CsvBindByName
    private String ENCOUNTER_TYPE_CODE;

    @CsvBindByName
    private String ENCOUNTER_TYPE_CODE_DESCRIPTION;

    @CsvBindByName
    private String ENCOUNTER_TYPE_CODE_SYSTEM;

    @CsvBindByName
    private String ENCOUNTER_START_TIME;

    @CsvBindByName
    private String ENCOUNTER_END_TIME;

    @CsvBindByName
    private String ENCOUNTER_LAST_UPDATED;

    @CsvBindByName
    private String ENCOUNTER_PROFILE;

    @CsvBindByName
    private String ENCOUNTER_TEXT_STATUS;

    @CsvBindByName
    private String LOCATION_NAME;

    @CsvBindByName
    private String LOCATION_STATUS;

    @CsvBindByName
    private String LOCATION_TYPE_CODE;

    @CsvBindByName
    private String LOCATION_TYPE_SYSTEM;

    @CsvBindByName
    private String LOCATION_ADDRESS1;

    @CsvBindByName
    private String LOCATION_ADDRESS2;

    @CsvBindByName
    private String LOCATION_CITY;

    @CsvBindByName
    private String LOCATION_DISTRICT;

    @CsvBindByName
    private String LOCATION_STATE;

    @CsvBindByName
    private String LOCATION_ZIP;

    @CsvBindByName
    private String LOCATION_PHYSICAL_TYPE_CODE;

    @CsvBindByName
    private String LOCATION_PHYSICAL_TYPE_SYSTEM;

    @CsvBindByName
    private String LOCATION_TEXT_STATUS;

    @CsvBindByName
    private String LOCATION_LAST_UPDATED;

    @CsvBindByName
    private String SCREENING_LAST_UPDATED;

    @CsvBindByName
    private String SCREENING_PROFILE;

    @CsvBindByName
    private String SCREENING_LANGUAGE;

    @CsvBindByName
    private String SCREENING_TEXT_STATUS;

    @CsvBindByName
    private String SCREENING_CODE_SYSTEM_NAME;

    @CsvBindByName
    private String QUESTION_CODE_SYSTEM_NAME;

    @CsvBindByName
    private String OBSERVATION_CATEGORY_SDOH_SYSTEM;

    @CsvBindByName
    private String OBSERVATION_CATEGORY_SOCIAL_HISTORY_CODE;

    @CsvBindByName
    private String OBSERVATION_CATEGORY_SOCIAL_HISTORY_SYSTEM;

    @CsvBindByName
    private String OBSERVATION_CATEGORY_SURVEY_CODE;

    @CsvBindByName
    private String OBSERVATION_CATEGORY_SURVEY_SYSTEM;

    @CsvBindByName
    private String OBSERVATION_CATEGORY_SNOMED_SYSTEM;

    @CsvBindByName
    private String ANSWER_CODE_SYSTEM_NAME;
}
