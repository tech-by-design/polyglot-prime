package org.techbd.service.converters.shinny;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.util.YamlUtil;
public abstract class BaseConverter implements IConverter {

    private static final Logger LOG = LoggerFactory.getLogger(BaseConverter.class.getName());
    public static Map<String, String> PROFILE_MAP = getProfileUrlMap();


    public static Map<String, String> getProfileUrlMap() {
        return YamlUtil.getYamlResourceAsMap("src/main/resources/shinny/shinny-artifacts/profile.yml");
    }

    public CanonicalType getProfileUrl() {
        return new CanonicalType(PROFILE_MAP.get(getResourceType().name().toLowerCase()));
    }

    public static Extension createExtension(String url,String value, String system, String code, String display) {
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
        if (StringUtils.isNotEmpty(value)){
            extension.setValue(new StringType(value));
        }
        return extension;
    }
    
}
