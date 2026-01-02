package org.techbd.ingest.exceptions;

import org.springframework.stereotype.Component;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.server.endpoint.SoapFaultMappingExceptionResolver;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.LogUtil;
import org.techbd.ingest.util.TemplateLogger;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Custom SOAP Fault Resolver that adds error trace ID to all SOAP faults,
 * including parsing errors that occur before reaching endpoint code.
 */
@Component
public class CustomSoapFaultResolver extends SoapFaultMappingExceptionResolver {

    private final TemplateLogger logger;

    public CustomSoapFaultResolver(AppLogger appLogger) {
        this.logger = appLogger.getLogger(CustomSoapFaultResolver.class);
        // Set order to handle faults before default resolver
        setOrder(1);
    }

    @Override
    protected void customizeFault(Object endpoint, Exception ex, SoapFault fault) {
        // Generate error trace ID
        String errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
        String interactionId = extractInteractionId();
        
        try {
            // Get fault message
            String faultMessage = fault.getFaultStringOrReason();
            
            // Enhance message for empty body errors
            if (ex.getMessage() != null && ex.getMessage().contains("Empty request body")) {
                faultMessage = "Empty request body - no SOAP message provided";
            }
            
            logger.error("CustomSoapFaultResolver:: SOAP Fault occurred. interactionId={}, errorTraceId={}, error={}", 
                    interactionId, errorTraceId, ex.getMessage(), ex);
            
            // Log detailed error to CloudWatch
            LogUtil.logDetailedError(
                400, // Use 400 for client errors like empty body
                "SOAP Fault: " + faultMessage, 
                interactionId, 
                errorTraceId, 
                ex
            );
            
            // Add error trace information to fault detail
            var faultDetail = fault.addFaultDetail();
            
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
            
            logger.info("CustomSoapFaultResolver:: Added error trace details to SOAP fault. interactionId={}, errorTraceId={}", 
                    interactionId, errorTraceId);
            
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
}