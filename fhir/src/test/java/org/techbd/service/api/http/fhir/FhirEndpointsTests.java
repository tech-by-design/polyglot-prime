package org.techbd.service.api.http.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
		// TODO: add more assertions specific to expected content
	}

	public String fixtureContent(final String filename) throws IOException, InterruptedException {
		return Helpers.textFromURL(
				"https://raw.githubusercontent.com/tech-by-design/docs.techbd.org/main/assurance/1115-waiver/ahc-hrsn/screening/regression-test-prime/fhir-service-prime/src/2024-05-16/"
						+ filename);
	}

}
