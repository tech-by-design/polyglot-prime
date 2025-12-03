package org.techbd.ingest.service.portconfig;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.techbd.ingest.config.PortConfig.PortEntry;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

/**
 * Central orchestrator that applies PortConfig-based overrides to the
 * {@link RequestContext}.
 *
 * <p>This service delegates all resolution logic to small, focused components:
 * <ul>
 *     <li>{@link PortResolverService} – determines which PortEntry applies</li>
 *     <li>{@link QueueResolver} – resolves queue URLs</li>
 *     <li>{@link BucketResolver} – resolves data/metadata buckets</li>
 *     <li>{@link DataDirResolver} – builds S3 object keys</li>
 * </ul>
 * <p>If no matching port entry is found, the service logs that defaults are
 * being used and returns the context unchanged.
 */
@Service
public class PortConfigApplierService {

    private final PortResolverService portEntryResolver;
    private final QueueResolver queueResolver;
    private final BucketResolver bucketResolver;
    private final DataDirResolver keyResolver;
    private final TemplateLogger LOG;

    /**
     * Creates a new instance of the applier service.
     *
     * @param portEntryResolver resolver for finding the applicable PortEntry
     * @param queueResolver     resolves queue URLs, including header-based overrides
     * @param bucketResolver    resolves S3 data and metadata buckets
     * @param keyResolver       resolves object, metadata, and ack keys
     * @param appLogger         application-level structured logger provider
     */
    public PortConfigApplierService(PortResolverService portEntryResolver,
            QueueResolver queueResolver,
            BucketResolver bucketResolver,
            DataDirResolver keyResolver,
            AppLogger appLogger) {

        this.portEntryResolver = portEntryResolver;
        this.queueResolver = queueResolver;
        this.bucketResolver = bucketResolver;
        this.keyResolver = keyResolver;
        this.LOG = appLogger.getLogger(PortConfigApplierService.class);
    }

    /**
     * Applies queue, bucket, and S3 key overrides to the provided
     * {@link RequestContext} based on the resolved {@link PortEntry}.
     *
     * <p>The method performs the following steps:
     * <ol>
     *     <li>Resolve the matching PortEntry (route-parameter–based or header-based)</li>
     *     <li>Override the queue URL</li>
     *     <li>Override data and metadata bucket names</li>
     *     <li>Override data, metadata, and ack object keys</li>
     *     <li>Log each override individually for traceability</li>
     * </ol>
     *
     * <p>If no PortEntry is resolved, logs an informational message and returns the
     * context unchanged.
     *
     * @param context the mutable request context containing routing and S3 metadata
     * @return the updated {@link RequestContext}, or the original if no entry matched
     */
    public RequestContext applyPortConfigOverrides(RequestContext context) {
        String interactionId = context.getInteractionId();

        Optional<PortEntry> portEntryOpt = portEntryResolver.resolve(context);
        if (!portEntryOpt.isPresent()) {
            LOG.debug(
                    "[PORT_CONFIG_APPLY]:: No port entry resolved for context. Using default values. interactionId={}",
                    interactionId);
            return context;
        }

        PortEntry entry = portEntryOpt.get();
        LOG.info(
                "[PORT_CONFIG_APPLY]:: CHECK_FOR_PORT_CONFIG_OVERRIDES for port {} sourceId :{} msgType :{} interactionId={}",
                entry.port, context.getSourceId(), context.getMsgType(), interactionId);

        // queue
        String queueUrl = queueResolver.resolveQueueUrl(context, entry);
        if (queueUrl != null && !queueUrl.equals(context.getQueueUrl())) {
            context.setQueueUrl(queueUrl);
            LOG.debug("[PORT_CONFIG_APPLY]:: Queue URL overridden to: {} interactionId={}", queueUrl,
                    interactionId);
        }

        // buckets
        String dataBucket = bucketResolver.resolveDataBucket(entry,interactionId);
        if (dataBucket != null && !dataBucket.equals(context.getDataBucketName())) {
            context.setDataBucketName(dataBucket);
            LOG.debug("[PORT_CONFIG_APPLY]:: Data bucket overridden to: {} interactionId={}", dataBucket,
                    interactionId);
        }

        String metadataBucket = bucketResolver.resolveMetadataBucket(entry,interactionId);
        if (metadataBucket != null && !metadataBucket.equals(context.getMetaDataBucketName())) {
            context.setMetaDataBucketName(metadataBucket);
            LOG.debug("[PORT_CONFIG_APPLY]:: Metadata bucket overridden to: {} interactionId={}", metadataBucket,
                    interactionId);
        }

        // keys
        String objectKey = keyResolver.resolveDataKey(context, entry);
        if (objectKey != null && !objectKey.equals(context.getObjectKey())) {
            context.setObjectKey(objectKey);
            LOG.debug("[PORT_CONFIG_APPLY]:: Object key overridden to: {} interactionId={}", objectKey,
                    interactionId);
        }

        String metadataKey = keyResolver.resolveMetadataKey(context, entry);
        if (metadataKey != null && !metadataKey.equals(context.getMetadataKey())) {
            context.setMetadataKey(metadataKey);
            LOG.debug("[PORT_CONFIG_APPLY]:: Metadata key overridden to: {} interactionId={}", metadataKey,
                    interactionId);
        }

        String ackObjectKey = keyResolver.resolveAckObjectKey(context, entry);
        if (ackObjectKey != null && !ackObjectKey.equals(context.getAckObjectKey())) {
            context.setAckObjectKey(ackObjectKey);
            LOG.debug("[PORT_CONFIG_APPLY]:: Ack object key overridden to: {} interactionId={}", ackObjectKey,
                    interactionId);
        }

        return context;
    }
}
