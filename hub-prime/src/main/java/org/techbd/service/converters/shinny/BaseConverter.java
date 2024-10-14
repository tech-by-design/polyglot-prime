package org.techbd.service.converters.shinny;

import java.util.Map;

import org.hl7.fhir.r4.model.CanonicalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.service.http.hub.prime.api.Hl7Service;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.io.IOException;
import java.util.Collections;

public abstract class BaseConverter implements IConverter {

    private static final Logger LOG = LoggerFactory.getLogger(Hl7Service.class.getName());
    public static Map<String, String> PROFILE_MAP = getProfileUrlMap();

    public static Map<String, String> getProfileUrlMap() {
        LOG.info("BaseConverter::getProfileUrlMap reading profile urls from src/main/resources/shinny/profile.yml -BEGIN");
        try {
            String content = Files.readString(Path.of("src/main/resources/shinny/profile.yml"));
            Yaml yaml = new Yaml();
            Map<String, Object> yamlMap = yaml.load(content);
            return yamlMap.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> String.valueOf(entry.getValue())));
        } catch (IOException ex) {
            LOG.error("Exception during reading profile urls from src/main/resources/shinny/profile.yml", ex);
        }
        LOG.info("BaseConverter::getProfileUrlMap reading profile urls from src/main/resources/shinny/profile.yml -END");
        return Collections.emptyMap();
    }

    public CanonicalType getProfileUrl() {
        return new CanonicalType(PROFILE_MAP.get(getResourceType().name().toLowerCase()));
    }

}
