package org.techbd.ingest.service;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.processor.MessageProcessingStep;

@Service
public class MessageProcessorService {
    private static final Logger LOG = LoggerFactory.getLogger(MessageProcessorService.class);

    private final List<MessageProcessingStep> processingSteps;

    public MessageProcessorService(List<MessageProcessingStep> processingSteps) {
        this.processingSteps = processingSteps;
        LOG.info("MessageProcessorService:: initialized");
    }

    public Map<String, String> processMessage(RequestContext context, MultipartFile file) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("MessageProcessorService:: processMessage called with MultipartFile. interactionId={}, filename={}, filesize={}", 
            interactionId, 
            file != null ? file.getOriginalFilename() : "null", 
            file != null ? file.getSize() : 0);
        for (MessageProcessingStep step : processingSteps) {
            LOG.info("MessageProcessorService:: Processing step {} for interactionId={}", step.getClass().getSimpleName(), interactionId);
            step.process(context, file);
        }
        LOG.info("MessageProcessorService:: All processing steps completed for interactionId={}", interactionId);
        return createSuccessResponse(context.getMessageId(), context);
    }

    public Map<String, String> processMessage(RequestContext context, String content) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("MessageProcessorService:: processMessage called with String content. interactionId={}", interactionId);
        for (MessageProcessingStep step : processingSteps) {
            LOG.info("MessageProcessorService:: Processing step {} for interactionId={}", step.getClass().getSimpleName(), interactionId);
            step.process(context, content);
        }
        LOG.info("MessageProcessorService:: All processing steps completed for interactionId={}", interactionId);
        return createSuccessResponse(context.getMessageId(), context);
    }

    private Map<String, String> createSuccessResponse(String messageId, RequestContext context) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        try {
            LOG.info("MessageProcessorService:: Creating success response for interactionId={}", interactionId);
            return Map.of(
                    "messageId", messageId,
                    "interactionId", context.getInteractionId(),
                    "fullS3Path", context.getFullS3Path(),
                    "timestamp", context.getTimestamp());
        } catch (Exception e) {
            LOG.error("MessageProcessorService:: Error creating success response for interactionId={}", interactionId, e);
            //throw new RuntimeException("Failed to create response: " + e.getMessage(), e);
            return Map.of(
                "error", "Failed to create response",
                "interactionId", interactionId,
                "exception", e.getClass().getSimpleName(),
                "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
            );
            
        }
    }
}