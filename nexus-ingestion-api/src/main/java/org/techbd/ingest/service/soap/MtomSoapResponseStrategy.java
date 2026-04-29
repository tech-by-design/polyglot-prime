package org.techbd.ingest.service.soap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapMessage;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.SoapResponseUtil;
import org.techbd.ingest.util.TemplateLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * MTOM strategy — wraps the SOAP/XOP response in a multipart/related envelope.
 *
 * Wire format (SOAP 1.2):
 * <pre>
 *   Content-Type: multipart/related;
 *     boundary="uuid:...";
 *     type="application/xop+xml";
 *     start="&lt;rootpart@...&gt;";
 *     start-info="application/soap+xml";
 *     action="urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b"
 * </pre>
 *
 * Wire format (SOAP 1.1, per W3C SOAP11-MTOM binding):
 * <pre>
 *   Content-Type: multipart/related;
 *     boundary="uuid:...";
 *     type="application/xop+xml";
 *     start="&lt;rootpart@...&gt;";
 *     start-info="text/xml";
 *     action="urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b"
 * </pre>
 */
public class MtomSoapResponseStrategy implements SoapResponseStrategy {

    private static final String IHE_PNR_ACTION =
            "urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b";

    private static final String SOAP_12_MEDIA_TYPE = "application/soap+xml";
    private static final String SOAP_11_MEDIA_TYPE = "text/xml";

    private final SoapResponseUtil soapResponseUtil;
    private final TemplateLogger log;

    public MtomSoapResponseStrategy(SoapResponseUtil soapResponseUtil, AppLogger appLogger) {
        this.soapResponseUtil = soapResponseUtil;
        this.log = appLogger.getLogger(MtomSoapResponseStrategy.class);
    }

    @Override
    public byte[] writeResponse(
            String interactionId,
            MessageContext messageContext,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            SoapMessage builtResponse) {

        try {
            boolean isSoap11 = isSoap11(httpRequest);
            String soapMediaType = isSoap11 ? SOAP_11_MEDIA_TYPE : SOAP_12_MEDIA_TYPE;

            String boundary = soapResponseUtil.generateMimeBoundary();
            String startCid = "<rootpart@" + soapResponseUtil.generateUuid() + ">";
            String soapXml = soapResponseUtil.serializeSoapMessage(builtResponse, interactionId);

            String multipart = buildMultipartBody(boundary, startCid, soapXml, soapMediaType);

            String contentType = "multipart/related;"
                    + " boundary=\"" + boundary + "\";"
                    + " type=\"application/xop+xml\";"
                    + " start=\"" + startCid + "\";"
                    + " start-info=\"" + soapMediaType + "\";"
                    + " action=\"" + IHE_PNR_ACTION + "\"";

            // Use setHeader (not setContentType) to bypass any MediaType
            // validation in the Tomcat connector layer.
            httpResponse.setHeader("Content-Type", contentType);
            httpRequest.setAttribute(Constants.ACK_CONTENT_TYPE, contentType);

            byte[] responseBytes = multipart.getBytes(StandardCharsets.UTF_8);
            httpResponse.setContentLengthLong(responseBytes.length);
            httpResponse.flushBuffer();
            httpResponse.getOutputStream().write(responseBytes);
            httpResponse.getOutputStream().flush();

            log.info("MtomSoapResponseStrategy:: MTOM response written. " +
                    "boundary={} interactionId={} soapMediaType={}",
                    boundary, interactionId, soapMediaType);
            return responseBytes;

        } catch (IOException e) {
            if (isBrokenPipe(e)) {
                log.warn("MtomSoapResponseStrategy:: Client disconnected before MTOM response " +
                        "could be written (caller timed out). interactionId={}", interactionId);
                throw new RuntimeException("Client disconnected during MTOM write", e);
            }
            log.error("MtomSoapResponseStrategy:: Failed to write MTOM response. " +
                    "interactionId={} error={}", interactionId, e.getMessage(), e);
            throw new RuntimeException("Failed to write MTOM response", e);
        }
    }

    private String buildMultipartBody(String boundary, String startCid, String soapXml,
                                      String soapMediaType) {
        return "--" + boundary + "\r\n"
                + "Content-Type: application/xop+xml; charset=UTF-8; type=\"" + soapMediaType + "\"\r\n"
                + "Content-Transfer-Encoding: 8bit\r\n"
                + "Content-ID: " + startCid + "\r\n"
                + "\r\n"
                + soapXml + "\r\n"
                + "--" + boundary + "--\r\n";
    }

    /**
     * Returns {@code true} when the incoming request is SOAP 1.1.
     *
     * <p>Detection strategy (first match wins):
     * <ol>
     *   <li>Request {@code Content-Type} contains {@code text/xml} — canonical SOAP 1.1 indicator.</li>
     *   <li>Request {@code SOAPAction} header is present — SOAP 1.1 exclusively uses this header.</li>
     *   <li>Request {@code Content-Type} contains {@code application/soap+xml} — SOAP 1.2, so returns false.</li>
     * </ol>
     */
    private boolean isSoap11(HttpServletRequest httpRequest) {
        String contentType = httpRequest.getContentType();
        if (contentType != null) {
            if (contentType.contains("text/xml")) {
                return true;
            }
            if (contentType.contains("application/soap+xml")) {
                return false;
            }
        }
        // SOAPAction header is SOAP 1.1-only
        return httpRequest.getHeader("SOAPAction") != null;
    }

    private boolean isBrokenPipe(Throwable e) {
        while (e != null) {
            String msg = e.getMessage();
            String cls = e.getClass().getName();
            if ((msg != null && (msg.contains("Broken pipe")
                    || msg.contains("Connection reset")))
                    || cls.contains("ClientAbortException")) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }
}