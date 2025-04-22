package org.techbd.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.DSLContext;
import org.jooq.Record2;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
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

    // public static Map<String, Map<String, String>> getYamlResourceAsFlatMap(String filePath) {
    //     LOG.info("YamlUtil::getYamlResourceAsNestedMap reading values from {} - BEGIN", filePath);
    //     try {
    //         String content = Files.readString(Path.of(filePath));
    //         Yaml yaml = new Yaml();
    //         Map<String, Object> yamlMap = yaml.load(content);
    
    //         Map<String, Map<String, String>> result = yamlMap.entrySet().stream()
    //                 .collect(Collectors.toMap(
    //                         Map.Entry::getKey,
    //                         entry -> parseCommaSeparatedValues(entry.getValue()).stream()
    //                                 .collect(Collectors.toMap(
    //                                         val -> val.toLowerCase(),
    //                                         val -> val,
    //                                         (existing, replacement) -> existing // keep first on duplicate
    //                                 ))
    //                 ));
    
    //         return result;
    //     } catch (IOException ex) {
    //         LOG.error("Exception during reading values from {}", filePath, ex);
    //     }
    //     LOG.info("YamlUtil::getYamlResourceAsNestedMap reading values from {} - END", filePath);
    //     return Collections.emptyMap();
    // }

    public static Map<String, Map<String, String>> getYamlResourceFromDb(DSLContext dsl) {
        LOG.info("YamlUtil::getYamlResourceFromDb fetching values from database - BEGIN");
        try {
            List<Record2<String, String>> records = dsl
                    .select(field("name", String.class), field("values", String.class))
                    .from(table("techbd_udi_ingress.code_mapping"))
                    .fetch();
    
            Map<String, Map<String, String>> result = records.stream()
                    .collect(Collectors.toMap(
                            Record2::component1, // name
                            record -> parseCommaSeparatedValues(record.component2()).stream()
                                    .collect(Collectors.toMap(
                                            val -> val.toLowerCase(),
                                            val -> val,
                                            (existing, replacement) -> existing
                                    ))
                    ));
    
            return result;
        } catch (Exception ex) {
            LOG.error("Exception during fetching values from database", ex);
        }
        LOG.info("YamlUtil::getYamlResourceFromDb fetching values from database - END");
        return Collections.emptyMap();
    }
    
    

    private static List<String> parseCommaSeparatedValues(Object value) {
        if (value instanceof String) {
            return Stream.of(((String) value).split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        } else if (value instanceof List<?>) {
            return ((List<?>) value).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
