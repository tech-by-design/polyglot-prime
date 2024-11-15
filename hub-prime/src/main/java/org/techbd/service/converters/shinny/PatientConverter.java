package org.techbd.service.converters.shinny;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningData;
import org.techbd.util.DateUtil;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

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
     * @param bundle The FHIR Bundle to which the patient data is related.
     * @param demographicData The demographic data of the patient.
     * @param screeningDataList The list of screening data relevant to the patient.
     * @param qrAdminData The administrative data related to the patient.
     * @param interactionId The interaction ID used for tracking or referencing the conversion.
     * @return A BundleEntryComponent containing the converted FHIR Patient resource.
     */
    @Override
    public BundleEntryComponent convert(Bundle bundle, DemographicData demographicData,
            List<ScreeningData> screeningDataList,
            QeAdminData qrAdminData, String interactionId) {
        Patient patient = new Patient();
        setMeta(patient); // setting profile url
        ScreeningData screeningData = screeningDataList.get(0); 
        //all records in a file have same encounter id , facility id , patient mrn id
        patient.setId(generateUniqueId(screeningData.getEncounterId(),screeningData.getFacilityId(),screeningData.getPatientMrId())); 
        Meta meta = patient.getMeta();
        meta.setLastUpdated(getMaxLastUpdatedDate(screeningDataList)); //max date available in all screening records
        patient.setLanguage("en");
    //     patient.addIdentifier().setSystem("http://hospital.org/mrn").setValue(patientId);
    //     patient.addName()
    //             .setFamily(familyName)
    //             .addGiven(givenName)
    //             .addPrefix(prefix)
    //             .addSuffix(suffix);

    //     // Add middle name as an extension
    //     patient.getNameFirstRep()
    //             .addExtension(new Extension()
    //                     .setUrl("http://shinny.org/ImplementationGuide/HRSN/StructureDefinition/middle-name")
    //                     .setValue(new org.hl7.fhir.r4.model.StringType(middleName)));

    //     patient.setBirthDateElement(new org.hl7.fhir.r4.model.DateType(isoBirthDate));
    //     patient.setGender(gender.equals("M") ? org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.MALE
    //             : org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.FEMALE);
    //    patient.setMeta(meta);
    //github.com/qe-collaborative-services/1115-hub
    //https://chatgpt.com/share/67318a9f-d0d4-800e-829b-b26e7476bec4
    //https://shinny.org/us/ny/hrsn/downloads.html
    //https://github.com/Shreeja-dev/polyglot-prime/blob/main/support/specifications/datapackage-ig.json
    //https://github.com/tech-by-design/polyglot-prime/issues/745
        BundleEntryComponent bundleEntryComponent = new BundleEntryComponent();
        bundleEntryComponent.setResource(patient);
        return bundleEntryComponent;
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
     * Returns the maximum lastUpdatedDate from a list of ScreeningData objects as a java.util.Date.
     * If no valid date is found, returns null.
     *
     * @param screeningDataList List of ScreeningData objects.
     * @return Maximum lastUpdatedDate as a java.util.Date or null if no valid dates are found.
     */
    public static Date getMaxLastUpdatedDate(List<ScreeningData> screeningDataList) {
        // Ensure the stream processes Instant objects and returns a java.util.Date
        return screeningDataList.stream()
                .map(ScreeningData::getEncounterLastUpdated)  // Assuming this method returns a String
                .filter(dateStr -> dateStr != null && !dateStr.isEmpty())  // Filter out null or empty strings
                .map(DateUtil::convertStringToDate)  // Convert string to java.util.Date
                .filter(date -> date != null)  // Filter out null Date values
                .max(Comparator.naturalOrder())  // Get the maximum Date
                .orElse(null);  // Return null if no valid date is found
    }


    @Override
    public String getId() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getId'");
    }


    @Override
    public String getFirstName() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getFirstName'");
    }


    @Override
    public String getLastName() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getLastName'");
    }


    @Override
    public String getMiddleName() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMiddleName'");
    }


    @Override
    public Identifier getMRN(String system, String value, String assigner) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMRN'");
    }


    @Override
    public Identifier getMPIID(String system, String value, String assigner) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMPIID'");
    }


    @Override
    public Identifier getSSN(String system, String value, String assigner) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSSN'");
    }


}
