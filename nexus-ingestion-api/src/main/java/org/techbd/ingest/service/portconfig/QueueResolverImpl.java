package org.techbd.ingest.service.portconfig;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig.PortEntry;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

/**
 * Resolves the SQS queue URL for a given request. Resolution priority is:
 * <ol>
 *   <li>Header {@code X-TechBd-Queue-Name}, resolved via SQS API</li>
 *   <li>PortEntry queue from configuration</li>
 *   <li>Default FIFO queue from {@link AppConfig}</li>
 * </ol>
 */
@Component
public class QueueResolverImpl implements PortConfigAttributeResolver {

    private final AppConfig appConfig;
    private final TemplateLogger LOG;
    private final SqsClient sqsClient;

    /**
     * Constructs a QueueResolverImpl.
     *
     * @param appConfig the application configuration
     * @param appLogger the application logger
     * @param sqsClient the AWS SQS client
     */
    public QueueResolverImpl(AppConfig appConfig, AppLogger appLogger, SqsClient sqsClient) {
        this.appConfig = appConfig;
        this.LOG = appLogger.getLogger(QueueResolverImpl.class);
        this.sqsClient = sqsClient;
    }

    /**
     * Resolves and applies the SQS queue URL to the request context.
     *
     * <p>Resolution steps:
     * <ul>
     *   <li>If {@code X-TechBd-Queue-Name} header is present, attempts to resolve it via SQS API</li>
     *   <li>If port entry defines a queue, use it</li>
     *   <li>Otherwise, fall back to the default FIFO queue URL from configuration</li>
     * </ul>
     *
     * @param context the request context to update
     * @param entry the port configuration entry
     * @param interactionId the interaction ID for logging
     */
    @Override
    public void resolve(RequestContext context, PortEntry entry, String interactionId) {
        String resolvedQueueUrl = resolveQueueUrl(context, entry, interactionId);
        
        if (resolvedQueueUrl != null && !resolvedQueueUrl.equals(context.getQueueUrl())) {
            context.setQueueUrl(resolvedQueueUrl);
            LOG.debug("[QUEUE_RESOLVER] Queue URL updated to: {} interactionId={}", resolvedQueueUrl, interactionId);
        }
    }

    /**
     * Internal method to resolve the queue URL.
     *
     * @param context the request context containing headers and interaction info
     * @param entry the port configuration entry
     * @param interactionId the interaction ID for logging
     * @return the resolved SQS queue URL
     */
    private String resolveQueueUrl(RequestContext context, PortEntry entry, String interactionId) {
        Map<String, String> headers = context.getHeaders();

        // Helper to find a header value ignoring case
        java.util.function.BiFunction<Map<String, String>, String, String> findHeader = (h, name) -> {
            if (h == null) return null;
            String v = h.get(name);
            if (v != null) return v;
            return h.entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(name))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        };

        // Check header override
        String overrideQueue = findHeader.apply(headers, "X-TechBd-Queue-Name");
        if (overrideQueue != null && !overrideQueue.isBlank()) {
            try {
                // Try to resolve the queue via SQS API
                GetQueueUrlResponse resp = sqsClient.getQueueUrl(
                        GetQueueUrlRequest.builder().queueName(overrideQueue).build());
                String resolvedQueueUrl = resp.queueUrl();
                LOG.info("[QUEUE_RESOLVER] Using X-TechBd-Queue-Name override, resolved queue URL: {} interactionId={}",
                        resolvedQueueUrl, interactionId);
                return resolvedQueueUrl;
            } catch (SqsException e) {
                LOG.warn(
                        "[QUEUE_RESOLVER] X-TechBd-Queue-Name '{}' could not be resolved by SQS: {}. Falling back to default queue. interactionId={}",
                        overrideQueue, e.getMessage(), interactionId, e);
            } catch (RuntimeException e) {
                LOG.warn(
                        "[QUEUE_RESOLVER] Error while resolving X-TechBd-Queue-Name '{}': {}. Falling back to default queue. interactionId={}",
                        overrideQueue, e.getMessage(), interactionId, e);
            }
        }

        // Use port config queue if present
        if (entry != null && entry.queue != null && !entry.queue.isBlank()) {
            LOG.info("[QUEUE_RESOLVER] Using queue from port config: {} interactionId={}", entry.queue,
                    interactionId);
            return entry.queue;
        }

        // Fallback to default
        String defaultQueue = appConfig.getAws().getSqs().getFifoQueueUrl();
        LOG.info("[QUEUE_RESOLVER] Falling back to default FIFO queue URL: {} interactionId={}", defaultQueue,
                interactionId);
        return defaultQueue;
    }
}