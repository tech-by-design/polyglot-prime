package org.techbd.ingest.interceptors;

import java.util.UUID;
import javax.xml.namespace.QName;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.processor.S3UploadStep;
import org.techbd.ingest.util.Hl7Util;
import org.techbd.ingest.util.SoapResponseUtil;

import jakarta.servlet.http.HttpServletRequest;

public class WsaHeaderInterceptor implements EndpointInterceptor {

    private final SoapResponseUtil soapResponseUtil;

    public WsaHeaderInterceptor(SoapResponseUtil soapResponseUtil) {
        this.soapResponseUtil = soapResponseUtil;
    }
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(WsaHeaderInterceptor.class);
     @Override
    public boolean handleRequest(MessageContext messageContext, Object endpoint) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        messageContext.getRequest().writeTo(out);
        String soapXml = out.toString(StandardCharsets.UTF_8);

        // Store in message context so endpoint can retrieve it later
        messageContext.setProperty(Constants.RAW_SOAP_ATTRIBUTE, soapXml);

        return true;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext, Object endpoint) throws Exception {
         var transportContext = TransportContextHolder.getTransportContext();
        var connection = (HttpServletConnection) transportContext.getConnection();
        HttpServletRequest httpRequest = connection.getHttpServletRequest();
        String interactionId = (String) httpRequest.getAttribute("interactionId");
        soapResponseUtil.buildSoapResponse(interactionId, messageContext);
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