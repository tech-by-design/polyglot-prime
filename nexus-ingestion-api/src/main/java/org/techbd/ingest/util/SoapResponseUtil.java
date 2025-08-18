package org.techbd.ingest.util;

import java.util.Iterator;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.springframework.stereotype.Component;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapMessage;
import org.techbd.ingest.config.AppConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SoapResponseUtil {

    private final AppConfig appConfig;

    public SoapMessage buildSoapResponse(String interactionId,
                                         MessageContext messageContext) {
        log.info("Building SOAP response for interactionId={}", interactionId);

        try {
            SoapMessage soapResponse = (SoapMessage) messageContext.getResponse();
            SoapMessage soapRequest = (SoapMessage) messageContext.getRequest();
            SoapHeader header = soapResponse.getSoapHeader();

            String messageId = "urn:uuid:" + UUID.randomUUID();
            String relatesTo = extractRelatesTo(soapRequest);

            if (relatesTo == null) {
                relatesTo = "urn:uuid:unknown-incoming-message-id";
                log.warn("RelatesTo header not found in request. Using fallback: {}", relatesTo);
            }

            var wsa = appConfig.getSoap().getWsa();
            var techbd = appConfig.getSoap().getTechbd();

            header.addHeaderElement(new QName(wsa.getNamespace(), "Action", wsa.getPrefix()))
                  .setText(wsa.getAction());
            header.addHeaderElement(new QName(wsa.getNamespace(), "MessageID", wsa.getPrefix()))
                  .setText(messageId);
            header.addHeaderElement(new QName(wsa.getNamespace(), "RelatesTo", wsa.getPrefix()))
                  .setText(relatesTo);
            header.addHeaderElement(new QName(wsa.getNamespace(), "To", wsa.getPrefix()))
                  .setText(wsa.getTo());
            header.addHeaderElement(new QName(techbd.getNamespace(), "InteractionID", techbd.getPrefix()))
                  .setText(interactionId);

           // marshaller.marshal(payload, soapResponse.getPayloadResult());

            log.info("SOAP response built successfully for interactionId={}", interactionId);
            return soapResponse;

        } catch (Exception e) {
            log.error("Failed to build SOAP response for interactionId={}", interactionId, e);
            throw new RuntimeException("Error creating SOAP response", e);
        }
    }

    private String extractRelatesTo(SoapMessage soapRequest) {
        String wsaNs = appConfig.getSoap().getWsa().getNamespace();
        Iterator<?> it = soapRequest.getSoapHeader().examineAllHeaderElements();
        while (it.hasNext()) {
            SoapHeaderElement element = (SoapHeaderElement) it.next();
            if ("MessageID".equals(element.getName().getLocalPart()) &&
                wsaNs.equals(element.getName().getNamespaceURI())) {
                return element.getText();
            }
        }
        return null;
    }
}

