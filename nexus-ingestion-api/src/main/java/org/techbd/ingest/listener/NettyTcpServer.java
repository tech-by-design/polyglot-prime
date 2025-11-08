package org.techbd.ingest.listener;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.util.AttributeKey;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;

@Component
public class NettyTcpServer {

    private static final Logger log = LoggerFactory.getLogger(TcpRoute.class);
    private static final AttributeKey<String> CLIENT_IP_KEY = AttributeKey.valueOf("clientIP");
    private static final AttributeKey<Integer> CLIENT_PORT_KEY = AttributeKey.valueOf("clientPort");

    @Value("${NETTY_TCP_SERVER_PORT:7000}")
    private int PORT;

    @PostConstruct
    public void startServer() {
        new Thread(() -> {
            EventLoopGroup boss = new NioEventLoopGroup(1);
            EventLoopGroup worker = new NioEventLoopGroup();

            try {
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(boss, worker)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                log.info("NETTY_TCP_SERVER [LIFECYCLE] Channel initialized: {}", ch);
                                ch.pipeline().addLast(new HAProxyMessageDecoder());
                                ch.pipeline().addLast(new StringDecoder());
                                ch.pipeline().addLast(new StringEncoder());
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {

                                    @Override
                                    public void handlerAdded(ChannelHandlerContext ctx) {
                                        log.info("NETTY_TCP_SERVER [LIFECYCLE] Handler added for channel {}", ctx.channel());
                                    }

                                    @Override
                                    public void handlerRemoved(ChannelHandlerContext ctx) {
                                        log.info("NETTY_TCP_SERVER [LIFECYCLE] Handler removed for channel {}", ctx.channel());
                                    }

                                    @Override
                                    public void channelRegistered(ChannelHandlerContext ctx) {
                                        log.info("NETTY_TCP_SERVER [LIFECYCLE] Channel registered: {}", ctx.channel());
                                    }

                                    @Override
                                    public void channelUnregistered(ChannelHandlerContext ctx) {
                                        log.info("NETTY_TCP_SERVER [LIFECYCLE] Channel unregistered: {}", ctx.channel());
                                    }

                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) {
                                        InetSocketAddress remoteAddr = (InetSocketAddress) ctx.channel().remoteAddress();
                                        InetSocketAddress localAddr = (InetSocketAddress) ctx.channel().localAddress();
                                        log.info("NETTY_TCP_SERVER [LIFECYCLE] Channel ACTIVE — connected from {}:{} → {}:{}",
                                                remoteAddr.getAddress().getHostAddress(), remoteAddr.getPort(),
                                                localAddr.getAddress().getHostAddress(), localAddr.getPort());
                                    }

                                    @Override
                                    public void channelInactive(ChannelHandlerContext ctx) {
                                        log.info("NETTY_TCP_SERVER [LIFECYCLE] Channel INACTIVE — connection closed: {}", ctx.channel());
                                    }

                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws IOException {
                                        log.info("NETTY_TCP_SERVER [HIT] ====================================================");
                                        log.info("NETTY_TCP_SERVER [HIT] Channel received a message — Handler invoked!");
                                        log.info("NETTY_TCP_SERVER [HIT] Channel: {}", ctx.channel());
                                        log.info("NETTY_TCP_SERVER [HIT] Message Type: {}", msg.getClass().getSimpleName());
                                        log.info("NETTY_TCP_SERVER [HIT] ====================================================");

                                        // 🔹 Log RAW incoming message BEFORE type checking
                                        log.info("NETTY_TCP_SERVER [RAW] ==========================================");
                                        log.info("NETTY_TCP_SERVER [RAW] Received Object Type: {}", msg.getClass().getName());
                                        log.info("NETTY_TCP_SERVER [RAW] Object toString(): {}", msg);
                                        log.info("NETTY_TCP_SERVER [RAW] Object Content: {}", msg.toString());

                                        if (msg instanceof String) {
                                            String strMsg = (String) msg;
                                            log.info("NETTY_TCP_SERVER [RAW] String Length: {}", strMsg.length());
                                            log.info("NETTY_TCP_SERVER [RAW] String Content: '{}'", strMsg);
                                            log.info("NETTY_TCP_SERVER [RAW] Hex Dump: {}",
                                                    strMsg.chars()
                                                            .mapToObj(c -> String.format("%02X ", c))
                                                            .collect(java.util.stream.Collectors.joining()));
                                        }
                                        log.info("NETTY_TCP_SERVER [RAW] ==========================================");

                                        // 🔹 Handle Proxy Protocol headers
                                        if (msg instanceof HAProxyMessage proxyMsg) {
                                            log.info("NETTY_TCP_SERVER [PROXY] ========================================");
                                            log.info("NETTY_TCP_SERVER [PROXY] Detected HAProxy Protocol Header");
                                            log.info("NETTY_TCP_SERVER [PROXY] Protocol Version: {}", proxyMsg.protocolVersion());
                                            log.info("NETTY_TCP_SERVER [PROXY] Command: {}", proxyMsg.command());
                                            log.info("NETTY_TCP_SERVER [PROXY] Proxied Protocol: {}", proxyMsg.proxiedProtocol());
                                            log.info("NETTY_TCP_SERVER [PROXY] Source Address: {}", proxyMsg.sourceAddress());
                                            log.info("NETTY_TCP_SERVER [PROXY] Source Port: {}", proxyMsg.sourcePort());
                                            log.info("NETTY_TCP_SERVER [PROXY] Destination Address: {}", proxyMsg.destinationAddress());
                                            log.info("NETTY_TCP_SERVER [PROXY] Destination Port: {}", proxyMsg.destinationPort());
                                            log.info("NETTY_TCP_SERVER [PROXY] Full Message: {}", proxyMsg);
                                            log.info("NETTY_TCP_SERVER [PROXY] ========================================");

                                            if (proxyMsg.command() == HAProxyCommand.PROXY) {
                                                String clientIP = proxyMsg.sourceAddress();
                                                Integer clientPort = proxyMsg.sourcePort();

                                                if (clientIP != null) {
                                                    log.info("NETTY_TCP_SERVER [PROXY] ✓ Storing client IP: {}:{}", clientIP, clientPort);
                                                    ctx.channel().attr(CLIENT_IP_KEY).set(clientIP);
                                                    ctx.channel().attr(CLIENT_PORT_KEY).set(clientPort);
                                                } else {
                                                    log.warn("NETTY_TCP_SERVER [PROXY] ⚠ Client IP is null despite PROXY command");
                                                }
                                            } else {
                                                log.info("NETTY_TCP_SERVER [PROXY] ℹ LOCAL command - likely NLB health check");
                                            }
                                            return;
                                        }

                                        // 🔹 Handle actual TCP message
                                        if (msg instanceof String message) {
                                            String remoteIP = ctx.channel().attr(CLIENT_IP_KEY).get();
                                            Integer remotePort = ctx.channel().attr(CLIENT_PORT_KEY).get();

                                            if (remoteIP == null) {
                                                InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
                                                remoteIP = remote.getAddress().getHostAddress();
                                                remotePort = remote.getPort();
                                                log.info("NETTY_TCP_SERVER Using direct socket address (no proxy header)");
                                            } else {
                                                log.info("NETTY_TCP_SERVER Using proxied client address from HAProxy header");
                                            }

                                            InetSocketAddress local = (InetSocketAddress) ctx.channel().localAddress();
                                            log.info("NETTY_TCP_SERVER ==============================");
                                            log.info("NETTY_TCP_SERVER Received message: {}", message);
                                            log.info("NETTY_TCP_SERVER Remote IP: {} | Remote Port: {}", remoteIP, remotePort);
                                            log.info("NETTY_TCP_SERVER Local IP: {} | Local Port: {}", local.getAddress().getHostAddress(), local.getPort());
                                            log.info("NETTY_TCP_SERVER Channel Info: {}", ctx.channel());
                                            log.info("NETTY_TCP_SERVER ==============================");

                                            String cleanMsg = removeMllp(message.trim());
                                            String response;

                                            try {
                                                GenericParser parser = new GenericParser();
                                                Message hl7Message = parser.parse(cleanMsg);
                                                Message ack = hl7Message.generateACK();
                                                response = new PipeParser().encode(ack);
                                                log.info("NETTY_TCP_SERVER Generated ACK:\n{}", response);
                                            } catch (HL7Exception e) {
                                                log.error("NETTY_TCP_SERVER Failed to parse HL7 message: {}", e.getMessage());
                                                response = createNack("ERR", e.getMessage());
                                            }

                                            ctx.writeAndFlush(response + "\n");
                                        }
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                        log.error("NETTY_TCP_SERVER Exception: {}", cause.getMessage(), cause);
                                        ctx.close();
                                    }
                                });
                            }
                        });

                ChannelFuture future = bootstrap.bind(PORT).sync();
                log.info("NETTY_TCP_SERVER Listening on port {}", PORT);
                future.channel().closeFuture().sync();

            } catch (Exception e) {
                log.error("NETTY_TCP_SERVER Fatal error: {}", e.getMessage(), e);
            } finally {
                boss.shutdownGracefully();
                worker.shutdownGracefully();
            }
        }).start();
    }

    private String removeMllp(String msg) {
        return msg.replaceAll("^[\\u000B]", "").replaceAll("[\\u001C\\r]+$", "");
    }

    private String createNack(String code, String error) {
        return "MSH|^~\\&|SERVER|LOCAL|CLIENT|REMOTE|" + Instant.now() + "||ACK^A01|1|P|2.3\r"
                + "MSA|AE|1|" + error + "\r";
    }
}
