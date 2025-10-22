package org.techbd.ingest.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.net.URI;

@Configuration
public class AwsSdkConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    // Optional override used for local testing (e.g. LocalStack endpoint)
    @Value("${aws.ssm.endpoint:}")
    private String ssmEndpoint;

    @Bean
    public SsmClient ssmClient() {
        if (ssmEndpoint != null && !ssmEndpoint.isBlank()) {
            return SsmClient.builder()
                    .region(Region.of(awsRegion))
                    .endpointOverride(URI.create(ssmEndpoint))
                    .build();
        }
        return SsmClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }
}