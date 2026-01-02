package org.techbd.ingest.interceptors;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.server.SoapEndpointInterceptor;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.exceptions.ErrorTraceIdGenerator;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.Hl7Util;
import org.techbd.ingest.util.LogUtil;
import org.techbd.ingest.util.SoapResponseUtil;
import org.techbd.ingest.util.TemplateLogger;
import jakarta.servlet.http.HttpServletRequest;

public class WsaHeaderInterceptor implements EndpointInterceptor, SoapEndpointInterceptor {

    private final SoapResponseUtil soapResponseUtil;
    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private final TemplateLogger LOG;

    public WsaHeaderInterceptor(SoapResponseUtil soapResponseUtil,
                                MessageProcessorService messageProcessorService,
                                AppConfig appConfig,
                                AppLogger appLogger) {
        this.soapResponseUtil = soapResponseUtil;
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.LOG = appLogger.getLogger(WsaHeaderInterceptor.class);
    }

    /**
     * Checks if the current request is targeting the /ws endpoint.
     * Only /ws requests should be processed by this interceptor.
     */
    private boolean isWsEndpoint(MessageContext messageContext) {
        try {
            var transportContext = TransportContextHolder.getTransportContext();
            if (transportContext != null && transportContext.getConnection() instanceof HttpServletConnection) {
                var connection = (HttpServletConnection) transportContext.getConnection();
                HttpServletRequest httpRequest = connection.getHttpServletRequest();
                String requestUri = httpRequest.getRequestURI();
                boolean isWs = "/ws".equals(requestUri);
                
                if (!isWs) {
                    LOG.debug("Skipping interceptor processing for non-/ws endpoint: {}", requestUri);
                }
                
                return isWs;
            }
        } catch (Exception e) {
            LOG.warn("Error checking request URI: {}", e.getMessage());
        }
        return false;
    }

    @Override
    public boolean handleRequest(MessageContext messageContext, Object endpoint) throws Exception {
        // Only process if request is to /ws endpoint
        if (!isWsEndpoint(messageContext)) {
            return true; // Continue chain but skip processing
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        messageContext.getRequest().writeTo(out);
        String soapXml = out.toString(StandardCharsets.UTF_8);
        messageContext.setProperty(Constants.RAW_SOAP_ATTRIBUTE, soapXml);

        String interactionId = (String) messageContext.getProperty(Constants.INTERACTION_ID);
        LOG.info("handleRequest: Captured SOAP request for /ws endpoint. interactionId={}", interactionId);

        return true;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext, Object endpoint) throws Exception {
        // Only process if request is to /ws endpoint
        if (!isWsEndpoint(messageContext)) {
            return true; // Continue chain but skip processing
        }

        var transportContext = TransportContextHolder.getTransportContext();
        var connection = (HttpServletConnection) transportContext.getConnection();
        HttpServletRequest httpRequest = connection.getHttpServletRequest();
        String interactionId = (String) httpRequest.getAttribute(Constants.INTERACTION_ID);

        SoapMessage message = soapResponseUtil.buildSoapResponse(interactionId, messageContext);
        RequestContext context = (RequestContext) httpRequest.getAttribute(Constants.REQUEST_CONTEXT);
        String rawSoapMessage = (String) messageContext.getProperty(Constants.RAW_SOAP_ATTRIBUTE);

        messageContext.setProperty(Constants.INTERACTION_ID, interactionId);
        LOG.info("handleResponse: Processing SOAP response for /ws endpoint. interactionId={}", interactionId);

        messageProcessorService.processMessage(context, rawSoapMessage,
                Hl7Util.soapMessageToString(message, interactionId));

        return true;
    }

    @Override
    public boolean handleFault(MessageContext messageContext, Object endpoint) throws Exception {
        // Only process if request is to /ws endpoint
        if (!isWsEndpoint(messageContext)) {
            return true; // Continue chain but skip processing
        }

        String interactionId = (String) messageContext.getProperty(Constants.INTERACTION_ID);
        String errorTraceId = null;

        try {
            SoapMessage faultMessage = (SoapMessage) messageContext.getResponse();
            if (faultMessage != null) {
                // Generate error trace ID for SOAP fault
                errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
                
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                faultMessage.writeTo(out);
                String faultXml = out.toString(StandardCharsets.UTF_8);
                
                // Store error trace ID in message context for potential use
                messageContext.setProperty(Constants.ERROR_TRACE_ID, errorTraceId);
                
                // Log detailed error with structured format
                LOG.error("handleFault: SOAP Fault encountered for /ws endpoint. interactionId={}, errorTraceId={}, fault={}", 
                         interactionId, errorTraceId, faultXml);
                
                // Create exception from fault for detailed logging
                Exception faultException = new Exception("SOAP Fault: " + extractFaultString(faultXml));
                
                // Log to CloudWatch with full details
                LogUtil.logDetailedError(
                    500, 
                    "SOAP Fault encountered", 
                    interactionId, 
                    errorTraceId, 
                    faultException
                );
                
                // Try to inject error trace ID into SOAP fault message
                try {
                    injectErrorTraceIdIntoFault(faultMessage, interactionId, errorTraceId);
                } catch (Exception e) {
                    LOG.warn("Failed to inject errorTraceId into SOAP fault: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            // Generate error trace ID if not already generated
            if (errorTraceId == null) {
                errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
            }
            
            LOG.error("handleFault: Exception while processing SOAP fault for /ws endpoint. interactionId={}, errorTraceId={}, error={}", 
                      interactionId, errorTraceId, e.getMessage(), e);
            
            // Log to CloudWatch with full details
            LogUtil.logDetailedError(
                500, 
                "Exception while processing SOAP fault", 
                interactionId, 
                errorTraceId, 
                e
            );
        }

        return true; // continue interceptor chain
    }

    @Override
    public void afterCompletion(MessageContext messageContext, Object endpoint, Exception ex) throws Exception {
        // Only process if request is to /ws endpoint
        if (!isWsEndpoint(messageContext)) {
            return; // Skip processing
        }

        String interactionId = (String) messageContext.getProperty(Constants.INTERACTION_ID);
        String status = (ex == null) ? "SUCCESS" : "FAILURE";

        if (ex != null) {
            // Generate error trace ID for exceptions in afterCompletion
            String errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
            
            LOG.error(
                    "afterCompletion: SOAP interaction completed with ERROR for /ws endpoint. interactionId={}, errorTraceId={}, errorMessage={}, stackTrace={}",
                    interactionId, errorTraceId, ex.getMessage(), ex);
            
            // Log to CloudWatch with full details
            LogUtil.logDetailedError(
                500, 
                "SOAP interaction completed with error", 
                interactionId, 
                errorTraceId, 
                ex
            );
        } else {
            LOG.info("afterCompletion: SOAP interaction completed for /ws endpoint. interactionId={}, status={}", 
                    interactionId, status);
        }
    }

    @Override
    public boolean understands(SoapHeaderElement header) {
        String namespaces = appConfig.getSoap().getWsa().getUnderstoodNamespaces();
        if (namespaces == null || namespaces.isEmpty()) {
            return false;
        }
        Set<String> namespaceSet = Arrays.stream(namespaces.split(","))
                                         .map(String::trim)
                                         .collect(Collectors.toSet());
        return namespaceSet.contains(header.getName().getNamespaceURI());
    }

    /**
     * Extracts the fault string from SOAP fault XML
     */
    private String extractFaultString(String faultXml) {
        try {
            // Simple extraction - look for faultstring content
            int startIdx = faultXml.indexOf("<faultstring>");
            int endIdx = faultXml.indexOf("</faultstring>");
            if (startIdx != -1 && endIdx != -1) {
                return faultXml.substring(startIdx + 13, endIdx);
            }
            
            // Try SOAP 1.2 format
            startIdx = faultXml.indexOf("<soap:Text");
            if (startIdx != -1) {
                startIdx = faultXml.indexOf(">", startIdx) + 1;
                endIdx = faultXml.indexOf("</soap:Text>", startIdx);
                if (endIdx != -1) {
                    return faultXml.substring(startIdx, endIdx);
                }
            }
            
            return "Unknown fault";
        } catch (Exception e) {
            return "Failed to extract fault string";
        }
    }

    /**
     * Injects error trace ID into SOAP fault message as a detail element
     */
    private void injectErrorTraceIdIntoFault(SoapMessage faultMessage, String interactionId, String errorTraceId) {
        try {
            var soapBody = faultMessage.getSoapBody();
            var fault = soapBody.getFault();
            
            if (fault != null) {
                var faultDetail = fault.getFaultDetail();
                if (faultDetail == null) {
                    faultDetail = fault.addFaultDetail();
                }
                
                // Add InteractionId as a detail element
                var interactionIdElement = faultDetail.addFaultDetailElement(
                    new javax.xml.namespace.QName("http://techbd.org/errorinfo", "InteractionId", "err")
                );
                interactionIdElement.addText(interactionId);
                
                // Add ErrorTraceId as a detail element
                var errorTraceIdElement = faultDetail.addFaultDetailElement(
                    new javax.xml.namespace.QName("http://techbd.org/errorinfo", "ErrorTraceId", "err")
                );
                errorTraceIdElement.addText(errorTraceId);
                
                LOG.info("Successfully injected errorTraceId into SOAP fault. interactionId={}, errorTraceId={}", 
                        interactionId, errorTraceId);
            }
        } catch (Exception e) {
            LOG.warn("Failed to inject errorTraceId into SOAP fault detail: {}", e.getMessage());
            // Non-critical failure - don't propagate
        }
    }
}