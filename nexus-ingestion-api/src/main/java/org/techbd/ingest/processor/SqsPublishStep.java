package org.techbd.ingest.processor;

import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MetadataBuilderService;
import org.techbd.ingest.service.messagegroup.MessageGroupService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * {@code [SQS_PUBLISH_STEP]} is a {@link MessageProcessingStep} implementation
 * responsible for publishing messages to an Amazon SQS queue.
 * <p>
 * This step is typically used to send messages containing metadata and content
 * to SQS for downstream processing by other services or systems.
 * </p>
 *
 * <p>
 * It supports both {@link MultipartFile} and raw {@link String} content. The
 * message body is typically constructed using details from the
 * {@link RequestContext}, including source, identifiers, and correlation
 * metadata.
 * </p>
 */
@Component
@Order(2)
public class SqsPublishStep implements MessageProcessingStep {

    private TemplateLogger LOG;

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final MetadataBuilderService metadataBuilderService;
    private final MessageGroupService messageGroupService;

    public SqsPublishStep(SqsClient sqsClient, ObjectMapper objectMapper, MetadataBuilderService metadataBuilderService,
            MessageGroupService messageGroupService, AppLogger appLogger) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.metadataBuilderService = metadataBuilderService;
        this.messageGroupService = messageGroupService;
        this.LOG = appLogger.getLogger(SqsPublishStep.class);
        LOG.info("[SQS_PUBLISH_STEP] initialized");
    }

    @Override
    public void process(RequestContext context, MultipartFile file) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.debug("[SQS_PUBLISH_STEP]:: process called with MultipartFile. interactionId={}, filename={}", interactionId,
                file != null ? file.getOriginalFilename() : "null");

        if (context == null || context.getQueueUrl() == null || context.getQueueUrl().isBlank()) {
            throw new IllegalArgumentException(
                    "[SQS_PUBLISH_STEP]:: Queue URL is null or empty for interactionId=" + interactionId);
        }

        try {
            final var messageGroupId = messageGroupService.createMessageGroupId(context, interactionId);
            Map<String, Object> message = metadataBuilderService.buildSqsMessage(context);
            String messageJson = objectMapper.writeValueAsString(message);
            LOG.info("[SQS_PUBLISH_STEP]:: SENDING_MESSAGE to SQS. interactionId={}, queueUrl={}", interactionId,
                    context.getQueueUrl());

            String messageId = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(context.getQueueUrl())
                    .messageBody(messageJson)
                    .messageGroupId(messageGroupId)
                    .build())
                    .messageId();
            context.setMessageId(messageId);
            LOG.info("[SQS_PUBLISH_STEP]:: MESSAGE_SENT to SQS successfully. interactionId={}, messageId={}", interactionId,
                    messageId);
        } catch (Exception e) {
            LOG.error("[SQS_PUBLISH_STEP]:: FAILED. interactionId={}", interactionId, e);
            throw new RuntimeException(
                    "SQS Publish Step Failed for interactionId " + interactionId + " with error: " + e.getMessage(), e);
        }
    }

    public void process(RequestContext context, String content, String ackMessage) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.debug("[SQS_PUBLISH_STEP]:: process called with String content. interactionId={}", interactionId);

        if (context == null || context.getQueueUrl() == null || context.getQueueUrl().isBlank()) {
            throw new IllegalArgumentException(
                    "[SQS_PUBLISH_STEP]:: Queue URL is null or empty for interactionId=" + interactionId);
        }

        try {
            final var messageGroupId = messageGroupService.createMessageGroupId(context, interactionId);
            Map<String, Object> message = metadataBuilderService.buildSqsMessage(context);
            String messageJson = objectMapper.writeValueAsString(message);
            LOG.info("[SQS_PUBLISH_STEP]:: SENDING_MESSAGE to SQS. interactionId={}, queueUrl={}", interactionId,
                    context.getQueueUrl());

            String messageId = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(context.getQueueUrl())
                    .messageBody(messageJson)
                    .messageGroupId(messageGroupId)
                    .build())
                    .messageId();
            context.setMessageId(messageId);
            LOG.info("[SQS_PUBLISH_STEP]:: MESSAGE_SENT to SQS successfully. interactionId={}, messageId={}", interactionId,
                    messageId);
        } catch (Exception e) {
            LOG.error("[SQS_PUBLISH_STEP]:: FAILED. interactionId={}", interactionId, e);
            throw new RuntimeException(
                    "SQS Publish Step Failed for interactionId " + interactionId + " with error: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isEnabledFor(RequestContext context) {
        return context.getMessageSourceType() != null && context.getMessageSourceType().shouldUploadToSqs();
    }
}
