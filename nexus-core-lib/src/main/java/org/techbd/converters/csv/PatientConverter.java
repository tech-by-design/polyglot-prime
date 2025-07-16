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
import org.techbd.config.CoreUdiPrimeJpaConfig;
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

    public PatientConverter(CodeLookupService codeLookupService,final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig) {
        super(codeLookupService,coreUdiPrimeJpaConfig);
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

            String[] raceDescriptions = new String[raceCodes.length];
            String raceCodeDesc = demographicData.getRaceCodeDescription();
            StringBuilder raceTextBuilder = new StringBuilder();

            for (int i = 0; i < raceCodes.length; i++) {
                String trimmedCode = raceCodes[i].trim();
                String display = fetchDisplay(trimmedCode, raceCodeDesc, CsvConstants.RACE_CODE, interactionId);

                raceDescriptions[i] = display;
                Extension ombCategoryExtension = new Extension(getOmbRaceCategory(trimmedCode, interactionId));
                String system = fetchSystem(raceCodes[i].trim(), demographicData.getRaceCodeSystem(), CsvConstants.RACE_CODE, interactionId);
                ombCategoryExtension.setValue(new Coding()
                        .setSystem(system)
                        .setCode(trimmedCode)
                        .setDisplay(display));
                raceExtension.addExtension(ombCategoryExtension);

                if (StringUtils.isNotBlank(display)) {
                    if (raceTextBuilder.length() > 0) raceTextBuilder.append(", ");
                    raceTextBuilder.append(display.trim());

                }
            }

            if (raceTextBuilder.length() > 0) {
                raceExtension.addExtension(new Extension("text", new StringType(raceTextBuilder.toString())));
            }

            patient.addExtension(raceExtension);
        }

        if (StringUtils.isNotEmpty(demographicData.getEthnicityCode())) {
            Extension ethnicityExtension = new Extension("http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity");

            String[] ethnicityCodes = fetchCode(demographicData.getEthnicityCode(), CsvConstants.ETHNICITY_CODE, interactionId).split(";");

            String ethnicityCodeDesc = demographicData.getEthnicityCodeDescription();
            String[] ethnicityDescriptions = new String[ethnicityCodes.length];
            StringBuilder ethnicityTextBuilder = new StringBuilder();

            for (int i = 0; i < ethnicityCodes.length; i++) {
                String trimmedCode = ethnicityCodes[i].trim();
                String display = fetchDisplay(trimmedCode, ethnicityCodeDesc, CsvConstants.ETHNICITY_CODE, interactionId);

                ethnicityDescriptions[i] = display;

                Extension ombCategoryExtension = new Extension(getOmbEthnicityCategory(trimmedCode, interactionId));
                String system = fetchSystem(ethnicityCodes[i].trim(), demographicData.getEthnicityCodeSystem(), CsvConstants.ETHNICITY_CODE, interactionId);

                ombCategoryExtension.setValue(new Coding()
                        .setSystem(system)
                        .setCode(trimmedCode)
                        .setDisplay(display));

                ethnicityExtension.addExtension(ombCategoryExtension);

                if (StringUtils.isNotBlank(display)) {
                    if (ethnicityTextBuilder.length() > 0) ethnicityTextBuilder.append(", ");
                    ethnicityTextBuilder.append(display.trim());
                }
            }

            if (ethnicityTextBuilder.length() > 0) {
                ethnicityExtension.addExtension(new Extension("text", new StringType(ethnicityTextBuilder.toString())));
            }

            patient.addExtension(ethnicityExtension);
        }
        

        if (StringUtils.isNotEmpty(demographicData.getSexAtBirthCode())) {
            Extension birthSexExtension = new Extension("http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex");
            birthSexExtension.setValue(new CodeType(fetchCode(demographicData.getSexAtBirthCode(), CsvConstants.SEX_AT_BIRTH_CODE, interactionId))); // Use CodeType for valueCode
            patient.addExtension(birthSexExtension);
        }

        if (StringUtils.isNotEmpty(demographicData.getPersonalPronounsCode())) {
            String[] codes = demographicData.getPersonalPronounsCode().split(";");
            String[] displays = StringUtils.defaultString(demographicData.getPersonalPronounsDescription()).split(";");

            Extension pronounsExtension = new Extension("http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-personal-pronouns");
            CodeableConcept concept = new CodeableConcept();

            for (int i = 0; i < codes.length; i++) {
                String rawCode = codes[i].trim();
                if (StringUtils.isNotEmpty(rawCode)) {
                    String code = fetchCode(rawCode, CsvConstants.PERSONAL_PRONOUNS_CODE, interactionId);
                    String display = (i < displays.length) ? displays[i].trim() : null;
                    display = fetchDisplay(code, display, CsvConstants.PERSONAL_PRONOUNS_CODE, interactionId);

                    Coding coding = new Coding()
                        .setSystem(fetchSystem(code, demographicData.getPersonalPronounsSystem(), CsvConstants.PERSONAL_PRONOUNS_CODE, interactionId))
                        .setCode(code)
                        .setDisplay(display);

                    concept.addCoding(coding);
                }
            }

            if (!concept.getCoding().isEmpty()) {
                pronounsExtension.setValue(concept);
                patient.addExtension(pronounsExtension);
            }
        }

        if (StringUtils.isNotEmpty(demographicData.getGenderIdentityCode())) {
            String[] codes = demographicData.getGenderIdentityCode().split(";");
            String[] systems = StringUtils.defaultString(demographicData.getGenderIdentityCodeSystem()).split(";");
            String[] descriptions = StringUtils.defaultString(demographicData.getGenderIdentityCodeDescription()).split(";");

            Extension genderIdentityExtension = new Extension("http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-gender-identity");
            CodeableConcept genderConcept = new CodeableConcept();

            for (int i = 0; i < codes.length; i++) {
                String rawCode = codes[i].trim();
                if (StringUtils.isNotEmpty(rawCode)) {
                    String code = fetchCode(rawCode, CsvConstants.GENDER_IDENTITY_CODE, interactionId);
                    String system = fetchSystem(code, (i < systems.length ? systems[i].trim() : null), CsvConstants.GENDER_IDENTITY_CODE, interactionId);
                    String display = fetchDisplay(code, (i < descriptions.length ? descriptions[i].trim() : null), CsvConstants.GENDER_IDENTITY_CODE, interactionId);

                    Coding coding = new Coding()
                        .setSystem(system)
                        .setCode(code)
                        .setDisplay(display);

                    genderConcept.addCoding(coding);
                }
            }

            if (!genderConcept.getCoding().isEmpty()) {
                genderIdentityExtension.setValue(genderConcept);
                patient.addExtension(genderIdentityExtension);
            }
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
                    String langCode = fetchCode(languageCode, CsvConstants.PREFERRED_LANGUAGE_CODE, interactionId);
                    Coding coding = new Coding();
                    coding.setSystem(fetchSystem(langCode, data.getPreferredLanguageCodeSystem(), CsvConstants.PREFERRED_LANGUAGE_CODE, interactionId));
                    coding.setCode(langCode);
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
