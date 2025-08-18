package org.techbd.ingest.interceptors;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.Hl7Util;
import org.techbd.ingest.util.SoapResponseUtil;

import jakarta.servlet.http.HttpServletRequest;

public class WsaHeaderInterceptor implements EndpointInterceptor {

    private final SoapResponseUtil soapResponseUtil;
    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    public WsaHeaderInterceptor(SoapResponseUtil soapResponseUtil, MessageProcessorService messageProcessorService, AppConfig appConfig) {
        this.soapResponseUtil = soapResponseUtil;
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        LOG.info("WsaHeaderInterceptor initialized with MessageProcessorService and AppConfig");
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(WsaHeaderInterceptor.class);
     @Override
    public boolean handleRequest(MessageContext messageContext, Object endpoint) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        messageContext.getRequest().writeTo(out);
        String soapXml = out.toString(StandardCharsets.UTF_8);
        messageContext.setProperty(Constants.RAW_SOAP_ATTRIBUTE, soapXml);
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
        messageProcessorService.processMessage(context, rawSoapMessage,Hl7Util.soapMessageToString(message, interactionId));
        return true;
    }

    @Override
    public boolean handleFault(MessageContext messageContext, Object endpoint) throws Exception {
        // Optionally modify fault responses
        return true;
    }

    @Override
    public void afterCompletion(MessageContext messageContext, Object endpoint, Exception ex) throws Exception {
        // Optional cleanup
    }
}