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
        metadata.put("interactionId", context.interactionId());
        metadata.put("tenantId", context.tenantId());
        metadata.put("fileName", context.fileName());
        metadata.put("FileSize", String.valueOf(context.fileSize()));
        metadata.put("s3ObjectPath", context.fullS3Path());
        metadata.put("UploadTime", context.uploadTime().toString());
        metadata.put("UploadedBy", context.userAgent());
        return metadata;
    }

    public Map<String, Object> buildMetadataJson(RequestContext context) {
        Map<String, Object> jsonMetadata = new HashMap<>();
        jsonMetadata.put("tenantId", context.tenantId());
        jsonMetadata.put("interactionId", context.interactionId());
        jsonMetadata.put("uploadDate", String.format("%d-%02d-%02d",
                context.uploadTime().getYear(), context.uploadTime().getMonthValue(), context.uploadTime().getDayOfMonth()));
        jsonMetadata.put("timestamp", context.timestamp());
        jsonMetadata.put("fileName", context.fileName());
        jsonMetadata.put("fileSize", String.valueOf(context.fileSize()));
        jsonMetadata.put("sourceSystem", "Mirth Connect");
        jsonMetadata.put("s3ObjectPath", context.fullS3Path());
        jsonMetadata.put("requestUrl", context.requestUrl());
        jsonMetadata.put("fullRequestUrl", context.fullRequestUrl());
        jsonMetadata.put("queryParams", context.queryParams());
        jsonMetadata.put("protocol", context.protocol());
        jsonMetadata.put("localAddress", context.localAddress());
        jsonMetadata.put("remoteAddress", context.remoteAddress());

        List<Map<String, String>> headerList = context.headers().entrySet().stream()
                .map(entry -> Map.of(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        jsonMetadata.put("headers", headerList);

        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("key", context.objectKey());
        wrapper.put("json_metadata", jsonMetadata);

        return wrapper;
    }

    public Map<String, Object> buildSqsMessage(RequestContext context) {
        Map<String, Object> message = new HashMap<>();
        message.put("tenantId", context.tenantId());
        message.put("interactionId", context.interactionId());
        message.put("requestUrl", context.requestUrl());
        message.put("timestamp", context.timestamp());
        message.put("fileName", context.fileName());
        message.put("fileSize", context.fileSize());
        message.put("s3ObjectId", context.objectKey());
        message.put("s3ObjectPath", context.fullS3Path());

        if (context.getS3Response() != null) {
            message.put("s3Response", context.getS3Response());
        }

        return message;
    }
}
