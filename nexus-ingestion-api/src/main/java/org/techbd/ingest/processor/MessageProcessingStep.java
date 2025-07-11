package org.techbd.ingest.processor;

import org.techbd.ingest.model.RequestContext;
import org.springframework.web.multipart.MultipartFile;

public interface MessageProcessingStep {
    void process(RequestContext context, MultipartFile file);
    void process(RequestContext context, String content);
}
