package org.techbd.ingest.exceptions;

import org.springframework.stereotype.Component;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.server.endpoint.SoapFaultMappingExceptionResolver;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.LogUtil;
import org.techbd.ingest.util.TemplateLogger;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Custom SOAP Fault Resolver that adds error trace ID to all SOAP faults,
 * including MustUnderstand and other framework-level errors.
 * 
 * This resolver runs with order=0 to catch faults before other resolvers,
 * including framework-generated faults like MustUnderstand.
 */
@Component
public class CustomSoapFaultResolver extends SoapFaultMappingExceptionResolver {

    private final TemplateLogger logger;

    public CustomSoapFaultResolver(AppLogger appLogger) {
        this.logger = appLogger.getLogger(CustomSoapFaultResolver.class);
        // CRITICAL: Set order to 0 to handle faults before other resolvers
        // This ensures we catch MustUnderstand and other framework faults
        setOrder(0);
    }

    @Override
    protected void customizeFault(Object endpoint, Exception ex, SoapFault fault) {
        // Generate error trace ID
        String errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
        String interactionId = extractInteractionId();
        
        try {
            // Get fault message and code
            String faultMessage = fault.getFaultStringOrReason();
            String faultCode = fault.getFaultCode() != null ? fault.getFaultCode().toString() : "UNKNOWN";
            
            // Determine error code based on fault type
            int errorCode = 400; // Default to client error
            if (faultMessage != null) {
                if (faultMessage.contains("MustUnderstand")) {
                    errorCode = 400; // Client didn't handle required header
                    logger.warn("CustomSoapFaultResolver:: MustUnderstand fault detected. interactionId={}, errorTraceId={}", 
                            interactionId, errorTraceId);
                } else if (faultMessage.contains("Empty request body")) {
                    faultMessage = "Empty request body - no SOAP message provided";
                } else if (faultMessage.contains("Unable to create envelope")) {
                    faultMessage = "Invalid SOAP message format";
                }
            }
            
            logger.error("CustomSoapFaultResolver:: SOAP Fault occurred. interactionId={}, errorTraceId={}, faultCode={}, error={}", 
                    interactionId, errorTraceId, faultCode, ex != null ? ex.getMessage() : faultMessage);
            
            // Set ingestion failed flag in request context
            setIngestionFailedFlag(interactionId);
            
            // Log detailed error to CloudWatch
            LogUtil.logDetailedError(
                errorCode,
                "SOAP Fault [" + faultCode + "]: " + faultMessage, 
                interactionId, 
                errorTraceId, 
                ex != null ? ex : new Exception(faultMessage)
            );
            
            // Add error trace information to fault detail
            var faultDetail = fault.getFaultDetail();
            if (faultDetail == null) {
                faultDetail = fault.addFaultDetail();
            }
            
            // Add InteractionId
            var interactionIdElement = faultDetail.addFaultDetailElement(
                new javax.xml.namespace.QName("http://techbd.org/errorinfo", "InteractionId", "err")
            );
            interactionIdElement.addText(interactionId);
            
            // Add ErrorTraceId
            var errorTraceIdElement = faultDetail.addFaultDetailElement(
                new javax.xml.namespace.QName("http://techbd.org/errorinfo", "ErrorTraceId", "err")
            );
            errorTraceIdElement.addText(errorTraceId);
            
            logger.info("CustomSoapFaultResolver:: Added error trace details to SOAP fault. interactionId={}, errorTraceId={}, faultCode={}", 
                    interactionId, errorTraceId, faultCode);
            
        } catch (Exception e) {
            logger.error("CustomSoapFaultResolver:: Failed to customize SOAP fault. interactionId={}, errorTraceId={}, error={}", 
                    interactionId, errorTraceId, e.getMessage(), e);
        }
    }

    /**
     * Extract interaction ID from request context
     */
    private String extractInteractionId() {
        try {
            var transportContext = TransportContextHolder.getTransportContext();
            if (transportContext != null && transportContext.getConnection() instanceof HttpServletConnection) {
                var connection = (HttpServletConnection) transportContext.getConnection();
                HttpServletRequest httpRequest = connection.getHttpServletRequest();
                
                // Try to get from header first
                String interactionId = httpRequest.getHeader(Constants.HEADER_INTERACTION_ID);
                if (interactionId != null && !interactionId.isBlank()) {
                    return interactionId;
                }
                
                // Try to get from attribute
                Object attrInteractionId = httpRequest.getAttribute(Constants.INTERACTION_ID);
                if (attrInteractionId != null) {
                    return attrInteractionId.toString();
                }
            }
        } catch (Exception e) {
            logger.debug("CustomSoapFaultResolver:: Could not extract interaction ID: {}", e.getMessage());
        }
        
        // Return unknown if not found
        return "unknown";
    }

    /**
     * Set ingestion failed flag in request context
     * This ensures failed requests are properly marked
     */
    private void setIngestionFailedFlag(String interactionId) {
        try {
            var transportContext = TransportContextHolder.getTransportContext();
            if (transportContext != null && transportContext.getConnection() instanceof HttpServletConnection) {
                var connection = (HttpServletConnection) transportContext.getConnection();
                HttpServletRequest httpRequest = connection.getHttpServletRequest();
                
                // Get RequestContext and set failed flag
                RequestContext context = (RequestContext) httpRequest.getAttribute(Constants.REQUEST_CONTEXT);
                if (context != null) {
                    context.setIngestionFailed(true);
                    logger.info("CustomSoapFaultResolver:: Set ingestionFailed=true for interactionId={}", interactionId);
                } else {
                    logger.debug("CustomSoapFaultResolver:: RequestContext not yet available for interactionId={} (may be set later)", interactionId);
                }
            }
        } catch (Exception e) {
            logger.warn("CustomSoapFaultResolver:: Failed to set ingestionFailed flag: {}", e.getMessage());
        }
    }
}