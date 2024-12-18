package org.techbd.service.converters.shinny;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
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
import org.techbd.util.CsvConstants;
import org.techbd.util.CsvConversionUtil;

/**
 * Converts data related to an Organization into a FHIR Organization resource.
 */
@Component
@Order(1)
public class OrganizationConverter extends BaseConverter {
    private static final Logger LOG = LoggerFactory.getLogger(OrganizationConverter.class.getName());

    /**
     * Returns the resource type associated with this converter.
     *
     * @return The FHIR ResourceType.Organization enum.
     */
    public ResourceType getResourceType() {
        return ResourceType.Organization;
    }

    /**
     * Converts organization-related data into a FHIR Organization resource
     * wrapped in a BundleEntryComponent.
     *
     * @param bundle            The FHIR Bundle to which the organization data is
     *                          related.
     * @param demographicData   The demographic data related to the patient (for
     *                          organization details).
     * @param qrAdminData       The administrative data related to the patient or
     *                          organization.
     * @param screeningDataList The list of screening data (if required for the
     *                          organization context).
     * @param interactionId     The interaction ID used for tracking or referencing
     *                          the conversion.
     * @return A BundleEntryComponent containing the converted FHIR Organization
     *         resource.
     */
    @Override
    public List<BundleEntryComponent>  convert(Bundle bundle,DemographicData demographicData,QeAdminData qeAdminData ,
    ScreeningProfileData screeningProfileData ,List<ScreeningObservationData> screeningObservationData,String interactionId,Map<String,String> idsGenerated) {
        Organization organization = new Organization();
        setMeta(organization);
        organization.setId("Organization/"+CsvConversionUtil.sha256(qeAdminData.getFacilityId())); // Assuming qrAdminData contains orgId
        idsGenerated.put(CsvConstants.ORGANIZATION_ID,organization.getId());
        Meta meta = organization.getMeta();
        meta.setLastUpdated(getLastUpdatedDate(qeAdminData));
        populateOrganizationName(organization, qeAdminData);
        populateOrganizationIdentifier(organization, qeAdminData);
        populateIsActive(organization, qeAdminData);
        populateOrganizationType(organization, qeAdminData);
        populateOrganizationAddress(organization, qeAdminData);
        BundleEntryComponent bundleEntryComponent = new BundleEntryComponent();
        bundleEntryComponent.setResource(organization);
        return List.of(bundleEntryComponent);
    }

    private static void populateOrganizationName(Organization organization, QeAdminData qrAdminData) {
        if (qrAdminData.getFacilityName() != null) {
            organization.setName(qrAdminData.getFacilityName());
        }
    }

    private static void populateOrganizationIdentifier(Organization organization, QeAdminData data) {
        if (StringUtils.isNotEmpty(data.getFacilityId())) {
            Identifier identifier = new Identifier();
            Coding coding = new Coding();
            coding.setDisplay("Care Ridge");  //Static_Value
            CodeableConcept type = new CodeableConcept();
            type.addCoding(coding);
            identifier.setType(type);
            identifier.setValue(data.getFacilityName());
            organization.addIdentifier(identifier);
        }
    }

    private static void populateOrganizationType(Organization organization, QeAdminData data) {
        // if (StringUtils.isNotEmpty(data.getFacilityCmsIdentifierTypeCode())) {
            // Create a new CodeableConcept for type
            CodeableConcept type = new CodeableConcept();

            // Create a new Coding object
            Coding coding = new Coding();
            String system = "http://terminology.hl7.org/CodeSystem/organization-type";  //TODO : remove static reference
            coding.setSystem(system); // Set the system
            coding.setCode(data.getOrganizationTypeCode()); // Set the code (e.g., "other")
            coding.setDisplay(data.getOrganizationTypeDisplay()); // Set the display (e.g., "Other")

            // Add the coding to the type
            type.addCoding(coding);

            // Set the type on the Organization object
            organization.setType(Collections.singletonList(type));
        // }
    }

    private static void populateOrganizationAddress(Organization organization, QeAdminData qrAdminData) {
        if (qrAdminData.getFacilityAddress1() != null || qrAdminData.getFacilityCity() != null ||
                qrAdminData.getFacilityState() != null || qrAdminData.getFacilityZip() != null) {

            Address address = new Address();

            // Set the full address text by concatenating address components
            String fullAddressText = qrAdminData.getFacilityAddress1();
            if (qrAdminData.getFacilityCity() != null) {
                fullAddressText += ", " + qrAdminData.getFacilityCity();
            }
            if (qrAdminData.getFacilityState() != null) {
                fullAddressText += ", " + qrAdminData.getFacilityState();
            }
            if (qrAdminData.getFacilityZip() != null) {
                fullAddressText += " " + qrAdminData.getFacilityZip();
            }
            address.setText(fullAddressText);

            // Set the address line (address line 1 and possibly address line 2)
            List<String> addressLines = new ArrayList<>();
            addressLines.add(qrAdminData.getFacilityAddress1());

            // Add address line 2 if it's available
            // if (qrAdminData.getFacilityAddress2() != null) {    //BlankInTheCSV
            //     addressLines.add(qrAdminData.getFacilityAddress2());
            // }

            // address.setLine(addressLines);

            // Set the city, district, state, and postal code
            address.setCity(qrAdminData.getFacilityCity());
            address.setDistrict(qrAdminData.getFacilityDistrict());
            address.setState(qrAdminData.getFacilityState());
            address.setPostalCode(qrAdminData.getFacilityZip());

            // Add the address to the organization
            organization.addAddress(address);
        }
    }

    private static void populateIsActive(Organization organization, QeAdminData qrAdminData) {
        if (StringUtils.isNotEmpty("TRUE")) {  //Static_Value
            organization.setActive(Boolean.parseBoolean("TRUE"));
        }
    }

    /**
     * Get the last updated date for the organization based on its data from
     * QeAdminData.
     *
     * @param qrAdminData The QeAdminData object containing the facility's last
     *                    updated date.
     * @return The last updated date.
     */
    private Date getLastUpdatedDate(QeAdminData qrAdminData) {
        // Check if the facilityLastUpdated field in QeAdminData is not null or empty
        if (qrAdminData != null && qrAdminData.getFacilityLastUpdated() != null
                && !qrAdminData.getFacilityLastUpdated().isEmpty()) {
            try {
                // Example: Assuming the format is "yyyy-MM-dd'T'HH:mm:ss'Z'" (ISO 8601 format)
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                return dateFormat.parse(qrAdminData.getFacilityLastUpdated()); // Return the parsed date
            } catch (ParseException e) {
                return new Date();
            }
        }
        return new Date();
    }

}
