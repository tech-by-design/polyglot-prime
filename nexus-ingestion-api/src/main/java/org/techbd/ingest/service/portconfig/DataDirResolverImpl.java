package org.techbd.ingest.service.portconfig;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.PortConfig.PortEntry;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

/**
 * Resolves S3 object keys (data and metadata) for both normal and {@code /hold}
 * routing paths. This component centralizes all logic for constructing
 * timestamped, directory-prefixed S3 keys to ensure consistent naming across
 * ingestion flows.
 *
 * <p>Responsibilities include:
 * <ul>
 *   <li>Applying {@code dataDir} and {@code metadataDir} prefixes from {@link PortEntry}</li>
 *   <li>Handling {@code /hold} versus non-hold routing</li>
 *   <li>Building timestamped filenames</li>
 *   <li>Generating date-based folder paths (yyyy/MM/dd)</li>
 * </ul>
 *
 * <h3>Sample Output</h3>
 * <pre>
 *  Normal mode:
 *      Data Key:     data/2025/12/02/ABC123_20251202T143000Z
 *      Metadata Key: metadata/2025/12/02/ABC123_20251202T143000Z_metadata.json
 *
 *  Hold mode (entry.route="/hold", port=9001):
 *      Data Key:     hold/9001/2025/12/02/20251202T143000Z_sample.xml
 *      Metadata Key: hold/9001/2025/12/02/20251202T143000Z_sample_metadata.json
 *
 *  Timestamped filename:
 *      Input file:   sample.xml
 *      Output:       20251202T143000Z_sample.xml
 *
 *  Date folder path:
 *      2025/12/02
 * </pre>
 */
@Component
class DataDirResolverImpl implements DataDirResolver {
private final TemplateLogger LOG;

    public DataDirResolverImpl(AppLogger appLogger) {
        this.LOG = appLogger.getLogger(DataDirResolverImpl.class);
    }
    /**
     * Builds the S3 key for the uploaded data object.
     *
     * <p><b>Examples:</b></p>
     * <pre>
     *  Normal mode:
     *     data/2025/12/02/ABC123_20251202T143000Z
     *
     *  Hold mode:
     *     hold/9001/2025/12/02/20251202T143000Z_sample.xml
     * </pre>
     *
     * If the port entry is marked as {@code /hold}, a hold-style key is created;
     * otherwise a standard ingestion key is created. After building the base key,
     * the port entry's {@code dataDir} prefix is applied when present.
     */
    @Override
    public String resolveDataKey(RequestContext context, PortEntry entry) {
        String baseKey = isHold(entry)
                ? buildHoldDataKey(context, entry)
                : buildNormalDataKey(context, entry);        
        String finalKey = applyPrefix(entry != null ? entry.dataDir : null, baseKey);
        LOG.info("[DATA_DIR_RESOLVER] Resolved Data Key: {} | route: {} | port: {} | fileName: {}",
                finalKey,
                entry != null ? entry.route : "null",
                entry != null ? entry.port : "null",
                context.getFileName());
        return finalKey;
    }

    /**
     * Builds the S3 key for the metadata JSON file associated with an upload.
     *
     * <p><b>Examples:</b></p>
     * <pre>
     *  Normal:
     *     metadata/2025/12/02/ABC123_20251202T143000Z_metadata.json
     *
     *  Hold:
     *     hold/9001/2025/12/02/20251202T143000Z_sample_metadata.json
     * </pre>
     */
    @Override
    public String resolveMetadataKey(RequestContext context, PortEntry entry) {
        String baseKey = isHold(entry)
                ? buildHoldMetadataKey(context, entry)
                : buildNormalMetadataKey(context, entry);
        String finalKey = applyPrefix(entry != null ? entry.metadataDir : null, baseKey);
        LOG.info("[DATA_DIR_RESOLVER] Resolved Metadata Key: {} | route: {} | port: {} | fileName: {}",
                finalKey,
                entry != null ? entry.route : "null",
                entry != null ? entry.port : "null",
                context.getFileName());
        return finalKey;
    }

    /**
     * Applies a directory prefix to a given S3 key, ensuring no double or trailing slashes.
     *
     * <p><b>Example:</b></p>
     * <pre>
     *   prefix:  "custom/prefix"
     *   key:     "data/2025/12/02/ABC123_20251202T143000Z"
     *
     *   Result → "custom/prefix/data/2025/12/02/ABC123_20251202T143000Z"
     * </pre>
     */
    private String applyPrefix(String prefix, String key) {
        if (prefix == null || prefix.isBlank()) {
            return key;
        }
        String cleaned = prefix.replaceAll("^/+", "").replaceAll("/+$", "");
        return cleaned.isEmpty() ? key : cleaned + "/" + key;
    }

    /**
     * Determines whether the port entry represents a {@code /hold} route.
     *
     * <p><b>Example:</b> entry.route="/hold" → true</p>
     */
    private boolean isHold(PortEntry entry) {
        return entry != null && "/hold".equals(entry.route);
    }

    /**
     * Constructs a timestamp-prepended filename based on the original upload name.
     *
     * <h4>Example:</h4>
     * <pre>
     *   fileName: "sample.xml"
     *   timestamp: "20251202T143000Z"
     *
     *   Output → "20251202T143000Z_sample.xml"
     * </pre>
     */
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

    /**
     * Produces a date-based folder structure {@code yyyy/MM/dd}.
     *
     * <h4>Example:</h4>
     * <pre>
     *   UploadTime: 2025-12-02T14:30:00Z
     *
     *   Output → "2025/12/02"
     * </pre>
     */
    private String datePath(RequestContext context) {
        ZonedDateTime uploadTime = context.getUploadTime();
        return uploadTime.format(Constants.DATE_PATH_FORMATTER);
    }

    /**
     * Builds the key for data files routed via {@code /hold}.
     *
     * <h4>Example:</h4>
     * <pre>
     *   hold/9001/2025/12/02/20251202T143000Z_sample.xml
     * </pre>
     */
    private String buildHoldDataKey(RequestContext context, PortEntry entry) {
        String datePath = datePath(context);
        String stampedName = buildTimestampedName(context.getFileName(), context.getTimestamp());
        return String.format("hold/%d/%s/%s", entry.port, datePath, stampedName);
    }

    /**
     * Builds the key for metadata files routed via {@code /hold}.
     *
     * <h4>Example:</h4>
     * <pre>
     *   hold/9001/2025/12/02/20251202T143000Z_sample_metadata.json
     * </pre>
     */
    private String buildHoldMetadataKey(RequestContext context, PortEntry entry) {
        String datePath = datePath(context);
        String stampedName = buildTimestampedName(context.getFileName(), context.getTimestamp());
        return String.format("hold/%d/%s/%s_metadata.json", entry.port, datePath, stampedName);
    }

    /**
     * Builds the standard ingestion data key (non-hold).
     *
     * <h4>Example:</h4>
     * <pre>
     *   data/2025/12/02/ABC123_20251202T143000Z
     * </pre>
     */
    private String buildNormalDataKey(RequestContext context, PortEntry entry) {
        String datePath = datePath(context);
        String interactionId = context.getInteractionId();
        return String.format("data/%s/%s_%s", datePath, interactionId, context.getTimestamp());
    }

    /**
     * Builds the standard ingestion metadata key (non-hold).
     *
     * <h4>Example:</h4>
     * <pre>
     *   metadata/2025/12/02/ABC123_20251202T143000Z_metadata.json
     * </pre>
     */
    private String buildNormalMetadataKey(RequestContext context, PortEntry entry) {
        String datePath = datePath(context);
        String interactionId = context.getInteractionId();
        return String.format(
                "metadata/%s/%s_%s_metadata.json",
                datePath, interactionId, context.getTimestamp()
        );
    }
}
