package org.techbd.model.csv;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents metrics collected during the processing of CSV files contained within a ZIP archive.
 * <p>
 * This class tracks various statistics such as the total number of files in the ZIP,
 * the number of FHIR bundles generated, the number of bundles sent to the NYEC API,
 * and the overall validation status of the ZIP file processing.
 * </p>
 * <p>
 * It is intended to provide a summary of the CSV processing workflow, including
 * success and failure states, to facilitate monitoring and debugging.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvProcessingMetrics {

    /** Total number of files found inside the ZIP (CSV + others). */
    private int totalNumberOfFilesInZipFile;
    /**
     * Among complete groups without Frictionless errors, the number of encounter
     * data sets
     * that could be translated into FHIR bundles (i.e., the number of FHIR bundles
     * that could be generated).
     */
    private int numberOfFhirBundlesGeneratedFromZipFile;

    /**
     * Tracks overall validation result for ZIP file processing.
     * Default is "Success". If any errors occur (e.g., UTF-8 encoding issues,
     * incomplete groups, or data integrity errors during FHIR conversion),
     * this will be set to "Failed".
     */
    @Builder.Default
    private String dataValidationStatus = CsvDataValidationStatus.SUCCESS.getDescription();
 }