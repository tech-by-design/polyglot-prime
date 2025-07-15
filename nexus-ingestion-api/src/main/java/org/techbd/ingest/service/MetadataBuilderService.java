package org.techbd.ingest.service;


import org.springframework.stereotype.Service;
import org.techbd.ingest.model.RequestContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MetadataBuilderService {
public Map<String, String> buildS3Metadata(RequestContext context) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("interactionId", context.getInteractionId());
        metadata.put("tenantId", context.getTenantId());
        metadata.put("fileName", context.getFileName());
        metadata.put("FileSize", String.valueOf(context.getFileSize()));
        metadata.put("s3ObjectPath", context.getFullS3Path());
        metadata.put("UploadTime", context.getUploadTime().toString());
        metadata.put("UploadedBy", context.getUserAgent());
        return metadata;
    }

    public Map<String, Object> buildMetadataJson(RequestContext context) {
        Map<String, Object> jsonMetadata = new HashMap<>();
        jsonMetadata.put("tenantId", context.getTenantId());
        jsonMetadata.put("interactionId", context.getInteractionId());
        jsonMetadata.put("uploadDate", String.format("%d-%02d-%02d",
                context.getUploadTime().getYear(), context.getUploadTime().getMonthValue(), context.getUploadTime().getDayOfMonth()));
        jsonMetadata.put("timestamp", context.getTimestamp());
        jsonMetadata.put("fileName", context.getFileName());
        jsonMetadata.put("fileSize", String.valueOf(context.getFileSize()));
        jsonMetadata.put("sourceSystem", "Mirth Connect");
        jsonMetadata.put("s3ObjectPath", context.getFullS3Path());
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

    public Map<String, Object> buildSqsMessage(RequestContext context) {
        Map<String, Object> message = new HashMap<>();
        message.put("tenantId", context.getTenantId());
        message.put("interactionId", context.getInteractionId());
        message.put("requestUrl", context.getRequestUrl());
        message.put("timestamp", context.getTimestamp());
        message.put("fileName", context.getFileName());
        message.put("fileSize", context.getFileSize());
        message.put("s3ObjectId", context.getObjectKey());
        message.put("s3ObjectPath", context.getFullS3Path());

        if (context.getS3Response() != null) {
            message.put("s3Response", context.getS3Response());
        }

        return message;
    }
}
