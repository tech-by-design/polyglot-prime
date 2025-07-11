package org.techbd.ingest.service;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.processor.MessageProcessingStep;

@Service
public class MessageProcessorService {
    private final List<MessageProcessingStep> processingSteps;

    public MessageProcessorService(List<MessageProcessingStep> processingSteps) {
        this.processingSteps = processingSteps;
    }

    public  Map<String, String> processMessage(RequestContext context, MultipartFile file) {
        for (MessageProcessingStep step : processingSteps) {
            step.process(context, file);
        }
        return createSuccessResponse(context.getMessageId(), context);
    }
    public  Map<String, String> processMessage(RequestContext context, String content) {
        for (MessageProcessingStep step : processingSteps) {
            step.process(context, content);
        }
        return createSuccessResponse(context.getMessageId(), context);
    }
    private Map<String, String> createSuccessResponse(String messageId, RequestContext context) {
        try {
            return Map.of(
                    "messageId", messageId,
                    "interactionId", context.interactionId(),
                    "fullS3Path", context.fullS3Path(),
                    "timestamp", context.timestamp());
        } catch (Exception e) {
            //log.error("Error creating success response", e);
            throw new RuntimeException("Failed to create response: " + e.getMessage(), e);
        }
    }
}