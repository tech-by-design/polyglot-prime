package org.techbd.service.converters.shinny;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Consent.ConsentProvisionType;
import org.hl7.fhir.r4.model.Consent.ConsentState;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Narrative.NarrativeStatus;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.util.CsvConstants;
import org.techbd.util.CsvConversionUtil;
import org.techbd.util.DateUtil;

/**
 * Converts data into a FHIR Consent resource.
 */
@Component
@Order(4)
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
    public List<BundleEntryComponent> convert(Bundle bundle, DemographicData demographicData, QeAdminData qeAdminData,
            ScreeningProfileData screeningProfileData, List<ScreeningObservationData> screeningObservationData,
            String interactionId, Map<String, String> idsGenerated, String baseFHIRUrl) {

        Consent consent = new Consent();
        setMeta(consent, baseFHIRUrl);

        consent.setId(CsvConversionUtil.sha256(UUID.randomUUID().toString()));

        Meta meta = consent.getMeta();
        
        meta.setLastUpdated(DateUtil.convertStringToDate(screeningProfileData.getConsentDateTime()));

        populateConsentStatusAndScope(consent, screeningProfileData);

        populateConsentCategory(consent, screeningProfileData);

        populatePatientReference(consent, idsGenerated);

        populateConsentDateTime(consent, screeningProfileData);

        populateOrganizationReference(consent, idsGenerated);

        populateConsentState(consent, screeningProfileData);

        // // TODO:
        // populateSourceReference(consent, screeningProfileData);

        populateSourceAttachment(consent);

        populateConsentPolicy(consent, screeningProfileData);
        
        String fullUrl = "http://shinny.org/us/ny/hrsn/Consent/" + consent.getId();
        BundleEntryComponent bundleEntryComponent = new BundleEntryComponent();
        bundleEntryComponent.setFullUrl(fullUrl);
        bundleEntryComponent.setRequest(new Bundle.BundleEntryRequestComponent().setMethod(HTTPVerb.POST)
                .setUrl("http://shinny.org/us/ny/hrsn/Consent/" + consent.getId()));
        bundleEntryComponent.setResource(consent);
        return List.of(bundleEntryComponent);
    }

    private static void populateConsentStatusAndScope(Consent consent, ScreeningProfileData data) {
        consent.setStatus(Consent.ConsentState.ACTIVE);

        CodeableConcept scope = new CodeableConcept();
        Coding coding = new Coding();
        coding.setCode("treatment"); // TODO : remove static reference
        coding.setSystem("http://terminology.hl7.org/CodeSystem/consentscope");
        coding.setDisplay("Treatment");
        scope.addCoding(coding);
        scope.setText("treatment"); // TODO : remove static reference

        consent.setScope(scope);

        // Narrative text = new Narrative();
        // text.setStatus(NarrativeStatus.GENERATED);
        // consent.setText(text);
    }

    private static void populateConsentCategory(Consent consent, ScreeningProfileData data) {
        List<CodeableConcept> categories = new ArrayList<>();

        CodeableConcept loincCategory = new CodeableConcept();
        Coding loincCoding = new Coding();
        loincCoding.setSystem("http://loinc.org");
        loincCoding.setCode("59284-0"); // TODO : remove static reference
        loincCoding.setDisplay("Consent Document"); // TODO : remove static reference

        loincCategory.addCoding(loincCoding);
        categories.add(loincCategory);

        CodeableConcept hl7Category = new CodeableConcept();
        Coding hl7Coding = new Coding();
        hl7Coding.setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode");
        hl7Coding.setCode("IDSCL"); // TODO : remove static reference

        hl7Category.addCoding(hl7Coding);
        categories.add(hl7Category);

        consent.setCategory(categories);
    }

    private void populatePatientReference(Consent consent, Map<String, String> idsGenerated) {
        consent.getPatient().setReference("Patient/" + idsGenerated.get(CsvConstants.PATIENT_ID));
    }

    private void populateOrganizationReference(Consent consent, Map<String, String> idsGenerated) {
        consent.getOrganizationFirstRep()
                .setReference("Organization/" + idsGenerated.get(CsvConstants.ORGANIZATION_ID));
    }

    private void populateConsentState(Consent consent, ScreeningProfileData screeningResourceData) {
        if (screeningResourceData != null) {
            consent.setStatus(ConsentState.valueOf(screeningResourceData.getConsentStatus().toUpperCase()));
        }
    }

    // TODO: Need to change the code using the ScreeningResourceData, now it is
    // static
    // private void populateSourceReference(Consent consent, ScreeningResourceData
    // screeningResourceData) {
    // consent.setSourceReference("QuestionnaireResponse/ConsentQuestionnaireResponse");
    // }

    public static void populateConsentDateTime(Consent consent, ScreeningProfileData screeningResourceData) {
        String consentDateTime = screeningResourceData.getConsentDateTime();
        consent.setDateTime(DateUtil.convertStringToDate(consentDateTime));
    }

    private void populateSourceAttachment(Consent consent) {
        Attachment attachment = new Attachment();
        attachment.setContentType("application/pdf");
        attachment.setLanguage("en");
        consent.setSource(attachment);
    }
    
    private void populateConsentPolicy(Consent consent, ScreeningProfileData screeningResourceData) {
        Consent.ConsentPolicyComponent policyComponent = new Consent.ConsentPolicyComponent();
        policyComponent.setAuthority("urn:uuid:d1eaac1a-22b7-4bb6-9c62-cc95d6fdf1a5");
        consent.getPolicy().add(policyComponent);
    }
    
}
