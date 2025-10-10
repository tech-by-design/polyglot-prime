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
        return appConfig.getAws().getS3().getHoldConfig().getBucket();
    }

    @Override
    public String getMetadataBucketName() {
        return appConfig.getAws().getS3().getHoldConfig().getMetadataBucket();
    }

    /**
     * Build the metadata key including optional per-port metadataDir prefix.
     */
    @Override
    public String getDataKey(String interactionId, Map<String, String> headers, String originalFileName, String timestamp) {
        String prefix = "";
        int requestPort = -1;
        try {
            String portHeader = headers != null ? headers.getOrDefault(Constants.REQ_X_FORWARDED_PORT, headers.getOrDefault(Constants.REQ_X_FORWARDED_PORT, null)) : null;
            if (portHeader == null) {
                portHeader = HttpUtil.extractDestinationPort(headers);
            }
            if (portHeader != null) {
                try {
                    requestPort = Integer.parseInt(portHeader);
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
                        LOG.warn("PortConfig not loaded; using default metadata key and no prefix");
                    }
                } catch (NumberFormatException nfe) {
                    LOG.warn("Invalid x-forwarded-port header value: {} — using default metadata key and no prefix", portHeader);
                }
            }
        } catch (Exception e) {
            LOG.error("Error resolving per-port metadataDir prefix for metadata key", e);
        }

        // compute upload date parts (UTC)
        Instant now = Instant.now();
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);
        String yyyy = String.format("%04d", uploadTime.getYear());
        String mm = String.format("%02d", uploadTime.getMonthValue());
        String dd = String.format("%02d", uploadTime.getDayOfMonth());

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

        // final path: /hold/{destination_port}/{YYYY}/{MM}/{DD}/{timestamp_filename}.{extension}
        String dataKey = String.format("/hold/%d/%s/%s/%s/%s", requestPort, yyyy, mm, dd, timestampedName);

        return (prefix.isEmpty() ? "" : prefix) + dataKey;
    }

    /**
     * Build the metadata key including optional per-port metadataDir prefix.
     */
    @Override
    public String getMetaDataKey(String interactionId, Map<String, String> headers, String originalFileName, String timestamp) {
        String prefix = "";
        try {
            String portHeader = headers != null ? headers.getOrDefault(Constants.REQ_X_FORWARDED_PORT, headers.getOrDefault(Constants.REQ_X_FORWARDED_PORT, null)) : null;
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
