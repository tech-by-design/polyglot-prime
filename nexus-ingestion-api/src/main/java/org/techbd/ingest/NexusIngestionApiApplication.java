package org.techbd.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = { "org.techbd" })
public class NexusIngestionApiApplication implements CommandLineRunner {

    @Value("${TCP_SERVER_SOCKER_APPROACH_PORT:5000}")
    private int listenPort;

    public static void main(String[] args) {
        SpringApplication.run(NexusIngestionApiApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
            log("Listening on port " + listenPort);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        }
    }

    private void handleClient(Socket socket) {
        String remote = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        log("Connection accepted from " + remote);

        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            byte[] buffer = new byte[4096];
            int read = in.read(buffer);
            if (read > 0) {
                byte[] data = new byte[read];
                System.arraycopy(buffer, 0, data, 0, read);

                // Try parsing Proxy Protocol v2 header if present
                ProxyInfo proxyInfo = parseProxyProtocolV2(data);
                if (proxyInfo != null) {
                    log("Proxy Protocol v2 info: " + proxyInfo);
                } else {
                    log("No Proxy Protocol header detected");
                }

                // Print raw payload (up to 500 bytes for readability)
                String payload = new String(data, "UTF-8");
                log("Payload (" + read + " bytes): " +
                        (payload.length() > 500 ? payload.substring(0, 500) + "..." : payload));

                out.write("ACK\n".getBytes());
            }

        } catch (Exception e) {
            log("Error handling client " + remote + ": " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            log("Connection closed: " + remote);
        }
    }

    private static class ProxyInfo {
        String srcIp, dstIp;
        int srcPort, dstPort;

        @Override
        public String toString() {
            return "{src_ip=" + srcIp + ", dst_ip=" + dstIp +
                    ", src_port=" + srcPort + ", dst_port=" + dstPort + "}";
        }
    }

    /**
     * Parses AWS NLB Proxy Protocol v2 header if present.
     */
    private ProxyInfo parseProxyProtocolV2(byte[] data) {
        byte[] signature = new byte[]{'\r', '\n', '\r', '\n', 0, '\r', '\n', 'Q', 'U', 'I', 'T', '\n'};
        if (data.length < signature.length + 4) return null;
        for (int i = 0; i < signature.length; i++) {
            if (data[i] != signature[i]) return null;
        }

        byte versionCmd = data[12];
        byte family = data[13];
        int length = ByteBuffer.wrap(data, 14, 2).order(ByteOrder.BIG_ENDIAN).getShort();

        if (family == 0x11 && data.length >= 16 + 12) { // TCP/IPv4
            byte[] src = new byte[4];
            byte[] dst = new byte[4];
            System.arraycopy(data, 16, src, 0, 4);
            System.arraycopy(data, 20, dst, 0, 4);
            int srcPort = ByteBuffer.wrap(data, 24, 2).order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;
            int dstPort = ByteBuffer.wrap(data, 26, 2).order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;

            ProxyInfo info = new ProxyInfo();
            try {
                info.srcIp = InetAddress.getByAddress(src).getHostAddress();
                info.dstIp = InetAddress.getByAddress(dst).getHostAddress();
                info.srcPort = srcPort;
                info.dstPort = dstPort;
            } catch (Exception ignored) {}
            return info;
        }
        return null;
    }

    private void log(String msg) {
        System.out.println("[" + Instant.now() + "] " + msg);
    }
}
