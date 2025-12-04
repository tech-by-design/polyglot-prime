package org.techbd.ingest.service.portconfig;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.techbd.ingest.config.PortConfig.PortEntry;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

/**
 * Central orchestrator that applies PortConfig-based overrides to the
 * {@link RequestContext}.
 *
 * <p>This service delegates all resolution logic to implementations of
 * {@link PortConfigAttributeResolver}. Each resolver is responsible for
 * updating specific attributes of the context (queue URLs, buckets, keys, etc.).
 *
 * <p>If no matching port entry is found, the service logs that defaults are
 * being used and returns the context unchanged.
 */
@Service
public class PortConfigApplierService {

    private final PortResolverService portEntryResolver;
    private final List<PortConfigAttributeResolver> attributeResolvers;
    private final TemplateLogger LOG;

    /**
     * Creates a new instance of the applier service.
     *
     * @param portEntryResolver resolver for finding the applicable PortEntry
     * @param attributeResolvers list of all attribute resolvers to apply
     * @param appLogger application-level structured logger provider
     */
    public PortConfigApplierService(
            PortResolverService portEntryResolver,
            List<PortConfigAttributeResolver> attributeResolvers,
            AppLogger appLogger) {

        this.portEntryResolver = portEntryResolver;
        this.attributeResolvers = attributeResolvers;
        this.LOG = appLogger.getLogger(PortConfigApplierService.class);
    }

    /**
     * Applies queue, bucket, and S3 key overrides to the provided
     * {@link RequestContext} based on the resolved {@link PortEntry}.
     *
     * <p>The method performs the following steps:
     * <ol>
     *     <li>Resolve the matching PortEntry (route-parameter–based or header-based)</li>
     *     <li>Iteratively invoke each {@link PortConfigAttributeResolver} to apply overrides</li>
     *     <li>Log the overall operation for traceability</li>
     * </ol>
     *
     * <p>If no PortEntry is resolved, logs an informational message and returns the
     * context unchanged.
     *
     * @param context the mutable request context containing routing and S3 metadata
     * @return the updated {@link RequestContext}, or the original if no entry matched
     */
    public RequestContext applyPortConfigOverrides(RequestContext context) {
        String interactionId = context.getInteractionId();

        Optional<PortEntry> portEntryOpt = portEntryResolver.resolve(context);
        if (!portEntryOpt.isPresent()) {
            LOG.debug(
                    "[PORT_CONFIG_APPLY] No port entry resolved for context. Using default values. interactionId={}",
                    interactionId);
            return context;
        }
        PortEntry entry = portEntryOpt.get();
        LOG.info(
                "[PORT_CONFIG_APPLY] CHECK_FOR_PORT_CONFIG_OVERRIDES for port {} sourceId: {} msgType: {} interactionId={}",
                entry.port, context.getSourceId(), context.getMsgType(), interactionId);

        // Iteratively apply all attribute resolvers
        for (PortConfigAttributeResolver resolver : attributeResolvers) {
            resolver.resolve(context, entry, interactionId);
        }
        LOG.info("[PORT_CONFIG_APPLY] All port config overrides applied successfully. interactionId={}",
                interactionId);
        return context;
    }
}