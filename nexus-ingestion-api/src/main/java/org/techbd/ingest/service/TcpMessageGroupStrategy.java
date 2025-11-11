package org.techbd.ingest.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

@Component
@Order(2)
public class TcpMessageGroupStrategy implements MessageGroupStrategy {
    private final TemplateLogger LOG;

    public TcpMessageGroupStrategy(AppLogger appLogger) {
        this.LOG = appLogger.getLogger(TcpMessageGroupStrategy.class);
    }

    @Override
    public boolean supports(RequestContext context) {
        return MessageSourceType.TCP == context.getMessageSourceType();
    }

    @Override
    public String createGroupId(RequestContext context, String interactionId) {
        return StringUtils.defaultIfBlank(context.getDestinationPort(), Constants.DEFAULT_MESSAGE_GROUP_ID);
    }
}
