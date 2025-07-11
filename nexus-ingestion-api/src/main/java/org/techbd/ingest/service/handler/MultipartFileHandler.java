package org.techbd.ingest.service.handler;


import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;

@Component
public class MultipartFileHandler implements IngestionSourceHandler {
    private final MessageProcessorService processorService;

    public MultipartFileHandler(MessageProcessorService processorService) {
        this.processorService = processorService;
    }

    @Override
    public boolean canHandle(Object source) {
        return source instanceof MultipartFile;
    }

    @Override
    public  Map<String, String> handleAndProcess(Object source,RequestContext context) {
        MultipartFile file = (MultipartFile) source;      
        return processorService.processMessage(context, file);
    }
}