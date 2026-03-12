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
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.exceptions.ErrorTraceIdGenerator;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.service.iti.AcknowledgementService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.LogUtil;
import org.techbd.ingest.util.TemplateLogger;
import org.techbd.iti.schema.MCCIIN000002UV01;
import org.techbd.iti.schema.PRPAIN201301UV02;
import org.techbd.iti.schema.PRPAIN201302UV02;
import org.techbd.iti.schema.PRPAIN201304UV02;

import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;

/**
 * PIX Manager SOAP Endpoint.
 *
 * Handles the following IHE ITI transactions:
 * <ul>
 * <li> Patient Identity Feed - Add (PRPA_IN201301UV02)</li>
 * <li> Patient Identity Feed - Update (PRPA_IN201302UV02)</li>
 * <li> Patient Identity Feed - Merge/Duplicate Resolved
 * (PRPA_IN201304UV02)</li>
 * </ul>
 *
 * Incoming HL7v3 XML messages are processed via
 * {@link MessageProcessorService}. Acknowledgements are returned using
 * {@link AcknowledgementService}.
 *
 */
@Endpoint
public class PixEndpoint extends AbstractMessageSourceProvider {

    private static TemplateLogger log;
    private static final String NAMESPACE_URI = "urn:hl7-org:v3";
    private final AcknowledgementService ackService;
    private final AppConfig appConfig;
    
    public PixEndpoint(AcknowledgementService ackService, AppConfig appConfig, AppLogger appLogger, PortConfig portConfig) {
        super(appConfig, appLogger);
        this.ackService = ackService;
        this.appConfig = appConfig;
        log = appLogger.getLogger(PixEndpoint.class);
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "PRPA_IN201301UV02")
    @ResponsePayload
    public MCCIIN000002UV01 handlePixAdd(@RequestPayload PRPAIN201301UV02 request,
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
            log.info("PixEndpoint:: Received PRPA_IN201301UV02 request. sourceId={} msgType={} interactionId={}", 
                sourceId, msgType, interactionId);
            
            String rawSoapMessage = (String) messageContext.getProperty("RAW_SOAP_MESSAGE");
            RequestContext context = createRequestContext(interactionId, null, httpRequest, rawSoapMessage.length(), "soap-message.xml");
            
            // Set sourceId and msgType in context if available
            context.setSourceId(sourceId);
            context.setMsgType(msgType);
            context.setPixRequest(true);
            
            MCCIIN000002UV01 response = ackService.createPixAcknowledgement(
                    request.getId(), request.getSender().getDevice(),
                    context.getSourceIp() + ":" + context.getDestinationPort(),
                    context.getProtocol(), interactionId
            );
            httpRequest.setAttribute(Constants.REQUEST_CONTEXT, context);
            return response;
        } catch (Exception e) {
            // Generate error trace ID
            errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
            
            log.error("PixEndpoint:: Exception processing PRPA_IN201301UV02. interactionId={}, errorTraceId={}, error={}",
                    interactionId, errorTraceId, e.getMessage(), e);
            
            // Log detailed error to CloudWatch
            LogUtil.logDetailedError(
                500, 
                "Exception processing PRPA_IN201301UV02", 
                interactionId, 
                errorTraceId, 
                e
            );
            
            return ackService.createPixAcknowledgmentError("Internal server error", interactionId, errorTraceId);
        } 
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "PRPA_IN201302UV02")
    @ResponsePayload
    public MCCIIN000002UV01 handlePixUpdate(@RequestPayload PRPAIN201302UV02 request,
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
            log.info("PixEndpoint:: Received PRPA_IN201302UV02 request. sourceId={} msgType={} interactionId={}", 
                sourceId, msgType, interactionId);
            
            String rawSoapMessage = (String) messageContext.getProperty("RAW_SOAP_MESSAGE");
            RequestContext context = createRequestContext(interactionId, null, httpRequest, rawSoapMessage.length(), "soap-message.xml");
            
            // Set sourceId and msgType in context if available
            context.setSourceId(sourceId);
            context.setMsgType(msgType);
            context.setPixRequest(true);
            MCCIIN000002UV01 response = ackService.createPixAcknowledgement(
                    request.getId(), request.getSender().getDevice(),
                    context.getSourceIp() + ":" + context.getDestinationPort(),
                    context.getProtocol(), interactionId
            );
            httpRequest.setAttribute(Constants.REQUEST_CONTEXT, context);
            return response;
        } catch (Exception e) {
            // Generate error trace ID
            errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
            
            log.error("PixEndpoint:: Exception processing PRPA_IN201302UV02. interactionId={}, errorTraceId={}, error={}",
                    interactionId, errorTraceId, e.getMessage(), e);
            
            // Log detailed error to CloudWatch
            LogUtil.logDetailedError(
                500, 
                "Exception processing PRPA_IN201302UV02", 
                interactionId, 
                errorTraceId, 
                e
            );
            
            return ackService.createPixAcknowledgmentError("Internal server error", interactionId, errorTraceId);
        } 
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "PRPA_IN201304UV02")
    @ResponsePayload
    public MCCIIN000002UV01 handlePixDuplicateResolved(@RequestPayload PRPAIN201304UV02 request,
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
            log.info("PixEndpoint:: Received PRPA_IN201304UV02 request. sourceId={} msgType={} interactionId={}", 
                sourceId, msgType, interactionId);
            
            String rawSoapMessage = (String) messageContext.getProperty("RAW_SOAP_MESSAGE");
            RequestContext context = createRequestContext(interactionId, null, httpRequest, rawSoapMessage.length(), "soap-message.xml");
            
            // Set sourceId and msgType in context if available
            context.setSourceId(sourceId);
            context.setMsgType(msgType);
            context.setPixRequest(true);
            MCCIIN000002UV01 response = ackService.createPixAcknowledgement(
                    request.getId(), request.getSender().getDevice(),
                    context.getSourceIp() + ":" + context.getDestinationPort(),
                    context.getProtocol(), interactionId
            );
            httpRequest.setAttribute(Constants.REQUEST_CONTEXT, context);
            return response;
        } catch (Exception e) {
            // Generate error trace ID
            errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
            
            log.error("PixEndpoint:: Exception processing PRPA_IN201304UV02. interactionId={}, errorTraceId={}, error={}",
                    interactionId, errorTraceId, e.getMessage(), e);
            
            // Log detailed error to CloudWatch
            LogUtil.logDetailedError(
                500, 
                "Exception processing PRPA_IN201304UV02", 
                interactionId, 
                errorTraceId, 
                e
            );
            
            return ackService.createPixAcknowledgmentError("Internal server error", interactionId, errorTraceId);
        } 
    }

    @Override
    public MessageSourceType getMessageSource() {
        return MessageSourceType.SOAP_PIX;
    }
}