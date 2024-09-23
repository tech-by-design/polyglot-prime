package org.techbd.service.http.hub.prime.api;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.techbd.conf.Configuration;
import org.techbd.orchestrate.fhir.OrchestrationEngine;
import org.techbd.orchestrate.fhir.OrchestrationEngine.Device;
import org.techbd.service.http.Helpers;
import org.techbd.service.http.InteractionsFilter;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.InteractionDisposition;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.oauth2.sdk.util.CollectionUtils;

import ca.uhn.fhir.validation.ResultSeverityEnum;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Mono;

@Service
public class FHIRService {

        private static final Logger LOG = LoggerFactory.getLogger(FHIRService.class.getName());
        private final AppConfig appConfig;
        private final OrchestrationEngine engine = new OrchestrationEngine();
        private final UdiPrimeJpaConfig udiPrimeJpaConfig;

        public FHIRService(
                        final AppConfig appConfig, final UdiPrimeJpaConfig udiPrimeJpaConfig) {
                this.appConfig = appConfig;
                this.udiPrimeJpaConfig = udiPrimeJpaConfig;
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
                        HttpServletRequest request, String provenance) {
                final var fhirProfileUrl = (fhirProfileUrlParam != null) ? fhirProfileUrlParam
                                : (fhirProfileUrlHeader != null) ? fhirProfileUrlHeader
                                                : appConfig.getDefaultSdohFhirProfileUrl();
                var immediateResult = validate(request, payload, fhirProfileUrl, uaValidationStrategyJson,
                                includeRequestInOutcome);                
                var result = Map.of("OperationOutcome", immediateResult);
                final var interactionId = getBundleInteractionId(request);
                final var DSL = udiPrimeJpaConfig.dsl();
                final var jooqCfg = DSL.configuration();
                // Check for the X-TechBD-HealthCheck header
                if ("true".equals(healthCheck)) {
                        LOG.info("%s is true, skipping DataLake submission."
                                        .formatted(AppConfig.Servlet.HeaderName.Request.HEALTH_CHECK_HEADER));
                        return result; // Return without proceeding to DataLake submission
                }
                registerStateAccept(interactionId, request.getRequestURI(),
                                tenantId, payload, provenance, jooqCfg);
                Map<String, Object> payloadWithDisposition = getTechByDesignDisposistion(interactionId, immediateResult,
                                jooqCfg);
                registerStateDisposition(interactionId, request.getRequestURI(), tenantId,
                                payloadWithDisposition, provenance, jooqCfg);
                if (checkForTechByDesignDisposition(interactionId, payloadWithDisposition)) {
                        return payloadWithDisposition;
                }
                sendToScoringEngine(request, customDataLakeApi, tenantId, payload, provenance, payloadWithDisposition,
                                dataLakeApiContentType, jooqCfg);
                return result;
        }

        private Map<String, Object> validate(HttpServletRequest request, String payload, String fhirProfileUrl,
                        String uaValidationStrategyJson,
                        boolean includeRequestInOutcome) {

                LOG.info("Getting structure definition Urls from config - Before: ");
                final var structureDefintionUrls = appConfig.getStructureDefinitionsUrls();
                LOG.info("Getting structure definition Urls from config - After : ", structureDefintionUrls);
                final var valueSetUrls = appConfig.getValueSetUrls();
                LOG.info(" Total value system URLS  in config: ", null != valueSetUrls ? valueSetUrls.size() : 0);
                final var codeSystemUrls = appConfig.getCodeSystemUrls();
                LOG.info(" Total code system URLS  in config: ", null != codeSystemUrls ? codeSystemUrls.size() : 0);
                final var sessionBuilder = engine.session()
                                .onDevice(Device.createDefault())
                                .withPayloads(List.of(payload))
                                .withFhirProfileUrl(fhirProfileUrl)
                                .withFhirStructureDefinitionUrls(structureDefintionUrls)
                                .withFhirCodeSystemUrls(codeSystemUrls)
                                .withFhirValueSetUrls(valueSetUrls)
                                .addHapiValidationEngine() // by default
                                // clearExisting is set to true so engines can be fully supplied through header
                                .withUserAgentValidationStrategy(uaValidationStrategyJson, true);
                final var session = sessionBuilder.build();
                final var bundleAsyncInteractionId = getBundleInteractionId(request);
                engine.orchestrate(session);
                session.getValidationResults().stream()
                                .map(OrchestrationEngine.ValidationResult::getIssues)
                                .filter(CollectionUtils::isNotEmpty)
                                .flatMap(List::stream)
                                .toList().stream()
                                .filter(issue -> (ResultSeverityEnum.FATAL.getCode()
                                                .equalsIgnoreCase(issue.getSeverity())))
                                .forEach(c -> {
                                        LOG.error(
                                                        "\n\n**********************FHIRController:Bundle ::  FATAL ERRORR********************** -BEGIN");
                                        LOG.error("##############################################\nFATAL ERROR Message"
                                                        + c.getMessage()
                                                        + "##############");
                                        LOG.error(
                                                        "\n\n**********************FHIRController:Bundle ::  FATAL ERRORR********************** -END");
                                });
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
                                "rejectionsList", List.of(),
                                "rejectionsMap", Map.of(),
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

        private void sendToScoringEngine(HttpServletRequest request, String customDataLakeApi,
                        String tenantId, String payload, String provenance,
                        Map<String, Object> validationPayloadWithDisposition,
                        String dataLakeApiContentType,
                        org.jooq.Configuration jooqCfg) {
                final var interactionId = getBundleInteractionId(request);
                LOG.info("FHIRService:: sendToScoringEngine BEGIN for  interaction id : {}",
                                interactionId);
                Map<String, Object> bundlePayloadWithDisposistion = addValidationResultToPayload(
                                getBundleInteractionId(request), payload, validationPayloadWithDisposition);
                final var requestURI = request.getRequestURI();
                final var bundleAsyncInteractionId = getBundleInteractionId(request);
                final var dataLakeApiBaseURL = customDataLakeApi != null && !customDataLakeApi.isEmpty()
                                ? customDataLakeApi
                                : appConfig.getDefaultDatalakeApiUrl();
                final var webClient = WebClient.builder().baseUrl(dataLakeApiBaseURL)
                                .filter(ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
                                        LOG.info("FHIRService:: sendToScoringEngine Filter request before post- BEGIN  interaction id : {}",
                                                        interactionId);
                                        final var requestBuilder = new StringBuilder();
                                        requestBuilder.append(clientRequest.method().name())
                                                        .append(" ")
                                                        .append(clientRequest.url())
                                                        .append(" HTTP/1.1")
                                                        .append("\n");
                                        clientRequest.headers().forEach((name, values) -> values
                                                        .forEach(value -> requestBuilder.append(name).append(": ")
                                                                        .append(value).append("\n")));
                                        // final var outboundHttpMessage = requestBuilder.toString();
                                        registerStateForward(provenance, bundleAsyncInteractionId, requestURI, tenantId,
                                                        null != bundlePayloadWithDisposistion
                                                                        ? bundlePayloadWithDisposistion
                                                                        : validationPayloadWithDisposition,
                                                        jooqCfg);
                                        LOG.info("FHIRService:: sendToScoringEngine Filter request before post- END  interaction id : {}",
                                                        interactionId);
                                        return Mono.just(clientRequest);
                                })).build();

                try {
                        LOG.info("FHIRService:: sendToScoringEngine Post to scoring engine - BEGIN  interaction id : {}",
                                        interactionId);
                        webClient.post()
                                        .uri("?processingAgent=" + tenantId)
                                        .body(BodyInserters.fromValue(null != bundlePayloadWithDisposistion
                                                        ? bundlePayloadWithDisposistion
                                                        : payload))
                                        .header("Content-Type",
                                                        Optional.ofNullable(
                                                                        Optional.ofNullable(dataLakeApiContentType)
                                                                                        .orElse(request.getContentType()))
                                                                        .orElse(AppConfig.Servlet.FHIR_CONTENT_TYPE_HEADER_VALUE))
                                        .retrieve()
                                        .bodyToMono(String.class)
                                        .subscribe(response -> {
                                                registerStateComplete(bundleAsyncInteractionId,
                                                                requestURI, tenantId, response, provenance, jooqCfg);
                                        }, (Throwable error) -> { // Explicitly specify the type Throwable
                                                registerStateFailure(dataLakeApiBaseURL,
                                                                bundleAsyncInteractionId, error, requestURI, tenantId,
                                                                provenance, jooqCfg);
                                        });
                        LOG.info("FHIRService:: sendToScoringEngine Post to scoring engine - END  interaction id : {}",
                                        interactionId);
                } catch (Exception e) {
                        validationPayloadWithDisposition.put("exception", e.toString());
                        LOG.error("ERROR:: FHIRService:: sendToScoringEngine Exception while sending to scoring engine payload with interaction id {}",
                                        getBundleInteractionId(request), e);
                }
                LOG.info("FHIRService:: sendToScoringEngine END for  interaction id : {}",
                                getBundleInteractionId(request));

        }

        private Map<String, Object> addValidationResultToPayload(String interactionId, String bundlePayload,
                        Map<String, Object> payloadWithDisposition) {
                LOG.info("FHIRService:: addValidationResultToPayload BEGIN for  interaction id : {}",
                                interactionId);
                Map<String, Object> resultMap = null;
                try {
                        Map<String, Object> extractedOutcome = extractResourceTypeAndIssue(interactionId,
                                        payloadWithDisposition);
                        Map<String, Object> bundleMap = Configuration.objectMapper.readValue(bundlePayload,
                                        new TypeReference<Map<String, Object>>() {
                                        });
                        resultMap = appendToBundlePayload(interactionId, bundleMap,
                                        extractedOutcome);
                } catch (Exception ex) {
                        LOG.error("ERROR :: FHIRService:: addValidationResultToPayload END for  interaction id : {}",
                                        interactionId, ex);
                }
                LOG.info("FHIRService:: addValidationResultToPayload END for  interaction id : {}",
                                interactionId);
                return resultMap;
        }

        private Map<String, Object> extractResourceTypeAndIssue(String interactionId,
                        Map<String, Object> operationOutcomePayload) {
                LOG.info("FHIRService:: extractResourceTypeAndIssue BEGIN for  interaction id : {}",
                                interactionId);
                @SuppressWarnings("unchecked")
                Map<String, Object> operationOutcome = (Map<String, Object>) ((Map<String, Object>) ((List<?>) ((Map<String, Object>) operationOutcomePayload
                                .get("OperationOutcome")).get("validationResults")).get(0)).get("operationOutcome");
                Map<String, Object> result = new HashMap<>();
                result.put("resourceType", operationOutcome.get("resourceType"));
                result.put("issue", operationOutcome.get("issue"));
                LOG.info("FHIRService:: extractResourceTypeAndIssue END for  interaction id : {}",
                                interactionId);
                return result;
        }

        private Map<String, Object> appendToBundlePayload(String interactionId, Map<String, Object> payload1,
                        Map<String, Object> extractedOutcome) {
                LOG.info("FHIRService:: appendToBundlePayload BEGIN for  interaction id : {}",
                                interactionId);
                List<Map<String, Object>> entries = (List<Map<String, Object>>) payload1.get("entry");
                Map<String, Object> newEntry = new HashMap<>();
                newEntry.put("fullUrl", "http://shinny.org/OperationOutcome");
                newEntry.put("resource", extractedOutcome);
                entries.add(newEntry);
                Map<String, Object> modifiedPayload = new HashMap<>(payload1);
                modifiedPayload.put("entry", entries);
                LOG.info("FHIRService:: appendToBundlePayload END for  interaction id : {}",
                                interactionId);
                return modifiedPayload;
        }

        private void registerStateAccept(String bundleAsyncInteractionId, String requestURI, String tenantId,
                        String payload,
                        String provenance, org.jooq.Configuration jooqCfg) {
                LOG.info("REGISTER State Accept : BEGIN for  interaction id : {} tenant id : {}",
                                bundleAsyncInteractionId, tenantId);
                final var payloadRIHR = new RegisterInteractionHttpRequest();
                try {
                        payloadRIHR.setInteractionId(bundleAsyncInteractionId);
                        payloadRIHR.setInteractionKey(requestURI);
                        payloadRIHR.setNature(Configuration.objectMapper.valueToTree(
                                        Map.of("nature", "Original FHIR Payload", "tenant_id",
                                                        tenantId)));
                        payloadRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                        try {
                                // input FHIR Bundle JSON payload
                                payloadRIHR.setPayload(
                                                Configuration.objectMapper.readTree(payload));
                        } catch (JsonProcessingException jpe) {
                                // in case the payload is not JSON store the string
                                payloadRIHR.setPayload(Configuration.objectMapper
                                                .valueToTree(payload));
                        }
                        payloadRIHR.setFromState("NONE");
                        payloadRIHR.setToState("ACCEPT_FHIR_BUNDLE");
                        payloadRIHR.setCreatedAt(OffsetDateTime.now());
                        payloadRIHR.setCreatedBy(FHIRService.class.getName());
                        payloadRIHR.setProvenance(provenance);
                        final var execResult = payloadRIHR.execute(jooqCfg);
                        LOG.info("REGISTER State Accept : END for  interaction id :{} ,tenant id : {} " + execResult,
                                        bundleAsyncInteractionId, tenantId);
                } catch (Exception e) {
                        LOG.error("ERROR:: REGISTER State Accept  CALL for  interaction id : {} tenant id : {} "
                                        + payloadRIHR.getName() + " payloadRIHR error", bundleAsyncInteractionId,
                                        tenantId, e);
                }
        }

        private void registerStateDisposition(String bundleAsyncInteractionId, String requestURI, String tenantId,
                        Map<String, Object> payloadWithDisposition,
                        String provenance, org.jooq.Configuration jooqCfg) {
                LOG.info("REGISTER State Disposition : BEGIN for interaction id : {} tenant id : {}",
                                bundleAsyncInteractionId, tenantId);
                final var payloadRIHR = new RegisterInteractionHttpRequest();
                try {
                        payloadRIHR.setInteractionId(bundleAsyncInteractionId);
                        payloadRIHR.setInteractionKey(requestURI);
                        payloadRIHR.setNature(Configuration.objectMapper.valueToTree(
                                        Map.of("nature", "Tech By Design Disposition", "tenant_id",
                                                        tenantId)));
                        payloadRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                        payloadRIHR.setPayload(
                                        Configuration.objectMapper.valueToTree(payloadWithDisposition));
                        payloadRIHR.setFromState("ACCEPT_FHIR_BUNDLE");
                        payloadRIHR.setToState("DISPOSITION");
                        payloadRIHR.setCreatedAt(OffsetDateTime.now());
                        payloadRIHR.setCreatedBy(FHIRService.class.getName());
                        payloadRIHR.setProvenance(provenance);
                        final var execResult = payloadRIHR.execute(jooqCfg);
                        LOG.info("REGISTER State Disposition : END  for interaction id : {} tenant id : {}"
                                        + execResult, bundleAsyncInteractionId, tenantId);
                } catch (Exception e) {
                        LOG.error("\"ERROR:: REGISTER State Disposition CALL for interaction id : {}  tenant id : {}"
                                        + payloadRIHR.getName() + " payloadRIHR error", bundleAsyncInteractionId,
                                        tenantId, e);
                }
        }

        private void registerStateForward(String provenance, String bundleAsyncInteractionId, String requestURI,
                        String tenantId,
                        Map<String, Object> payloadWithDisposition, org.jooq.Configuration jooqCfg) {
                LOG.info("REGISTER State Forward : BEGIN for inteaction id  : {} tenant id : {}",
                                bundleAsyncInteractionId, tenantId);
                final var forwardedAt = OffsetDateTime.now();
                final var initRIHR = new RegisterInteractionHttpRequest();
                try {
                        // TODO - store payload based on includeIncomingPayloadInDB - check if this is
                        // needed
                        // immediateResult.put("outboundHttpMessage",
                        // outboundHttpMessage + "\n" + (includeIncomingPayloadInDB
                        // ? payload
                        // : "The incoming FHIR payload was not stored (to save space).\nThis is not an
                        // error or warning just an FYI - if you'd like to see the incoming FHIR payload
                        // for debugging, next time just pass in the optional
                        // `?include-incoming-payload-in-db=true` to request payload storage for each
                        // request that you'd like to store."));
                        initRIHR.setInteractionId(bundleAsyncInteractionId);
                        initRIHR.setInteractionKey(requestURI);
                        initRIHR.setNature(Configuration.objectMapper.valueToTree(
                                        Map.of("nature", "Forward HTTP Request", "tenant_id",
                                                        tenantId)));
                        initRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                        initRIHR.setPayload(Configuration.objectMapper
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

        private void registerStateComplete(String bundleAsyncInteractionId, String requestURI, String tenantId,
                        String response, String provenance, org.jooq.Configuration jooqCfg) {
                LOG.info("REGISTER State Complete : BEGIN for interaction id :  {} tenant id : {}",
                                bundleAsyncInteractionId, tenantId);
                final var forwardRIHR = new RegisterInteractionHttpRequest();
                try {
                        forwardRIHR.setInteractionId(bundleAsyncInteractionId);
                        forwardRIHR.setInteractionKey(requestURI);
                        forwardRIHR.setNature(Configuration.objectMapper.valueToTree(
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
                                forwardRIHR.setPayload(Configuration.objectMapper
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
                        LOG.info("REGISTER State Complete : BEGIN for interaction id : {} tenant id : {}" + execResult,
                                        bundleAsyncInteractionId, tenantId);
                } catch (Exception e) {
                        LOG.error("ERROR:: REGISTER State Complete CALL for interaction id : {} tenant id : {} "
                                        + forwardRIHR.getName()
                                        + " forwardRIHR error", bundleAsyncInteractionId, tenantId, e);
                }
        }

        private void registerStateFailure(String dataLakeApiBaseURL, String bundleAsyncInteractionId, Throwable error,
                        String requestURI, String tenantId,
                        String provenance, org.jooq.Configuration jooqCfg) {
                LOG.error("Register State Failure - Exception while sending FHIR payload to datalake URL {} for interaction id {}",
                                dataLakeApiBaseURL, bundleAsyncInteractionId, error);
                final var errorRIHR = new RegisterInteractionHttpRequest();
                try {
                        errorRIHR.setInteractionId(bundleAsyncInteractionId);
                        errorRIHR.setInteractionKey(requestURI);
                        errorRIHR.setNature(Configuration.objectMapper.valueToTree(
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
                        errorRIHR.setPayload(Configuration.objectMapper
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

        private Map<String, Object> getTechByDesignDisposistion(String interactionId,
                        Map<String, Object> validationResultJson,
                        org.jooq.Configuration jooqCfg) {
                try {
                        LOG.info("FHIRService:: Invoke Tech By Design Disposition procedure -BEGIN for interaction id : {}",
                                        interactionId);
                        JsonNode validationResultStr = Configuration.objectMapper.convertValue(validationResultJson,
                                        JsonNode.class);
                        InteractionDisposition intDisp = new InteractionDisposition();
                        intDisp.setInputJson(validationResultStr);
                        intDisp.setInteractionId(interactionId);
                        final var execResult = intDisp.execute(jooqCfg);
                        JsonNode payloadWithDisposition = intDisp.getReturnValue();
                        LOG.info("FHIRService:: Invoke Tech By Design Disposition procedure -END for interaction id : {} Result of invoking interaction_disposition : "
                                        + execResult, interactionId);
                        return Configuration.objectMapper.convertValue(payloadWithDisposition,
                                        new TypeReference<Map<String, Object>>() {
                                        });
                } catch (Exception ex) {
                        LOG.error("ERROR:: ", ex);
                }
                return null;
        }

        private boolean checkForTechByDesignDisposition(String interactionId,
                        Map<String, Object> payloadWithDisposition) {
                LOG.info("FHIRService :: checkForTechByDesignDisposition  - BEGIN for interaction id : {} ",
                                interactionId);
                var hasRejections = false;
                if (null != payloadWithDisposition) {
                        try {
                                String payloadWithDispositionStr = Configuration.objectMapper
                                                .writeValueAsString(payloadWithDisposition);
                                List<String> actions = JsonPath.read(payloadWithDispositionStr,
                                                "$.techByDesignDisposition[*].action");
                                hasRejections = actions.contains("reject");
                                LOG.info(" FHIRService :: checkForTechByDesignDisposition Tech by Design Disposition : "
                                                + (hasRejections ? "DO NOT FORWARD" : "FORWARD"));
                        } catch (Exception ex) {
                                LOG.error("ERROR :: FHIRService :: checkForTechByDesignDisposition  - END for interaction id : {} ",
                                                interactionId, ex);
                        }
                }
                LOG.info("FHIRService :: checkForTechByDesignDisposition  - END for interaction id : {} ",
                                interactionId);
                return hasRejections;
        }
}
