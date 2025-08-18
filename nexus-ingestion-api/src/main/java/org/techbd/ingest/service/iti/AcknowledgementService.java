package org.techbd.ingest.service.iti;

import org.techbd.iti.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;
import java.util.UUID;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

@Service
public class AcknowledgementService {

    private static final Logger logger = LoggerFactory.getLogger(AcknowledgementService.class);

    public MCCIIN000002UV01 createPixAcknowledgement(
            II originalRequestId,
            MCCIMT000100UV01Device originalSenderDevice,
            String senderRoot,
            String senderTelecomURL,
            String techBDInteractionId) {

        logger.info("AcknowledgementService:: Creating HL7 acknowledgement response for interactionId: {}",
                techBDInteractionId);

        MCCIIN000002UV01 ack = new MCCIIN000002UV01();

        // Main ID
        II id = new II();
        id.setRoot(UUID.randomUUID().toString());
        ack.setId(id);
        logger.debug("AcknowledgementService:: Assigned response ID: {} for interaction id :{}", id.getRoot(),
                techBDInteractionId);

        // Creation time
        TS creationTime = new TS();
        creationTime.setValue(DateTimeFormatter.ofPattern("yyyyMMddHHmmssZ")
                .format(ZonedDateTime.now(java.time.ZoneOffset.of("+05:30"))));
        ack.setCreationTime(creationTime);

        // Interaction ID
        II interaction = new II();
        interaction.setRoot("2.16.840.1.113883.1.6");
        interaction.setExtension("MCCI_IN000002UV01");
        ack.setInteractionId(interaction);
        MCCIMT000200UV01Receiver receiver = new MCCIMT000200UV01Receiver();
        receiver.setTypeCode(CommunicationFunctionType.RCV); 
        MCCIMT000200UV01Device receiverDevice = new MCCIMT000200UV01Device();
        receiverDevice.setDeterminerCode("INSTANCE");
        if (originalSenderDevice != null && originalSenderDevice.getId() != null) {
            for (II id1 : originalSenderDevice.getId()) {
                II copy = new II();
                copy.setRoot(id1.getRoot());
                copy.setExtension(id1.getExtension());
                copy.setAssigningAuthorityName(id1.getAssigningAuthorityName());
                receiverDevice.getId().add(copy);
            }
        }
        receiver.setDevice(receiverDevice);
        ack.getReceiver().add(receiver);
        // Sender
        MCCIMT000200UV01Sender sender = new MCCIMT000200UV01Sender();
        sender.setTypeCode(CommunicationFunctionType.SND); // required for sender

        MCCIMT000200UV01Device senderDevice = new MCCIMT000200UV01Device();
        senderDevice.setDeterminerCode("INSTANCE");

        // Set telecom if provided
        if (senderTelecomURL != null && !senderTelecomURL.isBlank()) {
            TEL tel = new TEL();
            tel.setValue(senderTelecomURL);
            senderDevice.getTelecom().add(tel);
        }

        // Optional: copy IDs from some original sender device if available
        if (originalSenderDevice != null && originalSenderDevice.getId() != null) {
            for (II id2 : originalSenderDevice.getId()) {
                II copy = new II();
                copy.setRoot(id2.getRoot());
                copy.setExtension(id2.getExtension());
                copy.setAssigningAuthorityName(id2.getAssigningAuthorityName());
                senderDevice.getId().add(copy);
            }
        }

        // Attach device to sender
        sender.setDevice(senderDevice);

// Add sender to ack
ack.setSender(sender);


        // Acknowledgement
        MCCIMT000200UV01Acknowledgement ackBlock = new MCCIMT000200UV01Acknowledgement();
        MCCIMT000200UV01TargetMessage targetMessage = new MCCIMT000200UV01TargetMessage();
        if (originalRequestId != null) {
            targetMessage.setId(originalRequestId);
        } else {
            II unknownId = new II();
            unknownId.setRoot("UNKNOWN");
            targetMessage.setId(unknownId);
        }
        ackBlock.setTargetMessage(targetMessage);
        ack.getAcknowledgement().add(ackBlock);

        logger.info("AcknowledgementService:: HL7 acknowledgement created successfully for interactionId: {}",
                techBDInteractionId);
        return ack;
    }

    public MCCIIN000002UV01 createPixAcknowledgmentError(String errorMessage, String techBDInteractionId) {
        logger.warn(
                "AcknowledgementService:: Creating HL7 error acknowledgment for interactionId: {}, errorMessage: {}",
                techBDInteractionId, errorMessage);

        MCCIIN000002UV01 ack = new MCCIIN000002UV01();

        // Set ID
        II id = new II();
        id.setRoot("2.25.999999999999999999999999999999999999");
        ack.setId(id);

        // Set creation time
        try {
            GregorianCalendar calendar = new GregorianCalendar();
            XMLGregorianCalendar xmlCal = DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
            TS creationTime = new TS();
            creationTime.setValue(xmlCal.toXMLFormat());
            ack.setCreationTime(creationTime);
        } catch (Exception e) {
            logger.error(
                    "AcknowledgementService:: Error setting creationTime in error acknowledgment for interactionId: {}",
                    techBDInteractionId, e);
        }

        // Interaction ID
        II interactionId = new II();
        interactionId.setRoot("2.16.840.1.113883.1.6");
        interactionId.setExtension("MCCI_IN000002UV01");
        ack.setInteractionId(interactionId);

        // Processing codes
        CS processingCode = new CS();
        processingCode.setCode("P");
        ack.setProcessingCode(processingCode);

        CS processingModeCode = new CS();
        processingModeCode.setCode("T");
        ack.setProcessingModeCode(processingModeCode);

        CS acceptAckCode = new CS();
        acceptAckCode.setCode("AL");
        ack.setAcceptAckCode(acceptAckCode);

        // Acknowledgement with ERROR
        MCCIMT000200UV01Acknowledgement acknowledgement = new MCCIMT000200UV01Acknowledgement();
        CS typeCode = new CS();
        typeCode.setCode("AE");
        acknowledgement.setTypeCode(typeCode);

        // Add error detail
        MCCIMT000200UV01AcknowledgementDetail detail = new MCCIMT000200UV01AcknowledgementDetail();
        detail.setTypeCode(AcknowledgementDetailType.E);
        ED text = new ED();
        TEL tel = new TEL();
        tel.setValue(errorMessage);
        text.setReference(tel);
        detail.setText(text);
        acknowledgement.getAcknowledgementDetail().add(detail);
        ack.getAcknowledgement().add(acknowledgement);

        logger.warn("AcknowledgementService:: HL7 error acknowledgment created successfully for interactionId: {}",
                techBDInteractionId);
        return ack;
    }

    public RegistryResponseType createPnrAcknowledgement(String status, String techBDInteractionId) {
        logger.info("AcknowledgementService:: Creating PnR acknowledgement with status: {} for interactionId: {}",
                status, techBDInteractionId);

        ObjectFactory factory = new ObjectFactory();
        RegistryResponseType response = factory.createRegistryResponseType();

        if ("Success".equalsIgnoreCase(status)) {
            response.setStatus("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success");
        } else {
            response.setStatus("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure");
        }

        logger.debug(
                "AcknowledgementService:: techbdGeneratedInteractionId: urn:uuid:techbd-generated-interactionid:{} for interactionId: {}",
                techBDInteractionId, UUID.randomUUID());
        return response;
    }
}
