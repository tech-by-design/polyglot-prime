package org.techbd.orchestrate.fhir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.StructureDefinition;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationOptions;

public class ImplGuideTest {

    private static final String IG_PROFILE_URL = "https://djq7jdt8kb490.cloudfront.net/1115/StructureDefinition-SHINNYBundleProfile.json";
    private FhirContext fhirContext;
    private FhirValidator validator;
    private StructureDefinition profile;

    @BeforeEach
    void setup() throws URISyntaxException, IOException {
        fhirContext = FhirContext.forR4();
        validator = fhirContext.newValidator();
        validator.setValidateAgainstStandardSchema(true);
        validator.setValidateAgainstStandardSchematron(false);

        URL url = new URI(IG_PROFILE_URL).toURL();
        IParser parser = fhirContext.newJsonParser();
        profile = parser.parseResource(StructureDefinition.class, url.openStream());
    }

    @ParameterizedTest
    @MethodSource("provideHappyPathFixtures")
    void testHappyPathStructureDefinitionValidationFixture(String fixtureFileName) throws IOException {
        String input = loadFixture(fixtureFileName);
        if (input == null) {
            return;
        }

        Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, input);
        ValidationOptions options = new ValidationOptions().addProfile(profile.getUrl());
        var result = validator.validateWithResult(bundle, options);

        String encoded = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(result.toOperationOutcome());
        String expectedResult = """
                                {
                                  "resourceType": "OperationOutcome",
                                  "issue": [ {
                                    "severity": "information",
                                    "code": "informational",
                                    "diagnostics": "No issues detected during validation"
                                  } ]
                                }""";
        assertThat(encoded).isEqualTo(expectedResult);
    }

    @ParameterizedTest
    @MethodSource("provideUnHappyPathFixtures")
    void testUnHappyPathStructureDefinitionNoResourceTypeValidationFixture(String fixtureFileName) throws IOException {
        String input = loadFixture(fixtureFileName);
        if (input == null) {
            return;
        }

        assertThrows(DataFormatException.class, () -> {
            Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, input);
            ValidationOptions options = new ValidationOptions().addProfile(profile.getUrl());
            validator.validateWithResult(bundle, options);
        });
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

    static Stream<String> provideHappyPathFixtures() throws IOException, URISyntaxException {
        return loadFilesFromDirectory("org/techbd/fixtures/happy-path");
    }

    static Stream<String> provideUnHappyPathFixtures() throws IOException, URISyntaxException {
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
