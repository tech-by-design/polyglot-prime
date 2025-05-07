package org.techbd.config;

import java.io.IOException;
import java.io.InputStream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

class ConfigLoaderTest {

        private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

        @Test
        void testLoadConfigWithOverrides() throws IOException {
                AppConfig config = ConfigLoader.loadConfig("dev");
                // only in application.yml
                Assertions.assertThat(config.getBaseFHIRURL()).isEqualTo("http://shinny.org/us/ny/hrsn");
                // both in application.yml and application-dev.yml -value in application-dev.yml
                // should override
                Assertions.assertThat(config.getDefaultDatalakeApiUrl()).isEqualTo("https://test.com/dev/HRSNBundle");
        }

        @Test
        void testLoadConfig() throws IOException {
                AppConfig mainConfig = loadYamlFromClasspath("application.yml");
                AppConfig envConfig = loadYamlFromClasspath("application-dev.yml");

                AppConfig config = ConfigLoader.loadConfig("dev");

                // Base FHIR URL
                Assertions.assertThat(config.getBaseFHIRURL())
                                .isEqualTo(mainConfig.getBaseFHIRURL());

                // Overridden defaultDatalakeApiUrl
                Assertions.assertThat(config.getDefaultDatalakeApiUrl())
                                .isEqualTo(envConfig.getDefaultDatalakeApiUrl());

                // Operation Outcome Help URL
                Assertions.assertThat(config.getOperationOutcomeHelpUrl())
                                .isEqualTo(mainConfig.getOperationOutcomeHelpUrl());

                // Structure Definitions
                Assertions.assertThat(config.getStructureDefinitionsUrls())
                                .containsExactlyInAnyOrderEntriesOf(mainConfig.getStructureDefinitionsUrls());

                // IG Packages
                AppConfig.FhirV4Config fhirV4Config = config.getIgPackages().get("fhir-v4");
                Assertions.assertThat(fhirV4Config).isNotNull();
                Assertions.assertThat(fhirV4Config.getShinnyPackages())
                                .containsExactlyInAnyOrderEntriesOf(
                                                mainConfig.getIgPackages().get("fhir-v4").getShinnyPackages());

                // Base Packages
                Assertions.assertThat(fhirV4Config.getBasePackages())
                                .containsExactlyInAnyOrderEntriesOf(
                                                mainConfig.getIgPackages().get("fhir-v4").getBasePackages());

                // CSV Validation Paths
                Assertions.assertThat(config.getCsv().validation())
                                .usingRecursiveComparison()
                                .isEqualTo(envConfig.getCsv().validation());

                // Default Data Lake API Authentication
                Assertions.assertThat(config.getDefaultDataLakeApiAuthn())
                                .usingRecursiveComparison()
                                .isEqualTo(envConfig.getDefaultDataLakeApiAuthn());
        }

        @Test
        void testLoadConfig_InvalidEnvConfig() throws IOException {
                AppConfig mainConfig = loadYamlFromClasspath("application.yml");
                AppConfig config = ConfigLoader.loadConfig("devtest");
                Assertions.assertThat(config.getDefaultDatalakeApiUrl())
                                .isEqualTo(mainConfig.getDefaultDatalakeApiUrl());
                Assertions.assertThat(config.getCsv()).isNull();
        }

        private AppConfig loadYamlFromClasspath(String filePath) throws IOException {
                try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filePath)) {
                        return (inputStream != null) ? YAML_MAPPER.readValue(inputStream, AppConfig.class)
                                        : new AppConfig();
                }
        }
}