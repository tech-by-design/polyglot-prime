package org.techbd.ingest.service.soap;

import java.io.ByteArrayOutputStream;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapMessage;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Default strategy — preserves the existing behaviour exactly.
 *
 * Spring-WS has already written the SOAP response; nothing extra is done here.
 * Content-Type remains {@code application/soap+xml} as set by the framework.
 */
public class DefaultSoapResponseStrategy implements SoapResponseStrategy {

    private final TemplateLogger log;

    public DefaultSoapResponseStrategy(AppLogger appLogger) {
        this.log = appLogger.getLogger(DefaultSoapResponseStrategy.class);
    }

    @Override
    public byte[] writeResponse(
            String interactionId,
            MessageContext messageContext,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            SoapMessage builtResponse) {

        // Intentionally a no-op: Spring-WS handles serialisation in its normal
        // pipeline.
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            builtResponse.writeTo(baos);

            byte[] bytes = baos.toByteArray();

            log.debug("DefaultSoapResponseStrategy:: SOAP response captured. size={} interactionId={}",
                    bytes.length, interactionId);

            return bytes;

        } catch (Exception e) {
            log.error("DefaultSoapResponseStrategy:: Failed to serialize SOAP response. interactionId={} error={}",
                    interactionId, e.getMessage(), e);

            throw new RuntimeException("Failed to serialize SOAP response", e);
        }
    }
}