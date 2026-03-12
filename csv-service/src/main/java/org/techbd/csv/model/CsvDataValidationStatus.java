package org.techbd.csv.model;

public enum CsvDataValidationStatus {
    /**
     * Success - All CSV files in the ZIP were processed successfully
     * and every record was converted to FHIR without errors.
     */
    SUCCESS("Success"),

    /**
     * Partial Success - At least one FHIR resource was generated,
     * but some CSV files or records encountered errors 
     * (e.g., encoding issues, incomplete groups, or data integrity problems).
     */
    PARTIAL_SUCCESS("Partial Success"),

    /**
     * Failed - No FHIR resources were generated because all CSV files 
     * failed processing due to encoding issues, incomplete groups, 
     * or data integrity errors.
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
