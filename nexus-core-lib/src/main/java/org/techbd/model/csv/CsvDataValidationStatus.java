package org.techbd.model.csv;

public enum CsvDataValidationStatus {
    /**
     * Indicates that all CSV files in the ZIP were processed successfully 
     * without encoding issues, incomplete groups, or data integrity errors.
     */
    SUCCESS("Success"),

    /**
     * Indicates that one or more CSV files failed processing due to
     * UTF-8 encoding issues, incomplete groups, or data integrity errors
     * during FHIR conversion.
     */
    FAILED("Failed");

    private final String description;

    CsvDataValidationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
