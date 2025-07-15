package org.techbd.ingest.processor;

import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MetadataBuilderService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@Order(2)
public class SqsPublishStep implements MessageProcessingStep {
    private static final Logger LOG = LoggerFactory.getLogger(SqsPublishStep.class);

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final MetadataBuilderService metadataBuilderService;
    private final AppConfig appConfig;

    public SqsPublishStep(SqsClient sqsClient, ObjectMapper objectMapper, MetadataBuilderService metadataBuilderService,
            AppConfig appConfig) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.metadataBuilderService = metadataBuilderService;
        this.appConfig = appConfig;
        LOG.info("SqsPublishStep initialized");
    }

    @Override
    public void process(RequestContext context, MultipartFile file) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("SqsPublishStep:: process called with MultipartFile. interactionId={}, filename={}", interactionId,
                file != null ? file.getOriginalFilename() : "null");
        try {
            Map<String, Object> message = metadataBuilderService.buildSqsMessage(context);
            String messageJson = objectMapper.writeValueAsString(message);
            LOG.info("SqsPublishStep:: Sending message to SQS. interactionId={}", interactionId);
            String messageId = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(appConfig.getAws().getSqs().getFifoQueueUrl())
                    .messageBody(messageJson)
                    .messageGroupId(context.getTenantId())
                    .build())
                    .messageId();
            context.setMessageId(messageId);
            LOG.info("SqsPublishStep:: Message sent to SQS successfully. interactionId={}, messageId={}", interactionId,
                    messageId);
        } catch (Exception e) {
            LOG.error("SqsPublishStep:: SQS Publish Step Failed. interactionId={}", interactionId, e);
            // throw new RuntimeException("SQS Publish Step Failed", e);
        }
    }

    public void process(RequestContext context, String content) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("SqsPublishStep:: process called with String content. interactionId={}", interactionId);
        try {
            Map<String, Object> message = metadataBuilderService.buildSqsMessage(context);
            message.put("content", content);
            String messageJson = objectMapper.writeValueAsString(message);
            LOG.info("SqsPublishStep:: Sending message to SQS. interactionId={}", interactionId);
            String messageId = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(Constants.FIFO_Q_URL)
                    .messageBody(messageJson)
                    .messageGroupId(context.getTenantId())
                    .build())
                    .messageId();
            context.setMessageId(messageId);
            LOG.info("SqsPublishStep:: Message sent to SQS successfully. interactionId={}, messageId={}", interactionId,
                    messageId);
        } catch (Exception e) {
            LOG.error("SqsPublishStep:: SQS Publish Step Failed. interactionId={}", interactionId, e);
            // throw new RuntimeException("SQS Publish Step Failed", e);
        }
    }
}