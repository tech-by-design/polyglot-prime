package org.techbd.orchestrate;


import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.techbd.service.http.hub.prime.api.FHIRService.KeyDetails;

import io.netty.handler.ssl.SslContextBuilder;
import reactor.netty.http.client.HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

public class ScoringEngineApiTest {
    
    private static final String MTLS_KEY_SECRET_NAME = "techbd-qa-client-key";
    private static final String MTLS_CERT_SECRET_NAME = "techbd-qa-client-certificate";
    private static final String URL_QA_SCORING_ENGINE_API = "https://qa.hrsn.nyehealth.org/HRSNBundle";
        private static final Logger LOG = LoggerFactory.getLogger(ScoringEngineApiTest.class.getName());

    @Test
        public void testSimplePass() {
        assertThat("success").isEqualTo("success");
    }

    @Test
    public void testSimpleFail() {
    assertThat("success").isEqualTo("fail");
}
    @Test
    public void testApi_error() throws Exception {
        try {
            KeyDetails keyDetails = getSecretsFromAWSSecretManager();
            final String CERTIFICATE = keyDetails.cert();
            final String PRIVATE_KEY = keyDetails.key();
            LOG.warn("Certificate and Key Details fetched successfully");
            final var payload = Files.readString(Paths.get(
                    "src/test/resources/org/techbd/ig-examples/Bundle-AHCHRSNScreeningResponseExample.json"));
            // Initialize SSLContext for mTLS
            LOG.warn("Payload fetched successfully");
            LOG.warn("Creating SSLContext  successfully");

            final var sslContext = SslContextBuilder.forClient()
                    .keyManager(new ByteArrayInputStream(CERTIFICATE.getBytes()),
                            new ByteArrayInputStream(PRIVATE_KEY.getBytes()))
                    .build();
            LOG.warn("SSLContext created successfully");

            // final var sslContext = createSslContext(secretValuesFromAWS);
            LOG.warn("Get HttpClient interaction id");
            HttpClient httpClient = HttpClient.create()
                    .secure(ssl -> ssl.sslContext(sslContext));
            // .protocol(HttpProtocol.H2, HttpProtocol.HTTP11);

            LOG.info("Create ReactorClientHttpConnector: ");
            ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
            LOG.info("Build WebClient with MTLS Enabled ReactorClientHttpConnector-BEGIN");
            var webClient = WebClient.builder()
                    .baseUrl(URL_QA_SCORING_ENGINE_API)
                    // .defaultHeader("Content-Type", "application/fhir+json")
                    .clientConnector(connector)
                    .build();
            LOG.info("Build WebClient with MTLS Enabled ReactorClientHttpConnector-END");
            LOG.info("post-BEGIN");

            String response = webClient.post()
                    .uri("?processingAgent=TEST_PARTNER1")
                    .body(BodyInserters.fromValue(payload))
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(error -> {
                        // Print the error message for any exception
                        LOG.warn("&&&&&&&&&&&An error occurred&&&&&&&&&&&: {}", error.getMessage());
                        if (error instanceof final WebClientResponseException webClientResponseException) {
                            String responseBody = webClientResponseException
                                    .getResponseBodyAsString();
                            LOG.warn("&&&&&&&&&&&An responseBody &&&&&&&&&&&: {}", responseBody);
                            assertThat(responseBody).contains(
                                    "{\"status\": \"Error\", \"message\": \"Validation Error: Invalid QE. QE identifier not supported for sending to data lake.\"}");
                        }
                    })
                    .block(); // Wait for the response synchronously
            LOG.warn("Response : {} ",response);
            LOG.warn("Test end");
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }
    }
     private KeyDetails getSecretsFromAWSSecretManager() {
        Region region = Region.US_EAST_1;
        LOG.warn(
                "FHIRService:: getSecretsFromAWSSecretManager  - Get Secrets Client Manager for region : {} BEGIN for interaction id: {}",
                Region.US_EAST_1);
        SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                .region(region)
                .build();
        KeyDetails keyDetails = new KeyDetails(getValue(secretsClient, MTLS_KEY_SECRET_NAME),
                getValue(secretsClient, MTLS_CERT_SECRET_NAME));
        secretsClient.close();
        LOG.warn(
                "FHIRService:: getSecretsFromAWSSecretManager  - Get Secrets Client Manager for region : {} END for interaction id: {}",
                Region.US_EAST_1);
        return keyDetails;
    }

    public static String getValue(SecretsManagerClient secretsClient, String secretName) {
        LOG.warn("FHIRService:: getValue  - Get Value of secret with name  : {} -BEGIN", secretName);
        String secret = null;
        try {
            GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
            secret = valueResponse.secretString();
            // LOG.warn("**************VALUE*****************\n\n" + secret+"\n\n");
            LOG.warn("\n\nFHIRService:: getValue  - Fetched value of secret with name  : {}  value  is null : {} -END",
                    secretName,
                    secret == null ? "value not retrieved" : "value retrieved successfully");
        } catch (SecretsManagerException e) {
            LOG.warn("ERROR:: FHIRService:: getValue  - Get Value of secret with name  : {} - FAILED with error "
                    + e.awsErrorDetails().errorMessage(), e);
        } catch (Exception e) {
            LOG.warn("ERROR:: FHIRService:: getValue  - Get Value of secret with name  : {} - FAILED with error ",
                    e);
        }
        LOG.warn("FHIRService:: getValue  - Get Value of secret with name  : {} -END", secretName);
        return secret;
    }

}
