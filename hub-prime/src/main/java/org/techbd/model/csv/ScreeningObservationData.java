package org.techbd.model.csv;

import com.opencsv.bean.CsvBindByName;
import lombok.Getter;
import lombok.Setter;

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
@Setter
public class ScreeningObservationData {

    @CsvBindByName(column = "PATIENT_MR_ID_VALUE")
    private String patientMrIdValue;

    @CsvBindByName(column = "SCREENING_CODE")
    private String screeningCode;

    @CsvBindByName(column = "ENCOUNTER_ID")
    private String encounterId;

    @CsvBindByName(column = "SCREENING_CODE_DESCRIPTION")
    private String screeningCodeDescription;

    @CsvBindByName(column = "RECORDED_TIME")
    private String recordedTime;

    @CsvBindByName(column = "QUESTION_CODE")
    private String questionCode;

    @CsvBindByName(column = "QUESTION_CODE_DISPLAY")
    private String questionCodeDisplay;

    @CsvBindByName(column = "QUESTION_CODE_TEXT")
    private String questionCodeText;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SDOH_TEXT")
    private String observationCategorySdohText;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SDOH_CODE")
    private String observationCategorySdohCode;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SDOH_DISPLAY")
    private String observationCategorySdohDisplay;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SNOMED_CODE")
    private String observationCategorySnomedCode;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SNOMED_DISPLAY")
    private String observationCategorySnomedDisplay;

    @CsvBindByName(column = "ANSWER_CODE")
    private String answerCode;

    @CsvBindByName(column = "ANSWER_CODE_DESCRIPTION")
    private String answerCodeDescription;

    @CsvBindByName(column = "DATA_ABSENT_REASON_CODE")
    private String dataAbsentReasonCode;

    @CsvBindByName(column = "DATA_ABSENT_REASON_DISPLAY")
    private String dataAbsentReasonDisplay;

    @CsvBindByName(column = "DATA_ABSENT_REASON_TEXT")
    private String dataAbsentReasonText;

    // Default constructor
    public ScreeningObservationData() {
    }
}
