package org.techbd.converter.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

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
import org.techbd.converters.csv.BaseConverter;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.csv.CodeLookupService;
import org.techbd.util.fhir.CoreFHIRUtil;

class BaseConverterTest {

    private BaseConverter baseConverter;
    private CodeLookupService mockCodeLookupService;

    @BeforeEach
    void setUp() {
        mockCodeLookupService = mock(CodeLookupService.class);

        // Create a concrete subclass of BaseConverter for testing
        baseConverter = new BaseConverter(mockCodeLookupService) {
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

        String result = baseConverter.fetchSystem("example", "category", "interactionId");
        assertEquals("mappedSystem", result);

        result = baseConverter.fetchSystem("unknown", "category", "interactionId");
        assertEquals("unknown", result);
    }

    @Test
    void testGetProfileUrl() {
        CanonicalType profileUrl = baseConverter.getProfileUrl();
        assertEquals(CoreFHIRUtil.getProfileUrl("patient"), profileUrl.getValue());
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
