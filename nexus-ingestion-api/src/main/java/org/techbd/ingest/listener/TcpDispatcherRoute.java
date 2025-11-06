package org.techbd.ingest.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.builder.RouteBuilder;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.service.MessageProcessorService;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.HttpUtil;
import org.techbd.ingest.util.TemplateLogger;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.GenericParser;

/**
 * Single dispatcher route that listens on a configured TCP port and, per request,
 * looks up the destination port in PortConfig (x-forwarded-port / headers) and
 * decides whether to reply as MLLP-framed or plain TCP.
 */
public class TcpDispatcherRoute extends RouteBuilder {

    private final TemplateLogger logger;
    private final int listenPort;
    private final MessageProcessorService messageProcessorService;
    private final AppConfig appConfig;
    private final PortConfig portConfig;

    public TcpDispatcherRoute(int listenPort,
                              MessageProcessorService messageProcessorService,
                              AppConfig appConfig,
                              AppLogger appLogger,
                              PortConfig portConfig) {
        this.listenPort = listenPort;
        this.messageProcessorService = messageProcessorService;
        this.appConfig = appConfig;
        this.portConfig = portConfig;
        this.logger = appLogger.getLogger(TcpDispatcherRoute.class);
    }

    @Override
    public void configure() throws Exception {
        String endpointUri = null;
        if (getContext().getComponent("tcp") != null) {
            endpointUri = "tcp://0.0.0.0:" + listenPort + "?sync=true";
        } else if (getContext().getComponent("netty4") != null) {
            endpointUri = "netty4://tcp://0.0.0.0:" + listenPort + "?sync=true";
        } else if (getContext().getComponent("netty") != null) {
            endpointUri = "netty://tcp://0.0.0.0:" + listenPort + "?sync=true";
        } else {
            // Log warning and enumerate available Camel components to aid diagnostics
            try {
                String components = String.join(", ", getContext().getComponentNames());
                logger.warn("TcpDispatcherRoute: no tcp/netty component available on classpath — not starting dispatcher on port {}. Available Camel components: {}", listenPort, components);
            } catch (Exception ex) {
                logger.warn("TcpDispatcherRoute: no tcp/netty component available on classpath — not starting dispatcher on port {}. Failed to list components: {}", listenPort, ex.getMessage());
            }
            return;
        }

        from(endpointUri)
            .routeId("tcp-dispatcher-" + listenPort)
            .process(exchange -> {
                String raw = exchange.getIn().getBody(String.class);
                String interactionId = UUID.randomUUID().toString();

                // normalize headers
                Map<String, String> headers = new HashMap<>();
                exchange.getIn().getHeaders().forEach((k, v) -> {
                    if (k == null || v == null) return;
                    headers.put(k, v instanceof String ? (String) v : String.valueOf(v));
                });

                // resolve destination port from x-forwarded-port or headers
                String portHeader = exchange.getIn().getHeader(Constants.REQ_X_FORWARDED_PORT, String.class);
                if (portHeader == null || portHeader.isBlank()) {
                    portHeader = HttpUtil.extractDestinationPort(headers);
                }

                Integer destPort = null;
                if (portHeader != null) {
                    try { destPort = Integer.valueOf(portHeader.trim()); } catch (NumberFormatException ignored) {}
                }

                // lookup port config entry
                PortConfig.PortEntry entry = null;
                if (destPort != null) {
                    entry = portConfig.findEntryForPort(destPort).orElse(null);
                }

                boolean shouldMllp = false;
                if (entry != null) {
                    String proto = entry.protocol == null ? "" : entry.protocol.trim();
                    String resp = entry.responseType == null ? "" : entry.responseType.trim();
                    shouldMllp = "tcp".equalsIgnoreCase(proto) && "mllp".equalsIgnoreCase(resp);
                }

                logger.info("TcpDispatcher: received interactionId={} destPort={} shouldMllp={}", interactionId, destPort, shouldMllp);

                GenericParser parser = new GenericParser();
                String response;
                try {
                    Message msg = parser.parse(raw == null ? "" : raw);
                    Message ack = msg.generateACK();
                    String ackStr = MllpRoute.addNteWithInteractionId(ack, interactionId, appConfig.getVersion());
                    // best-effort processing; downstream may accept a null RequestContext
                    try {
                        messageProcessorService.processMessage(null, raw, ackStr);
                    } catch (Exception e) {
                        logger.warn("TcpDispatcher: messageProcessorService.processMessage failed (ignoring) interactionId={} : {}", interactionId, e.getMessage());
                    }
                    response = ackStr;
                } catch (Throwable e) {
                    logger.error("TcpDispatcher: failed to process message interactionId={} : {}", interactionId, e.getMessage());
                    response = "MSH|^~\\&|DISPATCHER|LOCAL|||||ACK^O01|1|P|2.3\rMSA|AE|1\rNTE|1||InteractionID:" + interactionId + "\r";
                }

                if (shouldMllp) {
                    String framed = "\u000B" + response + "\u001C\r";
                    exchange.getMessage().setBody(framed);
                } else {
                    exchange.getMessage().setBody(response);
                }
            });
    }
}