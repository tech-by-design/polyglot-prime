package org.techbd.orchestrate.fhir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;

public class ImplGuideTest {

    FhirContext ctx;
    FhirValidator validator;
    FhirInstanceValidator instanceValidator;

    @BeforeEach
    void setup() throws URISyntaxException, IOException {
        ctx = FhirContext.forR4();
        validator = ctx.newValidator();
        instanceValidator = new FhirInstanceValidator(ctx);

        final var terminologyService = new CommonCodeSystemsTerminologyService(ctx);
        final var validationSupport = new PrePopulatedValidationSupport(ctx);
        //validationSupport.fetchStructureDefinition(IG_PROFILE_URL);

        final var defaultValidationSupport = new DefaultProfileValidationSupport(ctx);
        final var validationSupportChain = new ValidationSupportChain(
                defaultValidationSupport,
                terminologyService,
                validationSupport);
        instanceValidator.setValidationSupport(validationSupportChain);
        validator.registerValidatorModule(instanceValidator);
    }

    @Test
    void testUnHappyPath_Angele108_Keitha498_Kihn564() throws IOException {
        assertUnHappyPathInvalidEncounterStatus("org/techbd/fixtures/unhappy-path/Angele108_Keitha498_Kihn564_4d678b50-d57b-97cd-d8f5-97209d6b2e6d.json");
    }

    @Test
    void testUnHappyPath_Anika194_Ashlee14_Daniel959() throws IOException {
        assertUnHappyPathInvalidEncounterStatus("org/techbd/fixtures/unhappy-path/Anika194_Ashlee14_Daniel959_47f5bba7-e8fb-f59e-36be-4097a0d27214.json");
    }

    @Test
    void testUnHappyPath_Niesha86_Powlowski563() throws IOException {
        assertUnHappyPathInvalidEncounterStatus("org/techbd/fixtures/unhappy-path/Niesha86_Powlowski563_with_errors.json");
    }

    void assertUnHappyPathInvalidEncounterStatus(String fixtureFileName) throws IOException {
        String input = loadFixture(fixtureFileName);
        assertThat(input)
                .as("Fixture file '%s' should not be null or empty", fixtureFileName)
                .isNotNull()
                .isNotBlank();
        final var bundle = ctx.newJsonParser().parseResource(Bundle.class, input);
        final var result = validator.validateWithResult(bundle);
        final var messages = result.getMessages();
        final var expectedErrorMessage = "The value provided ('finished') is not in the value set 'EncounterStatus";
        assertThat(messages)
                .anyMatch(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
                && m.getMessage().contains(expectedErrorMessage))
                .withFailMessage("Fixture file %s do not have valid encounter status.The value provided ('finished') is not in the value set 'EncounterStatus' (http://hl7.org/fhir/ValueSet/encounter-status|4.0.1)", fixtureFileName);
    }

    @Test
    void testUnHappyPathInvalidObservationStatus_Niesha86_Powlowski563() throws IOException {
        assertUnHappyPathInvalidObservationStatus("org/techbd/fixtures/unhappy-path/Niesha86_Powlowski563_with_errors.json");
    }

    void assertUnHappyPathInvalidObservationStatus(String fixtureFileName) throws IOException {
        String input = loadFixture(fixtureFileName);
        assertThat(input)
                .as("Fixture file '%s' should not be null or empty", fixtureFileName)
                .isNotNull()
                .isNotBlank();
        final var bundle = ctx.newJsonParser().parseResource(Bundle.class, input);
        final var result = validator.validateWithResult(bundle);
        final var messages = result.getMessages();
        final var expectedErrorMessage = "The value provided ('final') is not in the value set 'ObservationStatus'";
        assertThat(messages)
                .anyMatch(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
                && m.getMessage().contains(expectedErrorMessage))
                .withFailMessage("The value provided ('final') is not in the value set 'ObservationStatus' (http://hl7.org/fhir/ValueSet/observation-status|4.0.1), and a code is required from this value set  (error message = Validation failed)", fixtureFileName);
    }

    @Test
    void testUnHappyPathInvalidEventStatus_Niesha86_Powlowski563() throws IOException {
        assertUnHappyPathInvalidEventStatus("org/techbd/fixtures/unhappy-path/Niesha86_Powlowski563_with_errors.json");
    }

    void assertUnHappyPathInvalidEventStatus(String fixtureFileName) throws IOException {
        String input = loadFixture(fixtureFileName);
        assertThat(input)
                .as("Fixture file '%s' should not be null or empty", fixtureFileName)
                .isNotNull()
                .isNotBlank();
        final var bundle = ctx.newJsonParser().parseResource(Bundle.class, input);
        final var result = validator.validateWithResult(bundle);
        final var messages = result.getMessages();
        final var expectedErrorMessage = "The value provided ('completed') is not in the value set 'EventStatus'";
        assertThat(messages)
                .anyMatch(m -> ResultSeverityEnum.ERROR.equals(m.getSeverity())
                && m.getMessage().contains(expectedErrorMessage))
                .withFailMessage("The value provided ('completed') is not in the value set 'EventStatus' (http://hl7.org/fhir/ValueSet/event-status|4.0.1), and a code is required from this value set  (error message = Validation failed)", fixtureFileName);
    }

    private String loadFixture(String filename) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
            if (inputStream == null) {
                System.err.println("Failed to load the fixture: " + filename);
                return null;
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to read JSON input from file: " + e.getMessage());
            return null;
        }
    }

    static Stream<String> getHappyPathFixtures() throws IOException, URISyntaxException {
        return loadFilesFromDirectory("org/techbd/fixtures/happy-path");
    }

    static Stream<String> getUnHappyPathFixtures() throws IOException, URISyntaxException {
        return loadFilesFromDirectory("org/techbd/fixtures/unhappy-path");
    }

    private static Stream<String> loadFilesFromDirectory(String directory) throws IOException, URISyntaxException {
        URL url = ImplGuideTest.class.getClassLoader().getResource(directory);
        if (url == null) {
            throw new IOException("Directory not found: " + directory);
        }
        Path path = Paths.get(url.toURI());
        return Files.walk(path)
                .filter(Files::isRegularFile)
                .map(Path::toString)
                .map(p -> directory + "/" + path.relativize(Paths.get(p)).toString().replace("\\", "/"));
    }
}