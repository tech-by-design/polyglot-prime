package org.techbd.ingest.service.portconfig;

import org.techbd.ingest.config.PortConfig.PortEntry;
import org.techbd.ingest.model.RequestContext;

/**
 * Unified interface for resolving and applying port configuration attributes
 * to a {@link RequestContext}.
 * 
 * <p>Each implementation is responsible for resolving a specific aspect of the
 * configuration (e.g., queue URL, S3 buckets, object keys) and updating the
 * context accordingly.
 */
public interface PortConfigAttributeResolver {
    
    /**
     * Resolves and applies configuration attributes to the request context.
     * 
     * @param context the request context to update
     * @param entry the resolved port entry configuration; may be {@code null}
     * @param interactionId the interaction ID for logging and traceability
     */
    void resolve(RequestContext context, PortEntry entry, String interactionId);
}
