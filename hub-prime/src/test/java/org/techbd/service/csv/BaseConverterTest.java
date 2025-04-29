package org.techbd.service.csv;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.techbd.service.converters.shinny.BaseConverter;

public class BaseConverterTest {

    private static Map<String, Map<String, String>> mockCodeLookup;
    private static Map<String, Map<String, String>> mockSystemLookup;

    @BeforeAll
    static void setup() {
        // Mock CODE_LOOKUP and SYSTEM_LOOKUP
        mockCodeLookup = new HashMap<>();
        Map<String, String> categoryMap = new HashMap<>();
        categoryMap.put("testcode", "mappedCode");
        mockCodeLookup.put("testCategory", categoryMap);

        mockSystemLookup = new HashMap<>();
        Map<String, String> systemCategoryMap = new HashMap<>();
        systemCategoryMap.put("testsystem", "mappedSystem");
        mockSystemLookup.put("testCategory", systemCategoryMap);

        // Set the static fields in BaseConverter
        BaseConverter.CODE_LOOKUP = mockCodeLookup;
        BaseConverter.SYSTEM_LOOKUP = mockSystemLookup;
    }

    @Test
    void testFetchCode() {
        String result = BaseConverter.fetchCode("testcode", "testCategory");
        assertEquals("mappedCode", result);

        result = BaseConverter.fetchCode("unknownCode", "testCategory");
        assertEquals("unknownCode", result);

        result = BaseConverter.fetchCode(null, "testCategory");
        assertNull(result);

        result = BaseConverter.fetchCode("testcode", null);
        assertEquals("testcode", result);
    }

    @Test
    void testFetchSystem() {
        String result = BaseConverter.fetchSystem("testsystem", "testCategory");
        assertEquals("mappedSystem", result);

        result = BaseConverter.fetchSystem("unknownSystem", "testCategory");
        assertEquals("unknownSystem", result);

        result = BaseConverter.fetchSystem(null, "testCategory");
        assertNull(result);

        result = BaseConverter.fetchSystem("testsystem", null);
        assertEquals("testsystem", result);
    }

    @Test
    void testCreateExtension() {
        Extension extension = BaseConverter.createExtension("http://example.com", "value", "http://system", "code", "display");
        assertNotNull(extension);
        assertEquals("http://example.com", extension.getUrl());
        assertTrue(extension.getValue() instanceof org.hl7.fhir.r4.model.CodeableConcept);

        assertThrows(IllegalArgumentException.class, () -> BaseConverter.createExtension("", "value", null, null, null));
    }

    @Test
    void testCreateAssignerReference() {
        Reference reference = BaseConverter.createAssignerReference("Patient/123");
        assertNotNull(reference);
        assertEquals("Patient/123", reference.getReference());

        assertThrows(IllegalArgumentException.class, () -> BaseConverter.createAssignerReference("InvalidReference"));
    }
}
