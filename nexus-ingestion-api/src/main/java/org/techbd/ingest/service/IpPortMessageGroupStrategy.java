package org.techbd.ingest.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.ingest.model.RequestContext;


@Component
@Order(4)
public class IpPortMessageGroupStrategy implements MessageGroupStrategy {
    @Override
    public boolean supports(RequestContext context) {
        // This is the last strategy in the chain of message group ID generation.
        // It acts as a fallback when no other strategies (MLLP, Tenant, etc.) apply.
        // Always returns true to ensure a message group ID is generated.
        return true;
    }

    @Override
    public String createGroupId(RequestContext context, String interactionId) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(context.getSourceIp()))
            parts.add(context.getSourceIp().trim());
        if (StringUtils.isNotBlank(context.getDestinationIp()))
            parts.add(context.getDestinationIp().trim());
        if (StringUtils.isNotBlank(context.getDestinationPort()))
            parts.add(context.getDestinationPort().trim());
        return parts.isEmpty() ? "unknown-tenantId" : String.join("_", parts);
    }
}
