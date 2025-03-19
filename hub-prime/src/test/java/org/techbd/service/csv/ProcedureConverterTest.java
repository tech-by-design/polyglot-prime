package org.techbd.service.csv;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.converters.shinny.ProcedureConverter;
import org.techbd.util.CsvConstants;
import org.techbd.util.FHIRUtil;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

class ProcedureConverterTest {

    @InjectMocks
    private ProcedureConverter procedureConverter;

    @Mock
    private DemographicData demographicData;

    @Mock
    private QeAdminData qeAdminData;

    @Mock
    private ScreeningProfileData screeningProfileData;

    private Map<String, String> idsGenerated;
    private String baseFHIRUrl;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        idsGenerated = new HashMap<>();
        baseFHIRUrl = "http://shinny.org/us/ny/hrsn";

        // Initialize FHIRUtil.PROFILE_MAP
        FHIRUtil.PROFILE_MAP = new HashMap<>();
        FHIRUtil.PROFILE_MAP.put("procedure", "/StructureDefinition/shinny-sdoh-procedure");
    }

    @Test
    void testConvert() {
        // Mock the procedure code data from CSV
        when(screeningProfileData.getProcedureCode()).thenReturn("G0136");
        when(screeningProfileData.getProcedureCodeDescription()).thenReturn("SDOH Assessment");
        when(screeningProfileData.getProcedureCodeSystem()).thenReturn("http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets");
        // Create a new Bundle instead of null
        Bundle bundle = new Bundle();

        // Initialize screeningObservationData as empty list instead of mock
        List<ScreeningObservationData> screeningObservationData = new ArrayList<>();

        when(screeningProfileData.getEncounterId()).thenReturn("encounter123");
        when(screeningProfileData.getProcedureStatusCode()).thenReturn("completed");
        when(screeningProfileData.getProcedureCode()).thenReturn("G0136");
        when(screeningProfileData.getProcedureCodeDescription()).thenReturn("SDOH Assessment");

        List<BundleEntryComponent> result = procedureConverter.convert(bundle, demographicData, qeAdminData,
                screeningProfileData, screeningObservationData, "interaction123", idsGenerated, baseFHIRUrl);

        assertNotNull(result);
        assertEquals(1, result.size());

        Procedure procedure = (Procedure) result.get(0).getResource();
        assertEquals("43de5bbc18c1986ae36a0fac0d9a932c345b33cfb3f30d965a168faf85777397", procedure.getIdElement().getIdPart());
        assertEquals("completed", procedure.getStatus().toCode());
        assertEquals("G0136", procedure.getCode().getCodingFirstRep().getCode());
        assertEquals("SDOH Assessment", procedure.getCode().getText());
    }


    @Test
    void testGeneratedProcedureResponseJson() throws Exception {
        // Setup
        final var bundle = new Bundle();
        final var demographicData = mock(DemographicData.class);
        final var screeningDataList = new ArrayList<ScreeningObservationData>();
        final var qrAdminData = mock(QeAdminData.class);
        final Map<String, String> idsGenerated = new HashMap<>();
        final var screeningResourceData = mock(ScreeningProfileData.class);

        // Add required IDs to idsGenerated
        idsGenerated.put(CsvConstants.PATIENT_ID, "patient123");
        idsGenerated.put(CsvConstants.ENCOUNTER_ID, "encounter123");
        idsGenerated.put(CsvConstants.ORGANIZATION_ID, "org123");

        // Setup screening data with dates
        when(screeningResourceData.getEncounterId()).thenReturn("encounter123");
        when(screeningResourceData.getProcedureStatusCode()).thenReturn("completed");
        when(screeningResourceData.getProcedureCode()).thenReturn("G0136");
        when(screeningResourceData.getProcedureCodeDescription()).thenReturn("SDOH Assessment");

        // Add screening observations
        ScreeningObservationData obs1 = new ScreeningObservationData();
        obs1.setScreeningStartDateTime("2023-07-12T21:38:00+05:30");
        obs1.setScreeningEndDateTime("2023-07-13T00:38:00+05:30");
        obs1.setQuestionCode("88122-7");
        obs1.setObservationId("FoodInsecurity88122-7");
        screeningDataList.add(obs1);

        ScreeningObservationData obs2 = new ScreeningObservationData();
        obs2.setQuestionCode("88123-5");
        obs2.setObservationId("FoodInsecurity88123-5");
        screeningDataList.add(obs2);

        // Execute
        final var result = procedureConverter.convert(
                bundle,
                demographicData,
                qrAdminData,
                screeningResourceData,
                screeningDataList,
                "interactionId",
                idsGenerated,
                "http://shinny.org/us/ny/hrsn");

        final Procedure procedure = (Procedure) result.get(0).getResource();

        final var filePath = "src/test/resources/org/techbd/csv/generated-json/procedure-response.json";
        final FhirContext fhirContext = FhirContext.forR4();
        final IParser fhirJsonParser = fhirContext.newJsonParser().setPrettyPrint(true);
        final String fhirResourceJson = fhirJsonParser.encodeResourceToString(procedure);

        // Add assertions to verify content
        assertNotNull(procedure.getPerformedPeriod());
        assertNotNull(procedure.getSubject());
        assertNotNull(procedure.getEncounter());
        //assertFalse(procedure.getReasonReference().isEmpty());

        final Path outputPath = Paths.get(filePath);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, fhirResourceJson);
    }
}
