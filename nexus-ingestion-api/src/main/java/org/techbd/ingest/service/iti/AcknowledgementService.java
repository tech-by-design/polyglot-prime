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

    public MCCIIN000002UV01 createAcknowledgement(
            II originalRequestId,
            MCCIMT000100UV01Device originalSenderDevice,
            String senderRoot,
            String senderTelecomURL,
            String interactionId) {
        logger.info("[{}] Creating HL7 acknowledgement response", interactionId);
        MCCIIN000002UV01 ack = new MCCIIN000002UV01();
        II id = new II();
        id.setRoot(UUID.randomUUID().toString());
        ack.setId(id);
        logger.debug("[{}] Assigned response ID: {}", interactionId, id.getRoot());
        TS creationTime = new TS();
        creationTime.setValue(DateTimeFormatter.ofPattern("yyyyMMddHHmmssZ")
                .format(ZonedDateTime.now(java.time.ZoneOffset.of("+05:30"))));
        ack.setCreationTime(creationTime);
        logger.debug("[{}] Set creationTime: {}", interactionId, creationTime.getValue());
        II interaction = new II();
        interaction.setRoot("2.16.840.1.113883.1.6");
        interaction.setExtension("MCCI_IN000002UV01");
        ack.setInteractionId(interaction);
        MCCIMT000200UV01Receiver receiver = new MCCIMT000200UV01Receiver();
        MCCIMT000100UV01Device receiverDevice = new MCCIMT000100UV01Device();
        receiverDevice.setDeterminerCode("INSTANCE");
        if (originalSenderDevice != null && originalSenderDevice.getId() != null
                && !originalSenderDevice.getId().isEmpty()) {
            receiverDevice.getId().addAll(originalSenderDevice.getId());
            logger.debug("[{}] ReceiverDevice has {} ID(s)", interactionId, originalSenderDevice.getId().size());
        } else {
            logger.warn(
                    "[{}] originalSenderDevice or its IDs were null or empty",
                    interactionId);
        }
        ack.getReceiver().add(receiver);
        MCCIMT000200UV01Sender sender = new MCCIMT000200UV01Sender();
        MCCIMT000100UV01Device senderDevice = new MCCIMT000100UV01Device();
        senderDevice.setDeterminerCode("INSTANCE");
        if (senderTelecomURL != null && !senderTelecomURL.isBlank()) {
            TEL tel = new TEL();
            tel.setValue(senderTelecomURL);
            senderDevice.getTelecom().add(tel);
            logger.debug("[{}] Added telecom URL to senderDevice", interactionId);
        } else {
            logger.debug("[{}] No telecom URL provided for senderDevice", interactionId);
        }
        ack.setSender(sender);
        MCCIMT000200UV01Acknowledgement ackBlock = new MCCIMT000200UV01Acknowledgement();
        MCCIMT000200UV01TargetMessage targetMessage = new MCCIMT000200UV01TargetMessage();

        if (originalRequestId != null) {
            targetMessage.setId(originalRequestId);
            logger.debug("[{}] Using provided originalRequestId", interactionId);
        } else {
            II unknownId = new II();
            unknownId.setRoot("UNKNOWN");
            targetMessage.setId(unknownId);
            logger.warn("[{}] originalRequestId was null.", interactionId);
        }

        ackBlock.setTargetMessage(targetMessage);
        ack.getAcknowledgement().add(ackBlock);

        logger.info("[{}] Acknowledgement construction completed", interactionId);
        return ack;
    }

    public  MCCIIN000002UV01 createAcknowledgmentError(String errorMessage) {
        MCCIIN000002UV01 ack = new MCCIIN000002UV01();
        // Set ID
        II id = new II();
        id.setRoot("2.25.999999999999999999999999999999999999"); // Example OID
        ack.setId(id);
        // Set creation time
        try {
            GregorianCalendar calendar = new GregorianCalendar();
            XMLGregorianCalendar xmlCal = DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
            TS creationTime = new TS();
            creationTime.setValue(xmlCal.toXMLFormat());
            ack.setCreationTime(creationTime);
        } catch (Exception e) {
            // log error if needed
        }
        // Interaction ID
        II interactionId = new II();
        interactionId.setRoot("2.16.840.1.113883.1.6");
        interactionId.setExtension("MCCI_IN000002UV01");
        ack.setInteractionId(interactionId);
        // Processing code
        CS processingCode = new CS();
        processingCode.setCode("P"); // 'P' = Production
        ack.setProcessingCode(processingCode);
        // Processing mode code
        CS processingModeCode = new CS();
        processingModeCode.setCode("T"); // 'T' = Current processing
        ack.setProcessingModeCode(processingModeCode);
        // Accept acknowledgment type
        CS acceptAckCode = new CS();
        acceptAckCode.setCode("AL"); // 'AL' = Always
        ack.setAcceptAckCode(acceptAckCode);
        // Acknowledgement with ERROR
        MCCIMT000200UV01Acknowledgement acknowledgement = new MCCIMT000200UV01Acknowledgement();
        CS typeCode = new CS();
        typeCode.setCode("AE"); // AE = Application Error
        acknowledgement.setTypeCode(typeCode);

        // Add acknowledgment detail
        MCCIMT000200UV01AcknowledgementDetail detail = new MCCIMT000200UV01AcknowledgementDetail();
        detail.setTypeCode(AcknowledgementDetailType.E); // Error
        ED text = new ED();
       // text.getContent().add("Invalid identifier or message format."); TODO : check how to add
        detail.setText(text);

        acknowledgement.getAcknowledgementDetail().add(detail);
        ack.getAcknowledgement().add(acknowledgement);

        return ack;
    }
}