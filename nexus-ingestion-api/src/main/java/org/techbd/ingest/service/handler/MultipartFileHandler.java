package org.techbd.ingest.service.handler;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;

/**
 * {@code MultipartFileHandler} is an implementation of {@link IngestionSourceHandler} that
 * handles ingestion of {@link MultipartFile} sources.
 * <p>
 * This handler validates whether the provided source is a {@code MultipartFile} and delegates
 * its processing to the {@link MessageProcessorService}. It is typically used to process
 * file uploads sent via multipart/form-data requests.
 * </p>
 */
@Component
public class MultipartFileHandler implements IngestionSourceHandler {
    private static final Logger LOG = LoggerFactory.getLogger(MultipartFileHandler.class);

    private final MessageProcessorService processorService;

    public MultipartFileHandler(MessageProcessorService processorService) {
        this.processorService = processorService;
        LOG.info("MultipartFileHandler initialized");
    }

    /**
     * Checks if the source object is a valid MultipartFile.
     *
     * @param source The source object to check.
     * @return true if the source is a valid MultipartFile, false otherwise.
     */
    @Override
    public boolean canHandle(Object source, RequestContext context) {
        boolean canHandle = source instanceof MultipartFile;
        LOG.info("MultipartFileHandler:: canHandle called. Source type: {}, Result: {}, interactionId={}", 
                source != null ? source.getClass().getSimpleName() : "null", canHandle, context != null ? context.getInteractionId() : "unknown");
        return canHandle;
    }

    /**
     * Processes the MultipartFile by delegating to the MessageProcessorService.
     *
     * @param source  The MultipartFile to process.
     * @param context The request context containing metadata for processing.
     * @return A map containing the result of the processing.
     */
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