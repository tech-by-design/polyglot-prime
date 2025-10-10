package org.techbd.ingest.controller;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.AbstractMessageSourceProvider;
import org.techbd.ingest.MessageSourceProvider;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.HttpUtil;
import org.techbd.ingest.util.TemplateLogger;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.techbd.ingest.config.PortConfig;

/**
 * Controller for handling data ingestion requests. This controller processes
 * file uploads and string content ingestion.
 */
@RestController
public class DataIngestionController extends AbstractMessageSourceProvider {

    private final TemplateLogger LOG;
    private final MessageProcessorService messageProcessorService;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;
    private final PortConfig portConfig;

    public DataIngestionController(MessageProcessorService messageProcessorService, ObjectMapper objectMapper, AppConfig appConfig, AppLogger appLogger, PortConfig portConfig) {
        super(appConfig, appLogger);
        this.messageProcessorService = messageProcessorService;
        this.objectMapper = objectMapper;
        this.appConfig = appConfig;
        this.portConfig = portConfig;
        LOG = appLogger.getLogger(DataIngestionController.class);
        LOG.info("DataIngestionController initialized");
    }

    /**
     * Health check endpoint using HEAD method on /ingest endpoint.
     *
     * @return A response entity indicating service health status.
     */
    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ResponseEntity<Void> healthCheck() {
        try {
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            LOG.error("Health check failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Endpoint to handle ingestion requests.
     *
     * This endpoint can accept either: - a file upload (multipart/form-data) -
     * raw data in the body (JSON, XML, plain text, HL7, etc.)
     *
     * If raw body data is provided, a filename is generated based on the
     * Content-Type header.
     *
     * @param file The optional file to be ingested (when multipart/form-data).
     * @param body The optional raw payload (when Content-Type is
     * JSON/XML/Text).
     * @param headers The request headers containing metadata.
     * @param request The HTTP servlet request.
     * @return A response entity containing the result of the ingestion process.
     * @throws Exception If an error occurs during processing.
     */
    @PostMapping(value = "/ingest", consumes = {
        MediaType.MULTIPART_FORM_DATA_VALUE,
        "multipart/related",
        "application/xop+xml",
        MediaType.TEXT_XML_VALUE,
        MediaType.APPLICATION_XML_VALUE,
        MediaType.ALL_VALUE
    })
    public ResponseEntity<String> ingest(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestBody(required = false) String body,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        String interactionId = (String) request.getAttribute(Constants.INTERACTION_ID);
        LOG.info("DataIngestionController:: Received ingest request. interactionId={}", interactionId);

        // Get the request port from x-forwarded-port header
        String portHeader = headers.getOrDefault(Constants.REQ_X_FORWARDED_PORT, headers.getOrDefault("x-forwarded-port", null));
        int requestPort = -1;
        if (portHeader != null) {
            try {
                requestPort = Integer.parseInt(portHeader);
            } catch (NumberFormatException e) {
                LOG.error("Invalid x-forwarded-port header value: {}", portHeader);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Header 'x-forwarded-port' is not set properly with a valid port number");
            }
        } else {
            LOG.error("Missing x-forwarded-port header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Header 'x-forwarded-port' is not set properly with a valid port number");
        }
        LOG.info("DataIngestionController:: Request received on port {}", requestPort);

        // Check port config for matching port and route == "/hold"
        if (portConfig.isLoaded()) {
            for (PortConfig.PortEntry entry : portConfig.getPortConfigurationList()) {
                if (entry.port == requestPort && "/hold".equals(entry.route)) {
                    LOG.info("DataIngestionController: Matched /hold route for port {}. Forwarding to /hold. request={}, response={}", requestPort, request, response);
                    if (response == null) {
                        LOG.error("DataIngestionController: HttpServletResponse is null before forwarding to /hold! This will cause a NullPointerException.");
                    }
                    try {
                        request.getRequestDispatcher("/hold").forward(request, response);
                        LOG.info("DataIngestionController: Successfully forwarded to /hold for port {}", requestPort);
                    } catch (Exception ex) {
                        LOG.error("DataIngestionController: Exception while forwarding to /hold for port {}: {}", requestPort, ex.getMessage(), ex);
                        throw ex;
                    }
                    return ResponseEntity.status(HttpStatus.OK).body("Forwarded to /hold");
                }
            }
        } else {
            String errorMsg = "Failed to load port configuration JSON from S3";
            LOG.error(errorMsg);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMsg);
        }

        Map<String, String> responseMap;

        if (file != null && !file.isEmpty()) {
            LOG.info("DataIngestionController:: File received: {} ({} bytes). interactionId={}",
                    file.getOriginalFilename(), file.getSize(), interactionId);
            RequestContext context = createRequestContext(interactionId,
                    headers, request, file.getSize(), file.getOriginalFilename());
            responseMap = messageProcessorService.processMessage(context, file);

        } else if (body != null && !body.isBlank()) {
            String contentType = request.getContentType();
            String extension = HttpUtil.resolveExtension(contentType);
            String generatedFileName = "payload-" + UUID.randomUUID() + extension;
            LOG.info("DataIngestionController:: Raw body received (Content-Type={}): {}... interactionId={}",
                    contentType, body.substring(0, Math.min(200, body.length())), interactionId);
            RequestContext context = createRequestContext(interactionId,
                    headers, request, body.length(), generatedFileName);
            responseMap = messageProcessorService.processMessage(context, body);

        } else {
            LOG.warn("DataIngestionController:: Neither file nor body provided. interactionId={}", interactionId);
            throw new IllegalArgumentException("Request must contain either a file or body data");
        }
        LOG.info("DataIngestionController:: Ingestion processed successfully. interactionId={}", interactionId);
        String responseJson = objectMapper.writeValueAsString(responseMap);
        LOG.info("DataIngestionController:: Returning response for interactionId={}", interactionId);
        return ResponseEntity.ok(responseJson);
    }

    @Override
    public MessageSourceType getMessageSource() {
        return MessageSourceType.HTTP_INGEST;
    }

    @Override
    public String getDataBucketName() {
        // return default bucket only — do not append per-port dataDir here
        return appConfig.getAws().getS3().getDefaultConfig().getBucket();
    }

    @Override
    public String getMetadataBucketName() {
        // return default metadata bucket only — do not append per-port metadataDir here
        return appConfig.getAws().getS3().getDefaultConfig().getMetadataBucket();
    }

    /**
     * Build the full S3 data path (bucket + optional per-port dataDir + key)
     */
    @Override
    public String getFullS3DataPath(String interactionId, Map<String, String> headers, String originalFileName,String timestamp) {
        String bucket = getDataBucketName();
        String prefix = "";
        try {
            String portHeader = headers != null ? headers.getOrDefault("x-server-port", headers.getOrDefault("X-Server-Port", null)) : null;
            if (portHeader == null) {
                portHeader = HttpUtil.extractDestinationPort(headers);
            }
            if (portHeader != null) {
                try {
                    int requestPort = Integer.parseInt(portHeader);
                    if (!portConfig.isLoaded()) {
                        portConfig.loadConfig();
                    }
                    if (portConfig.isLoaded()) {
                        for (PortConfig.PortEntry entry : portConfig.getPortConfigurationList()) {
                            if (entry.port == requestPort) {
                                String dataDir = entry.dataDir;
                                if (dataDir != null && !dataDir.isBlank()) {
                                    String normalized = dataDir.replaceAll("^/+", "").replaceAll("/+$", "");
                                    if (!normalized.isEmpty()) {
                                        prefix = normalized + "/";
                                    }
                                }
                                break;
                            }
                        }
                    } else {
                        LOG.warn("PortConfig not loaded; using default data bucket and no prefix");
                    }
                } catch (NumberFormatException nfe) {
                    LOG.warn("Invalid x-server-port header value: {} — using default bucket and no prefix", portHeader);
                }
            }
        } catch (Exception e) {
            LOG.error("Error resolving per-port dataDir prefix for full S3 data path", e);
        }

        String key = getDataKey(interactionId, headers, originalFileName,timestamp);
        String fullKey = (prefix.isEmpty() ? "" : prefix) + key;
        return Constants.S3_PREFIX + bucket + "/" + fullKey;
    }

    /**
     * Build the metadata key including optional per-port metadataDir prefix.
     */
    @Override
    public String getMetaDataKey(String interactionId, Map<String, String> headers, String originalFileName,String timestamp) {
        String prefix = "";
        try {
            String portHeader = headers != null ? headers.getOrDefault("x-server-port", headers.getOrDefault("X-Server-Port", null)) : null;
            if (portHeader == null) {
                portHeader = HttpUtil.extractDestinationPort(headers);
            }
            if (portHeader != null) {
                try {
                    int requestPort = Integer.parseInt(portHeader);
                    if (!portConfig.isLoaded()) {
                        portConfig.loadConfig();
                    }
                    if (portConfig.isLoaded()) {
                        for (PortConfig.PortEntry entry : portConfig.getPortConfigurationList()) {
                            if (entry.port == requestPort) {
                                String metadataDir = entry.metadataDir;
                                if (metadataDir != null && !metadataDir.isBlank()) {
                                    String normalized = metadataDir.replaceAll("^/+", "").replaceAll("/+$", "");
                                    if (!normalized.isEmpty()) {
                                        prefix = normalized + "/";
                                    }
                                }
                                break;
                            }
                        }
                    } else {
                        LOG.warn("PortConfig not loaded; using default metadata key and no prefix");
                    }
                } catch (NumberFormatException nfe) {
                    LOG.warn("Invalid x-server-port header value: {} — using default metadata key and no prefix", portHeader);
                }
            }
        } catch (Exception e) {
            LOG.error("Error resolving per-port metadataDir prefix for metadata key", e);
        }

        // build metadata relative key (same semantics as previous default)
        Instant now = Instant.now();
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);
        String datePath = uploadTime.format(Constants.DATE_PATH_FORMATTER);
        String metaKey = String.format("metadata/%s/%s_%s_metadata.json", datePath, interactionId, timestamp);

        return (prefix.isEmpty() ? "" : prefix) + metaKey;
    }
}