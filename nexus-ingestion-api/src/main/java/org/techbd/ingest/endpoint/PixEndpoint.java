package org.techbd.ingest.endpoint;

import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.techbd.ingest.service.iti.AcknowledgementService;
import org.techbd.iti.schema.MCCIIN000002UV01;
import org.techbd.iti.schema.PRPAIN201301UV02;
import org.techbd.iti.schema.PRPAIN201302UV02;
import org.techbd.iti.schema.PRPAIN201304UV02;
@Endpoint
public class PixEndpoint {

    private static final String NAMESPACE_URI = "urn:hl7-org:v3";
    private final AcknowledgementService ackService;

    public PixEndpoint(AcknowledgementService ackService) {
        this.ackService = ackService;
    }

    // ITI-44: Patient Registry Record Added
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "PRPA_IN201301UV02")
    @ResponsePayload
    public MCCIIN000002UV01 handlePixAdd(@RequestPayload PRPAIN201301UV02 request) {
        return ackService.createAcknowledgement(
                request.getId(), request.getSender().getDevice(),
                "2.25.256133121442266547198931747355024016667.1.1.1",
                "http://helprodmcccd.myhie.com:9002/pixpdq/PIXManager_Service"
        );
    }

    // ITI-44/46: Patient Registry Record Revised
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "PRPA_IN201302UV02")
    @ResponsePayload
    public MCCIIN000002UV01 handlePixUpdate(@RequestPayload PRPAIN201302UV02 request) {
        return ackService.createAcknowledgement(
                request.getId(), request.getSender().getDevice(),
                "2.25.256133121442266547198931747355024016667.1.1.1",
                "http://helprodmcccd.myhie.com:9002/pixpdq/PIXManager_Service"
        );
    }

    // ITI-44: Patient Registry Duplicates Resolved
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "PRPA_IN201304UV02")
    @ResponsePayload
    public MCCIIN000002UV01 handlePixDuplicateResolved(@RequestPayload PRPAIN201304UV02 request) {
        return ackService.createAcknowledgement(
                request.getId(), request.getSender().getDevice(),
                "2.25.256133121442266547198931747355024016667.1.1.1",
                "http://helprodmcccd.myhie.com:9002/pixpdq/PIXManager_Service"
        );
    }
}
