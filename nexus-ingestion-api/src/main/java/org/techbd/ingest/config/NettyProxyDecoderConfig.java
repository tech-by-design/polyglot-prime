package org.techbd.ingest.config;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;

/**
 * Provides Netty decoders for Camel routes.
 * Ensures HAProxyMessageDecoder is created per channel (not shared).
 */
@Configuration
public class NettyProxyDecoderConfig {

    @Bean(name = "proxyDecoders")
    public List<Supplier<ChannelHandler>> proxyDecoders() {
        return List.of(() -> new ChannelInboundHandlerAdapter() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                // Add a new HAProxy decoder instance for this channel
                ctx.pipeline().addLast(new HAProxyMessageDecoder());
                // Remove this temporary handler
                ctx.pipeline().remove(this);
            }
        });
    }
}
