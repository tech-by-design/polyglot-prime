package org.techbd.ingest.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ws.WebServiceMessage;
import org.techbd.iti.schema.MCCIIN000002UV01;

import jakarta.xml.bind.JAXBElement;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;

import javax.xml.namespace.QName;

import org.junit.jupiter.api.Test;
import org.techbd.iti.schema.RegistryResponseType;

public class Hl7UtilTest {
    @Mock
    private AppLogger appLogger;

    @Mock
    private TemplateLogger templateLogger;

    private Hl7Util hl7Util;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        when(appLogger.getLogger(Hl7Util.class)).thenReturn(templateLogger);

        // Initialize static LOG
        hl7Util = new Hl7Util(appLogger);
    }

    @Test
    void shouldConvertMCCIMessageToXml() {
        MCCIIN000002UV01 message = new MCCIIN000002UV01();

        String result = Hl7Util.toXmlString(message, "123");

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.contains("<")); // basic XML check
    }

    @Test
    void shouldConvertRegistryResponseToXml() {
        RegistryResponseType responseType = new RegistryResponseType();

        JAXBElement<RegistryResponseType> element = new JAXBElement<>(
                new QName("urn:test", "RegistryResponse"),
                RegistryResponseType.class,
                responseType);

        String result = Hl7Util.toXmlString(element, "123");

        assertNotNull(result);
        assertTrue(result.contains("RegistryResponse"));
    }

    @Test
    void shouldConvertSoapMessageToString() throws Exception {
        WebServiceMessage soapMessage = mock(WebServiceMessage.class);

        doAnswer(invocation -> {
            ByteArrayOutputStream out = invocation.getArgument(0);
            out.write("<soap>test</soap>".getBytes());
            return null;
        }).when(soapMessage).writeTo(any());

        String result = Hl7Util.soapMessageToString(soapMessage, "123");

        assertEquals("<soap>test</soap>", result);
    }

    @Test
    void shouldThrowException_whenMcciMarshallingFails() {

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> Hl7Util.toXmlString((MCCIIN000002UV01) null, "123"));

        assertTrue(ex.getMessage().contains("interactionId=123"));

        verify(templateLogger).error(
                contains("Error converting MCCIIN000002UV01"),
                eq("123"),
                any(Exception.class));
    }

    @Test
    void shouldThrowException_whenRegistryMarshallingFails() {

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> Hl7Util.toXmlString((JAXBElement<RegistryResponseType>) null, "123"));

        assertTrue(ex.getMessage().contains("interactionId=123"));

        verify(templateLogger).error(
                contains("Error converting"),
                any(),
                eq("123"),
                any(Exception.class));
    }

    @Test
    void shouldThrowException_whenSoapMessageFails() throws Exception {

        WebServiceMessage soapMessage = mock(WebServiceMessage.class);

        doThrow(new RuntimeException("boom"))
                .when(soapMessage)
                .writeTo(any());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> Hl7Util.soapMessageToString(soapMessage, "123"));

        assertTrue(ex.getMessage().contains("interactionId=123"));

        verify(templateLogger).error(
                contains("Error converting SoapMessage"),
                eq("123"),
                any(Exception.class));
    }
}
