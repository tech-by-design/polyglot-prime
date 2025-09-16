package org.techbd.ingest.service;

import org.springframework.stereotype.Service;
import org.techbd.ingest.model.RequestContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code MetadataBuilderService} is a utility service responsible for building metadata maps
 * and structured messages used in S3 and SQS operations.
 * <p>
 * It extracts relevant fields from the {@link RequestContext}, such as tenant ID,
 * interaction ID, source type, file name, and timestamp, and formats them into
 * maps or message payloads suitable for:
 * </p>
 * <ul>
 *   <li>Adding metadata to S3 object uploads</li>
 *   <li>Constructing SQS message bodies or attributes</li>
 * </ul>
 *
 * <p>
 * This service helps enforce a consistent metadata structure across the ingestion pipeline.
 * </p>
 */
@Service
public class MetadataBuilderService {

    /**
     * Builds a metadata map for S3 object upload from the provided request context.
     *
     * @param context The request context containing metadata for the operation.
     * @return A map of S3 metadata key-value pairs.
     */
    public Map<String, String> buildS3Metadata(RequestContext context) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("interactionId", context.getInteractionId());
        metadata.put("tenantId", context.getTenantId());
        metadata.put("fileName", context.getFileName());
        metadata.put("FileSize", String.valueOf(context.getFileSize()));
        metadata.put("s3DataObjectPath", context.getFullS3DataPath());
        metadata.put("fullS3MetaDataPath", context.getFullS3MetadataPath());
        if (context.getFullS3AckMessagePath() != null) {
            metadata.put("fullS3AcknowledgementPath", context.getFullS3AckMessagePath()); 
        }
        metadata.put("UploadTime", context.getUploadTime().toString());
        metadata.put("UploadedBy", context.getUserAgent());
        return metadata;
    }

    /**
     * Builds a detailed JSON metadata map for S3 object upload from the provided request context.
     * Includes headers and additional request information.
     *
     * @param context The request context containing metadata for the operation.
     * @return A map containing the object key and a nested metadata map.
     */
    public Map<String, Object> buildMetadataJson(RequestContext context) {
        Map<String, Object> jsonMetadata = new HashMap<>();
        jsonMetadata.put("tenantId", context.getTenantId());
        jsonMetadata.put("interactionId", context.getInteractionId());
        jsonMetadata.put("uploadDate", String.format("%d-%02d-%02d",
                context.getUploadTime().getYear(), context.getUploadTime().getMonthValue(), context.getUploadTime().getDayOfMonth()));
        jsonMetadata.put("timestamp", context.getTimestamp());
        jsonMetadata.put("fileName", context.getFileName());
        jsonMetadata.put("fileSize", String.valueOf(context.getFileSize()));
        jsonMetadata.put("sourceSystem", context.getUserAgent());
        jsonMetadata.put("s3DataObjectPath", context.getFullS3DataPath());
        jsonMetadata.put("fullS3MetaDataPath", context.getFullS3MetadataPath());
        if (context.getFullS3AckMessagePath() != null) {
            jsonMetadata.put("fullS3AcknowledgementPath", context.getFullS3AckMessagePath()); 
        }       
        jsonMetadata.put("requestUrl", context.getRequestUrl());
        jsonMetadata.put("fullRequestUrl", context.getFullRequestUrl());
        jsonMetadata.put("queryParams", context.getQueryParams());
        jsonMetadata.put("protocol", context.getProtocol());
        jsonMetadata.put("localAddress", context.getLocalAddress());
        jsonMetadata.put("remoteAddress", context.getRemoteAddress());
        List<Map<String, String>> headerList = context.getHeaders().entrySet().stream()
                .map(entry -> Map.of(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        jsonMetadata.put("headers", headerList);
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("key", context.getObjectKey());
        wrapper.put("json_metadata", jsonMetadata);
        return wrapper;
    }

    /**
     * Builds a message map for SQS publishing from the provided request context.
     * Includes S3 object information and optional S3 response.
     *
     * @param context The request context containing metadata for the operation.
     * @return A map representing the SQS message payload.
     */
    public Map<String, Object> buildSqsMessage(RequestContext context) {
        Map<String, Object> message = new HashMap<>();
        message.put("interactionId", context.getInteractionId());
        message.put("requestUrl", context.getRequestUrl());
        message.put("timestamp", context.getTimestamp());
        message.put("fileName", context.getFileName());
        message.put("fileSize", context.getFileSize());
        message.put("s3ObjectId", context.getObjectKey());
        message.put("s3DataObjectPath", context.getFullS3DataPath());
        message.put("fullS3MetaDataPath", context.getFullS3MetadataPath());
        if (context.getFullS3AckMessagePath() != null) {
            message.put("fullS3AcknowledgementPath", context.getFullS3AckMessagePath());
        }
        message.put("messageGroupId", context.getMessageGroupId());
        if (context.getS3Response() != null) {
            message.put("s3Response", context.getS3Response());
        }
        return message;
    }
}
