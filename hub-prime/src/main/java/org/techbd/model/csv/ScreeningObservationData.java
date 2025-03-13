package org.techbd.model.csv;

import com.opencsv.bean.CsvBindByName;
import lombok.Getter;
import lombok.Setter;

/**
 * This class represents the data structure for the Screening CSV file,
 * containing details about the patient's
 * encounter, facility, screening details, and associated answers to questions.
 * <p>
 * It is used to bind data from a CSV file where each field corresponds to a
 * column in the CSV file.
 * </p>
 */

@Getter
@Setter
public class ScreeningObservationData {

    @CsvBindByName(column = "PATIENT_MR_ID_VALUE")
    private String patientMrIdValue;

    @CsvBindByName(column = "FACILITY_ID")
    private String facilityId;

    @CsvBindByName(column = "FACILITY_NAME")
    private String facilityName;

    @CsvBindByName(column = "ENCOUNTER_ID")
    private String encounterId;

    @CsvBindByName(column = "SCREENING_IDENTIFIER")
    private String screeningIdentifier;

    @CsvBindByName(column = "SCREENING_CODE")
    private String screeningCode;

    @CsvBindByName(column = "SCREENING_CODE_DESCRIPTION")
    private String screeningCodeDescription;

    @CsvBindByName(column = "SCREENING_CODE_SYSTEM")
    private String screeningCodeSystem;

    @CsvBindByName(column = "QUESTION_CODE")
    private String questionCode;

    @CsvBindByName(column = "QUESTION_CODE_DESCRIPTION")
    private String questionCodeDescription;

    @CsvBindByName(column = "QUESTION_CODE_SYSTEM")
    private String questionCodeSystem;

    @CsvBindByName(column = "ANSWER_CODE")
    private String answerCode;

    @CsvBindByName(column = "ANSWER_CODE_DESCRIPTION")
    private String answerCodeDescription;

    @CsvBindByName(column = "ANSWER_CODE_SYSTEM")
    private String answerCodeSystem;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SDOH_CODE")
    private String observationCategorySdohCode;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SDOH_TEXT")
    private String observationCategorySdohText;

    @CsvBindByName(column = "DATA_ABSENT_REASON_CODE")
    private String dataAbsentReasonCode;

    @CsvBindByName(column = "DATA_ABSENT_REASON_DISPLAY")
    private String dataAbsentReasonDisplay;

    @CsvBindByName(column = "POTENTIAL_NEED_INDICATED")
    private String potentialNeedIndicated;

    @CsvBindByName(column = "SCREENING_START_DATETIME")
    private String screeningStartDatetime;

    // @CsvBindByName(column = "OBSERVATION_ID")
    private String observationId;

    @CsvBindByName(column = "SCREENING_END_DATETIME")
    private String screeningEndDatetime;

    // Default constructor
    public ScreeningObservationData() {
    }
}
