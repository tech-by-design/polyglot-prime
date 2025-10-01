package org.techbd.csv.converter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.techbd.corelib.config.CoreUdiPrimeJpaConfig;
import org.techbd.csv.converters.BaseConverter;
import org.techbd.csv.model.DemographicData;
import org.techbd.csv.model.QeAdminData;
import org.techbd.csv.model.ScreeningObservationData;
import org.techbd.csv.model.ScreeningProfileData;
import org.techbd.csv.service.CodeLookupService;
import org.techbd.corelib.util.CoreFHIRUtil;

class BaseConverterTest {

    private BaseConverter baseConverter;
    private CodeLookupService mockCodeLookupService;
    private CoreUdiPrimeJpaConfig mockCoreUdiPrimeJpaConfig;

    @BeforeEach
    void setUp() throws Exception {
        mockCodeLookupService = mock(CodeLookupService.class);
        //CoreFHIRUtil.initialize(CsvTestHelper.getProfileMap(), CsvTestHelper.BASE_FHIR_URL);
        // Create a concrete subclass of BaseConverter for testing
        baseConverter = new BaseConverter(mockCodeLookupService,mockCoreUdiPrimeJpaConfig) {
            @Override
            public ResourceType getResourceType() {
                return ResourceType.Patient; // Example resource type
            }

            @Override
            public List<BundleEntryComponent> convert(Bundle bundle, DemographicData demographicData,
                    QeAdminData qeAdminData, ScreeningProfileData screeningProfileData,
                    List<ScreeningObservationData> screeningObservationData, String interactionId,
                    Map<String, String> idsGenerated, String baseFHIRUrl) {
                // TODO Auto-generated method stub
                throw new UnsupportedOperationException("Unimplemented method 'convert'");
            }
        };
    }

    @Test
    void testFetchCode() {
        Map<String, String> categoryMap = new HashMap<>();
        categoryMap.put("example", "mappedValue");
        Map<String, Map<String, String>> codeLookup = new HashMap<>();
        codeLookup.put("category", categoryMap);

        BaseConverter.CODE_LOOKUP = codeLookup;

        String result = baseConverter.fetchCode("example", "category", "interactionId");
        assertEquals("mappedValue", result);

        result = baseConverter.fetchCode("unknown", "category", "interactionId");
        assertEquals("unknown", result);
    }

    @Test
    void testFetchSystem() {
        Map<String, String> categoryMap = new HashMap<>();
        categoryMap.put("example", "mappedSystem");
        Map<String, Map<String, String>> systemLookup = new HashMap<>();
        systemLookup.put("category", categoryMap);

        BaseConverter.SYSTEM_LOOKUP = systemLookup;

        String result = baseConverter.fetchSystem("example", "defaultSystem", "category", "interactionId");
        assertEquals("mappedSystem", result);

        result = baseConverter.fetchSystem("unknown", "unknown", "category", "interactionId");
        assertEquals("unknown", result);
    }
    
    @Test
    void testGetProfileUrl1() {
        try (MockedStatic<CoreFHIRUtil> mockedCoreUtil = mockStatic(CoreFHIRUtil.class)) {
            // Arrange
            String expectedUrl = "http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-patient";
            mockedCoreUtil.when(() -> CoreFHIRUtil.getProfileUrl("patient")).thenReturn(expectedUrl);

            // Act
            CanonicalType result = baseConverter.getProfileUrl();

            // Assert
            assertNotNull(result);
            assertEquals(expectedUrl, result.getValue());
            mockedCoreUtil.verify(() -> CoreFHIRUtil.getProfileUrl("patient"));
        }
    }
    
    @Test
    void testCreateExtension() {
        Extension extension = BaseConverter.createExtension(
            "http://example.com",
            "value",
            "http://system.com",
            "code",
            "display"
        );

        assertEquals("http://example.com", extension.getUrl());
        assertNotNull(extension.getValue());
    }

    @Test
    void testCreateAssignerReference() {
        Reference reference = BaseConverter.createAssignerReference("Patient/123");
        assertEquals("Patient/123", reference.getReference());
    }

    @Test
    void testCreateAssignerReferenceInvalidFormat() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            BaseConverter.createAssignerReference("InvalidFormat");
        });
        assertEquals("Reference string must be in the format 'ResourceType/ResourceId'", exception.getMessage());
    }
}
