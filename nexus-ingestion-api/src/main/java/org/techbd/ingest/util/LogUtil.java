package org.techbd.ingest.util;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class LogUtil {

    private static final Logger LOG = LoggerFactory.getLogger(LogUtil.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private LogUtil() {
    }

    public static void logDetailedError(int code, String message, String interactionId, String errorTraceId,  Exception ex) {

        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode errorNode = root.putObject("error");

            errorNode.put("code", code);
            errorNode.put("message", message);
            errorNode.put("interactionId", interactionId);
            errorNode.put("errorTraceId", errorTraceId);
            errorNode.put("error", ex != null ? ex.toString() : null);

            StringWriter sw = new StringWriter();
            if (ex != null) {
                ex.printStackTrace(new PrintWriter(sw));
            }
            errorNode.put("stackTrace", sw.toString());

            // Single-line compact JSON
            String json = objectMapper.writeValueAsString(root);

            LOG.error(json);

        } catch (Exception loggingEx) {
            LOG.error("Failed to log structured error for traceId {}: {}", 
                    errorTraceId, loggingEx.getMessage(), loggingEx);
        }
    }
}

