package org.techbd.ingest.service.handler;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;

@Component
public class MultipartFileHandler implements IngestionSourceHandler {
    private static final Logger LOG = LoggerFactory.getLogger(MultipartFileHandler.class);

    private final MessageProcessorService processorService;

    public MultipartFileHandler(MessageProcessorService processorService) {
        this.processorService = processorService;
        LOG.info("MultipartFileHandler initialized");
    }

    @Override
    public boolean canHandle(Object source) {
        boolean canHandle = source instanceof MultipartFile;
        LOG.info("MultipartFileHandler:: canHandle called. Source type: {}, Result: {}", 
                source != null ? source.getClass().getSimpleName() : "null", canHandle);
        return canHandle;
    }

    @Override
    public Map<String, String> handleAndProcess(Object source, RequestContext context) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("MultipartFileHandler:: handleAndProcess called. interactionId={}", interactionId);
        MultipartFile file = (MultipartFile) source;
        Map<String, String> result = processorService.processMessage(context, file);
        LOG.info("MultipartFileHandler:: Processing complete in handleAndProcess. interactionId={}", interactionId);
        return result;
    }
}