package org.techbd.service.http;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatchers;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.techbd.conf.Configuration;
import org.techbd.service.constants.SourceType;
import org.techbd.service.http.Interactions.RequestResponseEncountered;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.util.StandardCharset;

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

    @Value("${org.techbd.service.http.interactions.saveUserDataToInteractions:true}")
    private boolean saveUserDataToInteractions;
    @Autowired
    private UdiPrimeJpaConfig udiPrimeJpaConfig;

    private InteractionPersistRules iprDB;

    // TODO: figure out why this is not being read from application.yml (NULL is
    // being called, though)
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    @Value("${org.techbd.service.http.interactions.persist.db.uri-matcher.regex:#{null}}")
    private void setPersistInDbMatchers(final List<Object> regexAndMethods) {
        LOG.info("setPersistInDbMatchers %s".formatted(regexAndMethods == null ? "NULL" : regexAndMethods.toString()));
        this.iprDB = new InteractionPersistRules.Builder()
                .withMatchers(
                        regexAndMethods == null
                                ? List.of(
                                        "^/login",
                                        "^/home",
                                        "^/docs", "^/docs/techbd-hub", "^/docs/shinny-fhir-ig", "^/docs/swagger-ui",
                                        "^/docs/swagger-ui/techbd-api", "^/docs/swagger-ui/query-api",
                                        "^/docs/announcements",
                                        "^/console*", "^/console/.*",
                                        "^/content*", "^/content/.*",
                                        "^/data-quality*", "^/data-quality/.*",
                                        "^/needs-attention*", "^/needs-attention/.*",
                                        "^/interactions*", "^/interactions/.*",
                                        "^/dashboard*", "^/dashboard/.*",
                                        "^/api/ux/.*",
                                        "^/api/expect/.*",
                                        "^/metadata",
                                        List.of("^/Hl7.*", "POST", "persistReqPayload persistRespPayload"),
                                        List.of("^/Bundle.*", "POST", "persistReqPayload persistRespPayload"),
                                        List.of("^/flatfile.*", "POST", "persistReqPayload persistRespPayload"))
                                : regexAndMethods)
                .build();
        LOG.info("setPersistInDbMatchers %s".formatted(this.iprDB.toString()));
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
        final var mutatableReq = new ContentCachingRequestWrapper(origRequest);
        final var persistInteractionDB = iprDB.requestMatcher().matches(origRequest);
        final var persistReqPayloadDB = iprDB.persistReqPayloadMatcher().matches(origRequest);
        final var persistRespPayloadDB = iprDB.persistRespPayloadMatcher().matches(origRequest);

        LOG.info("InteractionsFilter Persist DB %s %s (req body %s, resp body %s)".formatted(requestURI,
                persistInteractionDB, persistReqPayloadDB, persistRespPayloadDB));

        // Prepare a serializable RequestEncountered as early as possible in
        // request cycle and store it as an attribute so that other filters
        // and controllers can use the common "active request" instance.
        var requestEncountered = new Interactions.RequestEncountered(mutatableReq, null);
        setActiveRequestEnc(origRequest, requestEncountered);

        final var mutatableResp = new ContentCachingResponseWrapper(origResponse);

        // Check for the X-TechBD-HealthCheck header
        if ("true".equals(origRequest.getHeader(AppConfig.Servlet.HeaderName.Request.HEALTH_CHECK_HEADER))) {
            LOG.info("%s is true, skipping persistence.".formatted(AppConfig.Servlet.HeaderName.Request.HEALTH_CHECK_HEADER));
            chain.doFilter(origRequest, origResponse);  // Skip the rest of the steps, as it is a health check request.
            return;
        }

        chain.doFilter(mutatableReq, mutatableResp);
        if (mutatableResp.getStatus() >= 400 && mutatableResp.getStatus() < 500) {
            LOG.error("Exception in InteractionFilter while processing request to URI : {} ",requestURI);
            mutatableResp.copyBodyToResponse();
            return;
        } 
        final var requestBody = persistReqPayloadDB ? mutatableReq.getContentAsByteArray()
                : "persistPayloads = false".getBytes(StandardCharset.UTF_8);
        requestEncountered = requestEncountered.withRequestBody(requestBody);
        setActiveRequestEnc(origRequest, requestEncountered);
        RequestResponseEncountered rre = null;
        if (!persistRespPayloadDB) {
            rre = new Interactions.RequestResponseEncountered(requestEncountered,
                    new Interactions.ResponseEncountered(mutatableResp, requestEncountered,
                            "persistPayloads = false".getBytes(StandardCharset.UTF_8)));
        } else {
            rre = new Interactions.RequestResponseEncountered(requestEncountered,
                    new Interactions.ResponseEncountered(mutatableResp, requestEncountered,
                            mutatableResp.getContentAsByteArray()));
        }

        interactions.addHistory(rre);
        setActiveInteraction(mutatableReq, rre);
        final var provenance = "%s.doFilterInternal".formatted(InteractionsFilter.class.getName());

        if (persistInteractionDB && !requestURI.equals("/Bundle") && !requestURI.equals("/Bundle/")
        && !requestURI.equals("/Hl7/v2")  && !requestURI.equals("/Hl7/v2/")
        && !requestURI.startsWith("/flatfile/csv")  && !requestURI.startsWith("/flatfile/csv/")
        ) {
            final var rihr = new RegisterInteractionHttpRequest();
            try {
                LOG.info("REGISTER State None : BEGIN for  interaction id : {} tenant id : {}",
                rre.interactionId().toString(), rre.tenant());
                final var tenant = rre.tenant();
                final var dsl = udiPrimeJpaConfig.dsl();
                rihr.setInteractionId(rre.interactionId().toString());
                rihr.setNature((JsonNode)Configuration.objectMapper.valueToTree(
                        Map.of("nature", RequestResponseEncountered.class.getName(), "tenant_id",
                                tenant != null ? tenant.tenantId() != null ? tenant.tenantId() : "N/A" : "N/A")));
                rihr.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                rihr.setInteractionKey(requestURI);
                rihr.setSourceType(SourceType.FHIR.name());
                rihr.setPayload((JsonNode) Configuration.objectMapper.valueToTree(rre));
                rihr.setCreatedAt(createdAt); // don't let DB set this, since it might be stored out of order
                rihr.setCreatedBy(InteractionsFilter.class.getName());
                rihr.setProvenance(provenance);
                // User details
                if (saveUserDataToInteractions) {
                    var curUserName = "API_USER";
                    var gitHubLoginId = "N/A";
                    final var sessionId = origRequest.getRequestedSessionId();
                    var userRole = "API_ROLE";

                    final var curUser = GitHubUserAuthorizationFilter.getAuthenticatedUser(origRequest);
                    if (curUser.isPresent()) {
                        final var ghUser = curUser.get().ghUser();
                        if (null != ghUser) {
                            curUserName = Optional.ofNullable(ghUser.name()).orElse("NO_DATA");
                            gitHubLoginId = Optional.ofNullable(ghUser.gitHubId()).orElse("NO_DATA");
                            userRole = curUser.get().principal().getAuthorities().stream()
                                    .map(GrantedAuthority::getAuthority)
                                    .collect(Collectors.joining(","));
                            LOG.info("userRole: " + userRole);
                            userRole = "DEFAULT_ROLE"; // TODO: Remove this when role is implemented as part of Auth
                        }
                    }
                    rihr.setUserName(curUserName);
                    rihr.setUserId(gitHubLoginId);
                    rihr.setUserSession(sessionId);
                    rihr.setUserRole(userRole);
                } else {
                    LOG.info("User details are not saved with Interaction as saveUserDataToInteractions: "
                            + saveUserDataToInteractions);
                }

                rihr.execute(dsl.configuration());
                LOG.info("REGISTER State None : END for  interaction id : {} tenant id : {}",
                rre.interactionId().toString(), rre.tenant());
            } catch (Exception e) {
                LOG.error("ERROR:: REGISTER State None  for  interaction id : {} tenant id : {} : CALL " + rihr.getName() + " error",  rre.interactionId().toString(), rre.tenant(),e);
            }
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
                        : regexAndMethods.stream()
                                .map(item -> createRegexRequestMatcher(item, null))
                                .collect(Collectors.toList());
                final var matchersArg = matchers.toArray(new RequestMatcher[0]);
                return strict ? RequestMatchers.allOf(matchersArg) : RequestMatchers.anyOf(matchersArg);
            }

            private static RequestMatcher createPersistReqPayloadMatcher(final List<Object> regexAndMethods,
                    boolean strict) {
                final var matchers = regexAndMethods == null ? List.of()
                        : regexAndMethods.stream()
                                .map(item -> createRegexRequestMatcher(item, "persistReqPayload"))
                                .filter(matcher -> matcher != null)
                                .collect(Collectors.toList());
                final var matchersArg = matchers.toArray(new RequestMatcher[0]);
                return strict ? RequestMatchers.allOf(matchersArg) : RequestMatchers.anyOf(matchersArg);
            }

            private static RequestMatcher createPersistRespPayloadMatcher(final List<Object> regexAndMethods,
                    boolean strict) {
                final var matchers = regexAndMethods == null ? List.of()
                        : regexAndMethods.stream()
                                .map(item -> createRegexRequestMatcher(item, "persistRespPayload"))
                                .filter(matcher -> matcher != null)
                                .collect(Collectors.toList());
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
                                (httpMethod == null || httpMethod.isBlank()) ? null : httpMethod);
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
