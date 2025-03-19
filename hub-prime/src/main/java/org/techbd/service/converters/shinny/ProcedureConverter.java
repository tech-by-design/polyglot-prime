package org.techbd.service.converters.shinny;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.model.csv.*;
import org.techbd.util.CsvConstants;
import org.techbd.util.CsvConversionUtil;
import org.techbd.util.DateUtil;
import org.techbd.util.FHIRUtil;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

/**
 * Converts healthcare screening data into FHIR Procedure resources.
 * 
 * <p>
 * This converter transforms screening-related data into standardized FHIR
 * Procedure resources. It handles the creation and population of procedure
 * details
 * including status, codes, references, and metadata.
 * </p>
 *
 * @author TechBD Healthcare
 * @version 1.0
 * @since 2024-03-17
 */
@Component
@Order(7)
public class ProcedureConverter extends BaseConverter {

    // Constants
    private static final Logger LOG = LoggerFactory.getLogger(ProcedureConverter.class);
    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

    // FHIR-specific constants
    private static final String PROCEDURE_BASE_URL = "http://shinny.org/us/ny/hrsn/Procedure/";
    private static final String SNOMED_SYSTEM = "http://snomed.info/sct";
    private static final String DEFAULT_SYSTEM = "urn:oid:2.16.840.1.113883.6.285";

    // Clinical codes and displays
    private static final String SOCIAL_SERVICE_CODE = "410606002";
    private static final String SOCIAL_SERVICE_DISPLAY = "Social service procedure";
    private static final String DEFAULT_PROCEDURE_DISPLAY = "SDOH Assessment";

    /**
     * Returns the resource type for this converter.
     *
     * @return ResourceType.Procedure
     */
    @Override
    public ResourceType getResourceType() {
        return ResourceType.Procedure;
    }

    /**
     * Converts screening data into a FHIR Procedure resource.
     *
     * @param bundle                   The FHIR Bundle to which the procedure
     *                                 belongs
     * @param demographicData          Patient demographic information
     * @param qeAdminData              Administrative data
     * @param screeningProfileData     Screening profile information
     * @param screeningObservationData List of screening observations
     * @param interactionId            Unique identifier for the interaction
     * @param idsGenerated             Map of generated resource IDs
     * @param baseFHIRUrl              Base URL for FHIR resources
     * @return List of BundleEntryComponents containing the procedure
     */
    @Override
    public List<BundleEntryComponent> convert(
            Bundle bundle,
            DemographicData demographicData,
            QeAdminData qeAdminData,
            ScreeningProfileData screeningProfileData,
            List<ScreeningObservationData> screeningObservationData,
            String interactionId,
            Map<String, String> idsGenerated,
            String baseFHIRUrl) {

        Procedure procedure = createProcedure(screeningProfileData, baseFHIRUrl);
        populateProcedureDetails(procedure, screeningProfileData, screeningObservationData, idsGenerated);
        BundleEntryComponent entry = createBundleEntry(procedure);
        return List.of(entry);
    }

    private Procedure createProcedure(ScreeningProfileData profileData, String baseFHIRUrl) {
        Procedure procedure = new Procedure();
        setMeta(procedure, baseFHIRUrl);
        Meta meta = procedure.getMeta();
        meta.setLastUpdated(new DateTime().toDate());
        procedure.setMeta(meta);

        // Generate a unique ID using CsvConversionUtil.sha256
        String uniqueId = CsvConversionUtil.sha256(
                generateUniqueId(profileData.getEncounterId(), profileData.getFacilityId(),
                        profileData.getProcedureCode()));
        procedure.setId(uniqueId);

        return procedure;
    }

    private String generateUniqueId(String encounterId, String facilityId, String procedureCode) {
        return encounterId + "_" + facilityId + "_" + procedureCode;
    }

    private BundleEntryComponent createBundleEntry(Procedure procedure) {
        String fullUrl = PROCEDURE_BASE_URL + procedure.getId();

        return new BundleEntryComponent()
                .setFullUrl(fullUrl)
                .setResource(procedure)
                .setRequest(new Bundle.BundleEntryRequestComponent()
                        .setMethod(HTTPVerb.POST)
                        .setUrl(fullUrl));
    }

    // Private helper methods for populating procedure details

    private void populateProcedureDetails(
            Procedure procedure,
            ScreeningProfileData profileData,
            List<ScreeningObservationData> observations,
            Map<String, String> idsGenerated) {

        populateProcedureStatus(procedure, profileData);
        populateProcedureCode(procedure, profileData);
        populateReferences(procedure, idsGenerated);
        populatePerformedPeriod(procedure, observations);
        populateLastUpdated(procedure);
    }

    private void populateProcedureStatus(Procedure procedure, ScreeningProfileData profileData) {
        String statusCode = profileData.getProcedureStatusCode();
        procedure.setStatus(StringUtils.isNotEmpty(statusCode)
                ? Procedure.ProcedureStatus.fromCode(statusCode)
                : Procedure.ProcedureStatus.COMPLETED);
    }

    private void populateProcedureCode(Procedure procedure, ScreeningProfileData profileData) {
        CodeableConcept code = new CodeableConcept();

        // Add coding if available from CSV data
        if (StringUtils.isNotEmpty(profileData.getProcedureCode())) {
            code.addCoding(new Coding()
                    .setSystem(StringUtils.defaultIfEmpty(
                            profileData.getProcedureCodeSystem(),
                            DEFAULT_SYSTEM))
                    .setCode(profileData.getProcedureCode())
                    .setDisplay(StringUtils.defaultIfEmpty(
                            profileData.getProcedureCodeDescription(),
                            DEFAULT_PROCEDURE_DISPLAY)));
        }

        // Set text from CSV data
        code.setText(StringUtils.defaultIfEmpty(
                profileData.getProcedureCodeDescription(),
                DEFAULT_PROCEDURE_DISPLAY));

        procedure.setCode(code);
    }

    private void populateReferences(Procedure procedure, Map<String, String> idsGenerated) {
        // Set patient reference
        if (idsGenerated.containsKey(CsvConstants.PATIENT_ID)) {
            procedure.setSubject(new Reference("Patient/" +
                    idsGenerated.get(CsvConstants.PATIENT_ID)));
        }

        // Set encounter reference
        if (idsGenerated.containsKey(CsvConstants.ENCOUNTER_ID)) {
            procedure.setEncounter(new Reference("Encounter/" +
                    idsGenerated.get(CsvConstants.ENCOUNTER_ID)));
        }
    }

    private void populatePerformedPeriod(Procedure procedure,
            List<ScreeningObservationData> observations) {
        if (observations == null || observations.isEmpty()) {
            return;
        }

        ScreeningObservationData data = observations.get(0);
        String startTime = data.getScreeningStartDateTime();
        String endTime = data.getScreeningEndDateTime();

        if (StringUtils.isNotEmpty(startTime) && StringUtils.isNotEmpty(endTime)) {
            Period period = new Period()
                    .setStart(DateUtil.convertStringToDate(startTime))
                    .setEnd(DateUtil.convertStringToDate(endTime));
            procedure.setPerformed(period);
        } else if (StringUtils.isNotEmpty(startTime)) {
            procedure.setPerformed(new DateTimeType(
                    DateUtil.convertStringToDate(startTime)));
        }
    }

    private void populateLastUpdated(Procedure procedure) {
        if (procedure.getMeta() == null) {
            procedure.setMeta(new Meta());
        }
        procedure.getMeta().setLastUpdated(new DateTime().toDate());
    }
}
