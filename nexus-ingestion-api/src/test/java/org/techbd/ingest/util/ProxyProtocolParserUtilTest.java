package org.techbd.ingest.util;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProxyProtocolParserUtilTest {

    private static final byte[] SIGNATURE = {
        0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };

    /**
     * Helper to build Proxy Protocol v2 header.
     */
    private static byte[] buildProxyHeader(byte versionCommand, byte addressFamily, byte[] addrBlock) {
        short length = (short) addrBlock.length;
        ByteBuffer buf = ByteBuffer.allocate(SIGNATURE.length + 4 + addrBlock.length);
        buf.put(SIGNATURE);
        buf.put(versionCommand);
        buf.put(addressFamily);
        buf.putShort(length);
        buf.put(addrBlock);
        return buf.array();
    }

    @Test
    @DisplayName("Parse valid IPv4 proxy header")
    void testParseValidIPv4() {
        byte versionCommand = (byte) 0x21; // Version 2, PROXY command
        byte addressFamily = 0x11; // TCP over IPv4

        byte[] srcIp = new byte[]{(byte)192, (byte)168, 1, 10};
        byte[] dstIp = new byte[]{(byte)10, 0, 0, 5};
        short srcPort = 5000;
        short dstPort = 8080;

        ByteBuffer addrBuf = ByteBuffer.allocate(12);
        addrBuf.put(srcIp);
        addrBuf.put(dstIp);
        addrBuf.putShort(srcPort);
        addrBuf.putShort(dstPort);

        byte[] data = buildProxyHeader(versionCommand, addressFamily, addrBuf.array());
        ProxyProtocolParserUtil.ParseResult result = ProxyProtocolParserUtil.parseProxyProtocolV2(data, "IPv4-Test","test-interaction-1");

        assertThat(result.proxyInfo).isNotNull();
        assertThat(result.proxyInfo.addressFamily).isEqualTo("IPv4");
        assertThat(result.proxyInfo.srcIp).isEqualTo("192.168.1.10");
        assertThat(result.proxyInfo.dstIp).isEqualTo("10.0.0.5");
        assertThat(result.proxyInfo.srcPort).isEqualTo(5000);
        assertThat(result.proxyInfo.dstPort).isEqualTo(8080);
    }

    @Test
    @DisplayName("Parse valid IPv6 proxy header")
    void testParseValidIPv6() {
        byte versionCommand = (byte) 0x21; // Version 2, PROXY command
        byte addressFamily = 0x21; // TCP over IPv6

        byte[] srcIp = new byte[16];
        byte[] dstIp = new byte[16];
        srcIp[15] = 1; // ::1
        dstIp[15] = 2; // ::2
        short srcPort = 1234;
        short dstPort = 4321;

        ByteBuffer addrBuf = ByteBuffer.allocate(36);
        addrBuf.put(srcIp);
        addrBuf.put(dstIp);
        addrBuf.putShort(srcPort);
        addrBuf.putShort(dstPort);

        byte[] data = buildProxyHeader(versionCommand, addressFamily, addrBuf.array());
        ProxyProtocolParserUtil.ParseResult result = ProxyProtocolParserUtil.parseProxyProtocolV2(data, "IPv6-Test","test-interaction-1");

        assertThat(result.proxyInfo).isNotNull();
        assertThat(result.proxyInfo.addressFamily).isEqualTo("IPv6");
        assertThat(result.proxyInfo.srcIp).endsWith("0001"); // IPv6 ::1
        assertThat(result.proxyInfo.dstIp).endsWith("0002");
        assertThat(result.proxyInfo.srcPort).isEqualTo(1234);
        assertThat(result.proxyInfo.dstPort).isEqualTo(4321);
    }

    @Test
    @DisplayName("Handle LOCAL command (should not parse proxy info)")
    void testLocalCommandIgnored() {
        byte versionCommand = (byte) 0x20; // Version 2, LOCAL command
        byte addressFamily = 0x11;

        byte[] dummyAddr = new byte[12]; // Placeholder IPv4 structure
        byte[] data = buildProxyHeader(versionCommand, addressFamily, dummyAddr);

        ProxyProtocolParserUtil.ParseResult result = ProxyProtocolParserUtil.parseProxyProtocolV2(data, "LOCAL-Test","test-interaction-1");
        // LOCAL means not proxied, so info can be null
        assertThat(result.proxyInfo).isNull();
    }

    @Test
    @DisplayName("Handle invalid signature gracefully")
    void testInvalidSignature() {
        byte[] invalidData = new byte[20];
        ProxyProtocolParserUtil.ParseResult result = ProxyProtocolParserUtil.parseProxyProtocolV2(invalidData, "InvalidSig-Test","test-interaction-1");
        assertThat(result.proxyInfo).isNull();
        assertThat(result.payload).isEqualTo(invalidData);
    }

    @Test
    @DisplayName("Handle truncated IPv4 header")
    void testTruncatedHeader() {
        byte versionCommand = (byte) 0x21;
        byte addressFamily = 0x11;
        byte[] addrData = new byte[4]; // intentionally too short

        byte[] data = buildProxyHeader(versionCommand, addressFamily, addrData);
        ProxyProtocolParserUtil.ParseResult result = ProxyProtocolParserUtil.parseProxyProtocolV2(data, "Truncated-Test","test-interaction-1");

        assertThat(result.proxyInfo).isNull(); // cannot parse incomplete header
    }

    @Test
    @DisplayName("Parse valid IPv4 and extract payload after header")
    void testIPv4WithPayload() {
        byte versionCommand = (byte) 0x21;
        byte addressFamily = 0x11;

        byte[] srcIp = new byte[]{1, 2, 3, 4};
        byte[] dstIp = new byte[]{5, 6, 7, 8};
        short srcPort = 123;
        short dstPort = 456;

        ByteBuffer addrBuf = ByteBuffer.allocate(12);
        addrBuf.put(srcIp);
        addrBuf.put(dstIp);
        addrBuf.putShort(srcPort);
        addrBuf.putShort(dstPort);

        byte[] header = buildProxyHeader(versionCommand, addressFamily, addrBuf.array());
        byte[] payload = "HELLO".getBytes();

        byte[] data = ByteBuffer.allocate(header.length + payload.length)
                .put(header)
                .put(payload)
                .array();

        ProxyProtocolParserUtil.ParseResult result = ProxyProtocolParserUtil.parseProxyProtocolV2(data, "IPv4Payload-Test","test-interaction-1");

        assertThat(result.proxyInfo).isNotNull();
        assertThat(result.payload).containsExactly(payload);
    }
}
