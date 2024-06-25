package org.techbd.service.http;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatchers;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.techbd.conf.Configuration;
import org.techbd.service.http.Interactions.RequestResponseEncountered;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;
import org.techbd.util.ArtifactStore;
import org.techbd.util.ArtifactStore.Artifact;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@WebFilter(urlPatterns = "/*")
@Order(-999)
public class InteractionsFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(InteractionsFilter.class.getName());
    public static final Interactions interactions = new Interactions();

    @Value("${org.techbd.service.http.interactions.default-persist-strategy:#{null}}")
    private String defaultPersistStrategy;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private UdiPrimeJpaConfig udiPrimeJpaConfig;

    @Autowired
    private ApplicationContext appContext;

    private InteractionPersistRules iprDB;
    private InteractionPersistRules iprSFTP;

    // TODO: figure out why this is not being read from application.yml (NULL is
    // being called, though)
    @Value("${org.techbd.service.http.interactions.persist.db.uri-matcher.regex:#{null}}")
    private void setPersistinDbMatchers(final List<Object> regexAndMethods) {
        LOG.info("setPersistinDbMatchers %s".formatted(regexAndMethods == null ? "NULL" : regexAndMethods.toString()));
        this.iprDB = new InteractionPersistRules.Builder()
                .withMatchers(
                        regexAndMethods == null
                                ? List.of(".*",
                                        List.of("^/Bundle/.*", "POST", "persistReqPayload persistRespPayload"))
                                : regexAndMethods)
                .build();
        LOG.info("setPersistinDbMatchers %s".formatted(this.iprDB.toString()));
    }

    // TODO: figure out why this is not being read from application.yml (NULL is
    // being called, though)
    @Value("${org.techbd.service.http.interactions.persist.sftp.uri-matcher.regex:#{null}}")
    private void setPersistInSftpMatchers(final List<Object> regexAndMethods) {
        LOG.info(
                "setPersistInSftpMatchers %s".formatted(regexAndMethods == null ? "NULL" : regexAndMethods.toString()));
        this.iprSFTP = new InteractionPersistRules.Builder()
                .withMatchers(regexAndMethods == null
                        ? List.of("^/Bundle/.*", "POST", "persistReqPayload persistRespPayload")
                        : regexAndMethods)
                .build();
        LOG.info("setPersistinDbMatchers %s".formatted(this.iprSFTP.toString()));
    }

    public static final Interactions.Tenant getActiveRequestTenant(final @NonNull HttpServletRequest request) {
        return (Interactions.Tenant) request.getAttribute("activeHttpRequestTenant");
    }

    public static final Interactions.RequestEncountered getActiveRequestEnc(final @NonNull HttpServletRequest request) {
        return (Interactions.RequestEncountered) request.getAttribute("activeHttpRequestEncountered");
    }

    public static final Interactions.RequestResponseEncountered getActiveInteraction(
            final @NonNull HttpServletRequest request) {
        return (Interactions.RequestResponseEncountered) request.getAttribute("activeHttpInteraction");
    }

    protected static final void setActiveRequestTenant(final @NonNull HttpServletRequest request,
            final @NonNull Interactions.Tenant tenant) {
        request.setAttribute("activeHttpRequestTenant", tenant);
    }

    protected static final void setActiveRequestEnc(final @NonNull HttpServletRequest request,
            final @NonNull Interactions.RequestEncountered re) {
        request.setAttribute("activeHttpRequestEncountered", re);
        setActiveRequestTenant(request, re.tenant());
    }

    protected static final void setActiveInteraction(final @NonNull HttpServletRequest request,
            final @NonNull Interactions.RequestResponseEncountered rre) {
        request.setAttribute("activeHttpInteraction", rre);
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
        final var createdAt = OffsetDateTime.now();

        final var persistInteractionDB = iprDB.requestMatcher().matches(origRequest);
        final var persistReqPayloadDB = iprDB.persistReqPayloadMatcher().matches(origRequest);
        final var persistRespPayloadDB = iprDB.persistRespPayloadMatcher().matches(origRequest);

        final var mutatableReq = new ContentCachingRequestWrapper(origRequest);
        final var requestBody = persistReqPayloadDB ? mutatableReq.getContentAsByteArray()
                : "persistPayloads = false".getBytes();

        LOG.info("InteractionsFilter Persist DB %s %s (req body %s, resp body %s)".formatted(requestURI,
                persistInteractionDB, persistReqPayloadDB, persistRespPayloadDB));

        // Prepare a serializable RequestEncountered as early as possible in
        // request cycle and store it as an attribute so that other filters
        // and controllers can use the common "active request" instance.
        final var requestEncountered = new Interactions.RequestEncountered(mutatableReq, requestBody);
        setActiveRequestEnc(origRequest, requestEncountered);

        final var mutatableResp = new ContentCachingResponseWrapper(origResponse);

        chain.doFilter(mutatableReq, mutatableResp);

        RequestResponseEncountered rre = null;
        if (!persistRespPayloadDB) {
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
                .appContext(appContext);

        final var provenance = "%s.doFilterInternal".formatted(InteractionsFilter.class.getName());
        final var artifact = ArtifactStore.jsonArtifact(rre, rre.interactionId().toString(),
                InteractionsFilter.class.getName() + ".interaction", asb.getProvenance());

        if (persistInteractionDB) {
            final var rihr = new RegisterInteractionHttpRequest();
            try {
                final var tenant = rre.tenant();
                final var dsl = udiPrimeJpaConfig.dsl();
                rihr.setInteractionId(rre.interactionId().toString());
                rihr.setNature(Configuration.objectMapper.valueToTree(
                        Map.of("nature", RequestResponseEncountered.class.getName(), "tenant_id",
                                tenant == null ? "N/A" : tenant.tenantId())));
                rihr.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                rihr.setInteractionKey(requestURI);
                rihr.setPayload((Configuration.objectMapper
                        .readTree(artifact.getJsonString().orElse("no artifact.getJsonString() in " + provenance))));
                rihr.setCreatedAt(createdAt); // don't let DB set this, since it might be stored out of order
                rihr.setCreatedBy(InteractionsFilter.class.getName());
                rihr.setProvenance(provenance);
                rihr.execute(dsl.configuration());
            } catch (Exception e) {
                LOG.error("CALL " + rihr.getName() + " error", e);
            }
        }

        final var ps = asb.build();
        mutatableResp.setHeader(Interactions.Servlet.HeaderName.Response.PERSISTENCE_STRATEGY_ARGS, strategyJson);
        if (ps != null) {
            final AtomicInteger info = new AtomicInteger(0);
            final AtomicInteger issue = new AtomicInteger(0);
            mutatableResp.setHeader(Interactions.Servlet.HeaderName.Response.PERSISTENCE_STRATEGY_FACTORY,
                    ps.getClass().getName());
            ps.persist(
                    artifact,
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

    public static class InteractionPersistRules {
        private final RequestMatcher requestMatcher;
        private final RequestMatcher persistReqPayloadMatcher;
        private final RequestMatcher persistRespPayloadMatcher;

        private InteractionPersistRules(final RequestMatcher requestMatcher,
                final RequestMatcher persistReqPayloadMatcher,
                final RequestMatcher persistRespPayloadMatcher) {
            this.requestMatcher = requestMatcher;
            this.persistReqPayloadMatcher = persistReqPayloadMatcher;
            this.persistRespPayloadMatcher = persistRespPayloadMatcher;
        }

        public RequestMatcher requestMatcher() {
            return requestMatcher;
        }

        public RequestMatcher persistReqPayloadMatcher() {
            return persistReqPayloadMatcher;
        }

        public RequestMatcher persistRespPayloadMatcher() {
            return persistRespPayloadMatcher;
        }

        public static class Builder {
            private List<Object> regexAndMethods;
            private boolean strict;

            public Builder strict(final boolean strict) {
                this.strict = strict;
                return this;
            }

            public Builder withMatchers(List<Object> regexAndMethods) {
                this.regexAndMethods = regexAndMethods;
                return this;
            }

            public InteractionPersistRules build() {
                final var requestMatcher = createRequestMatcher(regexAndMethods, strict);
                final var persistReqPayloadMatcher = createPersistReqPayloadMatcher(regexAndMethods, strict);
                final var persistRespPayloadMatcher = createPersistRespPayloadMatcher(regexAndMethods, strict);
                return new InteractionPersistRules(requestMatcher, persistReqPayloadMatcher, persistRespPayloadMatcher);
            }

            private static RequestMatcher createRequestMatcher(final List<Object> regexAndMethods, boolean strict) {
                final var matchers = regexAndMethods == null ? List.of()
                        : (regexAndMethods.stream()
                                .map(item -> createRegexRequestMatcher(item, null))
                                .collect(Collectors.toList()));
                final var matchersArg = matchers.toArray(new RequestMatcher[0]);
                return strict ? RequestMatchers.allOf(matchersArg) : RequestMatchers.anyOf(matchersArg);
            }

            private static RequestMatcher createPersistReqPayloadMatcher(final List<Object> regexAndMethods,
                    boolean strict) {
                final var matchers = regexAndMethods == null ? List.of()
                        : (regexAndMethods.stream()
                                .map(item -> createRegexRequestMatcher(item, "persistReqPayload"))
                                .filter(matcher -> matcher != null)
                                .collect(Collectors.toList()));
                final var matchersArg = matchers.toArray(new RequestMatcher[0]);
                return strict ? RequestMatchers.allOf(matchersArg) : RequestMatchers.anyOf(matchersArg);
            }

            private static RequestMatcher createPersistRespPayloadMatcher(final List<Object> regexAndMethods,
                    boolean strict) {
                final var matchers = regexAndMethods == null ? List.of()
                        : (regexAndMethods.stream()
                                .map(item -> createRegexRequestMatcher(item, "persistRespPayload"))
                                .filter(matcher -> matcher != null)
                                .collect(Collectors.toList()));
                final var matchersArg = matchers.toArray(new RequestMatcher[0]);
                return strict ? RequestMatchers.allOf(matchersArg) : RequestMatchers.anyOf(matchersArg);
            }

            private static RegexRequestMatcher createRegexRequestMatcher(Object item, String filterFlag) {
                // Reads properties or strings that match something like this
                // regex:
                // - ^/api/v1/.*, , persistReqPayload persistRespPayload
                // - [^/admin/.*, POST, persistReqPayload]
                //
                // * The first pattern matches any request with a URI that starts with /api/v1/
                // regardless of the HTTP method. If the request matches this pattern, both
                // request payload and response payload should be persisted.
                // * The second pattern matches any POST request with a URI that starts with
                // /admin/. If the request matches this pattern, only the request payload should
                // be persisted.

                if (item instanceof String) {
                    return new RegexRequestMatcher((String) item, null);
                } else if (item instanceof List) {
                    @SuppressWarnings("unchecked")
                    final var tuple = (List<String>) item;
                    final var tuples = tuple.size();
                    final var pattern = tuple.get(0);
                    final var httpMethod = tuples > 1 ? tuple.get(1).trim() : null;
                    // LOG.info("%s (%s) (%s)".formatted(tuple, pattern, httpMethod));
                    if (filterFlag == null || tuples > 2 && tuple.get(2).contains(filterFlag)) {
                        LOG.info("%s (%s)".formatted(tuple, filterFlag));
                        return new RegexRequestMatcher(pattern,
                                httpMethod == null ? httpMethod : (httpMethod.isBlank() ? null : httpMethod));
                    }
                    return null;
                } else {
                    LOG.error("Invalid InteractionPersistRules configuration `%s`".formatted(item));
                    return null;
                }
            }
        }
    }
}
