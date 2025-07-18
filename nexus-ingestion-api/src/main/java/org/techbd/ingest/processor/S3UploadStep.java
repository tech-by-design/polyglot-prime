package org.techbd.ingest.processor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MetadataBuilderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * {@code S3UploadStep} is a {@link MessageProcessingStep} implementation
 * responsible for
 * uploading files or raw content along with metadata to Amazon S3.
 * <p>
 * This step is typically the first in the ingestion pipeline and ensures that
 * the original
 * payload is safely stored before further processing steps are executed.
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
    private static final Logger LOG = LoggerFactory.getLogger(S3UploadStep.class);
    private final MetadataBuilderService metadataBuilderService;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;
    private final S3Client s3Client;

    /**
     * Constructs an S3UploadStep with required dependencies.
     *
     * @param s3UploadService        Service for uploading files and strings to S3.
     * @param metadataBuilderService Service for building metadata for S3 objects.
     * @param objectMapper           Jackson ObjectMapper for JSON serialization.
     * @param appConfig              Application configuration containing AWS
     *                               settings.
     */
    public S3UploadStep(
            MetadataBuilderService metadataBuilderService,
            ObjectMapper objectMapper,
            AppConfig appConfig, S3Client s3Client) {
        this.metadataBuilderService = metadataBuilderService;
        this.objectMapper = objectMapper;
        this.appConfig = appConfig;
        this.s3Client = s3Client;
        LOG.info("S3UploadStep initialized");
    }

    /**
     * Uploads the provided file and its metadata to S3.
     *
     * @param context The request context containing metadata for the operation.
     * @param file    The file to upload to S3.
     */
    @Override
    public void process(RequestContext context, MultipartFile file) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("S3UploadStep:: process called with MultipartFile. interactionId={}, filename={}", interactionId,
                file != null ? file.getOriginalFilename() : "null");
        try {
            Map<String, String> metadata = metadataBuilderService.buildS3Metadata(context);
            Map<String, Object> metadataJson = metadataBuilderService.buildMetadataJson(context);
            String metadataContent = objectMapper.writeValueAsString(metadataJson);
            LOG.info("S3UploadStep:: Uploading metadata to S3 bucket {} using  key {} for interactionId={}",
                    appConfig.getAws().getS3().getBucket(), context.getMetadataKey(), interactionId);
            uploadStringContent(appConfig.getAws().getS3().getBucket(), context.getMetadataKey(), metadataContent,
                    null, interactionId);
            LOG.info("S3UploadStep:: Uploading file to S3 bucket {} using key {} for interactionId={}",
                    appConfig.getAws().getS3().getBucket(), context.getObjectKey(), interactionId);
            String s3Response = uploadFile(context.getObjectKey(), appConfig.getAws().getS3().getBucket(), file,
                    metadata, interactionId);
            context.setS3Response(s3Response);
            LOG.info("S3UploadStep:: File and metadata uploaded successfully. interactionId={}", interactionId);
        } catch (Exception e) {
            LOG.error("S3UploadStep:: S3 Upload Step Failed. interactionId={}", interactionId, e);
            // throw new RuntimeException("S3 Upload Step Failed", e);
        }
    }

    /**
     * Uploads the provided string content and its metadata to S3.
     *
     * @param context The request context containing metadata for the operation.
     * @param content The string content to upload to S3.
     */
    public void process(RequestContext context, String content) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("S3UploadStep:: process called with String content. interactionId={}", interactionId);
        try {
            Map<String, String> metadata = metadataBuilderService.buildS3Metadata(context);
            Map<String, Object> metadataJson = metadataBuilderService.buildMetadataJson(context);
            String metadataContent = objectMapper.writeValueAsString(metadataJson);
            LOG.info("S3UploadStep:: Uploading metadata to S3 bucket {} using key {} for interactionId={}",
                    Constants.BUCKET_NAME, context.getMetadataKey(), interactionId);
            uploadStringContent(Constants.BUCKET_NAME, context.getMetadataKey(), metadataContent, null, interactionId);
            LOG.info("S3UploadStep:: Uploading content to S3 bucket {} using key {} for interactionId={}",
                    Constants.BUCKET_NAME, context.getObjectKey(), interactionId);
            uploadStringContent(Constants.BUCKET_NAME, context.getObjectKey(), content, metadata, interactionId);
            LOG.info("S3UploadStep:: Content and metadata uploaded successfully. interactionId={}", interactionId);
        } catch (Exception e) {
            LOG.error("S3UploadStep:: S3 Upload Step Failed. interactionId={}", interactionId, e);
            // throw new RuntimeException("S3 Upload Step Failed", e);
        }
    }

    private String uploadFile(
            String key,
            String bucketName,
            MultipartFile file,
            Map<String, String> metadata,
            String interactionId) throws IOException {

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .metadata(metadata)
                .build();

        try {
            PutObjectResponse response = s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
            LOG.info("[S3 Upload] Interaction ID: {} | Endpoint: {} | Bucket: {} | Key: {} | Size: {} bytes | ETag: {}",
                    interactionId,
                    s3Client.serviceClientConfiguration().endpointOverride().orElse(null),
                    bucketName,
                    key,
                    file.getSize(),
                    response.eTag());

            return "Uploaded to S3: " + key + " (ETag: " + response.eTag() + ")";
        } catch (SdkException e) {
            LOG.error("[S3 Upload Failed] Interaction ID: {} | Bucket: {} | Key: {} | Error: {}",
                    interactionId,
                    bucketName,
                    key,
                    e.getMessage(), e);
            throw e;
        }
    }

    private void uploadStringContent(
            String bucketName,
            String fileName,
            String content,
            Map<String, String> metadata,
            String interactionId) {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .metadata(metadata)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromBytes(contentBytes));
            LOG.info("[S3 Upload] Interaction ID: {} | Endpoint: {} | Bucket: {} | Key: {} | Size: {} bytes ",
                    interactionId,
                    s3Client.serviceClientConfiguration().endpointOverride().orElse(null),
                    bucketName,
                    fileName,
                    contentBytes.length);

        } catch (SdkException e) {
            LOG.error("[S3 Upload Failed] Interaction ID: {} | Bucket: {} | Key: {} | Error: {}",
                    interactionId,
                    bucketName,
                    fileName,
                    e.getMessage(), e);
            throw e;
        }
    }

}