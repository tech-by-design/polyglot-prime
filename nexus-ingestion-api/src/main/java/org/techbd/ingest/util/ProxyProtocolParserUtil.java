package org.techbd.ingest.util;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProxyProtocolParserUtil {

    private static final Logger log = LoggerFactory.getLogger(ProxyProtocolParserUtil.class);

    // Proxy Protocol v2 signature
    private static final byte[] PROXY_V2_SIGNATURE = {
        0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };

    private ProxyProtocolParserUtil() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Check if data starts with Proxy Protocol v2 signature.
     */
    public static boolean startsWithProxyProtocolSignature(byte[] data) {
        if (data == null || data.length < PROXY_V2_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PROXY_V2_SIGNATURE.length; i++) {
            if (data[i] != PROXY_V2_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parse Proxy Protocol v2 header from raw bytes.
     */
    public static ParseResult parseProxyProtocolV2(byte[] data, String contextInfo,String interactionId) {
        if (data.length < 16 || !startsWithProxyProtocolSignature(data)) {
            log.debug("ProxyProtocolParser [{}] | No valid Proxy Protocol v2 signature found. Data length = {} interactionId = {}", contextInfo, data.length, interactionId);
            return new ParseResult(null, data);
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.position(12); // Skip signature

            byte versionCommand = buffer.get();
            byte addressFamily = buffer.get();
            short length = buffer.getShort();

            int version = (versionCommand & 0xF0) >> 4;
            int command = (versionCommand & 0x0F);

            log.debug("ProxyProtocolParser [{}] | Detected Proxy Protocol Version: {}, Command: {} ({}) interactionId={}",
                    contextInfo, version,
                    command, command == 1 ? "PROXY" : (command == 0 ? "LOCAL" : "UNKNOWN"), interactionId);

            log.debug("ProxyProtocolParser [{}] | Address Family and Protocol Byte: 0x{} | Length: {} bytes interactionId={}",
                    contextInfo, String.format("%02X", addressFamily), length, interactionId);

            ProxyInfo info = null;

            // Handle LOCAL command correctly — no proxy info should be parsed
            if (command == 0) { // LOCAL {
                log.debug("ProxyProtocolParser [{}] | LOCAL command detected — ignoring proxy address info interactionId={}", contextInfo, interactionId);
                byte[] payload = Arrays.copyOfRange(data, 16 + length, data.length);
                return new ParseResult(null, payload);
            }

            // Handle PROXY command normally
            if (command == 1) {
                if (addressFamily == 0x11) { // TCP over IPv4
                    log.debug("ProxyProtocolParser [{}] | Parsing TCP over IPv4 header interactionId={}", contextInfo, interactionId);
                    byte[] srcAddr = new byte[4];
                    byte[] dstAddr = new byte[4];
                    buffer.get(srcAddr);
                    buffer.get(dstAddr);
                    int srcPort = buffer.getShort() & 0xFFFF;
                    int dstPort = buffer.getShort() & 0xFFFF;

                    String srcIp = formatIpv4(srcAddr);
                    String dstIp = formatIpv4(dstAddr);

                    log.debug("ProxyProtocolParser [{}] | Source IPv4: {} | Destination IPv4: {} | SrcPort: {} | DstPort: {} interactionId={}",
                            contextInfo, srcIp, dstIp, srcPort, dstPort, interactionId);

                    info = new ProxyInfo(srcIp, dstIp, srcPort, dstPort, "IPv4");

                } else if (addressFamily == 0x21) { // TCP over IPv6
                    log.debug("ProxyProtocolParser [{}] | Parsing TCP over IPv6 header interactionId={}", contextInfo, interactionId);
                    byte[] srcAddr = new byte[16];
                    byte[] dstAddr = new byte[16];
                    buffer.get(srcAddr);
                    buffer.get(dstAddr);
                    int srcPort = buffer.getShort() & 0xFFFF;
                    int dstPort = buffer.getShort() & 0xFFFF;

                    String srcIp = formatIpv6(srcAddr);
                    String dstIp = formatIpv6(dstAddr);

                    log.debug("ProxyProtocolParser [{}] | Source IPv6: {} | Destination IPv6: {} | SrcPort: {} | DstPort: {} interactionId={}",
                            contextInfo, srcIp, dstIp, srcPort, dstPort, interactionId);

                    info = new ProxyInfo(srcIp, dstIp, srcPort, dstPort, "IPv6");
                } else {
                    log.debug("ProxyProtocolParser [{}] | Unsupported address family byte: 0x{} interactionId={}", contextInfo, String.format("%02X", addressFamily), interactionId);
                }
            }

            int headerEnd = 16 + length;
            if (headerEnd > data.length) {
                log.debug("ProxyProtocolParser [{}] | Header length exceeds total data length. HeaderEnd={}, Total={}", contextInfo, headerEnd, data.length, interactionId);
                headerEnd = Math.min(headerEnd, data.length);
            }

            byte[] payload = Arrays.copyOfRange(data, headerEnd, data.length);

            log.debug("ProxyProtocolParser [{}] | Header parsed successfully. Payload size: {} bytes interactionId={}", contextInfo, payload.length, interactionId);
            return new ParseResult(info, payload);
        } catch (Exception e) {
            log.error("ProxyProtocolParser [{}] | Error  {} parsing proxy protocol header interactionId={} ", contextInfo, interactionId, e);
            return new ParseResult(null, data);
        }
    }

    private static String formatIpv4(byte[] addr) {
        return String.format("%d.%d.%d.%d",
                addr[0] & 0xFF, addr[1] & 0xFF, addr[2] & 0xFF, addr[3] & 0xFF);
    }

    private static String formatIpv6(byte[] addr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i += 2) {
            if (i > 0) sb.append(":");
            sb.append(String.format("%02x%02x", addr[i] & 0xFF, addr[i + 1] & 0xFF));
        }
        return sb.toString();
    }

    public static String[] parseAddress(String address) {
        String ip = null;
        String port = null;

        if (address != null && address.contains(":")) {
            int lastColonIndex = address.lastIndexOf(':');
            ip = address.substring(0, lastColonIndex);
            port = address.substring(lastColonIndex + 1);
            if (ip.startsWith("/")) {
                ip = ip.substring(1);
            }
        }

        log.debug("ProxyProtocolParser | Parsed address '{}' into IP='{}' Port='{}'", address, ip, port);
        return new String[]{ip, port};
    }

    public static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...(truncated)";
    }

    public static class ProxyInfo {
        public final String srcIp;
        public final String dstIp;
        public final int srcPort;
        public final int dstPort;
        public final String addressFamily;

        public ProxyInfo(String srcIp, String dstIp, int srcPort, int dstPort, String addressFamily) {
            this.srcIp = srcIp;
            this.dstIp = dstIp;
            this.srcPort = srcPort;
            this.dstPort = dstPort;
            this.addressFamily = addressFamily;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s:%d -> %s:%d", addressFamily, srcIp, srcPort, dstIp, dstPort);
        }
    }

    public static class ParseResult {
        public final ProxyInfo proxyInfo;
        public final byte[] payload;

        public ParseResult(ProxyInfo proxyInfo, byte[] payload) {
            this.proxyInfo = proxyInfo;
            this.payload = payload;
        }
    }
}
