package org.techbd.ingest.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.client.core.WebServiceMessageExtractor;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpUrlConnection;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.exceptions.ErrorTraceIdGenerator;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.LogUtil;
import org.techbd.ingest.util.TemplateLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.MimeHeaders;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;

@Service
public class SoapForwarderService {

    private final WebServiceTemplate webServiceTemplate;
    private final TemplateLogger LOG;

    public SoapForwarderService(WebServiceTemplate webServiceTemplate, AppLogger appLogger) {
        this.webServiceTemplate = webServiceTemplate;
        this.LOG = appLogger.getLogger(SoapForwarderService.class);
    }

    /**
     * Forwards the SOAP request to the /ws endpoint.
     * Handles both SOAP 1.1 and SOAP 1.2 protocols.
     * Supports multipart/related (MTOM) messages with attachments.
     * 
     * The entire raw message (including attachments if present) is forwarded as-is.
     * The /ws endpoint handles all processing including S3 upload.
     * 
     * @param request The original HTTP request
     * @param body The raw SOAP message body (may include multipart content)
     * @param sourceId The source identifier from the URL path
     * @param msgType The message type from the URL path
     * @param interactionId The interaction ID for tracking
     * @return ResponseEntity with SOAP response or fault
     */
    public ResponseEntity<String> forward(HttpServletRequest request, String body, 
                                          String sourceId, String msgType, String interactionId) {
        String errorTraceId = null;
        
        try {
            String contentType = request.getContentType();
            LOG.info("SoapForwarderService:: Forwarding request. ContentType={} sourceId={} msgType={} interactionId={}", 
                contentType, sourceId, msgType, interactionId);

            // Create SOAP message from raw body
            SOAPMessage soapRequest = createSoapMessage(body, contentType);
            
            // Determine target URL
            String targetUrl = getBaseUrl(request) + "/ws";
            LOG.info("SoapForwarderService:: Forwarding to URL={}", targetUrl);

            // Forward the request and get response using WebServiceTemplate
            String soapResponse = webServiceTemplate.sendAndReceive(
                targetUrl,
                new WebServiceMessageCallback() {
                    @Override
                    public void doWithMessage(WebServiceMessage message) throws IOException {
                        try {
                            SaajSoapMessage saajMessage = (SaajSoapMessage) message;
                            SOAPMessage msg = saajMessage.getSaajMessage();
                            
                            // Add custom headers to the transport
                            addCustomHeaders(request, sourceId, msgType, interactionId);
                            
                            // Copy the SOAP Part (envelope + body)
                            msg.getSOAPPart().setContent(soapRequest.getSOAPPart().getContent());
                            
                            // Copy all attachments if present (for MTOM messages)
                            if (soapRequest.countAttachments() > 0) {
                                LOG.debug("SoapForwarderService:: Copying {} attachments", 
                                    soapRequest.countAttachments());
                                    
                                soapRequest.getAttachments().forEachRemaining(attachment -> {
                                    try {
                                        msg.addAttachmentPart(attachment);
                                    } catch (Exception e) {
                                        LOG.error("SoapForwarderService:: Error copying attachment", e);
                                    }
                                });
                            }
                            
                            msg.saveChanges();
                            
                            LOG.debug("SoapForwarderService:: SOAP message prepared with {} attachments", 
                                msg.countAttachments());
                                
                        } catch (Exception e) {
                            throw new IOException("Failed to prepare SOAP message", e);
                        }
                    }
                },
                new WebServiceMessageExtractor<String>() {
                    @Override
                    public String extractData(WebServiceMessage message) throws IOException {
                        try {
                            SaajSoapMessage saajMessage = (SaajSoapMessage) message;
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            saajMessage.getSaajMessage().writeTo(out);
                            return out.toString(StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            throw new IOException("Failed to extract SOAP response", e);
                        }
                    }
                }
            );

            LOG.info("SoapForwarderService:: Successfully forwarded request and received response. interactionId={}", 
                interactionId);
            
            // Determine response content type based on SOAP version
            String soapVersion = determineSoapVersion(body);
            String responseContentType = soapVersion.equals(SOAPConstants.SOAP_1_2_PROTOCOL)
                ? "application/soap+xml; charset=utf-8"
                : "text/xml; charset=utf-8";

            return ResponseEntity
                .ok()
                .header("Content-Type", responseContentType)
                .body(soapResponse);

        } catch (Exception e) {
            // Generate error trace ID for forwarding errors
            errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
            
            LOG.error("SoapForwarderService:: Error forwarding request. interactionId={}, errorTraceId={}, error={}", 
                interactionId, errorTraceId, e.getMessage(), e);
            
            // Log detailed error to CloudWatch
            LogUtil.logDetailedError(
                500, 
                "SOAP forwarding error", 
                interactionId, 
                errorTraceId, 
                e
            );
            
            // Determine SOAP version for fault response
            String soapVersion = determineSoapVersion(body);
            
            return ResponseEntity
                .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", soapVersion.equals(SOAPConstants.SOAP_1_2_PROTOCOL) 
                    ? "application/soap+xml; charset=utf-8" 
                    : "text/xml; charset=utf-8")
                .body(createSoapFault(e.getMessage(), soapVersion, interactionId, errorTraceId));
        }
    }

    public static String getBaseUrl(HttpServletRequest request) {
        String proto = Optional.ofNullable(request.getHeader("X-Forwarded-Proto"))
                .orElse(request.getScheme());

        String host = Optional.ofNullable(request.getHeader("X-Forwarded-Host"))
                .orElse(request.getServerName());
        String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        int port;
        boolean skipPort = false;

        if ("sandbox".equals(activeProfile)) {
            port = request.getServerPort();
            skipPort = false;
        } else {
            port = Optional.ofNullable(request.getHeader("X-Forwarded-Port"))
                    .map(Integer::parseInt)
                    .orElse(request.getServerPort());
            skipPort = (proto.equals("http") && port == 80) || (proto.equals("https") && port == 443) || (proto.equals("https") && port == 80);
        }

        if (skipPort) {
            return proto + "://" + host;
        }
        return proto + "://" + host + ":" + port;
    }


    /**
     * Adds custom HTTP headers to the SOAP request for routing parameters.
     * These headers can be extracted by the /ws endpoint for processing.
     */
    private void addCustomHeaders(HttpServletRequest request, String sourceId, String msgType, String interactionId) {
        try {
            TransportContext context = TransportContextHolder.getTransportContext();
            if (context != null && context.getConnection() instanceof HttpUrlConnection) {
                HttpUrlConnection connection = (HttpUrlConnection) context.getConnection();
                
                // Add custom routing headers
                if (sourceId != null && !sourceId.isBlank()) {
                    connection.addRequestHeader(Constants.HEADER_SOURCE_ID, sourceId);
                    LOG.debug("SoapForwarderService:: Added header {}={}", Constants.HEADER_SOURCE_ID, sourceId);
                }
                
                if (msgType != null && !msgType.isBlank()) {
                    connection.addRequestHeader(Constants.HEADER_MSG_TYPE, msgType);
                    LOG.debug("SoapForwarderService:: Added header {}={}", Constants.HEADER_MSG_TYPE, msgType);
                }
                
                if (interactionId != null && !interactionId.isBlank()) {
                    connection.addRequestHeader(Constants.HEADER_INTERACTION_ID, interactionId);
                    LOG.debug("SoapForwarderService:: Added header {}={}", Constants.HEADER_INTERACTION_ID, interactionId);
                }
                String ackContentType = (String) request.getAttribute(Constants.ACK_CONTENT_TYPE);
                if (ackContentType != null) {
                    connection.addRequestHeader(Constants.ACK_CONTENT_TYPE, ackContentType.toString());
                    LOG.debug("SoapForwarderService:: Forwarded ACK_CONTENT_TYPE={}", ackContentType);
                }
                // Copy important headers from original request
                copyRelevantHeaders(request, connection);
            }
        } catch (Exception e) {
            LOG.warn("SoapForwarderService:: Failed to add custom headers: {}", e.getMessage());
        }
    }

    /**
     * Copies all HTTP headers from the incoming request to the outgoing
     * HttpUrlConnection.
     * Automatically excludes headers that cannot be set on HttpUrlConnection.
     */
    private void copyRelevantHeaders(HttpServletRequest request, HttpUrlConnection connection) throws IOException {

        // Headers that HttpUrlConnection does NOT allow clients to set
        Set<String> restrictedHeaders = Set.of(
                "host", "connection", "content-length", "transfer-encoding",
                "expect", "upgrade");

        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();

            if (headerName == null)
                continue;

            String lower = headerName.toLowerCase();

            // Skip restricted headers
            if (restrictedHeaders.contains(lower)) {
                LOG.debug("SoapForwarderService:: Skipping restricted header: {}", headerName);
                continue;
            }

            Enumeration<String> headerValues = request.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                String value = headerValues.nextElement();

                if (value == null)
                    continue;

                connection.addRequestHeader(headerName, value);
                LOG.debug("SoapForwarderService:: Copied header: {} = {}", headerName, value);
            }
        }
    }

    /**
     * Creates a SOAPMessage from raw XML string.
     * Automatically detects SOAP version and handles MTOM multipart messages.
     * 
     * @param body The raw SOAP message body
     * @param contentType The Content-Type header from the request
     * @return SOAPMessage object ready to be forwarded
     */
    private SOAPMessage createSoapMessage(String body, String contentType) throws SOAPException, IOException {
        MimeHeaders headers = new MimeHeaders();
        
        // Check if it's MTOM/multipart content
        if (contentType != null && contentType.contains("multipart/related")) {
            LOG.debug("SoapForwarderService:: Processing multipart/related (MTOM) message");
            // For MTOM messages, preserve the original Content-Type
            headers.addHeader("Content-Type", contentType);
            // Use DYNAMIC protocol to auto-detect version
            MessageFactory msgFactory = MessageFactory.newInstance(SOAPConstants.DYNAMIC_SOAP_PROTOCOL);
            return msgFactory.createMessage(headers, 
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        }
        
        // For regular SOAP messages, detect version and set appropriate Content-Type
        String soapProtocol = determineSoapVersion(body);
        
        if (soapProtocol.equals(SOAPConstants.SOAP_1_2_PROTOCOL)) {
            LOG.debug("SoapForwarderService:: Processing SOAP 1.2 message");
            headers.addHeader("Content-Type", "application/soap+xml; charset=utf-8");
        } else {
            LOG.debug("SoapForwarderService:: Processing SOAP 1.1 message");
            headers.addHeader("Content-Type", "text/xml; charset=utf-8");
        }
        
        MessageFactory msgFactory = MessageFactory.newInstance(soapProtocol);
        return msgFactory.createMessage(headers, 
            new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Detects SOAP version by examining the namespace in the envelope.
     * 
     * @param soapXml The SOAP XML string
     * @return SOAP protocol constant (SOAP_1_1_PROTOCOL or SOAP_1_2_PROTOCOL)
     */
    private String determineSoapVersion(String soapXml) {
        if (soapXml == null) {
            LOG.debug("SoapForwarderService:: Null SOAP XML, defaulting to SOAP 1.1");
            return SOAPConstants.SOAP_1_1_PROTOCOL;
        }
        
        // Check for SOAP 1.2 namespace
        if (soapXml.contains("http://www.w3.org/2003/05/soap-envelope")) {
            LOG.debug("SoapForwarderService:: Detected SOAP 1.2 namespace");
            return SOAPConstants.SOAP_1_2_PROTOCOL;
        }
        
        // Check for SOAP 1.1 namespace
        if (soapXml.contains("http://schemas.xmlsoap.org/soap/envelope/")) {
            LOG.debug("SoapForwarderService:: Detected SOAP 1.1 namespace");
            return SOAPConstants.SOAP_1_1_PROTOCOL;
        }
        
        // Default to SOAP 1.1
        LOG.debug("SoapForwarderService:: No SOAP namespace detected, defaulting to SOAP 1.1");
        return SOAPConstants.SOAP_1_1_PROTOCOL;
    }

    /**
     * Creates a SOAP fault message for error responses.
     * Creates SOAP 1.1 or 1.2 fault based on the protocol version.
     * MODIFIED: Added interactionId and errorTraceId parameters
     * 
     * @param errorMessage The error message
     * @param soapProtocol The SOAP protocol version
     * @param interactionId The interaction ID for tracking
     * @param errorTraceId The error trace ID for debugging
     * @return SOAP fault XML string
     */
    private String createSoapFault(String errorMessage, String soapProtocol, String interactionId, String errorTraceId) {
        if (soapProtocol.equals(SOAPConstants.SOAP_1_2_PROTOCOL)) {
            // SOAP 1.2 Fault with error details
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope" xmlns:err="http://techbd.org/errorinfo">
                    <env:Body>
                        <env:Fault>
                            <env:Code>
                                <env:Value>env:Receiver</env:Value>
                            </env:Code>
                            <env:Reason>
                                <env:Text xml:lang="en">%s</env:Text>
                            </env:Reason>
                            <env:Detail>
                                <err:InteractionId>%s</err:InteractionId>
                                <err:ErrorTraceId>%s</err:ErrorTraceId>
                            </env:Detail>
                        </env:Fault>
                    </env:Body>
                </env:Envelope>
                """.formatted(escapeXml(errorMessage), escapeXml(interactionId), escapeXml(errorTraceId));
        } else {
            // SOAP 1.1 Fault with error details
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:err="http://techbd.org/errorinfo">
                    <soap:Body>
                        <soap:Fault>
                            <faultcode>soap:Server</faultcode>
                            <faultstring>%s</faultstring>
                            <detail>
                                <err:InteractionId>%s</err:InteractionId>
                                <err:ErrorTraceId>%s</err:ErrorTraceId>
                            </detail>
                        </soap:Fault>
                    </soap:Body>
                </soap:Envelope>
                """.formatted(escapeXml(errorMessage), escapeXml(interactionId), escapeXml(errorTraceId));
        }
    }

    /**
     * Escapes special XML characters in the error message.
     */
    private String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}