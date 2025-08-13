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

import jakarta.servlet.http.HttpServletRequest;

public class WsaHeaderInterceptor implements EndpointInterceptor {

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
        SoapMessage soapResponse = (SoapMessage) messageContext.getResponse();
        SoapMessage soapRequest = (SoapMessage) messageContext.getRequest();
        SoapHeader header = soapResponse.getSoapHeader();
        String wsaNs = "http://www.w3.org/2005/08/addressing";
        String wsaPrefix = "wsa";
        String techbdNs = "urn:techbd:custom";
        String techbdPrefix = "techbd";
        String messageId = "urn:uuid:" + UUID.randomUUID();
        String action = "urn:hl7-org:v3:MCCI_IN000002UV01";
        String to = "http://www.w3.org/2005/08/addressing/anonymous";
        String relatesTo = null;
        Iterator<?> it = soapRequest.getSoapHeader().examineAllHeaderElements();
        while (it.hasNext()) {
            SoapHeaderElement element = (SoapHeaderElement) it.next();
            if ("MessageID".equals(element.getName().getLocalPart()) &&
                    wsaNs.equals(element.getName().getNamespaceURI())) {
                relatesTo = element.getText();
                break;
            }
        }
        if (relatesTo == null) {
            relatesTo = "urn:uuid:unknown-incoming-message-id"; // fallback
        }
        header.addHeaderElement(new QName(wsaNs, "Action", wsaPrefix)).setText(action);
        header.addHeaderElement(new QName(wsaNs, "MessageID", wsaPrefix)).setText(messageId);
        header.addHeaderElement(new QName(wsaNs, "RelatesTo", wsaPrefix)).setText(relatesTo);
        header.addHeaderElement(new QName(wsaNs, "To", wsaPrefix)).setText(to);
        var transportContext = TransportContextHolder.getTransportContext();
        var connection = (HttpServletConnection) transportContext.getConnection();
        HttpServletRequest httpRequest = connection.getHttpServletRequest();
        String interactionId = (String) httpRequest.getAttribute("interactionId");
        if (interactionId == null || interactionId.isBlank()) {
            interactionId = "urn:uuid:techbd-generated-interactionid:" + UUID.randomUUID();
        }
        header.addHeaderElement(new QName(techbdNs, "InteractionID", techbdPrefix))
                .setText(interactionId);
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