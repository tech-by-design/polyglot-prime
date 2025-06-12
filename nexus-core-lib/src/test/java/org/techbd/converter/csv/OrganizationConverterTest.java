package org.techbd.converter.csv;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.SoftAssertions;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.converters.csv.OrganizationConverter;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.csv.CodeLookupService;
import org.techbd.util.fhir.CoreFHIRUtil;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

@ExtendWith(MockitoExtension.class)
class OrganizationConverterTest {
    private static final Logger LOG = LoggerFactory.getLogger(PatientConverterTest.class.getName());
   
    @Mock
    CodeLookupService codeLookupService;

    @InjectMocks
    private OrganizationConverter organizationConverter;

    @BeforeEach
    void setUp() throws Exception {
            Field profileMapField = CoreFHIRUtil.class.getDeclaredField("PROFILE_MAP");
            profileMapField.setAccessible(true);
            profileMapField.set(null, CsvTestHelper.getProfileMap());
            Field baseFhirUrlField = CoreFHIRUtil.class.getDeclaredField("BASE_FHIR_URL");
            baseFhirUrlField.setAccessible(true);
            baseFhirUrlField.set(null, CsvTestHelper.BASE_FHIR_URL);
    }

    @Test
//     @Disabled
    void testConvert() throws Exception {
        // Create the necessary data objects for the test
        final Bundle bundle = new Bundle();
        final DemographicData demographicData =  CsvTestHelper.createDemographicData();
        final List<ScreeningObservationData> screeningDataList =  CsvTestHelper.createScreeningObservationData();
        final QeAdminData qrAdminData =  CsvTestHelper.createQeAdminData();
        final ScreeningProfileData screeningResourceData =  CsvTestHelper.createScreeningProfileData();
        final Map<String, String> idsGenerated = new HashMap<>();
        // Call the convert method of the organization converter
        final BundleEntryComponent result = organizationConverter
                .convert(bundle, demographicData, qrAdminData, screeningResourceData, screeningDataList,
                        "interactionId",idsGenerated,null)
                .get(0);

        // Create soft assertions to verify the result
        final SoftAssertions softly = new SoftAssertions();

        // Assert that the result is not null
        softly.assertThat(result).isNotNull();

        // Assert that the result resource is an instance of Organization
        softly.assertThat(result.getResource()).isInstanceOf(Organization.class);

        // Cast the result to Organization and assert various properties
        final Organization organization = (Organization) result.getResource();

        // Assert that the organization ID is not null or empty and matches expected
        // value
        softly.assertThat(organization.getId()).isNotEmpty();
        softly.assertThat(organization.getId())
                .isEqualTo("401faef5898b5cf2724938b4369644bee72183c1c92f31d403a6b2538a069944");

        // Assert that the organization has a name and it matches the expected name
        softly.assertThat(organization.hasName()).isTrue();
        softly.assertThat(organization.getName()).isEqualTo("Care Ridge SCN");

        // Assert that the organization has the correct identifier
        final Identifier mrIdentifier = organization.getIdentifier().stream()
                .filter(identifier -> "SCNExample".equals(identifier.getValue()))
                .findFirst()
                .orElse(null);

        softly.assertThat(mrIdentifier).isNotNull();
       // softly.assertThat(mrIdentifier.getSystem()).isEqualTo("http://www.scn.ny.gov/");
        //softly.assertThat(mrIdentifier.getValue()).isEqualTo("SCNExample");

        // Assert that the organization has no extensions (since the output does not
        // have extensions)
        softly.assertThat(organization.getExtension()).hasSize(0);

        // Assert that the organization has the correct address
        softly.assertThat(organization.getAddress()).hasSize(1);
        final Address address = organization.getAddressFirstRep();
        softly.assertThat(address.getText()).isEqualTo("111 Care Ridge St, Plainview, NY 11803");
        softly.assertThat(address.getCity()).isEqualTo("Plainview");
        softly.assertThat(address.getDistrict()).isEqualTo("Nassau County");
        softly.assertThat(address.getState()).isEqualTo("NY");
        softly.assertThat(address.getPostalCode()).isEqualTo("11803");
        softly.assertThat(address.getLine()).hasSize(1);
        softly.assertThat(address.getLine().get(0).getValue()).isEqualTo("111 Care Ridge St");

        // Assert that the organization has the correct type
        softly.assertThat(organization.getType()).hasSize(1);
        final CodeableConcept type = organization.getTypeFirstRep();
        softly.assertThat(type.getCodingFirstRep().getSystem())
                .isEqualTo("http://terminology.hl7.org/CodeSystem/organization-type");
        softly.assertThat(type.getCodingFirstRep().getCode()).isEqualTo("other");
        softly.assertThat(type.getCodingFirstRep().getDisplay()).isEqualTo("Other");

        //Assert that organization active is true
        softly.assertThat(organization.getActive()).isTrue();

        // Assert fullUrl
        softly.assertThat(result.getFullUrl()).isEqualTo("http://shinny.org/us/ny/hrsn/Organization/" + organization.getId());
        softly.assertThat(result.getRequest().getMethod()).isEqualTo(org.hl7.fhir.r4.model.Bundle.HTTPVerb.POST);
        softly.assertThat(result.getRequest().getUrl()).isEqualTo("http://shinny.org/us/ny/hrsn/Organization/" + organization.getId());

        // Assert all soft assertions
       // softly.assertAll();
    }

    @Test
//     @Disabled
    void testGeneratedJson() throws Exception {
        final var bundle = new Bundle();
        final var demographicData =  CsvTestHelper.createDemographicData();
        final var screeningDataList =  CsvTestHelper.createScreeningObservationData();
        final var qrAdminData =  CsvTestHelper.createQeAdminData();
        final Map<String, String> idsGenerated = new HashMap<>();
        final ScreeningProfileData screeningResourceData =  CsvTestHelper.createScreeningProfileData();
        final var result = organizationConverter.convert(bundle, demographicData, qrAdminData, screeningResourceData,
                screeningDataList,
                "interactionId", idsGenerated,CsvTestHelper.BASE_FHIR_URL);
        final Organization organization = (Organization) result.get(0).getResource();
        final var filePath = "src/test/resources/org/techbd/csv/generated-json/organization.json";
        final FhirContext fhirContext = FhirContext.forR4();
        final IParser fhirJsonParser = fhirContext.newJsonParser().setPrettyPrint(true);
        final String fhirResourceJson = fhirJsonParser.encodeResourceToString(organization);
        final Path outputPath = Paths.get(filePath);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, fhirResourceJson);
    }
}
