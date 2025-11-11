package org.techbd.ingest.util;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.ingest.commons.PortBasedPaths;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.config.PortConfig.PortEntry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Utility class for accessing port configuration entries in a safe and
 * consistent way.
 */
@Component
public class PortConfigUtil {

    private static final Logger log = LoggerFactory.getLogger(PortConfigUtil.class);

    private final PortConfig portConfig;
    private final AppConfig appConfig;

    public PortConfigUtil(PortConfig portConfig, AppConfig appConfig) {
        this.portConfig = portConfig;
        this.appConfig = appConfig;
    }

    /**
     * Reads the PortEntry configuration for a given port.
     *
     * @param port the port number to look up
     * @return Optional containing the PortEntry if found, otherwise empty
     */
    public Optional<PortEntry> readPortEntry(int port, String interactionId) {
        if (port <= 0) {
            log.warn("PortConfigUtil: Invalid port number {} interactionId={}", port, interactionId);
            return Optional.empty();
        }

        try {
            Optional<PortEntry> entry = portConfig.findEntryForPort(port);
            if (entry.isPresent()) {
                try {
                    ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
                    String json = mapper.writeValueAsString(entry.get());
                    log.info("[PORT_CONFIG_MATCH]  Found PortEntry for port={} interactionId={} \n{}",
                            port, interactionId, json);
                } catch (Exception e) {
                    log.error("[PORT_CONFIG_ERROR] Failed to serialize PortEntry for port={} interactionId={}",
                            port, interactionId, e);
                }
            } else {
                log.warn("[PORT_CONFIG_ERROR] : No configuration found for port {} interactionId={}", port, interactionId);
            }
            return entry;
        } catch (Exception e) {
            log.error("[PORT_CONFIG_ERROR] Error: {} reading PortEntry for port {} interactionId={}", e.getMessage(), port,
                    interactionId, e);
            return Optional.empty();
        }
    }

    public boolean validatePortEntry(Optional<PortConfig.PortEntry> portEntryOpt, int destinationPort,
            String interactionId) {
        if (portEntryOpt == null || !portEntryOpt.isPresent()) {
            log.error("PortConfigUtil: No port configuration entry found for destination port: {}, interactionId={}",
                    destinationPort, interactionId);
            return false;
        }
        log.info("PortConfigUtil: Port configuration found for destination port: {}, interactionId={}",
                destinationPort, interactionId);
        return true;
    }

    /**
     * Resolve S3 paths based on port configuration (similar to PixEndpoint logic).
     * Strategy Pattern: Different path resolution strategies based on route
     * configuration.
     */
    public PortBasedPaths resolvePortBasedPaths(Optional<PortConfig.PortEntry> portEntryOpt,
            String interactionId, Map<String, String> headers,
            String originalFileName, String timestamp, String datePath) {
        // Determine buckets based on route
        boolean isHoldRoute = portEntryOpt.map(pe -> "/hold".equals(pe.route)).orElse(false);

        String dataBucketName;
        String metadataBucketName;

        if (isHoldRoute) {
            dataBucketName = appConfig.getAws().getS3().getHoldConfig().getBucket();
            metadataBucketName = appConfig.getAws().getS3().getHoldConfig().getMetadataBucket();
        } else {
            dataBucketName = appConfig.getAws().getS3().getDefaultConfig().getBucket();
            metadataBucketName = appConfig.getAws().getS3().getDefaultConfig().getMetadataBucket();
        }

        // Determine queue: use port config queue if present, otherwise use default
        // queue
        String queue = portEntryOpt
                .map(pe -> pe.queue)
                .filter(q -> q != null && !q.isBlank())
                .orElse(appConfig.getAws().getSqs().getFifoQueueUrl());

        // Build base keys
        String baseDataKey;
        String baseMetadataKey;

        if (isHoldRoute && portEntryOpt.isPresent()) {
            int portNum = portEntryOpt.get().port;
            String timestampedName = buildTimestampedFileName(originalFileName, timestamp);
            baseDataKey = String.format("hold/%d/%s/%s", portNum, datePath, timestampedName);
            baseMetadataKey = String.format("hold/%d/%s/%s_metadata.json", portNum, datePath, timestampedName);
        } else {
            baseDataKey = String.format("data/%s/%s_%s", datePath, interactionId, timestamp);
            baseMetadataKey = String.format("metadata/%s/%s_%s_metadata.json", datePath, interactionId, timestamp);
        }

        // Apply directory prefixes from port configuration
        if (portEntryOpt.isPresent()) {
            PortConfig.PortEntry entry = portEntryOpt.get();

            if (entry.dataDir != null && !entry.dataDir.isBlank()) {
                String prefix = entry.dataDir.replaceAll("^/+", "").replaceAll("/+$", "");
                if (!prefix.isEmpty()) {
                    baseDataKey = prefix + "/" + baseDataKey;
                }
            }

            if (entry.metadataDir != null && !entry.metadataDir.isBlank()) {
                String prefix = entry.metadataDir.replaceAll("^/+", "").replaceAll("/+$", "");
                if (!prefix.isEmpty()) {
                    baseMetadataKey = prefix + "/" + baseMetadataKey;
                }
            }
        }

        String acknowledgementKey = baseDataKey + "_acknowledgement";
        String fullS3DataPath = "s3://" + dataBucketName + "/" + baseDataKey;
        String fullS3MetadataPath = "s3://" + metadataBucketName + "/" + baseMetadataKey;
        String fullS3AcknowledgementPath = "s3://" + dataBucketName + "/" + acknowledgementKey;

        return new PortBasedPaths(
                baseDataKey,
                baseMetadataKey,
                acknowledgementKey,
                fullS3DataPath,
                fullS3MetadataPath,
                fullS3AcknowledgementPath,
                dataBucketName,
                metadataBucketName,
                queue);
    }

    /**
     * Build timestamped filename for hold route.
     */
    private String buildTimestampedFileName(String originalFileName, String timestamp) {
        String original = (originalFileName == null || originalFileName.isBlank()) ? "body" : originalFileName;
        String baseName = original;
        String extension = "";

        int lastDot = original.lastIndexOf('.');
        if (lastDot > 0 && lastDot < original.length() - 1) {
            baseName = original.substring(0, lastDot);
            extension = original.substring(lastDot + 1);
        }

        String timestampedName = timestamp + "_" + baseName;
        if (!extension.isBlank()) {
            timestampedName = timestampedName + "." + extension;
        }

        return timestampedName;
    }

}
