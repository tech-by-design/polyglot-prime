package org.techbd.service.api.http.fhir;

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
import org.techbd.conf.Configuration;
import org.techbd.service.api.http.Helpers;
import org.w3c.dom.Document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.constraints.NotNull;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FhirEndpointsTests {
	private final FhirAppConfiguration appConfig;

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	public FhirEndpointsTests(final FhirAppConfiguration appConfig) {
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
		final var bvr = getBundleValidateResult("fhir-fixture-shinny-healtheconnections-unhappy-path.json");
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
		assertThat(validationResults).hasSize(3);

		// Check details of the first validation result
		Map<String, Object> firstResult = validationResults.get(0);
		assertThat(firstResult).containsEntry("profileUrl",
				"https://djq7jdt8kb490.cloudfront.net/1115/StructureDefinition-SHINNYBundleProfile.json");
		assertThat(firstResult).containsEntry("engine", "HAPI");
		assertThat(firstResult).containsEntry("valid", false);

		// Check issues in the first validation result
		List<Map<String, Object>> issues = objectMapper.convertValue(firstResult.get("issues"),
				new TypeReference<List<Map<String, Object>>>() {
				});
		assertThat(issues).hasSize(1);

		Map<String, Object> firstIssue = issues.get(0);
		Map<String, Object> location = objectMapper.convertValue(firstIssue.get("location"),
				new TypeReference<Map<String, Object>>() {
				});
		assertThat(location).containsKey("diagnostics");
		assertThat(firstIssue).containsEntry("message",
				"HAPI-1821: [element=\"lastUpdated\"] Invalid attribute value \"2023-10-28 10:07:42.9149210\": Invalid date/time format: \"2023-10-28 10:07:42.9149210\": Expected character 'T' at index 10 but found  ");
		assertThat(firstIssue).containsEntry("severity", "FATAL");

		// Check details of the second validation result
		Map<String, Object> secondResult = validationResults.get(1);
		assertThat(secondResult).containsEntry("profileUrl",
				"https://djq7jdt8kb490.cloudfront.net/1115/StructureDefinition-SHINNYBundleProfile.json");
		assertThat(secondResult).containsEntry("engine", "HL7");
		assertThat(secondResult).containsEntry("valid", true);
		assertThat((List<?>) secondResult.get("issues")).isEmpty();

		// Check details of the third validation result
		Map<String, Object> thirdResult = validationResults.get(2);
		assertThat(thirdResult).containsEntry("profileUrl",
				"https://djq7jdt8kb490.cloudfront.net/1115/StructureDefinition-SHINNYBundleProfile.json");
		assertThat(thirdResult).containsEntry("engine", "INFERNO");
		assertThat(thirdResult).containsEntry("valid", true);
		assertThat((List<?>) thirdResult.get("issues")).isEmpty();
	}

	public String fixtureContent(final String filename) throws IOException, InterruptedException {
		return Helpers.textFromURL(
				"https://raw.githubusercontent.com/tech-by-design/docs.techbd.org/main/assurance/1115-waiver/ahc-hrsn/screening/regression-test-prime/fhir-service-prime/src/2024-05-16/"
						+ filename);
	}

}
