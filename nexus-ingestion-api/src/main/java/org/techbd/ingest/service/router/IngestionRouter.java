package org.techbd.ingest.service.router;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.handler.IngestionSourceHandler;

/**
 * {@code IngestionRouter} is a service that routes an incoming source object to an appropriate
 * {@link IngestionSourceHandler} based on its type and delegates processing to that handler.
 * <p>
 * It uses a list of {@link IngestionSourceHandler} implementations and selects the first one
 * capable of handling the given source object.
 * </p>
 */
@Service
public class IngestionRouter {
    private static final Logger LOG = LoggerFactory.getLogger(IngestionRouter.class);

    private final List<IngestionSourceHandler> handlers;

    /**
     * Constructs an {@code IngestionRouter} with the provided list of handlers.
     *
     * @param handlers the list of {@link IngestionSourceHandler} implementations that this router can use
     */
    public IngestionRouter(List<IngestionSourceHandler> handlers) {
        this.handlers = handlers;
        LOG.info("IngestionRouter initialized with {} handlers.", handlers != null ? handlers.size() : 0);
    }

    /**
     * Routes and processes the given source object using an appropriate handler.
     * <p>
     * The method finds the first {@link IngestionSourceHandler} that supports the source object
     * (via {@code canHandle}) and then delegates the processing to it (via {@code handleAndProcess}).
     * If no suitable handler is found, an {@link IllegalArgumentException} is thrown.
     * </p>
     *
     * @param source  the source object to be routed and processed
     * @param context contextual metadata related to the request, such as interaction ID
     * @return a map containing processing results or metadata as returned by the selected handler
     * @throws IllegalArgumentException if no handler is available to process the given source object
     */
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

