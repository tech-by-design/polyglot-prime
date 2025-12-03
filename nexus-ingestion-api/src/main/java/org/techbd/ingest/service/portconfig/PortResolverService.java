package org.techbd.ingest.service.portconfig;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.config.PortConfig.PortEntry;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service responsible for resolving the appropriate {@link PortEntry} for a
 * given {@link RequestContext}. It iterates through a list of {@link PortConfigResolver}s
 * to find a matching configuration based on source ID, message type, or port.
 *
 * <p>Resolution logic:
 * <ul>
 *   <li>If a resolver matches {@code sourceId} and {@code msgType}, that entry is used.</li>
 *   <li>If no exact match, a resolver matching only the port is returned.</li>
 *   <li>If no resolvers match, an empty {@link Optional} is returned.</li>
 * </ul>
 *
 * <p>Example PortEntry list:
 * <pre>
 *  - PortEntry(port=8080, dataDir="data", metadataDir="metadata", queue="queue1")
 *  - PortEntry(sourceId="LAB1", msgType="ORU", dataDir="lab/data", metadataDir="lab/metadata", queue="labQueue")
 * </pre>
 *
 * <p>Usage:
 * <pre>
 *   Optional&lt;PortEntry&gt; entry = portResolverService.resolve(requestContext);
 *   if (entry.isPresent()) {
 *       LOG.info("Resolved port entry: {}", entry.get());
 *   } else {
 *       LOG.info("No matching port entry found, using defaults.");
 *   }
 * </pre>
 */
@Service
public class PortResolverService {

    private final List<PortConfigResolver> resolvers;
    private final TemplateLogger LOG;
    private final PortConfig portConfig;

    /**
     * Constructs the service with a list of resolvers and port configuration.
     *
     * @param resolvers  list of resolvers to apply in order
     * @param portConfig the complete port configuration
     * @param appLogger  the application logger
     */
    public PortResolverService(List<PortConfigResolver> resolvers, PortConfig portConfig,
            AppLogger appLogger) {
        this.resolvers = resolvers;
        this.portConfig = portConfig;
        this.LOG = appLogger.getLogger(PortResolverService.class);
    }

    /**
     * Attempts to resolve a {@link PortEntry} for the given {@link RequestContext}.
     *
     * <p>
     * Iterates through the list of {@link PortConfigResolver}s in order. Returns
     * the first {@link PortEntry} that matches the context criteria.
     *
     * @param context the request context containing sourceId, msgType, and other
     *                info
     * @return an {@link Optional} containing the resolved PortEntry, or empty if
     *         none match
     */
    public Optional<PortEntry> resolve(RequestContext context) {
        LOG.info("[PORT_RESOLVER] Resolving PortEntry for context: sourceId={}, msgType={}, port={}",
                context.getSourceId(), context.getMsgType(), context.getDestinationPort());

        for (PortConfigResolver resolver : resolvers) {
            Optional<PortEntry> result = resolver.resolve(portConfig.getPortConfigurationList(), context);

            if (result.isPresent()) {
                PortEntry entry = result.get();
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    String json = mapper.writeValueAsString(entry);
                    LOG.info("[PORT_CONFIG_MATCH] Found PortEntry using resolver {}: port={}, sourceId={}, msgType={} interactionId={} {}",
                            resolver.getClass().getSimpleName(), context.getDestinationPort(), entry.sourceId, entry.msgType, context.getInteractionId(), json);
                } catch (Exception e) {
                    LOG.error("[PORT_CONFIG_ERROR] Failed to serialize PortEntry for port={} interactionId={}",
                            context.getDestinationPort(), context.getInteractionId(), e);
                }
                return result;
            }
        }

        LOG.warn("[PORT_CONFIG_ERROR] No configuration found for port {} sourceId={} msgType={} interactionId={}",
                context.getDestinationPort(), context.getSourceId(), context.getMsgType(), context.getInteractionId());

        return Optional.empty();
    }

}
