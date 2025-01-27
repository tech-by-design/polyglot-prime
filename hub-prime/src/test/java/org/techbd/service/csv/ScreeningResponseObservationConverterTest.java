package org.techbd.service.csv;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.converters.shinny.BaseConverter;
import org.techbd.service.converters.shinny.ScreeningResponseObservationConverter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

class ScreeningResponseObservationConverterTest {

    @InjectMocks
    private ScreeningResponseObservationConverter converter;

    @Mock
    private BaseConverter baseConverter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testConvert() throws IOException {
        Bundle bundle = new Bundle();
        DemographicData demographicData = CsvTestHelper.createDemographicData();
        QeAdminData qeAdminData = CsvTestHelper.createQeAdminData();
        ScreeningProfileData screeningProfileData = CsvTestHelper.createScreeningProfileData();
        List<ScreeningObservationData> screeningObservationDataList = CsvTestHelper.createScreeningObservationData();
        String interactionId = "testInteractionId";
        Map<String, String> idsGenerated = new HashMap<>();
        List<BundleEntryComponent> result = converter.convert(
                bundle,
                demographicData,
                qeAdminData,
                screeningProfileData,
                screeningObservationDataList,
                interactionId, idsGenerated);
        assertThat(result).isNotNull();
        assertThat(result).hasSize(screeningObservationDataList.size() + 1);
        for (int i = 0; i < screeningObservationDataList.size(); i++) {
            ScreeningObservationData screeningData = screeningObservationDataList.get(i);
            BundleEntryComponent entry = result.get(i);
            assertThat(entry.getFullUrl()).isEqualTo(
                    "http://shinny.org/us/ny/hrsn/Observation/" + screeningData.getObservationId());
            assertThat(entry.getResource()).isInstanceOf(Observation.class);
            Observation observation = (Observation) entry.getResource();
            assertThat(observation.getCode().getCodingFirstRep().getCode()).isEqualTo(screeningData.getQuestionCode());
            assertThat(observation.getCode().getText()).isEqualTo(screeningData.getQuestionCodeText());

        }
    }

    @Test
   // @Disabled
    void testGeneratedScreeningResponseJson() throws Exception {
        final var bundle = new Bundle();
        final var demographicData = CsvTestHelper.createDemographicData();
        final var screeningDataList = CsvTestHelper.createScreeningObservationData();
        final var qrAdminData = CsvTestHelper.createQeAdminData();
        final Map<String, String> idsGenerated = new HashMap<>();
        final var screeningResourceData = CsvTestHelper.createScreeningProfileData();
        final var result = converter.convert(bundle, demographicData, qrAdminData, screeningResourceData,
                screeningDataList, "interactionId", idsGenerated);
        final Observation observation = (Observation) result.get(0).getResource();
        final var filePath = "src/test/resources/org/techbd/csv/generated-json/screening-response.json";
        final FhirContext fhirContext = FhirContext.forR4();
        final IParser fhirJsonParser = fhirContext.newJsonParser().setPrettyPrint(true);
        final String fhirResourceJson = fhirJsonParser.encodeResourceToString(observation);
        final Path outputPath = Paths.get(filePath);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, fhirResourceJson);
    }

}
