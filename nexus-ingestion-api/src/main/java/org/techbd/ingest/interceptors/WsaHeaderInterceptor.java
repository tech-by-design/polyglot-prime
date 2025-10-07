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
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.Hl7Util;
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

    @Override
    public boolean handleRequest(MessageContext messageContext, Object endpoint) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        messageContext.getRequest().writeTo(out);
        String soapXml = out.toString(StandardCharsets.UTF_8);
        messageContext.setProperty(Constants.RAW_SOAP_ATTRIBUTE, soapXml);

        String interactionId = (String) messageContext.getProperty(Constants.INTERACTION_ID);
        LOG.info("handleRequest: Captured SOAP request. interactionId={}", interactionId);

        return true;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext, Object endpoint) throws Exception {
        var transportContext = TransportContextHolder.getTransportContext();
        var connection = (HttpServletConnection) transportContext.getConnection();
        HttpServletRequest httpRequest = connection.getHttpServletRequest();
        String interactionId = (String) httpRequest.getAttribute(Constants.INTERACTION_ID);

        SoapMessage message = soapResponseUtil.buildSoapResponse(interactionId, messageContext);
        RequestContext context = (RequestContext) httpRequest.getAttribute(Constants.REQUEST_CONTEXT);
        String rawSoapMessage = (String) messageContext.getProperty(Constants.RAW_SOAP_ATTRIBUTE);

        messageContext.setProperty(Constants.INTERACTION_ID, interactionId);
        LOG.info("handleResponse: Processing SOAP response. interactionId={}", interactionId);

        messageProcessorService.processMessage(context, rawSoapMessage,
                Hl7Util.soapMessageToString(message, interactionId));

        return true;
    }

    @Override
    public boolean handleFault(MessageContext messageContext, Object endpoint) throws Exception {
        String interactionId = (String) messageContext.getProperty(Constants.INTERACTION_ID);

        try {
            SoapMessage faultMessage = (SoapMessage) messageContext.getResponse();
            if (faultMessage != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                faultMessage.writeTo(out);
                String faultXml = out.toString(StandardCharsets.UTF_8);
                LOG.error("handleFault: SOAP Fault encountered. interactionId={}, fault={}", interactionId, faultXml);
            }
        } catch (Exception e) {
            LOG.error("handleFault: Exception while processing SOAP fault. interactionId={}, error={}", 
                      interactionId, e.getMessage(), e);
        }

        return true; // continue interceptor chain
    }

    @Override
    public void afterCompletion(MessageContext messageContext, Object endpoint, Exception ex) throws Exception {
        String interactionId = (String) messageContext.getProperty(Constants.INTERACTION_ID);
        String status = (ex == null) ? "SUCCESS" : "FAILURE";

        if (ex != null) {
            LOG.error(
                    "afterCompletion: SOAP interaction completed with ERROR. interactionId={}, errorMessage={}, stackTrace={}",
                    interactionId, ex.getMessage(), ex);
        } else {
            LOG.info("afterCompletion: SOAP interaction completed. interactionId={}, status={}", interactionId, status);
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
}