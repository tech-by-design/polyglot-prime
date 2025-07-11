package org.techbd.ingest.model;

import java.time.ZonedDateTime;
import java.util.Map;

public class RequestContext {
    private final Map<String, String> headers;
    private final String requestUrl;
    private final String tenantId;
    private final String interactionId;
    private final ZonedDateTime uploadTime;
    private final String timestamp;
    private final String fileName;
    private final long fileSize;
    private final String objectKey;
    private final String metadataKey;
    private final String fullS3Path;
    private final String userAgent;
    private final String fullRequestUrl;
    private final String queryParams;
    private final String protocol;
    private final String localAddress;
    private final String remoteAddress;

    private String s3Response;
    private String messageId;

    public RequestContext(Map<String, String> headers, String requestUrl, String tenantId, String interactionId,
                          ZonedDateTime uploadTime, String timestamp, String fileName, long fileSize,
                          String objectKey, String metadataKey, String fullS3Path, String userAgent,
                          String fullRequestUrl, String queryParams, String protocol,
                          String localAddress, String remoteAddress) {
        this.headers = headers;
        this.requestUrl = requestUrl;
        this.tenantId = tenantId;
        this.interactionId = interactionId;
        this.uploadTime = uploadTime;
        this.timestamp = timestamp;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.objectKey = objectKey;
        this.metadataKey = metadataKey;
        this.fullS3Path = fullS3Path;
        this.userAgent = userAgent;
        this.fullRequestUrl = fullRequestUrl;
        this.queryParams = queryParams;
        this.protocol = protocol;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
    }

    public Map<String, String> headers() { return headers; }
    public String requestUrl() { return requestUrl; }
    public String tenantId() { return tenantId; }
    public String interactionId() { return interactionId; }
    public ZonedDateTime uploadTime() { return uploadTime; }
    public String timestamp() { return timestamp; }
    public String fileName() { return fileName; }
    public long fileSize() { return fileSize; }
    public String objectKey() { return objectKey; }
    public String metadataKey() { return metadataKey; }
    public String fullS3Path() { return fullS3Path; }
    public String userAgent() { return userAgent; }
    public String fullRequestUrl() { return fullRequestUrl; }
    public String queryParams() { return queryParams; }
    public String protocol() { return protocol; }
    public String localAddress() { return localAddress; }
    public String remoteAddress() { return remoteAddress; }

    public String getS3Response() { return s3Response; }
    public void setS3Response(String s3Response) { this.s3Response = s3Response; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
}

