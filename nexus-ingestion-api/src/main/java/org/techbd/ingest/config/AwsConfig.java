package org.techbd.ingest.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.sqs.*;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;

@Configuration
public class AwsConfig {

    private final AppConfig appConfig;
    private final Environment environment;

    public AwsConfig(AppConfig appConfig, Environment environment) {
        this.appConfig = appConfig;
        this.environment = environment;
    }

    @Bean
    public S3Client s3Client() {
        AppConfig.Aws aws = appConfig.getAws();
        AppConfig.Aws.S3 s3 = aws.getS3();

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(aws.getRegion()));

        if (isSandboxProfile() && s3.getEndpoint() != null) {
            builder.endpointOverride(URI.create(s3.getEndpoint()));
            builder.forcePathStyle(true); // Required for LocalStack
        }

        builder.credentialsProvider(resolveCredentialsProvider(aws));

        return builder.build();
    }

    @Bean
    public SqsClient sqsClient() {
        AppConfig.Aws aws = appConfig.getAws();
        AppConfig.Aws.Sqs sqs = aws.getSqs();

        SqsClientBuilder builder = SqsClient.builder()
                .region(Region.of(aws.getRegion()));

        if (isSandboxProfile() && sqs.getEndpoint() != null) {
            builder.endpointOverride(URI.create(sqs.getEndpoint()));// Required for LocalStack
        }

        builder.credentialsProvider(resolveCredentialsProvider(aws));

        return builder.build();
    }

    private AwsCredentialsProvider resolveCredentialsProvider(AppConfig.Aws aws) {
        if (aws.getAccessKey() != null && aws.getSecretKey() != null) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(aws.getAccessKey(), aws.getSecretKey())
            );
        }
        return DefaultCredentialsProvider.create();
    }

    private boolean isSandboxProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("sandbox".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
