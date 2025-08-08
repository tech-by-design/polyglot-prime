package org.techbd.ingest.service.iti;



import org.techbd.iti.schema.*;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class AcknowledgementService {

    public MCCIIN000002UV01 createAcknowledgement(
            II originalRequestId,
            MCCIMT000100UV01Device originalSenderDevice,
            String senderRoot,
            String senderTelecomURL
    ) {
        MCCIIN000002UV01 ack = new MCCIIN000002UV01();

        // Set id (UUID)
        II id = new II();
        id.setRoot(UUID.randomUUID().toString());
        ack.setId(id);

        // Timestamp
        TS creationTime = new TS();
        creationTime.setValue(DateTimeFormatter.ofPattern("yyyyMMddHHmmssZ")
                .format(ZonedDateTime.now(java.time.ZoneOffset.of("+05:30"))));
        ack.setCreationTime(creationTime);

        // InteractionId (fixed)
        II interactionId = new II();
        interactionId.setRoot("2.16.840.1.113883.1.6");
        interactionId.setExtension("MCCI_IN000002UV01");
        ack.setInteractionId(interactionId);

        // ack.setProcessingCode(new CS("P"));
        // ack.setProcessingModeCode(new CS("R"));
        // ack.setAcceptAckCode(new CS("NE"));

        // Receiver (mirror original sender)
        MCCIMT000200UV01Receiver receiver = new MCCIMT000200UV01Receiver();
        MCCIMT000100UV01Device receiverDevice = new MCCIMT000100UV01Device();
        receiverDevice.getId().addAll(originalSenderDevice.getId());
        receiverDevice.setDeterminerCode("INSTANCE");
        // receiver.setDevice(receiverDevice);
        // receiver.setTypeCode("RCV");
        ack.getReceiver().add(receiver);

        // Sender (us)
        MCCIMT000200UV01Sender sender = new MCCIMT000200UV01Sender();
        MCCIMT000100UV01Device senderDevice = new MCCIMT000100UV01Device();
        senderDevice.setDeterminerCode("INSTANCE");
        // senderDevice.getId().add(new II(senderRoot));

        if (senderTelecomURL != null && !senderTelecomURL.isEmpty()) {
            TEL tel = new TEL();
            tel.setValue(senderTelecomURL);
            senderDevice.getTelecom().add(tel);
        }

        // sender.setDevice(senderDevice);
        // sender.setTypeCode("SND");
        ack.setSender(sender);

        // Acknowledgement block
        MCCIMT000200UV01Acknowledgement ackBlock = new MCCIMT000200UV01Acknowledgement();
        // ackBlock.setTypeCode(new CS("CA"));
        MCCIMT000200UV01TargetMessage targetMessage = new MCCIMT000200UV01TargetMessage();
        targetMessage.setId(originalRequestId);
        ackBlock.setTargetMessage(targetMessage);
        ack.getAcknowledgement().add(ackBlock);

        return ack;
    }
}
