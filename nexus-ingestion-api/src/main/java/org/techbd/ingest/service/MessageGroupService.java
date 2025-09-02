
package org.techbd.ingest.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.model.RequestContext;

import com.amazonaws.services.kms.model.MessageType;

/**
 * Service responsible for generating a unique message group ID
 * using metadata from a {@link RequestContext}.
 * <p>
 * The generated ID is used to group related messages, for example
 * in SQS FIFO queues or other message-driven systems.
 * <p>
 * Format of the message group ID: <br>
 * <code>{source_ip}_{destination_ip}_{destination_port}</code>
 * <br>
 * If any of the components are missing or blank, the group ID defaults to
 * <code>default_message_group</code> and a warning is logged.
 */
@Service
public class MessageGroupService {

    private static final Logger logger = LoggerFactory.getLogger(MessageGroupService.class);

    /**
     * Creates a message group ID by combining the source IP, destination IP,
     * and destination port from the given {@link RequestContext}.
     *
     * @param context the context containing metadata about the message source and
     *                destination
     * @return a string in the format:
     *         {@code sourceIp_destinationIp_destinationPort},
     *         or {@code default_message_group} if any part is missing
     */
    public String createMessageGroupId(RequestContext context, String interactionId) {
        String sourceIp = context.getSourceIp();
        String destinationIp = context.getDestinationIp();
        String destinationPort = context.getDestinationPort();
        String tenantId = context.getTenantId();
        MessageSourceType messageSourceType = context.getMessageSourceType();
        String messageGroupId = Constants.DEFAULT_MESSAGE_GROUP_ID;

        if (MessageSourceType.MLLP == messageSourceType) {
            if (StringUtils.isNotBlank(destinationPort)) {
                messageGroupId = destinationPort.trim();
            } else {
                logger.warn("MLLP source but no destination port. Using default group. interactionId='{}'",
                        interactionId);
            }
        } else if (StringUtils.isNotBlank(tenantId)) {
            messageGroupId = tenantId.trim();
        } else {
            List<String> parts = new ArrayList<>();
            if (StringUtils.isNotBlank(sourceIp))
                parts.add(sourceIp.trim());
            if (StringUtils.isNotBlank(destinationIp))
                parts.add(destinationIp.trim());
            if (StringUtils.isNotBlank(destinationPort))
                parts.add(destinationPort.trim());
            if (!parts.isEmpty()) {
                messageGroupId = String.join("_", parts);
            } else {
                logger.warn("No context values available. Using default message group. interactionId='{}'",
                        interactionId);
            }
        }

        logger.debug("Generated message group ID: {} for interactionId: {}", messageGroupId, interactionId);
        context.setMessageGroupId(messageGroupId);
        return messageGroupId;
    }

}