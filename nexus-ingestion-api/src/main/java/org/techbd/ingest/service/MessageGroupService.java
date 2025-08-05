
package org.techbd.ingest.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.model.RequestContext;
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
     * @param context the context containing metadata about the message source and destination
     * @return a string in the format: {@code sourceIp_destinationIp_destinationPort},
     *         or {@code default_message_group} if any part is missing
     */
    public String createMessageGroupId(RequestContext context,String interactionId) {
        String sourceIp = context.getSourceIp();
        String destinationIp = context.getDestinationIp();
        String destinationPort = context.getDestinationPort();
        var messageGroupId =Constants.DEFAULT_MESSAGE_GROUP_ID;
        if (isBlank(sourceIp) || isBlank(destinationIp) || isBlank(destinationPort)) {
            logger.warn("Incomplete request context. Using default message group. "
                      + "sourceIp='{}', destinationIp='{}', destinationPort='{}', interactionId='{}'",
                      sourceIp, destinationIp, destinationPort, interactionId);
            context.setMessageGroupId(messageGroupId);
            return messageGroupId;
        }
        messageGroupId =String.format("%s_%s_%s", sourceIp.trim(), destinationIp.trim(), destinationPort.trim());
        logger.debug("Generated message group ID: {} for interactionId: {}", messageGroupId, interactionId);
        context.setMessageGroupId(messageGroupId);
        return messageGroupId;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}