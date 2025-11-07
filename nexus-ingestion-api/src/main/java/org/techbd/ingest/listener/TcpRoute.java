package org.techbd.ingest.listener;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;

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
        // Build base endpoint
        String endpointUri = "netty:tcp://0.0.0.0:" + tcpPort
                + "?sync=true"
                + "&allowDefaultCodec=false"
                + "&decoders=#proxyDecoders"; // use custom decoder bean

        from(endpointUri)
            .routeId("tcp-listener-" + tcpPort)
            .log("[TCP] Message received on port " + tcpPort)
            .process(this::process);
    }

    /**
     * Processor that logs message details and extracts Proxy Protocol info when enabled.
     */
    private void process(Exchange exchange) {
        String message = exchange.getIn().getBody(String.class);
        String remote = exchange.getIn().getHeader("CamelNettyRemoteAddress", String.class);
        String local = exchange.getIn().getHeader("CamelNettyLocalAddress", String.class);

        if (printProxyProtocolMessage) {
            Object proxyMsg = exchange.getIn().getHeader("CamelNettyHAProxyMessage");
            if (proxyMsg instanceof HAProxyMessage haProxy) {
                log.info(
                    "[PROXY] Client: {}:{} → Server: {}:{} | Version: {} | Command: {} | Protocol: {}",
                    haProxy.sourceAddress(),
                    haProxy.sourcePort(),
                    haProxy.destinationAddress(),
                    haProxy.destinationPort(),
                    haProxy.protocolVersion(),
                    haProxy.command(),
                    haProxy.proxiedProtocol()
                );
            } else {
                log.info("[PROXY] Proxy Protocol v2 enabled, but no HAProxyMessage found.");
            }
        } else {
            log.info("[TCP] Connection from {} to {}", remote, local);
        }

        // Respond with ACK
        String response = "ACK received on port " + tcpPort;
        exchange.getMessage().setBody(response);
    }

    /**
     * Register a Netty HAProxyMessageDecoder bean for use in the route.
     */
    @org.springframework.context.annotation.Bean(name = "proxyDecoders")
    public java.util.List<io.netty.channel.ChannelHandler> proxyDecoders() {
        return java.util.List.of(new HAProxyMessageDecoder());
    }
}
