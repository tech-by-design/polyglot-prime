package org.techbd.service.api.http;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

record Tenant(String tenantId, String name) {
    public Tenant(@NonNull String tenantId) {
        this(tenantId, "unspecified");
    }

    public Tenant(final @NonNull HttpServletRequest request) {
        this(request.getHeader("TECH_BD_FHIR_SERVICE_QE_IDENTIFIER"),
                request.getHeader("TECH_BD_FHIR_SERVICE_QE_NAME"));
    }
}

record Header(String name, String value) {
}

record RequestEncountered(
        UUID requestId,
        Tenant tenant,
        String method,
        String uri,
        String clientIpAddress,
        String userAgent,
        Instant encounteredAt,
        List<Header> headers,
        Map<String, String[]> parameters,
        String contentType,
        String queryString,
        String protocol,
        String servletSessionId,
        List<Cookie> cookies,
        byte[] requestBody) {

    public RequestEncountered(HttpServletRequest request, byte[] body) throws IOException {
        this(
                UUID.randomUUID(),
                new Tenant(request),
                request.getMethod(),
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
                request.getSession(false) != null ? request.getSession(false).getId() : null,
                Arrays.asList(request.getCookies() != null ? request.getCookies() : new Cookie[0]),
                body // Request body
        );
    }
}

record ResponseEncountered(
        UUID requestId,
        UUID responseId,
        int status,
        Instant encounteredAt,
        List<Header> headers,
        byte[] responseBody) {

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

record RequestResponseEncountered(
        UUID interactionId,
        Tenant tenant,
        RequestEncountered request,
        ResponseEncountered response) {
    public RequestResponseEncountered(RequestEncountered request, ResponseEncountered response) {
        this(request.requestId(), request.tenant(), request, response);
    }
}

@Component
@WebFilter(urlPatterns = "/*")
@Order(-999)
public class InteractionsFilter extends OncePerRequestFilter {
    @Value("${org.techbd.service.api.http.filter.recent-req-resp-observables-max}")
    private static int maxObservables = 50;

    private static final Map<UUID, RequestResponseEncountered> observables = Collections
            .synchronizedMap(new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, RequestResponseEncountered> eldest) {
                    return size() > InteractionsFilter.maxObservables;
                }
            });

    protected static final void setActiveRequestTenant(final @NonNull HttpServletRequest request,
            final @NonNull Tenant tenant) {
        request.setAttribute("activeHttpRequestTenant", tenant);
    }

    public static final Tenant getActiveRequestTenant(final @NonNull HttpServletRequest request) {
        return (Tenant) request.getAttribute("activeHttpRequestTenant");
    }

    protected static final void setActiveRequestEnc(final @NonNull HttpServletRequest request,
            final @NonNull RequestEncountered re) {
        request.setAttribute("activeHttpRequestEncountered", re);
        setActiveRequestTenant(request, re.tenant());
    }

    public static final RequestEncountered getActiveRequestEnc(final @NonNull HttpServletRequest request) {
        return (RequestEncountered) request.getAttribute("activeHttpRequestEncountered");
    }

    protected static final void setActiveInteraction(final @NonNull HttpServletRequest request,
            final @NonNull RequestResponseEncountered rre) {
        request.setAttribute("activeHttpInteraction", rre);
    }

    public static final RequestResponseEncountered getActiveInteraction(final @NonNull HttpServletRequest request) {
        return (RequestResponseEncountered) request.getAttribute("activeHttpInteraction");
    }

    @Override
    protected void doFilterInternal(final @NonNull HttpServletRequest origRequest,
            @NonNull final HttpServletResponse origResponse, @NonNull final FilterChain chain)
            throws IOException, ServletException {
        if (isAsyncDispatch(origRequest)) {
            chain.doFilter(origRequest, origResponse);
            return;
        }

        final var req = new ContentCachingRequestWrapper(origRequest);
        final var requestBody = req.getContentAsByteArray();

        // Prepare a serializable RequestEncountered as early as possible in
        // request cycle and store it as an attribute so that other filters
        // and controllers can use the common "active request" instance.
        RequestEncountered requestEncountered = new RequestEncountered(req, requestBody);
        InteractionsFilter.setActiveRequestEnc(origRequest, requestEncountered);

        final var resp = new ContentCachingResponseWrapper(origResponse);

        chain.doFilter(req, resp);

        // TODO: for large response bodies this may be quite expensive in terms
        // of memory so we might want to store in S3, disk, etc. instead.
        final var responseBody = resp.getContentAsByteArray();
        resp.copyBodyToResponse();

        RequestResponseEncountered observed = new RequestResponseEncountered(requestEncountered,
                new ResponseEncountered(resp, requestEncountered, responseBody));
        observables.put(observed.interactionId(), observed);

        InteractionsFilter.setActiveInteraction(req, observed);
    }

    @Override
    public void destroy() {
        // Cleanup code if needed
    }

    public static Map<UUID, RequestResponseEncountered> getObservables() {
        return Collections.unmodifiableMap(observables);
    }
}
