package org.techbd.service.converters.shinny;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.ResourceType;
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
        meta.setLastUpdated(getMaxLastUpdatedDate(screeningDataList)); // max date available in all screening records
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
        populatePreferredLanguage(patient,demographicData);
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
        }
        if (demographicData.getFamilyName() != null) {
            name.setFamily(demographicData.getFamilyName());
        }
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



    /**
     * Adds extensions to a Patient object based on the demographic data.
     * <p>
     * This method creates and adds extensions to a FHIR Patient resource, based on
     * the demographic data provided.
     * Specifically, it adds extensions for sex at birth, ethnicity, and race, if
     * the respective data is available.
     * These extensions are added to the Patient resource using the appropriate URLs
     * and values from the
     * provided demographic data.
     * </p>
     *
     * @param patient         the FHIR Patient object to which the extensions will
     *                        be added
     * @param demographicData the DemographicData object containing the data to
     *                        populate the extensions
     */
    private void populateExtensions(Patient patient, DemographicData demographicData) {
        // final var sexAtBirthExtension = getSexAtBirthExtension(demographicData.getSexAtBirthCode(),
        //         demographicData.getSexAtBirthCodeDescription(), demographicData.getSexAtBirthCodeSystem());
        // if (null != sexAtBirthExtension) {
        //     patient.addExtension(sexAtBirthExtension);
        // }
        // final var ethinicityExtension = createEthnicityExtension(demographicData);
        // if (null != ethinicityExtension) {
        //     patient.addExtension(VALUESETS_MAP.get("hl7UsCoreEthinicity"),
        //             ethinicityExtension);
        // }
        // final var raceExtension = createRaceExtension(demographicData);
        // if (null != raceExtension) {
        //     patient.addExtension(VALUESETS_MAP.get("hl7UsCoreRace"), raceExtension);
        // }
    }

    /**
     * Creates an extension for the sex at birth based on the provided code,
     * description, and code system.
     * <p>
     * This method generates a FHIR extension for the sex at birth using the
     * specified code, description,
     * and code system. The extension is created using the URL for the US Core sex
     * at birth extension
     * and is assigned a code value based on the provided code.
     * </p>
     *
     * @param code        the code representing the sex at birth (e.g., "M" for
     *                    Male, "F" for Female)
     * @param description a description for the code (optional, not used in this
     *                    method)
     * @param system      the code system URL for the sex at birth code (optional,
     *                    not used in this method)
     * @return an Extension object representing the sex at birth, or null if the
     *         code is not provided
     *
     * @example
     * 
     *          <pre>
     *   Example 1: Sex at birth extension for Male (M)
     *   {
     *     "url": "http://hl7.org/fhir/StructureDefinition/us-core-birthSex",
     *     "valueCode": "M"
     *   }
     *
     *   Example 2: Sex at birth extension for Female (F)
     *   {
     *     "url": "http://hl7.org/fhir/StructureDefinition/us-core-birthSex",
     *     "valueCode": "F"
     *   }
     *          </pre>
     */
    @Override
    public Extension getSexAtBirthExtension(String code, String description, String system) {
        // if (code != null) {
        //     Extension sexAtBirthExtension = new Extension()
        //             .setUrl(VALUESETS_MAP.get("hl7UsCoreBirthSex")) // TODO - revisit
        //             .setValue(new org.hl7.fhir.r4.model.CodeType(code));
        //     return sexAtBirthExtension;
        // }
        return null;
    }

    /**
     * Creates an OMB race category extension using the provided code, description,
     * and code system.
     * <p>
     * This method generates a FHIR extension for the OMB race category using the
     * specified code, description,
     * and code system. It creates a `Coding` object representing the race category
     * and then assigns it to
     * an extension with a predefined URL ("ombCategory").
     * </p>
     *
     * @param code        the code representing the OMB race category (e.g.,
     *                    "1002-5" for American Indian or Alaska Native)
     * @param description the description of the race category (e.g., "American
     *                    Indian or Alaska Native")
     * @param system      the code system URL for the OMB race category (e.g.,
     *                    "urn:oid:2.16.840.1.113883.6.238")
     * @return an Extension object representing the OMB race category, containing a
     *         `Coding` with the provided code, description, and system
     *
     * @example
     * 
     *          <pre>
     *   Example 1: OMB race category extension for American Indian or Alaska Native
     *   {
     *     "url": "ombCategory",
     *     "valueCoding": {
     *       "system": "urn:oid:2.16.840.1.113883.6.238",
     *       "code": "1002-5",
     *       "display": "American Indian or Alaska Native"
     *     }
     *   }
     *
     *   Example 2: OMB race category extension for Asian
     *   {
     *     "url": "ombCategory",
     *     "valueCoding": {
     *       "system": "urn:oid:2.16.840.1.113883.6.238",
     *       "code": "2028-9",
     *       "display": "Asian"
     *     }
     *   }
     *          </pre>
     */
    @Override
    public Extension getRaceOmbExtension(String code, String description, String system) {
        // Optional<CodeSystem> codeSystem = codeSystemLookupService.lookupCodeInFile(MASTER_DATA_PATH_MAP.get("ombRace"),
        //         code);
        // if (codeSystem.isPresent()) {
        //     if (system == null) {
        //         system = codeSystem.get().getSystem();
        //     }
        //     if (description == null) {
        //         description = codeSystem.get().getDisplay();
        //     }
        // }
        // Coding coding = new Coding()
        //         .setSystem(system)// TODO : check if system will be provided always
        //         .setCode(code)
        //         .setDisplay(description);
        // Extension ombCategoryExtension = new Extension()
        //         .setUrl("ombCategory")
        //         .setValue(coding);
        // return ombCategoryExtension;
        return null;
    }

    /**
     * Creates a detailed race extension using the provided code, description, and
     * code system.
     * <p>
     * This method generates a FHIR extension for a detailed race category using the
     * specified code, description,
     * and code system. It creates a `Coding` object that represents the race
     * category and assigns it to an extension
     * with a URL of "detailed". The extension is then returned for use in the FHIR
     * resource.
     * </p>
     *
     * @param code        the code representing the detailed race category (e.g.,
     *                    "1002-5" for American Indian or Alaska Native)
     * @param description the description of the race category (e.g., "American
     *                    Indian or Alaska Native")
     * @param system      the code system URL for the race category (e.g.,
     *                    "urn:oid:2.16.840.1.113883.6.238")
     * @return an Extension object representing the detailed race category,
     *         containing a `Coding` with the provided code, description, and system
     *
     * @example
     * 
     *          <pre>
     *   Example 1: Detailed race extension for American Indian or Alaska Native
     *   {
     *     "url": "detailed",
     *     "valueCoding": {
     *       "system": "urn:oid:2.16.840.1.113883.6.238",
     *       "code": "1002-5",
     *       "display": "American Indian or Alaska Native"
     *     }
     *   }
     *
     *   Example 2: Detailed race extension for Asian
     *   {
     *     "url": "detailed",
     *     "valueCoding": {
     *       "system": "urn:oid:2.16.840.1.113883.6.238",
     *       "code": "2028-9",
     *       "display": "Asian"
     *     }
     *   }
     *          </pre>
     */
    @Override
    public Extension getRaceDetailedExtension(String code, String description, String system) {
        // Optional<CodeSystem> codeSystem = codeSystemLookupService
        //         .lookupCodeInFile(MASTER_DATA_PATH_MAP.get("detiledRace"), code);
        // if (codeSystem.isPresent()) {
        //     if (system == null) {
        //         system = codeSystem.get().getSystem();
        //     }
        //     if (description == null) {
        //         description = codeSystem.get().getDisplay();
        //     }
        // }
        // Coding coding = new Coding()
        //         .setSystem(system) // TODO : check if system will be provided always
        //         .setCode(code)
        //         .setDisplay(description);
        // Extension detailedExtension = new Extension()
        //         .setUrl("detailed") // TODO -verify what value comes here
        //         .setValue(coding);
        // return detailedExtension;
        return null;
    }

    /**
     * Creates an ethnicity OMB (Office of Management and Budget) category extension
     * using the provided code, description, and code system.
     * <p>
     * This method generates a FHIR extension for an ethnicity category using the
     * specified code, description, and code system.
     * It creates a `Coding` object representing the ethnicity category and assigns
     * it to an extension with a URL of "ombCategory".
     * The extension is then returned for use in the FHIR resource.
     * </p>
     *
     * @param code        the code representing the ethnicity category (e.g.,
     *                    "2028-9" for Asian)
     * @param description the description of the ethnicity category (e.g., "Asian")
     * @param system      the code system URL for the ethnicity category (e.g.,
     *                    "urn:oid:2.16.840.1.113883.6.238")
     * @return an Extension object representing the ethnicity category, containing a
     *         `Coding` with the provided code, description, and system
     *
     * @example
     * 
     *          <pre>
     *   Example 1: Ethnicity OMB category extension for Asian
     *   {
     *     "url": "ombCategory",
     *     "valueCoding": {
     *       "system": "urn:oid:2.16.840.1.113883.6.238",
     *       "code": "2028-9",
     *       "display": "Asian"
     *     }
     *   }
     *
     *   Example 2: Ethnicity OMB category extension for Hispanic or Latino
     *   {
     *     "url": "ombCategory",
     *     "valueCoding": {
     *       "system": "urn:oid:2.16.840.1.113883.6.238",
     *       "code": "2135-2",
     *       "display": "Hispanic or Latino"
     *     }
     *   }
     *          </pre>
     */
    @Override
    public Extension getEthinicityOmbExtension(String code, String description, String system) {
        // Optional<CodeSystem> codeSystem = codeSystemLookupService
        //         .lookupCodeInFile(MASTER_DATA_PATH_MAP.get("ombEthinicity"), code);
        // if (codeSystem.isPresent()) {
        //     if (system == null) {
        //         system = codeSystem.get().getSystem();
        //     }
        //     if (description == null) {
        //         description = codeSystem.get().getDisplay();
        //     }
        // }
        // Coding coding = new Coding()
        //         .setSystem(system) // TODO : check if system will be provided always
        //         .setCode(code)
        //         .setDisplay(description);
        // Extension ombCategoryExtension = new Extension()
        //         .setUrl("ombCategory") // TODO : check and move to enum
        //         .setValue(coding);
        // return ombCategoryExtension;
        return null;
    }

    /**
     * Creates an ethnicity detailed extension using the provided code, description,
     * and code system.
     * <p>
     * This method generates a FHIR extension for ethnicity details using the
     * specified code, description, and code system.
     * It creates a `Coding` object representing the ethnicity category and assigns
     * it to an extension with a URL of "detailed".
     * The extension is then returned for use in the FHIR resource.
     * </p>
     *
     * @param code        the code representing the ethnicity category (e.g.,
     *                    "2028-9" for Asian)
     * @param description the description of the ethnicity category (e.g., "Asian")
     * @param system      the code system URL for the ethnicity category (e.g.,
     *                    "urn:oid:2.16.840.1.113883.6.238")
     * @return an Extension object representing the detailed ethnicity category,
     *         containing a `Coding` with the provided code, description, and system
     *
     * @example
     * 
     *          <pre>
     *   Example 1: Detailed ethnicity extension for Asian
     *   {
     *     "url": "detailed",
     *     "valueCoding": {
     *       "system": "urn:oid:2.16.840.1.113883.6.238",
     *       "code": "2028-9",
     *       "display": "Asian"
     *     }
     *   }
     *
     *   Example 2: Detailed ethnicity extension for Hispanic or Latino
     *   {
     *     "url": "detailed",
     *     "valueCoding": {
     *       "system": "urn:oid:2.16.840.1.113883.6.238",
     *       "code": "2135-2",
     *       "display": "Hispanic or Latino"
     *     }
     *   }
     *          </pre>
     */
    @Override
    public Extension getEthinicityDetailedExtension(String code, String description, String system) {
        // Optional<CodeSystem> codeSystem = codeSystemLookupService
        //         .lookupCodeInFile(MASTER_DATA_PATH_MAP.get("detiledEthinicity"), code);
        // if (codeSystem.isPresent()) {
        //     if (system == null) {
        //         system = codeSystem.get().getSystem();
        //     }
        //     if (description == null) {
        //         description = codeSystem.get().getDisplay();
        //     }
        // }
        // Coding coding = new Coding()
        //         .setSystem(system)// TODO : check if system will be provided always
        //         .setCode(code)
        //         .setDisplay(description);
        // Extension detailedExtension = new Extension()
        //         .setUrl("detailed")// TODO : check and move to enum
        //         .setValue(coding);
        // return detailedExtension;
        return null;
    }

    /**
     * Creates an ethnicity extension based on the provided demographic data.
     * <p>
     * This method checks if the necessary ethnicity information (code, description,
     * and code system name) is available
     * in the provided `DemographicData`. If so, it creates the appropriate FHIR
     * extension based on the ethnicity code.
     * The method selects between an OMB (Office of Management and Budget) ethnicity
     * extension or a detailed ethnicity
     * extension based on the given code. If the code matches known OMB ethnicity
     * codes, the corresponding OMB extension
     * is created. Otherwise, a detailed ethnicity extension is generated.
     * </p>
     *
     * @param data the `DemographicData` object containing ethnicity-related
     *             information such as code, description, and code system name
     * @return an `Extension` object representing the ethnicity information, or
     *         `null` if the necessary data is not available
     */
    private Extension createEthnicityExtension(DemographicData data) {
        // if (data.getEthnicityCode() != null && data.getEthnicityCodeDescription() != null
        //         && data.getEthnicityCodeSystemName() != null) {
        //     return switch (data.getEthnicityCode()) {
        //         case "2135-2", "2186-5" -> getEthinicityOmbExtension(data.getEthnicityCode(),
        //                 data.getEthnicityCodeDescription(), data.getEthnicityCodeSystemName()); // TODO - revisit which
        //                                                                                         // all are omb
        //                                                                                         // ethinicity
        //                                                                                         // extensions and which
        //                                                                                         // are detailed
        //         default -> getEthinicityDetailedExtension(data.getEthnicityCode(), data.getEthnicityCodeDescription(),
        //                 data.getEthnicityCodeSystemName());
        //     };
        // }
        return null;
    }

    /**
     * Creates a race extension based on the provided demographic data.
     * <p>
     * This method checks if the necessary race information (code, description, and
     * code system name) is available
     * in the provided `DemographicData`. If so, it creates the appropriate FHIR
     * extension based on the race code.
     * The method selects between an OMB (Office of Management and Budget) race
     * extension or a detailed race extension
     * based on the given code. If the code matches known OMB race codes, the
     * corresponding OMB extension is created.
     * Otherwise, a detailed race extension is generated.
     * </p>
     *
     * @param data the `DemographicData` object containing race-related information
     *             such as code, description, and code system name
     * @return an `Extension` object representing the race information, or `null` if
     *         the necessary data is not available
     */
    private Extension createRaceExtension(DemographicData data) {
        // if (data.getRaceCode() != null && data.getRaceCodeDescription() != null
        //         && data.getRaceCodeSystemName() != null) {
        //     return switch (data.getRaceCode()) {
        //         case "1002-5", "2028-9", "2054-5", "2076-8", "2106-3" -> getRaceOmbExtension(data.getRaceCode(),
        //                 data.getRaceCodeDescription(), data.getRaceCodeSystemName()); // TODO - revisit which allare omb
        //                                                                               // race extensions
        //         default -> getRaceDetailedExtension(data.getRaceCode(), data.getRaceCodeDescription(),
        //                 data.getRaceCodeSystemName());
        //     };
        // }
        return null;
    }

    /**
     * Creates an Identifier object representing the MR (Medical Record) identifier
     * for a patient based on the provided demographic data.
     *
     * This method constructs an Identifier with a specific type code ("MR") and
     * sets its system to the facility ID provided in the demographic data. The
     * value of the identifier is set to the patient's medical record ID.
     *
     * @param data the demographic data object containing patient information
     * @return an Identifier object populated with the patient's MR details, or
     *         null if the patient medical record ID is empty or null
     *
     *         Example JSON representation of the generated Identifier:
     * 
     *         {
     *         "type": {
     *         "coding": [
     *         {
     *         "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
     *         "code": "MR"
     *         }
     *         ]
     *         },
     *         "system": "http://www.scn.gov/facility/CUMS",
     *         "value": "1234"
     *         }
     */
    private static void populateMrIdentifier(Patient patient, DemographicData data) {
        // if (StringUtils.isNotEmpty(data.getPatientMrId())) {
        //     Identifier identifier = new Identifier();
        //     Coding coding = new Coding();
        //     coding.setSystem(VALUESETS_MAP.get("patientIdentifier"));
        //     coding.setCode("MR");
        //     CodeableConcept type = new CodeableConcept();
        //     type.addCoding(coding);
        //     identifier.setType(type);
        //     identifier.setSystem(IDENTIFIER_SYSTEM_MAP.get("scnGovFacility") + "/" + data.getFacilityId());
        //     identifier.setValue(data.getPatientMrId());

        //     // Optional: Add assigner if needed (uncomment if required)
        //     // Reference assigner = new Reference();
        //     // assigner.setReference("Organization/OrganizationExampleOther-SCN1"); //TODO -
        //     // populate while organization is populated
        //     // identifier.setAssigner(assigner);

        //     // Set the identifier on the Patient object
        //     patient.addIdentifier(identifier);
        // }
    }

    /**
     * Creates an Identifier object representing the MA (Medicaid) identifier
     * for a patient based on the provided demographic data.
     *
     * This method constructs an Identifier with a specific type code ("MA") and
     * sets its system to the Medicaid system URL. The value of the identifier
     * is set to the patient's Medicaid CIN (Client Identification Number).
     *
     * @param data the demographic data object containing patient information
     * @return an Identifier object populated with the patient's Medicaid details,
     *         or null if the Medicaid CIN is empty or null
     *
     *         Example JSON representation of the generated Identifier:
     * 
     *         {
     *         "type": {
     *         "coding": [
     *         {
     *         "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
     *         "code": "MA"
     *         }
     *         ]
     *         },
     *         "system": "http://www.medicaid.gov",
     *         "value": "5555"
     *         }
     */
    private static void populateMaIdentifier(Patient patient, DemographicData data) {
        // if (StringUtils.isNotEmpty(data.getMedicaidCin())) {
        //     Identifier identifier = new Identifier();
        //     Coding coding = new Coding();
        //     coding.setSystem(VALUESETS_MAP.get("patientIdentifier"));
        //     coding.setCode("MA");
        //     CodeableConcept type = new CodeableConcept();
        //     type.addCoding(coding);
        //     identifier.setType(type);
        //     identifier.setSystem(IDENTIFIER_SYSTEM_MAP.get("medicaidGov"));
        //     identifier.setValue(data.getMedicaidCin());
        //     patient.addIdentifier(identifier);
        // }
    }

    /**
     * Creates an Identifier object representing the SS (Social Security)
     * identifier for a patient based on the provided demographic data.
     *
     * This method constructs an Identifier with a specific type code ("MA")
     * (indicating Medicaid or similar coding for Social Security use). It
     * sets the system to the Social Security Administration (SSA) system URL
     * and assigns the patient's SSN (Social Security Number) as the value.
     *
     * @param data the demographic data object containing patient information
     * @return an Identifier object populated with the patient's SSN details,
     *         or null if the SSN is empty or null
     *
     *         Example JSON representation of the generated Identifier:
     * 
     *         {
     *         "type": {
     *         "coding": [
     *         {
     *         "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
     *         "code": "MA"
     *         }
     *         ]
     *         },
     *         "system": "http://www.ssa.gov",
     *         "value": "4567"
     *         }
     */
    private static void populateSsnIdentifier(Patient patient, DemographicData data) {
        // if (StringUtils.isNotEmpty(data.getSsn())) {
        //     Identifier identifier = new Identifier();
        //     Coding coding = new Coding();
        //     coding.setSystem(VALUESETS_MAP.get("patientIdentifier"));
        //     coding.setCode("SSN");
        //     CodeableConcept type = new CodeableConcept();
        //     type.addCoding(coding);
        //     identifier.setType(type);
        //     identifier.setSystem(IDENTIFIER_SYSTEM_MAP.get("ssaGov"));
        //     identifier.setValue(data.getSsn());
        //     patient.addIdentifier(identifier);
        // }
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

    private static void populateBirthDate(Patient patient,DemographicData demographicData) {
        Optional.ofNullable(demographicData.getPatientBirthDate())
                .map(DateUtil::parseDate)
                .ifPresent(patient::setBirthDate);  // Sets birthDate only if present and parsed correctly
    }

    // Private method to populate phone from DemographicData
    private static void populatePhone(Patient patient, DemographicData demographicData) {
        // Optional.ofNullable(demographicData.getPhone())
        //         .ifPresent(phone -> patient.addTelecom(new ContactPoint()
        //                 .setSystem(ContactPoint.ContactPointSystem.PHONE)
        //                 .setValue(phone))); // Adds phone number to telecom if present
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

    private static void populatePreferredLanguage(Patient patient, DemographicData data) {
        // Optional.ofNullable(data.getPreferredLanguageCode())
        //         .filter(StringUtils::isNotEmpty)
        //         .ifPresent(languageCode -> {
        //             Coding coding = new Coding();
        //             coding.setCode(languageCode);

        //             CodeableConcept language = new CodeableConcept();
        //             language.addCoding(coding);

        //             PatientCommunicationComponent communication = new PatientCommunicationComponent();
        //             communication.setLanguage(language);
        //             communication.setPreferred(true);

        //             patient.addCommunication(communication);
        //         });
    }
}
