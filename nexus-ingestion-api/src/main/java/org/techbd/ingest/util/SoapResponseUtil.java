package org.techbd.ingest.util;

import java.util.Iterator;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.springframework.stereotype.Component;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class SoapResponseUtil {

    private final AppConfig appConfig;
    private TemplateLogger log;
    public SoapResponseUtil(AppConfig appConfig, AppLogger appLogger) {
        this.appConfig = appConfig;
        this.log = appLogger.getLogger(SoapResponseUtil.class);
        log.info("SoapResponseUtil initialized with AppConfig");
    }

 public SoapMessage buildSoapResponse(String interactionId, MessageContext messageContext) {
    log.info("Building SOAP response for interactionId={}", interactionId);

    try {
        SoapMessage soapResponse = (SoapMessage) messageContext.getResponse();
        SoapMessage soapRequest = (SoapMessage) messageContext.getRequest();
        SoapHeader header = soapResponse.getSoapHeader();

        String messageId = "urn:uuid:" + UUID.randomUUID();
        String relatesTo = extractRelatesTo(soapRequest);

        if (relatesTo == null) {
            relatesTo = "urn:uuid:unknown-incoming-message-id";
            log.warn("RelatesTo header not found in request. Using fallback: {} interactionId={}", relatesTo, interactionId);
        }

            var wsa = appConfig.getSoap().getWsa();
              String action;
              var transportContext = TransportContextHolder.getTransportContext();
              var connection = (HttpServletConnection) transportContext.getConnection();
              HttpServletRequest httpRequest = connection.getHttpServletRequest();

              RequestContext context = (RequestContext) httpRequest.getAttribute(Constants.REQUEST_CONTEXT);
              if (context.isPixRequest()) {
                  action = wsa.getAction();
                  log.info("PIX request detected. Using Action={} interactionId={}", action, interactionId);
              } else {
                  action = wsa.getPnrAction();
                  log.info("PNR request detected. Using Action={} interactionId={}", action, interactionId);
              }

            // Standard WS-Addressing headers only
            header.addHeaderElement(new QName(wsa.getNamespace(), "Action", wsa.getPrefix()))
                    .setText(action);
            header.addHeaderElement(new QName(wsa.getNamespace(), "MessageID", wsa.getPrefix()))
                    .setText(messageId);
            header.addHeaderElement(new QName(wsa.getNamespace(), "RelatesTo", wsa.getPrefix()))
                    .setText(relatesTo);
            header.addHeaderElement(new QName(wsa.getNamespace(), "To", wsa.getPrefix()))
                    .setText(wsa.getTo());

            log.info("SOAP response built successfully for interactionId={}", interactionId);
            return soapResponse;

    } catch (Exception e) {
        log.error("Failed to build SOAP response for interactionId={}, version={}",
                interactionId, appConfig.getVersion(), e);
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

