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
                                ch.pipeline().addLast(new HAProxyMessageDecoder());
                                ch.pipeline().addLast(new StringDecoder());
                                ch.pipeline().addLast(new StringEncoder());
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {

                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws IOException {
                                        // 🔹 Handle Proxy Protocol headers
                                        if (msg instanceof HAProxyMessage proxyMsg) {
                                            log.info("NETTY_TCP_SERVER [PROXY] Detected Proxy Protocol v2 header");
                                            log.info("NETTY_TCP_SERVER [PROXY] Client: {}:{} → Server: {}:{}",
                                                    proxyMsg.sourceAddress(), proxyMsg.sourcePort(),
                                                    proxyMsg.destinationAddress(), proxyMsg.destinationPort());
                                            log.info("NETTY_TCP_SERVER [PROXY] Protocol: {} | Version: {} | Command: {}",
                                                    proxyMsg.proxiedProtocol(),
                                                    proxyMsg.protocolVersion(),
                                                    proxyMsg.command());
                                            return;
                                        }

                                        // 🔹 Handle actual TCP message
                                        if (msg instanceof String message) {
                                            String remoteIP = ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
                                            int remotePort = ((InetSocketAddress) ctx.channel().remoteAddress()).getPort();
                                            String localIP = ((InetSocketAddress) ctx.channel().localAddress()).getAddress().getHostAddress();
                                            int localPort = ((InetSocketAddress) ctx.channel().localAddress()).getPort();

                                            log.info("NETTY_TCP_SERVER ==============================");
                                            log.info("NETTY_TCP_SERVER Received message: {}", message);
                                            log.info("NETTY_TCP_SERVER Remote IP: {} | Remote Port: {}", remoteIP, remotePort);
                                            log.info("NETTY_TCP_SERVER Local IP: {} | Local Port: {}", localIP, localPort);
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

                                            // // 🔹 Apply MLLP framing if enabled
                                            // if (ENABLE_MLLP) {
                                            //     response = "\u000B" + response + "\u001C\r";
                                            //     log.info("NETTY_TCP_SERVER Sending MLLP ACK");
                                            // } else {
                                            //     log.info("NETTY_TCP_SERVER Sending plain TCP ACK");
                                            // }

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
