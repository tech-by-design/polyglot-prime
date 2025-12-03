package org.techbd.ingest.service.portconfig;

import org.springframework.stereotype.Component;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig.PortEntry;
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
 *
 * <p>The resolver interacts with {@link AppConfig} to ensure that bucket-related
 * configuration is centralized.
 */
@Component
class BucketResolverImpl implements BucketResolver {

    private final AppConfig appConfig;
    private final TemplateLogger LOG;

    public BucketResolverImpl(AppConfig appConfig, AppLogger appLogger) {
        this.appConfig = appConfig;
        this.LOG = appLogger.getLogger(BucketResolverImpl.class);
    }

    /**
     * Resolves which S3 data bucket should be used for the given port entry.
     *
     * <p>If the entry's route matches {@code "/hold"}, the bucket defined in
     * {@code holdConfig} is returned. Otherwise, the bucket from the default S3
     * configuration is selected.
     *
     * <p>Logs the resolved bucket for traceability.
     *
     * @param entry the port entry whose routing rules determine the bucket selection;
     *              may be {@code null}
     * @param interactionId the interaction ID associated with the request
     * @return the resolved S3 data bucket name; never {@code null}
     */
    @Override
    public String resolveDataBucket(PortEntry entry,String interactionId) {
        String bucket;
        if (entry != null && "/hold".equals(entry.route)) {
            bucket = appConfig.getAws().getS3().getHoldConfig().getBucket();
        } else {
            bucket = appConfig.getAws().getS3().getDefaultConfig().getBucket();
        }
        LOG.info("[S3_BUCKET_RESOLVER] Resolved data bucket: {} for route: {}, interactionId: {}", bucket, entry != null ? entry.route : "null", interactionId);
        return bucket;
    }

    /**
     * Resolves which S3 metadata bucket should be used for the given port entry.
     *
     * <p>Similar to {@link #resolveDataBucket(PortEntry)}, routing through
     * {@code "/hold"} causes the resolver to return the "hold" metadata bucket.
     * Otherwise, the default metadata bucket is returned.
     *
     * <p>Logs the resolved bucket for traceability.
     *
     * @param entry the port entry used to determine routing and bucket selection;
     *              may be {@code null}
     * @param interactionId the interaction ID associated with the request
     * @return the resolved S3 metadata bucket name; never {@code null}
     */
    @Override
    public String resolveMetadataBucket(PortEntry entry,String interactionId) {
        String bucket;
        if (entry != null && "/hold".equals(entry.route)) {
            bucket = appConfig.getAws().getS3().getHoldConfig().getMetadataBucket();
        } else {
            bucket = appConfig.getAws().getS3().getDefaultConfig().getMetadataBucket();
        }
        LOG.info("[S3_BUCKET_RESOLVER] Resolved metadata bucket: {} for route: {}, interactionId: {}", bucket, entry != null ? entry.route : "null", interactionId);
        return bucket;
    }
}
