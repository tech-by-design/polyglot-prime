package org.techbd.service.http.hub.prime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.techbd.conf.Configuration;
import org.techbd.service.http.Helpers;
import org.w3c.dom.Document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.constraints.NotNull;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = Application.class)
class ApplicationTests {
	private final AppConfig appConfig;

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	public ApplicationTests(final AppConfig appConfig) {
		this.appConfig = appConfig;
	}

	protected String getTestServerUrl(final @NotNull String path) {
		return "http://localhost:" + port + path;
	}

	@Test
	public void metaDataShouldReturnCapabilities() throws Exception {
		ResponseEntity<String> response = restTemplate.getForEntity(getTestServerUrl("/metadata"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();

		// Parse the response body as XML
		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new ByteArrayInputStream(response.getBody().getBytes(StandardCharsets.UTF_8)));

		XPath xPath = XPathFactory.newInstance().newXPath();
		XPathExpression expression = xPath.compile("/CapabilityStatement/software/version/@value");
		String version = (String) expression.evaluate(document, XPathConstants.STRING);

		assertThat(version).isNotEmpty().isEqualTo(appConfig.getVersion());
	}

	Map<?, ?> getBundleValidateResult(final @NotNull String fixtureFilename) throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add(Configuration.Servlet.HeaderName.Request.TENANT_ID, "unit-test");
		
		HttpEntity<String> requestEntity = new HttpEntity<>(fixtureContent(fixtureFilename), headers);

		ResponseEntity<String> response = restTemplate.postForEntity(getTestServerUrl("/Bundle/$validate"),
				requestEntity, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();

		return objectMapper.readValue(response.getBody(), Map.class);
	}

	@Test
	void bundleValidateHealtheconnectionsUnhappyPath() throws Exception {
		final var bvr = getBundleValidateResult("TestCase301.json");
		assertThat(bvr.containsKey("OperationOutcome"));
		Map<String, Object> operationOutcome = objectMapper.convertValue(bvr.get("OperationOutcome"),
				new TypeReference<Map<String, Object>>() {
				});

		// Check the presence of "device"
		assertThat(operationOutcome).containsKey("device");

		// Check the presence of "validationResults"
		assertThat(operationOutcome).containsKey("validationResults");
		List<Map<String, Object>> validationResults = objectMapper.convertValue(
				operationOutcome.get("validationResults"), new TypeReference<List<Map<String, Object>>>() {
				});
		assertThat(validationResults).hasSize(1);

		// Check details of the first validation result
		assertValidationResult(validationResults.get(0),
				"https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-SHINNYBundleProfile.json", "HAPI", false,
				"HAPI-1821: [element=\"gender\"] Invalid attribute value \"UN\": Unknown AdministrativeGender code 'UN'",
				"FATAL");
	}

	Map<?, ?> getBundleValidateResultWithHapiEngine(final @NotNull String fixtureFilename) throws Exception {
		
		RestTemplate restTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000); 
        factory.setReadTimeout(60000);    
        restTemplate.setRequestFactory(factory);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add(Configuration.Servlet.HeaderName.Request.TENANT_ID, "unit-test");
		headers.add("X-TechBD-FHIR-Validation-Strategy", "{\"engines\": [\"HAPI\"]}");

		HttpEntity<String> requestEntity = new HttpEntity<>(fixtureContent(fixtureFilename), headers);

		ResponseEntity<String> response = restTemplate.postForEntity(getTestServerUrl("/Bundle/$validate"),
				requestEntity, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();

		return objectMapper.readValue(response.getBody(), Map.class);
	}

	@Test
	void bundleValidateTestCase301WithHapiEngines() throws Exception {
		final var bvr = getBundleValidateResultWithHapiEngine("TestCase301.json");
		assertThat(bvr.containsKey("OperationOutcome"));
		Map<String, Object> operationOutcome = objectMapper.convertValue(bvr.get("OperationOutcome"),
				new TypeReference<Map<String, Object>>() {
				});

		// Check the presence of "device"
		assertThat(operationOutcome).containsKey("device");

		// Check the presence of "validationResults"
		assertThat(operationOutcome).containsKey("validationResults");
		List<Map<String, Object>> validationResults = objectMapper.convertValue(
				operationOutcome.get("validationResults"), new TypeReference<List<Map<String, Object>>>() {
				});
		assertThat(validationResults).hasSize(1);

		// Check details of the first validation result
		assertValidationResult(validationResults.get(0),
				"https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-SHINNYBundleProfile.json", "HAPI", false,
				"HAPI-1821: [element=\"gender\"] Invalid attribute value \"UN\": Unknown AdministrativeGender code 'UN'",
				"FATAL");
	}

	private void assertValidationResult(Map<String, Object> validationResult, String profileUrl, String engine,
			boolean isValid, String issueMessage, String severity) {
		assertThat(validationResult).containsEntry("profileUrl", profileUrl);
		//assertThat(validationResult).containsEntry("engine", engine);
		assertThat(validationResult).containsEntry("valid", isValid);

		if (issueMessage != null && severity != null) {
			List<Map<String, Object>> issues = objectMapper.convertValue(validationResult.get("issues"),
					new TypeReference<List<Map<String, Object>>>() {
					});
			assertThat(issues).hasSizeGreaterThan(0);

			Map<String, Object> firstIssue = issues.get(0);
			Map<String, Object> location = objectMapper.convertValue(firstIssue.get("location"),
					new TypeReference<Map<String, Object>>() {
					});
			assertThat(location).containsKey("diagnostics");
			assertThat(firstIssue).containsEntry("message", issueMessage);
			assertThat(firstIssue).containsEntry("severity", severity);
		}
	}

	public String fixtureContent(final String filename) throws IOException, InterruptedException {
		return Helpers.textFromURL(
				"https://raw.githubusercontent.com/tech-by-design/docs.techbd.org/main/assurance/1115-waiver/ahc-hrsn/screening/regression-test-prime/fhir-service-prime/src/2024-06-10/"
						+ filename);
	}

}
