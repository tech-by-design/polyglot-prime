package org.techbd.converters.csv;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.csv.CodeLookupService;
import org.techbd.util.DateUtil;
import org.techbd.util.csv.CsvConstants;
import org.techbd.util.csv.CsvConversionUtil;
import org.joda.time.DateTime;
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

    public ProcedureConverter(CodeLookupService codeLookupService) {
        super(codeLookupService);
    }

    // Constants
    private static final Logger LOG = LoggerFactory.getLogger(ProcedureConverter.class);
    

    // FHIR-specific constants
    private static final String PROCEDURE_BASE_URL = "http://shinny.org/us/ny/hrsn/Procedure/";
   
    // Clinical displays
   
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
        if (StringUtils.isNotEmpty(screeningProfileData.getProcedureCode())) {
            Procedure procedure = createProcedure(screeningProfileData, baseFHIRUrl);
            populateProcedureDetails(procedure, screeningProfileData, screeningObservationData, idsGenerated, interactionId);
            BundleEntryComponent entry = createBundleEntry(procedure);
            return List.of(entry);
        } else {
            LOG.info(
                    "ProcedureConverter:: No data for procedure, observation will not be created for interaction id :{} ",
                    interactionId);
            return List.of();
        }
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
            Map<String, String> idsGenerated,
            String interactionId) {

        populateProcedureStatus(procedure, profileData, interactionId);
        populateProcedureCode(procedure, profileData, interactionId);
        populateReferences(procedure, idsGenerated);
        populatePerformedPeriod(procedure, observations);
        populateLastUpdated(procedure);
    }

    private void populateProcedureStatus(Procedure procedure, ScreeningProfileData profileData, String interactionId) {
        String statusCode = fetchCode(profileData.getProcedureStatusCode(), CsvConstants.PROCEDURE_STATUS_CODE, interactionId);
        // procedure.setStatus(StringUtils.isNotEmpty(statusCode)
        //         ? Procedure.ProcedureStatus.fromCode(statusCode)
        //         : //Procedure.ProcedureStatus.COMPLETED);
        if (StringUtils.isNotEmpty(statusCode)) {
            procedure.setStatus(Procedure.ProcedureStatus.fromCode(statusCode));
        }
    
    }

    private void populateProcedureCode(Procedure procedure, ScreeningProfileData profileData, String interactionId) {
        CodeableConcept code = new CodeableConcept();

        // Add coding if available from CSV data
        if (StringUtils.isNotEmpty(profileData.getProcedureCode())) {
            code.addCoding(new Coding()
                    .setSystem(fetchSystem(profileData.getProcedureCodeSystem(), CsvConstants.PROCEDURE_CODE, interactionId))
                    .setCode(profileData.getProcedureCode())
                    .setDisplay(profileData.getProcedureCodeDescription()));
        }

        // Set text from CSV data
        code.setText(profileData.getProcedureCodeDescription());

          // TODO: Add modifier extension if available
        // if (StringUtils.isNotEmpty(profileData.getProcedureCodeModifier())) {
        // Extension modifierExtension = new Extension()
        // .setUrl("http://shinny.org/fhir/StructureDefinition/procedure-code-modifier")

        // .setValue(new StringType(profileData.getProcedureCodeModifier()));
        // code.addExtension(modifierExtension);
        // }
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
