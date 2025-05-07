package org.techbd.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;

public class ConfigLoader {

    public static AppConfig loadConfig(String env) throws IOException {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        AppConfig config = loadYamlFromClasspath("application.yml", yamlMapper);
        AppConfig envConfig = loadYamlFromClasspath("application-" + env + ".yml", yamlMapper);
        if (envConfig != null) {
            mergeConfigs(config, envConfig);
        }
        return config;
    }

    private static AppConfig loadYamlFromClasspath(String fileName, ObjectMapper yamlMapper) throws IOException {
        try (InputStream inputStream = ConfigLoader.class.getClassLoader().getResourceAsStream(fileName)) {
            return (inputStream != null) ? yamlMapper.readValue(inputStream, AppConfig.class) : new AppConfig();
        }
    }

    private static void mergeConfigs(AppConfig baseConfig, AppConfig envConfig) {
        for (var field : AppConfig.class.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object envValue = field.get(envConfig);
                if (envValue != null) {
                    field.set(baseConfig, envValue);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to merge config properties", e);
            }
        }
    }
}