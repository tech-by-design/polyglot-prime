package org.techbd.ingest.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.techbd.ingest.AbstractMessageSourceProvider;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.service.SoapForwarderService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/xds")
public class XdsRepositoryController extends AbstractMessageSourceProvider {

    private static final String XDS_SOURCE_ID = "xds";
    private static final String XDS_MSG_TYPE  = "ws";

    private final SoapForwarderService forwarder;
    private final TemplateLogger LOG;

    public XdsRepositoryController(
            AppConfig appConfig,
            AppLogger appLogger,
            SoapForwarderService forwarder) {
        super(appConfig, appLogger);
        this.forwarder = forwarder;
        this.LOG = appLogger.getLogger(XdsRepositoryController.class);
        LOG.info("XdsRepositoryController initialized");
    }

    @PostMapping(
        value = "/XDSbRepositoryWS",
        consumes = {
            "text/xml",
            "application/soap+xml",
            "application/xop+xml",
            "multipart/related",
            "*/*"
        }
    )
    public ResponseEntity<String> handleXdsRequest(
            @RequestBody(required = false) String body,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) throws Exception {

        String interactionId = (String) request.getAttribute(Constants.INTERACTION_ID);

        LOG.info("XDS request received. interactionId={}", interactionId);

        if (body == null || body.isBlank()) {
            LOG.warn("Empty XDS SOAP request. interactionId={}", interactionId);
            // Reuse the same pattern as DataIngestionController – forward with blank body
            // so SoapForwarderService can produce a proper fault, or handle here as needed.
            return forwarder.forward(request, "", null, null, interactionId);
        }

        LOG.info("Forwarding XDS SOAP request to /ws. interactionId={}", interactionId);
        return forwarder.forward(request, body, null, null, interactionId);
    }

    @Override
    public MessageSourceType getMessageSource() {
        return MessageSourceType.HTTP_INGEST;
    }
}