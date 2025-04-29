package org.techbd.service.csv;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.converters.shinny.BaseConverter;
import org.techbd.service.converters.shinny.CodeLookupService;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.util.FHIRUtil;

class BaseConverterTest {

    private BaseConverter baseConverter;
    private UdiPrimeJpaConfig mockUdiPrimeJpaConfig;
    private CodeLookupService mockCodeLookupService;

    @BeforeEach
    void setUp() {
        mockUdiPrimeJpaConfig = mock(UdiPrimeJpaConfig.class);
        mockCodeLookupService = mock(CodeLookupService.class);

        // Create a concrete subclass of BaseConverter for testing
        baseConverter = new BaseConverter(mockUdiPrimeJpaConfig, mockCodeLookupService) {
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
        Map<String, Map<String, String>> mockCodeLookup = new HashMap<>();
        Map<String, String> categoryMap = new HashMap<>();
        categoryMap.put("example", "exampleCode");
        mockCodeLookup.put("category", categoryMap);

        when(mockCodeLookupService.fetchCode(any())).thenReturn(mockCodeLookup);

        String result = baseConverter.fetchCode("example", "category");
        assertEquals("exampleCode", result);
    }

    @Test
    void testFetchSystem() {
        Map<String, Map<String, String>> mockSystemLookup = new HashMap<>();
        Map<String, String> categoryMap = new HashMap<>();
        categoryMap.put("example", "exampleSystem");
        mockSystemLookup.put("category", categoryMap);

        when(mockCodeLookupService.fetchSystem(any())).thenReturn(mockSystemLookup);

        String result = baseConverter.fetchSystem("example", "category");
        assertEquals("exampleSystem", result);
    }

    @Test
    void testCreateExtension() {
        String url = "http://example.com";
        String value = "exampleValue";
        String system = "http://example-system.com";
        String code = "exampleCode";
        String display = "Example Display";

        Extension extension = BaseConverter.createExtension(url, value, system, code, display);

        assertNotNull(extension);
        assertEquals(url, extension.getUrl());
        assertNotNull(extension.getValue());
    }

    @Test
    void testCreateAssignerReference() {
        String referenceString = "Patient/123";

        Reference reference = BaseConverter.createAssignerReference(referenceString);

        assertNotNull(reference);
        assertEquals(referenceString, reference.getReference());
    }

    @Test
    void testGetProfileUrl() {
        String profileUrl = baseConverter.getProfileUrl().getValue();
        assertEquals(FHIRUtil.getProfileUrl("patient"), profileUrl);
    }
}
