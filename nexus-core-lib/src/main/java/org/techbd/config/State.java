package org.techbd.config;

public enum State {
    // General States
    NONE,

    //INGESTION States
    INGESTION_SUCCESS,
    INGESTION_FAILED,
    S3_UPLOAD_SUCCESS,
    S3UPLOAD_FAILED,
    SQS_PUSH_SUCCESS,
    SQS_PUSH_FAILED,
    MESSAGE_RECEIVED_BY_CHANNEL,

    // FHIR-specific States
    ACCEPT_FHIR_BUNDLE,
    DISPOSITION,
    FORWARD,
    FAIL,
    COMPLETE,

    CONVERTED_TO_FHIR,
    FHIR_CONVERSION_FAILED,
    VALIDATION_SUCCESS,
    VALIDATION_FAILED,
    
    // CSV-specific States
    CSV_ACCEPT,
    CSV_CONVERSION_FAILED,

    // CCD-specific States
    CCDA_ACCEPT,

    // HL7-specific States
    HL7_ACCEPT,

}
