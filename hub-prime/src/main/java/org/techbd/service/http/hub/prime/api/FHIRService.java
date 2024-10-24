package org.techbd.service.http.hub.prime.api;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.UnexpectedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.techbd.conf.Configuration;
import org.techbd.orchestrate.fhir.OrchestrationEngine;
import org.techbd.orchestrate.fhir.OrchestrationEngine.Device;
import org.techbd.service.http.GitHubUserAuthorizationFilter;
import org.techbd.service.http.Helpers;
import org.techbd.service.http.Interactions;
import org.techbd.service.http.Interactions.RequestEncountered;
import org.techbd.service.http.Interactions.RequestResponseEncountered;
import org.techbd.service.http.InteractionsFilter;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.service.http.hub.prime.AppConfig.DefaultDataLakeApiAuthn;
import org.techbd.service.http.hub.prime.AppConfig.MTlsAwsSecrets;
import org.techbd.service.http.hub.prime.AppConfig.MTlsResources;
import org.techbd.service.http.hub.prime.AppConfig.PostStdinPayloadToNyecDataLakeExternal;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.common.util.StringUtils;
import io.netty.handler.ssl.SslContextBuilder;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

@Service
public class FHIRService {
        private static final String START_TIME_ATTRIBUTE = "startTime";
        private static final Logger LOG = LoggerFactory.getLogger(FHIRService.class.getName());
        private final AppConfig appConfig;
        private final OrchestrationEngine engine;
        private final UdiPrimeJpaConfig udiPrimeJpaConfig;

        @Value("${org.techbd.service.http.interactions.default-persist-strategy:#{null}}")
        private String defaultPersistStrategy;

        @Value("${org.techbd.service.http.interactions.saveUserDataToInteractions:true}")
        private boolean saveUserDataToInteractions;

        public FHIRService(
                        final AppConfig appConfig, final UdiPrimeJpaConfig udiPrimeJpaConfig,
                        OrchestrationEngine engine) {
                this.appConfig = appConfig;
                this.udiPrimeJpaConfig = udiPrimeJpaConfig;
                this.engine = engine;
        }

        public Object processBundle(final @RequestBody @Nonnull String payload,
                        String tenantId,
                        String fhirProfileUrlParam, String fhirProfileUrlHeader, String uaValidationStrategyJson,
                        String customDataLakeApi,
                        String dataLakeApiContentType,
                        String healthCheck,
                        boolean isSync,
                        boolean includeRequestInOutcome,
                        boolean includeIncomingPayloadInDB,
                        HttpServletRequest request, HttpServletResponse response, String provenance,
                        boolean includeOperationOutcome, String mtlsStrategy)
                        throws IOException {
                if (null == dataLakeApiContentType) {
                        dataLakeApiContentType = MediaType.APPLICATION_JSON_VALUE;
                }
                final var fhirProfileUrl = (fhirProfileUrlParam != null) ? fhirProfileUrlParam
                                : (fhirProfileUrlHeader != null) ? fhirProfileUrlHeader
                                                : appConfig.getDefaultSdohFhirProfileUrl();
                final var immediateResult = validate(request, payload, fhirProfileUrl,
                                uaValidationStrategyJson,
                                includeRequestInOutcome);
                final var result = Map.of("OperationOutcome", immediateResult);
                // Check for the X-TechBD-HealthCheck header
                if ("true".equals(healthCheck)) {
                        LOG.info("%s is true, skipping Scoring Engine submission."
                                        .formatted(AppConfig.Servlet.HeaderName.Request.HEALTH_CHECK_HEADER));
                        return result; // Return without proceeding to scoring engine submission
                }
                final var dslContext = udiPrimeJpaConfig.dsl();
                final var jooqCfg = dslContext.configuration();
                addObservabilityHeadersToResponse(request, response);
                Map<String, Object> payloadWithDisposition = registerBundleInteraction(jooqCfg, request,
                                response, payload, result);
                if (null == payloadWithDisposition) {
                        LOG.warn("FHIRService:: ERROR:: Disposition payload is not available.Send Bundle payload to scoring engine for interaction id {}.",
                                        getBundleInteractionId(request));
                        sendToScoringEngine(jooqCfg, request, customDataLakeApi, dataLakeApiContentType,
                                        includeIncomingPayloadInDB, tenantId, payload,
                                        provenance, null, includeOperationOutcome, mtlsStrategy);
                        return result;
                } else {
                        LOG.warn("FHIRService:: Received Disposition payload.Send Disposition payload to scoring engine for interaction id {}.",
                                        getBundleInteractionId(request));
                        sendToScoringEngine(jooqCfg, request, customDataLakeApi, dataLakeApiContentType,
                                        includeIncomingPayloadInDB, tenantId, payload,
                                        provenance, payloadWithDisposition, includeOperationOutcome, mtlsStrategy);
                        return payloadWithDisposition;
                }
        }

        private void addObservabilityHeadersToResponse(HttpServletRequest request, HttpServletResponse response) {
                final var startTime = (Instant) request.getAttribute(START_TIME_ATTRIBUTE);
                final var finishTime = Instant.now();
                final Duration duration = Duration.between(startTime, finishTime);

                final String startTimeText = startTime.toString();
                final String finishTimeText = finishTime.toString();
                final String durationMsText = String.valueOf(duration.toMillis());
                final String durationNsText = String.valueOf(duration.toNanos());

                // set response headers for those clients that can access HTTP headers
                response.addHeader("X-Observability-Metric-Interaction-Start-Time", startTimeText);
                response.addHeader("X-Observability-Metric-Interaction-Finish-Time", finishTimeText);
                response.addHeader("X-Observability-Metric-Interaction-Duration-Nanosecs", durationMsText);
                response.addHeader("X-Observability-Metric-Interaction-Duration-Millisecs", durationNsText);

                // set a cookie which is accessible to a JavaScript user agent that cannot
                // access HTTP headers (usually HTML pages in web browser cannot access HTTP
                // response headers)
                try {
                        final var metricCookie = new Cookie("Observability-Metric-Interaction-Active",
                                        URLEncoder.encode("{ \"startTime\": \"" + startTimeText
                                                        + "\", \"finishTime\": \"" + finishTimeText
                                                        + "\", \"durationMillisecs\": \"" + durationMsText
                                                        + "\", \"durationNanosecs\": \""
                                                        + durationNsText + "\" }", StandardCharsets.UTF_8.toString()));
                        metricCookie.setPath("/"); // Set path as required
                        metricCookie.setHttpOnly(false); // Ensure the cookie is accessible via JavaScript
                        response.addCookie(metricCookie);
                } catch (UnsupportedEncodingException ex) {
                        LOG.error("Exception during setting  Observability-Metric-Interaction-Active cookie to response header",
                                        ex);
                }
        }

        private Map<String, Object> registerBundleInteraction(org.jooq.Configuration jooqCfg,
                        HttpServletRequest request, HttpServletResponse response,
                        String payload, Map<String, Map<String, Object>> validationResult)
                        throws IOException {
                final Interactions interactions = new Interactions();
                final var mutatableReq = new ContentCachingRequestWrapper(request);
                RequestEncountered requestEncountered = null;
                if (null != getBundleInteractionId(request)) {
                        // If its a converted HL7 payload ,it will already have an interaction id.hence
                        // do not create new interactionId
                        requestEncountered = new Interactions.RequestEncountered(mutatableReq, payload.getBytes(),
                                        UUID.fromString(getBundleInteractionId(request)));
                } else {
                        requestEncountered = new Interactions.RequestEncountered(mutatableReq, payload.getBytes());
                }
                final var mutatableResp = new ContentCachingResponseWrapper(response);
                setActiveRequestEnc(mutatableReq, requestEncountered);
                final var rre = new Interactions.RequestResponseEncountered(requestEncountered,
                                new Interactions.ResponseEncountered(mutatableResp, requestEncountered,
                                                Configuration.objectMapper.writeValueAsBytes(validationResult)));
                interactions.addHistory(rre);
                setActiveInteraction(mutatableReq, rre);
                final var provenance = "%s.doFilterInternal".formatted(FHIRService.class.getName());
                final var rihr = new RegisterInteractionHttpRequest();

                try {
                        prepareRequest(rihr, rre, provenance, request);
                        rihr.execute(jooqCfg);
                        JsonNode payloadWithDisposition = rihr.getReturnValue();
                        LOG.info("REGISTER State None, Accept, Disposition: END for interaction id: {} ",
                                        rre.interactionId().toString());
                        return Configuration.objectMapper.convertValue(payloadWithDisposition,
                                        new TypeReference<Map<String, Object>>() {
                                        });
                } catch (Exception e) {
                        LOG.error("ERROR:: REGISTER State None, Accept, Disposition: for interaction id: {} tenant id: {}: CALL "
                                        + rihr.getName() + " error", rre.interactionId().toString(), rre.tenant(), e);
                }
                return null;
        }

        private void prepareRequest(RegisterInteractionHttpRequest rihr, RequestResponseEncountered rre,
                        String provenance, HttpServletRequest request) {
                LOG.info("REGISTER State None, Accept, Disposition: BEGIN for interaction id: {} tenant id: {}",
                                rre.interactionId().toString(), rre.tenant());
                rihr.setInteractionId(rre.interactionId().toString());
                rihr.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                                Map.of("nature", RequestResponseEncountered.class.getName(),
                                                "tenant_id",
                                                rre.tenant() != null ? (rre.tenant().tenantId() != null
                                                                ? rre.tenant().tenantId()
                                                                : "N/A") : "N/A")));
                rihr.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                rihr.setInteractionKey(request.getRequestURI());
                rihr.setPayload((JsonNode) Configuration.objectMapper.valueToTree(rre));
                rihr.setCreatedAt(OffsetDateTime.now());
                rihr.setCreatedBy(InteractionsFilter.class.getName());
                rihr.setProvenance(provenance);
                if (saveUserDataToInteractions) {
                        setUserDetails(rihr, request);
                } else {
                        LOG.info("User details are not saved with Interaction as saveUserDataToInteractions: "
                                        + saveUserDataToInteractions);
                }
        }

        private void setUserDetails(RegisterInteractionHttpRequest rihr, HttpServletRequest request) {
                var curUserName = "API_USER";
                var gitHubLoginId = "N/A";
                final var sessionId = request.getRequestedSessionId();
                var userRole = "API_ROLE";
                final var curUser = GitHubUserAuthorizationFilter.getAuthenticatedUser(request);
                if (curUser.isPresent()) {
                        final var ghUser = curUser.get().ghUser();
                        if (ghUser != null) {
                                curUserName = Optional.ofNullable(ghUser.name()).orElse("NO_DATA");
                                gitHubLoginId = Optional.ofNullable(ghUser.gitHubId()).orElse("NO_DATA");
                                userRole = curUser.get().principal().getAuthorities().stream()
                                                .map(GrantedAuthority::getAuthority)
                                                .collect(Collectors.joining(","));
                                LOG.info("userRole: " + userRole);
                                userRole = "DEFAULT_ROLE"; // TODO -set user role
                        }
                }
                rihr.setUserName(curUserName);
                rihr.setUserId(gitHubLoginId);
                rihr.setUserSession(sessionId);
                rihr.setUserRole(userRole);
        }

        protected static final void setActiveRequestTenant(final @NonNull HttpServletRequest request,
                        final @NonNull Interactions.Tenant tenant) {
                request.setAttribute("activeHttpRequestTenant", tenant);
        }

        private void setActiveRequestEnc(final @NonNull HttpServletRequest request,
                        final @NonNull Interactions.RequestEncountered re) {
                request.setAttribute("activeHttpRequestEncountered", re);
                setActiveRequestTenant(request, re.tenant());
        }

        private void setActiveInteraction(final @NonNull HttpServletRequest request,
                        final @NonNull Interactions.RequestResponseEncountered rre) {
                request.setAttribute("activeHttpInteraction", rre);
        }

        private Map<String, Object> validate(HttpServletRequest request, String payload, String fhirProfileUrl,
                        String uaValidationStrategyJson,
                        boolean includeRequestInOutcome) {
                LOG.info("Getting structure definition Urls from config - Before: ");
                final var igPackages = appConfig.getIgPackages();
                final var igVersion = appConfig.getIgVersion();
                final var fhirUmlsApiKey = appConfig.getFhirUmlsApiKey();
                final var fhirUmlsApiValue = getUmlsApiKeyFromSecretManager(fhirUmlsApiKey);
                final var sessionBuilder = engine.session()
                                .onDevice(Device.createDefault())
                                .withPayloads(List.of(payload))
                                .withFhirProfileUrl(fhirProfileUrl)
                                .withFhirIGPackages(igPackages)
                                .withIgVersion(igVersion)
                                .withFhirUmlsApiKeyValue(fhirUmlsApiValue)
                                .addHapiValidationEngine() // by default
                                // clearExisting is set to true so engines can be fully supplied through header
                                .withUserAgentValidationStrategy(uaValidationStrategyJson, true);
                final var session = sessionBuilder.build();
                final var bundleAsyncInteractionId = getBundleInteractionId(request);
                engine.orchestrate(session);
                // TODO: if there are errors that should prevent forwarding, stop here
                // TODO: need to implement `immediate` (sync) webClient op, right now it's async
                // only
                // immediateResult is what's returned to the user while async operation
                // continues
                final var immediateResult = new HashMap<>(Map.of(
                                "resourceType", "OperationOutcome",
                                "bundleSessionId", bundleAsyncInteractionId, // for tracking in database, etc.
                                "isAsync", true,
                                "validationResults", session.getValidationResults(),
                                "statusUrl",
                                getBaseUrl(request) + "/Bundle/$status/" + bundleAsyncInteractionId.toString(),
                                "device", session.getDevice()));

                if (uaValidationStrategyJson != null) {
                        immediateResult.put("uaValidationStrategy",
                                        Map.of(AppConfig.Servlet.HeaderName.Request.FHIR_VALIDATION_STRATEGY,
                                                        uaValidationStrategyJson,
                                                        "issues",
                                                        sessionBuilder.getUaStrategyJsonIssues()));
                }
                if (includeRequestInOutcome) {
                        immediateResult.put("request", InteractionsFilter.getActiveRequestEnc(request));
                }
                return immediateResult;
        }

        private void sendToScoringEngine(org.jooq.Configuration jooqCfg, HttpServletRequest request,
                        String scoringEngineApiURL,
                        String dataLakeApiContentType,
                        boolean includeIncomingPayloadInDB,
                        String tenantId,
                        String payload,
                        String provenance,
                        Map<String, Object> validationPayloadWithDisposition, boolean includeOperationOutcome,
                        String mtlsStrategy) {

                final var interactionId = getBundleInteractionId(request);
                LOG.info("FHIRService:: sendToScoringEngine BEGIN for interaction id: {} for", interactionId);

                try {
                        Map<String, Object> bundlePayloadWithDisposition = null;
                        LOG.info("FHIRService:: sendToScoringEngine includeOperationOutcome : {} interaction id: {}",
                                        includeOperationOutcome, interactionId);
                        if (includeOperationOutcome && null != validationPayloadWithDisposition) { // todo -revisit
                                LOG.info("FHIRService:: sendToScoringEngine Prepare payload with operation outcome interaction id: {}",
                                                interactionId);
                                bundlePayloadWithDisposition = preparePayload(request,
                                                payload,
                                                validationPayloadWithDisposition);
                        } else {
                                LOG.info("FHIRService:: sendToScoringEngine Send payload without operation outcome interaction id: {}",
                                                interactionId);
                                bundlePayloadWithDisposition = Configuration.objectMapper.readValue(payload,
                                                new TypeReference<Map<String, Object>>() {
                                                });
                        }
                        final var dataLakeApiBaseURL = Optional.ofNullable(scoringEngineApiURL)
                                        .filter(s -> !s.isEmpty())
                                        .orElse(appConfig.getDefaultDatalakeApiUrl());
                        final var defaultDatalakeApiAuthn = appConfig.getDefaultDataLakeApiAuthn();

                        if (null == defaultDatalakeApiAuthn) {
                                LOG.info("###### defaultDatalakeApiAuthn is not defined #######.Hence proceeding with post to scoring engine without mTls for interaction id :{}",
                                                interactionId);
                                handleNoMtls(MTlsStrategy.NO_MTLS, interactionId, tenantId, dataLakeApiBaseURL,
                                                jooqCfg, request,
                                                bundlePayloadWithDisposition, payload, dataLakeApiContentType,
                                                provenance, includeIncomingPayloadInDB);
                        } else {
                                handleMTlsStrategy(defaultDatalakeApiAuthn, interactionId, tenantId, dataLakeApiBaseURL,
                                                jooqCfg, request, bundlePayloadWithDisposition,
                                                payload,
                                                dataLakeApiContentType, provenance, includeIncomingPayloadInDB,
                                                mtlsStrategy);
                        }

                } catch (

                Exception e) {
                        handleError(validationPayloadWithDisposition, e, request);
                } finally {
                        LOG.info("FHIRService:: sendToScoringEngine END for interaction id: {}", interactionId);
                }
        }

        public void handleMTlsStrategy(DefaultDataLakeApiAuthn defaultDatalakeApiAuthn, String interactionId,
                        String tenantId, String dataLakeApiBaseURL,
                        org.jooq.Configuration jooqCfg, HttpServletRequest request,
                        Map<String, Object> bundlePayloadWithDisposition, String payload, String dataLakeApiContentType,
                        String provenance, boolean includeIncomingPayloadInDB, String mtlsStrategyStr) {
                MTlsStrategy mTlsStrategy = null;
                LOG.info("FHIRService:: handleMTlsStrategy MTLS strategy from endpoint :{} for interaction id: {}",
                                mtlsStrategyStr, interactionId);
                LOG.info("FHIRService:: handleMTlsStrategy MTLS strategy from application.yml :{} for interaction id: {}",
                                defaultDatalakeApiAuthn.mTlsStrategy(), interactionId);
                if (StringUtils.isNotEmpty(mtlsStrategyStr)) {
                        LOG.info("FHIRService:: Proceed with mtls strategy from endpoint  :{} for interaction id: {}",
                                        defaultDatalakeApiAuthn.mTlsStrategy(), interactionId);
                        mTlsStrategy = MTlsStrategy.fromString(mtlsStrategyStr);
                } else {
                        LOG.info("FHIRService:: handleMTlsStrategy MTLS strategy from application.yml for interaction id: {}",
                                        interactionId);
                        mTlsStrategy = MTlsStrategy.fromString(defaultDatalakeApiAuthn.mTlsStrategy());
                }
                switch (mTlsStrategy) {
                        case AWS_SECRETS -> handleAwsSecrets(defaultDatalakeApiAuthn.mTlsAwsSecrets(), interactionId,
                                        tenantId, dataLakeApiBaseURL, dataLakeApiContentType,
                                        bundlePayloadWithDisposition, jooqCfg, provenance,
                                        request.getRequestURI(), includeIncomingPayloadInDB, payload);
                        case AWS_SECRETS_TEMP_WITHOUT_HASH ->
                                handleAwsSecretsWithoutHash(defaultDatalakeApiAuthn.mTlsAwsSecrets(), interactionId,
                                                tenantId, dataLakeApiBaseURL, dataLakeApiContentType,
                                                bundlePayloadWithDisposition, jooqCfg, provenance,
                                                request.getRequestURI(), includeIncomingPayloadInDB, payload);
                        case AWS_SECRETS_TEMP_WITHOUT_OPENSSL ->
                                handleAwsSecretsWithoutOpenssl(defaultDatalakeApiAuthn.mTlsAwsSecrets(), interactionId,
                                                tenantId, dataLakeApiBaseURL, dataLakeApiContentType,
                                                bundlePayloadWithDisposition, jooqCfg, provenance,
                                                request.getRequestURI(), includeIncomingPayloadInDB, payload);
                        case AWS_SECRETS_TEMP_FILE ->
                                handleAwsSecretsTemporaryFile(defaultDatalakeApiAuthn.mTlsAwsSecrets(), interactionId,
                                                tenantId, dataLakeApiBaseURL, dataLakeApiContentType,
                                                bundlePayloadWithDisposition, jooqCfg, provenance,
                                                request.getRequestURI(), includeIncomingPayloadInDB, payload);
                        case AWS_SECRETS_TEMP_FILE_WITHOUT_HASH ->
                                handleAwsSecretsTemporaryFileWithoutHash(defaultDatalakeApiAuthn.mTlsAwsSecrets(), interactionId,
                                                tenantId, dataLakeApiBaseURL, dataLakeApiContentType,
                                                bundlePayloadWithDisposition, jooqCfg, provenance,
                                                request.getRequestURI(), includeIncomingPayloadInDB, payload);
                        case AWS_SECRETS_TEMP_FILE_WITHOUT_OPENSSL ->
                                handleAwsSecretsTemporaryFileWithoutOpenssl(defaultDatalakeApiAuthn.mTlsAwsSecrets(), interactionId,
                                                tenantId, dataLakeApiBaseURL, dataLakeApiContentType,
                                                bundlePayloadWithDisposition, jooqCfg, provenance,
                                                request.getRequestURI(), includeIncomingPayloadInDB, payload);
                        case AWS_SECRETS_TEMP_FILE_WITHOUT_OPENSSLANDHASH ->
                                handleAwsSecretsTemporaryFileWithoutHashAndOpenssl(defaultDatalakeApiAuthn.mTlsAwsSecrets(), interactionId,
                                                tenantId, dataLakeApiBaseURL, dataLakeApiContentType,
                                                bundlePayloadWithDisposition, jooqCfg, provenance,
                                                request.getRequestURI(), includeIncomingPayloadInDB, payload);
                        case POST_STDOUT_PAYLOAD_TO_NYEC_DATA_LAKE_EXTERNAL ->
                                handlePostStdoutPayload(interactionId, tenantId, jooqCfg, dataLakeApiBaseURL,
                                                bundlePayloadWithDisposition,
                                                includeIncomingPayloadInDB, payload, provenance, request,
                                                defaultDatalakeApiAuthn.postStdinPayloadToNyecDataLakeExternal());
                        case MTLS_RESOURCES ->
                                handleMtlsResources(interactionId, tenantId, jooqCfg,
                                                bundlePayloadWithDisposition,
                                                includeIncomingPayloadInDB, payload, provenance, request,
                                                dataLakeApiContentType, dataLakeApiBaseURL,
                                                defaultDatalakeApiAuthn.mTlsResources());
                        default ->
                                handleNoMtls(mTlsStrategy, interactionId, tenantId, dataLakeApiBaseURL, jooqCfg,
                                                request,
                                                bundlePayloadWithDisposition, payload, dataLakeApiContentType,
                                                provenance, includeIncomingPayloadInDB);
                }
        }

        private void handleMtlsResources(String interactionId, String tenantId, org.jooq.Configuration jooqCfg,
                        Map<String, Object> bundlePayloadWithDisposition, boolean includeIncomingPayloadInDB,
                        String payload, String provenance, HttpServletRequest request, String dataLakeApiContentType,
                        String dataLakeApiBaseURL,
                        MTlsResources mTlsResources) {
                LOG.info("FHIRService:: handleMtlsResources BEGIN for interaction id: {} tenantid :{} scoring",
                                interactionId,
                                tenantId);
                try {
                        registerStateForward(jooqCfg, provenance, getBundleInteractionId(request),
                                        request.getRequestURI(), tenantId,
                                        Optional.ofNullable(bundlePayloadWithDisposition)
                                                        .orElse(new HashMap<>()),
                                        null, includeIncomingPayloadInDB, payload);

                        if (null == mTlsResources.mTlsKeyResourceName()) {
                                LOG.error("ERROR:: FHIRService:: handleMtlsResources Key location  `mTlsKeyResourceName` is not configured in application.yml for interaction id : {}  tenant id :{} ",
                                                interactionId, tenantId);
                                throw new IllegalArgumentException(
                                                "Client key location `mTlsKeyResourceName` is not configured in application.yml");
                        }
                        if (null == mTlsResources.mTlsCertResourceName()) {
                                LOG.error("ERROR:: FHIRService:: handleMtlsResources Client certificate location `mTlsCertResourceName` not configured in application.yml  for interaction id : {} tenant id : {}",
                                                interactionId, tenantId);
                                throw new IllegalArgumentException(
                                                "Client certificate location `mTlsCertResourceName` not configured in application.yml");
                        }
                        String myClientKey = Files
                                        .readString(Paths.get(mTlsResources.mTlsKeyResourceName()));
                        if (null == myClientKey) {
                                LOG.error("ERROR:: FHIRService:: handleMtlsResources Key not provided.Copy the key to file in location :{} for interaction id :{} tenant id :{} ",
                                                mTlsResources.mTlsKeyResourceName(), interactionId, tenantId);
                                throw new IllegalArgumentException(
                                                "Client key not provided.Copy the key to file in location : "
                                                                + mTlsResources.mTlsKeyResourceName());
                        }
                        LOG.info("FHIRService:: handleMtlsResources Client key fetched successfully for interaction id: {} tenantid :{} ",
                                        interactionId,
                                        tenantId);
                        String myClientCert = Files.readString(Paths.get(mTlsResources.mTlsCertResourceName()));
                        if (null == myClientCert) {
                                LOG.error("ERROR:: FHIRService:: handleMtlsResources Client certificate not provided.Copy the certificate to file in location :{}  for interaction id : {}  tenantId :{}",
                                                mTlsResources.mTlsCertResourceName(), interactionId, tenantId);
                                throw new IllegalArgumentException(
                                                "Client certificate not provided.Copy the certificate to file in location : "
                                                                + mTlsResources.mTlsCertResourceName());
                        }
                        LOG.info("FHIRService:: handleMtlsResources Client cert fetched successfully for interaction id: {} tenantid :{} ",
                                        interactionId,
                                        tenantId);
                        LOG.info("FHIRService:: handleMtlsResources Get SSL Context -BEGIN for interaction id: {} tenantid:{}",
                                        interactionId, tenantId);

                        final var sslContext = SslContextBuilder.forClient()
                                        .keyManager(new ByteArrayInputStream(myClientCert.getBytes()),
                                                        new ByteArrayInputStream(myClientKey.getBytes()))
                                        .build();
                        LOG.info("FHIRService:: handleMtlsResources Get SSL Context -END for interaction id: {} tenantId:{}",
                                        interactionId, tenantId);
                        LOG.info("FHIRService:: handleMtlsResources Create HttpClient for interaction id: {} tenantID:{}",
                                        interactionId, tenantId);
                        HttpClient httpClient = HttpClient.create()
                                        .secure(sslSpec -> sslSpec.sslContext(sslContext));

                        LOG.info("FHIRService:: Create ReactorClientHttpConnector for interaction id: {} tenantId:{}",
                                        interactionId, tenantId);
                        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
                        LOG.info("FHIRService:: Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN with scoring Engine API URL : {} for interactionID :{} tenant Id:{} ",
                                        dataLakeApiBaseURL, interactionId, tenantId);
                        var webClient = WebClient.builder()
                                        .baseUrl(dataLakeApiBaseURL)
                                        .defaultHeader("Content-Type", dataLakeApiContentType)
                                        .clientConnector(connector)
                                        .build();
                        LOG.info("FHIRService:: Build WebClient with MTLS Enabled ReactorClientHttpConnector -END with scoring Engine API URL : {} for interactionID :{} tenant Id:{} ",
                                        dataLakeApiBaseURL, interactionId, tenantId);
                        LOG.info("FHIRService:: handleMtlsResources Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN \n"
                                        +
                                        "with scoring Engine API URL: {} \n" +
                                        "dataLakeApiContentType: {} \n" +
                                        "bundlePayloadWithDisposition: {} \n" +
                                        "for interactionID: {} \n" +
                                        "tenant Id: {}",
                                        dataLakeApiBaseURL,
                                        dataLakeApiContentType,
                                        bundlePayloadWithDisposition == null ? "Payload is null"
                                                        : "Payload is not null",
                                        interactionId,
                                        tenantId);
                        sendPostRequest(webClient, tenantId, bundlePayloadWithDisposition, payload,
                                        dataLakeApiContentType, interactionId,
                                        jooqCfg, provenance, request.getRequestURI(), dataLakeApiBaseURL);
                        LOG.info("FHIRService:: handleMtlsResources Build WebClient with MTLS Enabled ReactorClientHttpConnector -END for interaction Id :{}",
                                        interactionId);
                        LOG.info("FHIRService:: handleMtlsResources END for interaction id: {} tenantid :{} ",
                                        interactionId,
                                        tenantId);
                } catch (Exception ex) {
                        LOG.error("ERROR:: handleMtlsResources Exception while posting to scoring engine with MTLS enabled for interactionId : {}",
                                        interactionId, ex);
                        registerStateFailed(jooqCfg, interactionId,
                                        request.getRequestURI(), tenantId, ex.getMessage(), provenance);

                }
        }

        private void handleNoMtls(MTlsStrategy mTlsStrategy, String interactionId, String tenantId,
                        String dataLakeApiBaseURL,
                        org.jooq.Configuration jooqCfg, HttpServletRequest request,
                        Map<String, Object> bundlePayloadWithDisposition, String payload, String dataLakeApiContentType,
                        String provenance, boolean includeIncomingPayloadInDB) {
                if (!MTlsStrategy.NO_MTLS.value.equals(mTlsStrategy.value)) {
                        LOG.info("#########Invalid MTLS Strategy defined #############: Allowed values are {} .Hence proceeding with post to scoring engine without mTls for interaction id :{}",
                                        MTlsStrategy.getAllValues(), interactionId);
                }
                LOG.info("FHIRService:: handleNoMtls Build WebClient with MTLS  Disabled -BEGIN \n"
                                +
                                "with scoring Engine API URL: {} \n" +
                                "dataLakeApiContentType: {} \n" +
                                "bundlePayloadWithDisposition: {} \n" +
                                "for interactionID: {} \n" +
                                "tenant Id: {}",
                                dataLakeApiBaseURL,
                                dataLakeApiContentType,
                                bundlePayloadWithDisposition == null ? "Payload is null"
                                                : "Payload is not null",
                                interactionId,
                                tenantId);
                var webClient = createWebClient(dataLakeApiBaseURL, jooqCfg, request,
                                tenantId, payload,
                                bundlePayloadWithDisposition, provenance, includeIncomingPayloadInDB,
                                interactionId);
                LOG.info("FHIRService:: createWebClient END for interaction id: {} tenant id :{} ", interactionId,
                                tenantId);
                LOG.info("FHIRService:: sendPostRequest BEGIN for interaction id: {} tenantid :{} ", interactionId,
                                tenantId);
                sendPostRequest(webClient, tenantId, bundlePayloadWithDisposition, payload,
                                dataLakeApiContentType, interactionId,
                                jooqCfg, provenance, request.getRequestURI(), dataLakeApiBaseURL);
                LOG.info("FHIRService:: sendPostRequest END for interaction id: {} tenantid :{} ", interactionId,
                                tenantId);
        }

        private void handleAwsSecretsTemporaryFile(MTlsAwsSecrets mTlsAwsSecrets, String interactionId, String tenantId,
                        String dataLakeApiBaseURL, String dataLakeApiContentType,
                        Map<String, Object> bundlePayloadWithDisposition,
                        org.jooq.Configuration jooqCfg, String provenance, String requestURI,
                        boolean includeIncomingPayloadInDB, String payload) {
                try {
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFile  Proceed with temporary file creation -BEGIN for interactionId : {}",
                                        interactionId);
                        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                                Security.addProvider(new BouncyCastleProvider());
                        }
                        registerStateForward(jooqCfg, provenance, interactionId, requestURI,
                                        tenantId, bundlePayloadWithDisposition, null, includeIncomingPayloadInDB,
                                        payload);

                        if (null == mTlsAwsSecrets || null == mTlsAwsSecrets.mTlsKeySecretName()
                                        || null == mTlsAwsSecrets.mTlsCertSecretName()) {
                                throw new IllegalArgumentException(
                                                "######## Strategy defined is aws-secrets but mTlsKeySecretName and mTlsCertSecretName is not correctly configured. ######### ");
                        }

                        KeyDetails keyDetails = getSecretsFromAWSSecretManager(mTlsAwsSecrets.mTlsKeySecretName(),
                                        mTlsAwsSecrets.mTlsCertSecretName());
                        final String CERTIFICATE = keyDetails.cert();
                        final String PRIVATE_KEY = keyDetails.key();
                        if (StringUtils.isEmpty(CERTIFICATE)) {
                                throw new IllegalArgumentException(
                                                "Certificate read from secrets manager with certificate secret name : {} is null "
                                                                + mTlsAwsSecrets.mTlsCertSecretName());
                        }

                        if (StringUtils.isEmpty(PRIVATE_KEY)) {
                                throw new IllegalArgumentException(
                                                "Private key read from secrets manager with key secret name : {} is null "
                                                                + mTlsAwsSecrets.mTlsKeySecretName());
                        }
                        LOG.info("FHIRService :: validate cert through openssl -BEGIN for interactionId : {}",
                                        interactionId);
                        validateCertificate(CERTIFICATE, interactionId);
                        LOG.info("FHIRService :: Openssl success - certificate is valid -END for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService :: validate KEY through openssl -BEGIN for interactionId : {}",
                                        interactionId);
                        validatePrivateKey(PRIVATE_KEY, interactionId);
                        LOG.info("FHIRService :: kEY IS Successful through openssl for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFile Certificate and Key Details fetched successfully for interactionId : {}",
                                        interactionId);

                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFile Creating SSLContext -BEGIN for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFile Creating  temp file  tempC -BEGIN for interactionId : {}",
                                        interactionId);
                        Path certFile = Files.createTempFile("tempC", ".pem");
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFile Creating  temp file  tempC -END for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFile Creating  temp file  tempK -BEGIN for interactionId : {}",
                                        interactionId);
                        Path keyFile = Files.createTempFile("tempK", ".key");
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFile Creating  temp file  tempK -END for interactionId : {}",
                                        interactionId);
                        try {
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFile Write to  tempc -BEGIN for interactionId : {}",
                                                interactionId);
                                Files.writeString(certFile, CERTIFICATE);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFile Write to  tempC -END for interactionId : {}",
                                                interactionId);
                                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                                byte[] certHashBytes = digest.digest();
                                String certFileHash = HexFormat.of().formatHex(certHashBytes);
                                LOG.info("FHIRService :: Hash for certfile : {} -END for interactionId : {}",
                                                certFileHash, interactionId);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFile Write to  tempK -BEGIN for interactionId : {}",
                                                interactionId);
                                Files.writeString(keyFile, PRIVATE_KEY);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFile Write to  tempK -END for interactionId : {}",
                                                interactionId);
                                MessageDigest digestKey = MessageDigest.getInstance("SHA-256");
                                byte[] keyHashBytes = digestKey.digest();
                                String keyFileHash = HexFormat.of().formatHex(keyHashBytes);
                                LOG.info("FHIRService :: Hash for key file : {} -END for interactionId : {}",
                                                keyFileHash, interactionId);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFile Create SSL context with tempC and tempK -BEGIN for interactionId : {}",
                                                interactionId);
                                final var sslContext = SslContextBuilder.forClient()
                                                .keyManager(Files.newInputStream(certFile),
                                                                Files.newInputStream(keyFile))
                                                .build();
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFile Create SSL context with tempC and tempK -END for interactionId : {}",
                                                interactionId);
                                LOG.info("FHIRService :: handleAwsSecrets SSLContext created successfully for interactionId : {}",
                                                interactionId);

                                HttpClient httpClient = HttpClient.create()
                                                .secure(ssl -> ssl.sslContext(sslContext));

                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFile HttpClient created successfully for interactionId : {}",
                                                interactionId);

                                ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFile ReactorClientHttpConnector created successfully for interactionId : {}",
                                                interactionId);

                                LOG.info("FHIRService:: handleAwsSecretsTemporaryFile Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN \n"
                                                + "with scoring Engine API URL: {} \n" +
                                                "dataLakeApiContentType: {} \n" +
                                                "bundlePayloadWithDisposition: {} \n" +
                                                "for interactionID: {} \n" +
                                                "tenant Id: {}",
                                                dataLakeApiBaseURL,
                                                dataLakeApiContentType,
                                                bundlePayloadWithDisposition == null ? "Payload is null"
                                                                : "Payload is not null",
                                                interactionId,
                                                tenantId);

                                var webClient = WebClient.builder()
                                                .baseUrl(dataLakeApiBaseURL)
                                                .defaultHeader("Content-Type", dataLakeApiContentType)
                                                .clientConnector(connector)
                                                .build();

                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFile Build WebClient with MTLS Enabled ReactorClientHttpConnector -END for interactionId :{}",
                                                interactionId);
                                LOG.info("FHIRService:: handleAwsSecretsTemporaryFile - sendPostRequest BEGIN for interaction id: {} tenantId :{} ",
                                                interactionId, tenantId);

                                sendPostRequest(webClient, tenantId, bundlePayloadWithDisposition, payload,
                                                dataLakeApiContentType, interactionId,
                                                jooqCfg, provenance, requestURI, dataLakeApiBaseURL);

                                LOG.info("FHIRService:: handleAwsSecretsTemporaryFile -sendPostRequest END for interaction id: {} tenantId :{} ",
                                                interactionId, tenantId);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFile Post to scoring engine -END for interactionId :{}",
                                                interactionId);
                        } finally {
                                // Clean up temporary files
                                Files.deleteIfExists(certFile);
                                Files.deleteIfExists(keyFile);
                        }
                } catch (Exception ex) {
                        LOG.error("ERROR:: FHIRService :: handleAwsSecretsTemporaryFile Post to scoring engine FAILED with error :{} for interactionId :{} tenantId:{}",
                                        ex.getMessage(), interactionId, tenantId, ex);
                        registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, ex.getMessage(), provenance);
                }
                LOG.info("FHIRService :: handleAwsSecretsTemporaryFile -END for interactionId : {}", interactionId);
        }

        private void handleAwsSecretsTemporaryFileWithoutOpenssl(MTlsAwsSecrets mTlsAwsSecrets, String interactionId,
                        String tenantId,
                        String dataLakeApiBaseURL, String dataLakeApiContentType,
                        Map<String, Object> bundlePayloadWithDisposition,
                        org.jooq.Configuration jooqCfg, String provenance, String requestURI,
                        boolean includeIncomingPayloadInDB, String payload) {
                try {
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl  Proceed with temporary file creation -BEGIN for interactionId : {}",
                                        interactionId);
                        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                                Security.addProvider(new BouncyCastleProvider());
                        }
                        registerStateForward(jooqCfg, provenance, interactionId, requestURI,
                                        tenantId, bundlePayloadWithDisposition, null, includeIncomingPayloadInDB,
                                        payload);

                        if (null == mTlsAwsSecrets || null == mTlsAwsSecrets.mTlsKeySecretName()
                                        || null == mTlsAwsSecrets.mTlsCertSecretName()) {
                                throw new IllegalArgumentException(
                                                "######## Strategy defined is aws-secrets but mTlsKeySecretName and mTlsCertSecretName is not correctly configured. ######### ");
                        }

                        KeyDetails keyDetails = getSecretsFromAWSSecretManager(mTlsAwsSecrets.mTlsKeySecretName(),
                                        mTlsAwsSecrets.mTlsCertSecretName());
                        final String CERTIFICATE = keyDetails.cert();
                        final String PRIVATE_KEY = keyDetails.key();
                        if (StringUtils.isEmpty(CERTIFICATE)) {
                                throw new IllegalArgumentException(
                                                "Certificate read from secrets manager with certificate secret name : {} is null "
                                                                + mTlsAwsSecrets.mTlsCertSecretName());
                        }

                        if (StringUtils.isEmpty(PRIVATE_KEY)) {
                                throw new IllegalArgumentException(
                                                "Private key read from secrets manager with key secret name : {} is null "
                                                                + mTlsAwsSecrets.mTlsKeySecretName());
                        }
                        // LOG.info("FHIRService :: validate cert through openssl -BEGIN for
                        // interactionId : {}",
                        // interactionId);
                        // validateCertificate(CERTIFICATE, interactionId);
                        // LOG.info("FHIRService :: Openssl success - certificate is valid -END for
                        // interactionId : {}",
                        // interactionId);
                        // LOG.info("FHIRService :: validate KEY through openssl -BEGIN for
                        // interactionId : {}",
                        // interactionId);
                        // validatePrivateKey(PRIVATE_KEY, interactionId);
                        // LOG.info("FHIRService :: kEY IS Successful through openssl for interactionId
                        // : {}",
                        // interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl Certificate and Key Details fetched successfully for interactionId : {}",
                                        interactionId);

                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl Creating SSLContext -BEGIN for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl Creating  temp file  tempC -BEGIN for interactionId : {}",
                                        interactionId);
                        Path certFile = Files.createTempFile("tempC", ".pem");
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl Creating  temp file  tempC -END for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl Creating  temp file  tempK -BEGIN for interactionId : {}",
                                        interactionId);
                        Path keyFile = Files.createTempFile("tempK", ".key");
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl Creating  temp file  tempK -END for interactionId : {}",
                                        interactionId);
                        try {
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl Write to  tempc -BEGIN for interactionId : {}",
                                                interactionId);
                                Files.writeString(certFile, CERTIFICATE);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl Write to  tempC -END for interactionId : {}",
                                                interactionId);
                                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                                byte[] certHashBytes = digest.digest();
                                String certFileHash = HexFormat.of().formatHex(certHashBytes);
                                LOG.info("FHIRService :: Hash for certfile : {} -END for interactionId : {}",
                                                certFileHash, interactionId);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl Write to  tempK -BEGIN for interactionId : {}",
                                                interactionId);
                                Files.writeString(keyFile, PRIVATE_KEY);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl Write to  tempK -END for interactionId : {}",
                                                interactionId);
                                MessageDigest digestKey = MessageDigest.getInstance("SHA-256");
                                byte[] keyHashBytes = digestKey.digest();
                                String keyFileHash = HexFormat.of().formatHex(keyHashBytes);
                                LOG.info("FHIRService :: Hash for key file : {} -END for interactionId : {}",
                                                keyFileHash, interactionId);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl Create SSL context with tempC and tempK -BEGIN for interactionId : {}",
                                                interactionId);
                                final var sslContext = SslContextBuilder.forClient()
                                                .keyManager(Files.newInputStream(certFile),
                                                                Files.newInputStream(keyFile))
                                                .build();
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl Create SSL context with tempC and tempK -END for interactionId : {}",
                                                interactionId);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl SSLContext created successfully for interactionId : {}",
                                                interactionId);

                                HttpClient httpClient = HttpClient.create()
                                                .secure(ssl -> ssl.sslContext(sslContext));

                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl HttpClient created successfully for interactionId : {}",
                                                interactionId);

                                ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl ReactorClientHttpConnector created successfully for interactionId : {}",
                                                interactionId);

                                LOG.info("FHIRService:: handleAwsSecretsTemporaryFileWithoutOpenssl Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN \n"
                                                + "with scoring Engine API URL: {} \n" +
                                                "dataLakeApiContentType: {} \n" +
                                                "bundlePayloadWithDisposition: {} \n" +
                                                "for interactionID: {} \n" +
                                                "tenant Id: {}",
                                                dataLakeApiBaseURL,
                                                dataLakeApiContentType,
                                                bundlePayloadWithDisposition == null ? "Payload is null"
                                                                : "Payload is not null",
                                                interactionId,
                                                tenantId);

                                var webClient = WebClient.builder()
                                                .baseUrl(dataLakeApiBaseURL)
                                                .defaultHeader("Content-Type", dataLakeApiContentType)
                                                .clientConnector(connector)
                                                .build();

                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl Build WebClient with MTLS Enabled ReactorClientHttpConnector -END for interactionId :{}",
                                                interactionId);
                                LOG.info("FHIRService:: handleAwsSecretsTemporaryFileWithoutOpenssl - sendPostRequest BEGIN for interaction id: {} tenantId :{} ",
                                                interactionId, tenantId);

                                sendPostRequest(webClient, tenantId, bundlePayloadWithDisposition, payload,
                                                dataLakeApiContentType, interactionId,
                                                jooqCfg, provenance, requestURI, dataLakeApiBaseURL);

                                LOG.info("FHIRService:: handleAwsSecretsTemporaryFileWithoutOpenssl -sendPostRequest END for interaction id: {} tenantId :{} ",
                                                interactionId, tenantId);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl Post to scoring engine -END for interactionId :{}",
                                                interactionId);
                        } finally {
                                // Clean up temporary files
                                Files.deleteIfExists(certFile);
                                Files.deleteIfExists(keyFile);
                        }
                } catch (Exception ex) {
                        LOG.error("ERROR:: FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl Post to scoring engine FAILED with error :{} for interactionId :{} tenantId:{}",
                                        ex.getMessage(), interactionId, tenantId, ex);
                        registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, ex.getMessage(), provenance);
                }
                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutOpenssl -END for interactionId : {}", interactionId);
        }

        private void handleAwsSecretsTemporaryFileWithoutHash(MTlsAwsSecrets mTlsAwsSecrets, String interactionId,
                        String tenantId,
                        String dataLakeApiBaseURL, String dataLakeApiContentType,
                        Map<String, Object> bundlePayloadWithDisposition,
                        org.jooq.Configuration jooqCfg, String provenance, String requestURI,
                        boolean includeIncomingPayloadInDB, String payload) {
                try {
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash  Proceed with temporary file creation -BEGIN for interactionId : {}",
                                        interactionId);
                        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                                Security.addProvider(new BouncyCastleProvider());
                        }
                        registerStateForward(jooqCfg, provenance, interactionId, requestURI,
                                        tenantId, bundlePayloadWithDisposition, null, includeIncomingPayloadInDB,
                                        payload);

                        if (null == mTlsAwsSecrets || null == mTlsAwsSecrets.mTlsKeySecretName()
                                        || null == mTlsAwsSecrets.mTlsCertSecretName()) {
                                throw new IllegalArgumentException(
                                                "######## Strategy defined is aws-secrets but mTlsKeySecretName and mTlsCertSecretName is not correctly configured. ######### ");
                        }

                        KeyDetails keyDetails = getSecretsFromAWSSecretManager(mTlsAwsSecrets.mTlsKeySecretName(),
                                        mTlsAwsSecrets.mTlsCertSecretName());
                        final String CERTIFICATE = keyDetails.cert();
                        final String PRIVATE_KEY = keyDetails.key();
                        if (StringUtils.isEmpty(CERTIFICATE)) {
                                throw new IllegalArgumentException(
                                                "Certificate read from secrets manager with certificate secret name : {} is null "
                                                                + mTlsAwsSecrets.mTlsCertSecretName());
                        }

                        if (StringUtils.isEmpty(PRIVATE_KEY)) {
                                throw new IllegalArgumentException(
                                                "Private key read from secrets manager with key secret name : {} is null "
                                                                + mTlsAwsSecrets.mTlsKeySecretName());
                        }
                        LOG.info("FHIRService :: validate cert through openssl -BEGIN for interactionId : {}",
                                        interactionId);
                        validateCertificate(CERTIFICATE, interactionId);
                        LOG.info("FHIRService :: Openssl success - certificate is valid -END for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService :: validate KEY through openssl -BEGIN for interactionId : {}",
                                        interactionId);
                        validatePrivateKey(PRIVATE_KEY, interactionId);
                        LOG.info("FHIRService :: kEY IS Successful through openssl for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash Certificate and Key Details fetched successfully for interactionId : {}",
                                        interactionId);

                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash Creating SSLContext -BEGIN for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash Creating  temp file  tempC -BEGIN for interactionId : {}",
                                        interactionId);
                        Path certFile = Files.createTempFile("tempC", ".pem");
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash Creating  temp file  tempC -END for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash Creating  temp file  tempK -BEGIN for interactionId : {}",
                                        interactionId);
                        Path keyFile = Files.createTempFile("tempK", ".key");
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash Creating  temp file  tempK -END for interactionId : {}",
                                        interactionId);
                        try {
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash Write to  tempc -BEGIN for interactionId : {}",
                                                interactionId);
                                Files.writeString(certFile, CERTIFICATE);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash Write to  tempC -END for interactionId : {}",
                                                interactionId);
                                // MessageDigest digest = MessageDigest.getInstance("SHA-256");
                                // byte[] certHashBytes = digest.digest();
                                // String certFileHash = HexFormat.of().formatHex(certHashBytes);
                                // LOG.info("FHIRService :: Hash for certfile : {} -END for interactionId : {}",
                                // certFileHash, interactionId);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash Write to  tempK -BEGIN for interactionId : {}",
                                                interactionId);
                                Files.writeString(keyFile, PRIVATE_KEY);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash Write to  tempK -END for interactionId : {}",
                                                interactionId);
                                // MessageDigest digestKey = MessageDigest.getInstance("SHA-256");
                                // byte[] keyHashBytes = digestKey.digest();
                                // String keyFileHash = HexFormat.of().formatHex(keyHashBytes);
                                // LOG.info("FHIRService :: Hash for key file : {} -END for interactionId : {}",
                                // keyFileHash, interactionId);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash Create SSL context with tempC and tempK -BEGIN for interactionId : {}",
                                                interactionId);
                                final var sslContext = SslContextBuilder.forClient()
                                                .keyManager(Files.newInputStream(certFile),
                                                                Files.newInputStream(keyFile))
                                                .build();
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash Create SSL context with tempC and tempK -END for interactionId : {}",
                                                interactionId);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash SSLContext created successfully for interactionId : {}",
                                                interactionId);

                                HttpClient httpClient = HttpClient.create()
                                                .secure(ssl -> ssl.sslContext(sslContext));

                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash HttpClient created successfully for interactionId : {}",
                                                interactionId);

                                ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash ReactorClientHttpConnector created successfully for interactionId : {}",
                                                interactionId);

                                LOG.info("FHIRService:: handleAwsSecretsTemporaryFileWithoutHash Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN \n"
                                                + "with scoring Engine API URL: {} \n" +
                                                "dataLakeApiContentType: {} \n" +
                                                "bundlePayloadWithDisposition: {} \n" +
                                                "for interactionID: {} \n" +
                                                "tenant Id: {}",
                                                dataLakeApiBaseURL,
                                                dataLakeApiContentType,
                                                bundlePayloadWithDisposition == null ? "Payload is null"
                                                                : "Payload is not null",
                                                interactionId,
                                                tenantId);

                                var webClient = WebClient.builder()
                                                .baseUrl(dataLakeApiBaseURL)
                                                .defaultHeader("Content-Type", dataLakeApiContentType)
                                                .clientConnector(connector)
                                                .build();

                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash Build WebClient with MTLS Enabled ReactorClientHttpConnector -END for interactionId :{}",
                                                interactionId);
                                LOG.info("FHIRService:: handleAwsSecretsTemporaryFileWithoutHash - sendPostRequest BEGIN for interaction id: {} tenantId :{} ",
                                                interactionId, tenantId);

                                sendPostRequest(webClient, tenantId, bundlePayloadWithDisposition, payload,
                                                dataLakeApiContentType, interactionId,
                                                jooqCfg, provenance, requestURI, dataLakeApiBaseURL);

                                LOG.info("FHIRService:: handleAwsSecretsTemporaryFileWithoutHash -sendPostRequest END for interaction id: {} tenantId :{} ",
                                                interactionId, tenantId);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash Post to scoring engine -END for interactionId :{}",
                                                interactionId);
                        } finally {
                                // Clean up temporary files
                                Files.deleteIfExists(certFile);
                                Files.deleteIfExists(keyFile);
                        }
                } catch (Exception ex) {
                        LOG.error("ERROR:: FHIRService :: handleAwsSecretsTemporaryFileWithoutHash Post to scoring engine FAILED with error :{} for interactionId :{} tenantId:{}",
                                        ex.getMessage(), interactionId, tenantId, ex);
                        registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, ex.getMessage(), provenance);
                }
                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHash -END for interactionId : {}", interactionId);
        }

        private void handleAwsSecretsTemporaryFileWithoutHashAndOpenssl(MTlsAwsSecrets mTlsAwsSecrets,
                        String interactionId, String tenantId,
                        String dataLakeApiBaseURL, String dataLakeApiContentType,
                        Map<String, Object> bundlePayloadWithDisposition,
                        org.jooq.Configuration jooqCfg, String provenance, String requestURI,
                        boolean includeIncomingPayloadInDB, String payload) {
                try {
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl  Proceed with temporary file creation -BEGIN for interactionId : {}",
                                        interactionId);
                        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                                Security.addProvider(new BouncyCastleProvider());
                        }
                        registerStateForward(jooqCfg, provenance, interactionId, requestURI,
                                        tenantId, bundlePayloadWithDisposition, null, includeIncomingPayloadInDB,
                                        payload);

                        if (null == mTlsAwsSecrets || null == mTlsAwsSecrets.mTlsKeySecretName()
                                        || null == mTlsAwsSecrets.mTlsCertSecretName()) {
                                throw new IllegalArgumentException(
                                                "######## Strategy defined is aws-secrets but mTlsKeySecretName and mTlsCertSecretName is not correctly configured. ######### ");
                        }

                        KeyDetails keyDetails = getSecretsFromAWSSecretManager(mTlsAwsSecrets.mTlsKeySecretName(),
                                        mTlsAwsSecrets.mTlsCertSecretName());
                        final String CERTIFICATE = keyDetails.cert();
                        final String PRIVATE_KEY = keyDetails.key();
                        if (StringUtils.isEmpty(CERTIFICATE)) {
                                throw new IllegalArgumentException(
                                                "Certificate read from secrets manager with certificate secret name : {} is null "
                                                                + mTlsAwsSecrets.mTlsCertSecretName());
                        }

                        if (StringUtils.isEmpty(PRIVATE_KEY)) {
                                throw new IllegalArgumentException(
                                                "Private key read from secrets manager with key secret name : {} is null "
                                                                + mTlsAwsSecrets.mTlsKeySecretName());
                        }
                        // LOG.info("FHIRService :: validate cert through openssl -BEGIN for
                        // interactionId : {}",
                        // interactionId);
                        // validateCertificate(CERTIFICATE, interactionId);
                        // LOG.info("FHIRService :: Openssl success - certificate is valid -END for
                        // interactionId : {}",
                        // interactionId);
                        // LOG.info("FHIRService :: validate KEY through openssl -BEGIN for
                        // interactionId : {}",
                        // interactionId);
                        // validatePrivateKey(PRIVATE_KEY, interactionId);
                        // LOG.info("FHIRService :: kEY IS Successful through openssl for interactionId
                        // : {}",
                        // interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Certificate and Key Details fetched successfully for interactionId : {}",
                                        interactionId);

                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Creating SSLContext -BEGIN for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Creating  temp file  tempC -BEGIN for interactionId : {}",
                                        interactionId);
                        Path certFile = Files.createTempFile("tempC", ".pem");
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Creating  temp file  tempC -END for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Creating  temp file  tempK -BEGIN for interactionId : {}",
                                        interactionId);
                        Path keyFile = Files.createTempFile("tempK", ".pem");
                        LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Creating  temp file  tempK -END for interactionId : {}",
                                        interactionId);
                        try {
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Write to  tempc -BEGIN for interactionId : {}",
                                                interactionId);
                                Files.writeString(certFile, CERTIFICATE);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Write to  tempC -END for interactionId : {}",
                                                interactionId);
                                // MessageDigest digest = MessageDigest.getInstance("SHA-256");
                                // byte[] certHashBytes = digest.digest();
                                // String certFileHash = HexFormat.of().formatHex(certHashBytes);
                                // LOG.info("FHIRService :: Hash for certfile : {} -END for interactionId : {}",
                                // certFileHash, interactionId);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Write to  tempK -BEGIN for interactionId : {}",
                                                interactionId);
                                Files.writeString(keyFile, PRIVATE_KEY);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Write to  tempK -END for interactionId : {}",
                                                interactionId);
                                // MessageDigest digestKey = MessageDigest.getInstance("SHA-256");
                                // byte[] keyHashBytes = digestKey.digest();
                                // String keyFileHash = HexFormat.of().formatHex(keyHashBytes);
                                // LOG.info("FHIRService :: Hash for key file : {} -END for interactionId : {}",
                                // keyFileHash, interactionId);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Create SSL context with tempC and tempK -BEGIN for interactionId : {}",
                                                interactionId);
                                final var sslContext = SslContextBuilder.forClient()
                                                .keyManager(Files.newInputStream(certFile),
                                                                Files.newInputStream(keyFile))
                                                .build();
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Create SSL context with tempC and tempK -END for interactionId : {}",
                                                interactionId);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl SSLContext created successfully for interactionId : {}",
                                                interactionId);

                                HttpClient httpClient = HttpClient.create()
                                                .secure(ssl -> ssl.sslContext(sslContext));

                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl HttpClient created successfully for interactionId : {}",
                                                interactionId);

                                ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl ReactorClientHttpConnector created successfully for interactionId : {}",
                                                interactionId);

                                LOG.info("FHIRService:: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN \n"
                                                + "with scoring Engine API URL: {} \n" +
                                                "dataLakeApiContentType: {} \n" +
                                                "bundlePayloadWithDisposition: {} \n" +
                                                "for interactionID: {} \n" +
                                                "tenant Id: {}",
                                                dataLakeApiBaseURL,
                                                dataLakeApiContentType,
                                                bundlePayloadWithDisposition == null ? "Payload is null"
                                                                : "Payload is not null",
                                                interactionId,
                                                tenantId);

                                var webClient = WebClient.builder()
                                                .baseUrl(dataLakeApiBaseURL)
                                                .defaultHeader("Content-Type", dataLakeApiContentType)
                                                .clientConnector(connector)
                                                .build();

                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Build WebClient with MTLS Enabled ReactorClientHttpConnector -END for interactionId :{}",
                                                interactionId);
                                LOG.info("FHIRService:: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl - sendPostRequest BEGIN for interaction id: {} tenantId :{} ",
                                                interactionId, tenantId);

                                sendPostRequest(webClient, tenantId, bundlePayloadWithDisposition, payload,
                                                dataLakeApiContentType, interactionId,
                                                jooqCfg, provenance, requestURI, dataLakeApiBaseURL);

                                LOG.info("FHIRService:: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl -sendPostRequest END for interaction id: {} tenantId :{} ",
                                                interactionId, tenantId);
                                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Post to scoring engine -END for interactionId :{}",
                                                interactionId);
                        } finally {
                                // Clean up temporary files
                                Files.deleteIfExists(certFile);
                                Files.deleteIfExists(keyFile);
                        }
                } catch (Exception ex) {
                        LOG.error("ERROR:: FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl Post to scoring engine FAILED with error :{} for interactionId :{} tenantId:{}",
                                        ex.getMessage(), interactionId, tenantId, ex);
                        registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, ex.getMessage(), provenance);
                }
                LOG.info("FHIRService :: handleAwsSecretsTemporaryFileWithoutHashAndOpenssl -END for interactionId : {}", interactionId);
        }

        public static String hashString(String input) throws NoSuchAlgorithmException {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(input.getBytes());
                return HexFormat.of().formatHex(hashBytes);
        }

        private void handleAwsSecrets(MTlsAwsSecrets mTlsAwsSecrets, String interactionId, String tenantId,
                        String dataLakeApiBaseURL, String dataLakeApiContentType,
                        Map<String, Object> bundlePayloadWithDisposition,
                        org.jooq.Configuration jooqCfg, String provenance, String requestURI,
                        boolean includeIncomingPayloadInDB, String payload) {
                try {
                        LOG.info("FHIRService :: handleAwsSecrets -BEGIN for interactionId : {}",
                                        interactionId);

                        registerStateForward(jooqCfg, provenance, interactionId, requestURI,
                                        tenantId, bundlePayloadWithDisposition, null, includeIncomingPayloadInDB,
                                        payload);
                        if (null == mTlsAwsSecrets || null == mTlsAwsSecrets.mTlsKeySecretName()
                                        || null == mTlsAwsSecrets.mTlsCertSecretName()) {
                                throw new IllegalArgumentException(
                                                "######## Strategy defined is aws-secrets but mTlsKeySecretName and mTlsCertSecretName is not correctly configured. ######### ");
                        }
                        KeyDetails keyDetails = getSecretsFromAWSSecretManager(mTlsAwsSecrets.mTlsKeySecretName(),
                                        mTlsAwsSecrets.mTlsCertSecretName());
                        final String CERTIFICATE = keyDetails.cert();
                        final String PRIVATE_KEY = keyDetails.key();

                        if (StringUtils.isEmpty(CERTIFICATE)) {
                                throw new IllegalArgumentException(
                                                "Certifcate read from secrets manager with certficate secret name : {} is null "
                                                                + mTlsAwsSecrets.mTlsCertSecretName());
                        }

                        if (StringUtils.isEmpty(PRIVATE_KEY)) {
                                throw new IllegalArgumentException(
                                                "Private key read from secrets manager with key secret name : {} is null "
                                                                + mTlsAwsSecrets.mTlsKeySecretName());
                        }
                        LOG.info("FHIRService :: validate cert through openssl -BEGIN for interactionId : {}",
                                        interactionId);
                        validateCertificate(CERTIFICATE, interactionId);
                        LOG.info("FHIRService :: Openssl success - certificate is valid -END for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService ::Hash value for cert : {} -END for interactionId : {}",
                                        hashString(CERTIFICATE), interactionId);

                        LOG.info("FHIRService :: validate KEY through openssl -BEGIN for interactionId : {}",
                                        interactionId);
                        validatePrivateKey(PRIVATE_KEY, interactionId);
                        LOG.info("FHIRService :: kEY IS Successful through openssl for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService ::Hash value for key : {} -END for interactionId : {}",
                                        hashString(PRIVATE_KEY), interactionId);
                        LOG.info("FHIRService :: handleAwsSecrets -BEGIN for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService :: handleAwsSecrets Certificate and Key Details fetched successfully for interactionId : {}",
                                        interactionId);

                        LOG.info("FHIRService :: handleAwsSecrets Creating SSLContext  -BEGIN for interactionId : {}",
                                        interactionId);

                        final var sslContext = SslContextBuilder.forClient()
                                        .keyManager(new ByteArrayInputStream(CERTIFICATE.getBytes()),
                                                        new ByteArrayInputStream(PRIVATE_KEY.getBytes()))
                                        .build();
                        LOG.info("FHIRService :: handleAwsSecrets Creating SSLContext  - END for interactionId : {}",
                                        interactionId);

                        HttpClient httpClient = HttpClient.create()
                                        .secure(sslSpec -> sslSpec.sslContext(sslContext));
                        LOG.info("FHIRService :: handleAwsSecrets HttpClient created successfully  for interactionId : {}",
                                        interactionId);

                        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
                        LOG.info("FHIRService :: handleAwsSecrets ReactorClientHttpConnector created successfully  for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService:: handleAwsSecrets Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN \n"
                                        +
                                        "with scoring Engine API URL: {} \n" +
                                        "dataLakeApiContentType: {} \n" +
                                        "bundlePayloadWithDisposition: {} \n" +
                                        "for interactionID: {} \n" +
                                        "tenant Id: {}",
                                        dataLakeApiBaseURL,
                                        dataLakeApiContentType,
                                        bundlePayloadWithDisposition == null ? "Payload is null"
                                                        : "Payload is not null",
                                        interactionId,
                                        tenantId);
                        var webClient = WebClient.builder()
                                        .baseUrl(dataLakeApiBaseURL)
                                        .defaultHeader("Content-Type", dataLakeApiContentType)
                                        .clientConnector(connector)
                                        .build();
                        LOG.info("FHIRService :: handleAwsSecrets  Build WebClient with MTLS Enabled ReactorClientHttpConnector -END for interactionId :{}",
                                        interactionId);
                        LOG.info("FHIRService:: handleAwsSecrets - sendPostRequest BEGIN for interaction id: {} tenantid :{} ",
                                        interactionId,
                                        tenantId);
                        sendPostRequest(webClient, tenantId, bundlePayloadWithDisposition, payload,
                                        dataLakeApiContentType, interactionId,
                                        jooqCfg, provenance, requestURI, dataLakeApiBaseURL);
                        LOG.info("FHIRService:: handleAwsSecrets -sendPostRequest END for interaction id: {} tenantid :{} ",
                                        interactionId,
                                        tenantId);
                        LOG.info("FHIRService :: handleAwsSecrets Post to scoring engine -END for interactionId :{}",
                                        interactionId);
                } catch (Exception ex) {
                        LOG.error("ERROR:: FHIRService :: handleAwsSecrets Post to scoring engine FAILED with error :{} for interactionId :{} tenantId:{}",
                                        ex.getMessage(),
                                        interactionId, tenantId, ex);
                        registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, ex.getMessage(),
                                        provenance);
                }
                LOG.info("FHIRService :: handleAwsSecrets -END for interactionId : {}",
                                interactionId);
        }

        private void handleAwsSecretsWithoutHash(MTlsAwsSecrets mTlsAwsSecrets, String interactionId, String tenantId,
                        String dataLakeApiBaseURL, String dataLakeApiContentType,
                        Map<String, Object> bundlePayloadWithDisposition,
                        org.jooq.Configuration jooqCfg, String provenance, String requestURI,
                        boolean includeIncomingPayloadInDB, String payload) {
                try {
                        LOG.info("FHIRService :: handleAwsSecretsWithoutHash -BEGIN for interactionId : {}",
                                        interactionId);

                        registerStateForward(jooqCfg, provenance, interactionId, requestURI,
                                        tenantId, bundlePayloadWithDisposition, null, includeIncomingPayloadInDB,
                                        payload);
                        if (null == mTlsAwsSecrets || null == mTlsAwsSecrets.mTlsKeySecretName()
                                        || null == mTlsAwsSecrets.mTlsCertSecretName()) {
                                throw new IllegalArgumentException(
                                                "######## Strategy defined is aws-secrets but mTlsKeySecretName and mTlsCertSecretName is not correctly configured. ######### ");
                        }
                        KeyDetails keyDetails = getSecretsFromAWSSecretManager(mTlsAwsSecrets.mTlsKeySecretName(),
                                        mTlsAwsSecrets.mTlsCertSecretName());
                        final String CERTIFICATE = keyDetails.cert();
                        final String PRIVATE_KEY = keyDetails.key();

                        if (StringUtils.isEmpty(CERTIFICATE)) {

                                throw new IllegalArgumentException(
                                                "Certifcate read from secrets manager with certficate secret name : {} is null "
                                                                + mTlsAwsSecrets.mTlsCertSecretName());
                        }

                        if (StringUtils.isEmpty(PRIVATE_KEY)) {

                                throw new IllegalArgumentException(
                                                "Private key read from secrets manager with key secret name : {} is null "
                                                                + mTlsAwsSecrets.mTlsKeySecretName());
                        }
                        LOG.info("FHIRService :: validate cert through openssl -BEGIN for interactionId : {}",
                                        interactionId);
                        validateCertificate(CERTIFICATE, interactionId);
                        LOG.info("FHIRService :: Openssl success - certificate is valid -END for interactionId : {}",
                                        interactionId);
                        // LOG.info("FHIRService ::Hash value for cert : {} -END for interactionId :
                        // {}",
                        // hashString(CERTIFICATE), interactionId);

                        LOG.info("FHIRService :: validate KEY through openssl -BEGIN for interactionId : {}",
                                        interactionId);
                        validatePrivateKey(PRIVATE_KEY, interactionId);
                        LOG.info("FHIRService :: kEY IS Successful through openssl for interactionId : {}",
                                        interactionId);
                        // LOG.info("FHIRService ::Hash value for key : {} -END for interactionId : {}",
                        // hashString(PRIVATE_KEY), interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsWithoutHash -BEGIN for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsWithoutHash Certificate and Key Details fetched successfully for interactionId : {}",
                                        interactionId);

                        LOG.info("FHIRService :: handleAwsSecretsWithoutHash Creating SSLContext  -BEGIN for interactionId : {}",
                                        interactionId);

                        final var sslContext = SslContextBuilder.forClient()
                                        .keyManager(new ByteArrayInputStream(CERTIFICATE.getBytes()),
                                                        new ByteArrayInputStream(PRIVATE_KEY.getBytes()))
                                        .build();
                        LOG.info("FHIRService :: handleAwsSecretsWithoutHash Creating SSLContext  - END for interactionId : {}",
                                        interactionId);

                        HttpClient httpClient = HttpClient.create()
                                        .secure(sslSpec -> sslSpec.sslContext(sslContext));
                        LOG.info("FHIRService :: handleAwsSecretsWithoutHash HttpClient created successfully  for interactionId : {}",
                                        interactionId);

                        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
                        LOG.info("FHIRService :: handleAwsSecretsWithoutHash ReactorClientHttpConnector created successfully  for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService:: handleAwsSecretsWithoutHash Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN \n"
                                        +
                                        "with scoring Engine API URL: {} \n" +
                                        "dataLakeApiContentType: {} \n" +
                                        "bundlePayloadWithDisposition: {} \n" +
                                        "for interactionID: {} \n" +
                                        "tenant Id: {}",
                                        dataLakeApiBaseURL,
                                        dataLakeApiContentType,
                                        bundlePayloadWithDisposition == null ? "Payload is null"
                                                        : "Payload is not null",
                                        interactionId,
                                        tenantId);
                        var webClient = WebClient.builder()
                                        .baseUrl(dataLakeApiBaseURL)
                                        .defaultHeader("Content-Type", dataLakeApiContentType)
                                        .clientConnector(connector)
                                        .build();
                        LOG.info("FHIRService :: handleAwsSecretsWithoutHash  Build WebClient with MTLS Enabled ReactorClientHttpConnector -END for interactionId :{}",
                                        interactionId);
                        LOG.info("FHIRService:: handleAwsSecretsWithoutHash - sendPostRequest BEGIN for interaction id: {} tenantid :{} ",
                                        interactionId,
                                        tenantId);
                        sendPostRequest(webClient, tenantId, bundlePayloadWithDisposition, payload,
                                        dataLakeApiContentType, interactionId,
                                        jooqCfg, provenance, requestURI, dataLakeApiBaseURL);
                        LOG.info("FHIRService:: handleAwsSecretsWithoutHash -sendPostRequest END for interaction id: {} tenantid :{} ",
                                        interactionId,
                                        tenantId);
                        LOG.info("FHIRService :: handleAwsSecretsWithoutHash Post to scoring engine -END for interactionId :{}",
                                        interactionId);
                } catch (Exception ex) {
                        LOG.error("ERROR:: FHIRService :: handleAwsSecretsWithoutHash Post to scoring engine FAILED with error :{} for interactionId :{} tenantId:{}",
                                        ex.getMessage(),
                                        interactionId, tenantId, ex);
                        registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, ex.getMessage(),
                                        provenance);
                }
                LOG.info("FHIRService :: handleAwsSecretsWithoutHash -END for interactionId : {}",
                                interactionId);
        }

        private void handleAwsSecretsWithoutOpenssl(MTlsAwsSecrets mTlsAwsSecrets, String interactionId,
                        String tenantId,
                        String dataLakeApiBaseURL, String dataLakeApiContentType,
                        Map<String, Object> bundlePayloadWithDisposition,
                        org.jooq.Configuration jooqCfg, String provenance, String requestURI,
                        boolean includeIncomingPayloadInDB, String payload) {
                try {
                        LOG.info("FHIRService :: handleAwsSecretsWithoutOpenssl -BEGIN for interactionId : {}",
                                        interactionId);

                        registerStateForward(jooqCfg, provenance, interactionId, requestURI,
                                        tenantId, bundlePayloadWithDisposition, null, includeIncomingPayloadInDB,
                                        payload);
                        if (null == mTlsAwsSecrets || null == mTlsAwsSecrets.mTlsKeySecretName()
                                        || null == mTlsAwsSecrets.mTlsCertSecretName()) {
                                throw new IllegalArgumentException(
                                                "######## Strategy defined is aws-secrets but mTlsKeySecretName and mTlsCertSecretName is not correctly configured. ######### ");
                        }
                        KeyDetails keyDetails = getSecretsFromAWSSecretManager(mTlsAwsSecrets.mTlsKeySecretName(),
                                        mTlsAwsSecrets.mTlsCertSecretName());
                        final String CERTIFICATE = keyDetails.cert();
                        final String PRIVATE_KEY = keyDetails.key();

                        if (StringUtils.isEmpty(CERTIFICATE)) {

                                throw new IllegalArgumentException(
                                                "Certifcate read from secrets manager with certficate secret name : {} is null "
                                                                + mTlsAwsSecrets.mTlsCertSecretName());
                        }

                        if (StringUtils.isEmpty(PRIVATE_KEY)) {

                                throw new IllegalArgumentException(
                                                "Private key read from secrets manager with key secret name : {} is null "
                                                                + mTlsAwsSecrets.mTlsKeySecretName());
                        }
                        // LOG.info("FHIRService :: validate cert through openssl -BEGIN for
                        // interactionId : {}",
                        // interactionId);
                        // validateCertificate(CERTIFICATE, interactionId);
                        // LOG.info("FHIRService :: Openssl success - certificate is valid -END for
                        // interactionId : {}",
                        // interactionId);
                        LOG.info("FHIRService ::Hash value for cert : {} -END for interactionId : {}",
                                        hashString(CERTIFICATE), interactionId);

                        // LOG.info("FHIRService :: validate KEY through openssl -BEGIN for
                        // interactionId : {}",
                        // interactionId);
                        // validatePrivateKey(PRIVATE_KEY, interactionId);
                        // LOG.info("FHIRService :: kEY IS Successful through openssl for interactionId
                        // : {}",
                        // interactionId);
                        LOG.info("FHIRService ::Hash value for key : {} -END for interactionId : {}",
                                        hashString(PRIVATE_KEY), interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsWithoutOpenssl -BEGIN for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService :: handleAwsSecretsWithoutOpenssl Certificate and Key Details fetched successfully for interactionId : {}",
                                        interactionId);

                        LOG.info("FHIRService :: handleAwsSecretsWithoutOpenssl Creating SSLContext  -BEGIN for interactionId : {}",
                                        interactionId);

                        final var sslContext = SslContextBuilder.forClient()
                                        .keyManager(new ByteArrayInputStream(CERTIFICATE.getBytes()),
                                                        new ByteArrayInputStream(PRIVATE_KEY.getBytes()))
                                        .build();
                        LOG.info("FHIRService :: handleAwsSecretsWithoutOpenssl Creating SSLContext  - END for interactionId : {}",
                                        interactionId);

                        HttpClient httpClient = HttpClient.create()
                                        .secure(sslSpec -> sslSpec.sslContext(sslContext));
                        LOG.info("FHIRService :: handleAwsSecretsWithoutOpenssl HttpClient created successfully  for interactionId : {}",
                                        interactionId);

                        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
                        LOG.info("FHIRService :: handleAwsSecretsWithoutOpenssl ReactorClientHttpConnector created successfully  for interactionId : {}",
                                        interactionId);
                        LOG.info("FHIRService:: handleAwsSecretsWithoutOpenssl Build WebClient with MTLS Enabled ReactorClientHttpConnector -BEGIN \n"
                                        +
                                        "with scoring Engine API URL: {} \n" +
                                        "dataLakeApiContentType: {} \n" +
                                        "bundlePayloadWithDisposition: {} \n" +
                                        "for interactionID: {} \n" +
                                        "tenant Id: {}",
                                        dataLakeApiBaseURL,
                                        dataLakeApiContentType,
                                        bundlePayloadWithDisposition == null ? "Payload is null"
                                                        : "Payload is not null",
                                        interactionId,
                                        tenantId);
                        var webClient = WebClient.builder()
                                        .baseUrl(dataLakeApiBaseURL)
                                        .defaultHeader("Content-Type", dataLakeApiContentType)
                                        .clientConnector(connector)
                                        .build();
                        LOG.info("FHIRService :: handleAwsSecretsWithoutOpenssl  Build WebClient with MTLS Enabled ReactorClientHttpConnector -END for interactionId :{}",
                                        interactionId);
                        LOG.info("FHIRService:: handleAwsSecretsWithoutOpenssl - sendPostRequest BEGIN for interaction id: {} tenantid :{} ",
                                        interactionId,
                                        tenantId);
                        sendPostRequest(webClient, tenantId, bundlePayloadWithDisposition, payload,
                                        dataLakeApiContentType, interactionId,
                                        jooqCfg, provenance, requestURI, dataLakeApiBaseURL);
                        LOG.info("FHIRService:: handleAwsSecretsWithoutOpenssl -sendPostRequest END for interaction id: {} tenantid :{} ",
                                        interactionId,
                                        tenantId);
                        LOG.info("FHIRService :: handleAwsSecretsWithoutOpenssl Post to scoring engine -END for interactionId :{}",
                                        interactionId);
                } catch (Exception ex) {
                        LOG.error("ERROR:: FHIRService :: handleAwsSecretsWithoutOpenssl Post to scoring engine FAILED with error :{} for interactionId :{} tenantId:{}",
                                        ex.getMessage(),
                                        interactionId, tenantId, ex);
                        registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, ex.getMessage(),
                                        provenance);
                }
                LOG.info("FHIRService :: handleAwsSecretsWithoutOpenssl -END for interactionId : {}",
                                interactionId);
        }

        private void handlePostStdoutPayload(String interactionId, String tenantId, org.jooq.Configuration jooqCfg,
                        String dataLakeApiBaseURL,
                        Map<String, Object> bundlePayloadWithDisposition,
                        boolean includeIncomingPayloadInDB, String payload, String provenance,
                        HttpServletRequest request,
                        PostStdinPayloadToNyecDataLakeExternal postStdinPayloadToNyecDataLakeExternal) {
                LOG.info("Proceed with posting payload via external process BEGIN forinteractionId : {}",
                                interactionId);
                try {
                        registerStateForward(jooqCfg, provenance, getBundleInteractionId(request),
                                        request.getRequestURI(), tenantId,
                                        Optional.ofNullable(bundlePayloadWithDisposition)
                                                        .orElse(new HashMap<>()),
                                        null, includeIncomingPayloadInDB, payload);
                        var postToNyecExternalResponse = postStdinPayloadToNyecDataLakeExternal(dataLakeApiBaseURL,
                                        tenantId, interactionId,
                                        bundlePayloadWithDisposition,
                                        postStdinPayloadToNyecDataLakeExternal);

                        LOG.info("Create payload from postToNyecExternalResponse- BEGIN for interactionId : {}",
                                        interactionId);
                        var payloadJson = Map.of("completed",
                                        postToNyecExternalResponse.completed(),
                                        "processOutput", postToNyecExternalResponse.processOutput(),
                                        "errorOutput", postToNyecExternalResponse.errorOutput());
                        String responsePayload = Configuration.objectMapper
                                        .writeValueAsString(payloadJson);
                        LOG.info("Create payload from postToNyecExternalResponse- END forinteractionId : {}",
                                        interactionId);
                        if (postToNyecExternalResponse.completed() && null != postToNyecExternalResponse.processOutput()
                                        && postToNyecExternalResponse.processOutput()
                                                        .contains("{\"status\": \"Success\"")) {
                                registerStateComplete(jooqCfg, interactionId,
                                                request.getRequestURI(), tenantId, responsePayload,
                                                provenance);
                        } else {
                                registerStateFailed(jooqCfg, interactionId,
                                                request.getRequestURI(), tenantId, responsePayload,
                                                provenance);
                        }
                } catch (Exception ex) {
                        LOG.error("Exception while postStdinPayloadToNyecDataLakeExternal forinteractionId : {}",
                                        interactionId, ex);
                        registerStateFailed(jooqCfg, interactionId,
                                        request.getRequestURI(), tenantId, ex.getMessage(), provenance);
                }
                LOG.info("Proceed with posting payload via external process END for interactionId : {}",
                                interactionId);
        }

        private PostToNyecExternalResponse postStdinPayloadToNyecDataLakeExternal(String dataLakeApiBaseURL,
                        String tenantId,
                        String interactionId,
                        Map<String, Object> bundlePayloadWithDisposition,
                        PostStdinPayloadToNyecDataLakeExternal postStdinPayloadToNyecDataLakeExternal)
                        throws Exception {
                boolean completed = false;
                String processOutput = "";
                String errorOutput = "";
                LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal BEGIN for interaction id : {} tenantID :{}",
                                interactionId, tenantId);
                final var bashScriptPath = postStdinPayloadToNyecDataLakeExternal.cmd();
                if (null == bashScriptPath) {
                        throw new IllegalArgumentException(
                                        "Bash Script path not configured for the environment.Configure this in application.yml.");
                }
                LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal Fetched Bash Script Path :{} for interaction id : {} tenantID :{}",
                                bashScriptPath, interactionId, tenantId);
                LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal Prepare ProcessBuilder to run the bash script for interaction id : {} tenantID :{}",
                                interactionId, tenantId);
                final var processBuilder = new ProcessBuilder(bashScriptPath, tenantId, dataLakeApiBaseURL)
                                .redirectErrorStream(true);
                LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal Start the process  for interaction id : {} tenantID :{}",
                                interactionId, tenantId);
                final var process = processBuilder.start();
                LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal DEBUG: Capture any output from stdout or stderr immediately after starting\r\n"
                                + //
                                "        // the process  for interaction id : {} tenantID :{}",
                                interactionId, tenantId);
                try (var errorStream = process.getErrorStream();
                                var inputStream = process.getInputStream()) {

                        // DEBUG: Print any errors encountered
                        errorOutput = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                        if (!errorOutput.isBlank()) {
                                LOG.error("FHIRService :: postStdinPayloadToNyecDataLakeExternal Error Stream Output before sending payload:{}  for interaction id : {} tenantID :{}",
                                                errorOutput, interactionId, tenantId);
                                throw new UnexpectedException(
                                                "Error Stream Output before sending payload:\n" + errorOutput);
                        }
                        LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal Convert Payload map to String BEGIN for interaction id : {} tenantID :{}",
                                        interactionId, tenantId);

                        LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal Pass the payload map to String END for interaction id : {} tenantID :{}",
                                        interactionId, tenantId);
                        String payload = Configuration.objectMapper.writeValueAsString(bundlePayloadWithDisposition);
                        LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal Pass the payload via STDIN -BEGIN for interaction id : {} tenantID :{}",
                                        interactionId, tenantId);
                        try (OutputStream outputStream = process.getOutputStream()) {
                                outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
                                outputStream.flush();
                                LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal Payload send successfully to STDIN -END for interaction id : {} tenantID :{}",
                                                interactionId, tenantId);
                        } catch (IOException e) {
                                LOG.error("FHIRService :: postStdinPayloadToNyecDataLakeExternal Failed to write payload to process STDIN due to error :{}  begin for interaction id : {} tenantID :{}",
                                                e.getMessage(), interactionId, tenantId);
                                throw e;
                        }
                        final int timeout = Integer.valueOf(postStdinPayloadToNyecDataLakeExternal.timeout());
                        LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal Wait for the process to complete with a timeout :{}  begin for interaction id : {} tenantID :{}",
                                        timeout, interactionId, tenantId);
                        completed = process.waitFor(timeout, TimeUnit.SECONDS);
                        LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal Wait elapsed ...Fetch response  begin for interaction id : {} tenantID :{}",
                                        interactionId, tenantId);

                        processOutput = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                        LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal Standard Output received:{} for interaction id : {} tenantID :{}",
                                        processOutput, interactionId, tenantId);
                        errorOutput = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                        if (!errorOutput.isBlank()) {
                                LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal Error Stream Output after execution: {} for interaction id : {} tenantID :{}",
                                                errorOutput, interactionId, tenantId);
                        }
                }

                LOG.info("FHIRService :: postStdinPayloadToNyecDataLakeExternal END for interaction id : {} tenantID :{}",
                                interactionId, tenantId);
                return new PostToNyecExternalResponse(completed, processOutput, errorOutput);
        }

        private WebClient createWebClient(String scoringEngineApiURL,
                        org.jooq.Configuration jooqCfg,
                        HttpServletRequest request,
                        String tenantId,
                        String payload,
                        Map<String, Object> bundlePayloadWithDisposition,
                        String provenance,
                        boolean includeIncomingPayloadInDB, String interactionId) {
                return WebClient.builder()
                                .baseUrl(scoringEngineApiURL)
                                .filter(ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
                                        filter(clientRequest, request, jooqCfg, provenance, tenantId, payload,
                                                        bundlePayloadWithDisposition,
                                                        includeIncomingPayloadInDB);
                                        return Mono.just(clientRequest);
                                }))
                                .build();
        }

        private KeyDetails getSecretsFromAWSSecretManager(String keyName, String certName) {
                Region region = Region.US_EAST_1;
                LOG.warn(
                                "FHIRService:: getSecretsFromAWSSecretManager  - Get Secrets Client Manager for region : {} BEGIN for interaction id: {}",
                                Region.US_EAST_1);
                SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                                .region(region)
                                .build();
                KeyDetails keyDetails = new KeyDetails(getValue(secretsClient, keyName),
                                getValue(secretsClient, certName));
                secretsClient.close();
                LOG.warn(
                                "FHIRService:: getSecretsFromAWSSecretManager  - Get Secrets Client Manager for region : {} END for interaction id: {}",
                                Region.US_EAST_1);
                return keyDetails;
        }

        public static String getValue(SecretsManagerClient secretsClient, String secretName) {
                LOG.info("FHIRService:: getValue  - Get Value of secret with name  : {} -BEGIN", secretName);
                String secret = null;
                try {
                        GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
                                        .secretId(secretName)
                                        .build();

                        GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
                        secret = valueResponse.secretString();
                        LOG.info("FHIRService:: getValue  - Fetched value of secret with name  : {}  value  is null : {} -END",
                                        secretName, secret == null ? "true" : "false");
                } catch (SecretsManagerException e) {
                        LOG.error("ERROR:: FHIRService:: getValue  - Get Value of secret with name  : {} - FAILED with error "
                                        + e.awsErrorDetails().errorMessage(), e);
                } catch (Exception e) {
                        LOG.error("ERROR:: FHIRService:: getValue  - Get Value of secret with name  : {} - FAILED with error ",
                                        e);
                }
                LOG.info("FHIRService:: getValue  - Get Value of secret with name  : {} -END", secretName);
                return secret;
        }

        private void filter(ClientRequest clientRequest,
                        HttpServletRequest request,
                        org.jooq.Configuration jooqCfg,
                        String provenance,
                        String tenantId,
                        String payload,
                        Map<String, Object> bundlePayloadWithDisposition,
                        boolean includeIncomingPayloadInDB) {

                final var interactionId = getBundleInteractionId(request);
                LOG.info("FHIRService:: sendToScoringEngine Filter request before post - BEGIN interaction id: {}",
                                interactionId);
                final var requestURI = request.getRequestURI();
                StringBuilder requestBuilder = new StringBuilder()
                                .append(clientRequest.method().name()).append(" ")
                                .append(clientRequest.url()).append(" HTTP/1.1").append("\n");

                clientRequest.headers().forEach((name, values) -> values
                                .forEach(value -> requestBuilder.append(name).append(": ").append(value).append("\n")));

                final var outboundHttpMessage = requestBuilder.toString();

                registerStateForward(jooqCfg, provenance, getBundleInteractionId(request), requestURI, tenantId,
                                Optional.ofNullable(bundlePayloadWithDisposition).orElse(new HashMap<>()),
                                outboundHttpMessage, includeIncomingPayloadInDB, payload);

                LOG.info("FHIRService:: sendToScoringEngine Filter request before post - END interaction id: {}",
                                interactionId);
        }

        private void sendPostRequest(WebClient webClient,
                        String tenantId,
                        Map<String, Object> bundlePayloadWithDisposition,
                        String payload,
                        String dataLakeApiContentType,
                        String interactionId,
                        org.jooq.Configuration jooqCfg,
                        String provenance,
                        String requestURI, String scoringEngineApiURL) {

                LOG.info("FHIRService:: sendToScoringEngine Post to scoring engine - BEGIN interaction id: {} tenantID :{}",
                                interactionId, tenantId);

                webClient.post()
                                .uri("?processingAgent=" + tenantId)
                                .body(BodyInserters.fromValue(null != bundlePayloadWithDisposition
                                                ? bundlePayloadWithDisposition
                                                : payload))
                                .header("Content-Type", Optional.ofNullable(dataLakeApiContentType)
                                                .orElse(AppConfig.Servlet.FHIR_CONTENT_TYPE_HEADER_VALUE))
                                .retrieve()
                                .bodyToMono(String.class)
                                .subscribe(response -> {
                                        handleResponse(response, jooqCfg, interactionId, requestURI, tenantId,
                                                        provenance, scoringEngineApiURL);
                                }, error -> {
                                        registerStateFailure(jooqCfg, scoringEngineApiURL, interactionId, error,
                                                        requestURI, tenantId, provenance);
                                });

                LOG.info("FHIRService:: sendToScoringEngine Post to scoring engine - END interaction id: {} tenantid: {}",
                                interactionId, tenantId);
        }

        private void handleResponse(String response,
                        org.jooq.Configuration jooqCfg,
                        String interactionId,
                        String requestURI,
                        String tenantId,
                        String provenance,
                        String scoringEngineApiURL) {
                LOG.info("FHIRService:: handleResponse BEGIN for interaction id: {}", interactionId);

                try {
                        LOG.info("FHIRService:: handleResponse Response received for :{} interaction id: {}",
                                        interactionId, response);
                        final var responseMap = new ObjectMapper().readValue(response,
                                        new TypeReference<Map<String, String>>() {
                                        });

                        if ("Success".equalsIgnoreCase(responseMap.get("status"))) {
                                LOG.info("FHIRService:: handleResponse SUCCESS for interaction id: {}", interactionId);
                                registerStateComplete(jooqCfg, interactionId, requestURI, tenantId, response,
                                                provenance);
                        } else {
                                LOG.warn("FHIRService:: handleResponse FAILURE for interaction id: {}",
                                                interactionId);
                                registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, response,
                                                provenance);
                        }
                } catch (Exception e) {
                        LOG.error("FHIRService:: handleResponse unexpected error for interaction id : {}, response: {}",
                                        interactionId, response, e);
                        registerStateFailed(jooqCfg, interactionId, requestURI, tenantId, e.getMessage(),
                                        provenance);
                }
                LOG.info("FHIRService:: handleResponse END for interaction id: {}", interactionId);
        }

        private void handleError(Map<String, Object> validationPayloadWithDisposition,
                        Exception e,
                        HttpServletRequest request) {

                validationPayloadWithDisposition.put("exception", e.toString());
                LOG.error("ERROR:: FHIRService:: sendToScoringEngine Exception while sending to scoring engine payload with interaction id: {}",
                                getBundleInteractionId(request), e);
        }

        private Map<String, Object> preparePayload(HttpServletRequest request, String bundlePayload,
                        Map<String, Object> payloadWithDisposition) {
                final var interactionId = getBundleInteractionId(request);
                LOG.info("FHIRService:: addValidationResultToPayload BEGIN for interaction id : {}", interactionId);

                Map<String, Object> resultMap = null;

                try {
                        Map<String, Object> extractedOutcome = Optional
                                        .ofNullable(extractIssueAndDisposition(interactionId, payloadWithDisposition))
                                        .filter(outcome -> !outcome.isEmpty())
                                        .orElseGet(() -> {
                                                LOG.warn("FHIRService:: resource type operation outcome or issues or techByDisposition is missing or empty for interaction id : {}",
                                                                interactionId);
                                                return null;
                                        });

                        if (extractedOutcome == null) {
                                LOG.warn("FHIRService:: extractedOutcome is null for interaction id : {}",
                                                interactionId);
                                return payloadWithDisposition;
                        }
                        Map<String, Object> bundleMap = Optional
                                        .ofNullable(Configuration.objectMapper.readValue(bundlePayload,
                                                        new TypeReference<Map<String, Object>>() {
                                                        }))
                                        .filter(map -> !map.isEmpty())
                                        .orElseGet(() -> {
                                                LOG.warn("FHIRService:: bundleMap is missing or empty after parsing bundlePayload for interaction id : {}",
                                                                interactionId);
                                                return null;
                                        });

                        if (bundleMap == null) {
                                LOG.warn("FHIRService:: bundleMap is null for interaction id : {}", interactionId);
                                return payloadWithDisposition;
                        }
                        resultMap = appendToBundlePayload(interactionId, bundleMap, extractedOutcome);
                        LOG.info("FHIRService:: addValidationResultToPayload END - Validation results added to Bundle payload for interaction id : {}",
                                        interactionId);
                        return resultMap;
                } catch (Exception ex) {
                        LOG.error("ERROR :: FHIRService:: addValidationResultToPayload encountered an exception for interaction id : {}",
                                        interactionId, ex);
                }
                LOG.info("FHIRService:: addValidationResultToPayload END - Validation results not added - returning original payload for interaction id : {}",
                                interactionId);
                return payloadWithDisposition;
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> extractIssueAndDisposition(String interactionId,
                        Map<String, Object> operationOutcomePayload) {
                LOG.info("FHIRService:: extractResourceTypeAndDisposition BEGIN for interaction id : {}",
                                interactionId);

                if (operationOutcomePayload == null) {
                        LOG.warn("FHIRService:: operationOutcomePayload is null for interaction id : {}",
                                        interactionId);
                        return null;
                }

                return Optional.ofNullable(operationOutcomePayload.get("OperationOutcome"))
                                .filter(Map.class::isInstance)
                                .map(Map.class::cast)
                                .flatMap(operationOutcomeMap -> {
                                        List<?> validationResults = (List<?>) operationOutcomeMap
                                                        .get("validationResults");
                                        if (validationResults == null || validationResults.isEmpty()) {
                                                return Optional.empty();
                                        }
                                        Map<String, Object> validationResult = (Map<String, Object>) validationResults
                                                        .get(0);

                                        // Extract resourceType, issue and techByDesignDisposition
                                        Map<String, Object> result = new HashMap<>();
                                        result.put("resourceType", operationOutcomeMap.get("resourceType"));
                                        result.put("issue", validationResult.get("issues"));

                                        List<?> techByDesignDisposition = (List<?>) operationOutcomeMap
                                                        .get("techByDesignDisposition");
                                        if (techByDesignDisposition != null && !techByDesignDisposition.isEmpty()) {
                                                result.put("techByDesignDisposition", techByDesignDisposition);
                                        }

                                        return Optional.of(result);
                                })
                                .orElseGet(() -> {
                                        LOG.warn("FHIRService:: Missing required fields in operationOutcome for interaction id : {}",
                                                        interactionId);
                                        return null;
                                });
        }

        private Map<String, Object> appendToBundlePayload(String interactionId, Map<String, Object> payload,
                        Map<String, Object> extractedOutcome) {
                LOG.info("FHIRService:: appendToBundlePayload BEGIN for interaction id : {}", interactionId);
                if (payload == null) {
                        LOG.warn("FHIRService:: payload is null for interaction id : {}", interactionId);
                        return payload;
                }

                if (extractedOutcome == null || extractedOutcome.isEmpty()) {
                        LOG.warn("FHIRService:: extractedOutcome is null or empty for interaction id : {}",
                                        interactionId);
                        return payload; // Return the original payload if no new outcome to append
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entries = Optional.ofNullable(payload.get("entry"))
                                .filter(List.class::isInstance)
                                .map(entry -> (List<Map<String, Object>>) entry)
                                .orElseGet(() -> {
                                        LOG.warn("FHIRService:: 'entry' field is missing or invalid for interaction id : {}",
                                                        interactionId);
                                        return new ArrayList<>(); // if no entries in payload create new
                                });

                final Map<String, Object> newEntry = Map.of("resource", extractedOutcome);
                entries.add(newEntry);

                final Map<String, Object> finalPayload = new HashMap<>(payload);
                finalPayload.put("entry", List.copyOf(entries));
                LOG.info("FHIRService:: appendToBundlePayload END for interaction id : {}", interactionId);
                return finalPayload;
        }

        private void registerStateForward(org.jooq.Configuration jooqCfg, String provenance,
                        String bundleAsyncInteractionId, String requestURI,
                        String tenantId,
                        Map<String, Object> payloadWithDisposition,
                        String outboundHttpMessage, boolean includeIncomingPayloadInDB, String payload) {
                LOG.info("REGISTER State Forward : BEGIN for inteaction id  : {} tenant id : {}",
                                bundleAsyncInteractionId, tenantId);
                final var forwardedAt = OffsetDateTime.now();
                final var initRIHR = new RegisterInteractionHttpRequest();
                try {
                        // TODO -check the need of includeIncomingPayloadInDB and add this later if
                        // needed
                        // payloadWithDisposition.put("outboundHttpMessage", outboundHttpMessage);
                        // + "\n" + (includeIncomingPayloadInDB
                        // ? payload
                        // : "The incoming FHIR payload was not stored (to save space).\nThis is not an
                        // error or warning just an FYI - if you'd like to see the incoming FHIR payload
                        // `?include-incoming-payload-in-db=true` to request payload storage for each
                        // request that you'd like to store."));
                        initRIHR.setInteractionId(bundleAsyncInteractionId);
                        initRIHR.setInteractionKey(requestURI);
                        initRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                                        Map.of("nature", "Forward HTTP Request", "tenant_id",
                                                        tenantId)));
                        initRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                        initRIHR.setPayload((JsonNode) Configuration.objectMapper
                                        .valueToTree(payloadWithDisposition));
                        initRIHR.setFromState("DISPOSITION");
                        initRIHR.setToState("FORWARD");
                        initRIHR.setCreatedAt(forwardedAt); // don't let DB set this, use app
                        // time
                        initRIHR.setCreatedBy(FHIRService.class.getName());
                        initRIHR.setProvenance(provenance);
                        final var execResult = initRIHR.execute(jooqCfg);
                        LOG.info("REGISTER State Forward : END for interaction id : {} tenant id : {}" + execResult,
                                        bundleAsyncInteractionId, tenantId);
                } catch (Exception e) {
                        LOG.error("ERROR:: REGISTER State Forward CALL for interaction id : {} tenant id : {}"
                                        + initRIHR.getName() + " initRIHR error", bundleAsyncInteractionId, tenantId,
                                        e);
                }
        }

        private void registerStateComplete(org.jooq.Configuration jooqCfg, String bundleAsyncInteractionId,
                        String requestURI, String tenantId,
                        String response, String provenance) {
                LOG.info("REGISTER State Complete : BEGIN for interaction id :  {} tenant id : {}",
                                bundleAsyncInteractionId, tenantId);
                final var forwardRIHR = new RegisterInteractionHttpRequest();
                try {
                        forwardRIHR.setInteractionId(bundleAsyncInteractionId);
                        forwardRIHR.setInteractionKey(requestURI);
                        forwardRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                                        Map.of("nature", "Forwarded HTTP Response",
                                                        "tenant_id", tenantId)));
                        forwardRIHR.setContentType(
                                        MimeTypeUtils.APPLICATION_JSON_VALUE);
                        try {
                                // expecting a JSON payload from the server
                                forwardRIHR.setPayload(Configuration.objectMapper
                                                .readTree(response));
                        } catch (JsonProcessingException jpe) {
                                // in case the payload is not JSON store the string
                                forwardRIHR.setPayload((JsonNode) Configuration.objectMapper
                                                .valueToTree(response));
                        }
                        forwardRIHR.setFromState("FORWARD");
                        forwardRIHR.setToState("COMPLETE");
                        forwardRIHR.setCreatedAt(OffsetDateTime.now()); // don't let DB
                        // set this, use
                        // app time
                        forwardRIHR.setCreatedBy(FHIRService.class.getName());
                        forwardRIHR.setProvenance(provenance);
                        final var execResult = forwardRIHR.execute(jooqCfg);
                        LOG.info("REGISTER State Complete : END for interaction id : {} tenant id : {}" + execResult,
                                        bundleAsyncInteractionId, tenantId);
                } catch (Exception e) {
                        LOG.error("ERROR:: REGISTER State Complete CALL for interaction id : {} tenant id : {} "
                                        + forwardRIHR.getName()
                                        + " forwardRIHR error", bundleAsyncInteractionId, tenantId, e);
                }
        }

        private void registerStateFailed(org.jooq.Configuration jooqCfg, String bundleAsyncInteractionId,
                        String requestURI, String tenantId,
                        String response, String provenance) {
                LOG.info("REGISTER State Fail : BEGIN for interaction id :  {} tenant id : {}",
                                bundleAsyncInteractionId, tenantId);
                final var forwardRIHR = new RegisterInteractionHttpRequest();
                try {
                        forwardRIHR.setInteractionId(bundleAsyncInteractionId);
                        forwardRIHR.setInteractionKey(requestURI);
                        forwardRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                                        Map.of("nature", "Forwarded HTTP Response Error",
                                                        "tenant_id", tenantId)));
                        forwardRIHR.setContentType(
                                        MimeTypeUtils.APPLICATION_JSON_VALUE);
                        try {
                                // expecting a JSON payload from the server
                                forwardRIHR.setPayload(Configuration.objectMapper
                                                .readTree(response));
                        } catch (JsonProcessingException jpe) {
                                // in case the payload is not JSON store the string
                                forwardRIHR.setPayload((JsonNode) Configuration.objectMapper
                                                .valueToTree(response));
                        }
                        forwardRIHR.setFromState("FORWARD");
                        forwardRIHR.setToState("FAIL");
                        forwardRIHR.setCreatedAt(OffsetDateTime.now()); // don't let DB
                        // set this, use
                        // app time
                        forwardRIHR.setCreatedBy(FHIRService.class.getName());
                        forwardRIHR.setProvenance(provenance);
                        final var execResult = forwardRIHR.execute(jooqCfg);
                        LOG.info("REGISTER State Fail : END for interaction id : {} tenant id : {}" + execResult,
                                        bundleAsyncInteractionId, tenantId);
                } catch (Exception e) {
                        LOG.error("ERROR:: REGISTER State Fail CALL for interaction id : {} tenant id : {} "
                                        + forwardRIHR.getName()
                                        + " forwardRIHR error", bundleAsyncInteractionId, tenantId, e);
                }
        }

        private void registerStateFailure(org.jooq.Configuration jooqCfg, String dataLakeApiBaseURL,
                        String bundleAsyncInteractionId, Throwable error,
                        String requestURI, String tenantId,
                        String provenance) {
                LOG.error("Register State Failure - Exception while sending FHIR payload to datalake URL {} for interaction id {}",
                                dataLakeApiBaseURL, bundleAsyncInteractionId, error);
                final var errorRIHR = new RegisterInteractionHttpRequest();
                try {
                        errorRIHR.setInteractionId(bundleAsyncInteractionId);
                        errorRIHR.setInteractionKey(requestURI);
                        errorRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                                        Map.of("nature", "Forwarded HTTP Response Error",
                                                        "tenant_id", tenantId)));
                        errorRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                        final var rootCauseThrowable = NestedExceptionUtils
                                        .getRootCause(error);
                        final var rootCause = rootCauseThrowable != null
                                        ? rootCauseThrowable.toString()
                                        : "null";
                        final var mostSpecificCause = NestedExceptionUtils
                                        .getMostSpecificCause(error).toString();
                        final var errorMap = new HashMap<String, Object>() {
                                {
                                        put("dataLakeApiBaseURL", dataLakeApiBaseURL);
                                        put("error", error.toString());
                                        put("message", error.getMessage());
                                        put("rootCause", rootCause);
                                        put("mostSpecificCause", mostSpecificCause);
                                        put("tenantId", tenantId);
                                }
                        };
                        if (error instanceof final WebClientResponseException webClientResponseException) {
                                String responseBody = webClientResponseException
                                                .getResponseBodyAsString();
                                errorMap.put("responseBody", responseBody);
                                String bundleId = "";
                                JsonNode rootNode = Configuration.objectMapper
                                                .readTree(responseBody);
                                JsonNode bundleIdNode = rootNode.path("bundle_id"); // Adjust
                                // this
                                // path
                                // based
                                // on
                                // actual
                                if (!bundleIdNode.isMissingNode()) {
                                        bundleId = bundleIdNode.asText();
                                }
                                LOG.error(
                                                "Exception while sending FHIR payload to datalake URL {} for interaction id {} bundle id {} response from datalake {}",
                                                dataLakeApiBaseURL,
                                                bundleAsyncInteractionId, bundleId,
                                                responseBody);
                                errorMap.put("statusCode", webClientResponseException
                                                .getStatusCode().value());
                                final var responseHeaders = webClientResponseException
                                                .getHeaders()
                                                .entrySet()
                                                .stream()
                                                .collect(Collectors.toMap(
                                                                Map.Entry::getKey,
                                                                entry -> String.join(
                                                                                ",",
                                                                                entry.getValue())));
                                errorMap.put("headers", responseHeaders);
                                errorMap.put("statusText", webClientResponseException
                                                .getStatusText());
                        }
                        errorRIHR.setPayload((JsonNode) Configuration.objectMapper
                                        .valueToTree(errorMap));
                        errorRIHR.setFromState("FORWARD");
                        errorRIHR.setToState("FAIL");
                        errorRIHR.setCreatedAt(OffsetDateTime.now()); // don't let DB set this, use app time
                        errorRIHR.setCreatedBy(FHIRService.class.getName());
                        errorRIHR.setProvenance(provenance);
                        final var execResult = errorRIHR.execute(jooqCfg);
                        LOG.error("Register State Failure - END for interaction id : {} tenant id : {} forwardRIHR execResult"
                                        + execResult, bundleAsyncInteractionId, tenantId);
                } catch (Exception e) {
                        LOG.error("ERROR :: Register State Failure - for interaction id : {} tenant id : {} CALL "
                                        + errorRIHR.getName() + " errorRIHR error", bundleAsyncInteractionId, tenantId,
                                        e);
                }
        }

        private String getBundleInteractionId(HttpServletRequest request) {
                return InteractionsFilter.getActiveRequestEnc(request).requestId()
                                .toString();
        }

        private String getBaseUrl(HttpServletRequest request) {
                return Helpers.getBaseUrl(request);
        }

        public enum MTlsStrategy {
                NO_MTLS("no-mTls"),
                AWS_SECRETS("aws-secrets"),
                MTLS_RESOURCES("mTlsResources"),
                POST_STDOUT_PAYLOAD_TO_NYEC_DATA_LAKE_EXTERNAL("post-stdin-payload-to-nyec-datalake-external"),
                AWS_SECRETS_TEMP_FILE("aws-secrets-temp-file"),
                AWS_SECRETS_TEMP_WITHOUT_HASH("aws-secrets-without-hash"),
                AWS_SECRETS_TEMP_WITHOUT_OPENSSL("aws-secrets-without-openssl"),
                AWS_SECRETS_TEMP_FILE_WITHOUT_HASH("aws-secrets-temp-file-without-hash"),
                AWS_SECRETS_TEMP_FILE_WITHOUT_OPENSSL("aws-secrets-temp-file-without-openssl"),
                AWS_SECRETS_TEMP_FILE_WITHOUT_OPENSSLANDHASH("aws-secrets-temp-file-without-opensslandhash");

                private final String value;

                MTlsStrategy(String value) {
                        this.value = value;
                }

                public String getValue() {
                        return value;
                }

                public static String getAllValues() {
                        return Arrays.stream(MTlsStrategy.values())
                                        .map(MTlsStrategy::getValue)
                                        .collect(Collectors.joining(", "));
                }

                public static MTlsStrategy fromString(String value) {
                        for (MTlsStrategy strategy : MTlsStrategy.values()) {
                                if (strategy.value.equals(value)) {
                                        return strategy;
                                }
                        }
                        throw new IllegalArgumentException("No enum constant for value: " + value);
                }
        }

        public record KeyDetails(String key, String cert) {
        }

        public record PostToNyecExternalResponse(boolean completed, String processOutput, String errorOutput) {
        }

        private void validateCertificate(String certificate, String interactionId) throws Exception {
                File certFile = null; // Declare certFile outside the try block for finally access
                try {
                        LOG.info("Inside validate Certificate key - Begin for interactionId : {}", interactionId);
                        certFile = File.createTempFile("tempCert", ".pem");
                        LOG.info("Inside validate Temp File Created for interactionId : {}", interactionId);
                        Files.write(certFile.toPath(), certificate.getBytes(StandardCharsets.UTF_8));
                        LOG.info("Inside validatewrite to path completed for interactionId : {}", interactionId);
                        // Execute the OpenSSL command to verify the certificate
                        String[] command = { "openssl", "x509", "-in", certFile.getAbsolutePath(), "-noout", "-text" };
                        executeOpenSSLCommand(command, interactionId);
                        LOG.info("Inside validate executeOpenSSLCommand executed : {}", interactionId);
                } catch (Exception ex) {
                        LOG.error("Exception while executing openssl on certificate  " + ex.getMessage(), ex);
                } finally {
                        if (certFile != null) {
                                // Clean up the temporary file
                                certFile.delete();
                                LOG.info("Temporary certificate file deleted: {}", certFile.getAbsolutePath());
                        }
                        LOG.info("Inside validate Certificate key - End");
                }
        }

        private void validatePrivateKey(String privateKey, String interactionId) throws Exception {
                File keyFile = null; // Declare keyFile outside the try block for finally access
                try {
                        LOG.info("Inside validate Private Key - Begin for interaction id : {} ", interactionId);
                        keyFile = File.createTempFile("tempKey", ".pem");
                        LOG.info("Inside validate Private Key -Temp file created interaction id : {} ", interactionId);
                        Files.write(keyFile.toPath(), privateKey.getBytes(StandardCharsets.UTF_8));
                        LOG.info("Inside validate Private Key - Write file completed interaction id : {} ",
                                        interactionId);
                        // Execute the OpenSSL command to verify the private key
                        String[] command = { "openssl", "rsa", "-in", keyFile.getAbsolutePath(), "-check" };
                        executeOpenSSLCommand(command, interactionId);
                        LOG.info("Inside validate Private Key  Execute openssl completed for certificate interaction id : {} ",
                                        interactionId);
                } catch (Exception ex) {
                        LOG.error("Exception while executing openssl on key  " + ex.getMessage(), ex);
                } finally {
                        if (keyFile != null) {
                                // Clean up the temporary file
                                keyFile.delete();
                                LOG.info("Temporary private key file deleted: {}", keyFile.getAbsolutePath());
                        }
                        LOG.info("Inside validate Private Key - End");
                }
        }

        private void executeOpenSSLCommand(String[] command, String interactionId)
                        throws IOException, InterruptedException {
                LOG.info("Inside executeOpenSSLCommand - Begin for interaction id : {} ", interactionId);
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                LOG.info("Inside executeOpenSSLCommand - Process builder created : {} ", interactionId);
                processBuilder.redirectErrorStream(true); // Redirect error stream to output
                LOG.info("Inside executeOpenSSLCommand - redirectErrorStream  interaction id : {} ", interactionId);
                Process process = processBuilder.start();
                LOG.info("Inside executeOpenSSLCommand - processBuilder.start  interaction id : {} ", interactionId);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                                LOG.info(line);
                        }
                }
                LOG.info("Inside executeOpenSSLCommand - before process.waitFor  interaction id : {} ", interactionId);
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                        throw new IOException("OpenSSL command failed with exit code" + exitCode);
                }
                LOG.info("Inside executeOpenSSLCommand - End for interaction id : {} ", interactionId);
        }

        public String getUmlsApiKeyFromSecretManager(String keyName) {
            Region region = Region.US_EAST_1;
            LOG.info("keyName {} " , keyName);
            LOG.warn(
                    "FHIRService:: getUmlsApiKeyFromSecretManager - Get Secrets Client Manager for region : {} BEGIN for interaction id: {}",
                    region);
        
            SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                    .region(region)
                    .build();

            String umlsApiKey = getValue(secretsClient, keyName);
            secretsClient.close();
        
            LOG.warn(
                    "FHIRService:: getUmlsApiKeyFromSecretManager - Get Secrets Client Manager for region : {} END for interaction id: {}",
                    region);
            LOG.info("umlsApiKey : {} " , umlsApiKey);
            return umlsApiKey;
        }
}
