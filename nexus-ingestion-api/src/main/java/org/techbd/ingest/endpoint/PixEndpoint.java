package org.techbd.ingest.endpoint;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
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
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.service.iti.AcknowledgementService;
import org.techbd.ingest.util.AppLogger;
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
    private final PortConfig portConfig;
    private final ThreadLocal<PortConfig.PortEntry> currentPortEntry = new ThreadLocal<>();

    public PixEndpoint(AcknowledgementService ackService, AppConfig appConfig, AppLogger appLogger, PortConfig portConfig) {
        super(appConfig, appLogger);
        this.ackService = ackService;
        this.appConfig = appConfig;
        this.portConfig = portConfig;
        log = appLogger.getLogger(PixEndpoint.class);
        try {
            if (this.portConfig != null && !this.portConfig.isLoaded()) {
                this.portConfig.loadConfig();
            }
        } catch (Exception e) {
            log.warn("PixEndpoint: Failed to load PortConfig during initialization: {}", e.getMessage());
        }
    }

    private void resolvePortEntry(jakarta.servlet.http.HttpServletRequest httpRequest) {
        try {
            String portHeader = httpRequest.getHeader(Constants.REQ_X_FORWARDED_PORT);
            if (portHeader == null) {
                portHeader = httpRequest.getHeader("x-forwarded-port");
            }
            if (StringUtils.isNotBlank(portHeader) && portConfig != null) {
                try {
                    int requestPort = Integer.parseInt(portHeader);
                    if (!portConfig.isLoaded()) {
                        portConfig.loadConfig();
                    }
                    if (portConfig.isLoaded()) {
                        for (PortConfig.PortEntry entry : portConfig.getPortConfigurationList()) {
                            if (entry.port == requestPort) {
                                currentPortEntry.set(entry);
                                log.info("PixEndpoint: Matched port entry for port {} -> route={}", requestPort, entry.route);
                                break;
                            }
                        }
                    } else {
                        log.warn("PixEndpoint: PortConfig not loaded when resolving port entry");
                    }
                } catch (NumberFormatException nfe) {
                    log.warn("PixEndpoint: Invalid x-forwarded-port header value: {}", portHeader);
                }
            }
        } catch (Exception e) {
            log.warn("PixEndpoint: Exception while resolving port entry: {}", e.getMessage());
        }
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "PRPA_IN201301UV02")
    @ResponsePayload
    public MCCIIN000002UV01 handlePixAdd(@RequestPayload PRPAIN201301UV02 request,
            MessageContext messageContext) {
        var transportContext = TransportContextHolder.getTransportContext();
        var connection = (HttpServletConnection) transportContext.getConnection();
        HttpServletRequest httpRequest = connection.getHttpServletRequest();
        var interactionId = (String) httpRequest.getAttribute(Constants.INTERACTION_ID);
        if (StringUtils.isEmpty(interactionId)) {
            interactionId = UUID.randomUUID().toString();
        }
        resolvePortEntry(httpRequest);
        try {
            log.info("PixEndpoint:: Received PRPA_IN201301UV02 request. interactionId={}", interactionId);
            String rawSoapMessage = (String) messageContext.getProperty("RAW_SOAP_MESSAGE");
            RequestContext context = createRequestContext(interactionId, null, httpRequest, rawSoapMessage.length(), "soap-message.xml");
            MCCIIN000002UV01 response = ackService.createPixAcknowledgement(
                    request.getId(), request.getSender().getDevice(),
                    context.getSourceIp() + ":" + context.getDestinationPort(),
                    context.getProtocol(), interactionId
            );
            httpRequest.setAttribute(Constants.REQUEST_CONTEXT, context);
            return response;
        } catch (Exception e) {
            log.error("PixEndpoint:: Exception processing PRPA_IN201301UV02. interactionId={}, error={}",
                    interactionId, e.getMessage(), e);
            return ackService.createPixAcknowledgmentError("Internal server error", interactionId);
        } finally {
            currentPortEntry.remove();
        }
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "PRPA_IN201302UV02")
    @ResponsePayload
    public MCCIIN000002UV01 handlePixUpdate(@RequestPayload PRPAIN201302UV02 request,
            MessageContext messageContext) {
        var transportContext = TransportContextHolder.getTransportContext();
        var connection = (HttpServletConnection) transportContext.getConnection();
        HttpServletRequest httpRequest = connection.getHttpServletRequest();
        var interactionId = (String) httpRequest.getAttribute(Constants.INTERACTION_ID);
        if (StringUtils.isEmpty(interactionId)) {
            interactionId = UUID.randomUUID().toString();
        }
        resolvePortEntry(httpRequest);
        try {
            log.info("PixEndpoint:: Received PRPA_IN201302UV02 request. interactionId={}", interactionId);
            String rawSoapMessage = (String) messageContext.getProperty("RAW_SOAP_MESSAGE");
            RequestContext context = createRequestContext(interactionId, null, httpRequest, rawSoapMessage.length(), "soap-message.xml");
            MCCIIN000002UV01 response = ackService.createPixAcknowledgement(
                    request.getId(), request.getSender().getDevice(),
                    context.getSourceIp() + ":" + context.getDestinationPort(),
                    context.getProtocol(), interactionId
            );
            httpRequest.setAttribute(Constants.REQUEST_CONTEXT, context);
            return response;
        } catch (Exception e) {
            log.error("PixEndpoint:: Exception processing PRPA_IN201302UV02. interactionId={}, error={}",
                    interactionId, e.getMessage(), e);
            return ackService.createPixAcknowledgmentError("Internal server error", interactionId);
        } finally {
            currentPortEntry.remove();
        }
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "PRPA_IN201304UV02")
    @ResponsePayload
    public MCCIIN000002UV01 handlePixDuplicateResolved(@RequestPayload PRPAIN201304UV02 request,
            MessageContext messageContext) {
        var transportContext = TransportContextHolder.getTransportContext();
        var connection = (HttpServletConnection) transportContext.getConnection();
        HttpServletRequest httpRequest = connection.getHttpServletRequest();
        var interactionId = (String) httpRequest.getAttribute(Constants.INTERACTION_ID);
        if (StringUtils.isEmpty(interactionId)) {
            interactionId = UUID.randomUUID().toString();
        }
        resolvePortEntry(httpRequest);
        try {
            log.info("PixEndpoint:: Received PRPA_IN201304UV02 request. interactionId={}", interactionId);
            String rawSoapMessage = (String) messageContext.getProperty("RAW_SOAP_MESSAGE");
            RequestContext context = createRequestContext(interactionId, null, httpRequest, rawSoapMessage.length(), "soap-message.xml");
            MCCIIN000002UV01 response = ackService.createPixAcknowledgement(
                    request.getId(), request.getSender().getDevice(),
                    context.getSourceIp() + ":" + context.getDestinationPort(),
                    context.getProtocol(), interactionId
            );
            httpRequest.setAttribute(Constants.REQUEST_CONTEXT, context);
            return response;
        } catch (Exception e) {
            log.error("PixEndpoint:: Exception processing PRPA_IN201304UV02. interactionId={}, error={}",
                    interactionId, e.getMessage(), e);
            return ackService.createPixAcknowledgmentError("Internal server error", interactionId);
        } finally {
            currentPortEntry.remove();
        }
    }

    @Override
    public MessageSourceType getMessageSource() {
        PortConfig.PortEntry entry = currentPortEntry.get();
        if (entry != null && "/hold".equals(entry.route)) {
            return MessageSourceType.HTTP_HOLD;
        }
        return MessageSourceType.SOAP_PIX;
    }

    /**
     * Prefix the default data key with per-port dataDir when a port entry is
     * resolved for the current request.
     */
    @Override
    public String getDataKey(String interactionId, Map<String, String> headers, String originalFileName, String timestamp) {
        Instant now = Instant.now();
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);
        String datePath = uploadTime.format(Constants.DATE_PATH_FORMATTER);

        String baseKey = String.format("data/%s/%s_%s", datePath, interactionId, timestamp);
        PortConfig.PortEntry entry = currentPortEntry.get();
        if (entry != null && entry.dataDir != null && !entry.dataDir.isBlank()) {
            String prefix = entry.dataDir.replaceAll("^/+", "").replaceAll("/+$", "");
            if (!prefix.isEmpty()) {
                return prefix + "/" + baseKey;
            }
        }
        return baseKey;
    }

    /**
     * Prefix the default metadata key with per-port metadataDir when a port
     * entry is resolved for the current request.
     */
    @Override
    public String getMetaDataKey(String interactionId, Map<String, String> headers, String originalFileName, String timestamp) {
        Instant now = Instant.now();
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);
        String datePath = uploadTime.format(Constants.DATE_PATH_FORMATTER);

        String baseKey = String.format("metadata/%s/%s_%s_metadata.json", datePath, interactionId, timestamp);
        PortConfig.PortEntry entry = currentPortEntry.get();
        if (entry != null && entry.metadataDir != null && !entry.metadataDir.isBlank()) {
            String prefix = entry.metadataDir.replaceAll("^/+", "").replaceAll("/+$", "");
            if (!prefix.isEmpty()) {
                return prefix + "/" + baseKey;
            }
        }
        return baseKey;
    }

    // Backwards-compatible aliases if any code expects getKey()/getMetadataKey()
    public String getKey(String interactionId, Map<String, String> headers, String originalFileName, String timestamp) {
        return getDataKey(interactionId, headers, originalFileName, timestamp);
    }

    public String getMetadataKey(String interactionId, Map<String, String> headers, String originalFileName, String timestamp) {
        return getMetaDataKey(interactionId, headers, originalFileName, timestamp);
    }

    @Override
    public String getDataBucketName() {
        return appConfig.getAws().getS3().getDefaultConfig().getBucket();
    }

    @Override
    public String getMetadataBucketName() {
        return appConfig.getAws().getS3().getDefaultConfig().getMetadataBucket();
    }
}
