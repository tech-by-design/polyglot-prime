package org.techbd.ingest.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.exceptions.ErrorTraceIdGenerator;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.LogUtil;
import org.techbd.ingest.util.TemplateLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.xml.soap.SOAPConstants;

@Service
public class SoapForwarderService {

    private final TemplateLogger LOG;

    private final HttpClient httpClient;

    public SoapForwarderService(AppLogger appLogger) {
        this.LOG = appLogger.getLogger(SoapForwarderService.class);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)   // /ws is HTTP/1.1; upgrade later if needed
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Primary entry point — always forwards as raw bytes to /ws.
     * sourceId and msgType from path are forwarded as headers.
     */
    public ResponseEntity<String> forward(HttpServletRequest request, byte[] rawBytes,
            String sourceId, String msgType, String interactionId) {
        String errorTraceId = null;
        String bodySnippet = rawBytes.length > 0
                ? new String(rawBytes, 0, Math.min(rawBytes.length, 4096), StandardCharsets.UTF_8)
                : "";
        try {
            String contentType = request.getContentType();
            String targetUrl = getBaseUrl(request, interactionId) + "/ws";
            LOG.info(
                    "SoapForwarderService:: Forwarding raw to targetUrl={} ContentType={} sourceId={} msgType={} interactionId={}",
                    targetUrl, contentType, sourceId, msgType, interactionId);
            return forwardRaw(request, rawBytes, contentType, targetUrl, sourceId, msgType, interactionId);
        } catch (Exception e) {
            errorTraceId = ErrorTraceIdGenerator.generateErrorTraceId();
            LOG.error("SoapForwarderService:: Forward error. interactionId={} errorTraceId={} error={}",
                    interactionId, errorTraceId, e.getMessage(), e);
            LogUtil.logDetailedError(500, "SOAP forwarding error", interactionId, errorTraceId, e);
            String soapVersion = determineSoapVersion(bodySnippet);
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", soapVersion.equals(SOAPConstants.SOAP_1_2_PROTOCOL)
                            ? "application/soap+xml; charset=utf-8"
                            : "text/xml; charset=utf-8")
                    .body(createSoapFault(e.getMessage(), soapVersion, interactionId, errorTraceId));
        }
    }

    /**
     * Convenience overload — when sourceId/msgType are not available.
     */
    public ResponseEntity<String> forward(HttpServletRequest request, byte[] rawBytes, String interactionId) {
        return forward(request, rawBytes, null, null, interactionId);
    }

    /**
     * Legacy String overload — keeps backward compatibility.
     */
    public ResponseEntity<String> forward(HttpServletRequest request, String body,
            String sourceId, String msgType, String interactionId) {
        byte[] bytes = (body != null) ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
        return forward(request, bytes, sourceId, msgType, interactionId);
    }

    /**
     * Pipes rawBytes directly to targetUrl using the shared HttpClient.
     * No SAAJ parsing — byte stream delivered exactly as received.
     * Content-Type is reconstructed from bytes if missing or not multipart/related,
     * so /ws (Spring-WS/SAAJ) can parse the MTOM message correctly.
     * All original request headers are forwarded except restricted ones.
     *
     * Key changes from HttpURLConnection:
     *  - Connection pooling: keep-alive sockets are reused across calls automatically.
     *  - No manual disconnect() — the HttpClient lifecycle manages connections.
     *  - Read timeout set per-request via HttpRequest.timeout().
     */
    private ResponseEntity<String> forwardRaw(HttpServletRequest request, byte[] rawBytes,
            String contentType, String targetUrl,
            String sourceId, String msgType,
            String interactionId) throws Exception {

        // ── Step 1: determine outbound Content-Type ───────────────────────────
        String outboundContentType = contentType;
        if (isFromXdsRepository(request)
                && (contentType == null || !contentType.toLowerCase().contains("multipart/related"))) {
            String reconstructed = buildMultipartContentType(rawBytes);
            if (reconstructed != null) {
                outboundContentType = reconstructed;
                LOG.info(
                        "SoapForwarderService:: Reconstructed Content-Type for /ws. original={} outbound={} interactionId={}",
                        contentType, outboundContentType, interactionId);
            }
        }

        // ── Step 2: build HttpRequest ─────────────────────────────────────────
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofByteArray(rawBytes));

        // Content-Type (possibly reconstructed)
        if (outboundContentType != null) {
            reqBuilder.header("Content-Type", outboundContentType);
        }

        // SOAPAction
        String soapAction = request.getHeader("SOAPAction");
        if (soapAction != null) {
            reqBuilder.header("SOAPAction", soapAction);
        }

        // Custom / correlation headers
        if (interactionId != null && !interactionId.isBlank()) {
            reqBuilder.header(Constants.HEADER_INTERACTION_ID, interactionId);
        }
        String ackContentType = (String) request.getAttribute(Constants.ACK_CONTENT_TYPE);
        if (ackContentType != null) {
            reqBuilder.header(Constants.ACK_CONTENT_TYPE, ackContentType);
        }
        if (sourceId != null && !sourceId.isBlank()) {
            reqBuilder.header(Constants.HEADER_SOURCE_ID, sourceId);
            LOG.info("SoapForwarderService:: Forwarding sourceId={} interactionId={}", sourceId, interactionId);
        }
        if (msgType != null && !msgType.isBlank()) {
            reqBuilder.header(Constants.HEADER_MSG_TYPE, msgType);
            LOG.info("SoapForwarderService:: Forwarding msgType={} interactionId={}", msgType, interactionId);
        }

        // mTLS verified attribute
        String mtlsVerified = (String) request.getAttribute(Constants.HEADER_MTLS_VERIFIED);
        if ("true".equals(mtlsVerified)) {
            reqBuilder.header(Constants.HEADER_MTLS_VERIFIED, "true");
        }

        // Forward ALL original request headers except restricted ones.
        // content-type is already set above (possibly reconstructed).
        // host, connection, content-length, transfer-encoding must not be forwarded —
        // HttpClient manages these automatically.
        Set<String> skip = Set.of(
                "host", "connection", "content-length", "transfer-encoding",
                "expect", "upgrade", "content-type");

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (name == null || skip.contains(name.toLowerCase())) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values != null && values.hasMoreElements()) {
                String value = values.nextElement();
                if (value != null) {
                    reqBuilder.header(name, value);
                    LOG.debug("SoapForwarderService:: Forwarding header {}={} interactionId={}", name, value,
                            interactionId);
                }
            }
        }

        // ── Step 3: send (blocking) and read response ─────────────────────────
        HttpResponse<byte[]> response = httpClient.send(
                reqBuilder.build(),
                HttpResponse.BodyHandlers.ofByteArray());

        int status = response.statusCode();
        String respContentType = response.headers()
                .firstValue("Content-Type")
                .orElse(null);

        LOG.info("SoapForwarderService:: Raw forward response. status={} contentType={} interactionId={}",
                status, respContentType, interactionId);

        String responseBody = new String(response.body(), StandardCharsets.UTF_8);

        return ResponseEntity.status(status)
                .header("Content-Type", respContentType != null
                        ? respContentType
                        : "text/xml; charset=utf-8")
                .body(responseBody);
    }

    private boolean isFromXdsRepository(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.toLowerCase().endsWith("/xds/xdsbrepositoryws");
    }

    /**
     * Reconstructs multipart/related Content-Type from raw bytes.
     * Called ONLY when the declared Content-Type is missing or not
     * multipart/related.
     * Extracts boundary, type, start (Content-ID), and start-info from the body
     * itself.
     * Returns null if body does not appear to be multipart.
     */
    private String buildMultipartContentType(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length < 4)
            return null;

        // ── Step 1: find '--' at start of body, skip leading CRLF/whitespace ──
        int dashPos = -1;
        for (int i = 0; i < Math.min(rawBytes.length - 1, 64); i++) {
            byte b = rawBytes[i];
            if (b == '\r' || b == '\n' || b == ' ' || b == '\t')
                continue;
            if (b == '-' && rawBytes[i + 1] == '-') {
                dashPos = i + 2;
                break;
            }
            break; // first non-whitespace is not '--' → not multipart
        }
        if (dashPos < 0)
            return null;

        // ── Step 2: extract boundary value up to end of first line ────────────
        int boundaryEnd = dashPos;
        while (boundaryEnd < Math.min(rawBytes.length, dashPos + 256)
                && rawBytes[boundaryEnd] != '\r'
                && rawBytes[boundaryEnd] != '\n') {
            boundaryEnd++;
        }
        if (boundaryEnd <= dashPos)
            return null;

        String boundary = new String(rawBytes, dashPos, boundaryEnd - dashPos,
                StandardCharsets.UTF_8).trim();

        if (boundary.isBlank())
            return null;

        // ── Step 3: scan first part headers (2048 byte window) ────────────────
        String headerSnippet = new String(rawBytes, 0,
                Math.min(rawBytes.length, 2048), StandardCharsets.UTF_8);

        // Extract Content-Type of first part
        String firstPartType = "application/xop+xml"; // safe default for XDS/MTOM
        java.util.regex.Matcher ctMatcher = java.util.regex.Pattern
                .compile("Content-Type:\\s*([^;\\r\\n]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(headerSnippet);

        if (ctMatcher.find()) {
            firstPartType = ctMatcher.group(1)
                    .replace("\"", "")
                    .trim();
        }

        // Extract Content-ID of first part to use as 'start' parameter
        String start = null;
        java.util.regex.Matcher cidMatcher = java.util.regex.Pattern
                .compile("Content-ID:\\s*(<[^>]+>)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(headerSnippet);

        if (cidMatcher.find()) {
            start = cidMatcher.group(1)
                    .replace("\"", "")
                    .trim();
        }

        // ── Step 4: determine start-info from SOAP namespace ──────────────────
        String startInfo = headerSnippet.contains("http://www.w3.org/2003/05/soap-envelope")
                ? "application/soap+xml"
                : "text/xml";

        // ── Step 5: assemble Content-Type ─────────────────────────────────────
        StringBuilder ct = new StringBuilder();
        ct.append("multipart/related");
        ct.append("; type=\"").append(firstPartType).append("\"");
        ct.append("; boundary=\"").append(boundary).append("\"");

        if (start != null && !start.isBlank()) {
            ct.append("; start=\"").append(start).append("\"");
        }

        ct.append("; start-info=\"").append(startInfo).append("\"");

        return ct.toString();
    }

    public static String getBaseUrl(HttpServletRequest request, String interactionId) {
        String useExternalUrl = System.getenv("USE_EXTERNAL_URL");
        boolean isExternal = "true".equalsIgnoreCase(useExternalUrl);
        if (isExternal) {
            String proto = Optional.ofNullable(request.getHeader("X-Forwarded-Proto"))
                    .orElse(request.getScheme());
            String host = Optional.ofNullable(request.getHeader("X-Forwarded-Host"))
                    .orElse(request.getServerName());
            String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
            int port;
            boolean skipPort;

            if ("sandbox".equals(activeProfile)) {
                port = request.getServerPort();
                skipPort = false;
            } else {
                port = Optional.ofNullable(request.getHeader("X-Forwarded-Port"))
                        .map(Integer::parseInt)
                        .orElse(request.getServerPort());
                skipPort = (proto.equals("http") && port == 80)
                        || (proto.equals("https") && port == 443)
                        || (proto.equals("https") && port == 80);
            }
            return skipPort ? proto + "://" + host : proto + "://" + host + ":" + port;
        } else {
            String serverPort = System.getenv("SERVER_PORT");
            if (serverPort == null || serverPort.isBlank()) {
                serverPort = "8080"; // Spring Boot default
            }
            return "http://localhost:" + serverPort;
        }
    }

    private String determineSoapVersion(String xml) {
        if (xml == null)
            return SOAPConstants.SOAP_1_1_PROTOCOL;
        if (xml.contains("http://www.w3.org/2003/05/soap-envelope"))
            return SOAPConstants.SOAP_1_2_PROTOCOL;
        if (xml.contains("http://schemas.xmlsoap.org/soap/envelope/"))
            return SOAPConstants.SOAP_1_1_PROTOCOL;
        return SOAPConstants.SOAP_1_1_PROTOCOL;
    }

    private String createSoapFault(String errorMessage, String soapProtocol,
            String interactionId, String errorTraceId) {
        if (soapProtocol.equals(SOAPConstants.SOAP_1_2_PROTOCOL)) {
            return """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope" xmlns:err="http://techbd.org/errorinfo">
                        <env:Body><env:Fault>
                            <env:Code><env:Value>env:Receiver</env:Value></env:Code>
                            <env:Reason><env:Text xml:lang="en">%s</env:Text></env:Reason>
                            <env:Detail>
                                <err:InteractionId>%s</err:InteractionId>
                                <err:ErrorTraceId>%s</err:ErrorTraceId>
                            </env:Detail>
                        </env:Fault></env:Body>
                    </env:Envelope>
                    """
                    .formatted(escapeXml(errorMessage), escapeXml(interactionId), escapeXml(errorTraceId));
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:err="http://techbd.org/errorinfo">
                    <soap:Body><soap:Fault>
                        <faultcode>soap:Server</faultcode>
                        <faultstring>%s</faultstring>
                        <detail>
                            <err:InteractionId>%s</err:InteractionId>
                            <err:ErrorTraceId>%s</err:ErrorTraceId>
                        </detail>
                    </soap:Fault></soap:Body>
                </soap:Envelope>
                """
                .formatted(escapeXml(errorMessage), escapeXml(interactionId), escapeXml(errorTraceId));
    }

    private String escapeXml(String input) {
        if (input == null)
            return "";
        return input.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}