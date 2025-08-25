package org.techbd.ingest.model;

public enum SourceType {
    REST, // for ingest, MLLP etc
    SOAP, // for SOAP endpoints like Pix/Pnr
    HOLD // REST with S3 Save only.
}
