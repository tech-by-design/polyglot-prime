package org.techbd.ingest.service.handler;

import java.util.Map;

import org.techbd.ingest.model.RequestContext;

public interface IngestionSourceHandler {
    boolean canHandle(Object source);
    Map<String, String> handleAndProcess(Object source, RequestContext context);
}

