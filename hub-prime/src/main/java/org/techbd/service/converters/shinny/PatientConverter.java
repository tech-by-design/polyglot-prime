package org.techbd.service.converters.shinny;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Narrative.NarrativeStatus;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Patient.PatientCommunicationComponent;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningData;
import org.techbd.util.DateUtil;

@Component
public class PatientConverter extends BaseConverter implements IPatientConverter {
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
    public BundleEntryComponent convert(Bundle bundle, DemographicData demographicData,
            List<ScreeningData> screeningDataList,
            QeAdminData qrAdminData, String interactionId) {
        Patient patient = new Patient();
        setMeta(patient);
        ScreeningData screeningData = screeningDataList.get(0);
        patient.setId(generateUniqueId(screeningData.getEncounterId(), screeningData.getFacilityId(),
                screeningData.getPatientMrId()));
        Meta meta = patient.getMeta();
        meta.setLastUpdated(DateUtil.parseDate(demographicData.getPatientLastUpdated())); // max date available in all
                                                                                          // screening records
        patient.setLanguage("en");
        populateExtensions(patient, demographicData);
        populateMrIdentifier(patient, demographicData);
        populateMaIdentifier(patient, demographicData);
        populateSsnIdentifier(patient, demographicData);
        populatePatientName(patient, demographicData);
        populateAdministrativeSex(patient, demographicData);
        populateBirthDate(patient, demographicData);
        populatePhone(patient, demographicData);
        populateAddress(patient, demographicData);
        populatePreferredLanguage(patient, demographicData);
        populatePatientRelationContact(patient, demographicData);
        populatePatientText(patient, demographicData);
        BundleEntryComponent bundleEntryComponent = new BundleEntryComponent();
        bundleEntryComponent.setResource(patient);
        return bundleEntryComponent;
    }

    private static Patient populatePatientName(Patient patient, DemographicData demographicData) {
        HumanName name = new HumanName();
        if (demographicData.getGivenName() != null) {
            name.addGiven(demographicData.getGivenName());
        }
        if (demographicData.getMiddleName() != null) {
            name.addGiven(demographicData.getMiddleName());
            Extension middleNameExtension = new Extension();
            middleNameExtension.setUrl(demographicData.getMiddleNameExtensionUrl());
            middleNameExtension.setValue(new StringType(demographicData.getMiddleName()));
            name.addExtension(middleNameExtension);
        }
        if (demographicData.getFamilyName() != null) {
            name.setFamily(demographicData.getFamilyName());
        }
        name.addPrefix(demographicData.getPrefixName());
        name.addSuffix(demographicData.getSuffixName());
        name.addExtension(null, name);
        patient.addName(name);
        return patient;
    }

    /**
     * Concatenates the encounter ID, facility ID, and patient MRN ID
     * to form a unique identifier in the format: "encounterIdfacilityId-patMrnId".
     *
     * @param encounterId The encounter ID.
     * @param facilityId  The facility ID.
     * @param patMrnId    The patient MRN ID.
     * @return A concatenated string in the format:
     *         "encounterIdfacilityId-patMrnId".
     */
    private String generateUniqueId(String encounterId, String facilityId, String patMrnId) {
        // Use StringBuilder for efficient string concatenation
        return new StringBuilder()
                .append(encounterId)
                .append(facilityId)
                .append('-')
                .append(patMrnId)
                .toString();
    }

    private void populateExtensions(Patient patient, DemographicData demographicData) {
        if (StringUtils.isNotEmpty(demographicData.getExtensionOmbCategoryRaceUrl()) ||
                StringUtils.isNotEmpty(demographicData.getExtensionOmbCategoryRaceCode()) ||
                StringUtils.isNotEmpty(demographicData.getExtensionOmbCategoryRaceCodeDescription()) ||
                StringUtils.isNotEmpty(demographicData.getExtensionOmbCategoryRaceCodeSystemName())) {
            Extension raceOmbExtension = getRaceOmbExtension(
                    demographicData.getExtensionOmbCategoryRaceUrl(),
                    demographicData.getExtensionOmbCategoryRaceCodeSystemName(),
                    demographicData.getExtensionOmbCategoryRaceCode(),
                    demographicData.getExtensionOmbCategoryRaceCodeDescription());
            patient.addExtension(raceOmbExtension);
        }

        if (StringUtils.isNotEmpty(demographicData.getExtensionTextRaceUrl()) ||
                StringUtils.isNotEmpty(demographicData.getExtensionTextRaceCodeValue())) {

            Extension raceDetailedExtension = getRaceDetailedExtension(
                    demographicData.getExtensionTextRaceUrl(),
                    null,
                    demographicData.getExtensionTextRaceCodeValue(),
                    null);
            patient.addExtension(raceDetailedExtension);
        }

        if (StringUtils.isNotEmpty(demographicData.getExtensionOmbCategoryEthnicityUrl()) ||
                StringUtils.isNotEmpty(demographicData.getExtensionOmbCategoryEthnicityCode()) ||
                StringUtils.isNotEmpty(demographicData.getExtensionOmbCategoryEthnicityCodeDescription()) ||
                StringUtils.isNotEmpty(demographicData.getExtensionOmbCategoryEthnicityCodeSystemName())) {

            Extension ethnicityOmbExtension = getEthnicityOmbExtension(
                    demographicData.getExtensionOmbCategoryEthnicityUrl(),
                    demographicData.getExtensionOmbCategoryEthnicityCodeSystemName(),
                    demographicData.getExtensionOmbCategoryEthnicityCode(),
                    demographicData.getExtensionOmbCategoryEthnicityCodeDescription());
            patient.addExtension(ethnicityOmbExtension);
        }

        if (StringUtils.isNotEmpty(demographicData.getExtensionTextEthnicityUrl()) ||
                StringUtils.isNotEmpty(demographicData.getExtensionTextEthnicityCodeValue())) {

            Extension ethnicityDetailedExtension = getEthnicityDetailedExtension(
                    demographicData.getExtensionTextEthnicityUrl(),
                    null,
                    demographicData.getExtensionTextEthnicityCodeValue(),
                    null);
            patient.addExtension(ethnicityDetailedExtension);
        }

        if (StringUtils.isNotEmpty(demographicData.getExtensionPersonalPronounsUrl()) ||
                StringUtils.isNotEmpty(demographicData.getExtensionPersonalPronounsCode()) ||
                StringUtils.isNotEmpty(demographicData.getExtensionPersonalPronounsDisplay()) ||
                StringUtils.isNotEmpty(demographicData.getExtensionPersonalPronounsSystem())) {

            Extension personalPronounsExtension = getShinnyPersonalPronounsExtension(
                    demographicData.getExtensionPersonalPronounsUrl(),
                    demographicData.getExtensionPersonalPronounsSystem(),
                    demographicData.getExtensionPersonalPronounsCode(),
                    demographicData.getExtensionPersonalPronounsDisplay());
            patient.addExtension(personalPronounsExtension);
        }

        if (StringUtils.isNotEmpty(demographicData.getExtensionGenderIdentityUrl()) ||
                StringUtils.isNotEmpty(demographicData.getExtensionGenderIdentityCode()) ||
                StringUtils.isNotEmpty(demographicData.getExtensionGenderIdentityDisplay()) ||
                StringUtils.isNotEmpty(demographicData.getExtensionGenderIdentitySystem())) {

            Extension genderIdentityExtension = getShinnyGenderIdentityExtension(
                    demographicData.getExtensionGenderIdentityUrl(),
                    demographicData.getExtensionGenderIdentitySystem(),
                    demographicData.getExtensionGenderIdentityCode(),
                    demographicData.getExtensionGenderIdentityDisplay());
            patient.addExtension(genderIdentityExtension);
        }
    }

    private static void populateMrIdentifier(Patient patient, DemographicData data) {
        if (StringUtils.isNotEmpty(data.getPatientMrIdValue())) {
            Identifier identifier = new Identifier();
            Coding coding = new Coding();
            coding.setSystem(data.getPatientMrIdSystem());
            coding.setCode("MR");
            CodeableConcept type = new CodeableConcept();
            type.addCoding(coding);
            identifier.setType(type);
            identifier.setSystem(data.getPatientMrIdTypeSystem());
            identifier.setValue(data.getPatientMrIdValue());

            // Optional: Add assigner if needed (uncomment if required)
            // Reference assigner = new Reference();
            // assigner.setReference("Organization/OrganizationExampleOther-SCN1"); //TODO -
            // populate while organization is populated
            // identifier.setAssigner(assigner);

            // Set the identifier on the Patient object
            patient.addIdentifier(identifier);
        }
    }

    private static void populateMaIdentifier(Patient patient, DemographicData data) {
        if (StringUtils.isNotEmpty(data.getPatientMaIdValue())) {
            Identifier identifier = new Identifier();
            Coding coding = new Coding();
            coding.setSystem(data.getPatientMaIdSystem());
            coding.setCode("MA");
            CodeableConcept type = new CodeableConcept();
            type.addCoding(coding);
            identifier.setType(type);
            identifier.setSystem(data.getPatientMaIdTypeSystem());
            identifier.setValue(data.getPatientMaIdValue());
            patient.addIdentifier(identifier);
        }
    }

    private static void populateSsnIdentifier(Patient patient, DemographicData data) {
        if (StringUtils.isNotEmpty(data.getPatientSsIdValue())) {
            Identifier identifier = new Identifier();
            Coding coding = new Coding();
            coding.setSystem(data.getPatientSsIdSystem());
            coding.setCode("SSN");
            CodeableConcept type = new CodeableConcept();
            type.addCoding(coding);
            identifier.setType(type);
            identifier.setSystem(data.getPatientSsIdTypeSystem());
            identifier.setValue(data.getPatientSsIdValue());
            patient.addIdentifier(identifier);
        }
    }

    private static void populateAdministrativeSex(Patient patient, DemographicData demographicData) {
        Optional.ofNullable(demographicData.getGender())
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

    private static void populatePhone(Patient patient, DemographicData demographicData) {
        Optional.ofNullable(demographicData.getTelecomValue())
                .ifPresent(phone -> patient.addTelecom(new ContactPoint()
                        .setSystem(ContactPoint.ContactPointSystem.fromCode(demographicData.getTelecomSystem()))
                        .setValue(demographicData.getTelecomValue())));
    }

    private static void populateAddress(Patient patient, DemographicData data) {
        if (StringUtils.isNotEmpty(data.getCity()) && StringUtils.isNotEmpty(data.getState())) {
            Address address = new Address();
            Optional.ofNullable(data.getAddress1())
                    .filter(StringUtils::isNotEmpty)
                    .ifPresent(address::addLine);

            Optional.ofNullable(data.getAddress2())
                    .filter(StringUtils::isNotEmpty)
                    .ifPresent(address::addLine);
            address.setCity(data.getCity());
            address.setState(data.getState());
            Optional.ofNullable(data.getZip())
                    .filter(StringUtils::isNotEmpty)
                    .ifPresent(address::setPostalCode);
            patient.addAddress(address);
        }
    }

    private void populatePreferredLanguage(Patient patient, DemographicData data) {
        Optional.ofNullable(data.getPreferredLanguageCodeSystemCode())
                .filter(StringUtils::isNotEmpty)
                .ifPresent(languageCode -> {
                    Coding coding = new Coding();
                    coding.setSystem("urn:ietf:bcp:47");
                    coding.setCode(languageCode);
                    CodeableConcept language = new CodeableConcept();
                    language.addCoding(coding);
                    PatientCommunicationComponent communication = new PatientCommunicationComponent();
                    communication.setLanguage(language);
                    communication.setPreferred(true);
                    patient.addCommunication(communication);
                });
    }

    private static void populatePatientRelationContact(Patient patient, DemographicData data) {
        if (patient == null || data == null) return;
    
        Optional.ofNullable(data.getRelationshipPersonCode())
                .filter(StringUtils::isNotEmpty)
                .ifPresent(relationshipCode -> {
                    // Using builder pattern where applicable
                    var coding = new Coding()
                            .setSystem(data.getRelationshipPersonSystem())
                            .setCode(relationshipCode)
                            .setDisplay(data.getRelationshipPersonDescription());
    
                    var relationship = new CodeableConcept().addCoding(coding);
    
                    var name = new HumanName()
                            .setFamily(data.getRelationshipPersonFamilyName())
                            .addGiven(data.getRelationshipPersonGivenName());
    
                    var telecomSystem = Optional.ofNullable(data.getRelationshipPersonTelecomSystem())
                            .filter(StringUtils::isNotEmpty)
                            .map(String::toLowerCase)
                            .map(ContactPoint.ContactPointSystem::fromCode)
                            .orElse(null);
    
                    var telecom = new ContactPoint()
                            .setSystem(telecomSystem)
                            .setValue(data.getRelationshipPersonTelecomValue());
    
                    var contact = new Patient.ContactComponent()
                            .setRelationship(List.of(relationship))
                            .setName(name)
                            .addTelecom(telecom);
    
                    patient.addContact(contact);
                });
    }
    

    private static void populatePatientText(Patient patient, DemographicData data) {
        Optional.ofNullable(data.getPatientTextStatus())
                .filter(StringUtils::isNotEmpty)
                .ifPresent(status -> {
                    Narrative text = new Narrative();
                    text.setStatus(NarrativeStatus.fromCode(status.toLowerCase()));
                });
    }

    @Override
    public Extension getRaceOmbExtension(String url, String system, String code, String display) {
        return createExtension(url, system, code, display);
    }

    @Override
    public Extension getRaceDetailedExtension(String url, String system, String code, String display) {
        return createExtension(url, system, code, display);
    }

    @Override
    public Extension getEthnicityOmbExtension(String url, String system, String code, String display) {
        return createExtension(url, system, code, display);
    }

    @Override
    public Extension getEthnicityDetailedExtension(String url, String system, String code, String display) {
        return createExtension(url, system, code, display);
    }

    @Override
    public Extension getSexAtBirthExtension(String url, String system, String code, String display) {
        return createExtension(url, system, code, display);
    }

    @Override
    public Extension getShinnyPersonalPronounsExtension(String url, String system, String code, String display) {
        return createExtension(url, system, code, display);
    }

    @Override
    public Extension getShinnyGenderIdentityExtension(String url, String system, String code, String display) {
        return createExtension(url, system, code, display);
    }

}
