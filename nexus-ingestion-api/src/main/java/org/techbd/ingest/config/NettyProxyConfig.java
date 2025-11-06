package org.techbd.ingest.config;

import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyMessageEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Netty HAProxy/Proxy Protocol support.
 * 
 * This configuration provides beans for HAProxy message encoding/decoding
 * which can be used by Camel Netty4 endpoints to support Proxy Protocol.
 * 
 * Proxy Protocol allows load balancers and proxies to pass the original
 * client IP address and port to the backend server, which is essential
 * for proper logging and security auditing.
 */
@Configuration
public class NettyProxyConfig {
    
    /**
     * Creates an HAProxy message decoder bean for parsing Proxy Protocol headers.
     * 
     * This decoder can handle both Proxy Protocol v1 (text-based) and v2 (binary)
     * formats and extracts the original client connection information.
     * 
     * @return HAProxyMessageDecoder instance for use in Netty pipelines
     */
    @Bean(name = "haProxyDecoder")
    public HAProxyMessageDecoder haProxyMessageDecoder() {
        return new HAProxyMessageDecoder();
    }
    
    /**
     * Creates an HAProxy message encoder bean for generating Proxy Protocol headers.
     * 
     * This encoder is typically used when this service acts as a proxy itself
     * and needs to forward the original client information to downstream services.
     * 
     * @return HAProxyMessageEncoder instance for use in Netty pipelines
     */
    @Bean(name = "haProxyEncoder") 
    public HAProxyMessageEncoder haProxyMessageEncoder() {
        return HAProxyMessageEncoder.INSTANCE;
    }
}