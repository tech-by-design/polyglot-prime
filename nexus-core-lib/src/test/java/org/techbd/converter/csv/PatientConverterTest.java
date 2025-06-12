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
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.converters.csv.PatientConverter;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.csv.CodeLookupService;
import org.techbd.util.fhir.CoreFHIRUtil;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

@ExtendWith(MockitoExtension.class)
class PatientConverterTest {
        private static final Logger LOG = LoggerFactory.getLogger(PatientConverterTest.class.getName());
     
        @Mock
        CodeLookupService codeLookupService;

        @InjectMocks
        private PatientConverter patientConverter;

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
        // @Disabled
        void testConvert() throws Exception {
                final Bundle bundle = new Bundle();
                final DemographicData demographicData = CsvTestHelper.createDemographicData();
                final List<ScreeningObservationData> screeningDataList =  CsvTestHelper.createScreeningObservationData();
                final QeAdminData qrAdminData =  CsvTestHelper.createQeAdminData();
                final ScreeningProfileData screeningResourceData =  CsvTestHelper.createScreeningProfileData();
                final Map<String, String> idsGenerated = new HashMap<>();
                final BundleEntryComponent result = patientConverter
                                .convert(bundle, demographicData, qrAdminData, screeningResourceData,
                                                screeningDataList, "interactionId", idsGenerated,null)
                                .get(0);
                final SoftAssertions softly = new SoftAssertions();

                softly.assertThat(result).isNotNull();
                softly.assertThat(result.getResource()).isInstanceOf(Patient.class);

                final Patient patient = (Patient) result.getResource();
                softly.assertThat(patient.getId()).isNotEmpty();
                softly.assertThat(patient.getId())
                                .isNotNull()
                                .isEqualTo("c8538406d9cbf420fefe69f60b40f6573cd4ccd4cfb21b3301118b086a367163");
                softly.assertThat(patient.getLanguage()).isEqualTo("en");
                softly.assertThat(patient.hasName()).isTrue();
                softly.assertThat(patient.getNameFirstRep().getGivenAsSingleString()).isEqualTo("Jon");
                softly.assertThat(patient.getNameFirstRep().getFamily()).isEqualTo("Doe");
                softly.assertThat(patient.hasGender()).isTrue();
                softly.assertThat(patient.getGender().toCode()).isEqualTo("male");
                softly.assertThat(patient.getBirthDate()).isNotNull();
                softly.assertThat(patient.getAddressFirstRep().getCity()).isEqualTo("New York");
                final Identifier mrIdentifier = patient.getIdentifier().stream()
                                .filter(identifier -> "MR".equals(identifier.getType().getCodingFirstRep().getCode()))
                                .findFirst()
                                .orElse(null);
                softly.assertThat(mrIdentifier).isNotNull();
                softly.assertThat(mrIdentifier.getSystem()).isEqualTo("http://www.scn.gov/facility/CUMC");
                softly.assertThat(mrIdentifier.getValue()).isEqualTo("11223344");
                softly.assertThat(mrIdentifier.getType().getCodingFirstRep().getSystem())
                                .isEqualTo("http://terminology.hl7.org/CodeSystem/v2-0203");
                final Identifier maIdentifier = patient.getIdentifier().stream()
                                .filter(identifier -> "MA".equals(identifier.getType().getCodingFirstRep().getCode()))
                                .findFirst()
                                .orElse(null);
                softly.assertThat(maIdentifier).isNotNull();
                softly.assertThat(maIdentifier.getSystem()).isEqualTo("http://www.medicaid.gov/");
                softly.assertThat(maIdentifier.getValue()).isEqualTo("AA12345C");
                final Identifier ssnIdentifier = patient.getIdentifier().stream()
                                .filter(identifier -> "SS".equals(identifier.getType().getCodingFirstRep().getCode()))
                                .findFirst()
                                .orElse(null);
                softly.assertThat(ssnIdentifier).isNotNull();
                softly.assertThat(ssnIdentifier.getSystem()).isEqualTo("http://www.ssa.gov/");
                softly.assertThat(ssnIdentifier.getValue()).isEqualTo("999-34-2964");
                softly.assertThat(maIdentifier.getType().getCodingFirstRep().getSystem())
                                .isEqualTo("http://terminology.hl7.org/CodeSystem/v2-0203");
                softly.assertThat(maIdentifier.getType().getCodingFirstRep().getCode())
                                .isEqualTo("MA");
                softly.assertThat(ssnIdentifier.getType().getCodingFirstRep().getSystem())
                                .isEqualTo("http://terminology.hl7.org/CodeSystem/v2-0203");
                softly.assertThat(ssnIdentifier.getType().getCodingFirstRep().getCode())
                                .isEqualTo("SS");

                softly.assertThat(patient.getExtension()).hasSize(5);
                softly.assertThat(patient.getExtensionByUrl("http://hl7.org/fhir/us/core/StructureDefinition/us-core-race")).isNotNull();
                softly.assertThat(patient.getExtensionByUrl("http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity")).isNotNull();
                softly.assertThat(patient.getExtensionByUrl("http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex")).isNotNull();
                softly.assertThat(patient.getExtensionByUrl("http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-personal-pronouns")).isNotNull();
                softly.assertThat(patient.getExtensionByUrl("http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-gender-identity")).isNotNull();

                softly.assertThat(patient.hasAddress()).isTrue();
                final Address address = patient.getAddressFirstRep();
                softly.assertThat(address.getLine().stream()
                                .map(StringType::getValue))
                                .containsExactly("115 Broadway Apt2");// , "dummy_address2");
                softly.assertThat(address.getCity()).isEqualTo("New York");
                softly.assertThat(address.getState()).isEqualTo("NY");
                softly.assertThat(address.getPostalCode()).isEqualTo("10032");
                softly.assertThat(address.getDistrict()).isEqualTo("MANHATTAN");

                softly.assertThat(patient.hasContact()).isTrue();
                final Patient.ContactComponent contact = patient.getContactFirstRep();
                softly.assertThat(contact.getRelationshipFirstRep().getCodingFirstRep().getSystem())
                                .isEqualTo("http://terminology.hl7.org/CodeSystem/v2-0063");
                softly.assertThat(contact.getRelationshipFirstRep().getCodingFirstRep().getCode())
                                .isEqualTo("MTH");
                softly.assertThat(contact.getRelationshipFirstRep().getCodingFirstRep().getDisplay())
                                .isEqualTo("Mother");
                softly.assertThat(contact.getName().getFamily()).isEqualTo("Doe");
                softly.assertThat(contact.getName().getGiven().stream()
                                .map(StringType::getValue))
                                .contains("Joyce");
                softly.assertThat(contact.getTelecomFirstRep().getSystem())
                                .isEqualTo(ContactPoint.ContactPointSystem.PHONE);
                softly.assertThat(contact.getTelecomFirstRep().getValue()).isEqualTo("1234567890");
                
                softly.assertThat(patient.hasCommunication()).isTrue();
                final Patient.PatientCommunicationComponent communication = patient.getCommunicationFirstRep();
                softly.assertThat(communication.getLanguage().getCodingFirstRep().getSystem())
                                .isEqualTo("urn:ietf:bcp:47");
                softly.assertThat(communication.getLanguage().getCodingFirstRep().getCode())
                                .isEqualTo("en");
                softly.assertThat(communication.getPreferred()).isTrue();
                //softly.assertAll();
        }

        @Test
        // @Disabled
        void testGeneratedJson() throws Exception {
                final var bundle = new Bundle();
                final var demographicData =  CsvTestHelper.createDemographicData();
                final var screeningDataList =  CsvTestHelper.createScreeningObservationData();
                final var qrAdminData =  CsvTestHelper.createQeAdminData();
                final Map<String, String> idsGenerated = new HashMap<>();
                final ScreeningProfileData screeningResourceData =  CsvTestHelper.createScreeningProfileData();
                final var result = patientConverter.convert(bundle, demographicData, qrAdminData, screeningResourceData,
                                screeningDataList,
                                "interactionId", idsGenerated,CsvTestHelper.BASE_FHIR_URL);
                final Patient patient = (Patient) result.get(0).getResource();
                final var filePath = "src/test/resources/org/techbd/csv/generated-json/patient.json";
                final FhirContext fhirContext = FhirContext.forR4();
                final IParser fhirJsonParser = fhirContext.newJsonParser().setPrettyPrint(true);
                final String fhirResourceJson = fhirJsonParser.encodeResourceToString(patient);
                final Path outputPath = Paths.get(filePath);
                Files.createDirectories(outputPath.getParent());
                Files.writeString(outputPath, fhirResourceJson);
        }
}
