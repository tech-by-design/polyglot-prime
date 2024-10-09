package org.techbd.orchestrate.fhir;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.HashMap;

class OrchestrationEngineTest {

    private OrchestrationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new OrchestrationEngine();
    }

    @Test
    void testOrchestrateSingleSession() {
        OrchestrationEngine.OrchestrationSession session = engine.session()
                .withPayloads(List.of("not a valid payload"))
                .withFhirProfileUrl("http://example.com/fhirProfile")
                .addHapiValidationEngine()
                .build();

        engine.orchestrate(session);

        assertThat(engine.getSessions()).hasSize(1);
        assertThat(engine.getSessions().get(0).getPayloads()).containsExactly("not a valid payload");
        assertThat(engine.getSessions().get(0).getFhirProfileUrl()).isEqualTo("http://example.com/fhirProfile");

        List<OrchestrationEngine.ValidationResult> results = engine.getSessions().get(0).getValidationResults();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).isValid()).isFalse();
        assertThat(results.get(0).getIssues()).extracting("message").containsExactly(
                "HAPI-1861: Failed to parse JSON encoded FHIR content: HAPI-1859: Content does not appear to be FHIR JSON, first non-whitespace character was: '<' (must be '{')");
    }

    @Test
    void testOrchestrateMultipleSessions() {
        OrchestrationEngine.OrchestrationSession session1 = engine.session()
                .withPayloads(List.of("payload1"))
                .withFhirProfileUrl("http://example.com/fhirProfile")
                .addHapiValidationEngine()
                .build();

        OrchestrationEngine.OrchestrationSession session2 = engine.session()
                .withPayloads(List.of("payload2"))
                .withFhirProfileUrl("http://example.com/fhirProfile")
                .addHl7ValidationApiEngine()
                .build();

        engine.orchestrate(session1, session2);

        assertThat(engine.getSessions()).hasSize(2);

        OrchestrationEngine.OrchestrationSession retrievedSession1 = engine.getSessions().get(0);
        assertThat(retrievedSession1.getPayloads()).containsExactly("payload1");
        assertThat(retrievedSession1.getFhirProfileUrl()).isEqualTo("http://example.com/fhirProfile");
        assertThat(retrievedSession1.getValidationResults()).hasSize(1);
        assertThat(retrievedSession1.getValidationResults().get(0).isValid()).isFalse();
        assertThat(retrievedSession1.getValidationResults().get(0).getIssues()).extracting("message")
                .containsExactly(
                        "HAPI-1861: Failed to parse JSON encoded FHIR content: HAPI-1859: Content does not appear to be FHIR JSON, first non-whitespace character was: '<' (must be '{')");

        OrchestrationEngine.OrchestrationSession retrievedSession2 = engine.getSessions().get(1);
        assertThat(retrievedSession2.getPayloads()).containsExactly("payload2");
        assertThat(retrievedSession2.getFhirProfileUrl()).isEqualTo("http://example.com/fhirProfile");
        assertThat(retrievedSession2.getValidationResults()).hasSize(1);
        assertThat(retrievedSession2.getValidationResults().get(0).isValid()).isFalse();
    }

    @Test
    void testValidationEngineCaching() {
        OrchestrationEngine.OrchestrationSession session1 = engine.session()
                .withPayloads(List.of("payload1"))
                .withFhirProfileUrl("http://example.com/fhirProfile")
                .addHapiValidationEngine()
                .build();

        OrchestrationEngine.OrchestrationSession session2 = engine.session()
                .withPayloads(List.of("payload2"))
                .withFhirProfileUrl("http://example.com/fhirProfile")
                .addHapiValidationEngine()
                .build();

        engine.orchestrate(session1, session2);
        Map<String, String> structureDefintionMap = new HashMap<>();
        structureDefintionMap.put("shinnyPatient", "http://example.com/shinnyPatient");
        structureDefintionMap.put("shinnyOrganization", "http://example.com/shinnyOrganization");
        Map<String, String> valueSetMap = new HashMap<>();
        Map<String, Map<String, String>> igPackages = new HashMap<>();
        String igVersion = new String();
        valueSetMap.put("nyCountyCodes", "http://example.com/countyCodes");
        Map<String, String> codeSystemMap = new HashMap<>();
        codeSystemMap.put("shinnyConsentProvisionTypesVS", "http://example.com/shinnyConsentProvision");
        assertThat(engine.getSessions()).hasSize(2);
        assertThat(engine.getValidationEngine(OrchestrationEngine.ValidationEngineIdentifier.HAPI,
                "http://example.com/fhirProfile", structureDefintionMap, codeSystemMap, valueSetMap, igPackages,igVersion))
                .isSameAs(engine.getValidationEngine(
                        OrchestrationEngine.ValidationEngineIdentifier.HAPI,
                        "http://example.com/fhirProfile", structureDefintionMap, codeSystemMap,
                        valueSetMap, igPackages,igVersion));
    }
}
