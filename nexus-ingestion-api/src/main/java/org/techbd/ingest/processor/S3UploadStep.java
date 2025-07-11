package org.techbd.ingest.processor;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MetadataBuilderService;
import org.techbd.ingest.service.S3UploadService;

import java.util.Map;

@Component
@Order(1)
public class S3UploadStep implements MessageProcessingStep {
    private final S3UploadService s3UploadService;
    private final MetadataBuilderService metadataBuilderService;
    private final ObjectMapper objectMapper;

    public S3UploadStep(S3UploadService s3UploadService,
                        MetadataBuilderService metadataBuilderService,
                        ObjectMapper objectMapper) {
        this.s3UploadService = s3UploadService;
        this.metadataBuilderService = metadataBuilderService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(RequestContext context, MultipartFile file) {
        try {
            Map<String, String> metadata = metadataBuilderService.buildS3Metadata(context);
            Map<String, Object> metadataJson = metadataBuilderService.buildMetadataJson(context);
            String metadataContent = objectMapper.writeValueAsString(metadataJson);
            s3UploadService.uploadStringContent(Constants.BUCKET_NAME, context.metadataKey(), metadataContent, null);
            String s3Response = s3UploadService.uploadFile(context.objectKey(), Constants.BUCKET_NAME, file, metadata);
            context.setS3Response(s3Response);
        } catch (Exception e) {
            throw new RuntimeException("S3 Upload Step Failed", e);
        }
    }

    public void process(RequestContext context, String content) {
        try {
            Map<String, String> metadata = metadataBuilderService.buildS3Metadata(context);
            Map<String, Object> metadataJson = metadataBuilderService.buildMetadataJson(context);
            String metadataContent = objectMapper.writeValueAsString(metadataJson);
            s3UploadService.uploadStringContent(Constants.BUCKET_NAME, context.metadataKey(), metadataContent, null);
            s3UploadService.uploadStringContent(context.objectKey(), Constants.BUCKET_NAME, content, metadata);
        } catch (Exception e) {
            throw new RuntimeException("S3 Upload Step Failed", e);
        }
    }
}