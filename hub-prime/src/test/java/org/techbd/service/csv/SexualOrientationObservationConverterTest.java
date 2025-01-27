package org.techbd.service.csv;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.SoftAssertions;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.converters.shinny.SexualOrientationObservationConverter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

@ExtendWith(MockitoExtension.class)
class SexualOrientationObservationConverterTest {
        @InjectMocks
        private SexualOrientationObservationConverter sexualOrientationObservationConverter;

        @Test
        //@Disabled
        void testConvert() throws Exception {
                final Bundle bundle = new Bundle();
                final List<ScreeningObservationData> screeningDataList = CsvTestHelper.createScreeningObservationData();
                final QeAdminData qrAdminData = CsvTestHelper.createQeAdminData();
                final var demographicData = CsvTestHelper.createDemographicData();
                final ScreeningProfileData screeningResourceData = CsvTestHelper.createScreeningProfileData();
                final String interactionId = "interactionId";
                final Map<String, String> idsGenerated = new HashMap<>();
                final BundleEntryComponent result = sexualOrientationObservationConverter.convert(
                                bundle, demographicData, qrAdminData, screeningResourceData, screeningDataList,interactionId, idsGenerated).get(0);
                final SoftAssertions softly = new SoftAssertions();
                softly.assertThat(result).isNotNull();
                softly.assertThat(result.getResource()).isInstanceOf(Observation.class);
                final Observation observation = (Observation) result.getResource();
                softly.assertThat(observation.getMeta().getLastUpdated().toInstant().toString())
                                .isEqualTo("2024-02-22T18:30:00Z");
                softly.assertThat(observation.getMeta().getProfile().get(0).getValue())
                                .isEqualTo("http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-observation-sexual-orientation");
                softly.assertThat(observation.getStatus().toCode()).isEqualTo("final");
                softly.assertThat(observation.getCode().getCodingFirstRep().getSystem())
                                .isEqualTo("http://loinc.org");
                softly.assertThat(observation.getCode().getCodingFirstRep().getCode())
                                .isEqualTo("76690-7");
                softly.assertThat(observation.getCode().getCodingFirstRep().getDisplay())
                                .isEqualTo("Sexual orientation");
                softly.assertThat(observation.getValueCodeableConcept().getCodingFirstRep().getSystem())
                                .isEqualTo("http://terminology.hl7.org/CodeSystem/v3-NullFlavor");
                softly.assertThat(observation.getValueCodeableConcept().getCodingFirstRep().getCode())
                                .isEqualTo("UNK");
                softly.assertThat(observation.getValueCodeableConcept().getCodingFirstRep().getDisplay())
                                .isEqualTo("Unknown");
                softly.assertAll();
        }

        @Test
        //@Disabled
        void testGeneratedJson() throws Exception {
                final var bundle = new Bundle();
                final var demographicData = CsvTestHelper.createDemographicData();
                final var screeningDataList = CsvTestHelper.createScreeningObservationData();
                final var qrAdminData = CsvTestHelper.createQeAdminData();
                final Map<String, String> idsGenerated = new HashMap<>();
                final ScreeningProfileData screeningResourceData = CsvTestHelper.createScreeningProfileData();
                final var result = sexualOrientationObservationConverter.convert(bundle, demographicData, qrAdminData,
                                screeningResourceData, screeningDataList,
                                "interactionId", idsGenerated);
                final Observation observation = (Observation) result.get(0).getResource();
                final var filePath = "src/test/resources/org/techbd/csv/generated-json/sexual-orientation-observation.json";
                final FhirContext fhirContext = FhirContext.forR4();
                final IParser fhirJsonParser = fhirContext.newJsonParser().setPrettyPrint(true);
                final String fhirResourceJson = fhirJsonParser.encodeResourceToString(observation);
                final Path outputPath = Paths.get(filePath);
                Files.createDirectories(outputPath.getParent());
                Files.writeString(outputPath, fhirResourceJson);
        }

}