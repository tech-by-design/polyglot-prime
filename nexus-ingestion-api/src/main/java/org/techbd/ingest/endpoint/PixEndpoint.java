package org.techbd.ingest.endpoint;

import org.techbd.iti.schema.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
public class PixEndpoint {

    private static final String NAMESPACE_URI = "urn:hl7-org:v3";

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "PRPA_IN201301UV02")
    @ResponsePayload
    public MCCIIN000002UV01 handlePixAddRequest(@RequestPayload PRPAIN201301UV02 request) {

        MCCIIN000002UV01 ack = new MCCIIN000002UV01();

        // Set id (unique UUID)
        II id = new II();
        id.setRoot(UUID.randomUUID().toString());
        ack.setId(id);

        // creationTime - current timestamp in HL7 format (e.g., 20250805165700)
        TS creationTime = new TS();
        creationTime.setValue(DateTimeFormatter.ofPattern("yyyyMMddHHmmssZ")
                .format(ZonedDateTime.now(ZoneId.of("UTC+05:30"))));
        ack.setCreationTime(creationTime);

        // interactionId - fixed
        II interactionId = new II();
        interactionId.setRoot("2.16.840.1.113883.1.6");
        interactionId.setExtension("MCCI_IN000002UV01");
        ack.setInteractionId(interactionId);

        CS processingCode = new CS();
        processingCode.setCode("P");
        ack.setProcessingCode(processingCode);
        CS processingModeCode = new CS();
        processingModeCode.setCode("R");
        ack.setProcessingModeCode(processingModeCode);
        CS acceptAckCode = new CS();
        acceptAckCode.setCode("NE");
        ack.setAcceptAckCode(acceptAckCode);
        MCCIMT000200UV01Receiver receiver = new MCCIMT000200UV01Receiver();
        MCCIMT000100UV01Device senderDevice = request.getSender().getDevice();
        MCCIMT000100UV01Device receiverDevice = new MCCIMT000100UV01Device();
        receiverDevice.getId().addAll(senderDevice.getId());
        receiverDevice.setDeterminerCode("INSTANCE");
        // receiverDevice.setClassCode("DEV");
        // receiver.setTypeCode("RCV");
        // receiver.setDevice(receiverDevice);
        ack.getReceiver().add(receiver);
        MCCIMT000200UV01Sender sender = new MCCIMT000200UV01Sender();
        MCCIMT000100UV01Device device = new MCCIMT000100UV01Device();
        II senderId = new II();
        senderId.setRoot("2.25.256133121442266547198931747355024016667.1.1.1");
        TEL telecom = new TEL();
        telecom.setValue("http://helprodmcccd.myhie.com:9002/pixpdq/PIXManager_Service");
        device.getId().add(senderId);
        device.setDeterminerCode("INSTANCE");
        // device.setClassCode("DEV");
        device.getTelecom().add(telecom);
        // sender.setDevice(device);
        // sender.setTypeCode("SND");
        ack.setSender(sender);
        MCCIMT000200UV01Acknowledgement ackBlock = new MCCIMT000200UV01Acknowledgement();
        CS typeCode = new CS();
        typeCode.setCode("CA");
        ackBlock.setTypeCode(typeCode);
        MCCIMT000200UV01TargetMessage targetMessage = new MCCIMT000200UV01TargetMessage();
        II targetId = request.getId();
        targetMessage.setId(targetId);
        ackBlock.setTargetMessage(targetMessage);
        ack.getAcknowledgement().add(ackBlock);

        return ack;
    }

}