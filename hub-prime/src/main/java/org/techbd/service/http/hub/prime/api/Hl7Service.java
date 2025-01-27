package org.techbd.service.http.hub.prime.api;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.techbd.conf.Configuration;
import org.techbd.service.constants.SourceType;
import org.techbd.service.converters.shinny.Hl7FHIRToShinnyFHIRConverter;
import org.techbd.service.http.Constant;
import org.techbd.service.http.GitHubUserAuthorizationFilter;
import org.techbd.service.http.InteractionsFilter;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.linuxforhealth.hl7.HL7ToFHIRConverter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class Hl7Service {
    private static final Logger LOG = LoggerFactory.getLogger(Hl7Service.class.getName());
    private final Hl7FHIRToShinnyFHIRConverter hl7FHIRToShinnyFHIRConverter;
    private final FHIRService fhirService;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;

    @Value("${org.techbd.service.http.interactions.default-persist-strategy:#{null}}")
    private String defaultPersistStrategy;

    @Value("${org.techbd.service.http.interactions.saveUserDataToInteractions:true}")
    private boolean saveUserDataToInteractions;

    public Hl7Service(final FHIRService fhirService, final UdiPrimeJpaConfig udiPrimeJpaConfig,
            final Hl7FHIRToShinnyFHIRConverter hl7FHIRToShinnyFHIRConverter) {
        this.fhirService = fhirService;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
        this.hl7FHIRToShinnyFHIRConverter = hl7FHIRToShinnyFHIRConverter;
    }

    public Object processHl7Message(String hl7Payload, String tenantId, HttpServletRequest request,
            HttpServletResponse response,boolean logPayloadEnabled) throws IOException {
        final var interactionId = getBundleInteractionId(request);
        final var dslContext = udiPrimeJpaConfig.dsl();
        final var jooqCfg = dslContext.configuration();
        try {
            LOG.info("HL7Service::processHl7Message BEGIN for interactionid : {} tenantId :{} ", interactionId,
                    tenantId);
            if(logPayloadEnabled) {
                LOG.info("HL7Service :: *****************ORIGINAL HL7 PAYLOAD*************\n\n : {} ",hl7Payload);
            }

            final var hl7FHIRJson = convertHl7ToFHIRJson(jooqCfg, hl7Payload, tenantId, interactionId);
            if(logPayloadEnabled) {
                LOG.info("HL7Service :: *****************LINUX CONVERTED PAYLOAD*************\n\n : {} ",hl7FHIRJson);
            }
            if (null != hl7FHIRJson) {
                final String shinnyFhirJson = convertToShinnyFHIRJson(jooqCfg, hl7FHIRJson, tenantId, interactionId);
                if(logPayloadEnabled) {
                        LOG.info("HL7Service :: *****************SHINNY FHIR PAYLOAD*************\n\n : {} ",shinnyFhirJson);
                    }
                if (null != shinnyFhirJson) {
                    registerStateHl7Accept(jooqCfg,hl7Payload, hl7FHIRJson, tenantId, interactionId,  request,
                            response);
                    LOG.info(
                            "HL7Service::processHl7Message END -start processing FHIR Json for interactionid : {} tenantId :{} ",
                            interactionId, tenantId);
                    return fhirService.processBundle(shinnyFhirJson, tenantId, null, null, null, null, null,
                            Boolean.toString(false), false,
                            false,
                            false, request, response, null, true,null,null, null,null,SourceType.HL7.name(),null,null);
                }
            }
        } catch (Exception ex) {
            LOG.error(" ERROR:: HL7Service::processHl7Message BEGIN for interactionid : {} tenantId :{} ",
                    interactionId, tenantId, ex);
            registerStateFailed(jooqCfg, interactionId,
                    request.getRequestURI(), tenantId, ex.getMessage(),
                    "%s.processHl7Message".formatted(Hl7Service.class.getName()));
        }
        return null;
    }

    public String convertToShinnyFHIRJson(org.jooq.Configuration jooqCfg, String hl7FHIRJson, String tenantId,
            String interactionId) {
        LOG.info("HL7Service::convertToShinnyFHIRJson BEGIN for interactionid : {} tenantId :{} ", interactionId,
                tenantId);
        try {
            return hl7FHIRToShinnyFHIRConverter.convertToShinnyFHIRJson(hl7FHIRJson);
        } catch (Exception ex) {
            LOG.error(
                    "ERROR:: HL7Service::convertToShinnyFHIRJson Exception during conversion of JSON to Shinny Bundle Json   for interactionid : {} tenantId :{} ",
                    interactionId,
                    tenantId, ex);
            // TODO - call main proc
            registerStateFailed(jooqCfg, interactionId, null, tenantId,
                    "Exception during conversion of JSON to Shinny Bundle Json "
                            + ex.getMessage(),
                    "%s.convertToShinnyFHIRJson".formatted(Hl7Service.class.getName()));
        }
        LOG.info("HL7Service::convertToShinnyFHIRJson END for interactionid : {} tenantId :{} ", interactionId,
                tenantId);
        return null;
    }

    private String getBundleInteractionId(HttpServletRequest request) {
        return InteractionsFilter.getActiveRequestEnc(request).requestId()
                .toString();
    }

    private void registerStateFailed(org.jooq.Configuration jooqCfg, String interactionId,
            String requestURI, String tenantId,
            String response, String provenance) {
        LOG.info("REGISTER State Fail : BEGIN for interaction id :  {} tenant id : {}",
                interactionId, tenantId);
        final var forwardRIHR = new RegisterInteractionHttpRequest();
        try {
            forwardRIHR.setInteractionId(interactionId);
            forwardRIHR.setInteractionKey(requestURI);
            forwardRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                    Map.of("nature", "HL7 Handling Failed",
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
            forwardRIHR.setFromState("HL7_ACCEPT");
            forwardRIHR.setToState("FAIL");
            forwardRIHR.setCreatedAt(OffsetDateTime.now()); // don't let DB
            // set this, use
            // app time
            forwardRIHR.setCreatedBy(FHIRService.class.getName());
            forwardRIHR.setProvenance(provenance);
            forwardRIHR.execute(jooqCfg);     
        } catch (Exception e) {
            LOG.error("ERROR:: REGISTER State Fail CALL for interaction id : {} tenant id : {} "
                    + forwardRIHR.getName()
                    + " forwardRIHR error", interactionId, tenantId, e);
        }
        LOG.info("REGISTER State Fail : END for interaction id : {} tenant id : {}" ,
        interactionId, tenantId);
    }

    private void registerStateHl7Accept(org.jooq.Configuration jooqCfg, String hl7Payload ,String hl7FHIRJson, String tenantId,
            String interactionId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        LOG.info("REGISTER State HL7 ACCEPT : BEGIN for interaction id :  {} tenant id : {}",
                interactionId, tenantId);
        final var forwardedAt = OffsetDateTime.now();
        final var initRIHR = new RegisterInteractionHttpRequest();
        try {
            initRIHR.setInteractionId(interactionId);
            initRIHR.setInteractionKey(request.getRequestURI());
            initRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                    Map.of("nature", "Original HL7 Payload", "tenant_id",
                            tenantId)));
            initRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
            initRIHR.setPayload(Configuration.objectMapper
            .readTree(hl7FHIRJson));
            initRIHR.setPayloadText(hl7Payload);
            initRIHR.setFromState("NONE");
            initRIHR.setToState("HL7_ACCEPT");
            initRIHR.setCreatedAt(forwardedAt); // don't let DB set this, use app
            initRIHR.setCreatedBy(Hl7Service.class.getName());
            initRIHR.setProvenance("%s.registerStateHl7Accept".formatted(Hl7Service.class.getName()));
            if (saveUserDataToInteractions) {
                setUserDetails(initRIHR, request);
            } else {
                LOG.info("User details are not saved with Interaction as saveUserDataToInteractions: "
                        + saveUserDataToInteractions);
            }
            initRIHR.execute(jooqCfg);
        } catch (Exception e) {
            LOG.error("ERROR:: REGISTER State HL7 ACCEPT CALL for interaction id : {} tenant id : {}"
                    + initRIHR.getName() + " initRIHR error", interactionId, tenantId,
                    e);
        }
        LOG.info("REGISTER State HL7 ACCEPT : END for interaction id : {} tenant id : {}" ,
                interactionId, tenantId);
    }

    private void setUserDetails(RegisterInteractionHttpRequest rihr, HttpServletRequest request) {
        var curUserName = "API_USER";
        var gitHubLoginId = "N/A";
        final var sessionId = request.getRequestedSessionId();
        var userRole = "API_ROLE";
        if (!Constant.isStatelessApiUrl(request.getRequestURI())) { // Call only if not a stateless URL
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
        }
        rihr.setUserName(curUserName);
        rihr.setUserId(gitHubLoginId);
        rihr.setUserSession(sessionId);
        rihr.setUserRole(userRole);
    }

    private String convertHl7ToFHIRJson(org.jooq.Configuration jooqCfg, String hl7Payload, String tenantId,
            String interactionId) {
        LOG.info("HL7Service::convertToFHIRJson BEGIN for interactionid : {} tenantId :{} ", interactionId,
                tenantId);
        try {
               HL7ToFHIRConverter ftv = new HL7ToFHIRConverter();
            final var fhirJson = ftv.convert(hl7Payload);
            return fhirJson;
        } catch (Exception ex) {
            LOG.error(
                    "ERROR:: HL7Service::convertToFHIRJson Exception during the initial conversion of HL7 to JSON using Linuxforhelath HL7ToFHIRConverter  for interactionid : {} tenantId :{} ",
                    interactionId,
                    tenantId, ex);
            // TODO - call main proc
            registerStateFailed(jooqCfg, interactionId, null, tenantId,
                    "Exception during the initial conversion of HL7 to JSON using Linuxforhelath HL7ToFHIRConverter "
                            + ex.getMessage(),
                    "%s.convertToFHIRJson".formatted(Hl7Service.class.getName()));
        }
        LOG.info("HL7Service::convertToFHIRJson END for interactionid : {} tenantId :{} ", interactionId,
                tenantId);
        return null;
    }
}
