package org.techbd.model.csv;

import com.opencsv.bean.CsvBindByName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScreeningProfileData {

    @CsvBindByName(column = "PATIENT_MR_ID_VALUE")
    private String patientMrIdValue;

    @CsvBindByName(column = "FACILITY_ID")
    private String facilityId;

    @CsvBindByName(column = "FACILITY_NAME")
    private String facilityName;

    @CsvBindByName(column = "ENCOUNTER_ID")
    private String encounterId;

    @CsvBindByName(column = "ENCOUNTER_ID_SYSTEM")
    private String encounterIdSystem;

    @CsvBindByName(column = "SCREENING_IDENTIFIER")
    private String screeningIdentifier;

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

    @CsvBindByName(column = "ENCOUNTER_START_DATETIME")
    private String encounterStartDatetime;

    @CsvBindByName(column = "ENCOUNTER_END_DATETIME")
    private String encounterEndDatetime;

    @CsvBindByName(column = "ENCOUNTER_LOCATION")
    private String encounterLocation;

    @CsvBindByName(column = "PROCEDURE_STATUS_CODE")
    private String procedureStatusCode;

    @CsvBindByName(column = "PROCEDURE_CODE")
    private String procedureCode;

    @CsvBindByName(column = "PROCEDURE_CODE_DESCRIPTION")
    private String procedureCodeDescription;

    @CsvBindByName(column = "PROCEDURE_CODE_SYSTEM")
    private String procedureCodeSystem;

    @CsvBindByName(column = "PROCEDURE_CODE_MODIFIER")
    private String procedureCodeModifier;

    @CsvBindByName(column = "CONSENT_STATUS")
    private String consentStatus;

    @CsvBindByName(column = "CONSENT_DATE_TIME")
    private String consentDateTime;

    @CsvBindByName(column = "SCREENING_LAST_UPDATED")
    private String screeningLastUpdated;

    @CsvBindByName(column = "SCREENING_STATUS_CODE")
    private String screeningStatusCode;

    @CsvBindByName(column = "SCREENING_STATUS_CODE_DESCRIPTION")
    private String screeningStatusCodeDescription;

    @CsvBindByName(column = "SCREENING_STATUS_CODE_SYSTEM")
    private String screeningStatusCodeSystem;

    @CsvBindByName(column = "SCREENING_LANGUAGE_CODE")
    private String screeningLanguageCode;

    @CsvBindByName(column = "SCREENING_LANGUAGE_DESCRIPTION")
    private String screeningLanguageDescription;

    @CsvBindByName(column = "SCREENING_LANGUAGE_CODE_SYSTEM")
    private String screeningLanguageCodeSystem;

    @CsvBindByName(column = "SCREENING_ENTITY_ID")
    private String screeningEntityId;

    @CsvBindByName(column = "SCREENING_ENTITY_ID_CODE_SYSTEM")
    private String screeningEntityIdCodeSystem;

    // Default constructor
    public ScreeningProfileData() {
    }
}
