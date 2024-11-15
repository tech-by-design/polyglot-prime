package org.techbd.model.csv;

import com.opencsv.bean.CsvBindByName;

import lombok.Getter;

/**
 * This class represents the data structure for the Screening CSV file,
 * containing details about the patient's
 * encounter, location, screening details, and associated answers to questions.
 * <p>
 * It is used to bind data from a CSV file where each field corresponds to a
 * column in the CSV file.
 * </p>
 */
@Getter

public class ScreeningData {

    @CsvBindByName(column = "PATIENT_MR_ID")
    String patientMrId;

    @CsvBindByName(column = "FACILITY_ID")
    String facilityId;

    @CsvBindByName(column = "ENCOUNTER_ID")
    String encounterId;

    @CsvBindByName(column = "ENCOUNTER_CLASS_CODE")
    String encounterClassCode;

    @CsvBindByName(column = "ENCOUNTER_CLASS_CODE_DESCRIPTION")
    String encounterClassCodeDescription;

    @CsvBindByName(column = "ENCOUNTER_CLASS_CODE_SYSTEM")
    String encounterClassCodeSystem;

    @CsvBindByName(column = "ENCOUNTER_STATUS_CODE")
    String encounterStatusCode;

    @CsvBindByName(column = "ENCOUNTER_STATUS_CODE_DESCRIPTION")
    String encounterStatusCodeDescription;

    @CsvBindByName(column = "ENCOUNTER_STATUS_CODE_SYSTEM")
    String encounterStatusCodeSystem;

    @CsvBindByName(column = "ENCOUNTER_TYPE_CODE")
    String encounterTypeCode;

    @CsvBindByName(column = "ENCOUNTER_TYPE_CODE_DESCRIPTION")
    String encounterTypeCodeDescription;

    @CsvBindByName(column = "ENCOUNTER_TYPE_CODE_SYSTEM")
    String encounterTypeCodeSystem;

    @CsvBindByName(column = "ENCOUNTER_START_TIME")
    String encounterStartTime;

    @CsvBindByName(column = "ENCOUNTER_END_TIME")
    String encounterEndTime;

    @CsvBindByName(column = "ENCOUNTER_LAST_UPDATED")
    String encounterLastUpdated;

    @CsvBindByName(column = "LOCATION_NAME")
    String locationName;

    @CsvBindByName(column = "LOCATION_STATUS")
    String locationStatus;

    @CsvBindByName(column = "LOCATION_TYPE_CODE")
    String locationTypeCode;

    @CsvBindByName(column = "LOCATION_TYPE_SYSTEM")
    String locationTypeSystem;

    @CsvBindByName(column = "LOCATION_ADDRESS1")
    String locationAddress1;

    @CsvBindByName(column = "LOCATION_ADDRESS2")
    String locationAddress2;

    @CsvBindByName(column = "LOCATION_CITY")
    String locationCity;

    @CsvBindByName(column = "LOCATION_DISTRICT")
    String locationDistrict;

    @CsvBindByName(column = "LOCATION_STATE")
    String locationState;

    @CsvBindByName(column = "LOCATION_ZIP")
    String locationZip;

    @CsvBindByName(column = "LOCATION_PHYSICAL_TYPE_CODE")
    String locationPhysicalTypeCode;

    @CsvBindByName(column = "LOCATION_PHYSICAL_TYPE_SYSTEM")
    String locationPhysicalTypeSystem;

    @CsvBindByName(column = "SCREENING_STATUS_CODE")
    String screeningStatusCode;

    @CsvBindByName(column = "SCREENING_CODE")
    String screeningCode;

    @CsvBindByName(column = "SCREENING_CODE_DESCRIPTION")
    String screeningCodeDescription;

    @CsvBindByName(column = "SCREENING_CODE_SYSTEM_NAME")
    String screeningCodeSystemName;

    @CsvBindByName(column = "RECORDED_TIME")
    String recordedTime;

    @CsvBindByName(column = "QUESTION_CODE")
    String questionCode;

    @CsvBindByName(column = "QUESTION_CODE_DESCRIPTION")
    String questionCodeDescription;

    @CsvBindByName(column = "QUESTION_CODE_SYSTEM_NAME")
    String questionCodeSystemName;

    @CsvBindByName(column = "UCUM_UNITS")
    String ucumUnits;

    @CsvBindByName(column = "SDOH_DOMAIN")
    String sdohDomain;

    @CsvBindByName(column = "PARENT_QUESTION_CODE")
    String parentQuestionCode;

    @CsvBindByName(column = "ANSWER_CODE")
    String answerCode;

    @CsvBindByName(column = "ANSWER_CODE_DESCRIPTION")
    String answerCodeDescription;

    @CsvBindByName(column = "ANSWER_CODE_SYSTEM_NAME")
    String answerCodeSystemName;

    /**
     * Default constructor for OpenCSV to create an instance of ScreeningData.
     */
    public ScreeningData() {
    }
}
