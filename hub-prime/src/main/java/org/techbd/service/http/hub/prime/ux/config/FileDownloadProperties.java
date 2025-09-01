package org.techbd.service.http.hub.prime.ux.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "org.techbd.service.http.hub.prime")
public class FileDownloadProperties {
    /**
     * Maximum allowed file content size in MB for JSON pretty-printing (default 1MB).
     */
    private int maxDownloadJsonPrettyPrintSizeMB = 1;

    public int getMaxDownloadJsonPrettyPrintSizeMB() {
        return maxDownloadJsonPrettyPrintSizeMB;
    }

    public void setMaxDownloadJsonPrettyPrintSizeMB(int maxDownloadJsonPrettyPrintSizeMB) {
        this.maxDownloadJsonPrettyPrintSizeMB = maxDownloadJsonPrettyPrintSizeMB;
    }

    /**
     * Returns the max JSON pretty-print size in bytes.
     */
    public int getMaxDownloadJsonPrettyPrintSizeBytes() {
        return maxDownloadJsonPrettyPrintSizeMB * 1024 * 1024;
    }
}
