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
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;
@Component
@Order(1)
public class MllpMessageGroupStrategy implements MessageGroupStrategy {
    private final TemplateLogger LOG;

    public MllpMessageGroupStrategy(AppLogger appLogger) {
        this.LOG = appLogger.getLogger(MllpMessageGroupStrategy.class);
    }

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
        // System.out.println("**************  **************");
        // System.out.println("deliveryType: " + deliveryType);
        // System.out.println("facility: " + facility);
        // System.out.println("messageCode: " + messageCode);
        // System.out.println("interactionId: " + interactionId);
        LOG.info(
                "Delivery Type: " + (StringUtils.isBlank(deliveryType) ? "Not Available" : "Available") + " | " +
                "Facility: " + (StringUtils.isBlank(facility) ? "Not Available" : "Available") + " | " +
                "Message Code: " + (StringUtils.isBlank(messageCode) ? "Not Available" : "Available") + " | " +
                "InteractionId: " + (StringUtils.isBlank(interactionId) ? "Not Available" : "Available")
        );

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
