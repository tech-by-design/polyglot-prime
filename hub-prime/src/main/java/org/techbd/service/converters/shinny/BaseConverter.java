package org.techbd.service.converters.shinny;

import java.util.Map;

import org.hl7.fhir.r4.model.CanonicalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.util.YamlUtil;

public abstract class BaseConverter implements IConverter {

    private static final Logger LOG = LoggerFactory.getLogger(BaseConverter.class.getName());
    public static Map<String, String> PROFILE_MAP = getProfileUrlMap();

    public static Map<String, String> getProfileUrlMap() {
        return YamlUtil.getYamlResourceAsMap("src/main/resources/shinny/profile.yml");
    }

    public CanonicalType getProfileUrl() {
        return new CanonicalType(PROFILE_MAP.get(getResourceType().name().toLowerCase()));
    }

}
