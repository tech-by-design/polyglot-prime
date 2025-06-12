package org.techbd.converters.csv;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.techbd.config.MirthJooqConfig;
import org.techbd.service.csv.CodeLookupService;
import org.techbd.util.fhir.CoreFHIRUtil;

public abstract class BaseConverter implements IConverter {

    public static Map<String, Map<String, String>> CODE_LOOKUP;
    public static Map<String, Map<String, String>> SYSTEM_LOOKUP;
    public static Map<String, Map<String, String>> DISPLAY_LOOKUP;
    private final CodeLookupService codeLookupService;

    public BaseConverter(CodeLookupService codeLookupService) {
        this.codeLookupService = codeLookupService;
    }

    public String fetchCode(String valueFromCsv, String category, String interactionId) {
        if (CODE_LOOKUP == null) {
            final var dslContext = MirthJooqConfig.dsl();
            BaseConverter.CODE_LOOKUP = codeLookupService.fetchCode(dslContext, interactionId);
        }

        if (valueFromCsv == null || category == null) {
            return valueFromCsv;
        }
        Map<String, String> innerMap = CODE_LOOKUP.get(category);
        if (innerMap == null) {
            return valueFromCsv;
        }

        return innerMap.getOrDefault(valueFromCsv.toLowerCase(), valueFromCsv);
    }

    public String fetchSystem(String valueFromCsv, String category, String interactionId) {
        if (SYSTEM_LOOKUP == null) {
            final var dslContext = MirthJooqConfig.dsl();
            BaseConverter.SYSTEM_LOOKUP = codeLookupService.fetchSystem(dslContext, interactionId);
        }

        if (valueFromCsv == null || category == null) {
            return valueFromCsv;
        }
        Map<String, String> innerMap = SYSTEM_LOOKUP.get(category);
        if (innerMap == null) {
            return valueFromCsv;
        }

        return innerMap.getOrDefault(valueFromCsv.toLowerCase(), valueFromCsv);
    }

    public String fetchDisplay(String code, String valueFromCsv, String category, String interactionId) {
        if (DISPLAY_LOOKUP == null) {
            final var dslContext = MirthJooqConfig.dsl();
            BaseConverter.DISPLAY_LOOKUP = codeLookupService.fetchDisplay(dslContext, interactionId);
        }

        if (code == null || category == null) {
            return valueFromCsv;
        }
        Map<String, String> innerMap = DISPLAY_LOOKUP.get(category);
        if (innerMap == null) {
            return valueFromCsv;
        }

        return innerMap.getOrDefault(code, valueFromCsv);
    }

    public CanonicalType getProfileUrl() {
        return new CanonicalType(CoreFHIRUtil.getProfileUrl(getResourceType().name().toLowerCase()));
    }

    public static Extension createExtension(String url, String value, String system, String code, String display) {
        if (StringUtils.isEmpty(url)) {
            throw new IllegalArgumentException("Extension URL cannot be null or empty");
        }
        Extension extension = new Extension();
        extension.setUrl(url);
        if (StringUtils.isNotEmpty(system) || StringUtils.isNotEmpty(code) || StringUtils.isNotEmpty(display)) {
            Coding coding = new Coding();
            if (StringUtils.isNotEmpty(system)) {
                coding.setSystem(system);
            }
            if (StringUtils.isNotEmpty(code)) {
                coding.setCode(code);
            }
            if (StringUtils.isNotEmpty(display)) {
                coding.setDisplay(display);
            }
            CodeableConcept codeableConcept = new CodeableConcept();
            codeableConcept.addCoding(coding);
            extension.setValue(codeableConcept);
        }
        if (StringUtils.isNotEmpty(value)) {
            extension.setValue(new StringType(value));
        }
        return extension;
    }

    /**
     * Method to create a Reference object and populate the 'assigner' field
     * 
     * @param referenceString The reference string in the format
     *                        "ResourceType/ResourceId"
     * @return A Reference object representing the assigner reference
     */
    public static Reference createAssignerReference(String referenceString) {
        String[] parts = referenceString.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Reference string must be in the format 'ResourceType/ResourceId'");
        }
        Reference reference = new Reference();
        reference.setReference(referenceString);

        return reference;
    }
}
