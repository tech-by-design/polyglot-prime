package org.techbd.ingest.service.portconfig;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.PortConfig.PortEntry;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

/**
 * Resolves S3 object keys (data, metadata, and ack) for both normal and {@code /hold}
 * routing paths. This component centralizes all logic for constructing
 * timestamped, directory-prefixed S3 keys with tenant ID support to ensure 
 * consistent naming across ingestion flows.
 *
 * <p>Responsibilities include:
 * <ul>
 *   <li>Resolving tenant ID from sourceId and messageType</li>
 *   <li>Applying {@code dataDir} and {@code metadataDir} prefixes from {@link PortEntry}</li>
 *   <li>Handling {@code /hold} versus non-hold routing</li>
 *   <li>Building timestamped filenames</li>
 *   <li>Generating date-based folder paths (yyyy/MM/dd)</li>
 * </ul>
 *
 * <h3>Sample Output</h3>
 * <pre>
 *  Normal mode with tenant:
 *      Data Key:     data/netspective_pnr/2025/12/02/ABC123_20251202T143000Z
 *      Metadata Key: metadata/netspective_pnr/2025/12/02/ABC123_20251202T143000Z_metadata.json
 *
 *  Normal mode without tenant:
 *      Data Key:     data/2025/12/02/ABC123_20251202T143000Z
 *      Metadata Key: metadata/2025/12/02/ABC123_20251202T143000Z_metadata.json
 *
 *  Hold mode with tenant (entry.route="/hold"):
 *      Data Key:     hold/netspective_pnr/2025/12/02/20251202T143000Z_sample.xml
 *      Metadata Key: hold/netspective_pnr/2025/12/02/20251202T143000Z_sample_metadata.json
 * </pre>
 */
@Component
class DataDirResolverImpl implements PortConfigAttributeResolver {
    private final TemplateLogger LOG;

    public DataDirResolverImpl(AppLogger appLogger) {
        this.LOG = appLogger.getLogger(DataDirResolverImpl.class);
    }

    /**
     * Resolves and applies S3 object keys to the request context.
     *
     * @param context the request context to update
     * @param entry the port entry configuration
     * @param interactionId the interaction ID for logging
     */
    @Override
    public void resolve(RequestContext context, PortEntry entry, String interactionId) {
        String objectKey = resolveDataKey(context, entry);
        if (objectKey != null && !objectKey.equals(context.getObjectKey())) {
            context.setObjectKey(objectKey);
            LOG.info("[DATA_DIR_RESOLVER] Resolved Data Key: {} interactionId={}", objectKey, interactionId);
        }

        String metadataKey = resolveMetadataKey(context, entry);
        if (metadataKey != null && !metadataKey.equals(context.getMetadataKey())) {
            context.setMetadataKey(metadataKey);
            LOG.info("[DATA_DIR_RESOLVER] Resolved Metadata Key: {} interactionId={}", metadataKey, interactionId);
        }

        String ackObjectKey = resolveAckObjectKey(context, entry);
        if (ackObjectKey != null && !ackObjectKey.equals(context.getAckObjectKey())) {
            context.setAckObjectKey(ackObjectKey);
            LOG.info("[DATA_DIR_RESOLVER] Resolved Ack Object Key: {} interactionId={}", ackObjectKey,
                    interactionId);
        }
    }

    /**
     * Builds the S3 key for the uploaded data object.
     *
     * @param context the request context
     * @param entry the port entry configuration
     * @return the resolved data key
     */
    private String resolveDataKey(RequestContext context, PortEntry entry) {
        String baseKey = isHold(entry)
                ? buildHoldDataKey(context, entry)
                : buildNormalDataKey(context, entry);
        String finalKey = applyPrefix(entry != null ? entry.dataDir : null, baseKey);
        LOG.debug("[DATA_DIR_RESOLVER] Resolved Data Key: {} | route: {} | port: {} | fileName: {} | tenantId: {}",
                finalKey,
                entry != null ? entry.route : "null",
                entry != null ? entry.port : "null",
                context.getFileName(),
                resolveTenantId(context));
        return finalKey;
    }

    /**
     * Builds the S3 key for the metadata JSON file.
     *
     * @param context the request context
     * @param entry the port entry configuration
     * @return the resolved metadata key
     */
    private String resolveMetadataKey(RequestContext context, PortEntry entry) {
        String baseKey = isHold(entry)
                ? buildHoldMetadataKey(context, entry)
                : buildNormalMetadataKey(context, entry);
        String finalKey = applyPrefix(entry != null ? entry.metadataDir : null, baseKey);
        LOG.debug("[DATA_DIR_RESOLVER] Resolved Metadata Key: {} | route: {} | port: {} | fileName: {} | tenantId: {}",
                finalKey,
                entry != null ? entry.route : "null",
                entry != null ? entry.port : "null",
                context.getFileName(),
                resolveTenantId(context));
        return finalKey;
    }

    /**
     * Builds the S3 key for the acknowledgment file.
     *
     * @param context the request context
     * @param entry the port entry configuration
     * @return the resolved ack object key
     */
    private String resolveAckObjectKey(RequestContext context, PortEntry entry) {
        return resolveDataKey(context, entry) + "_ack";
    }

    /**
     * Resolves the tenant ID from sourceId and messageType.
     *
     * @param context the request context
     * @return the tenant ID, or null if both fields are missing
     */
    private String resolveTenantId(RequestContext context) {
        String sourceId = context.getSourceId();
        String messageType = context.getMsgType();

        boolean hasSource = sourceId != null && !sourceId.isBlank();
        boolean hasMsgType = messageType != null && !messageType.isBlank();

        if (hasSource && hasMsgType) {
            return sourceId + "_" + messageType;
        } else if (hasSource) {
            return sourceId;
        } else if (hasMsgType) {
            return messageType;
        } else {
            return null;
        }
    }

    private String applyPrefix(String prefix, String key) {
        if (prefix == null || prefix.isBlank()) {
            return key;
        }
        String cleaned = prefix.replaceAll("^/+", "").replaceAll("/+$", "");
        return cleaned.isEmpty() ? key : cleaned + "/" + key;
    }

    private boolean isHold(PortEntry entry) {
        return entry != null && "/hold".equals(entry.route);
    }

    private String buildTimestampedName(String fileName, String timestamp) {
        String original = (fileName == null || fileName.isBlank()) ? "body" : fileName;

        String baseName = original;
        String extension = "";
        int lastDot = original.lastIndexOf('.');

        if (lastDot > 0 && lastDot < original.length() - 1) {
            baseName = original.substring(0, lastDot);
            extension = original.substring(lastDot + 1);
        }

        String timestamped = timestamp + "_" + baseName;
        return extension.isBlank() ? timestamped : timestamped + "." + extension;
    }

    private String datePath(RequestContext context) {
        ZonedDateTime uploadTime = context.getUploadTime();
        return uploadTime.format(Constants.DATE_PATH_FORMATTER);
    }

    private String buildHoldDataKey(RequestContext context, PortEntry entry) {
        String datePath = datePath(context);
        String stampedName = buildTimestampedName(context.getFileName(), context.getTimestamp());
        String tenantId = resolveTenantId(context);

        if (tenantId != null) {
            return String.format("hold/%s/%s/%s", tenantId, datePath, stampedName);
        } else {
            return String.format("hold/%d/%s/%s", entry.port, datePath, stampedName);
        }
    }

    private String buildHoldMetadataKey(RequestContext context, PortEntry entry) {
        String datePath = datePath(context);
        String stampedName = buildTimestampedName(context.getFileName(), context.getTimestamp());
        String tenantId = resolveTenantId(context);

        if (tenantId != null) {
            return String.format("hold/metadata/%s/%s/%s_metadata.json", tenantId, datePath, stampedName);
        } else {
            return String.format("hold/metadata/%d/%s/%s_metadata.json", entry.port, datePath, stampedName);
        }
    }

    private String buildNormalDataKey(RequestContext context, PortEntry entry) {
        String datePath = datePath(context);
        String interactionId = context.getInteractionId();
        String tenantId = resolveTenantId(context);

        if (tenantId != null) {
            return String.format("data/%s/%s/%s_%s", tenantId, datePath, interactionId, context.getTimestamp());
        } else {
            return String.format("data/%s/%s_%s", datePath, interactionId, context.getTimestamp());
        }
    }

    private String buildNormalMetadataKey(RequestContext context, PortEntry entry) {
        String datePath = datePath(context);
        String interactionId = context.getInteractionId();
        String tenantId = resolveTenantId(context);

        if (tenantId != null) {
            return String.format(
                    "metadata/%s/%s/%s_%s_metadata.json",
                    tenantId, datePath, interactionId, context.getTimestamp());
        } else {
            return String.format(
                    "metadata/%s/%s_%s_metadata.json",
                    datePath, interactionId, context.getTimestamp());
        }
    }
}