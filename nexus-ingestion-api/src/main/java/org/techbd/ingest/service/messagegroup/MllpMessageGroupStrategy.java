package org.techbd.ingest.service.messagegroup;

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
        if (params == null) {
            LOG.warn("[GROUP_ID_GEN] Additional parameters map is null for interactionId={}", interactionId);
            return StringUtils.defaultIfBlank(context.getDestinationPort(), Constants.DEFAULT_MESSAGE_GROUP_ID);
        }

        String deliveryType = StringUtils.trimToEmpty(params.get(Constants.DELIVERY_TYPE)); // ZNT-4.1
        String facility = StringUtils.trimToEmpty(params.get(Constants.FACILITY)); // ZNT-8 split index 1
        String messageCode = StringUtils.trimToEmpty(params.get(Constants.MESSAGE_CODE)); // ZNT-2.1
        String qe = StringUtils.trimToEmpty(params.get(Constants.QE)); // ZNT-8 split index 0

        LOG.info("[GROUP_ID_GEN] DeliveryType={} Facility={} MessageCode={} QE={} InteractionId={}",
                StringUtils.isBlank(deliveryType) ? "Not Available" : "Available",
                StringUtils.isBlank(facility) ? "Not Available" : "Available",
                StringUtils.isBlank(messageCode) ? "Not Available" : "Available",
                StringUtils.isBlank(qe) ? "Not Available" : "Available",
                StringUtils.isBlank(interactionId) ? "Not Available" : interactionId);

        List<String> parts = new ArrayList<>();

        // 👉 Correct ordering
        if (StringUtils.isNotBlank(qe))
            parts.add(qe);
        if (StringUtils.isNotBlank(facility))
            parts.add(facility);
        if (StringUtils.isNotBlank(messageCode))
            parts.add(messageCode);
        if (StringUtils.isNotBlank(deliveryType))
            parts.add(deliveryType);

        if (!parts.isEmpty()) {
            return String.join("_", parts);
        }

        return StringUtils.defaultIfBlank(context.getDestinationPort(), Constants.DEFAULT_MESSAGE_GROUP_ID);
    }


}
