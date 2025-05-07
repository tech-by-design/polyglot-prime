package org.techbd.config;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;
import org.techbd.util.JsonText.ByteArrayToStringOrJsonSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class Interactions {

    private static final int MAX_IN_MEMORY_HISTORY = 50;

    private final Map<UUID, RequestResponseEncountered> history = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, RequestResponseEncountered> eldest) {
            return size() > MAX_IN_MEMORY_HISTORY;
        }
    });

    public RequestResponseEncountered addHistory(RequestResponseEncountered rre) {
        history.put(rre.interactionId(), rre);
        return rre;
    }

    public Map<UUID, RequestResponseEncountered> getHistory() {
        return Collections.unmodifiableMap(history);
    }

    public static class Servlet {
        public static class HeaderName {
            public static final String PREFIX = Configuration.Servlet.HeaderName.PREFIX + "Interaction-";

            public static class Request {
                public static final String PERSISTENCE_STRATEGY = PREFIX + "Persistence-Strategy";
                public static final String PROVENANCE = PREFIX + "Provenance";
            }

            public static class Response {
                public static final String PERSISTENCE_STRATEGY_ARGS = PREFIX + "Persistence-Strategy-Args";
                public static final String PERSISTENCE_STRATEGY_FACTORY = PREFIX + "Persistence-Strategy-Factory";
                public static final String PERSISTENCE_STRATEGY_INSTANCE = PREFIX + "Persistence-Strategy-Instance";
            }
        }
    }

    public record Tenant(String tenantId, String name) {
        public Tenant(Map<String, String> requestParameters) {
            this(
                    requestParameters.getOrDefault("tenantId", "unknown"),
                    requestParameters.getOrDefault("tenantName", "unspecified"));
        }
    }

    public record Header(String name, String value) {
    }

    public record RequestEncountered(
            UUID requestId, Tenant tenant, String method, String requestUrl, String absoluteUrl,
            String requestUri, String clientIpAddress, String userAgent, Instant encounteredAt,
            List<Header> headers, Map<String, String[]> parameters, String contentType,
            String queryString, String protocol, List<String> cookies,
            @JsonSerialize(using = ByteArrayToStringOrJsonSerializer.class) byte[] requestBody) {

        public RequestEncountered(Map<String, String> requestParameters, byte[] body) {
            this(
                    UUID.randomUUID(),
                    new Tenant(requestParameters),
                    requestParameters.getOrDefault("method", "GET"),
                    requestParameters.getOrDefault(Constants.REQUEST_URI, ""),
                    requestParameters.getOrDefault("absoluteUrl", ""),
                    requestParameters.getOrDefault(Constants.REQUEST_URI, ""),
                    requestParameters.getOrDefault("clientIpAddress", "unknown"),
                    requestParameters.getOrDefault(Constants.USER_AGENT, "unknown"),
                    Instant.now(),
                    requestParameters.entrySet().stream()
                            .filter(e -> e.getKey().startsWith("header-"))
                            .map(e -> new Header(e.getKey().replace("header-", ""), e.getValue()))
                            .collect(Collectors.toList()),
                    new HashMap<>(), // Parameters map (can be extended)
                    requestParameters.getOrDefault("contentType", "application/json"),
                    requestParameters.getOrDefault("queryString", ""),
                    requestParameters.getOrDefault("protocol", "HTTP/1.1"),
                    Arrays.asList(requestParameters.getOrDefault("cookies", "").split(";")),
                    body);
        }

        public RequestEncountered(Map<String, String> requestParameters, byte[] body, UUID requestId,
                List<Header> headers) {
            this(
                    requestId,
                    new Tenant(requestParameters),
                    requestParameters.getOrDefault("method", "POST"),
                    requestParameters.getOrDefault(Constants.REQUEST_URI, ""),
                    requestParameters.getOrDefault("absoluteUrl", ""),
                    requestParameters.getOrDefault(Constants.REQUEST_URI, ""),
                    requestParameters.getOrDefault("clientIpAddress", "unknown"),
                    requestParameters.getOrDefault(Constants.USER_AGENT, "unknown"),
                    Instant.now(),
                    headers,
                    new HashMap<>(), // Parameters map (can be extended)
                    requestParameters.getOrDefault("contentType", "application/json"),
                    requestParameters.getOrDefault("queryString", ""),
                    requestParameters.getOrDefault("protocol", "HTTP/1.1"),
                    Arrays.asList(requestParameters.getOrDefault("cookies", "").split(";")),
                    body);
        }

        public RequestEncountered withRequestBody(byte[] newRequestBody) {
            return new RequestEncountered(
                    this.requestId,
                    this.tenant,
                    this.method,
                    this.requestUrl,
                    this.absoluteUrl,
                    this.requestUri,
                    this.clientIpAddress,
                    this.userAgent,
                    this.encounteredAt,
                    this.headers,
                    this.parameters,
                    this.contentType,
                    this.queryString,
                    this.protocol,
                    this.cookies,
                    newRequestBody // Updated request body
            );
        }
    }

    public record ResponseEncountered(
            UUID requestId, int status, Instant encounteredAt, List<Header> headers,
            @JsonSerialize(using = ByteArrayToStringOrJsonSerializer.class) byte[] responseBody) {

        public ResponseEncountered(Map<String, String> responseParameters, RequestEncountered requestEncountered,
                byte[] responseBody, List<Header> headers) {
            this(
                    requestEncountered.requestId(),
                    Integer.parseInt(responseParameters.getOrDefault("status", "200")),
                    Instant.now(),
                    headers,
                    responseBody);
        }
    }

    // public record RequestResponseEncountered(UUID interactionId,
    // RequestEncountered request, ResponseEncountered response) {}
    public record RequestResponseEncountered(
            UUID interactionId,
            Tenant tenant,
            RequestEncountered request,
            ResponseEncountered response) {
        public RequestResponseEncountered(RequestEncountered request, ResponseEncountered response) {
            this(request.requestId(), request.tenant(), request, response);
        }
    }

    public static void setUserDetails(RegisterInteractionHttpRequest rihr, Map<String, String> requestParameters) {
        String curUserName = "API_USER";
        // String sessionId = requestParameters.get(Constants.REQUESTED_SESSION_ID);
        rihr.setUserName(curUserName);
        rihr.setUserSession(UUID.randomUUID().toString()); // TODO -check and add how to get this from mirth
    }

    public static void setActiveInteraction(Map<String, String> requestParameters, RequestResponseEncountered rre) {
        requestParameters.put("activeHttpInteraction", rre.interactionId().toString());
    }

    public static void setActiveRequestEnc(Map<String, String> requestParameters, RequestEncountered re) {
        requestParameters.put("activeHttpRequestEncountered", re.requestId().toString());
        setActiveRequestTenant(requestParameters, re.tenant());
    }

    public static final void setActiveRequestTenant(Map<String, String> requestParameters, Tenant tenant) {
        requestParameters.put("activeHttpRequestTenant", tenant.tenantId());
    }
}
