package org.techbd.ingest.util;

import java.util.Map;

import org.springframework.http.MediaType;
import org.techbd.ingest.commons.Constants;

public class HttpUtil {
    /**
     * Resolve file extension based on Content-Type.
     */
    public static String resolveExtension(String contentType) {
        if (contentType == null)
            return ".dat";

        return switch (contentType.toLowerCase()) {
            case MediaType.APPLICATION_JSON_VALUE -> ".json";
            case MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE -> ".xml";
            case MediaType.TEXT_PLAIN_VALUE -> ".txt";
            case "application/hl7-v2" -> ".hl7";
            default -> ".dat"; // fallback
        };
    }

    public static String extractTenantId(Map<String, String> headers) {
        var tenantId = headers.get(Constants.REQ_HEADER_TENANT_ID);
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = Constants.TENANT_ID;
        }
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = Constants.DEFAULT_TENANT_ID;
        }
        return tenantId;
    }

    public static String extractSourceIp(Map<String, String> headers) {
        final var xForwardedFor = headers.get(Constants.REQ_HEADER_X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return headers.get(Constants.REQ_HEADER_X_REAL_IP);
    }

    public static String extractDestinationIp(Map<String, String> headers) {
        return headers.get(Constants.REQ_X_SERVER_IP);
    }

    public static String extractDestinationPort(Map<String, String> headers) {
        return headers.get(Constants.REQ_X_SERVER_PORT);
    }
}
