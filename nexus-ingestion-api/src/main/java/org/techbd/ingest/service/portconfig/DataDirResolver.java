package org.techbd.ingest.service.portconfig;

import org.techbd.ingest.config.PortConfig.PortEntry;
import org.techbd.ingest.model.RequestContext;

public interface DataDirResolver {
    String resolveDataKey(RequestContext context, PortEntry entry);

    default String resolveAckObjectKey(RequestContext context, PortEntry entry) {
        return resolveDataKey(context, entry) + "_ack";
    }
    String resolveMetadataKey(RequestContext context, PortEntry entry);
}
