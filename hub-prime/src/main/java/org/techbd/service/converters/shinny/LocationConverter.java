package org.techbd.service.converters.shinny;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningData;
import org.techbd.model.csv.ScreeningResourceData;
import org.techbd.util.DateUtil;

/**
 * Converts data into a FHIR Location resource.
 */
@Component
public class LocationConverter extends BaseConverter {
    private static final Logger LOG = LoggerFactory.getLogger(LocationConverter.class.getName());

    /**
     * Returns the resource type associated with this converter.
     *
     * @return The FHIR ResourceType.Location enum.
     */
    @Override
    public ResourceType getResourceType() {
        return ResourceType.Location;
    }

    /**
     * Converts location-related data into a FHIR Location resource wrapped in a
     * BundleEntryComponent.
     *
     * @param bundle                The FHIR Bundle to which the location data is
     *                              related.
     * @param screeningResourceData The location data to be converted.
     * @param interactionId         The interaction ID used for tracking or
     *                              referencing
     *                              the conversion.
     * @return A BundleEntryComponent containing the converted FHIR Location
     *         resource.
     */
    @Override
    public BundleEntryComponent convert(Bundle bundle, DemographicData demographicData,
            List<ScreeningData> screeningDataList, QeAdminData qrAdminData, ScreeningResourceData screeningResourceData,
            String interactionId) {

        Location location = new Location();
        setMeta(location);
        

        // Set Location ID
        location.setId(generateUniqueId(interactionId));

        // Set Meta Data
        Meta meta = location.getMeta();
        meta.setLastUpdated(getLastUpdatedDate(screeningResourceData));

        // Set location status
        populateLocationStatus(location, screeningResourceData);

        // Set location name
        populateLocationName(location, screeningResourceData);

        // Set location type
        populateLocationType(location, screeningResourceData);

        // Set address
        populateAddress(location, screeningResourceData);

        // Set managing organization
        populateManagingOrganization(location, qrAdminData);

        // Set Physical  type
        populatePhysicalType(location, screeningResourceData);

        // Wrap the Location resource in a BundleEntryComponent
        BundleEntryComponent bundleEntryComponent = new BundleEntryComponent();
        bundleEntryComponent.setResource(location);
        return bundleEntryComponent;
    }

    private static void populateLocationStatus(Location location, ScreeningResourceData screeningResourceData) {
        if (screeningResourceData != null && screeningResourceData.getLocationStatus() != null) {
            location.setStatus(Location.LocationStatus.fromCode(screeningResourceData.getLocationStatus()));
        } else {
            location.setStatus(Location.LocationStatus.ACTIVE); // Default status
        }
    }

    private static void populateLocationName(Location location, ScreeningResourceData screeningResourceData) {
        if (screeningResourceData != null && screeningResourceData.getLocationName() != null) {
            location.setName(screeningResourceData.getLocationName());
        }
    }

    private static void populateLocationType(Location location, ScreeningResourceData screeningResourceData) {
        if (screeningResourceData != null && screeningResourceData.getLocationTypeCode() != null) {
            CodeableConcept type = new CodeableConcept();
            Coding coding = new Coding();
            coding.setSystem(screeningResourceData.getLocationTypeSystem());
            coding.setCode(screeningResourceData.getLocationTypeCode());
            type.addCoding(coding);
            location.setType(List.of(type));
        }
    }

    private static void populateAddress(Location location, ScreeningResourceData screeningResourceData) {
        if (screeningResourceData != null) {
            Address address = new Address();
            //TODO: need to generate id , address type, and  address line
           // address.setId("SCNAddressLocation");
           // address.setType(Location.AddressType.PHYSICAL);
           // address.setLine(List.of(screeningResourceData.getLine()));
            address.setCity(screeningResourceData.getLocationCity());
            address.setDistrict(screeningResourceData.getLocationDistrict());
            address.setState(screeningResourceData.getLocationState());
            address.setPostalCode(screeningResourceData.getLocationZip());
    
            location.setAddress(address);
        }
    }
    

    private static void populateManagingOrganization(Location location, QeAdminData qrAdminData) {
        if (qrAdminData != null && qrAdminData.getFacilityId() != null) {
            location.setManagingOrganization(
                    new Reference("Organization/" + qrAdminData.getFacilityId()));
        }
    }

    private static void populatePhysicalType(Location location, ScreeningResourceData screeningResourceData) {
        if (screeningResourceData != null && screeningResourceData.getLocationPhysicalTypeCode() != null) {
            CodeableConcept physicalType = new CodeableConcept();
            Coding coding = new Coding();
            coding.setSystem(screeningResourceData.getLocationPhysicalTypeSystem());
            coding.setCode(screeningResourceData.getLocationPhysicalTypeCode());
            physicalType.addCoding(coding);
            location.setPhysicalType(physicalType);
        }
    }
    

    /**
     * Generate a unique ID for the location based on its data.
     *
     * @param interactionId The interaction ID used to generate a unique identifier.
     * @return A unique ID for the location.
     */
    private String generateUniqueId(String interactionId) {
        return "Location-" + interactionId;
    }

    /**
     * Get the last updated date for the location based on its data.
     *
     * @param screeningResourceData The ScreeningResourceData object containing the location's last
     *                     updated date.
     * @return The last updated date.
     */
    private Date getLastUpdatedDate(ScreeningResourceData screeningResourceData) {
        if (screeningResourceData != null && screeningResourceData.getLocationLastUpdated() != null
                && !screeningResourceData.getLocationLastUpdated().isEmpty()) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                return dateFormat.parse(screeningResourceData.getLocationLastUpdated());
            } catch (ParseException e) {
                LOG.error("Error parsing last updated date", e);
            }
        }
        return new Date();
    }
}
