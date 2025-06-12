package org.techbd.service.ccda;

import org.techbd.config.MirthJooqConfig;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.techbd.config.MirthJooqConfig;
import org.techbd.config.Configuration;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;

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
            logger.info("Saving original CCDA payload with interactionId: " + interactionId);
            logger.info("Request URI: " + requestUri);
            logger.info("Tenant ID: " + tenantId);
            Map<String, Object> natureMap = Map.of(
                    "nature", "Original CCDA Payload",
                    "tenant_id", tenantId);
            JsonNode natureNode = Configuration.objectMapper.valueToTree(natureMap);
            JsonNode payloadNode = Configuration.objectMapper.valueToTree(operationOutcome);

            var jooqCfg = MirthJooqConfig.dsl().configuration();
            var rihr = new RegisterInteractionHttpRequest();

            rihr.setInteractionId(interactionId);
            rihr.setInteractionKey(requestUri);
            rihr.setNature(natureNode);
            rihr.setContentType("application/json");
            rihr.setPayloadText(payloadJson);
            rihr.setFromState("NONE");
            rihr.setToState("CCDA_ACCEPT");
            rihr.setSourceType("CCDA");
            rihr.setCreatedAt(OffsetDateTime.now());
            rihr.setCreatedBy(CCDAService.class.getName());
            String provenance = "%s.saveCcdaValidation".formatted(CCDAService.class.getName());
            rihr.setProvenance(provenance);
            int result = rihr.execute(jooqCfg);
            logger.info("Function execution result: " + result);
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
            logger.info("Saving CCDA validation with interactionId: " + interactionId);
            logger.info("Request URI: " + requestUri);
            logger.info("Tenant ID: " + tenantId);
            Map<String, Object> natureMap = Map.of(
                    "nature", "CCDA Validation Result",
                    "tenant_id", tenantId);
            JsonNode natureNode = Configuration.objectMapper.valueToTree(natureMap);
            JsonNode payloadNode = Configuration.objectMapper.valueToTree(operationOutcome);

            var jooqCfg = MirthJooqConfig.dsl().configuration();
            var rihr = new RegisterInteractionHttpRequest();

            rihr.setInteractionId(interactionId);
            rihr.setInteractionKey(requestUri);
            rihr.setNature(natureNode);
            rihr.setContentType("application/json");
            rihr.setPayload(payloadNode);
            rihr.setFromState("CCDA_ACCEPT");
            rihr.setToState(isValid ? "VALIDATION_SUCCESS" : "VALIDATION_FAILED");
            rihr.setSourceType("CCDA");
            rihr.setCreatedAt(OffsetDateTime.now());
            rihr.setCreatedBy(CCDAService.class.getName());
            String provenance = "%s.saveCcdaValidation".formatted(CCDAService.class.getName());
            rihr.setProvenance(provenance);
            int result = rihr.execute(jooqCfg);
            logger.info("Function execution result: " + result);
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
            logger.info("Saving FHIR conversion result with interactionId: " + interactionId);
            logger.info("Conversion result: " + (conversionSuccess ? "SUCCESS" : "FAILED"));

            Map<String, Object> natureMap = Map.of(
                    "nature", "Converted to FHIR",
                    "tenant_id", tenantId);
            JsonNode natureNode = Configuration.objectMapper.valueToTree(natureMap);
            JsonNode bundleNode = Configuration.objectMapper.valueToTree(bundle);

            var jooqCfg = MirthJooqConfig.dsl().configuration();
            var rihr = new RegisterInteractionHttpRequest();
            rihr.setInteractionId(interactionId);
            rihr.setInteractionKey(requestUri);
            rihr.setNature(natureNode);
            rihr.setContentType("application/json");
            rihr.setPayload(bundleNode);
            rihr.setFromState("VALIDATION_SUCCESS");
            rihr.setToState(conversionSuccess ? "CONVERTED_TO_FHIR" : "FHIR_CONVERSION_FAILED");
            rihr.setSourceType("CCDA");
            rihr.setCreatedAt(OffsetDateTime.now());
            rihr.setCreatedBy(CCDAService.class.getName());

            String provenance = "%s.saveCcdaValidation".formatted(CCDAService.class.getName());
            rihr.setProvenance(provenance);
            int result = rihr.execute(jooqCfg);
            logger.info("Function execution result: " + result);
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
            logger.info("Saving CCDA validation with interactionId: " + interactionId);
            logger.info("Request URI: " + requestUri);
            logger.info("Tenant ID: " + tenantId);

            Map<String, Object> natureMap = Map.of(
                    "nature", "CCDA Validation Result",
                    "tenant_id", tenantId);
            JsonNode natureNode = Configuration.objectMapper.valueToTree(natureMap);
            JsonNode payloadNode = Configuration.objectMapper.valueToTree(operationOutcome);

            var jooqCfg = MirthJooqConfig.dsl().configuration();
            var rihr = new RegisterInteractionHttpRequest();
            rihr.setInteractionId(interactionId);
            rihr.setInteractionKey(requestUri);
            rihr.setNature(natureNode);
            rihr.setContentType("application/json");
            rihr.setPayload(payloadNode);
            rihr.setFromState("CCDA_ACCEPT");
            rihr.setToState(isValid ? "VALIDATION_SUCCESS" : "VALIDATION_FAILED");
            rihr.setSourceType("CCDA");
            rihr.setCreatedAt(OffsetDateTime.now());
            rihr.setCreatedBy(CCDAService.class.getName());
            String provenance = "%s.saveCcdaValidation".formatted(CCDAService.class.getName());
            rihr.setProvenance(provenance);

            int result = rihr.execute(jooqCfg);
            logger.info("Function execution result: " + result);
            return result >= 0;
        } catch (Exception e) {
            logger.error("Error saving CCDA validation for interactionId: {}", interactionId, e);
            return false;
        }
    }
}
