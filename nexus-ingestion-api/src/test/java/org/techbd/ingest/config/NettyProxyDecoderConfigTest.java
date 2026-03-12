package org.techbd.ingest.config;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.channel.ChannelHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class NettyProxyDecoderConfigTest {

    @Test
    void testEachChannelGetsNewHAProxyDecoder() {
        NettyProxyDecoderConfig config = new NettyProxyDecoderConfig();
        List<Supplier<ChannelHandler>> decoderSuppliers = config.proxyDecoders();

        // Simulate first channel
        EmbeddedChannel channel1 = new EmbeddedChannel();
        decoderSuppliers.forEach(supplier -> channel1.pipeline().addLast(supplier.get()));
        Object decoder1 = channel1.pipeline().get(HAProxyMessageDecoder.class);

        // Simulate second channel
        EmbeddedChannel channel2 = new EmbeddedChannel();
        decoderSuppliers.forEach(supplier -> channel2.pipeline().addLast(supplier.get()));
        Object decoder2 = channel2.pipeline().get(HAProxyMessageDecoder.class);

        // Assert that each channel got a decoder
        assertThat(decoder1).isInstanceOf(HAProxyMessageDecoder.class);
        assertThat(decoder2).isInstanceOf(HAProxyMessageDecoder.class);

        // Assert they are NOT the same instance
        assertThat(decoder1).isNotSameAs(decoder2);
    }
}
