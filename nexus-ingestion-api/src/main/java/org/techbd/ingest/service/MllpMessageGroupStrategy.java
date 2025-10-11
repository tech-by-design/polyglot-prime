package org.techbd.ingest.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.model.RequestContext;

@Component
@Order(1)
public class MllpMessageGroupStrategy implements MessageGroupStrategy {

    @Override
    public boolean supports(RequestContext context) {
        return MessageSourceType.MLLP == context.getMessageSourceType();
    }

    @Override
    public String createGroupId(RequestContext context, String interactionId) {
        Map<String, String> params = context.getAdditionalParameters();
        String deliveryType = StringUtils.trimToEmpty(params.get("deliveryType"));
        String facility = StringUtils.trimToEmpty(params.get("facility"));
        String messageCode = StringUtils.trimToEmpty(params.get("messageCode"));

        List<String> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(deliveryType)) parts.add(deliveryType);
        if (StringUtils.isNotBlank(facility)) parts.add(facility);
        if (StringUtils.isNotBlank(messageCode)) parts.add(messageCode);

        if (!parts.isEmpty()) {
            return String.join("_", parts);
        }
        return StringUtils.defaultIfBlank(context.getDestinationPort(), Constants.DEFAULT_MESSAGE_GROUP_ID);
    }
}
