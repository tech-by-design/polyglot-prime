
package org.techbd.ingest.controller;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
import org.techbd.ingest.config.PortConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import lombok.extern.slf4j.Slf4j;


@RestController
@Slf4j
public class DataHoldController extends AbstractMessageSourceProvider {
    private static TemplateLogger LOG;
    private final MessageProcessorService messageProcessorService;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;
    private final PortConfig portConfig;

    public DataHoldController(MessageProcessorService messageProcessorService, ObjectMapper objectMapper,
            AppConfig appConfig, AppLogger appLogger, PortConfig portConfig) {
        super(appConfig, appLogger);
        this.messageProcessorService = messageProcessorService;
        this.objectMapper = objectMapper;
        this.appConfig = appConfig;
        this.portConfig = portConfig;
        LOG = appLogger.getLogger(DataHoldController.class);
        LOG.info("DataHoldController initialized");
    }

    /**
     * Endpoint to handle /hold requests.
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
    @PostMapping(value = "/hold", consumes = {
            MediaType.MULTIPART_FORM_DATA_VALUE,
            "multipart/related", // MTOM support
            "application/xop+xml", // XOP support
            MediaType.ALL_VALUE
    })
    public ResponseEntity<String> hold(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestBody(required = false) String body,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) throws Exception {
        String interactionId = (String) request.getAttribute(Constants.INTERACTION_ID);
        LOG.info("DataHoldController:: Received ingest request. interactionId={}", interactionId);

        // Print the content of the config JSON loaded from S3
        if (portConfig.isLoaded()) {
            LOG.info("PortConfig loaded from S3: {}", objectMapper.writeValueAsString(portConfig.getPortConfigurationList()));
        } else {
            LOG.warn("PortConfig not loaded from S3!");
        }

        Map<String, String> responseMap;

        if (file != null && !file.isEmpty()) {
            LOG.info("DataHoldController:: File received: {} ({} bytes). interactionId={}",
                    file.getOriginalFilename(), file.getSize(), interactionId);
            RequestContext context = createRequestContext(interactionId,
                    headers, request, file.getSize(), file.getOriginalFilename());
            responseMap = messageProcessorService.processMessage(context, file);

        } else if (body != null && !body.isBlank()) {
            String contentType = request.getContentType();
            String extension = HttpUtil.resolveExtension(contentType);
            String generatedFileName = "payload-" + UUID.randomUUID() + extension;
            LOG.info("DataHoldController:: Raw body received (Content-Type={}): {}... interactionId={}",
                    contentType, body.substring(0, Math.min(200, body.length())), interactionId);
            RequestContext context = createRequestContext(interactionId,
                    headers, request, body.length(), generatedFileName);
            responseMap = messageProcessorService.processMessage(context, body);

        } else {
            LOG.warn("DataHoldController:: Neither file nor body provided. interactionId={}", interactionId);
            throw new IllegalArgumentException("Request must contain either a file or body data");
        }
        LOG.info("DataHoldController:: Ingestion processed successfully. interactionId={}", interactionId);
        String responseJson = objectMapper.writeValueAsString(responseMap);
        LOG.info("DataHoldController:: Returning response for interactionId={}", interactionId);
        return ResponseEntity.ok(responseJson);
    }

    @Override
    public MessageSourceType getMessageSource() {
        return MessageSourceType.HTTP_HOLD;
    }

    @Override
    public String getDataBucketName() {
        String defaultBucket = appConfig.getAws().getS3().getHoldConfig().getBucket();

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
                log.warn("Invalid x-server-port header value: {}. Using default hold bucket", portHeader);
                return defaultBucket;
            }

            if (!portConfig.isLoaded()) {
                portConfig.loadConfig();
            }

            if (portConfig.isLoaded()) {
                for (PortConfig.PortEntry entry : portConfig.getPortConfigurationList()) {
                    if (entry.port == requestPort) {
                        String dataDir = entry.dataDir;
                        if (dataDir != null && !dataDir.isBlank()) {
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
                log.warn("PortConfig not loaded; using default hold bucket");
            }
        } catch (Exception e) {
            log.error("Error while resolving data bucket name from port config", e);
        }

        return defaultBucket;
    }

    @Override
    public String getMetadataBucketName() {
        String defaultBucket = appConfig.getAws().getS3().getHoldConfig().getMetadataBucket();

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
                log.warn("Invalid x-server-port header value: {}. Using default hold metadata bucket", portHeader);
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
                log.warn("PortConfig not loaded; using default hold metadata bucket");
            }
        } catch (Exception e) {
            log.error("Error while resolving metadata bucket name from port config", e);
        }

        return defaultBucket;
    }

    @Override
    public String getDataKey(String interactionId, Map<String, String> headers, String originalFileName,String timestamp) {
        // Build S3 key:
        // hold/{destination_port}/{YYYY}/{MM}/{DD}/{timestamp_filename}.{extension}
        Instant currentTime = Instant.now();
        ZonedDateTime now = currentTime.atZone(ZoneOffset.UTC);
        String yyyy = String.format("%04d", now.getYear());
        String mm = String.format("%02d", now.getMonthValue());
        String dd = String.format("%02d", now.getDayOfMonth());
        String extension = "";
        int dotIdx = originalFileName.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < originalFileName.length() - 1) {
            extension = originalFileName.substring(dotIdx + 1);
        }
        String timestampFileName = timestamp + "_" + originalFileName;
        return String.format("hold/%s/%s/%s/%s/%s%s%s",
                getDestinationPort(headers), yyyy, mm, dd, timestampFileName,
                extension.isEmpty() ? "" : ".", extension);
    }

    @Override
    public String getMetaDataKey(String interactionId, Map<String, String> headers, String originalFileName,String timestamp) {
        return getDataKey(interactionId, headers, originalFileName,timestamp) + "_metadata.json";
    }

    @Override
    public String getAcknowledgementKey(String interactionId, Map<String, String> headers, String originalFileName,String timestamp) {
        return getDataKey(interactionId, headers, originalFileName,timestamp) + ".ack.json";
    }
}
