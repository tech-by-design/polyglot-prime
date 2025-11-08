package org.techbd.ingest.listener;

import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.netty.handler.codec.haproxy.HAProxyMessage;

/**
 * TCP listener route that supports Proxy Protocol v2 (from AWS NLB).
 *
 * Always logs message content in a human-readable form (UTF-8 decoded),
 * and optionally logs HAProxy header details when enabled.
 */
@Component
public class TcpRoute extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(TcpRoute.class);

    @Value("${TCP_DISPATCHER_PORT:7980}")
    public int tcpPort;

    @Value("${PRINT_PROXY_PROTOCOL_MESSAGE:false}")
    public boolean printProxyProtocolMessage;

    @Override
    public void configure() {
        String endpointUri = "netty:tcp://0.0.0.0:" + tcpPort
                + "?sync=true"
                + "&textline=true"
                + "&encoding=UTF-8"
                + "&decoders=#proxyDecoders";

        from(endpointUri)
                .routeId("tcp-listener-" + tcpPort)
                .log("[TCP] Message received on port " + tcpPort)
                .process(this::process);
    }

    private void process(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        String message;

        // 🔹 Convert any message type to readable UTF-8 text safely
        if (body instanceof byte[] bytes) {
            message = new String(bytes, StandardCharsets.UTF_8)
                    .replaceAll("\\p{C}", ""); // remove control characters
        } else {
            message = String.valueOf(body);
        }

        String remote = exchange.getIn().getHeader("CamelNettyRemoteAddress", String.class);
        String local = exchange.getIn().getHeader("CamelNettyLocalAddress", String.class);

        // 🔹 Start log block
        log.info("PROXYPROTOCOL | ===== New TCP Exchange Received on Port {} =====", tcpPort);

        // 🔹 Log connection info
        log.info("PROXYPROTOCOL | Connection Info | Remote: {} | Local: {}", remote, local);

        // 🔹 Log all headers
        StringBuilder headerLog = new StringBuilder("PROXYPROTOCOL | Exchange Headers:\n");
        exchange.getIn().getHeaders().forEach((key, value) -> headerLog
                .append("PROXYPROTOCOL |   ").append(key)
                .append(": ").append(value).append("\n"));
        log.info(headerLog.toString().trim());

        // 🔹 Log readable message content
        log.info("PROXYPROTOCOL | Message Content (UTF-8 Decoded):\n{}", message);

        // 🔹 Proxy Protocol section
        if (printProxyProtocolMessage) {
            log.info("PROXYPROTOCOL | PRINT_PROXY_PROTOCOL_MESSAGE=true → checking HAProxy header...");
            Object proxyMsg = exchange.getIn().getHeader("CamelNettyHAProxyMessage");

            if (proxyMsg instanceof HAProxyMessage haProxy) {
                log.info(
                        "PROXYPROTOCOL | HAProxy Header | Client: {}:{} → Server: {}:{} | Version: {} | Command: {} | Protocol: {}",
                        haProxy.sourceAddress(),
                        haProxy.sourcePort(),
                        haProxy.destinationAddress(),
                        haProxy.destinationPort(),
                        haProxy.protocolVersion(),
                        haProxy.command(),
                        haProxy.proxiedProtocol());
            } else {
                log.info("PROXYPROTOCOL | No HAProxyMessage found — processed as plain TCP message.");
            }
        } else {
            log.info("PROXYPROTOCOL | PRINT_PROXY_PROTOCOL_MESSAGE=false → skipping HAProxy header parse.");
        }

        // 🔹 Response
        String response = "ACK received on port " + tcpPort;
        exchange.getMessage().setBody(response); // ✅ now safe because of textline=true
        log.info("PROXYPROTOCOL | Response Sent: {}", response);

        // 🔹 End log block
        log.info("PROXYPROTOCOL | ===== End of TCP Exchange =====");
    }
}
