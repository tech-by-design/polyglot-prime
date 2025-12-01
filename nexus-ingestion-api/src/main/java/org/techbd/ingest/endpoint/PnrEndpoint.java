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
import org.techbd.iti.schema.ObjectFactory;
import org.techbd.iti.schema.ProvideAndRegisterDocumentSetRequestType;
import org.techbd.iti.schema.RegistryResponseType;

import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.xml.bind.JAXBElement;

/**
 * n
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
    private final PortConfig portConfig;
    private final ThreadLocal<PortConfig.PortEntry> currentPortEntry = new ThreadLocal<>();

    public PnrEndpoint(AcknowledgementService ackService, AppConfig appConfig, AppLogger appLogger, PortConfig portConfig) {
        super(appConfig, appLogger);
        this.ackService = ackService;
        this.appConfig = appConfig;
        this.portConfig = portConfig;
        try {
            if (!this.portConfig.isLoaded()) {
                this.portConfig.loadConfig();
            }
        } catch (Exception e) {
            log = appLogger.getLogger(PnrEndpoint.class);
            log.warn("PnrEndpoint: Failed to load PortConfig during initialization: {}", e.getMessage());
        }
        log = appLogger.getLogger(PnrEndpoint.class);
    }

    private void resolvePortEntry(jakarta.servlet.http.HttpServletRequest httpRequest) {
        try {
            String portHeader = httpRequest.getHeader(Constants.REQ_X_FORWARDED_PORT);
            if (portHeader == null) {
                portHeader = httpRequest.getHeader("x-forwarded-port");
            }
            if (StringUtils.isNotBlank(portHeader)) {
                try {
                    int requestPort = Integer.parseInt(portHeader);
                    if (portConfig != null) {
                        if (!portConfig.isLoaded()) {
                            portConfig.loadConfig();
                        }
                        if (portConfig.isLoaded()) {
                            for (PortConfig.PortEntry entry : portConfig.getPortConfigurationList()) {
                                if (entry.port == requestPort) {
                                    currentPortEntry.set(entry);
                                    log.info("PnrEndpoint: Matched port entry for port {} -> route={}", requestPort, entry.route);
                                    break;
                                }
                            }
                        } else {
                            log.warn("PnrEndpoint: PortConfig not loaded when resolving port entry");
                        }
                    }
                } catch (NumberFormatException nfe) {
                    log.warn("PnrEndpoint: Invalid x-forwarded-port header value: {}", portHeader);
                }
            }
        } catch (Exception e) {
            log.warn("PnrEndpoint: Exception while resolving port entry: {}", e.getMessage());
        }
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "ProvideAndRegisterDocumentSetRequest")
    @ResponsePayload
    public JAXBElement<RegistryResponseType> handleProvideAndRegister(@RequestPayload JAXBElement<ProvideAndRegisterDocumentSetRequestType> request,
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
            log.info("PnrEndpoint:: Received ProvideAndRegisterDocumentSet-b (ITI-41) request. interactionId={}",
                    interactionId);
            // Get raw SOAP message and build context
            String rawSoapMessage = (String) messageContext.getProperty("RAW_SOAP_MESSAGE");
            RequestContext context = createRequestContext(interactionId, null, httpRequest, rawSoapMessage.length(), "soap-message.xml");
            RegistryResponseType response = ackService.createPnrAcknowledgement("Success", interactionId);
            ObjectFactory factory = new ObjectFactory();
            JAXBElement<RegistryResponseType> jaxbResponse = factory.createRegistryResponse(response);
            httpRequest.setAttribute(Constants.REQUEST_CONTEXT, context);
            return jaxbResponse;
        } catch (Exception e) {
            log.error("PnrEndpoint:: Exception processing ITI-41 request. interactionId={}, error={}", interactionId, e.getMessage(), e);
            RegistryResponseType response = ackService.createPnrAcknowledgement("Failure", interactionId);
            ObjectFactory factory = new ObjectFactory();
            return factory.createRegistryResponse(response);
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
        return MessageSourceType.SOAP_PNR;
    }

    /**
     * Prefix the default data key with per-port dataDir when a port entry is
     * resolved for the current request.
     */
    @Override
    public String getDataKey(String interactionId, Map<String, String> headers, String originalFileName, String timestamp) {
        PortConfig.PortEntry entry = currentPortEntry.get();

        Instant now = Instant.now();
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);
        String datePath = uploadTime.format(Constants.DATE_PATH_FORMATTER);
        String baseKey;
        if (entry != null && "/hold".equals(entry.route)) {
            // prepare filename and extension
            String original = (originalFileName == null || originalFileName.isBlank()) ? "body" : originalFileName;
            String baseName = original;
            String extension = "";

            int lastDot = original.lastIndexOf('.');
            if (lastDot > 0 && lastDot < original.length() - 1) {
                baseName = original.substring(0, lastDot);
                extension = original.substring(lastDot + 1);
            }

            // build timestamped filename: {timestamp}_{basename}.{extension}
            String timestampedName = timestamp + "_" + baseName;
            if (!extension.isBlank()) {
                timestampedName = timestampedName + "." + extension;
            }

            // final path: hold/{destination_port}/{YYYY}/{MM}/{DD}/{timestamp_filename}.{extension}
            baseKey = String.format("hold/%d/%s/%s", entry.port, datePath, timestampedName);

        } else {
            baseKey = String.format("data/%s/%s_%s", datePath, interactionId, timestamp);
        }

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
        PortConfig.PortEntry entry = currentPortEntry.get();

        Instant now = Instant.now();
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);
        String datePath = uploadTime.format(Constants.DATE_PATH_FORMATTER);
        String baseKey;
        if (entry != null && "/hold".equals(entry.route)) {
            // prepare filename and extension
            String original = (originalFileName == null || originalFileName.isBlank()) ? "body" : originalFileName;
            String baseName = original;
            String extension = "";

            int lastDot = original.lastIndexOf('.');
            if (lastDot > 0 && lastDot < original.length() - 1) {
                baseName = original.substring(0, lastDot);
                extension = original.substring(lastDot + 1);
            }

            // build timestamped filename: {timestamp}_{basename}.{extension}
            String timestampedName = timestamp + "_" + baseName;
            if (!extension.isBlank()) {
                timestampedName = timestampedName + "." + extension;
            }

            // final path: hold/{destination_port}/{YYYY}/{MM}/{DD}/{timestamp_filename}.{extension}_metadata.json
            baseKey = String.format("hold/%d/%s/%s_metadata.json", entry.port, datePath, timestampedName);

        } else {
            baseKey = String.format("metadata/%s/%s_%s_metadata.json", datePath, interactionId, timestamp);
        }

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
        PortConfig.PortEntry entry = currentPortEntry.get();
        if (entry != null && "/hold".equals(entry.route)) {
            return appConfig.getAws().getS3().getHoldConfig().getBucket();
        }
        return appConfig.getAws().getS3().getDefaultConfig().getBucket();
    }

    @Override
    public String getMetadataBucketName() {
        PortConfig.PortEntry entry = currentPortEntry.get();
        if (entry != null && "/hold".equals(entry.route)) {
            return appConfig.getAws().getS3().getHoldConfig().getMetadataBucket();
        }
        return appConfig.getAws().getS3().getDefaultConfig().getMetadataBucket();
    }
}
