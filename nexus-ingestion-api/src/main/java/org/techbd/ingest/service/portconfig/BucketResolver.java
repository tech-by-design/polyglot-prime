package org.techbd.ingest.service.portconfig;

import org.techbd.ingest.config.PortConfig.PortEntry;

/**
 * Resolves the appropriate S3 buckets (data and metadata) for an incoming request.
 * <p>
 * Implementations determine bucket selection based on the matched {@link PortEntry}
 * configuration — for example, using alternate buckets when the route indicates
 * a "/hold" workflow.
 * </p>
 */
public interface BucketResolver {

    /**
     * Resolves the S3 bucket where the main data payload should be stored.
     *
     * @param entry the resolved {@link PortEntry} for the request; may be {@code null}
     *              if no configuration matched
     * @param interactionId the interaction ID associated with the request
     * @return the name of the S3 bucket that should receive the data payload
     */
    String resolveDataBucket(PortEntry entry,String interactionId);

    /**
     * Resolves the S3 bucket where metadata for the request should be stored.
     *
     * @param entry the resolved {@link PortEntry} for the request; may be {@code null}
     *              if no configuration matched
     * @param interactionId the interaction ID associated with the request
     * @return the name of the S3 metadata bucket associated with the request
     */
    String resolveMetadataBucket(PortEntry entry,String interactionId);
}
