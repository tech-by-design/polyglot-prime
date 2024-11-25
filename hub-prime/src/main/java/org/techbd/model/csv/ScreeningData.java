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

    @CsvBindByName(column = "SCREENING_STATUS_CODE")
    String screeningStatusCode;

    @CsvBindByName(column = "SCREENING_CODE")
    String screeningCode;

    @CsvBindByName(column = "SCREENING_CODE_DESCRIPTION")
    String screeningCodeDescription;

    @CsvBindByName(column = "RECORDED_TIME")
    String recordedTime;

    @CsvBindByName(column = "QUESTION_CODE")
    String questionCode;

    @CsvBindByName(column = "QUESTION_CODE_DESCRIPTION")
    String questionCodeDescription;

    @CsvBindByName(column = "QUESTION_CODE_SYSTEM_NAME")
    String questionCodeSystemName;

    @CsvBindByName(column = "QUESTION_CODE_TEXT")
    String questionCodeText;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SDOH_TEXT")
    String observationCategorySdohText;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SDOH_CODE")
    String observationCategorySdohCode;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SDOH_DISPLAY")
    String observationCategorySdohDisplay;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SNOMED_CODE")
    String observationCategorySnomedCode;

    @CsvBindByName(column = "OBSERVATION_CATEGORY_SNOMED_DISPLAY")
    String observationCategorySnomedDisplay;

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
