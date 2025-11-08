package org.techbd.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "org.techbd" })
public class NexusIngestionApiApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NexusIngestionApiApplication.class);

    @Value("${TCP_SERVER_SOCKER_APPROACH_PORT:5000}")
    private int listenPort;

    public static void main(String[] args) {
        SpringApplication.run(NexusIngestionApiApplication.class, args);
    }

    @Override
    public void run(String... args) {
        try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
            log.info("NATIVE_SERVER_SOCKET | Listening on port {}", listenPort);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            log.error("NATIVE_SERVER_SOCKET | Error starting server on port {}: {}", listenPort, e.getMessage(), e);
        }
    }

    private void handleClient(Socket socket) {
        String remote = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        log.info("NATIVE_SERVER_SOCKET | Connection accepted from {}", remote);

        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            byte[] buffer = new byte[4096];
            int read = in.read(buffer);

            // AWS NLB health check: empty read or HTTP GET/HEAD
            if (read <= 0) {
                log.info("NATIVE_SERVER_SOCKET | Health check detected (empty request). Responding with 200 OK.");
                out.write("HTTP/1.1 200 OK\r\n\r\nOK".getBytes());
                return;
            }

            String payload = new String(buffer, 0, read, "UTF-8").trim();
            if (payload.startsWith("GET") || payload.startsWith("HEAD")) {
                log.info("NATIVE_SERVER_SOCKET | Health check HTTP probe received. Responding with 200 OK.");
                out.write("HTTP/1.1 200 OK\r\n\r\nOK".getBytes());
                return;
            }

            // Try parsing Proxy Protocol v2 header if present
            byte[] data = new byte[read];
            System.arraycopy(buffer, 0, data, 0, read);
            ProxyInfo proxyInfo = parseProxyProtocolV2(data);
            if (proxyInfo != null) {
                log.info("NATIVE_SERVER_SOCKET | Proxy Protocol v2 info: {}", proxyInfo);
            } else {
                log.info("NATIVE_SERVER_SOCKET | No Proxy Protocol header detected");
            }

            // Print raw payload (up to 500 bytes for readability)
            log.info("NATIVE_SERVER_SOCKET | Payload ({} bytes): {}", read,
                    (payload.length() > 500 ? payload.substring(0, 500) + "..." : payload));

            out.write("ACK\n".getBytes());

        } catch (Exception e) {
            log.error("NATIVE_SERVER_SOCKET | Error handling client {}: {}", remote, e.getMessage(), e);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            log.info("NATIVE_SERVER_SOCKET | Connection closed: {}", remote);
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
}
