package org.techbd.ingest.util;

import org.springframework.stereotype.Component;

/**
 * Utility class for generating SOAP fault responses with error trace information.
 * Supports both SOAP 1.1 and SOAP 1.2 protocols.
 */
@Component
public class SoapFaultUtil {

    /**
     * Creates a SOAP fault XML response with error trace information.
     * 
     * @param errorMessage The error message to include in the fault
     * @param interactionId The interaction ID for tracking
     * @param errorTraceId The error trace ID for debugging
     * @param isSoap12 Whether to use SOAP 1.2 (true) or SOAP 1.1 (false) format
     * @return SOAP fault XML string
     */
    public String createSoapFault(String errorMessage, String interactionId, String errorTraceId, boolean isSoap12) {
        String escapedMessage = escapeXml(errorMessage);
        String escapedInteractionId = escapeXml(interactionId);
        String escapedErrorTraceId = escapeXml(errorTraceId);
        
        if (isSoap12) {
            return createSoap12Fault(escapedMessage, escapedInteractionId, escapedErrorTraceId);
        } else {
            return createSoap11Fault(escapedMessage, escapedInteractionId, escapedErrorTraceId);
        }
    }

    /**
     * Creates a SOAP 1.2 fault with client error code (env:Sender).
     * Used for client-side errors like empty request body.
     */
    public String createClientSoap12Fault(String errorMessage, String interactionId, String errorTraceId) {
        String escapedMessage = escapeXml(errorMessage);
        String escapedInteractionId = escapeXml(interactionId);
        String escapedErrorTraceId = escapeXml(errorTraceId);
        
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope" xmlns:err="http://techbd.org/errorinfo">
                <env:Body>
                    <env:Fault>
                        <env:Code>
                            <env:Value>env:Sender</env:Value>
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
            """, escapedMessage, escapedInteractionId, escapedErrorTraceId);
    }

    /**
     * Creates a SOAP 1.1 fault with client error code (SOAP-ENV:Client).
     * Used for client-side errors like empty request body.
     */
    public String createClientSoap11Fault(String errorMessage, String interactionId, String errorTraceId) {
        String escapedMessage = escapeXml(errorMessage);
        String escapedInteractionId = escapeXml(interactionId);
        String escapedErrorTraceId = escapeXml(errorTraceId);
        
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:err="http://techbd.org/errorinfo">
                <SOAP-ENV:Body>
                    <SOAP-ENV:Fault>
                        <faultcode>SOAP-ENV:Client</faultcode>
                        <faultstring xml:lang="en">%s</faultstring>
                        <detail>
                            <err:InteractionId>%s</err:InteractionId>
                            <err:ErrorTraceId>%s</err:ErrorTraceId>
                        </detail>
                    </SOAP-ENV:Fault>
                </SOAP-ENV:Body>
            </SOAP-ENV:Envelope>
            """, escapedMessage, escapedInteractionId, escapedErrorTraceId);
    }

    /**
     * Creates a SOAP 1.2 fault with server error code (env:Receiver).
     * Used for server-side errors like processing failures.
     */
    private String createSoap12Fault(String escapedMessage, String escapedInteractionId, String escapedErrorTraceId) {
        return String.format("""
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
            """, escapedMessage, escapedInteractionId, escapedErrorTraceId);
    }

    /**
     * Creates a SOAP 1.1 fault with server error code (SOAP-ENV:Server).
     * Used for server-side errors like processing failures.
     */
    private String createSoap11Fault(String escapedMessage, String escapedInteractionId, String escapedErrorTraceId) {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:err="http://techbd.org/errorinfo">
                <SOAP-ENV:Body>
                    <SOAP-ENV:Fault>
                        <faultcode>SOAP-ENV:Server</faultcode>
                        <faultstring xml:lang="en">%s</faultstring>
                        <detail>
                            <err:InteractionId>%s</err:InteractionId>
                            <err:ErrorTraceId>%s</err:ErrorTraceId>
                        </detail>
                    </SOAP-ENV:Fault>
                </SOAP-ENV:Body>
            </SOAP-ENV:Envelope>
            """, escapedMessage, escapedInteractionId, escapedErrorTraceId);
    }

    /**
     * Determines SOAP version from Content-Type header.
     * 
     * @param contentType The Content-Type header value
     * @return true if SOAP 1.2, false if SOAP 1.1
     */
    public boolean isSoap12(String contentType) {
        return contentType != null && contentType.contains("application/soap+xml");
    }

    /**
     * Escapes XML special characters to prevent injection.
     * 
     * @param input The string to escape
     * @return Escaped XML-safe string
     */
    private String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}