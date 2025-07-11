package org.techbd.ingest.listener;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.router.IngestionRouter;

@Component
public class MllpRoute extends RouteBuilder {
    private final IngestionRouter ingestionRouter;

    public MllpRoute(IngestionRouter ingestionRouter) {
        this.ingestionRouter = ingestionRouter;
    }

    @Override
    public void configure() throws Exception {
        from("mllp:0.0.0.0:2575")
                .routeId("hl7-mllp-listener")
                .log("Received HL7 Message:\n${body}")
                .process(exchange -> {
                    String hl7Message = exchange.getIn().getBody(String.class);
                    RequestContext context = buildRequestContext(exchange, hl7Message);
                    ingestionRouter.routeAndProcess(hl7Message, context);
                    String ack = buildAck(hl7Message);
                    exchange.getMessage().setBody(ack);

                }).log("ACK generated and sent.");
    }

    private RequestContext buildRequestContext(Exchange exchange, String hl7Message) {
        String remoteAddress = exchange.getIn().getHeader("CamelMllpRemoteAddress", String.class);
        ZonedDateTime now = ZonedDateTime.now();
        String timestamp = String.valueOf(now.toInstant().toEpochMilli());
        String interactionId = UUID.randomUUID().toString();

        return new RequestContext(
                Map.of("Source-System", "MLLP"),
                "/hl7",
                "mllp-tenant", // Or derive from HL7 MSH if needed
                interactionId,
                now,
                timestamp,
                "hl7-message.hl7",
                hl7Message.length(),
                "data/" + timestamp + "-" + interactionId + ".hl7",
                "metadata/" + timestamp + "-" + interactionId + "-metadata.json",
                "s3://bucket/data/" + timestamp + "-" + interactionId + ".hl7",
                "MLLP Listener",
                exchange.getFromEndpoint().getEndpointUri(),
                "",
                "MLLP",
                "127.0.0.1",
                remoteAddress != null ? remoteAddress : "unknown");
    }

    private String buildAck(String receivedMessage) {
        try {
            String[] segments = receivedMessage.split("\r");

            if (segments.length == 0 || !segments[0].startsWith("MSH")) {
                throw new IllegalArgumentException("Invalid HL7 message: Missing MSH segment.");
            }
            String[] fields = segments[0].split("\\|");
            String sendingApp = (fields.length > 2) ? fields[2] : "UnknownApp";
            String sendingFac = (fields.length > 3) ? fields[3] : "UnknownFac";
            String receivingApp = (fields.length > 4) ? fields[4] : "ReceiverApp";
            String receivingFac = (fields.length > 5) ? fields[5] : "ReceiverFac";
            String controlId = (fields.length > 9) ? fields[9] : String.valueOf(System.currentTimeMillis());
            String ack = "MSH|^~\\&|" + receivingApp + "|" + receivingFac + "|" + sendingApp + "|" + sendingFac +
                    "|" + getCurrentTimestamp() + "||ACK^R01|" + controlId + "|P|2.3\r" +
                    "MSA|AA|" + controlId + "\r";
            log.info("Generated ACK:\n{}", ack);
            return ack;
        } catch (Exception e) {
            log.error("Error generating ACK:\n{}", e.getMessage(),e);
            return "MSH|^~\\&|ReceiverApp|ReceiverFac|SenderApp|SenderFac|" + getCurrentTimestamp()
                    + "||ACK^R01|FallbackCtrlID|P|2.3\r" +
                    "MSA|AA|FallbackCtrlID\r";
        }
    }

    private String getCurrentTimestamp() {
        return java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(java.time.LocalDateTime.now());
    }
}