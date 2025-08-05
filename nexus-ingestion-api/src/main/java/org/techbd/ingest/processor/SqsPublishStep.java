package org.techbd.ingest.processor;

import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageGroupService;
import org.techbd.ingest.service.MetadataBuilderService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
/**
 * {@code SqsPublishStep} is a {@link MessageProcessingStep} implementation responsible for
 * publishing messages to an Amazon SQS queue.
 * <p>
 * This step is typically used to send messages containing metadata and content to SQS
 * for downstream processing by other services or systems.
 * </p>
 *
 * <p>
 * It supports both {@link MultipartFile} and raw {@link String} content. The message body
 * is typically constructed using details from the {@link RequestContext}, including source,
 * identifiers, and correlation metadata.
 * </p>
 */
@Component
@Order(2)
public class SqsPublishStep implements MessageProcessingStep {
    private static final Logger LOG = LoggerFactory.getLogger(SqsPublishStep.class);

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final MetadataBuilderService metadataBuilderService;
    private final AppConfig appConfig;
    private final MessageGroupService messageGroupService;

    public SqsPublishStep(SqsClient sqsClient, ObjectMapper objectMapper, MetadataBuilderService metadataBuilderService,
            AppConfig appConfig, MessageGroupService messageGroupService) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.metadataBuilderService = metadataBuilderService;
        this.messageGroupService = messageGroupService;
        this.appConfig = appConfig;
        LOG.info("SqsPublishStep initialized");
    }

    @Override
    public void process(RequestContext context, MultipartFile file) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("SqsPublishStep:: process called with MultipartFile. interactionId={}, filename={}", interactionId,
                file != null ? file.getOriginalFilename() : "null");
        try {
            final var messageGroupId = messageGroupService.createMessageGroupId(context,interactionId);
            Map<String, Object> message = metadataBuilderService.buildSqsMessage(context);
            String messageJson = objectMapper.writeValueAsString(message);
            String queueUrl = appConfig.getAws().getSqs().getFifoQueueUrl() != null
                    ? appConfig.getAws().getSqs().getFifoQueueUrl()
                    : Constants.FIFO_Q_URL;
            LOG.info("SqsPublishStep:: Sending message to SQS. interactionId={}, queueUrl={}", interactionId, queueUrl);

            String messageId = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageJson)
                    .messageGroupId(messageGroupId)
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
            final var messageGroupId = messageGroupService.createMessageGroupId(context,interactionId);
            Map<String, Object> message = metadataBuilderService.buildSqsMessage(context);
            message.put("content", content);
            String messageJson = objectMapper.writeValueAsString(message);
            String queueUrl = appConfig.getAws().getSqs().getFifoQueueUrl() != null
                    ? appConfig.getAws().getSqs().getFifoQueueUrl()
                    : Constants.FIFO_Q_URL;
            LOG.info("SqsPublishStep:: Sending message to SQS. interactionId={}, queueUrl={}", interactionId, queueUrl);

            String messageId = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageJson)
                    .messageGroupId(messageGroupId)
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