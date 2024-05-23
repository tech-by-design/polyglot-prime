package org.techbd.service.api.http;

import java.io.IOException;
import java.util.List;

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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@WebFilter(urlPatterns = "/*")
@Order(-999)
public class InteractionsFilter extends OncePerRequestFilter {
    @Value("${org.techbd.service.api.http.interactions.default-persist-strategy:diagnostics}")
    private String defaultPersistStratregy = "diagnostics";

    @Value("${org.techbd.service.api.http.interactions.fs-persist-home}")
    private String fsPersistDefaultHome = System.getProperty("user.dir");

    public static final Interactions interactions = new Interactions();

    protected static final void setActiveRequestTenant(final @NonNull HttpServletRequest request,
            final @NonNull Interactions.Tenant tenant) {
        request.setAttribute("activeHttpRequestTenant", tenant);
    }

    public static final Interactions.Tenant getActiveRequestTenant(final @NonNull HttpServletRequest request) {
        return (Interactions.Tenant) request.getAttribute("activeHttpRequestTenant");
    }

    protected static final void setActiveRequestEnc(final @NonNull HttpServletRequest request,
            final @NonNull Interactions.RequestEncountered re) {
        request.setAttribute("activeHttpRequestEncountered", re);
        setActiveRequestTenant(request, re.tenant());
    }

    public static final Interactions.RequestEncountered getActiveRequestEnc(final @NonNull HttpServletRequest request) {
        return (Interactions.RequestEncountered) request.getAttribute("activeHttpRequestEncountered");
    }

    protected static final void setActiveInteraction(final @NonNull HttpServletRequest request,
            final @NonNull Interactions.RequestResponseEncountered rre) {
        request.setAttribute("activeHttpInteraction", rre);
    }

    public static final Interactions.RequestResponseEncountered getActiveInteraction(
            final @NonNull HttpServletRequest request) {
        return (Interactions.RequestResponseEncountered) request.getAttribute("activeHttpInteraction");
    }

    @Override
    protected void doFilterInternal(final @NonNull HttpServletRequest origRequest,
            @NonNull final HttpServletResponse origResponse, @NonNull final FilterChain chain)
            throws IOException, ServletException {
        if (isAsyncDispatch(origRequest)) {
            chain.doFilter(origRequest, origResponse);
            return;
        }

        final var mutatableReq = new ContentCachingRequestWrapper(origRequest);
        final var requestBody = mutatableReq.getContentAsByteArray();

        // Prepare a serializable RequestEncountered as early as possible in
        // request cycle and store it as an attribute so that other filters
        // and controllers can use the common "active request" instance.
        final var requestEncountered = new Interactions.RequestEncountered(mutatableReq, requestBody);
        InteractionsFilter.setActiveRequestEnc(origRequest, requestEncountered);

        final var mutatableResp = new ContentCachingResponseWrapper(origResponse);

        chain.doFilter(mutatableReq, mutatableResp);

        // TODO: for large response bodies this may be quite expensive in terms
        // of memory so we might want to store in S3, disk, etc. instead.
        // TODO: perhaps we should allow persistence args to specify what parts to store
        // (with/without body?)
        final var responseBody = mutatableResp.getContentAsByteArray();

        final var rre = new Interactions.RequestResponseEncountered(requestEncountered,
                new Interactions.ResponseEncountered(mutatableResp, requestEncountered, responseBody));
        interactions.addHistory(rre);

        InteractionsFilter.setActiveInteraction(mutatableReq, rre);
        final var ps = new Interactions.PersistenceSuggestion(mutatableReq, defaultPersistStratregy,
                fsPersistDefaultHome);
        final var strategy = ps.getStrategy();
        mutatableResp.setHeader("TECH_BD_INTERACTION_PERSISTENCE_STRATEGY_ARGS", ps.strategyJson());
        if (strategy != null) {
            mutatableResp.setHeader("TECH_BD_INTERACTION_PERSISTENCE_STRATEGY", strategy.getClass().getName());
            List<Interactions.Header> additionalHeaders = null;
            switch (strategy) {
                case Interactions.PersistenceSuggestion.StrategyResult.Persist p -> {
                    final var instance = p.instance();
                    mutatableResp.setHeader("TECH_BD_INTERACTION_PERSISTENCE_STRATEGY_INSTANCE",
                            instance.getClass().getName());
                    additionalHeaders = instance.persist(rre);
                }
                case Interactions.PersistenceSuggestion.StrategyResult.Invalid invalid -> {
                    additionalHeaders = invalid.headers();
                }
            }
            if (additionalHeaders != null) {
                for (Interactions.Header header : additionalHeaders) {
                    mutatableResp.setHeader(header.name(), header.value());
                }
            }
        }

        // do this last, after all other mutations are completed
        mutatableResp.copyBodyToResponse();
    }
}
