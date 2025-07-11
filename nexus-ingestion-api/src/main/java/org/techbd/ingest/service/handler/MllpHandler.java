package org.techbd.ingest.service.handler;


import java.util.Map;

import org.springframework.stereotype.Component;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;

@Component
public class MllpHandler implements IngestionSourceHandler {

    private final MessageProcessorService processorService;

    public MllpHandler(MessageProcessorService processorService) {
        this.processorService = processorService;
    }

    @Override
    public boolean canHandle(Object source) {
        return source instanceof String && ((String) source).startsWith("MSH");
    }

    @Override
    public Map<String, String> handleAndProcess(Object source, RequestContext context) {
        String hl7Payload = (String) source;
        return processorService.processMessage(context, hl7Payload);
    }
}