package org.techbd.ingest.service.handler;


import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;

@Component
public class MllpHandler implements IngestionSourceHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MllpHandler.class);

    private final MessageProcessorService processorService;

    public MllpHandler(MessageProcessorService processorService) {
        this.processorService = processorService;
        LOG.info("MllpHandler initialized ");
    }

    @Override
    public boolean canHandle(Object source) {
        boolean canHandle = source instanceof String && ((String) source).startsWith("MSH");//TODO modify based on the ports suported
        LOG.info("MllpHandler:: canHandle called. Source type: {}, Result: {}", 
                source != null ? source.getClass().getSimpleName() : "null", canHandle);
        return canHandle;
    }

    @Override
    public Map<String, String> handleAndProcess(Object source, RequestContext context) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("MllpHandler:: handleAndProcess called. interactionId={}", interactionId);
        String hl7Payload = (String) source;
        Map<String, String> result = processorService.processMessage(context, hl7Payload);
        LOG.info("MllpHandler:: Processing complete in handleAndProcess. interactionId={}", interactionId);
        return result;
    }
}