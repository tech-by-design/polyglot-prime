package org.techbd.ingest;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;

public interface MessageSourceProvider {
    /**
     * Identifies the source type of this endpoint/controller.
     *
     * @return the message source type (e.g., HTTP_INGEST, MLLP, SOAP_PIX, etc.)
     */
    MessageSourceType getMessageSource();

    String getDataBucketName();

    String getMetadataBucketName();
    
    String getTenantId(Map<String, String> headers);

    String getSourceIp(Map<String, String> headers);

    String getDestinationIp(Map<String, String> headers);

    String getDestinationPort(Map<String, String> headers);

    default String getDataKey(String interactionId, Map<String,String> headers,String originalFileName,String timestamp) {
        Instant now = Instant.now();
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);
        String datePath = uploadTime.format(Constants.DATE_PATH_FORMATTER);
        return String.format("data/%s/%s_%s", datePath, interactionId, timestamp);
    }

    default String getMetaDataKey(String interactionId, Map<String,String> headers,String originalFileName,String timestamp) {
        Instant now = Instant.now();
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);
        String datePath = uploadTime.format(Constants.DATE_PATH_FORMATTER);
        return String.format("metadata/%s/%s_%s_metadata.json", datePath, interactionId, timestamp);
    }

    default String getAcknowledgementKey(String interactionId, Map<String,String> headers,String originalFileName,String timestamp) {
        Instant now = Instant.now();
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);
        String datePath = uploadTime.format(Constants.DATE_PATH_FORMATTER);
        return String.format("data/%s/%s_%s_ack", datePath, interactionId, timestamp);
    }
      default String getFullS3DataPath(String interactionId, Map<String, String> headers, String originalFileName,String timestamp) {
        return Constants.S3_PREFIX + getDataBucketName() + "/" + getDataKey(interactionId, headers, originalFileName,timestamp);
    }

    default String getFullS3MetadataPath(String interactionId, Map<String, String> headers, String originalFileName,String timestamp) {
        return Constants.S3_PREFIX + getMetadataBucketName() + "/" + getMetaDataKey(interactionId, headers, originalFileName,timestamp);
    }

    default String getFullS3AcknowledgementPath(String interactionId, Map<String, String> headers, String originalFileName,String timestamp) {
        return Constants.S3_PREFIX + getDataBucketName() + "/" + getAcknowledgementKey(interactionId, headers, originalFileName,timestamp);
    }
}
