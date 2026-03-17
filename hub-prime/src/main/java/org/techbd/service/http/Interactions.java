package org.techbd.service.http;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.techbd.conf.Configuration;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterUserInteraction;
import org.techbd.util.JsonText.ByteArrayToStringOrJsonSerializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotNull;

@Component
public class Interactions {
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

    private static final int MAX_IN_MEMORY_HISTORY = 50;

    private final Map<UUID, RequestResponseEncountered> history = Collections
            .synchronizedMap(new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, RequestResponseEncountered> eldest) {
                    return size() > MAX_IN_MEMORY_HISTORY;
                }
            });

    public RequestResponseEncountered addHistory(final @NotNull RequestResponseEncountered rre) {
        history.put(rre.interactionId(), rre);
        return rre;
    }

    public Map<UUID, RequestResponseEncountered> getHistory() {
        return Collections.unmodifiableMap(history);
    }

    public record Tenant(String tenantId, String name) {
        public Tenant(@NonNull String tenantId) {
            this(tenantId, "unspecified");
        }

        public Tenant(final @NonNull HttpServletRequest request) {
            this(request.getHeader(Configuration.Servlet.HeaderName.Request.TENANT_ID),
                    request.getHeader(Configuration.Servlet.HeaderName.Request.TENANT_NAME));
        }
    }

    public record Header(String name, String value) {
    }

    public record RequestEncountered(
            UUID requestId,
            Tenant tenant,
            String method,
            String requestUrl,
            String absoluteUrl,
            String requestUri,
            String clientIpAddress,
            String userAgent,
            Instant encounteredAt,
            List<Header> headers,
            Map<String, String[]> parameters,
            String contentType,
            String queryString,
            String protocol,
            @JsonIgnore HttpSession session,
            List<Cookie> cookies,
            @JsonSerialize(using = ByteArrayToStringOrJsonSerializer.class) byte[] requestBody) {

        public RequestEncountered(HttpServletRequest request, byte[] body) throws IOException {
            this(
                    UUID.randomUUID(),
                    new Tenant(request),
                    request.getMethod(),
                    request.getRequestURL().toString(),
                    request.getRequestURL()
                            .append(request.getQueryString() != null ? "?" + request.getQueryString() : "").toString(),
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    Instant.now(),
                    StreamSupport
                            .stream(((Iterable<String>) () -> request.getHeaderNames().asIterator()).spliterator(),
                                    false)
                            .map(headerName -> new Header(headerName, request.getHeader(headerName)))
                            .collect(Collectors.toList()),
                    request.getParameterMap(),
                    request.getContentType(), // Content type
                    request.getQueryString(), // Query string
                    request.getProtocol(), // Protocol
                    request.getSession(false),
                    Arrays.asList(request.getCookies() != null ? request.getCookies() : new Cookie[0]),
                    body // Request body
            );
        }

        public RequestEncountered(HttpServletRequest request, byte[] body,UUID requestId) throws IOException {
            this(
                    requestId,
                    new Tenant(request),
                    request.getMethod(),
                    request.getRequestURL().toString(),
                    request.getRequestURL()
                            .append(request.getQueryString() != null ? "?" + request.getQueryString() : "").toString(),
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    Instant.now(),
                    StreamSupport
                            .stream(((Iterable<String>) () -> request.getHeaderNames().asIterator()).spliterator(),
                                    false)
                            .map(headerName -> new Header(headerName, request.getHeader(headerName)))
                            .collect(Collectors.toList()),
                    request.getParameterMap(),
                    request.getContentType(), // Content type
                    request.getQueryString(), // Query string
                    request.getProtocol(), // Protocol
                    request.getSession(false),
                    Arrays.asList(request.getCookies() != null ? request.getCookies() : new Cookie[0]),
                    body // Request body
            );
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
                this.session,
                this.cookies,
                newRequestBody // Updated request body
        );
    }
    }

    public record ResponseEncountered(
            UUID requestId,
            UUID responseId,
            int status,
            Instant encounteredAt,
            List<Header> headers,
            @JsonSerialize(using = ByteArrayToStringOrJsonSerializer.class) byte[] responseBody) {

        public ResponseEncountered(HttpServletResponse response, RequestEncountered requestEncountered,
                byte[] responseBody) {
            this(
                    requestEncountered.requestId(),
                    UUID.randomUUID(),
                    response.getStatus(),
                    Instant.now(),
                    response.getHeaderNames().stream()
                            .map(headerName -> new Header(headerName, response.getHeader(headerName)))
                            .collect(Collectors.toList()),
                    responseBody);
        }
    }

    public record RequestResponseEncountered(
            UUID interactionId,
            Tenant tenant,
            RequestEncountered request,
            ResponseEncountered response) {
        public RequestResponseEncountered(RequestEncountered request, ResponseEncountered response) {
            this(request.requestId(), request.tenant(), request, response);
        }
    }

    public static void setUserDetails(RegisterUserInteraction rihr, HttpServletRequest request) {
        var curUserName = "API_USER";
        var fusionAuthId = "N/A";
        final var sessionId = request.getRequestedSessionId();
        var userRole = "API_ROLE";
        if (!Constant.isStatelessApiUrl(request.getRequestURI())) { // Call only if not a stateless URL
            final var curUser = FusionAuthUserAuthorizationFilter.getAuthenticatedUser(request);
            if (curUser.isPresent()) {
                final var faUser = curUser.get().faUser();
                if (faUser != null) {
                    curUserName = Optional.ofNullable(faUser.name()).orElse("NO_DATA");
                    fusionAuthId = Optional.ofNullable(faUser.fusionAuthId()).orElse("NO_DATA");
                    userRole = curUser.get().principal().getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.joining(","));
                    userRole = "DEFAULT_ROLE"; // TODO -set user role
                }
            }
        }
        rihr.setPUserName(curUserName);
        rihr.setPUserId(fusionAuthId);
        rihr.setPUserSession(sessionId);
        rihr.setPUserRole(userRole);
    }

    public static void setActiveInteraction(final @NonNull HttpServletRequest request,
            final @NonNull Interactions.RequestResponseEncountered rre) {
        request.setAttribute("activeHttpInteraction", rre);
    }

    public static void setActiveRequestEnc(final @NonNull HttpServletRequest request,
            final @NonNull Interactions.RequestEncountered re) {
        request.setAttribute("activeHttpRequestEncountered", re);
        setActiveRequestTenant(request, re.tenant());
    }

    public static final void setActiveRequestTenant(final @NonNull HttpServletRequest request,
            final @NonNull Interactions.Tenant tenant) {
        request.setAttribute("activeHttpRequestTenant", tenant);
    }

}