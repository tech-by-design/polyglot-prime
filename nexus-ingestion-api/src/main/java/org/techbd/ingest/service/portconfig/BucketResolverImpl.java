package org.techbd.ingest.service.portconfig;

import org.springframework.stereotype.Component;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig.PortEntry;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

/**
 * Resolves the appropriate S3 buckets (data and metadata) for a given
 * {@link PortEntry}, based on routing rules and application configuration.
 *
 * <p>This implementation encapsulates all bucket-selection logic tied to the
 * {@code /hold} route. If a port entry is routed through {@code /hold},
 * the resolver returns the S3 bucket names from the application's
 * "hold" configuration; otherwise, it falls back to the default S3
 * configuration.
 */
@Component
class BucketResolverImpl implements PortConfigAttributeResolver {

    private final AppConfig appConfig;
    private final TemplateLogger LOG;

    public BucketResolverImpl(AppConfig appConfig, AppLogger appLogger) {
        this.appConfig = appConfig;
        this.LOG = appLogger.getLogger(BucketResolverImpl.class);
    }

    /**
     * Resolves and applies S3 bucket names to the request context.
     *
     * @param context the request context to update
     * @param entry the port entry whose routing rules determine bucket selection
     * @param interactionId the interaction ID for logging
     */
    @Override
    public void resolve(RequestContext context, PortEntry entry, String interactionId) {
        String dataBucket = resolveDataBucket(entry, interactionId);
        if (dataBucket != null && !dataBucket.equals(context.getDataBucketName())) {
            context.setDataBucketName(dataBucket);
            LOG.debug("[BUCKET_RESOLVER] Data bucket updated to: {} interactionId={}", dataBucket, interactionId);
        }

        String metadataBucket = resolveMetadataBucket(entry, interactionId);
        if (metadataBucket != null && !metadataBucket.equals(context.getMetaDataBucketName())) {
            context.setMetaDataBucketName(metadataBucket);
            LOG.debug("[BUCKET_RESOLVER] Metadata bucket updated to: {} interactionId={}", metadataBucket,
                    interactionId);
        }
    }

    /**
     * Resolves which S3 data bucket should be used for the given port entry.
     *
     * @param entry the port entry whose routing rules determine the bucket selection
     * @param interactionId the interaction ID for logging
     * @return the resolved S3 data bucket name
     */
    private String resolveDataBucket(PortEntry entry, String interactionId) {
        String bucket;
        if (entry != null && "/hold".equals(entry.route)) {
            bucket = appConfig.getAws().getS3().getHoldConfig().getBucket();
        } else {
            bucket = appConfig.getAws().getS3().getDefaultConfig().getBucket();
        }
        LOG.info("[BUCKET_RESOLVER] Resolved data bucket: {} for route: {}, interactionId: {}", bucket,
                entry != null ? entry.route : "null", interactionId);
        return bucket;
    }

    /**
     * Resolves which S3 metadata bucket should be used for the given port entry.
     *
     * @param entry the port entry used to determine routing and bucket selection
     * @param interactionId the interaction ID for logging
     * @return the resolved S3 metadata bucket name
     */
    private String resolveMetadataBucket(PortEntry entry, String interactionId) {
        String bucket;
        if (entry != null && "/hold".equals(entry.route)) {
            bucket = appConfig.getAws().getS3().getHoldConfig().getMetadataBucket();
        } else {
            bucket = appConfig.getAws().getS3().getDefaultConfig().getMetadataBucket();
        }
        LOG.info("[BUCKET_RESOLVER] Resolved metadata bucket: {} for route: {}, interactionId: {}", bucket,
                entry != null ? entry.route : "null", interactionId);
        return bucket;
    }
}
