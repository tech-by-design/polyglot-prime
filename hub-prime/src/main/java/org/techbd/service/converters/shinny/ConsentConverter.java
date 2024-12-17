package org.techbd.service.converters.shinny;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Consent.ConsentProvisionType;
import org.hl7.fhir.r4.model.Consent.ConsentState;
import org.hl7.fhir.r4.model.Meta;
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
 * Converts data into a FHIR Consent resource.
 */
@Component
public class ConsentConverter extends BaseConverter {
    private static final Logger LOG = LoggerFactory.getLogger(ConsentConverter.class.getName());

    /**
     * Returns the resource type associated with this converter.
     *
     * @return The FHIR ResourceType.Consent enum.
     */
    @Override
    public ResourceType getResourceType() {
        return ResourceType.Consent;
    }

    /**
     * Converts consent-related data into a FHIR Consent resource wrapped in a
     * BundleEntryComponent.
     *
     * @param bundle                The FHIR Bundle to which the consent data is
     *                              related.
     * @param demographicData       The demographic data related to the patient.
     * @param screeningDataList     The list of screening data (if required for the
     *                              consent context).
     * @param qrAdminData           The administrative data related to the patient
     *                              or
     *                              organization.
     * @param screeningResourceData Additional screening resource data (if needed).
     * @param interactionId         The interaction ID used for tracking or
     *                              referencing
     *                              the conversion.
     * @return A BundleEntryComponent containing the converted FHIR Consent
     *         resource.
     */
    @Override
    public List<BundleEntryComponent>  convert(Bundle bundle,DemographicData demographicData,QeAdminData qeAdminData ,
    ScreeningProfileData screeningProfileData ,List<ScreeningObservationData> screeningObservationData,String interactionId) {

        Consent consent = new Consent();
        setMeta(consent);

        // // Set Consent ID
        // consent.setId(generateUniqueId(interactionId));

        // // Set Meta Data
        // Meta meta = consent.getMeta();
        // meta.setLastUpdated(getLastUpdatedDate(screeningResourceData));

        // // Set consent scope
        // populateConsentStatusAndScope(consent, screeningResourceData);

        // // Set consent catehory
        // populateConsentCategory(consent, screeningResourceData);

        // // Set patient reference
        // populatePatientReference(consent, screeningResourceData);

        // // Set consent date time
        // populateConsentDateTime(consent, screeningResourceData);

        // // Set organization reference
        // populateOrganizationReference(consent, screeningResourceData);

        // // Set state of consent
        // populateConsentState(consent, screeningResourceData);

        // // Set source reference of the consent
        // // TODO:
        // // populateSourceReference(consent, screeningResourceData);

        // // Set policy of the consent
        // populateConsentPolicy(consent, screeningResourceData);

        // // Set provisions of the consent
        // populateConsentProvision(consent, screeningResourceData);

        // Wrap the Consent resource in a BundleEntryComponent
        BundleEntryComponent bundleEntryComponent = new BundleEntryComponent();
        bundleEntryComponent.setResource(consent);
        return List.of(bundleEntryComponent);
    }

    // private static void populateConsentStatusAndScope(Consent consent, ScreeningProfileData data) {
    //     consent.setStatus(Consent.ConsentState.ACTIVE);

    //     CodeableConcept scope = new CodeableConcept();
    //     Coding coding = new Coding();
    //     coding.setCode(data.getConsentScopeCode());
    //     scope.addCoding(coding);
    //     scope.setText(data.getConsentScopeText());

    //     consent.setScope(scope);
    // }

    // private static void populateConsentCategory(Consent consent, ScreeningProfileData data) {
    //     // Create a list of CodeableConcept to hold multiple categories
    //     List<CodeableConcept> categories = new ArrayList<>();

    //     // Add the first category (LOINC)
    //     CodeableConcept loincCategory = new CodeableConcept();
    //     Coding loincCoding = new Coding();
    //     loincCoding.setSystem("http://loinc.org");
    //     loincCoding.setCode(data.getConsentCategoryLoincCode());
    //     loincCoding.setDisplay(data.getConsentCategoryLoincDisplay());

    //     loincCategory.addCoding(loincCoding);
    //     categories.add(loincCategory);

    //     // Add the second category (HL7 ActCode)
    //     CodeableConcept hl7Category = new CodeableConcept();
    //     Coding hl7Coding = new Coding();
    //     hl7Coding.setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode");
    //     hl7Coding.setCode(data.getConsentCategoryIdsclCode());

    //     hl7Category.addCoding(hl7Coding);
    //     categories.add(hl7Category);

    //     // Set the categories in the consent object
    //     consent.setCategory(categories);
    // }

    // private void populatePatientReference(Consent consent, ScreeningProfileData screeningResourceData) {
    //     if (screeningResourceData != null && screeningResourceData.getPatientMrIdValue() != null) {
    //         consent.getPatient().setReference("Patient/" + screeningResourceData.getPatientMrIdValue());
    //     }
    // }

    // private void populateOrganizationReference(Consent consent, ScreeningProfileData screeningResourceData) {
    //     if (screeningResourceData != null && screeningResourceData.getFacilityId() != null) {
    //         consent.getOrganizationFirstRep().setReference("Organization/" + screeningResourceData.getFacilityId());
    //     }
    // }

    // private void populateConsentState(Consent consent, ScreeningProfileData screeningResourceData) {
    //     if (screeningResourceData != null && screeningResourceData.getConsentStatus() != null) {
    //         consent.setStatus(ConsentState.valueOf(screeningResourceData.getConsentStatus().toUpperCase()));
    //     }
    // }

    // // TODO: Need to change the code using the ScreeningResourceData, now it is
    // // static
    // // private void populateSourceReference(Consent consent, ScreeningResourceData
    // // screeningResourceData) {
    // // consent.setSourceReference("QuestionnaireResponse/ConsentQuestionnaireResponse");
    // // }

    // private void populateConsentPolicy(Consent consent, ScreeningProfileData screeningResourceData) {
    //     if (screeningResourceData == null || screeningResourceData.getConsentPolicyAuthority() == null) {
    //         return;
    //     }
    //     Consent.ConsentPolicyComponent policy = new Consent.ConsentPolicyComponent();
    //     policy.setAuthority(screeningResourceData.getConsentPolicyAuthority());

    //     consent.addPolicy(policy);
    // }

    // private void populateConsentProvision(Consent consent, ScreeningProfileData screeningResourceData) {
    //     if (screeningResourceData == null || screeningResourceData.getConsentProvisionType() == null) {
    //         return;
    //     }
    //     Consent.provisionComponent provision = new Consent.provisionComponent();
    //     provision.setType(ConsentProvisionType.PERMIT); // Directly set to "permit"
    //     consent.setProvision(provision);
    // }

    // public static void populateConsentDateTime(Consent consent, ScreeningProfileData screeningResourceData) {
    //     String consentDateTime = screeningResourceData.getConsentDateTime();
    //     consent.setDateTime(DateUtil.convertStringToDate(consentDateTime));
    // }

    // /**
    //  * Generate a unique ID for the consent based on its data.
    //  *
    //  * @param interactionId The interaction ID used to generate a unique identifier.
    //  * @return A unique ID for the consent.
    //  */
    // private String generateUniqueId(String interactionId) {
    //     // Example unique ID generation logic
    //     return "Consent-" + interactionId;
    // }

    // /**
    //  * Get the last updated date for the consent based on its data from QeAdminData.
    //  *
    //  * @param qrAdminData The QeAdminData object containing the consent's last
    //  *                    updated date.
    //  * @return The last updated date.
    //  */
    // private Date getLastUpdatedDate(ScreeningProfileData screeningResourceData) {
    //     if (screeningResourceData != null && screeningResourceData.getConsentLastUpdated() != null
    //             && !screeningResourceData.getConsentLastUpdated().isEmpty()) {
    //         try {
    //             SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    //             return dateFormat.parse(screeningResourceData.getConsentLastUpdated());
    //         } catch (ParseException e) {
    //             LOG.error("Error parsing last updated date", e);
    //         }
    //     }
    //     return new Date();
    // }
}
