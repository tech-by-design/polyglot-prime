package org.techbd.ingest.service.portconfig;

import org.techbd.ingest.config.PortConfig.PortEntry;
import org.techbd.ingest.model.RequestContext;

public interface QueueResolver {
    String resolveQueueUrl(RequestContext context, PortEntry entry);
}