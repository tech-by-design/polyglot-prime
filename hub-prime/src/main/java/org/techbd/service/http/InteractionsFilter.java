package org.techbd.service.http;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.techbd.service.http.Interactions.RequestResponseEncountered;
import org.techbd.sql.ArtifactsDataSource;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.util.ArtifactStore;
import org.techbd.util.ArtifactStore.Artifact;
import org.techbd.util.ArtifactStore.JdbcPersistence;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@WebFilter(urlPatterns = "/*")
@Order(-999)
public class InteractionsFilter extends OncePerRequestFilter {
    @Value("${org.techbd.service.http.interactions.default-persist-strategy:#{null}}")
    private String defaultPersistStrategy;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private UdiPrimeJpaConfig udiPrimeJpaConfig;

    @Autowired
    private ApplicationContext appContext;

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

        // for the /Bundle/$validate (at least, and maybe even /Bundle) we want
        // to store the entire request/response cycle including the response payload;
        // for everything else we only want to keep the request and response without
        // payloads
        final var requestURI = origRequest.getRequestURI();
        final var persistPayloads = requestURI.contains("/Bundle/$validate");

        final var mutatableReq = new ContentCachingRequestWrapper(origRequest);
        final var requestBody = persistPayloads ? mutatableReq.getContentAsByteArray()
                : "persistPayloads = false".getBytes();

        // Prepare a serializable RequestEncountered as early as possible in
        // request cycle and store it as an attribute so that other filters
        // and controllers can use the common "active request" instance.
        final var requestEncountered = new Interactions.RequestEncountered(mutatableReq, requestBody);
        setActiveRequestEnc(origRequest, requestEncountered);

        final var mutatableResp = new ContentCachingResponseWrapper(origResponse);

        chain.doFilter(mutatableReq, mutatableResp);

        // for the /Bundle/$validate (at least, and maybe even /Bundle) we want
        // to store the entire request/response cycle including the response payload;
        // for everything else we only want to keep the request and response without
        // payloads
        RequestResponseEncountered rre = null;
        if (!persistPayloads) {
            rre = new Interactions.RequestResponseEncountered(requestEncountered,
                    new Interactions.ResponseEncountered(mutatableResp, requestEncountered,
                            "persistPayloads = false".getBytes()));
        } else {
            rre = new Interactions.RequestResponseEncountered(requestEncountered,
                    new Interactions.ResponseEncountered(mutatableResp, requestEncountered,
                            mutatableResp.getContentAsByteArray()));
        }

        interactions.addHistory(rre);
        setActiveInteraction(mutatableReq, rre);

        // TODO: cache this in the constructor
        final var udiPrimePersistStrategy = new JdbcPersistence(
                new ArtifactsDataSource.PostgreSqlBuilder().dataSource(udiPrimeJpaConfig.udiPrimaryDataSource())
                        .build());

        // we want to find our persistence strategy in either properties or in header;
        // because X-TechBD-Interaction-Persistence-Strategy is global, document it in
        // SwaggerConfig.customGlobalHeaders
        final var strategyJson = Optional
                .ofNullable(mutatableReq.getHeader(Interactions.Servlet.HeaderName.Request.PERSISTENCE_STRATEGY))
                .orElse(defaultPersistStrategy);
        final var asb = new ArtifactStore.Builder()
                .strategyJson(strategyJson)
                .provenanceJson(mutatableReq.getHeader(Interactions.Servlet.HeaderName.Request.PROVENANCE))
                .mailSender(mailSender)
                .always(udiPrimePersistStrategy)
                .appContext(appContext);
        final var ps = asb.build();
        mutatableResp.setHeader(Interactions.Servlet.HeaderName.Response.PERSISTENCE_STRATEGY_ARGS, strategyJson);
        if (ps != null) {
            final AtomicInteger info = new AtomicInteger(0);
            final AtomicInteger issue = new AtomicInteger(0);
            mutatableResp.setHeader(Interactions.Servlet.HeaderName.Response.PERSISTENCE_STRATEGY_FACTORY,
                    ps.getClass().getName());
            ps.persist(
                    ArtifactStore.jsonArtifact(rre, rre.interactionId().toString(),
                            InteractionsFilter.class.getName() + ".interaction", asb.getProvenance()),
                    Optional.of(new ArtifactStore.PersistenceReporter() {
                        @Override
                        public void info(String message) {
                            mutatableResp
                                    .setHeader(Interactions.Servlet.HeaderName.Response.PERSISTENCE_STRATEGY_INSTANCE
                                            + "-Info-" + info.getAndIncrement(), message);
                        }

                        @Override
                        public void issue(String message) {
                            mutatableResp
                                    .setHeader(Interactions.Servlet.HeaderName.Response.PERSISTENCE_STRATEGY_INSTANCE
                                            + "-Issue-" + issue.getAndIncrement(), message);
                        }

                        @Override
                        public void persisted(Artifact artifact, String... location) {
                            // not doing anything with this yet
                        }
                    }));

        }

        mutatableResp.copyBodyToResponse();
    }
}
