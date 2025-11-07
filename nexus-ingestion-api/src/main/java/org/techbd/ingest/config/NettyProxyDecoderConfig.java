package org.techbd.ingest.config;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class NettyProxyDecoderConfig {

    @Bean(name = "proxyDecoders")
    public List<ChannelHandler> proxyDecoders() {
        return List.of(new HAProxyMessageDecoder());
    }
}
