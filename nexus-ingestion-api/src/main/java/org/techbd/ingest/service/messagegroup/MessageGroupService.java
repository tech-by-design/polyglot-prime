
package org.techbd.ingest.service.messagegroup;

import java.util.List;

import org.springframework.stereotype.Service;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

/**
 * Service responsible for generating a unique message group ID for a message
 * based on its metadata. The message group ID is used for grouping related
 * messages in message-driven systems (e.g., SQS FIFO queues, Kafka partitions).
 *
 * <p>
 * This service delegates the actual creation logic to a list of
 * {@link MessageGroupStrategy} implementations. The first strategy that
 * supports the given {@link RequestContext} is used to generate the ID.
 * </p>
 *
 * <p>
 * If no strategy matches, a default group ID
 * {@link Constants#DEFAULT_MESSAGE_GROUP_ID}
 * is assigned.
 * </p>
 *
 * <p>
 * Strategies can include, for example:
 * <ul>
 * <li>MLLP-based grouping (using deliveryType, facility, messageCode)</li>
 * <li>Tenant-based grouping (using tenantId)</li>
 * <li>Fallback grouping (using sourceIp, destinationIp, destinationPort)</li>
 * </ul>
 * </p>
 */
@Service
public class MessageGroupService {
    private final List<MessageGroupStrategy> strategies;
    private static TemplateLogger logger;

    public MessageGroupService(List<MessageGroupStrategy> strategies, AppLogger appLogger) {
        this.strategies = strategies;
        logger = appLogger.getLogger(MessageGroupService.class);
    }

    /**
     * Generates a message group ID for the given {@link RequestContext} and
     * interaction ID. Delegates to the first matching strategy.
     *
     * <p>
     * Execution flow:
     * <ol>
     * <li>Iterate over all strategies in order of precedence</li>
     * <li>Check if the strategy supports the given context</li>
     * <li>If supported, generate the message group ID and set it in context</li>
     * <li>If no strategy supports the context, fallback to default group ID</li>
     * </ol>
     * </p>
     *
     * @param context       the request context containing message metadata
     * @param interactionId a unique interaction ID for logging/tracing
     * @return the generated message group ID
     */
    public String createMessageGroupId(RequestContext context, String interactionId) {
        for (MessageGroupStrategy strategy : strategies) {
            if (strategy.supports(context)) {
                String groupId = strategy.createGroupId(context, interactionId);

                logger.info("Selected strategy [{}] generated messageGroupId='{}' for interactionId='{}'",
                        strategy.getClass().getSimpleName(), groupId, interactionId);

                context.setMessageGroupId(groupId);
                return groupId;
            }
        }
        logger.warn("No strategy matched, using default message group ID. interactionId='{}'", interactionId);
        context.setMessageGroupId(Constants.DEFAULT_MESSAGE_GROUP_ID);
        return Constants.DEFAULT_MESSAGE_GROUP_ID;
    }
}
