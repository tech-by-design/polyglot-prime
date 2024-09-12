package org.techbd.service.http.hub.prime.health;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.techbd.conf.Configuration;
import org.techbd.service.http.Helpers;
import org.techbd.service.http.hub.prime.AppConfig;

@Component("bundleValidateHealthCheck")
public class BundleValidateHealthIndicator implements HealthIndicator {

    private RestTemplate restTemplate = new RestTemplate();

    @Value("${TECHBD_HUB_PRIME_BASE_URL:#{null}}")
    private String baseUrl;

    @Override
    public Health health() {
        try {
            if (null == baseUrl) {
                throw new Exception("Invalid environment variable value for baseUrl");
            }
            String url = baseUrl + "/Bundle/$validate";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(Configuration.Servlet.HeaderName.Request.TENANT_ID, "unit-test");
            headers.add(AppConfig.Servlet.HeaderName.Request.HEALTH_CHECK_HEADER, "true");

            HttpEntity<String> requestEntity = new HttpEntity<>(fixtureContent("TestCase301.json"), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            if (response.getStatusCode().equals(HttpStatus.OK) && null != response.getBody()) {
                return Health.up().build();
            } else {
                return Health.down().withDetail("Error",
                        String.format("Validation service is not returning status code %d , response body %s",
                                response.getStatusCode(), response.getBody()))
                        .build();
            }
        } catch (final Exception e) {
            return Health.down(e).withDetail("Error", "Bundle validation endpoint is not responding correctly").build();
        }
    }

    public String fixtureContent(final String filename) throws IOException, InterruptedException {
        return Helpers.textFromURL(
                "https://raw.githubusercontent.com/tech-by-design/docs.techbd.org/main/assurance/1115-waiver/ahc-hrsn/screening/regression-test-prime/fhir-service-prime/src/2024-06-10/"
                + filename);
    }
}
