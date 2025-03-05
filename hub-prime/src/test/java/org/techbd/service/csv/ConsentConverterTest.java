package org.techbd.service.csv;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.SoftAssertions;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Consent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.converters.shinny.ConsentConverter;
import org.techbd.util.FHIRUtil;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

@ExtendWith(MockitoExtension.class)
class ConsentConverterTest {
    private static final Logger LOG = LoggerFactory.getLogger(ConsentConverterTest.class.getName());

    @InjectMocks
    private ConsentConverter consentConverter;

    @BeforeEach
    void setUp() throws Exception {
            Field profileMapField = FHIRUtil.class.getDeclaredField("PROFILE_MAP");
            profileMapField.setAccessible(true);
            profileMapField.set(null, CsvTestHelper.getProfileMap());
            Field baseFhirUrlField = FHIRUtil.class.getDeclaredField("BASE_FHIR_URL");
            baseFhirUrlField.setAccessible(true);
            baseFhirUrlField.set(null, CsvTestHelper.BASE_FHIR_URL);
    }

    @Test
    //@Disabled
    void testConvert() throws Exception {
        // Create the necessary data objects for the test
        final Bundle bundle = new Bundle();
        final DemographicData demographicData = CsvTestHelper.createDemographicData();
        final List<ScreeningObservationData> screeningDataList = CsvTestHelper.createScreeningObservationData();
        final QeAdminData qrAdminData = CsvTestHelper.createQeAdminData();
        final Map<String, String> idsGenerated = new HashMap<>();
        final ScreeningProfileData screeningResourceData = CsvTestHelper.createScreeningProfileData();

        // Call the convert method of the consent converter
        final BundleEntryComponent result = consentConverter.convert(bundle, demographicData, qrAdminData, screeningResourceData,
                 screeningDataList, "interactionId", idsGenerated,null).get(0);

        // Create soft assertions to verify the result
        final SoftAssertions softly = new SoftAssertions();

        // Assert that the result is not null
        softly.assertThat(result).isNotNull();

        // Assert that the result resource is an instance of Consent
        softly.assertThat(result.getResource()).isInstanceOf(Consent.class);

        // Cast the result to Consent and assert various properties
        final Consent consent = (Consent) result.getResource();

        // Assert that the consent ID is not null or empty and matches expected
        softly.assertThat(consent.getId()).isNotEmpty();
        softly.assertThat(consent.getId()).matches("[a-f0-9]{64}");
        // softly.assertThat(consent.getId()).isEqualTo("Consent-interactionId");

        // Assert that the consent status is active
        softly.assertThat(consent.getStatus()).isEqualTo(Consent.ConsentState.ACTIVE);

        // Assert that the consent has the correct consent type
        softly.assertThat(consent.getCategory()).hasSize(2);
        softly.assertThat(consent.getCategoryFirstRep().getCodingFirstRep().getCode()).isEqualTo("59284-0");

        // Assert all soft assertions
        softly.assertAll();
    }

    @Test
   // @Disabled
    void testGeneratedJson() throws Exception {
        final var bundle = new Bundle();
        final var demographicData = CsvTestHelper.createDemographicData();
        final var screeningDataList = CsvTestHelper.createScreeningObservationData();
        final var qrAdminData = CsvTestHelper.createQeAdminData();
        final Map<String, String> idsGenerated = new HashMap<>();
        final ScreeningProfileData screeningResourceData = CsvTestHelper.createScreeningProfileData();

        final var result = consentConverter.convert(bundle, demographicData, qrAdminData, screeningResourceData,
                screeningDataList,
                "interactionId", idsGenerated,CsvTestHelper.BASE_FHIR_URL);

        final Consent consent = (Consent) result.get(0).getResource();
        final var filePath = "src/test/resources/org/techbd/csv/generated-json/consent.json";
        final FhirContext fhirContext = FhirContext.forR4();
        final IParser fhirJsonParser = fhirContext.newJsonParser().setPrettyPrint(true);
        final String fhirResourceJson = fhirJsonParser.encodeResourceToString(consent);
        final Path outputPath = Paths.get(filePath);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, fhirResourceJson);
    }
}
