package org.techbd.ingest.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SoapFaultUtilTest {

    private SoapFaultUtil util;

    @BeforeEach
    void setUp() {
        util = new SoapFaultUtil();
    }

    @Test
    void shouldCreateSoap12ServerFault() {
        String result = util.createSoapFault("error", "int123", "trace123", true);

        assertTrue(result.contains("env:Envelope"));
        assertTrue(result.contains("env:Receiver"));
        assertTrue(result.contains("error"));
        assertTrue(result.contains("int123"));
        assertTrue(result.contains("trace123"));
    }

    @Test
    void shouldCreateSoap11ServerFault() {
        String result = util.createSoapFault("error", "int123", "trace123", false);

        assertTrue(result.contains("SOAP-ENV:Envelope"));
        assertTrue(result.contains("SOAP-ENV:Server"));
        assertTrue(result.contains("error"));
        assertTrue(result.contains("int123"));
        assertTrue(result.contains("trace123"));
    }

    @Test
    void shouldCreateSoap12ClientFault() {
        String result = util.createClientSoap12Fault("client error", "int1", "trace1");

        assertTrue(result.contains("env:Sender"));
        assertTrue(result.contains("client error"));
        assertTrue(result.contains("int1"));
        assertTrue(result.contains("trace1"));
    }

    @Test
    void shouldCreateSoap11ClientFault() {
        String result = util.createClientSoap11Fault("client error", "int1", "trace1");

        assertTrue(result.contains("SOAP-ENV:Client"));
        assertTrue(result.contains("client error"));
        assertTrue(result.contains("int1"));
        assertTrue(result.contains("trace1"));
    }

    @Test
    void shouldEscapeXmlCharacters() {
        String input = "<tag> & \" '";
        String result = util.createSoapFault(input, input, input, true);

        assertTrue(result.contains("&lt;tag&gt;"));
        assertTrue(result.contains("&amp;"));
        assertTrue(result.contains("&quot;"));
        assertTrue(result.contains("&apos;"));
    }

    @Test
    void shouldHandleNullValues() {
        String result = util.createSoapFault(null, null, null, true);

        assertNotNull(result);

        assertTrue(result.contains("<env:Text xml:lang=\"en\"></env:Text>"));
    }

    @Test
    void shouldReturnTrue_whenSoap12ContentType() {
        assertTrue(util.isSoap12("application/soap+xml"));
    }

    @Test
    void shouldReturnFalse_whenSoap11ContentType() {
        assertFalse(util.isSoap12("text/xml"));
    }

    @Test
    void shouldReturnFalse_whenContentTypeIsNull() {
        assertFalse(util.isSoap12(null));
    }

}
