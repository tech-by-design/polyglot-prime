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
        // return default hold bucket only
        return appConfig.getAws().getS3().getHoldConfig().getBucket();
    }

    @Override
    public String getMetadataBucketName() {
        // return default hold metadata bucket only
        return appConfig.getAws().getS3().getHoldConfig().getMetadataBucket();
    }

    /**
     * For hold controller, build full S3 data path using per-port dataDir prefix if present.
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
                        log.warn("PortConfig not loaded; using default hold bucket and no prefix");
                    }
                } catch (NumberFormatException nfe) {
                    log.warn("Invalid x-server-port header value: {} — using default hold bucket and no prefix", portHeader);
                }
            }
        } catch (Exception e) {
            log.error("Error resolving per-port dataDir prefix for hold full S3 data path", e);
        }

        String key = getDataKey(interactionId, headers, originalFileName, timestamp);
        String fullKey = (prefix.isEmpty() ? "" : prefix) + key;
        return Constants.S3_PREFIX + bucket + "/" + fullKey;
    }

    /**
     * For hold controller, metadata key should include per-port metadataDir prefix if present.
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
                        log.warn("PortConfig not loaded; using default hold metadata key and no prefix");
                    }
                } catch (NumberFormatException nfe) {
                    log.warn("Invalid x-server-port header value: {} — using default hold metadata key and no prefix", portHeader);
                }
            }
        } catch (Exception e) {
            log.error("Error resolving per-port metadataDir prefix for hold metadata key", e);
        }

        // build metadata relative key (same semantics as previous default)
        Instant now = Instant.now();
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);
        String datePath = uploadTime.format(Constants.DATE_PATH_FORMATTER);
        String metaKey = String.format("metadata/%s/%s_%s_metadata.json", datePath, interactionId, timestamp);

        return (prefix.isEmpty() ? "" : prefix) + metaKey;
    }
}