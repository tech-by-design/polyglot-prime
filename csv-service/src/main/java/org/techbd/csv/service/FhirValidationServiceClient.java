package org.techbd.csv.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@Service
public class FhirValidationServiceClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(FhirValidationServiceClient.class);
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    public FhirValidationServiceClient(@Value("${TECHBD_BL_BASEURL}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = new ObjectMapper();
        LOG.info("FhirValidationServiceClient initialized with baseUrl: {}", baseUrl);
    }
    
    public Object validateBundle(String bundle, String interactionId) {
        LOG.info("Calling FHIR validation service for bundle validation - interaction Id: {}", interactionId);
        
        try {
            //To-do : needs to change to /Bundle in future
            String response = webClient.post()
                    .uri("/Bundle/$validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-TechBD-Tenant-ID", "csv-service")
                    .body(BodyInserters.fromValue(bundle))
                    .retrieve()
                    .onStatus(status -> status.isError(), 
                        clientResponse -> clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("Unknown error from FHIR validation service")
                            .flatMap(errorBody -> {
                                LOG.error("Error response from FHIR validation service (status={}): {} - interaction Id: {}", 
                                    clientResponse.statusCode(), errorBody, interactionId);
                                return Mono.error(new RuntimeException("FHIR validation service error: " + errorBody));
                            }))
                    .bodyToMono(String.class)
                    .block();
            
            LOG.info("Successfully received response from FHIR validation service - interaction Id: {}", interactionId);
            return objectMapper.readValue(response, Object.class);
            
        } catch (WebClientResponseException e) {
            LOG.error("WebClient error while calling FHIR validation service: status={} body={} - interaction Id: {}", 
                e.getStatusCode(), e.getResponseBodyAsString(), interactionId);
            throw new RuntimeException("FHIR validation service error: " + e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("Unexpected error while calling FHIR validation service: {} - interaction Id: {}", e.getMessage(), interactionId, e);
            throw new RuntimeException("FHIR validation service error: " + e.getMessage(), e);
        }
    }
}