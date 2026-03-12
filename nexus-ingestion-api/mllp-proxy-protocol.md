# MLLP Route with Proxy Protocol Support

This document describes how to use the enhanced `MllpRoute` class with Proxy Protocol support for load balancer scenarios.

## Overview

The `MllpRoute` class has been enhanced to support the HAProxy Protocol (Proxy Protocol) which allows load balancers and proxies to preserve the original client connection information when forwarding traffic to backend servers.

## Key Features

### 🔹 **Dual Mode Support**
- **Standard MLLP**: Uses Camel's built-in MLLP component
- **Proxy Protocol MLLP**: Uses Netty with HAProxy decoder

### 🔹 **Automatic Detection**
- Automatically detects and processes Proxy Protocol headers
- Falls back to standard connection details when no proxy headers present
- Comprehensive logging for both connection types

### 🔹 **Real Client IP Preservation**
- Extracts original client IP from Proxy Protocol headers
- Maintains source/destination port information
- Logs both proxy and original connection details

## Usage

### Standard MLLP Route (Direct Connections)

```java
MllpRoute route = new MllpRoute(port, messageProcessorService, appConfig, appLogger, portConfig);
```

### MLLP Route with Proxy Protocol Support

```java
MllpRoute route = MllpRoute.withProxyProtocol(port, messageProcessorService, appConfig, appLogger, portConfig);
```

## Configuration Requirements

### 1. Dependencies

Ensure these dependencies are in your `pom.xml`:

```xml
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-netty</artifactId>
    <version>${camel.version}</version>
</dependency>

<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-codec-haproxy</artifactId>
</dependency>
```

### 2. Spring Configuration

The `NettyProxyConfig` class provides the required Spring beans:

```java
@Configuration
public class NettyProxyConfig {
    
    @Bean(name = "haProxyDecoder")
    public HAProxyMessageDecoder haProxyMessageDecoder() {
        return new HAProxyMessageDecoder();
    }
    
    @Bean(name = "haProxyEncoder") 
    public HAProxyMessageEncoder haProxyMessageEncoder() {
        return HAProxyMessageEncoder.INSTANCE;
    }
}
```

## Load Balancer Configuration

### HAProxy Configuration Example

```haproxy
frontend mllp_frontend
    bind *:8080
    default_backend mllp_servers

backend mllp_servers
    mode tcp
    option tcp-check
    # Enable Proxy Protocol v2
    server app1 10.0.0.10:8080 send-proxy-v2 check
    server app2 10.0.0.11:8080 send-proxy-v2 check
```

### AWS Application Load Balancer (ALB)

ALB automatically adds Proxy Protocol v2 headers when configured for TCP mode with target group settings:
- Protocol: TCP
- Enable Proxy Protocol v2 in target group attributes

## Connection Information Extraction

### With Proxy Protocol Enabled

When Proxy Protocol is detected, the route extracts:
- **Original Client IP**: Real client IP address
- **Original Client Port**: Real client port number  
- **Server IP**: Destination server IP
- **Server Port**: Destination server port

### Without Proxy Protocol (Fallback)

Falls back to standard Camel MLLP headers:
- `CamelMllpRemoteAddress`: Connection source
- `CamelMllpLocalAddress`: Connection destination

## Logging Output

### Proxy Protocol Connection
```
[PORT 8080] Proxy Protocol detected - Original client: 192.168.1.100:12345 -> Server: 10.0.0.10:8080, interactionId=abc123
[PORT 8080] Connection opened: 192.168.1.100:12345 -> 10.0.0.10:8080 (proxy=true) interactionId=abc123
```

### Standard Connection
```
[PORT 8080] No Proxy Protocol header detected, using standard connection info, interactionId=def456
[PORT 8080] Connection opened: 192.168.1.100:12345 -> 10.0.0.10:8080 (proxy=false) interactionId=def456
```

## Implementation Differences

### Standard MLLP Route
- Uses `mllp://0.0.0.0:port?autoAck=false` endpoint
- Relies on Camel's MLLP component for connection handling
- Gets connection info from MLLP headers

### Proxy Protocol MLLP Route  
- Uses `netty:tcp://0.0.0.0:port?sync=true&decoders=#haProxyDecoder&textline=true&delimiter=NONE` endpoint
- Uses Netty with HAProxy decoder for Proxy Protocol support
- Extracts connection info from HAProxy headers with fallback to standard headers

## Compatibility

### Camel Versions
- **Camel 4.x**: Uses `netty` component
- **Camel 3.x**: Falls back to `netty4` component if needed

### Proxy Protocol Versions
- **Proxy Protocol v1**: Text-based format
- **Proxy Protocol v2**: Binary format (recommended)

## Best Practices

1. **Use Proxy Protocol v2** for better performance and reliability
2. **Enable comprehensive logging** during initial setup to verify connection details
3. **Test both direct and proxy scenarios** to ensure fallback works correctly
4. **Monitor connection logs** to verify original client IPs are being captured
5. **Configure load balancer health checks** appropriately for TCP mode

## Troubleshooting

### Common Issues

1. **No Proxy Protocol headers detected**
   - Verify load balancer is configured to send Proxy Protocol headers
   - Check that `send-proxy-v2` is enabled in HAProxy configuration

2. **Connection failures**
   - Ensure Netty HAProxy decoder dependency is available
   - Verify Spring bean configuration for HAProxy components

3. **Incorrect client IPs logged**
   - Confirm Proxy Protocol is actually enabled on load balancer
   - Check that route was created with `withProxyProtocol()` method

### Debug Logging

Enable debug logging for detailed connection analysis:
```java
logger.debug("[PORT {}] No Proxy Protocol header detected, using standard connection info, interactionId={}", port, interactionId);
```

## Migration from Standard MLLP

To migrate existing MLLP routes to support Proxy Protocol:

1. **Replace constructor call**:
   ```java
   // Before
   MllpRoute route = new MllpRoute(port, messageProcessorService, appConfig, appLogger, portConfig);
   
   // After  
   MllpRoute route = MllpRoute.withProxyProtocol(port, messageProcessorService, appConfig, appLogger, portConfig);
   ```

2. **Add required dependencies** to `pom.xml`

3. **Ensure NettyProxyConfig** is available in Spring context

4. **Configure load balancer** to send Proxy Protocol headers

5. **Test thoroughly** with both proxy and direct connections