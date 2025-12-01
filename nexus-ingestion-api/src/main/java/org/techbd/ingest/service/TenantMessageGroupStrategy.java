package org.techbd.ingest.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.model.RequestContext;


@Component
@Order(3)
public class TenantMessageGroupStrategy implements MessageGroupStrategy {
    @Override
    public boolean supports(RequestContext context) {
        return StringUtils.isNotBlank(context.getTenantId()) &&
               !Constants.DEFAULT_TENANT_ID.equals(context.getTenantId());
    }

    @Override
    public String createGroupId(RequestContext context, String interactionId) {
        return context.getTenantId().trim();
    }
}
