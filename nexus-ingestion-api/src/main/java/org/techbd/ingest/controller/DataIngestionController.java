
package org.techbd.ingest.controller;

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
     * This endpoint can accept either:
     * - a file upload (multipart/form-data)
     * - raw data in the body (JSON, XML, plain text, HL7, etc.)
     *
     * If raw body data is provided, a filename is generated
     * based on the Content-Type header.
     *
     * @param file    The optional file to be ingested (when multipart/form-data).
     * @param body    The optional raw payload (when Content-Type is JSON/XML/Text).
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
            HttpServletRequest request) throws Exception {
        String interactionId = (String) request.getAttribute(Constants.INTERACTION_ID);
        LOG.info("DataIngestionController:: Received ingest request. interactionId={}", interactionId);

        // Get the request port from x-server-port header
        String portHeader = headers.getOrDefault("x-server-port", headers.getOrDefault("X-Server-Port", null));
        int requestPort = -1;
        if (portHeader != null) {
            try {
                requestPort = Integer.parseInt(portHeader);
            } catch (NumberFormatException e) {
                LOG.error("Invalid x-server-port header value: {}", portHeader);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Header 'x-server-port' is not set properly with a valid port number");
            }
        } else {
            LOG.error("Missing x-server-port header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Header 'x-server-port' is not set properly with a valid port number");
        }
        LOG.info("DataIngestionController:: Request received on port {}", requestPort);

        // Check port config for matching port and routeTo == "/hold"
        if (portConfig.isLoaded()) {
            for (PortConfig.PortEntry entry : portConfig.getPortConfigurationList()) {
                if (entry.port == requestPort && "/hold".equals(entry.route)) {
                    LOG.info("DataIngestionController redirecting to /hold for port {}", requestPort);
                    // Forward the request to /hold (internal forward, not HTTP redirect)
                    request.getRequestDispatcher("/hold").forward(request, null);
                    return null; // Response handled by /hold
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
        String defaultBucket = appConfig.getAws().getS3().getDefaultConfig().getBucket();

        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return defaultBucket;
            }
            HttpServletRequest req = attrs.getRequest();
            String portHeader = req.getHeader("x-server-port");
            if (portHeader == null) {
                portHeader = req.getHeader("X-Server-Port");
            }
            if (portHeader == null) {
                return defaultBucket;
            }
            int requestPort;
            try {
                requestPort = Integer.parseInt(portHeader);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid x-server-port header value: {}. Using default bucket", portHeader);
                return defaultBucket;
            }

            if (!portConfig.isLoaded()) {
                // attempt to load if not loaded
                portConfig.loadConfig();
            }

            if (portConfig.isLoaded()) {
                for (PortConfig.PortEntry entry : portConfig.getPortConfigurationList()) {
                    if (entry.port == requestPort) {
                        String dataDir = entry.dataDir;
                        if (dataDir != null && !dataDir.isBlank()) {
                            // normalize: strip leading/trailing slashes
                            String normalized = dataDir.replaceAll("^/+", "").replaceAll("/+$", "");
                            if (normalized.isEmpty()) {
                                return defaultBucket;
                            }
                            return defaultBucket + "/" + normalized;
                        }
                        break; // found entry but no dataDir
                    }
                }
            } else {
                LOG.warn("PortConfig not loaded; using default bucket");
            }
        } catch (Exception e) {
            LOG.error("Error while resolving data bucket name from port config", e);
        }

        return defaultBucket;
    }

    @Override
    public String getMetadataBucketName() {
        String defaultBucket = appConfig.getAws().getS3().getDefaultConfig().getMetadataBucket();

        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return defaultBucket;
            }
            HttpServletRequest req = attrs.getRequest();
            String portHeader = req.getHeader("x-server-port");
            if (portHeader == null) {
                portHeader = req.getHeader("X-Server-Port");
            }
            if (portHeader == null) {
                return defaultBucket;
            }
            int requestPort;
            try {
                requestPort = Integer.parseInt(portHeader);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid x-server-port header value: {}. Using default metadata bucket", portHeader);
                return defaultBucket;
            }

            if (!portConfig.isLoaded()) {
                portConfig.loadConfig();
            }

            if (portConfig.isLoaded()) {
                for (PortConfig.PortEntry entry : portConfig.getPortConfigurationList()) {
                    if (entry.port == requestPort) {
                        String metadataDir = entry.metadataDir;
                        if (metadataDir != null && !metadataDir.isBlank()) {
                            String normalized = metadataDir.replaceAll("^/+", "").replaceAll("/+$", "");
                            if (normalized.isEmpty()) {
                                return defaultBucket;
                            }
                            return defaultBucket + "/" + normalized;
                        }
                        break; // found entry but no metadataDir
                    }
                }
            } else {
                LOG.warn("PortConfig not loaded; using default metadata bucket");
            }
        } catch (Exception e) {
            LOG.error("Error while resolving metadata bucket name from port config", e);
        }

        return defaultBucket;
    }
}
