package org.techbd.ingest;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class SoapMessageValidationTest {

    @Test
    public void testSoapMessageValidity() throws Exception {
        String soapMessage = """
            <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                <SOAP-ENV:Header>
                    <wsa:Action xmlns:wsa="http://www.w3.org/2005/08/addressing">urn:hl7-org:v3:MCCI_IN000002UV01</wsa:Action>
                    <wsa:MessageID xmlns:wsa="http://www.w3.org/2005/08/addressing">urn:uuid:92f70838-14eb-492b-8c02-97e6353a9e08</wsa:MessageID>
                    <wsa:RelatesTo xmlns:wsa="http://www.w3.org/2005/08/addressing">urn:uuid:12345678-90ab-cdef-1234-567890abcdef</wsa:RelatesTo>
                    <wsa:To xmlns:wsa="http://www.w3.org/2005/08/addressing">http://www.w3.org/2005/08/addressing/anonymous</wsa:To>
                    <techbd:InteractionID xmlns:techbd="urn:techbd:custom">0a119f1b-c5ca-41c2-b029-e5c595ffcfde</techbd:InteractionID>
                </SOAP-ENV:Header>
                <SOAP-ENV:Body>
                    <ns3:RegistryResponse xmlns:ns2="urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0" xmlns:ns3="urn:oasis:names:tc:ebxml-regrep:xsd:rs:3.0" xmlns:ns4="urn:oasis:names:tc:ebxml-regrep:xsd:query:3.0" xmlns:ns5="urn:hl7-org:v3" xmlns:ns6="urn:oasis:names:tc:ebxml-regrep:xsd:lcm:3.0" xmlns:ns7="urn:ihe:iti:xds-b:2007" status="urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success"/>
                </SOAP-ENV:Body>
            </SOAP-ENV:Envelope>
        """;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(soapMessage.getBytes(StandardCharsets.UTF_8)));
        assertEquals("Envelope", doc.getDocumentElement().getLocalName());
        assertEquals("http://schemas.xmlsoap.org/soap/envelope/", doc.getDocumentElement().getNamespaceURI());
        assertTrue(hasElement(doc, "Action", "http://www.w3.org/2005/08/addressing"));
        assertTrue(hasElement(doc, "MessageID", "http://www.w3.org/2005/08/addressing"));
        assertTrue(hasElement(doc, "RelatesTo", "http://www.w3.org/2005/08/addressing"));
        assertTrue(hasElement(doc, "To", "http://www.w3.org/2005/08/addressing"));
        assertTrue(hasElement(doc, "InteractionID", "urn:techbd:custom"));
    }

    private boolean hasElement(Document doc, String localName, String namespaceUri) {
        NodeList nodes = doc.getElementsByTagNameNS(namespaceUri, localName);
        return nodes != null && nodes.getLength() > 0;
    }
}
