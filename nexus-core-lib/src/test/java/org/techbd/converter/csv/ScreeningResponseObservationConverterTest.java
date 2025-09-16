package org.techbd.converter.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
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
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.techbd.config.CoreUdiPrimeJpaConfig;
import org.techbd.converters.csv.ScreeningResponseObservationConverter;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.csv.CodeLookupService;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;
import org.techbd.util.fhir.CoreFHIRUtil;

import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

class ScreeningResponseObservationConverterTest {

    @Mock
    CodeLookupService codeLookupService;
    
    @Mock
    CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig;
    
    @Mock
    AppLogger appLogger;
    
    @Mock
    TemplateLogger templateLogger;

    private ScreeningResponseObservationConverter converter;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(appLogger.getLogger(ScreeningResponseObservationConverter.class)).thenReturn(templateLogger);
        
        // Manually instantiate the converter after mocks are set up
        converter = new ScreeningResponseObservationConverter(codeLookupService, coreUdiPrimeJpaConfig, appLogger);
        
        Field profileMapField = CoreFHIRUtil.class.getDeclaredField("PROFILE_MAP");
        profileMapField.setAccessible(true);
        profileMapField.set(null, CsvTestHelper.getProfileMap());
        Field baseFhirUrlField = CoreFHIRUtil.class.getDeclaredField("BASE_FHIR_URL");
        baseFhirUrlField.setAccessible(true);
        baseFhirUrlField.set(null, CsvTestHelper.BASE_FHIR_URL);
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
                interactionId, idsGenerated, CsvTestHelper.BASE_FHIR_URL);
        assertThat(result).isNotNull();
        //assertThat(result).hasSize(screeningObservationDataList.size() + 1);
        for (int i = 0; i < screeningObservationDataList.size(); i++) {
            BundleEntryComponent entry = result.get(i);
            assertThat(entry.getFullUrl()).startsWith("http://shinny.org/us/ny/hrsn/Observation/");
            assertThat(entry.getResource()).isInstanceOf(Observation.class);
            Observation observation = (Observation) entry.getResource();
            assertThat(observation.getCode().getCodingFirstRep().getCode()).isEqualTo(screeningObservationDataList.get(i).getQuestionCode());
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
                screeningDataList, "interactionId", idsGenerated, CsvTestHelper.BASE_FHIR_URL);
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



