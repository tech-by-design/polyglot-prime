package org.techbd.ingest.model;

import java.time.ZonedDateTime;
import java.util.Map;

import org.techbd.ingest.commons.MessageSourceType;

import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class RequestContext {
    private  Map<String, String> headers;
    private  String requestUrl;
    private  String tenantId;
    private  String interactionId;
    private  ZonedDateTime uploadTime;
    private  String timestamp;
    private  String fileName;
    private  long fileSize;
    private String objectKey;
    private String metadataKey;
    private String ackObjectKey;
    private  String fullS3DataPath;
    private  String fullS3AckMessagePath;
    private  String fullS3MetadataPath;
    private  String userAgent;
    private  String fullRequestUrl;
    private  String queryParams;
    private  String protocol;
    private  String localAddress;
    private  String remoteAddress;
    private  String sourceIp;
    private  String destinationIp;
    private  String destinationPort;
    private  MessageSourceType messageSourceType;
    private String dataBucketName;
    private String metaDataBucketName;
    private String messageGroupId;
    private String s3Response;
    private String messageId;
    private String techBdIngestionApiVersion;
    private Map<String,String> additionalParameters;
    private String queueUrl;
    private String sourceId;
    private String msgType;
    private boolean ingestionFailed;

    public RequestContext(Map<String, String> headers, String requestUrl, String tenantId, String interactionId,
                          ZonedDateTime uploadTime, String timestamp, String fileName, long fileSize,
                          String objectKey, String metadataKey, String fullS3DataPath, String userAgent,
                          String fullRequestUrl, String queryParams, String protocol,
                          String localAddress, String remoteAddress, String sourceIp, String destinationIp,
                          String destinationPort, String ackObjectKey,String fullS3AckMessagePath, 
                          String fullS3MetadataPath, MessageSourceType messageSourceType,String dataBucketName,String metadataBucketName,String techBdIngestionApiVersion) {
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
        this.ackObjectKey = ackObjectKey;
        this.fullS3DataPath = fullS3DataPath;
        this.fullS3AckMessagePath = fullS3AckMessagePath;
        this.userAgent = userAgent;
        this.fullRequestUrl = fullRequestUrl;
        this.queryParams = queryParams;
        this.protocol = protocol;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        this.sourceIp = sourceIp;
        this.destinationIp = destinationIp;
        this.destinationPort = destinationPort;
        this.fullS3MetadataPath = fullS3MetadataPath;
        this.messageSourceType = messageSourceType;
        this.dataBucketName = dataBucketName;
        this.metaDataBucketName = metadataBucketName;
        this.techBdIngestionApiVersion = techBdIngestionApiVersion;
    }

    public RequestContext(String interactionId, int requestPort, String sourceId2, String msgType2) {
        this.interactionId = interactionId;
        this.destinationPort = String.valueOf(requestPort);
        this.sourceId = sourceId2;
        this.msgType = msgType2;
    }
}

