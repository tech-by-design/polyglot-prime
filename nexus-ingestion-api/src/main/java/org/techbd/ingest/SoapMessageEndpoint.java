package org.techbd.ingest.endpoint;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.router.IngestionRouter;

import javax.xml.bind.JAXBElement;
import java.util.Map;
import java.util.UUID;

@Endpoint
@Slf4j
public class SoapMessageEndpoint {

    private static final String NAMESPACE_URI = "http://your.org.namespace";
    private final IngestionRouter ingestionRouter;

    public SoapMessageEndpoint(IngestionRouter ingestionRouter) {
        this.ingestionRouter = ingestionRouter;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "PNRRequest")
    public void handlePnr(@RequestPayload JAXBElement<?> request) {
        processSoapMessage(request);
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "PIXRequest")
    public void handlePix(@RequestPayload JAXBElement<?> request) {
        processSoapMessage(request);
    }

    private void processSoapMessage(Object request) {
        String interactionId = UUID.randomUUID().toString();
        log.info("SoapMessageEndpoint:: Received SOAP request. interactionId={}", interactionId);
        RequestContext context = new RequestContext(interactionId, Map.of(), null, null, null, 0L);
        ingestionRouter.routeAndProcess(request, context);
    }
}
