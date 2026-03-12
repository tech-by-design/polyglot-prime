package org.techbd.ingest.endpoint;

import java.util.UUID;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.techbd.ingest.AbstractMessageSourceProvider;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.exceptions.ErrorTraceIdGenerator;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.service.iti.AcknowledgementService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.LogUtil;
import org.techbd.ingest.util.TemplateLogger;
import org.techbd.iti.schema.ObjectFactory;
import org.techbd.iti.schema.ProvideAndRegisterDocumentSetRequestType;
import org.techbd.iti.schema.RegistryResponseType;

import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.xml.bind.JAXBElement;

/**
 * XDS.b Provide and Register Document Set-b Endpoint (ITI-41).
 *
 * Handles incoming SOAP requests containing document metadata and binary
 * content (MTOM/XOP attachments) according to IHE ITI-41 transaction
 * specification.
 *
 * Responsibilities:
 * <ul>
 * <li>Parse incoming ProvideAndRegisterDocumentSet-b requests</li>
 * <li>Extract metadata and binary documents</li>
 * <li>Pass to {@link MessageProcessorService} for processing</li>
 * <li>Return {@link RegistryResponseType} acknowledgements</li>
 * </ul>
 *
 * This endpoint is separate from {@link PixEndpoint} to adhere to the Single
 * Responsibility Principle (SRP) — PIX (ITI-8) and XDS.b PnR (ITI-41) are
 * fundamentally different transactions.
 */
@Endpoint
public class PnrEndpoint extends AbstractMessageSourceProvider {

    private static TemplateLogger log;
    private static final String NAMESPACE_URI = "urn:ihe:iti:xds-b:2007";
    private final AcknowledgementService ackService;
    private final AppConfig appConfig;

    public PnrEndpoint(AcknowledgementService ackService, AppConfig appConfig, AppLogger appLogger) {
        super(appConfig, appLogger);
        this.ackService = ackService;
        this.appConfig = appConfig;
        log = appLogger.getLogger(PnrEndpoint.class);
    }
    
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "ProvideAndRegisterDocumentSetRequest")
    @ResponsePayload
    public JAXBElement<RegistryResponseType> handleProvideAndRegister(
            @RequestPayload JAXBElement<ProvideAndRegisterDocumentSetRequestType> request,
            MessageContext messageContext) {
        
        var transportContext = TransportContextHolder.getTransportContext();
        var connection = (HttpServletConnection) transportContext.getConnection();
        HttpServletRequest httpRequest = connection.getHttpServletRequest();
        
        // Extract custom headers
        String sourceId = httpRequest.getHeader(Constants.HEADER_SOURCE_ID);
        String msgType = httpRequest.getHeader(Constants.HEADER_MSG_TYPE);
        String interactionId = httpRequest.getHeader(Constants.HEADER_INTERACTION_ID);
        
        // Use attribute interactionId if header not present
        if (StringUtils.isEmpty(interactionId)) {
            interactionId = (String) httpRequest.getAttribute(Constants.INTERACTION_ID);
        }
        if (StringUtils.isEmpty(interactionId)) {
            interactionId = UUID.randomUUID().toString();
        }
        
        String errorTraceId = null;
        
        try {
            log.info("PnrEndpoint:: Received ProvideAndRegisterDocumentSet-b (ITI-41) request. sourceId={} msgType={} interactionId={}",
                    sourceId, msgType, interactionId);
            
            // Get raw SOAP message and build context
            String rawSoapMessage = (String) messageContext.getProperty("RAW_SOAP_MESSAGE");
            RequestContext context = createRequestContext(interactionId, null, httpRequest, 
                rawSoapMessage.length(), "soap-message.xml");
            
            // Set sourceId and msgType in context if available
            context.setSourceId(sourceId);
            context.setMsgType(msgType);
            
            RegistryResponseType response = ackService.createPnrAcknowledgement("Success", interactionId);
            ObjectFactory factory = new ObjectFactory();
            JAXBElement<RegistryResponseType> jaxbResponse = factory.createRegistryResponse(response);
            
            httpRequest.setAttribute(Constants.REQUEST_CONTEXT, context);
            return jaxbResponse;
            
        } catch (Exception e) {
            // Generate error trace ID
            errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
            
            log.error("PnrEndpoint:: Exception processing ITI-41 request. sourceId={} msgType={} interactionId={}, errorTraceId={}, error={}", 
                sourceId, msgType, interactionId, errorTraceId, e.getMessage(), e);
            
            // Log detailed error to CloudWatch
            LogUtil.logDetailedError(
                500, 
                "Exception processing ProvideAndRegisterDocumentSet-b (ITI-41) request", 
                interactionId, 
                errorTraceId, 
                e
            );
            
            RegistryResponseType response = ackService.createPnrAcknowledgement("Failure", interactionId, errorTraceId);
            ObjectFactory factory = new ObjectFactory();
            return factory.createRegistryResponse(response);
        }
    }

    @Override
    public MessageSourceType getMessageSource() {
        return MessageSourceType.SOAP_PNR;
    }
}