package org.techbd.service.ccda;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.MirthJooqConfig;
import org.techbd.config.Nature;
import org.techbd.config.SourceType;
import org.techbd.config.State;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionCcdaRequest;
import org.techbd.util.fhir.CoreFHIRUtil;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Service class for saving various stages of CCDA bundle processing to the
 * database.
 * 
 * This includes saving the original CCDA payload, validation results, and FHIR
 * conversion outcomes.
 * The service uses JOOQ routines to persist data and logs the interaction using
 * log4j.
 */
public class CCDAService {
    private static final Logger logger = LoggerFactory.getLogger(CCDAService.class);

    public static boolean saveOriginalCcdaPayload(String interactionId, String tenantId,
            String requestUri, String payloadJson,
            Map<String, Object> operationOutcome) {
        try {
            logger.info("CCDAService saveOriginalCcdaPayload  BEGIN with  requestURI :{} tenantid :{} interactionId: {}", requestUri, tenantId, interactionId);
            Map<String, Object> natureMap = Map.of(
                    "nature", Nature.ORIGINAL_CCDA_PAYLOAD.getDescription(),
                    "tenant_id", tenantId);
            JsonNode natureNode = Configuration.objectMapper.valueToTree(natureMap);
            JsonNode payloadNode = Configuration.objectMapper.valueToTree(operationOutcome);
            var jooqCfg = MirthJooqConfig.dsl().configuration();
            var rihr = new RegisterInteractionCcdaRequest();
            rihr.setPInteractionId(interactionId);
            rihr.setPInteractionKey(requestUri);
            rihr.setPNature(natureNode);
            rihr.setPContentType("application/json");
            rihr.setPPayloadText(payloadJson);
            rihr.setPFromState(State.NONE.name());
            rihr.setPToState(State.CCDA_ACCEPT.name());
            rihr.setPSourceType(SourceType.CCDA.name());
            // rihr.setPCreatedAt(OffsetDateTime.now());
            rihr.setPCreatedBy(CCDAService.class.getName());
            String provenance = "%s.saveCcdaValidation".formatted(CCDAService.class.getName());
            rihr.setPProvenance(provenance);
            final Instant start = Instant.now();
            final int result = rihr.execute(jooqCfg);
            final Instant end = Instant.now();
            final JsonNode responseFromDB = rihr.getReturnValue();
            final Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(responseFromDB);
            logger.info(
                    "CCDAService -saveOriginalCcdaPayload : END  result: {}, timeTaken: {} ms, error: {}, interaction_id : {} hub_nexus_interaction_id: {}",
                    result,
                    Duration.between(start, end).toMillis(),
                    responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
                    interactionId,
                    responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"));
            return result >= 0;
        } catch (Exception e) {
            logger.error("Error saving original CCDA payload for interactionId: {}", interactionId, e);
            return false;
        }
    }

    /**
     * Saves the original CCDA payload along with metadata to the database.
     *
     * @param interactionId    Unique identifier for the interaction
     * @param tenantId         Tenant identifier for multi-tenancy support
     * @param requestUri       Request URI from which the payload originated
     * @param payloadJson      Original CCDA payload in JSON format as a string
     * @param operationOutcome A map containing operation outcome or metadata
     * @return true if the data is successfully saved, false otherwise
     */
    public static boolean saveValidation(final boolean isValid, String interactionId, String tenantId,
            String requestUri, String payloadJson,
            Map<String, Object> operationOutcome) {
        try {
            logger.info("CCDAService saveValidation  BEGIN with  requestURI :{} tenantid :{} interactionId: {}", requestUri, tenantId, interactionId);
            Map<String, Object> natureMap = Map.of(
                    "nature", Nature.CCDA_VALIDATION_RESULT.getDescription(),
                    "tenant_id", tenantId);
            JsonNode natureNode = Configuration.objectMapper.valueToTree(natureMap);
            JsonNode payloadNode = Configuration.objectMapper.valueToTree(operationOutcome);
            var jooqCfg = MirthJooqConfig.dsl().configuration();
            var rihr = new RegisterInteractionCcdaRequest();
            rihr.setPInteractionId(interactionId);
            rihr.setPInteractionKey(requestUri);
            rihr.setPNature(natureNode);
            rihr.setPContentType("application/json");
            rihr.setPPayload(payloadNode);
            rihr.setPFromState(State.CCDA_ACCEPT.name());
            rihr.setPToState(isValid ? State.VALIDATION_SUCCESS.name() : State.VALIDATION_FAILED.name());
            rihr.setPSourceType(SourceType.CCDA.name());
            // rihr.setPCreatedAt(OffsetDateTime.now());
            rihr.setPCreatedBy(CCDAService.class.getName());
            String provenance = "%s.saveCcdaValidation".formatted(CCDAService.class.getName());
            rihr.setPProvenance(provenance);
            final Instant start = Instant.now();
            final int result = rihr.execute(jooqCfg);
            final Instant end = Instant.now();
            final JsonNode responseFromDB = rihr.getReturnValue();
            final Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(responseFromDB);

            logger.info(
                    "CCDAService -saveValidation END | result: {}, timeTaken: {} ms, error: {}, hub_nexus_interaction_id: {}",
                    result,
                    Duration.between(start, end).toMillis(),
                    responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
                    responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"));

            return result >= 0;
        } catch (Exception e) {
            logger.error("Error saving CCDA validation for interactionId: {}", interactionId, e);
            return false;
        }
    }

    /**
     * Saves the result of the FHIR conversion process to the database.
     *
     * @param conversionSuccess Indicates whether the conversion was successful
     * @param interactionId     Unique identifier for the interaction
     * @param tenantId          Tenant identifier for multi-tenancy support
     * @param requestUri        Request URI from which the payload originated
     * @param bundle            The FHIR bundle resulting from the conversion
     * @return true if the data is successfully saved, false otherwise
     */
    public static boolean saveFhirConversionResult(boolean conversionSuccess, String interactionId,
            String tenantId, String requestUri,
            Map<String, Object> bundle) {
        try {
            logger.info("CCDAService saveFhirConversionResult  BEGIN with  requestURI :{} tenantid :{} interactionId: {}", requestUri, tenantId, interactionId);
            logger.info("CCDAService Conversion result: " + (conversionSuccess ? "SUCCESS" : "FAILED"));
            Map<String, Object> natureMap = Map.of(
                    "nature", Nature.CONVERTED_TO_FHIR.getDescription(),
                    "tenant_id", tenantId);
            JsonNode natureNode = Configuration.objectMapper.valueToTree(natureMap);
            JsonNode bundleNode = Configuration.objectMapper.valueToTree(bundle);
            var jooqCfg = MirthJooqConfig.dsl().configuration();
            var rihr = new RegisterInteractionCcdaRequest();
            rihr.setPInteractionId(interactionId);
            rihr.setPInteractionKey(requestUri);
            rihr.setPNature(natureNode);
            rihr.setPContentType("application/json");
            rihr.setPPayload(bundleNode);
            rihr.setPFromState(State.VALIDATION_SUCCESS.name());
            rihr.setPToState(conversionSuccess ? State.CONVERTED_TO_FHIR.name() : State.FHIR_CONVERSION_FAILED.name());
            rihr.setPSourceType(SourceType.CCDA.name());
            // rihr.setCreatedAt(OffsetDateTime.now());
            rihr.setPCreatedBy(CCDAService.class.getName());

            String provenance = "%s.saveCcdaValidation".formatted(CCDAService.class.getName());
            rihr.setPProvenance(provenance);
            final Instant start = Instant.now();
            final int result = rihr.execute(jooqCfg);
            final Instant end = Instant.now();
            final JsonNode responseFromDB = rihr.getReturnValue();
            final Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(responseFromDB);
            logger.info(
                    "CCDAService - saveFhirConversionResult : END | result: {}, timeTaken: {} ms, error: {}, hub_nexus_interaction_id: {}",
                    result,
                    Duration.between(start, end).toMillis(),
                    responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
                    responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"));
            return result >= 0;
        } catch (Exception e) {
            logger.error("Error saving FHIR conversion result for interactionId: {}", interactionId, e);
            return false;
        }
    }

    /**
     * Saves CCDA validation information to the database.
     * This method combines validation and outcome tracking.
     *
     * @param isValid          Whether the validation was successful
     * @param interactionId    The interaction ID
     * @param tenantId         The tenant ID
     * @param requestUri       The request URI
     * @param payloadJson      The raw CCDA payload as JSON
     * @param operationOutcome The operation outcome details
     * @return true if saving was successful, false otherwise
     */
    public static boolean saveCcdaValidation(final boolean isValid, String interactionId, String tenantId,
            String requestUri, String payloadJson,
            Map<String, Object> operationOutcome) {
        try {
            logger.info("CCDAService saveCcdaValidation  BEGIN with  requestURI :{} tenantid :{} interactionId: {}", requestUri, tenantId, interactionId);
            Map<String, Object> natureMap = Map.of(
                    "nature", Nature.CCDA_VALIDATION_RESULT.getDescription(),
                    "tenant_id", tenantId);
            JsonNode natureNode = Configuration.objectMapper.valueToTree(natureMap);
            JsonNode payloadNode = Configuration.objectMapper.valueToTree(operationOutcome);
            var jooqCfg = MirthJooqConfig.dsl().configuration();
            var rihr = new RegisterInteractionCcdaRequest();
            rihr.setPInteractionId(interactionId);
            rihr.setPInteractionKey(requestUri);
            rihr.setPNature(natureNode);
            rihr.setPContentType("application/json");
            rihr.setPPayload(payloadNode);
            rihr.setPFromState(State.CCDA_ACCEPT.name());
            rihr.setPToState(isValid ? State.VALIDATION_SUCCESS.name() : State.VALIDATION_FAILED.name());
            rihr.setPSourceType(SourceType.CCDA.name());
            // rihr.setCreatedAt(OffsetDateTime.now());
            rihr.setPCreatedBy(CCDAService.class.getName());
            String provenance = "%s.saveCcdaValidation".formatted(CCDAService.class.getName());
            rihr.setPProvenance(provenance);
            final Instant start = Instant.now();
            final int result = rihr.execute(jooqCfg);
            final Instant end = Instant.now();

            final JsonNode responseFromDB = rihr.getReturnValue();
            final Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(responseFromDB);

            logger.info(
                    "CCDAService -saveCcdaValidation : END | result: {}, timeTaken: {} ms, error: {}, hub_nexus_interaction_id: {}",
                    result,
                    Duration.between(start, end).toMillis(),
                    responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
                    responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"));
            return result >= 0;
        } catch (Exception e) {
            logger.error("Error saving CCDA validation for interactionId: {}", interactionId, e);
            return false;
        }
    }
}
