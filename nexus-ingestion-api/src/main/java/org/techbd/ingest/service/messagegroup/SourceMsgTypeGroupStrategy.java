package org.techbd.ingest.service.messagegroup;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.ingest.model.RequestContext;

/**
 * Message-grouping strategy that uses:
 *
 *     {sourceId}_{msgType}
 *
 * This is applied only when BOTH values are present and non-blank.
 * Example:
 *   sourceId = "LAB1"
 *   msgType  = "ORU"
 *
 *   → Group ID = "LAB1_ORU"
 */
@Component
@Order(3)
public class SourceMsgTypeGroupStrategy implements MessageGroupStrategy {

    @Override
    public boolean supports(RequestContext context) {
        return StringUtils.isNotBlank(context.getSourceId())
            && StringUtils.isNotBlank(context.getMsgType());
    }

    @Override
    public String createGroupId(RequestContext context, String interactionId) {
        return context.getSourceId().trim() + "_" + context.getMsgType().trim();
    }
}

