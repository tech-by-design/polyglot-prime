package org.techbd.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

public class YamlUtil {

    private static final Logger LOG = LoggerFactory.getLogger(YamlUtil.class.getName());

    public static Map<String, String> getYamlResourceAsMap(String filePath) {
        LOG.info("YamlUtil::getProfileUrlMap reading profile urls from {} -BEGIN", filePath);
        try {
            String content = Files.readString(Path.of(filePath));
            Yaml yaml = new Yaml();
            Map<String, Object> yamlMap = yaml.load(content);
            return yamlMap.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> String.valueOf(entry.getValue())));
        } catch (IOException ex) {
            LOG.error("Exception during reading profile urls from {}", filePath, ex);
        }
        LOG.info("YamlUtil::getProfileUrlMap reading profile urls from {} -END", filePath);
        return Collections.emptyMap();
    }

    public static Map<String, List<String>> getYamlResourceAsListMap(String filePath) {
        LOG.info("YamlUtil::getYamlResourceAsListMap reading values from {} - BEGIN", filePath);
        try {
            String content = Files.readString(Path.of(filePath));
            Yaml yaml = new Yaml();
            Map<String, Object> yamlMap = yaml.load(content);

            return yamlMap.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> parseCommaSeparatedValues(entry.getValue())));
        } catch (IOException ex) {
            LOG.error("Exception during reading values from {}", filePath, ex);
        }
        LOG.info("YamlUtil::getYamlResourceAsListMap reading values from {} - END", filePath);
        return Collections.emptyMap();
    }

    private static List<String> parseCommaSeparatedValues(Object value) {
        if (value instanceof String) {
            return Stream.of(((String) value).split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
