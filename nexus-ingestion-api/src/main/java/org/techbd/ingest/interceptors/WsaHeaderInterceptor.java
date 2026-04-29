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
import org.techbd.ingest.feature.FeatureEnum;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.service.soap.SoapResponseStrategy;
import org.techbd.ingest.service.soap.SoapResponseStrategyFactory;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.Hl7Util;
import org.techbd.ingest.util.LogUtil;
import org.techbd.ingest.util.SoapResponseUtil;
import org.techbd.ingest.util.TemplateLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
public class WsaHeaderInterceptor implements EndpointInterceptor, SoapEndpointInterceptor {

    private final SoapResponseUtil soapResponseUtil;
    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private final TemplateLogger LOG;
    private final SoapResponseStrategyFactory strategyFactory;

    public WsaHeaderInterceptor(SoapResponseUtil soapResponseUtil,
                                MessageProcessorService messageProcessorService,
                                AppConfig appConfig,
                                AppLogger appLogger,
                                SoapResponseStrategyFactory strategyFactory) {
        this.soapResponseUtil = soapResponseUtil;
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.LOG = appLogger.getLogger(WsaHeaderInterceptor.class);
        this.strategyFactory = strategyFactory;  
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
        // Prefer raw bytes stored on request by the factory (original MIME boundary intact).
        var transportContext = TransportContextHolder.getTransportContext();
        if (transportContext != null && transportContext.getConnection() instanceof HttpServletConnection conn) {
            HttpServletRequest httpRequest = conn.getHttpServletRequest();
            String rawFromRequest = (String) httpRequest.getAttribute(Constants.RAW_SOAP_ATTRIBUTE);
            if (rawFromRequest != null) {
                messageContext.setProperty(Constants.RAW_SOAP_ATTRIBUTE, rawFromRequest);
                return true;
            }
        }
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
        String ackPayload;
        var transportContext = TransportContextHolder.getTransportContext();
        var connection = (HttpServletConnection) transportContext.getConnection();
        HttpServletRequest httpRequest = connection.getHttpServletRequest();
        HttpServletResponse httpResponse = connection.getHttpServletResponse();

        String interactionId = (String) httpRequest.getAttribute(Constants.INTERACTION_ID);

        SoapMessage message = soapResponseUtil.buildSoapResponse(interactionId, messageContext);

        String responseType = (String) httpRequest.getAttribute(Constants.RESPONSE_TYPE);
        if (responseType == null || responseType.isBlank()) {
            responseType = httpRequest.getHeader(Constants.RESPONSE_TYPE);
        }
        SoapResponseStrategy strategy = strategyFactory.resolve(responseType);

        RequestContext context = (RequestContext) httpRequest.getAttribute(Constants.REQUEST_CONTEXT);
        String rawSoapMessage = (String) messageContext.getProperty(Constants.RAW_SOAP_ATTRIBUTE);

        messageContext.setProperty(Constants.INTERACTION_ID, interactionId);
        LOG.info("handleResponse: responseType={} interactionId={}", responseType, interactionId);

        byte[] actualResponseBytes =strategy.writeResponse(interactionId, messageContext, httpRequest, httpResponse, message);

        // ── For MTOM strategies: mark the MessageContext response as already sent ──
        // This prevents Spring-WS from serializing the SoapMessage a second time
        // after handleResponse returns, which would append bytes past the committed
        // Content-Length and cause client-side truncation.
        
        if (isMtomResponseType(responseType)) {
            messageContext.getResponse().writeTo(new java.io.OutputStream() {
                @Override
                public void write(int b) {
                } // discard — already written

                @Override
                public void write(byte[] b, int off, int len) {
                }
            });
            LOG.debug("handleResponse: suppressed Spring-WS secondary write for MTOM. interactionId={}", interactionId);
        }
            if (isMtomResponseType(responseType)) {
                ackPayload = new String(actualResponseBytes, StandardCharsets.UTF_8);
            } else {
                ackPayload = Hl7Util.soapMessageToString(message, interactionId);
            }
            messageProcessorService.processMessage(context, rawSoapMessage,
                    ackPayload);
        return true;
    }

    private boolean isMtomResponseType(String responseType) {
        if (responseType == null || responseType.isBlank())
            return false;
        String n = responseType.trim().toLowerCase();
        return n.equals("mtom") || n.equals("mtom_trubridge");
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
                
                // Try to inject error trace ID into SOAP fault message (only if not already present)
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
        var transportContext = TransportContextHolder.getTransportContext();
        var connection = (HttpServletConnection) transportContext.getConnection();
        HttpServletRequest httpRequest = connection.getHttpServletRequest();
        String ackContentType = (String) httpRequest.getAttribute(Constants.ACK_CONTENT_TYPE);
        // Override content type if configured in port configuration
        if (ackContentType != null && !ackContentType.isBlank()) {
            try {
                var httpResponse = connection.getHttpServletResponse();
                        httpResponse.setHeader("Content-Type", ackContentType);

                httpResponse.setContentType(ackContentType);
                LOG.info("handleResponse: Overriding response content type to: {} for interactionId={}", 
                        ackContentType, interactionId);
            } catch (Exception e) {
                LOG.warn("handleResponse: Failed to override response content type for interactionId={}, error={}", 
                        interactionId, e.getMessage());
            }
        }
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
        if (FeatureEnum.isEnabled(FeatureEnum.IGNORE_MUST_UNDERSTAND_HEADERS)) {
            String headerNamespace = header.getName().getNamespaceURI();
            String headerName = header.getName().getLocalPart();
            boolean ignoreMustUnderstand = true;

            LOG.warn(
                    "Claiming to understand header because ignoreMustUnderstand={} (mustUnderstand ignored): {}:{} from namespace: {}",
                    ignoreMustUnderstand,
                    header.getName().getPrefix(),
                    headerName,
                    headerNamespace);
            return true;
        }
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
     * Only adds if not already present to avoid duplicates
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
                
                // Check if InteractionId and ErrorTraceId already exist
                boolean hasInteractionId = hasDetailElement(faultDetail, "InteractionId");
                boolean hasErrorTraceId = hasDetailElement(faultDetail, "ErrorTraceId");
                
                if (hasInteractionId && hasErrorTraceId) {
                    LOG.debug("Error trace details already present in SOAP fault, skipping injection. interactionId={}", interactionId);
                    return;
                }
                
                // Add InteractionId if not present
                if (!hasInteractionId) {
                    var interactionIdElement = faultDetail.addFaultDetailElement(
                        new javax.xml.namespace.QName("http://techbd.org/errorinfo", "InteractionId", "err")
                    );
                    interactionIdElement.addText(interactionId);
                    LOG.debug("Added InteractionId to SOAP fault. interactionId={}", interactionId);
                }
                
                // Add ErrorTraceId if not present
                if (!hasErrorTraceId) {
                    var errorTraceIdElement = faultDetail.addFaultDetailElement(
                        new javax.xml.namespace.QName("http://techbd.org/errorinfo", "ErrorTraceId", "err")
                    );
                    errorTraceIdElement.addText(errorTraceId);
                    LOG.debug("Added ErrorTraceId to SOAP fault. errorTraceId={}", errorTraceId);
                }
                
                LOG.info("Successfully injected error trace details into SOAP fault. interactionId={}, errorTraceId={}", 
                        interactionId, errorTraceId);
            }
        } catch (Exception e) {
            LOG.warn("Failed to inject errorTraceId into SOAP fault detail: {}", e.getMessage());
            // Non-critical failure - don't propagate
        }
    }

    /**
     * Check if a detail element with the given local name exists in fault detail
     */
    private boolean hasDetailElement(org.springframework.ws.soap.SoapFaultDetail faultDetail, String localName) {
        try {
            var iterator = faultDetail.getDetailEntries();
            while (iterator.hasNext()) {
                var element = iterator.next();
                if (element.getName().getLocalPart().equals(localName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.debug("Error checking for detail element {}: {}", localName, e.getMessage());
        }
        return false;
    }
}