package org.techbd.ingest.model;

import java.time.ZonedDateTime;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
/**
 * {@code RequestContext} holds metadata about the current request being processed.
 * <p>
 * It encapsulates information such as:
 * <ul>
 *   <li>HTTP headers and request URL</li>
 *   <li>Tenant ID and interaction ID</li>
 *   <li>Upload timestamp</li>
 *   <li>File-related details (e.g., name, type)</li>
 *   <li>S3 path/location</li>
 *   <li>Any additional contextual data needed during ingestion</li>
 * </ul>
 * </p>
 *
 * <p>
 * This context object is passed through each step of the ingestion pipeline
 * to maintain continuity and enable traceability, logging, and downstream processing.
 */
@Getter
@Setter
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
}

