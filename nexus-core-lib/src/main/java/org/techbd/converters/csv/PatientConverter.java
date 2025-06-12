package org.techbd.converters.csv;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Patient.PatientCommunicationComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
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

@Component
@Order(2)
public class PatientConverter extends BaseConverter {

    public PatientConverter(CodeLookupService codeLookupService) {
        super(codeLookupService);
    }
    private static final Logger LOG = LoggerFactory.getLogger(PatientConverter.class.getName());

    /**
     * Returns the resource type associated with this converter.
     *
     * @return The FHIR ResourceType.Patient enum.
     */
    public ResourceType getResourceType() {
        return ResourceType.Patient;
    }

    /**
     * Converts demographic and screening data into a FHIR Patient resource
     * wrapped in a BundleEntryComponent.
     *
     * @param bundle            The FHIR Bundle to which the patient data is
     *                          related.
     * @param demographicData   The demographic data of the patient.
     * @param screeningDataList The list of screening data relevant to the patient.
     * @param qrAdminData       The administrative data related to the patient.
     * @param interactionId     The interaction ID used for tracking or referencing
     *                          the conversion.
     * @return A BundleEntryComponent containing the converted FHIR Patient
     *         resource.
     */
    @Override
    public List<BundleEntryComponent> convert(Bundle bundle, DemographicData demographicData, QeAdminData qeAdminData,
            ScreeningProfileData screeningProfileData, List<ScreeningObservationData> screeningObservationData,
            String interactionId, Map<String, String> idsGenerated,String baseFHIRUrl) {
        LOG.info("PatientConverter :: convert  BEGIN for transaction id :{}", interactionId);
        Patient patient = new Patient();
        setMeta(patient,baseFHIRUrl);
        patient.setId(CsvConversionUtil
                .sha256(generateUniqueId(screeningProfileData.getEncounterId(), qeAdminData.getFacilityId(),
                        demographicData.getPatientMrIdValue())));
        idsGenerated.put(CsvConstants.PATIENT_ID, patient.getId());
        String fullUrl = "http://shinny.org/us/ny/hrsn/Patient/" + patient.getId();
        Meta meta = patient.getMeta();
        if (StringUtils.isNotEmpty(demographicData.getPatientLastUpdated())) {
            meta.setLastUpdated(DateUtil.parseDate(demographicData.getPatientLastUpdated())); // max date available in all
        } else {                                                                               // screening records
            meta.setLastUpdated(new java.util.Date());
        }                                                                                         
        patient.setLanguage("en");
        populatePatientWithExtensions(patient, demographicData, interactionId);
        populateMrIdentifier(patient, demographicData,qeAdminData, idsGenerated );
        populateMaIdentifier(patient, demographicData);
        populateSsnIdentifier(patient, demographicData);
        populatePatientName(patient, demographicData);
        populateAdministrativeSex(patient, demographicData, interactionId);
        populateBirthDate(patient, demographicData);
        populatePhone(patient, demographicData, interactionId);
        populateAddress(patient, demographicData, interactionId);
        populatePreferredLanguage(patient, demographicData, interactionId);
       // populatePatientRelationContact(patient, demographicData);

        // populatePatientText(patient, demographicData);
        BundleEntryComponent bundleEntryComponent = new BundleEntryComponent();
        bundleEntryComponent.setFullUrl(fullUrl);
        bundleEntryComponent.setRequest(new Bundle.BundleEntryRequestComponent().setMethod(HTTPVerb.POST).setUrl("http://shinny.org/us/ny/hrsn/Patient/" + patient.getId()));
        bundleEntryComponent.setResource(patient);
        LOG.info("PatientConverter :: convert  END for transaction id :{}", interactionId);
        return List.of(bundleEntryComponent);
    }

    public void populatePatientWithExtensions(Patient patient,DemographicData demographicData, String interactionId) {
        if (StringUtils.isNotEmpty(demographicData.getRaceCode())) {
            Extension raceExtension = new Extension("http://hl7.org/fhir/us/core/StructureDefinition/us-core-race");

            String[] raceCodes = fetchCode(demographicData.getRaceCode(), CsvConstants.RACE_CODE, interactionId).split(";");
            String system = fetchSystem(demographicData.getRaceCodeSystem(), CsvConstants.RACE_CODE, interactionId); // Common system
            String[] raceDescriptions = demographicData.getRaceCodeDescription() != null
                    ? demographicData.getRaceCodeDescription().split(";")
                    : new String[0]; // Handle null descriptions

            StringBuilder raceTextBuilder = new StringBuilder();

            for (int i = 0; i < raceCodes.length; i++) {
                Extension ombCategoryExtension = new Extension("ombCategory");
                ombCategoryExtension.setValue(new Coding()
                        .setSystem(system)
                        .setCode(raceCodes[i].trim())
                        .setDisplay(i < raceDescriptions.length ? raceDescriptions[i].trim() : ""));
                raceExtension.addExtension(ombCategoryExtension);

                if (i < raceDescriptions.length) {
                    if (raceTextBuilder.length() > 0) {
                        raceTextBuilder.append(", ");
                    }
                    raceTextBuilder.append(raceDescriptions[i].trim());
                }
            }

            if (raceTextBuilder.length() > 0) {
                Extension textExtension = new Extension("text");
                textExtension.setValue(new StringType(raceTextBuilder.toString()));
                raceExtension.addExtension(textExtension);
            }

            patient.addExtension(raceExtension);
        }

        if (StringUtils.isNotEmpty(demographicData.getEthnicityCode())) {
            Extension ethnicityExtension = new Extension(
                    "http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity");

            String[] ethnicityCodes = fetchCode(demographicData.getEthnicityCode(), CsvConstants.ETHNICITY_CODE, interactionId).split(";");
            String system = fetchSystem(demographicData.getEthnicityCodeSystem(), CsvConstants.ETHNICITY_CODE, interactionId); // Common system
            String[] ethnicityDescriptions = demographicData.getEthnicityCodeDescription() != null
                    ? demographicData.getEthnicityCodeDescription().split(";")
                    : new String[0]; // Handle null descriptions

            StringBuilder ethnicityTextBuilder = new StringBuilder();

            for (int i = 0; i < ethnicityCodes.length; i++) {
                Extension ombCategoryExtension = new Extension("ombCategory");
                ombCategoryExtension.setValue(new Coding()
                        .setSystem(system)
                        .setCode(ethnicityCodes[i].trim())
                        .setDisplay(i < ethnicityDescriptions.length ? ethnicityDescriptions[i].trim() : ""));
                ethnicityExtension.addExtension(ombCategoryExtension);

                if (i < ethnicityDescriptions.length) {
                    if (ethnicityTextBuilder.length() > 0) {
                        ethnicityTextBuilder.append(", ");
                    }
                    ethnicityTextBuilder.append(ethnicityDescriptions[i].trim());
                }
            }

            if (ethnicityTextBuilder.length() > 0) {
                Extension textExtension = new Extension("text");
                textExtension.setValue(new StringType(ethnicityTextBuilder.toString()));
                ethnicityExtension.addExtension(textExtension);
            }

            patient.addExtension(ethnicityExtension);
        }
        

        if (StringUtils.isNotEmpty(demographicData.getSexAtBirthCode())) {
            Extension birthSexExtension = new Extension("http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex");
            birthSexExtension.setValue(new CodeType(fetchCode(demographicData.getSexAtBirthCode(), CsvConstants.SEX_AT_BIRTH_CODE, interactionId))); // Use CodeType for valueCode
            patient.addExtension(birthSexExtension);
        }

        if (StringUtils.isNotEmpty(demographicData.getPersonalPronounsCode())) {
            Extension pronounsExtension = new Extension("http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-personal-pronouns");
            pronounsExtension.setValue(new CodeableConcept().addCoding(new Coding()
                    .setSystem(fetchSystem(demographicData.getPersonalPronounsSystem(), CsvConstants.PERSONAL_PRONOUNS_CODE, interactionId))
                    .setCode(fetchCode(demographicData.getPersonalPronounsCode(), CsvConstants.PERSONAL_PRONOUNS_CODE, interactionId))
                    .setDisplay(demographicData.getPersonalPronounsDescription())));
            patient.addExtension(pronounsExtension);
        }

        if (StringUtils.isNotEmpty(demographicData.getGenderIdentityCode())) {
            Extension genderIdentityExtension = new Extension("http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-gender-identity");
            genderIdentityExtension.setValue(new CodeableConcept().addCoding(new Coding()
                    .setSystem(fetchSystem(demographicData.getGenderIdentityCodeSystem(), CsvConstants.GENDER_IDENTITY_CODE, interactionId))
                    .setCode(fetchCode(demographicData.getGenderIdentityCode(), CsvConstants.GENDER_IDENTITY_CODE, interactionId))
                    .setDisplay(demographicData.getGenderIdentityCodeDescription())));
            patient.addExtension(genderIdentityExtension);
        }
    }
    private static Patient populatePatientName(Patient patient, DemographicData demographicData) {
        HumanName name = new HumanName();
        if (demographicData.getGivenName() != null) {
            name.addGiven(demographicData.getGivenName());
        }
        if (demographicData.getMiddleName() != null) {
            Extension middleNameExtension = new Extension();
            middleNameExtension.setUrl("http://shinny.org/us/ny/hrsn/StructureDefinition/middle-name"); // TODO : remove
                                                                                                        // static
                                                                                                        // reference
            middleNameExtension.setValue(new StringType(demographicData.getMiddleName()));
            name.addExtension(middleNameExtension);
        }
        if (demographicData.getFamilyName() != null) {
            name.setFamily(demographicData.getFamilyName());
        }
        patient.addName(name);
        return patient;
    }

    // /**
    // * Concatenates the encounter ID, facility ID, and patient MRN ID
    // * to form a unique identifier in the format:
    // "encounterIdfacilityId-patMrnId".
    // *
    // * @param encounterId The encounter ID.
    // * @param facilityId The facility ID.
    // * @param patMrnId The patient MRN ID.
    // * @return A concatenated string in the format:
    // * "encounterIdfacilityId-patMrnId".
    // */
    private String generateUniqueId(String encounterId, String facilityId, String patMrnId) {
        return new StringBuilder()
                .append(encounterId)
                .append(facilityId)
                .append('-')
                .append(patMrnId)
                .toString();
    }

   

    private static void populateMrIdentifier(Patient patient, DemographicData data,QeAdminData qeAdminData,Map<String,String> idsGenerated) {
        if (StringUtils.isNotEmpty(data.getPatientMrIdValue())) {
            Identifier identifier = new Identifier();
            Coding coding = new Coding();
            coding.setSystem("http://terminology.hl7.org/CodeSystem/v2-0203"); // TODO : remove static reference
            coding.setCode("MR");
            CodeableConcept type = new CodeableConcept();
            type.addCoding(coding);
            identifier.setType(type);
            identifier.setSystem("http://www.scn.gov/facility/"+qeAdminData.getFacilityId());
            identifier.setValue(data.getPatientMrIdValue());

            // Optional: Add assigner if needed (uncomment if required)
            Reference assigner = new Reference();
            assigner.setReference("Organization/"+idsGenerated.get(CsvConstants.ORGANIZATION_ID));
            // populate while organization is populated
            identifier.setAssigner(assigner);

            // Set the identifier on the Patient object
            patient.addIdentifier(identifier);
        }
    }

    private static void populateMaIdentifier(Patient patient, DemographicData data) {
        if (StringUtils.isNotEmpty(data.getPatientMedicaidId())) {
            Identifier identifier = new Identifier();
            Coding coding = new Coding();
            coding.setSystem("http://terminology.hl7.org/CodeSystem/v2-0203"); // TODO : remove static reference
            coding.setCode("MA");
            CodeableConcept type = new CodeableConcept();
            type.addCoding(coding);
            identifier.setType(type);
            identifier.setSystem("http://www.medicaid.gov/"); // TODO : remove static reference
            identifier.setValue(data.getPatientMedicaidId());
            patient.addIdentifier(identifier);
        }
    }

    private static void populateSsnIdentifier(Patient patient, DemographicData data) {
        if (StringUtils.isNotEmpty(data.getPatientSsIdValue())) {
            Identifier identifier = new Identifier();
            Coding coding = new Coding();
            coding.setSystem("http://terminology.hl7.org/CodeSystem/v2-0203"); // TODO : remove static reference
            coding.setCode("SS");
            coding.setDisplay("Social Security Number");
            CodeableConcept type = new CodeableConcept();
            type.addCoding(coding);
            type.setText("Social Security Number");
            identifier.setType(type);
            identifier.setSystem("http://www.ssa.gov/"); // TODO : remove static reference
            identifier.setValue(data.getPatientSsIdValue());
            patient.addIdentifier(identifier);
        }
    }

    private void populateAdministrativeSex(Patient patient, DemographicData demographicData, String interactionId) {
        Optional.ofNullable(fetchCode(demographicData.getAdministrativeSexCode(), CsvConstants.ADMINISTRATIVE_SEX_CODE, interactionId))
                .map(sexCode -> switch (sexCode) {
                    case "male", "M" -> AdministrativeGender.MALE;
                    case "female", "F" -> AdministrativeGender.FEMALE;
                    case "other", "O" -> AdministrativeGender.OTHER;
                    default -> AdministrativeGender.UNKNOWN;
                })
                .ifPresent(patient::setGender);
    }

    private static void populateBirthDate(Patient patient, DemographicData demographicData) {
        Optional.ofNullable(demographicData.getPatientBirthDate())
                .map(DateUtil::parseDate)
                .ifPresent(patient::setBirthDate);
    }

    private void populatePhone(Patient patient, DemographicData demographicData, String interactionId) {
        if (StringUtils.isNotEmpty(demographicData.getTelecomValue())) {
            ContactPoint.ContactPointUse telecomUse = null;
            try {
                telecomUse = ContactPoint.ContactPointUse.fromCode(fetchCode(demographicData.getTelecomUse(), CsvConstants.TELECOM_USE, interactionId));
            } catch (FHIRException e) {
                telecomUse = ContactPoint.ContactPointUse.HOME; // Default to HOME
            }
            patient.addTelecom(new ContactPoint()
                    .setSystem(ContactPoint.ContactPointSystem.PHONE)
                    .setValue(demographicData.getTelecomValue())
                    .setUse(telecomUse));
        }
    }


    private void populateAddress(Patient patient, DemographicData data, String interactionId) {
        if (StringUtils.isNotEmpty(data.getCity()) && StringUtils.isNotEmpty(data.getState())) {
            Address address = new Address();
            Optional.ofNullable(data.getAddress1())
                    .filter(StringUtils::isNotEmpty)
                    .ifPresent(address::addLine);
            Optional.ofNullable(data.getAddress2())
            .filter(StringUtils::isNotEmpty)
            .ifPresent(address::addLine);
            address.setCity(data.getCity());
            address.setState(fetchCode(data.getState(), CsvConstants.STATE, interactionId));
            Optional.ofNullable(data.getCounty())
                    .filter(StringUtils::isNotEmpty)
                    .ifPresent(address::setDistrict);
            Optional.ofNullable(data.getZip())
                    .filter(StringUtils::isNotEmpty)
                    .ifPresent(address::setPostalCode);
            String addressText = String.format("%s %s, %s %s", address.getLine().get(0), address.getCity(),
                fetchCode(data.getState(), CsvConstants.STATE, interactionId), address.getPostalCode());
            address.setText(addressText);

            patient.addAddress(address);
        }
    }

    private void populatePreferredLanguage(Patient patient, DemographicData data, String interactionId) {
        Optional.ofNullable(data.getPreferredLanguageCode())
                .filter(StringUtils::isNotEmpty)
                .ifPresent(languageCode -> {
                    Coding coding = new Coding();
                    coding.setSystem(fetchSystem(data.getPreferredLanguageCodeSystem(), CsvConstants.PREFERRED_LANGUAGE_CODE, interactionId));
                    coding.setCode(fetchCode(languageCode, CsvConstants.PREFERRED_LANGUAGE_CODE, interactionId));
                    coding.setDisplay(data.getPreferredLanguageCodeDescription());
                    CodeableConcept language = new CodeableConcept();
                    language.addCoding(coding);
                    PatientCommunicationComponent communication = new PatientCommunicationComponent();
                    communication.setLanguage(language);
                    communication.setPreferred(true);
                    patient.addCommunication(communication);
                });
    }

    // private static void populatePatientRelationContact(Patient patient, DemographicData data) {
    //     if (patient == null || data == null)
    //         return;

    //     Optional.ofNullable(data.getRelationshipPersonCode())
    //             .filter(StringUtils::isNotEmpty)
    //             .ifPresent(relationshipCode -> {
    //                 // Using builder pattern where applicable
    //                 var coding = new Coding()
    //                         .setSystem("http://terminology.hl7.org/CodeSystem/v2-0063") // TODO : remove static reference
    //                         .setCode(relationshipCode)
    //                         .setDisplay(data.getRelationshipPersonDescription());

    //                 var relationship = new CodeableConcept().addCoding(coding);

    //                 var name = new HumanName()
    //                         .setFamily(data.getRelationshipPersonFamilyName())
    //                         .addGiven(data.getRelationshipPersonGivenName());

    //                 var telecomSystem = Optional.ofNullable("Phone") // TODO : remove static reference
    //                         .filter(StringUtils::isNotEmpty)
    //                         .map(String::toLowerCase)
    //                         .map(ContactPoint.ContactPointSystem::fromCode)
    //                         .orElse(null);

    //                 var telecom = new ContactPoint()
    //                         .setSystem(telecomSystem)
    //                         .setValue(data.getRelationshipPersonTelecomValue());

    //                 var contact = new Patient.ContactComponent()
    //                         .setRelationship(List.of(relationship))
    //                         .setName(name)
    //                         .addTelecom(telecom);

    //                 patient.addContact(contact);
    //             });
    // }

}
