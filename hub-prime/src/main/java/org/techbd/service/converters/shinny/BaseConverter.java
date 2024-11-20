package org.techbd.service.converters.shinny;

import java.util.Map;

import org.hl7.fhir.r4.model.CanonicalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.util.YamlUtil;

public abstract class BaseConverter implements IConverter {

    private static final Logger LOG = LoggerFactory.getLogger(BaseConverter.class.getName());
    public static Map<String, String> PROFILE_MAP = getProfileUrlMap();
    public static Map<String, String> EXTENSIONS_MAP = getExtensionsMap();
    public static Map<String, String> VALUESETS_MAP = getValueSetsMap();
    public static Map<String, String> MASTER_DATA_PATH_MAP = getMasterDataPathMap();
    public static Map<String, String> IDENTIFIER_SYSTEM_MAP = getIdentifierSystemMap();

    public static Map<String, String> getProfileUrlMap() {
        return YamlUtil.getYamlResourceAsMap("src/main/resources/shinny/shinny-artifacts/profile.yml");
    }

    private static Map<String, String> getIdentifierSystemMap() {
        return YamlUtil.getYamlResourceAsMap("src/main/resources/shinny/shinny-artifacts/identifier-systems.yml");
    }

    private static Map<String, String> getMasterDataPathMap() {
        return YamlUtil.getYamlResourceAsMap("src/main/resources/shinny/shinny-artifacts/config.yml");
    }

    private static Map<String, String> getValueSetsMap() {
        return YamlUtil.getYamlResourceAsMap("src/main/resources/shinny/shinny-artifacts/valuesets.yml");
    }

    private static Map<String, String> getExtensionsMap() {
        return YamlUtil.getYamlResourceAsMap("src/main/resources/shinny/shinny-artifacts/extensions.yml");
    }

    public CanonicalType getProfileUrl() {
        return new CanonicalType(PROFILE_MAP.get(getResourceType().name().toLowerCase()));
    }

}
