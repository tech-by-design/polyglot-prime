package org.techbd.ingest.listener;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * TCP listener route that manually parses Proxy Protocol v2 headers.
 * This approach doesn't rely on HAProxy decoder and reads raw bytes directly.
 */
@Component
public class TcpRouteManualProxyParser extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(TcpRouteManualProxyParser.class);

    // Proxy Protocol v2 signature
    private static final byte[] PROXY_V2_SIGNATURE = {
        0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };

    @Value("${TCP_DISPATCHER_MANUAL_PROXY:6001}")
    private int tcpPort;

    @Value("${PRINT_PROXY_PROTOCOL_MESSAGE:false}")
    private boolean printProxyProtocolMessage;

    @Override
    public void configure() {
        // Use plain netty endpoint without HAProxy decoder
        String endpointUri = "netty:tcp://0.0.0.0:" + tcpPort
                + "?sync=true"
                + "&allowDefaultCodec=false";

        from(endpointUri)
                .routeId("tcp-manual-proxy-" + tcpPort)
                .log("[TCP] Message received on port " + tcpPort)
                .process(this::process);
    }

    private void process(Exchange exchange) {
        byte[] rawData = exchange.getIn().getBody(byte[].class);
        String remote = exchange.getIn().getHeader("CamelNettyRemoteAddress", String.class);
        String local = exchange.getIn().getHeader("CamelNettyLocalAddress", String.class);

        log.info("MANUAL_PROXY | ===== New TCP Exchange Received on Port {} =====", tcpPort);
        log.info("MANUAL_PROXY | Connection Info | Remote: {} | Local: {}", remote, local);

        ProxyInfo proxyInfo = null;
        byte[] payload = rawData;

        if (printProxyProtocolMessage) {
            log.info("MANUAL_PROXY | Parsing Proxy Protocol v2 header manually...");
            ParseResult result = parseProxyProtocolV2(rawData);
            proxyInfo = result.proxyInfo;
            payload = result.payload;

            if (proxyInfo != null) {
                log.info("MANUAL_PROXY | Proxy Protocol v2 Info | Client: {}:{} → Server: {}:{} | Family: {}",
                        proxyInfo.srcIp, proxyInfo.srcPort,
                        proxyInfo.dstIp, proxyInfo.dstPort,
                        proxyInfo.addressFamily);
            } else {
                log.info("MANUAL_PROXY | No Proxy Protocol v2 header detected");
            }
        } else {
            log.info("MANUAL_PROXY | PRINT_PROXY_PROTOCOL_MESSAGE=false → skipping proxy header parse");
        }

        // Log payload
        String payloadStr = new String(payload, StandardCharsets.UTF_8);
        log.info("MANUAL_PROXY | Payload ({} bytes):\n{}", payload.length, 
                payloadStr.length() > 200 ? payloadStr.substring(0, 200) + "..." : payloadStr);

        // Send response
        String response = "ACK received on port " + tcpPort;
        exchange.getMessage().setBody(response);
        log.info("MANUAL_PROXY | Response Sent: {}", response);
        log.info("MANUAL_PROXY | ===== End of TCP Exchange =====");
    }

    /**
     * Manually parse Proxy Protocol v2 header from raw bytes
     */
    private ParseResult parseProxyProtocolV2(byte[] data) {
        // Check if data starts with Proxy Protocol v2 signature
        if (data.length < 16 || !startsWithSignature(data)) {
            return new ParseResult(null, data);
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.position(12); // Skip signature

            byte versionCommand = buffer.get();
            byte addressFamily = buffer.get();
            short length = buffer.getShort();

            // Extract proxy info based on address family
            ProxyInfo info = null;
            if (addressFamily == 0x11) { // TCP over IPv4
                byte[] srcAddr = new byte[4];
                byte[] dstAddr = new byte[4];
                buffer.get(srcAddr);
                buffer.get(dstAddr);
                int srcPort = buffer.getShort() & 0xFFFF;
                int dstPort = buffer.getShort() & 0xFFFF;

                info = new ProxyInfo(
                    formatIpv4(srcAddr),
                    formatIpv4(dstAddr),
                    srcPort,
                    dstPort,
                    "IPv4"
                );
            } else if (addressFamily == 0x21) { // TCP over IPv6
                byte[] srcAddr = new byte[16];
                byte[] dstAddr = new byte[16];
                buffer.get(srcAddr);
                buffer.get(dstAddr);
                int srcPort = buffer.getShort() & 0xFFFF;
                int dstPort = buffer.getShort() & 0xFFFF;

                info = new ProxyInfo(
                    formatIpv6(srcAddr),
                    formatIpv6(dstAddr),
                    srcPort,
                    dstPort,
                    "IPv6"
                );
            }

            // Extract remaining payload after proxy header
            int headerEnd = 16 + length;
            byte[] payload = Arrays.copyOfRange(data, headerEnd, data.length);

            return new ParseResult(info, payload);
        } catch (Exception e) {
            log.error("MANUAL_PROXY | Error parsing proxy protocol header", e);
            return new ParseResult(null, data);
        }
    }

    private boolean startsWithSignature(byte[] data) {
        for (int i = 0; i < PROXY_V2_SIGNATURE.length; i++) {
            if (data[i] != PROXY_V2_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private String formatIpv4(byte[] addr) {
        return String.format("%d.%d.%d.%d",
            addr[0] & 0xFF, addr[1] & 0xFF, addr[2] & 0xFF, addr[3] & 0xFF);
    }

    private String formatIpv6(byte[] addr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i += 2) {
            if (i > 0) sb.append(":");
            sb.append(String.format("%02x%02x", addr[i] & 0xFF, addr[i + 1] & 0xFF));
        }
        return sb.toString();
    }

    // Data classes
    private static class ProxyInfo {
        final String srcIp;
        final String dstIp;
        final int srcPort;
        final int dstPort;
        final String addressFamily;

        ProxyInfo(String srcIp, String dstIp, int srcPort, int dstPort, String addressFamily) {
            this.srcIp = srcIp;
            this.dstIp = dstIp;
            this.srcPort = srcPort;
            this.dstPort = dstPort;
            this.addressFamily = addressFamily;
        }
    }

    private static class ParseResult {
        final ProxyInfo proxyInfo;
        final byte[] payload;

        ParseResult(ProxyInfo proxyInfo, byte[] payload) {
            this.proxyInfo = proxyInfo;
            this.payload = payload;
        }
    }
}