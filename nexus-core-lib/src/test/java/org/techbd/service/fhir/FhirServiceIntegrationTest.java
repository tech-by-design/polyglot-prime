package org.techbd.service.fhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.techbd.config.AppConfig;
import org.techbd.config.ConfigLoader;
import org.techbd.config.Constants;
import org.techbd.service.fhir.engine.OrchestrationEngine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class FhirServiceIntegrationTest {
    private FHIRService fhirService;
    private AppConfig appConfig;

    @BeforeEach
    void setUp() throws Exception {
        appConfig = ConfigLoader.loadConfig("sandbox");
        fhirService = new FHIRService();
        fhirService.setAppConfig(appConfig);
        org.techbd.util.fhir.FHIRUtil.initialize(appConfig);
        OrchestrationEngine engine = new OrchestrationEngine();
        fhirService.setEngine(engine);
    }

    // @Test
    // public void testProcessBundle() throws Exception {
    // String interactionId = UUID.randomUUID().toString();
    // Map<String, String> requestParameters = getRequestParameters(interactionId);
    // Map<String, String> headerParameters = getHeaderParameters();
    // Map<String, Object> responseParameters = new HashMap<>();
    // String bundleJson = Files.readString(Path.of(
    // "src/test/resources/org/techbd/fhir/AHCHRSNQuestionnaireResponseExample1.2.3.json"));
    // Object validationResults = fhirService.processBundle(bundleJson,
    // requestParameters, headerParameters,
    // responseParameters);
    // assertNotNull(validationResults);

    // // Convert validationResults to JSON
    // ObjectMapper mapper = new ObjectMapper();
    // mapper.registerModule(new JavaTimeModule());
    // String jsonString =
    // mapper.writerWithDefaultPrettyPrinter().writeValueAsString(validationResults);
    // Files.writeString(Path.of("src/test/resources/org/techbd/fhir/output.json"),
    // jsonString,
    // StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    // JsonNode rootNode = mapper.readTree(jsonString);
    // JsonNode validationResultsNode = rootNode.path("validationResults");
    // boolean isValid = validationResultsNode.path(0).path("valid").asBoolean();
    // JsonNode issuesNode =
    // validationResultsNode.path(0).path("operationOutcome").path("issue");
    // boolean hasError = false;
    // for (JsonNode issue : issuesNode) {
    // if ("error".equalsIgnoreCase(issue.path("severity").asText())) {
    // hasError = true;
    // break;
    // }
    // }
    // assertThat(hasError).isFalse();
    // }

    private Map<String, String> getRequestParameters(String interactionId) {
        Map<String, String> requestParams = new HashMap<>();
        requestParams.put(Constants.INTERACTION_ID, interactionId);
        requestParams.put(Constants.TENANT_ID, "tenant123");
        requestParams.put(Constants.SOURCE_TYPE, "FHIR");
        requestParams.put(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME, "2024-01-24T10:15:30Z");
        return requestParams;
    }

    private Map<String, String> getHeaderParameters() {
        Map<String, String> headerParams = new HashMap<>();
        headerParams.put(Constants.USER_AGENT, "user-agent");
        return headerParams;
    }
}
