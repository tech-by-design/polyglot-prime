package org.techbd.service.api.http;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
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

record Header(String name, String value) {
}

record RequestEncountered(
        UUID requestId,
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
        String sessionId,
        List<Cookie> cookies,
        byte[] requestBody) {

    public RequestEncountered(HttpServletRequest request, byte[] body) throws IOException {
        this(
                UUID.randomUUID(),
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                Instant.now(),
                StreamSupport
                        .stream(((Iterable<String>) () -> request.getHeaderNames().asIterator()).spliterator(), false)
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
        UUID responseId,
        int status,
        Instant encounteredAt,
        List<Header> headers,
        byte[] responseBody) {

    public ResponseEncountered(HttpServletResponse response, RequestEncountered requestEncountered,
            byte[] responseBody) {
        this(
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
        RequestEncountered request,
        ResponseEncountered response) {
    public RequestResponseEncountered(RequestEncountered request, ResponseEncountered response) {
        this(request.requestId(), request, response);
    }
}

@Component
@WebFilter(urlPatterns = "/*")
@Order(-999)
public class InteractionsFilter extends OncePerRequestFilter {
    private final static Logger LOG = Logger.getLogger(InteractionsFilter.class.getName());

    @Value("${org.techbd.service.api.http.filter.recent-req-resp-observables-max}")
    private static int maxObservables = 1000;

    private static final Map<UUID, RequestResponseEncountered> observables = Collections
            .synchronizedMap(new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, RequestResponseEncountered> eldest) {
                    return size() > InteractionsFilter.maxObservables;
                }
            });

    @Override
    protected void doFilterInternal(final @NonNull HttpServletRequest origRequest,
            @NonNull final HttpServletResponse origResponse, @NonNull final FilterChain chain)
            throws IOException, ServletException {
        if (isAsyncDispatch(origRequest)) {
            chain.doFilter(origRequest, origResponse);
            return;
        }

        final var req = new ContentCachingRequestWrapper(origRequest);
        final var resp = new ContentCachingResponseWrapper(origResponse);

        chain.doFilter(req, resp);

        final var requestBody = req.getContentAsByteArray();
        final var responseBody = resp.getContentAsByteArray();

        resp.copyBodyToResponse();

        RequestEncountered requestEncountered = new RequestEncountered(origRequest, requestBody);
        RequestResponseEncountered observed = new RequestResponseEncountered(requestEncountered,
                new ResponseEncountered(resp, requestEncountered, responseBody));
        observables.put(observed.interactionId(), observed);

        LOG.info(observed.interactionId().toString() + " " + Integer.valueOf(observables.size()).toString());
    }

    @Override
    public void destroy() {
        // Cleanup code if needed
    }

    public static Map<UUID, RequestResponseEncountered> getObservables() {
        return Collections.unmodifiableMap(observables);
    }

}
