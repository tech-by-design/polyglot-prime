package org.techbd.nexusingestionapi.listener;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class MllpRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        
        from("mllp:0.0.0.0:2575")
            .routeId("hl7-mllp-listener")
            .log("Received HL7 Message:\n${body}")
            .process(exchange -> {
                String hl7Message = exchange.getIn().getBody(String.class);
                
                // You can reuse your existing buildAck logic here
                String ack = buildAck(hl7Message);

                exchange.getMessage().setBody(ack);  // ACK will be framed and sent automatically
            }).log("ACK generated and sent.");
;

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

        String ack =
                "MSH|^~\\&|" + receivingApp + "|" + receivingFac + "|" + sendingApp + "|" + sendingFac +
                "|" + getCurrentTimestamp() + "||ACK^R01|" + controlId + "|P|2.3\r" +
                "MSA|AA|" + controlId + "\r";

        log.info("Generated ACK:\n{}", ack);
        return ack;

    } catch (Exception e) {
        e.printStackTrace();
        return "MSH|^~\\&|ReceiverApp|ReceiverFac|SenderApp|SenderFac|" + getCurrentTimestamp() + "||ACK^R01|FallbackCtrlID|P|2.3\r" +
               "MSA|AA|FallbackCtrlID\r";
    }
}


    private String getCurrentTimestamp() {
        return java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(java.time.LocalDateTime.now());
    }
}