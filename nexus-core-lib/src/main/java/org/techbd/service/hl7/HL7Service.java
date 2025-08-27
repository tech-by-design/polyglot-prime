package org.techbd.service.hl7;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.CoreAppConfig;
import org.techbd.config.CoreUdiPrimeJpaConfig;
import org.techbd.config.Nature;
import org.techbd.config.SourceType;
import org.techbd.config.State;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHl7Request;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;
import org.techbd.util.fhir.CoreFHIRUtil;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Service class for saving various stages of HL7 message processing to the
 * database.
 * 
 * This includes saving the original HL7 payload, validation results, and FHIR
 * conversion outcomes.
 * The service uses JOOQ routines to persist data and logs the interaction using
 * SLF4J.
 */
@Service
public class HL7Service {
    private final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig;
    private final TemplateLogger logger;
    private final CoreAppConfig coreAppConfig;

    public HL7Service(final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig, final AppLogger appLogger, final CoreAppConfig coreAppConfig) {
        this.coreUdiPrimeJpaConfig = coreUdiPrimeJpaConfig;
        this.logger = appLogger.getLogger(HL7Service.class);
        this.coreAppConfig = coreAppConfig;
    }    

    /**
     * Saves the original HL7 payload along with metadata to the database.
     *
     * @param interactionId    Unique identifier for the interaction
     * @param tenantId         Tenant identifier for multi-tenancy support
     * @param requestUri       Request URI from which the payload originated
     * @param payloadJson      Original HL7 payload in JSON format as a string
     * @param operationOutcome A map containing operation outcome or metadata
     * @return true if the data is successfully saved, false otherwise
     */
    public boolean saveOriginalHl7Payload(String interactionId, String tenantId,
            String requestUri, String payloadJson,
            Map<String, Object> operationOutcome) {
        try {
            logger.info("HL7Service saveOriginalHl7Payload BEGIN with requestURI :{} tenantid :{} interactionId: {}", requestUri, tenantId, interactionId);
            Map<String, Object> natureMap = Map.of(
                    "nature", Nature.ORIGINAL_HL7_PAYLOAD.getDescription(), // Replace with HL7-specific if applicable
                    "tenant_id", tenantId);
            JsonNode natureNode = Configuration.objectMapper.valueToTree(natureMap);
            JsonNode payloadNode = Configuration.objectMapper.valueToTree(operationOutcome);
            var jooqCfg = coreUdiPrimeJpaConfig.dsl().configuration();
            var rihr = new RegisterInteractionHl7Request();
            rihr.setPInteractionId(interactionId);
            rihr.setPInteractionKey(requestUri);
            rihr.setPNature(natureNode);
            rihr.setPContentType("application/json");
            rihr.setPPayloadText(payloadJson);
            rihr.setPFromState(State.NONE.name());
            rihr.setPToState(State.HL7_ACCEPT.name()); // Replace if HL7 state differs
            rihr.setPSourceType(SourceType.HL7.name()); // Replace with HL7 if defined
            rihr.setPCreatedAt(OffsetDateTime.now());
            rihr.setPCreatedBy(HL7Service.class.getName());
            String provenance = "%s.saveHl7Validation".formatted(HL7Service.class.getName());
            rihr.setPProvenance(provenance);
            rihr.setPTechbdVersionNumber(coreAppConfig.getVersion());
            final Instant start = Instant.now();
            final int result = rihr.execute(jooqCfg);
            final Instant end = Instant.now();
            final JsonNode responseFromDB = rihr.getReturnValue();
            final Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(responseFromDB);
            logger.info(
                    "HL7Service - saveOriginalHl7Payload END | result: {}, timeTaken: {} ms, error: {}, interaction_id: {}, hub_nexus_interaction_id: {}",
                    result,
                    Duration.between(start, end).toMillis(),
                    responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
                    interactionId,
                    responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"));
            return result >= 0;
        } catch (Exception e) {
            logger.error("Error saving original HL7 payload for interactionId: {}", interactionId, e);
            return false;
        }
    }

    /**
     * Saves the HL7 validation result along with metadata to the database.
     *
     * @param isValid          Whether the HL7 message is valid or not
     * @param interactionId    Unique identifier for the interaction
     * @param tenantId         Tenant identifier for multi-tenancy support
     * @param requestUri       Request URI of the HL7 message
     * @param payloadJson      The original HL7 message in JSON format
     * @param operationOutcome A map containing validation outcome details
     * @return true if the data is successfully saved, false otherwise
     */
    public boolean saveValidation(final boolean isValid, String interactionId, String tenantId,
            String requestUri, String payloadJson,
            Map<String, Object> operationOutcome) {
        try {
            logger.info("HL7Service saveValidation BEGIN with requestURI :{} tenantid :{} interactionId: {}", requestUri, tenantId, interactionId);
            Map<String, Object> natureMap = Map.of(
                    "nature", Nature.HL7_VALIDATION_RESULT.getDescription(),
                    "tenant_id", tenantId);
            JsonNode natureNode = Configuration.objectMapper.valueToTree(natureMap);
            JsonNode payloadNode = Configuration.objectMapper.valueToTree(operationOutcome);
            var jooqCfg = coreUdiPrimeJpaConfig.dsl().configuration();
            var rihr = new RegisterInteractionHl7Request();
            rihr.setPInteractionId(interactionId);
            rihr.setPInteractionKey(requestUri);
            rihr.setPNature(natureNode);
            rihr.setPContentType("application/json");
            rihr.setPPayload(payloadNode);
            rihr.setPFromState(State.HL7_ACCEPT.name());
            rihr.setPToState(isValid ? State.VALIDATION_SUCCESS.name() : State.VALIDATION_FAILED.name());
            rihr.setPSourceType(SourceType.HL7.name());
            rihr.setPCreatedAt(OffsetDateTime.now());
            rihr.setPCreatedBy(HL7Service.class.getName());
            String provenance = "%s.saveHl7Validation".formatted(HL7Service.class.getName());
            rihr.setPProvenance(provenance);
            rihr.setPTechbdVersionNumber(coreAppConfig.getVersion());
            final Instant start = Instant.now();
            final int result = rihr.execute(jooqCfg);
            final Instant end = Instant.now();
            final JsonNode responseFromDB = rihr.getReturnValue();
            final Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(responseFromDB);

            logger.info(
                    "HL7Service - saveValidation END | result: {}, timeTaken: {} ms, error: {}, hub_nexus_interaction_id: {}",
                    result,
                    Duration.between(start, end).toMillis(),
                    responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
                    responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"));

            return result >= 0;
        } catch (Exception e) {
            logger.error("Error saving HL7 validation for interactionId: {}", interactionId, e);
            return false;
        }
    }
}
