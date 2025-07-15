package org.techbd.ingest.processor;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MetadataBuilderService;
import org.techbd.ingest.service.S3UploadService;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Order(1)
public class S3UploadStep implements MessageProcessingStep {
    private static final Logger LOG = LoggerFactory.getLogger(S3UploadStep.class);

    private final S3UploadService s3UploadService;
    private final MetadataBuilderService metadataBuilderService;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;

    public S3UploadStep(S3UploadService s3UploadService,
                        MetadataBuilderService metadataBuilderService,
                        ObjectMapper objectMapper,
                        AppConfig appConfig) {
        this.s3UploadService = s3UploadService;
        this.metadataBuilderService = metadataBuilderService;
        this.objectMapper = objectMapper;
        this.appConfig = appConfig;
        LOG.info("S3UploadStep initialized");
    }

    @Override
    public void process(RequestContext context, MultipartFile file) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("S3UploadStep:: process called with MultipartFile. interactionId={}, filename={}", interactionId, file != null ? file.getOriginalFilename() : "null");
        try {
            Map<String, String> metadata = metadataBuilderService.buildS3Metadata(context);
            Map<String, Object> metadataJson = metadataBuilderService.buildMetadataJson(context);
            String metadataContent = objectMapper.writeValueAsString(metadataJson);
            LOG.info("S3UploadStep:: Uploading metadata to S3. interactionId={}", interactionId);
            s3UploadService.uploadStringContent(appConfig.getAws().getS3().getBucket(), context.getMetadataKey(), metadataContent, null);
            LOG.info("S3UploadStep:: Uploading file to S3. interactionId={}", interactionId);
            String s3Response = s3UploadService.uploadFile(context.getObjectKey(), appConfig.getAws().getS3().getBucket(), file, metadata);
            context.setS3Response(s3Response);
            LOG.info("S3UploadStep:: File and metadata uploaded successfully. interactionId={}", interactionId);
        } catch (Exception e) {
            LOG.error("S3UploadStep:: S3 Upload Step Failed. interactionId={}", interactionId, e);
            //throw new RuntimeException("S3 Upload Step Failed", e);
        }
    }

    public void process(RequestContext context, String content) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("S3UploadStep:: process called with String content. interactionId={}", interactionId);
        try {
            Map<String, String> metadata = metadataBuilderService.buildS3Metadata(context);
            Map<String, Object> metadataJson = metadataBuilderService.buildMetadataJson(context);
            String metadataContent = objectMapper.writeValueAsString(metadataJson);
            LOG.info("S3UploadStep:: Uploading metadata to S3. interactionId={}", interactionId);
            s3UploadService.uploadStringContent(Constants.BUCKET_NAME, context.getMetadataKey(), metadataContent, null);
            LOG.info("S3UploadStep:: Uploading content to S3. interactionId={}", interactionId);
            s3UploadService.uploadStringContent(context.getObjectKey(), Constants.BUCKET_NAME, content, metadata);
            LOG.info("S3UploadStep:: Content and metadata uploaded successfully. interactionId={}", interactionId);
        } catch (Exception e) {
            LOG.error("S3UploadStep:: S3 Upload Step Failed. interactionId={}", interactionId, e);
            //throw new RuntimeException("S3 Upload Step Failed", e);
        }
    }
}