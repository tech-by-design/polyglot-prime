package org.techbd.converters.csv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.config.CoreUdiPrimeJpaConfig;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.csv.CodeLookupService;
import org.techbd.util.AppLogger;
import org.techbd.util.DateUtil;
import org.techbd.util.TemplateLogger;
import org.techbd.util.csv.CsvConstants;
import org.techbd.util.csv.CsvConversionUtil;

/**
 * Converts data related to an Organization into a FHIR Organization resource.
 */
@Component
@Order(1)
public class OrganizationConverter extends BaseConverter {
    private final TemplateLogger LOG;
    public OrganizationConverter(CodeLookupService codeLookupService,final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig, AppLogger appLogger) {
        super(codeLookupService,coreUdiPrimeJpaConfig);
        LOG = appLogger.getLogger(OrganizationConverter.class);
    }


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
    ScreeningProfileData screeningProfileData ,List<ScreeningObservationData> screeningObservationData,String interactionId,Map<String,String> idsGenerated,String baseFHIRUrl) {
        Organization organization = new Organization();
        setMeta(organization,baseFHIRUrl);
        organization.setId(CsvConversionUtil.sha256(qeAdminData.getFacilityId())); // Assuming qrAdminData contains orgId
        idsGenerated.put(CsvConstants.ORGANIZATION_ID,organization.getId());
        String fullUrl = "http://shinny.org/us/ny/hrsn/Organization/" + organization.getId();
        Meta meta = organization.getMeta();
        meta.setLastUpdated(DateUtil.parseDate(qeAdminData.getFacilityLastUpdated()));
        populateOrganizationName(organization, qeAdminData);
        populateOrganizationIdentifier(organization, screeningProfileData, interactionId);
        populateIsActive(organization, qeAdminData);
        populateOrganizationType(organization, qeAdminData, interactionId);
        populateOrganizationAddress(organization, qeAdminData, interactionId);
         
        BundleEntryComponent bundleEntryComponent = new BundleEntryComponent();
        bundleEntryComponent.setFullUrl(fullUrl);
        bundleEntryComponent.setRequest(new Bundle.BundleEntryRequestComponent().setMethod(HTTPVerb.POST).setUrl("http://shinny.org/us/ny/hrsn/Organization/" + organization.getId()));
        bundleEntryComponent.setResource(organization);
        return List.of(bundleEntryComponent);
    }

    private static void populateOrganizationName(Organization organization, QeAdminData qrAdminData) {
        if (qrAdminData.getFacilityName() != null) {
            organization.setName(qrAdminData.getFacilityName());
        }
    }

    private void populateOrganizationIdentifier(Organization organization, ScreeningProfileData data, String interactionId) {
        if (StringUtils.isNotEmpty(data.getFacilityId())) {

            Map<String, String> systemToCodeMap = Map.of(
                    "http://hl7.org/fhir/sid/us-npi", "NPI",
                    "http://www.medicaid.gov/", "MA",
                    "http://www.irs.gov/", "TAX");

            String rawSystem = data.getScreeningEntityIdCodeSystem(); // system string from CSV
            String code = systemToCodeMap.getOrDefault(rawSystem, "UNKNOWN");
            String system = fetchSystem(code, rawSystem, CsvConstants.SCREENING_ENITITY_ID, interactionId);

            Identifier identifier = new Identifier();
            Coding coding = new Coding();
            coding.setSystem(system);
            coding.setCode(code);
            CodeableConcept type = new CodeableConcept();
            type.addCoding(coding);
            identifier.setType(type);
            identifier.setSystem(system);
            identifier.setValue(data.getScreeningEntityId());
            organization.addIdentifier(identifier);
        }
    }    

    private void populateOrganizationType(Organization organization, QeAdminData data, String interactionId) {
        if (StringUtils.isNotEmpty(data.getOrganizationTypeCode()) || StringUtils.isNotEmpty(data.getOrganizationTypeDisplay())) {
            CodeableConcept type = new CodeableConcept();

            // Split codes and displays
            String[] codes = StringUtils.defaultString(data.getOrganizationTypeCode()).split(";");
            String[] displays = StringUtils.defaultString(data.getOrganizationTypeDisplay()).split(";");

            for (int i = 0; i < codes.length; i++) {
                String rawCode = codes[i].trim();
                if (StringUtils.isNotEmpty(rawCode)) {
                    String code = fetchCode(rawCode, CsvConstants.ORGANIZATION_TYPE_CODE, interactionId);
                    String display = (i < displays.length) ? displays[i].trim() : null;
                    display = fetchDisplay(code, display, CsvConstants.ORGANIZATION_TYPE_CODE, interactionId);

                    Coding coding = new Coding();
                    coding.setCode(code);
                    coding.setDisplay(display);
                    coding.setSystem(fetchSystem(code, data.getOrganizationTypeCodeSystem(), CsvConstants.ORGANIZATION_TYPE_CODE, interactionId));

                    type.addCoding(coding);
                }
            }

            organization.setType(Collections.singletonList(type));
        }
    }

    private void populateOrganizationAddress(Organization organization, QeAdminData qrAdminData, String interactionId) {
        if (StringUtils.isNotEmpty(qrAdminData.getFacilityAddress1()) || StringUtils.isNotEmpty(qrAdminData.getFacilityCity()) ||
            StringUtils.isNotEmpty(qrAdminData.getFacilityState()) || StringUtils.isNotEmpty(qrAdminData.getFacilityZip())) {

            Address address = new Address();

            String fullAddressText = qrAdminData.getFacilityAddress1();
            //please do confirm
            // if (StringUtils.isNotEmpty(qrAdminData.getFacilityAddress2())) {
            //     fullAddressText += ", " + qrAdminData.getFacilityAddress2();
            // }
            if (StringUtils.isNotEmpty(qrAdminData.getFacilityCity())) {
                fullAddressText += ", " + qrAdminData.getFacilityCity();
            }
            
            if (StringUtils.isNotEmpty(qrAdminData.getFacilityState())) {
                String originalValue = qrAdminData.getFacilityState();
                String code = fetchCode(originalValue, CsvConstants.STATE, interactionId);

                if (!code.equalsIgnoreCase(originalValue)) {
                    address.setState(code);
                } else {
                    String codeFromDisplay = fetchCodeFromDisplay(originalValue, CsvConstants.STATE, interactionId);
                    if (codeFromDisplay != null) {
                        address.setState(codeFromDisplay);
                    } else {
                        address.setState(originalValue);
                    }
                }

                fullAddressText += ", " + address.getState();
            }

            if (StringUtils.isNotEmpty(qrAdminData.getFacilityZip())) {
                fullAddressText += " " + qrAdminData.getFacilityZip();
            }
            address.setText(fullAddressText);

            if (StringUtils.isNotEmpty(qrAdminData.getFacilityAddress1())){
                List<StringType> addressLines = new ArrayList<>();
                addressLines.add(new StringType(qrAdminData.getFacilityAddress1()));
                if (StringUtils.isNotEmpty(qrAdminData.getFacilityAddress2())){
                    addressLines.add(new StringType(qrAdminData.getFacilityAddress2()));
                }
                    address.setLine(addressLines);
            
            }
        

            address.setCity(qrAdminData.getFacilityCity());
            address.setDistrict(qrAdminData.getFacilityCounty());
            address.setPostalCode(qrAdminData.getFacilityZip());

            organization.addAddress(address);
        }
    }

    private static void populateIsActive(Organization organization, QeAdminData qrAdminData) {
        if (StringUtils.isNotEmpty("TRUE")) {  //TODO : remove static reference
            organization.setActive(Boolean.parseBoolean("TRUE"));
        }
    }
}
