package org.techbd.ingest.commons;

public enum MessageSourceType {

    // HTTP endpoints
    HTTP_INGEST(true, true),   // S3 + SQS
    HTTP_HOLD(true, true),    // S3 + SQS

    // HL7 via MLLP
    MLLP(true, true),          // S3 + SQS

    // SOAP endpoints
    SOAP_PIX(true, true),      // S3 + SQS
    SOAP_PNR(true, true);      // S3 + SQS

    private final boolean s3Upload;
    private final boolean sqsUpload;

    MessageSourceType(boolean s3Upload, boolean sqsUpload) {
        this.s3Upload = s3Upload;
        this.sqsUpload = sqsUpload;
    }

public boolean shouldUploadToS3() {
        return s3Upload;
    }

    public boolean shouldUploadToSqs() {
        return sqsUpload;
    }
}
