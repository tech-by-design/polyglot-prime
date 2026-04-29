package org.techbd.ingest.service.soap;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapMessage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Strategy for writing the SOAP response depending on the requested wire format.
 *
 * Implementations:
 *   - DefaultSoapResponseStrategy   → application/soap+xml  (existing behaviour)
 *   - MtomSoapResponseStrategy      → multipart/related MTOM
 *   - TruBridgeMtomSoapResponseStrategy → TruBridge MTOM wire format
 */
public interface SoapResponseStrategy {

    /**
     * Write (or mutate) the outbound SOAP response.
     *
     * @param interactionId  correlation ID for logging
     * @param messageContext Spring-WS message context (request + response in-flight)
     * @param httpRequest    raw servlet request (headers, attributes)
     * @param httpResponse   raw servlet response (may be used to set headers directly)
     * @param builtResponse  the SOAP message already built by SoapResponseUtil.buildSoapResponse()
     */
    byte[] writeResponse(
            String interactionId,
            MessageContext messageContext,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            SoapMessage builtResponse);
}