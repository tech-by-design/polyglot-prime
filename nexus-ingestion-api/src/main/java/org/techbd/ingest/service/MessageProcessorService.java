package org.techbd.ingest.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.processor.MessageProcessingStep;
import org.techbd.ingest.service.portconfig.PortConfigApplierService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

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

    private final AppConfig appConfig;
    private  TemplateLogger LOG;
    private final List<MessageProcessingStep> processingSteps;
    private final PortConfigApplierService portConfigApplierService;

    public MessageProcessorService(List<MessageProcessingStep> processingSteps, AppLogger appLogger, AppConfig appConfig, PortConfigApplierService portConfigApplierService) {
        this.processingSteps = processingSteps;
        LOG = appLogger.getLogger(MessageProcessorService.class);
        LOG.info("MessageProcessorService:: initialized");
        this.appConfig = appConfig;
        this.portConfigApplierService = portConfigApplierService;
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
                "MessageProcessorService:: processMessage called with MultipartFile. interactionId={}, filename={}, filesize={} from source : {}",
                interactionId,
                file != null ? file.getOriginalFilename() : "null",
                file != null ? file.getSize() : 0, context.getMessageSourceType().name());
        portConfigApplierService.applyPortConfigOverrides(context);        
        for (MessageProcessingStep step : processingSteps) {
            if (step.isEnabledFor(context)) {
                LOG.info("MessageProcessorService:: Executing step {} for interactionId={}",
                        step.getClass().getSimpleName(), interactionId);
                step.process(context, file);
            } else {
                LOG.debug("MessageProcessorService:: Skipping step {} for interactionId={} (disabled)",
                        step.getClass().getSimpleName(), interactionId);
            }
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
        LOG.info("MessageProcessorService:: processMessage called with String content. interactionId={} from source {}",
                interactionId, context.getMessageSourceType().name());
        portConfigApplierService.applyPortConfigOverrides(context); 
        for (MessageProcessingStep step : processingSteps) {
            if (step.isEnabledFor(context)) {
                LOG.info("MessageProcessorService:: Executing step {} for interactionId={}",
                        step.getClass().getSimpleName(), interactionId);
                step.process(context, content, ackMessage);
            } else {
                LOG.debug("MessageProcessorService:: Skipping step {} for interactionId={} (disabled)",
                        step.getClass().getSimpleName(), interactionId);
            }
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
            Map<String, String> response = new HashMap<>();
            response.put("messageId", messageId != null ? messageId : context.getInteractionId());
            response.put("interactionId", context.getInteractionId());
            response.put("ingestionApiVersion", appConfig.getVersion());
            response.put("fullS3Path", context.getFullS3DataPath() != null ? context.getFullS3DataPath() : "not-set");
            response.put("fullS3MetaDataPath",
                    context.getFullS3MetadataPath() != null ? context.getFullS3MetadataPath() : "not-set");
            response.put("timestamp", context.getTimestamp());
            if (context.getFullS3AckMessagePath() != null) {
                response.put("fullS3AcknowledgementPath", context.getFullS3AckMessagePath());
            }

            return response;
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