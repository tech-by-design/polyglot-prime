package org.techbd.ingest.service.router;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.handler.IngestionSourceHandler;

@Service
public class IngestionRouter {
    private static final Logger LOG = LoggerFactory.getLogger(IngestionRouter.class);

    private final List<IngestionSourceHandler> handlers;

    public IngestionRouter(List<IngestionSourceHandler> handlers) {
        this.handlers = handlers;
        LOG.info("IngestionRouter initialized with {} handlers.", handlers != null ? handlers.size() : 0);
    }

    public Map<String, String> routeAndProcess(Object source, RequestContext context) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("IngestionRouter:: Routing and processing source of type {}. interactionId={}", 
                source != null ? source.getClass().getSimpleName() : "null", interactionId);

        return handlers.stream()
                .filter(h -> h.canHandle(source))
                .findFirst()
                .map(handler -> {
                    LOG.info("IngestionRouter:: Handler {} selected for processing. interactionId={}", handler.getClass().getSimpleName(), interactionId);
                    return handler.handleAndProcess(source, context);
                })
                .orElseThrow(() -> {
                    LOG.error("IngestionRouter:: No handler found for input type: {}. interactionId={}", 
                        source != null ? source.getClass() : "null", interactionId);
                    return new IllegalArgumentException("No handler for input type: " + (source != null ? source.getClass() : "null"));
                });
    }
}
