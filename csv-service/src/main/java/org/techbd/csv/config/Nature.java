package org.techbd.csv.config;

public enum Nature {

    // General
    ORIGINAL_REQUEST_RECEIVED("Original Request Received"),
    S3_UPLOAD("S3 Upload"),
    SQS_PUSH("SQS Push"),
    MESSAGE_PULLED_BY_CHANNEL("Message Pulled by Channel"),

    // FHIR
    ORIGINAL_FHIR_PAYLOAD("Original FHIR Payload"),
    TECH_BY_DISPOSITION("techByDesignDisposition"),
    FORWARD_HTTP_REQUEST("Forward HTTP Request"),
    FORWARDED_HTTP_RESPONSE("Forwarded HTTP Response"),
    FORWARDED_HTTP_RESPONSE_ERROR("Forwarded HTTP Response Error"),

    // CSV
    ORIGINAL_CSV_ZIP_ARCHIVE("Original CSV Zip Archive"),
    ORIGINAL_FLAT_FILE_CSV("Original Flat File CSV"),
    CSV_VALIDATION_RESULT("CSV Validation Result"),
    CSV_TO_FHIR_CONVERSION("CSV to FHIR Conversion"),
    UPDATE_ZIP_FILE_PROCESSING_DETAILS("Update Zip File Processing Details"),

    CONVERTED_TO_FHIR("Converted to FHIR"),
    // CCD
    ORIGINAL_CCDA_PAYLOAD("Original CCDA Payload"),
    CCDA_VALIDATION_RESULT("CCDA Validation Result"),

     // HL7
     ORIGINAL_HL7_PAYLOAD("Original HL7 Payload"),
     HL7_VALIDATION_RESULT("HL7 Validation Result");

    private final String description;

    Nature(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
