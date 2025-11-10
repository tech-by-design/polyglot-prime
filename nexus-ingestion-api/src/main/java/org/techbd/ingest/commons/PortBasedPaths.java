package org.techbd.ingest.commons;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
     * Mutable data class holding port-based path information.
     * Used internally during RequestContext building.
     */
    @Getter
    @Setter
    @AllArgsConstructor
    public class PortBasedPaths {
        String dataKey;
        String metaDataKey;
        String acknowledgementKey;
        String fullS3DataPath;
        String fullS3MetadataPath;
        String fullS3AcknowledgementPath;
        String dataBucketName;
        String metadataBucketName;
        String queue;
    }
