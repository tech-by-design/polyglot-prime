package org.techbd.ingest.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.processor.MessageProcessingStep;

/**
 * {@code MessageProcessorService} is responsible for orchestrating the
 * processing of messages
 * through a configurable sequence of {@link MessageProcessingStep}
 * implementations.
 * <p>
 * It supports both multipart file uploads and raw JSON/String content, allowing
 * flexible
 * ingestion workflows across different sources. Each step in the pipeline
 * performs a distinct
 * task, such as validation, transformation, persistence, or publishing to
 * downstream systems.
 * </p>
 *
 * <p>
 * Processing steps are executed in the order they are provided, enabling
 * deterministic
 * and extensible handling of incoming messages.
 * </p>
 */
@Service
public class MessageProcessorService {
    private static final Logger LOG = LoggerFactory.getLogger(MessageProcessorService.class);

    private final List<MessageProcessingStep> processingSteps;

    public MessageProcessorService(List<MessageProcessingStep> processingSteps) {
        this.processingSteps = processingSteps;
        LOG.info("MessageProcessorService:: initialized");
    }

    /**
     * Processes a multipart file by executing each configured processing step in
     * sequence.
     *
     * @param context The request context containing metadata for the operation.
     * @param file    The multipart file to process.
     * @return A map containing the result of the processing, including message ID
     *         and S3 path.
     */
    public Map<String, String> processMessage(RequestContext context, MultipartFile file) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info(
                "MessageProcessorService:: processMessage called with MultipartFile. interactionId={}, filename={}, filesize={}",
                interactionId,
                file != null ? file.getOriginalFilename() : "null",
                file != null ? file.getSize() : 0);
        for (MessageProcessingStep step : processingSteps) {
            LOG.info("MessageProcessorService:: Processing step {} for interactionId={}",
                    step.getClass().getSimpleName(), interactionId);
            step.process(context, file);
        }
        LOG.info("MessageProcessorService:: All processing steps completed for interactionId={}", interactionId);
        return createSuccessResponse(context.getMessageId(), context);
    }

    /**
     * Processes a raw string content without an acknowledgement message.
     *
     * @param context The request context containing metadata for the operation.
     * @param content The raw string content to process.
     * @return A map containing the result of the processing, including message ID
     *         and S3 path.
     */
    public Map<String, String> processMessage(RequestContext context, String content) {
        return processMessage(context, content, null);
    }

    /**
     * Processes a raw string content by executing each configured processing step
     * in sequence.
     *
     * @param context    The request context containing metadata for the operation.
     * @param content    The raw string content to process.
     * @param ackMessage The acknowledgement message to be processed.
     * @return A map containing the result of the processing, including message ID
     *         and S3 path.
     */
    public Map<String, String> processMessage(RequestContext context, String content, String ackMessage) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("MessageProcessorService:: processMessage called with String content. interactionId={}",
                interactionId);
        for (MessageProcessingStep step : processingSteps) {
            LOG.info("MessageProcessorService:: Processing step {} for interactionId={}",
                    step.getClass().getSimpleName(), interactionId);
            step.process(context, content, ackMessage);
        }
        LOG.info("MessageProcessorService:: All processing steps completed for interactionId={}", interactionId);
        return createSuccessResponse(context.getMessageId(), context);
    }

    /**
     * Creates a success response map containing the message ID, interaction ID, S3
     * path, and timestamp.
     *
     * @param messageId The ID of the processed message.
     * @param context   The request context containing metadata for the operation.
     * @return A map with success details.
     */
    private Map<String, String> createSuccessResponse(String messageId, RequestContext context) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        try {
            LOG.info("MessageProcessorService:: Creating success response for interactionId={}", interactionId);
            return Map.of(
                    "messageId", messageId,
                    "interactionId", context.getInteractionId(),
                    "fullS3Path", context.getFullS3DataPath(),
                    "timestamp", context.getTimestamp());
        } catch (Exception e) {
            LOG.error("MessageProcessorService:: Error creating success response for interactionId={}", interactionId,
                    e);
            // throw new RuntimeException("Failed to create response: " + e.getMessage(),
            // e);
            return Map.of(
                    "error", "Failed to create response",
                    "interactionId", interactionId,
                    "exception", e.getClass().getSimpleName(),
                    "message", e.getMessage() != null ? e.getMessage() : "Unknown error");

        }
    }
}