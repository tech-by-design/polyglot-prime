package org.techbd.ingest.service.router;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.handler.IngestionSourceHandler;

@Service
public class IngestionRouter {
    private final List<IngestionSourceHandler> handlers;

    public IngestionRouter(List<IngestionSourceHandler> handlers) {
        this.handlers = handlers;
    }

    public Map<String, String> routeAndProcess(Object source, RequestContext context) {
        return handlers.stream()
                .filter(h -> h.canHandle(source))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No handler for input type: " + source.getClass()))
                .handleAndProcess(source, context);
    }
}
