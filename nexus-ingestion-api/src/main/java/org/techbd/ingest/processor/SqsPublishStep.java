package org.techbd.ingest.processor;

import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageGroupService;
import org.techbd.ingest.service.MetadataBuilderService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * {@code SqsPublishStep} is a {@link MessageProcessingStep} implementation
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
    private final AppConfig appConfig;
    private final MessageGroupService messageGroupService;
    private final PortConfig portConfig;

    public SqsPublishStep(SqsClient sqsClient, ObjectMapper objectMapper, MetadataBuilderService metadataBuilderService,
            AppConfig appConfig, MessageGroupService messageGroupService, AppLogger appLogger, PortConfig portConfig) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.metadataBuilderService = metadataBuilderService;
        this.messageGroupService = messageGroupService;
        this.appConfig = appConfig;
        this.portConfig = portConfig;
        this.LOG = appLogger.getLogger(SqsPublishStep.class);
        LOG.info("SqsPublishStep initialized");
    }

    @Override
    public void process(RequestContext context, MultipartFile file) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("SqsPublishStep:: process called with MultipartFile. interactionId={}, filename={}", interactionId,
                file != null ? file.getOriginalFilename() : "null");
        try {
            final var messageGroupId = messageGroupService.createMessageGroupId(context, interactionId);
            Map<String, Object> message = metadataBuilderService.buildSqsMessage(context);
            String messageJson = objectMapper.writeValueAsString(message);
            String queueUrl = resolveQueueUrl(context);
            LOG.info("SqsPublishStep:: Sending message to SQS. interactionId={}, queueUrl={}", interactionId, queueUrl);

            String messageId = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageJson)
                    .messageGroupId(messageGroupId)
                    .build())
                    .messageId();
            if (context != null) {
                context.setMessageId(messageId);
            }
            LOG.info("SqsPublishStep:: Message sent to SQS successfully. interactionId={}, messageId={}", interactionId,
                    messageId);
        } catch (Exception e) {
            LOG.error("SqsPublishStep:: SQS Publish Step Failed. interactionId={}", interactionId, e);
            // throw new RuntimeException("SQS Publish Step Failed", e);
        }
    }

    public void process(RequestContext context, String content, String ackMessage) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("SqsPublishStep:: process called with String content. interactionId={}", interactionId);
        try {
            final var messageGroupId = messageGroupService.createMessageGroupId(context, interactionId);
            Map<String, Object> message = metadataBuilderService.buildSqsMessage(context);
            String messageJson = objectMapper.writeValueAsString(message);
            String queueUrl = resolveQueueUrl(context);
            LOG.info("SqsPublishStep:: Sending message to SQS. interactionId={}, queueUrl={}", interactionId, queueUrl);

            String messageId = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageJson)
                    .messageGroupId(messageGroupId)
                    .build())
                    .messageId();
            if (context != null) {
                context.setMessageId(messageId);
            }
            LOG.info("SqsPublishStep:: Message sent to SQS successfully. interactionId={}, messageId={}", interactionId,
                    messageId);
        } catch (Exception e) {
            LOG.error("SqsPublishStep:: SQS Publish Step Failed. interactionId={}", interactionId, e);
            // throw new RuntimeException("SQS Publish Step Failed", e);
        }
    }

    /**
     * Resolves the SQS queue URL based on the port config and request context.
     * Falls back to default if no match.
     */
    private String resolveQueueUrl(RequestContext context) {
        /**
         * Resolves the SQS queue URL based on the x-server-port header in the
         * request context. - Always prefers the x-server-port header
         * (case-insensitive). - Falls back to portConfig if available and
         * loaded. - Logs all decisions for traceability.
         */
        int requestPort = -1;
        try {
            Map<String, String> headers = context.getHeaders();
            String portHeader = null;
            if (headers != null) {
                // Case-insensitive lookup for x-server-port
                portHeader = headers.get("x-server-port");
                if (portHeader == null) {
                    portHeader = headers.get("X-Server-Port");
                }
            }
            if (portHeader != null && !portHeader.isBlank()) {
                requestPort = Integer.parseInt(portHeader);
                LOG.info("SqsPublishStep:: Using x-server-port header value: {}", requestPort);
            } else {
                LOG.warn("SqsPublishStep:: x-server-port header missing or blank; cannot resolve port for SQS queue selection.");
            }
        } catch (Exception e) {
            LOG.warn("SqsPublishStep:: Could not parse request port from x-server-port header: {}", e.getMessage());
        }
        if (portConfig != null && portConfig.isLoaded() && requestPort > 0) {
            for (PortConfig.PortEntry entry : portConfig.getPortConfigurationList()) {
                if (entry.port == requestPort && entry.queue != null && !entry.queue.isBlank()) {
                    LOG.info("SqsPublishStep:: Using queue from port config for port {}: {}", requestPort, entry.queue);
                    return entry.queue;
                }
            }
            LOG.warn("SqsPublishStep:: No matching port entry found in port config for port {}. Falling back to default queue.", requestPort);
        } else if (requestPort > 0) {
            LOG.warn("SqsPublishStep:: Port config unavailable or not loaded. Falling back to default queue for port {}.", requestPort);
        }
        // Fallback to default
        LOG.info("SqsPublishStep:: Using default SQS queue from AppConfig");
        return appConfig.getAws().getSqs().getFifoQueueUrl();
    }

    @Override
    public boolean isEnabledFor(RequestContext context) {
        return context.getMessageSourceType() != null && context.getMessageSourceType().shouldUploadToSqs();
    }
}
