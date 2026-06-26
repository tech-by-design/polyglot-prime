package org.techbd.ingest.processor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MetadataBuilderService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import tools.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * {@code S3UploadStep is a {@link MessageProcessingStep} implementation
 * responsible for uploading files or raw content along with metadata to Amazon
 * S3.
 * <p>
 * This step is typically the first in the ingestion pipeline and ensures that
 * the original payload is safely stored before further processing steps are executed.
 * </p>
 *
 * <p>
 * It supports both {@link MultipartFile} (for uploaded files) and
 * {@link String} content
 * (e.g., HL7, FHIR JSON) uploads.
 * </p>
 */
@Component
@Order(1)
public class S3UploadStep implements MessageProcessingStep {

    private final MetadataBuilderService metadataBuilderService;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;
    private final TemplateLogger LOG;
    private final S3Client s3Client;

    /**
     * Constructs an {@code S3UploadStep} with required dependencies.
     *
     * @param s3UploadService Service for uploading files and strings to S3.
     * @param metadataBuilderService Service for building metadata for S3
     * objects.
     * @param objectMapper Jackson ObjectMapper for converting Java objects to
     * JSON.
     * @param appConfig AppConfig for reading application configuration.
     * @param s3Client AWS S3 client for performing S3 operations.
     */
    public S3UploadStep(
            MetadataBuilderService metadataBuilderService,
            ObjectMapper objectMapper,
            AppConfig appConfig,
            S3Client s3Client,AppLogger appLogger) {
        this.metadataBuilderService = metadataBuilderService;
        this.objectMapper = objectMapper;
        this.appConfig = appConfig;
        this.s3Client = s3Client;
        this.LOG = appLogger.getLogger(S3UploadStep.class);
        LOG.info("[S3_UPLOAD_STEP] initialized");
    }

    /**
     * Uploads the provided file and its metadata to S3.
     *
     * @param context The request context containing metadata for the operation.
     * @param file The file to upload to S3.
     */
    @Override
    public void process(RequestContext context, MultipartFile file) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("[S3_UPLOAD_STEP]:: process called with MultipartFile. interactionId={}, filename={}",
                interactionId,
                file != null ? file.getOriginalFilename() : "null");
        try {
            Map<String, String> metadata = metadataBuilderService.buildS3Metadata(context);
            Map<String, Object> metadataJson = metadataBuilderService.buildMetadataJson(context);
            String metadataContent = objectMapper.writeValueAsString(metadataJson);
            String bucketName = context.getDataBucketName();
            String metaDataBucketName = context.getMetaDataBucketName();
            String objectKey = context.getObjectKey();
            String metadataKey = context.getMetadataKey();
  
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Uploaded file is null or empty");
            }
            LOG.info("[S3_UPLOAD_STEP]:: Uploading metadata to S3 bucket {} using key {} for interactionId={}",
                    metaDataBucketName, metadataKey, interactionId);
            uploadStringContent(metaDataBucketName, metadataKey, metadataContent, null, interactionId);
            LOG.info("[S3_UPLOAD_STEP]:: Uploading file to S3 bucket {} using key {} for interactionId={}",
                    bucketName, objectKey, interactionId);
            String s3Response = uploadFile(objectKey, bucketName, file, metadata, interactionId);
            context.setS3Response(s3Response);
            LOG.info("[S3_UPLOAD_STEP]:: File and metadata uploaded successfully. interactionId={}", interactionId);
        } catch (Exception e) {
            LOG.error("[S3_UPLOAD_STEP]:: S3 Upload Step Failed while uploading to bucket {}. interactionId={}", context.getDataBucketName(), interactionId, e);
            throw new RuntimeException("[S3_UPLOAD_STEP]:: S3 Upload Step Failed while uploading to bucket " + context.getDataBucketName() + ". interactionId=" + interactionId + " with error: " + e.getMessage(), e);
        }
    }

    /**
     * Uploads the provided string content and its metadata to S3.
     *
     * @param context The request context containing metadata for the operation.
     * @param content The string content to upload to S3.
     */
    public void process(RequestContext context, String content, String ackMessage) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.debug("[S3_UPLOAD_STEP]:: BEGIN process called with String content. interactionId={}", interactionId);
        try {
            Map<String, String> metadata = metadataBuilderService.buildS3Metadata(context);
            Map<String, Object> metadataJson = metadataBuilderService.buildMetadataJson(context);
            String metadataContent = objectMapper.writeValueAsString(metadataJson);
            String bucketName = context.getDataBucketName();
            String metaDataBucketName = context.getMetaDataBucketName();
            String objectKey = context.getObjectKey();
            String metadataKey = context.getMetadataKey();
            String acknowledgementKey = context.getAckObjectKey();

            LOG.info("[S3_UPLOAD_STEP]:: UPLOADING_METADATA to S3 bucket {} using key {} for interactionId={}",
                    metaDataBucketName, metadataKey, interactionId);
            uploadStringContent(metaDataBucketName, metadataKey, metadataContent, null, interactionId);

            LOG.info("[S3_UPLOAD_STEP]:: UPLOADING_CONTENT to S3 bucket {} using key {} for interactionId={}",
                    bucketName, objectKey, interactionId);
            uploadStringContent(bucketName, objectKey, content, metadata, interactionId);

            if (ackMessage != null && !ackMessage.isEmpty()) {
                LOG.info("[S3_UPLOAD_STEP]:: UPLOADING_ACK_MESSAGE to S3 bucket {} using key {} for interactionId={}",
                        bucketName, acknowledgementKey, interactionId);
                uploadStringContent(bucketName, acknowledgementKey, ackMessage, metadata, interactionId);
            } else {
                LOG.info("[S3_UPLOAD_STEP]:: NO_ACK_MESSAGE available to upload for interactionId={}",
                        interactionId);
            }
        } catch (Exception e) {
            LOG.error("[S3_UPLOAD_STEP]:: FAILED while uploading to bucket {}. interactionId={}", context.getDataBucketName(), interactionId, e);
            throw new RuntimeException("S3 Upload Step Failed for interactionId=" + interactionId + " while uploading to bucket " + context.getDataBucketName() + " with error: " + e.getMessage(), e);
        }
    }

    private void uploadStringContent(
            String bucketName,
            String fileName,
            String content,
            Map<String, String> metadata,
            String interactionId) {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        try {
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType("application/json; charset=UTF-8")
                    .contentLength((long) contentBytes.length);

            if (metadata != null && !metadata.isEmpty()) {
                requestBuilder = requestBuilder.metadata(metadata);
            }

            PutObjectRequest request = requestBuilder.build();
            PutObjectResponse response = s3Client.putObject(request, RequestBody.fromBytes(contentBytes));

            LOG.debug("[S3 Upload] Interaction ID: {} | Endpoint: {} | Bucket: {} | Key: {} | Size: {} bytes | ETag: {}",
                    interactionId,
                    s3Client.serviceClientConfiguration().endpointOverride().orElse(null),
                    bucketName,
                    fileName,
                    contentBytes.length,
                    response.eTag());

        } catch (SdkException e) {
            LOG.error("[S3 Upload Failed] Interaction ID: {} | Bucket: {} | Key: {} | Error: {}",
                    interactionId,
                    bucketName,
                    fileName,
                    e.getMessage(), e);
            throw e;
        }
    }

    private String uploadFile(
            String key,
            String bucketName,
            MultipartFile file,
            Map<String, String> metadata,
            String interactionId) throws IOException {
        try {
            byte[] fileBytes = file.getBytes();
            long actualByteLength = fileBytes.length;
            long declaredSize = file.getSize();

            if (actualByteLength != declaredSize) {
                LOG.warn("[S3_UPLOAD_STEP]:: Size mismatch detected — declared={} actual={} "
                        + "fileName={} interactionId={}",
                        declaredSize, actualByteLength, file.getOriginalFilename(), interactionId);
            } else {
                LOG.debug("[S3_UPLOAD_STEP]:: Size check passed — declared={} actual={} "
                        + "fileName={} interactionId={}",
                        declaredSize, actualByteLength, file.getOriginalFilename(), interactionId);
            }

            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(actualByteLength);

            if (metadata != null && !metadata.isEmpty()) {
                // S3 user-defined metadata is transmitted as HTTP headers (x-amz-meta-*).
                // HTTP headers must be ASCII — non-ASCII values (e.g. accented characters
                // in filenames like César, Verónica) cause the AWS SDK to encode the header
                // value differently from what was signed, resulting in 403
                // SignatureDoesNotMatch
                Map<String, String> sanitizedMetadata = sanitizeMetadata(metadata);
                requestBuilder = requestBuilder.metadata(sanitizedMetadata);
            }

            PutObjectRequest request = requestBuilder.build();
            PutObjectResponse response = s3Client.putObject(
                    request,
                    RequestBody.fromBytes(fileBytes));

            LOG.debug(
                    "[S3 Upload] Interaction ID: {} | Endpoint: {} | Bucket: {} | Key: {} | Size: {} bytes | ETag: {}",
                    interactionId,
                    s3Client.serviceClientConfiguration().endpointOverride().orElse(null),
                    bucketName,
                    key,
                    actualByteLength,
                    response.eTag());

            return "Uploaded to S3: " + key + " (ETag: " + response.eTag() + ")";
        } catch (SdkException e) {
            LOG.error("[S3 Upload Failed] Interaction ID: {} | Bucket: {} | Key: {} | Error: {}",
                    interactionId, bucketName, key, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Sanitizes all metadata values to ASCII.
     * S3 metadata is sent as HTTP headers which must be ASCII-safe.
     * Non-ASCII characters (e.g. accented letters in filenames) are decomposed
     * via NFD normalization and remaining non-ASCII bytes replaced with
     * underscores.
     *
     * @param metadata the raw metadata map
     * @return a new map with all values sanitized to ASCII
     */
    private Map<String, String> sanitizeMetadata(Map<String, String> metadata) {
        Map<String, String> sanitized = new java.util.HashMap<>();
        metadata.forEach((k, v) -> sanitized.put(k, sanitizeAscii(v)));
        return sanitized;
    }

    private String sanitizeAscii(String value) {
        if (value == null)
            return "";
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("[^\\x00-\\x7F]", "_");
    }

    @Override
    public boolean isEnabledFor(RequestContext context) {
        return context.getMessageSourceType().shouldUploadToS3();
    }

}
