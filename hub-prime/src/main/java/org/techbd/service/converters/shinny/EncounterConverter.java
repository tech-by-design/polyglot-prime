package org.techbd.service.converters.shinny;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.util.DateUtil;

/**
 * Converts data into a FHIR Encounter resource.
 */
@Component
public class EncounterConverter extends BaseConverter {
    private static final Logger LOG = LoggerFactory.getLogger(EncounterConverter.class.getName());

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
    public List<BundleEntryComponent>  convert(Bundle bundle,DemographicData demographicData,QeAdminData qeAdminData ,
    ScreeningProfileData screeningProfileData ,List<ScreeningObservationData> screeningObservationData,String interactionId) {

        Encounter encounter = new Encounter();
        setMeta(encounter);

        // Set Encounter ID
        // encounter.setId(generateUniqueId(interactionId));

        // Set Meta Data
        Meta meta = encounter.getMeta();
        // meta.setLastUpdated(getLastUpdatedDate(qrAdminData));

        // // Set encounter status
        // populateEncounterStatus(encounter, screeningResourceData);

        // // Set encounter class
        // populateEncounterClass(encounter, screeningResourceData);

        // // Set encounter type
        // populateEncounterType(encounter, screeningResourceData);

        // // Set encounter period
        // populateEncounterPeriod(encounter, screeningResourceData);

        // // Set patient reference
        // populatePatientReference(encounter, screeningResourceData);

        // // Set location
        // populateLocationReference(encounter, screeningResourceData);

        // Wrap the Encounter resource in a BundleEntryComponent
        BundleEntryComponent bundleEntryComponent = new BundleEntryComponent();
        bundleEntryComponent.setResource(encounter);
        return List.of(bundleEntryComponent);
    }

    // private void populatePatientReference(Encounter encounter, ScreeningProfileData screeningResourceData) {
    //     if (screeningResourceData != null && screeningResourceData.getPatientMrIdValue() != null) {
    //         encounter.setSubject(new Reference("Patient/" + screeningResourceData.getPatientMrIdValue()));
    //     }
    // }

    // private static void populateEncounterClass(Encounter encounter, ScreeningProfileData data) {
    //     if (data.getEncounterClassCode() != null) {
    //         Coding encounterClass = new Coding();
    //         encounterClass.setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode");
    //         encounterClass.setCode(data.getEncounterClassCode());
    //         encounter.setClass_(encounterClass);
    //     }
    // }

    // private static void populateEncounterType(Encounter encounter, ScreeningProfileData data) {
    //     if (data.getEncounterTypeCode() != null || data.getEncounterTypeCodeDescription() != null) {
    //         CodeableConcept encounterType = new CodeableConcept();

    //         if (data.getEncounterTypeCode() != null) {
    //             Coding coding = new Coding();
    //             coding.setCode(data.getEncounterTypeCode());
    //             encounterType.addCoding(coding);
    //         }

    //         if (data.getEncounterTypeCodeDescription() != null) {
    //             encounterType.setText(data.getEncounterTypeCodeDescription());
    //         }

    //         encounter.getType().add(encounterType);
    //     }
    // }

    // private void populateEncounterStatus(Encounter encounter, ScreeningProfileData screeningResourceData) {
    //     if (screeningResourceData != null && screeningResourceData.getEncounterStatusCode() != null) {
    //         encounter.setStatus(Encounter.EncounterStatus.fromCode(screeningResourceData.getEncounterStatusCode()));
    //     } else {
    //         encounter.setStatus(Encounter.EncounterStatus.UNKNOWN);
    //     }
    // }

    // private void populateEncounterPeriod(Encounter encounter, ScreeningProfileData screeningResourceData) {
    //     if (screeningResourceData != null) {
    //         String startDateTime = screeningResourceData.getEncounterStartTime();
    //         String endDateTime = screeningResourceData.getEncounterEndTime();

    //         if (startDateTime != null) {
    //             encounter.getPeriod().setStart(DateUtil.convertStringToDate(startDateTime));
    //         }

    //         if (endDateTime != null) {
    //             encounter.getPeriod().setEnd(DateUtil.convertStringToDate(endDateTime));
    //         }
    //     }
    // }

    // private void populateLocationReference(Encounter encounter, ScreeningProfileData screeningResourceData) {
    //     if (screeningResourceData != null && screeningResourceData.getLocationName() != null) {
    //         encounter.addLocation(new Encounter.EncounterLocationComponent()
    //                 .setLocation(new Reference("Location/" + screeningResourceData.getLocationName())));
    //     }
    // }

    // /**
    //  * Generate a unique ID for the encounter based on its data.
    //  *
    //  * @param interactionId The interaction ID used to generate a unique identifier.
    //  * @return A unique ID for the encounter.
    //  */
    // private String generateUniqueId(String interactionId) {
    //     return "Encounter-" + interactionId;
    // }

    // /**
    //  * Get the last updated date for the encounter based on its data from
    //  * QeAdminData.
    //  *
    //  * @param qrAdminData The QeAdminData object containing the encounter's last
    //  *                    updated date.
    //  * @return The last updated date.
    //  */
    // private Date getLastUpdatedDate(QeAdminData qrAdminData) {
    //     if (qrAdminData != null && qrAdminData.getFacilityLastUpdated() != null
    //             && !qrAdminData.getFacilityLastUpdated().isEmpty()) {
    //         try {
    //             SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    //             return dateFormat.parse(qrAdminData.getFacilityLastUpdated());
    //         } catch (ParseException e) {
    //             LOG.error("Error parsing last updated date", e);
    //         }
    //     }
    //     return new Date();
    // }
}
