package org.techbd.ingest.listener;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.function.Supplier;

/**
 * TCP listener route that supports Proxy Protocol v2 (from AWS NLB).
 * 
 * When PRINT_PROXY_PROTOCOL_MESSAGE=true, decodes the HAProxy header to extract
 * the actual client IP and port (instead of the NLB IP).
 */
@Component
public class TcpRoute extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(TcpRoute.class);

    @Value("${TCP_DISPATCHER_PORT:7980}")
    private int tcpPort;

    @Value("${PRINT_PROXY_PROTOCOL_MESSAGE:false}")
    private boolean printProxyProtocolMessage;

    @Override
    public void configure() {
        String endpointUri = "netty:tcp://0.0.0.0:" + tcpPort
                + "?sync=true"
                + "&allowDefaultCodec=false"
                + "&decoders=#proxyDecoders";

        from(endpointUri)
                .routeId("tcp-listener-" + tcpPort)
                .log("[TCP] Message received on port " + tcpPort)
                .process(this::process);
    }

    private void process(Exchange exchange) {
        String message = exchange.getIn().getBody(String.class);
        String remote = exchange.getIn().getHeader("CamelNettyRemoteAddress", String.class);
        String local = exchange.getIn().getHeader("CamelNettyLocalAddress", String.class);

        // 🔹 Start log block
        log.info("PROXYPROTOCOL | ===== New TCP Exchange Received on Port {} =====", tcpPort);

        // 🔹 Log connection details
        log.info("PROXYPROTOCOL | Connection Info | Remote: {} | Local: {}", remote, local);

        // 🔹 Log all headers
        StringBuilder headerLog = new StringBuilder("PROXYPROTOCOL | Exchange Headers:\n");
        exchange.getIn().getHeaders().forEach((key, value) -> headerLog.append("PROXYPROTOCOL |   ").append(key)
                .append(": ").append(value).append("\n"));
        log.info(headerLog.toString().trim());

        // 🔹 Log raw message body
        log.info("PROXYPROTOCOL | Raw Body:\n{}", message);

        // 🔹 If Proxy Protocol info is enabled
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
                log.info("PROXYPROTOCOL | Proxy Protocol v2 enabled, but no HAProxyMessage found.");
            }
        } else {
            log.info("PROXYPROTOCOL | PRINT_PROXY_PROTOCOL_MESSAGE=false → skipping HAProxy header parse.");
        }

        // 🔹 Response log
        String response = "ACK received on port " + tcpPort;
        exchange.getMessage().setBody(response);
        log.info("PROXYPROTOCOL | Response Sent: {}", response);

        // 🔹 End log block
        log.info("PROXYPROTOCOL | ===== End of TCP Exchange =====");
    }
}
