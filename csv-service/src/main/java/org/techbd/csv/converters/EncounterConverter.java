package org.techbd.csv.converters;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.corelib.config.CoreUdiPrimeJpaConfig;
import org.techbd.corelib.util.AppLogger;
import org.techbd.corelib.util.DateUtil;
import org.techbd.corelib.util.TemplateLogger;
import org.techbd.csv.model.DemographicData;
import org.techbd.csv.model.QeAdminData;
import org.techbd.csv.model.ScreeningObservationData;
import org.techbd.csv.model.ScreeningProfileData;
import org.techbd.csv.service.CodeLookupService;
import org.techbd.csv.util.CsvConstants;
import org.techbd.csv.util.CsvConversionUtil;

/**
 * Converts data into a FHIR Encounter resource.
 */
@Component
@Order(5)
public class EncounterConverter extends BaseConverter {
    private final TemplateLogger LOG;
    public EncounterConverter(CodeLookupService codeLookupService,final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig, AppLogger appLogger) {
        super(codeLookupService,coreUdiPrimeJpaConfig);
        this.LOG = appLogger.getLogger(EncounterConverter.class);
    }


    /**
     * Returns the resource type associated with this converter.
     *
     * @return The FHIR ResourceType.Encounter enum.
     */
    @Override
    public ResourceType getResourceType() {
        return ResourceType.Encounter;
    }

    /**
     * Converts encounter-related data into a FHIR Encounter resource wrapped in a
     * BundleEntryComponent.
     *
     * @param bundle                The FHIR Bundle to which the encounter data is
     *                              related.
     * @param demographicData       The demographic data related to the patient.
     * @param screeningDataList     The list of screening data (if required for the
     *                              encounter context).
     * @param qrAdminData           The administrative data related to the patient
     *                              or organization.
     * @param screeningResourceData Additional screening resource data (if needed).
     * @param interactionId         The interaction ID used for tracking or
     *                              referencing the conversion.
     * @return A BundleEntryComponent containing the converted FHIR Encounter
     *         resource.
     */
    @Override
    public List<BundleEntryComponent> convert(Bundle bundle, DemographicData demographicData, QeAdminData qeAdminData,
            ScreeningProfileData screeningProfileData, List<ScreeningObservationData> screeningObservationData,
            String interactionId, Map<String, String> idsGenerated,String baseFHIRUrl) {

        Encounter encounter = new Encounter();
        setMeta(encounter,baseFHIRUrl);

        encounter.setId(CsvConversionUtil.sha256(screeningProfileData.getEncounterId()));
        
        idsGenerated.put(CsvConstants.ENCOUNTER_ID, encounter.getId());

        String fullUrl = "http://shinny.org/us/ny/hrsn/Encounter/" + encounter.getId();

        Meta meta = encounter.getMeta();

        if (StringUtils.isNoneEmpty(screeningProfileData.getEncounterLastUpdated())) {
            meta.setLastUpdated(DateUtil.parseDate(screeningProfileData.getEncounterLastUpdated()));
        } else if (StringUtils.isNotEmpty(screeningProfileData.getEncounterStartDatetime())) {
            meta.setLastUpdated(DateUtil.parseDate(screeningProfileData.getEncounterStartDatetime()));
        } else {
            meta.setLastUpdated(new java.util.Date());
        }

        populateEncounterIdentifier(encounter, screeningProfileData);

        populateEncounterStatus(encounter, screeningProfileData, interactionId);

        populateEncounterClass(encounter, screeningProfileData, interactionId);

        populateEncounterType(encounter, screeningProfileData, interactionId);

        populateEncounterPeriod(encounter, screeningProfileData);

        populatePatientReference(encounter, idsGenerated);

        //populateLocationReference(encounter, screeningProfileData, idsGenerated);
        
        // Narrative text = new Narrative();
        // text.setStatus(NarrativeStatus.GENERATED);
        // encounter.setText(text);
        BundleEntryComponent bundleEntryComponent = new BundleEntryComponent();
        bundleEntryComponent.setFullUrl(fullUrl);
        bundleEntryComponent.setRequest(new Bundle.BundleEntryRequestComponent().setMethod(HTTPVerb.POST)
                .setUrl("http://shinny.org/us/ny/hrsn/Encounter/" + encounter.getId()));
        bundleEntryComponent.setResource(encounter);

        return List.of(bundleEntryComponent);
    }

    private void populatePatientReference(Encounter encounter, Map<String, String> idsGenerated) {
        encounter.setSubject(new Reference("Patient/" + idsGenerated.get(CsvConstants.PATIENT_ID)));
    }

    private void populateEncounterClass(Encounter encounter, ScreeningProfileData data, String interactionId) {
        if (StringUtils.isNotEmpty(data.getEncounterClassCode())) {
            Coding encounterClass = new Coding();
            String encounterClassCode = fetchCode(data.getEncounterClassCode(), CsvConstants.ENCOUNTER_CLASS_CODE, interactionId);
            String encounterClassDescription = fetchDisplay(encounterClassCode, data.getEncounterClassCodeDescription(), CsvConstants.ENCOUNTER_CLASS_CODE, interactionId);
            encounterClass.setSystem(fetchSystem(encounterClassCode, data.getEncounterClassCodeSystem(), CsvConstants.ENCOUNTER_CLASS_CODE, interactionId));
            encounterClass.setCode(encounterClassCode);
            encounterClass.setDisplay(encounterClassDescription);
            encounter.setClass_(encounterClass);
        }
    }

    private void populateEncounterType(Encounter encounter, ScreeningProfileData data, String interactionId) {
        if (StringUtils.isNotEmpty(data.getEncounterTypeCode()) || StringUtils.isNotEmpty(data.getEncounterTypeCodeDescription())) {
            CodeableConcept encounterType = new CodeableConcept();

            if (data.getEncounterTypeCode() != null) {
                String encounterDisplay = fetchDisplay(data.getEncounterTypeCode(), data.getEncounterTypeCodeDescription(), CsvConstants.ENCOUNTER_TYPE_CODE, interactionId);
                Coding coding = new Coding();
                coding.setCode(data.getEncounterTypeCode());
                coding.setSystem(fetchSystem(data.getEncounterTypeCode(), data.getEncounterTypeCodeSystem(), CsvConstants.ENCOUNTER_TYPE_CODE, interactionId));
                coding.setDisplay(encounterDisplay);
                encounterType.addCoding(coding);
                encounterType.setText(encounterDisplay);
            }
            encounter.getType().add(encounterType);
        }
    }

    private void populateEncounterStatus(Encounter encounter, ScreeningProfileData screeningResourceData, String interactionId) {
        if (screeningResourceData != null && StringUtils.isNotEmpty(screeningResourceData.getEncounterStatusCode())) {
            encounter.setStatus(Encounter.EncounterStatus.fromCode(fetchCode(screeningResourceData.getEncounterStatusCode(), CsvConstants.ENCOUNTER_STATUS_CODE, interactionId)));
        } else {
            encounter.setStatus(Encounter.EncounterStatus.UNKNOWN);
        }
    }

    private void populateEncounterPeriod(Encounter encounter, ScreeningProfileData screeningResourceData) {
        if (screeningResourceData != null) {
            String startDateTime = screeningResourceData.getEncounterStartDatetime();
            String endDateTime = screeningResourceData.getEncounterEndDatetime();

            if (startDateTime != null) {
                encounter.getPeriod().setStart(DateUtil.convertStringToDate(startDateTime));
            }

            if (endDateTime != null) {
                encounter.getPeriod().setEnd(DateUtil.convertStringToDate(endDateTime));
            }
        }
    }

    private void populateLocationReference(Encounter encounter, ScreeningProfileData screeningResourceData,
            Map<String, String> idsGenerated) {
        if (screeningResourceData != null) {
            encounter.addLocation(new Encounter.EncounterLocationComponent()
                    .setLocation(new Reference("Location/" + screeningResourceData.getEncounterLocation())));
        }
    }
    
    private static void populateEncounterIdentifier(Encounter encounter, ScreeningProfileData data) {
        if (StringUtils.isNotEmpty(data.getEncounterId())) {
            Identifier identifier = new Identifier();
            identifier.setSystem(data.getEncounterIdSystem());
            identifier.setValue(data.getEncounterId());
            encounter.addIdentifier(identifier);
        }
    }
}
