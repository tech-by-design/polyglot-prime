package org.techbd.ingest.interceptors;

import java.util.UUID;
import javax.xml.namespace.QName;
import java.util.Iterator;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapMessage;

public class WsaHeaderInterceptor implements EndpointInterceptor {

    @Override
    public boolean handleRequest(MessageContext messageContext, Object endpoint) throws Exception {
        // You can extract wsa:MessageID or RelatesTo here if needed
        return true;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext, Object endpoint) throws Exception {
        SoapMessage soapResponse = (SoapMessage) messageContext.getResponse();
        SoapMessage soapRequest = (SoapMessage) messageContext.getRequest();
        SoapHeader header = soapResponse.getSoapHeader();
        String wsaNs = "http://www.w3.org/2005/08/addressing";
        String wsaPrefix = "wsa";
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