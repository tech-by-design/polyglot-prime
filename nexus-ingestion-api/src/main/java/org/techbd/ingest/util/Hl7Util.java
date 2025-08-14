package org.techbd.ingest.util;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.WebServiceMessage;
import org.techbd.iti.schema.MCCIIN000002UV01;
import org.techbd.iti.schema.RegistryResponseType;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
/**
 * Utility class for handling HL7 message operations.
 * <p>
 * This class provides helper methods for converting HL7 message objects into their
 * XML string representations for logging, debugging, or further processing.
 * </p>
 */
public class Hl7Util {

private static final Logger LOG = LoggerFactory.getLogger(Hl7Util.class);

  /**
     * Converts an {@link MCCIIN000002UV01} HL7 message into its XML string representation.
     *
     * @param message       the HL7 {@code MCCIIN000002UV01} message to convert
     * @param interactionId the interaction ID associated with the message, used for logging context
     * @return a formatted XML string representation of the HL7 message
     * @throws RuntimeException if the marshalling process fails
     */
    public static String toXmlString(MCCIIN000002UV01 message, String interactionId) {
        try {
            JAXBContext context = JAXBContext.newInstance(MCCIIN000002UV01.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            StringWriter sw = new StringWriter();
            marshaller.marshal(message, sw);
            return sw.toString();
        } catch (Exception e) {
            LOG.error("Error converting MCCIIN000002UV01 to String for interactionId={}", interactionId, e);
            throw new RuntimeException(
                "Error converting MCCIIN000002UV01 to String for interactionId=" + interactionId, e
            );
        }
    }
    /**
     * Converts a {@link RegistryResponseType} wrapped in a {@link JAXBElement} into
     * its XML string representation.
     *
     * @param response      the {@code JAXBElement<RegistryResponseType>} to be
     *                      converted
     * @param interactionId the interaction ID for logging context
     * @return the XML string representation of the response, formatted and UTF-8
     *         encoded
     * @throws RuntimeException if marshalling fails
     */
    public static String toXmlString(JAXBElement<RegistryResponseType> response, String interactionId) {
        StringWriter sw = new StringWriter();
    try {
        JAXBContext context = JAXBContext.newInstance(RegistryResponseType.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");        
        marshaller.marshal(response, sw);        
    } catch (Exception e) {
        LOG.error("Error converting {} to XML String for interactionId={}",
                  RegistryResponseType.class.getSimpleName(), interactionId, e);
        throw new RuntimeException(
            "Error converting " + RegistryResponseType.class.getSimpleName()
            + " to XML String for interactionId=" + interactionId, e
        );
    }
    return sw.toString();
}

public static String soapMessageToString(WebServiceMessage soapMessage, String interactionId) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        soapMessage.writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    } catch (Exception e) {
        LOG.error("Error converting SoapMessage to String for interactionId={}", interactionId, e);
        throw new RuntimeException(
            "Error converting SoapMessage to String for interactionId=" + interactionId, e
        );
    }
}
}