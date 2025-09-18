package org.techbd.ingest.config;
import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/**
 * {@code AwsConfig} is a Spring {@link Configuration} class responsible for setting up
 * AWS SDK clients such as {@link S3Client} and {@link SqsClient}.
 * <p>
 * It handles environment-specific setup (e.g., sandbox or LocalStack) by configuring custom
 * endpoints and credentials, making it suitable for both local development and cloud deployments.
 * </p>
 *
 * <p>
 * Typical use cases:
 * <ul>
 *   <li>Use custom endpoints to route requests to LocalStack during integration testing.</li>
 *   <li>Use real AWS endpoints in production with environment-based credential loading.</li>
 * </ul>
 * </p>
 *
 * <p><b>Beans defined:</b></p>
 * <ul>
 *   <li>{@code S3Client} — for interacting with Amazon S3.</li>
 *   <li>{@code SqsClient} — for sending/receiving messages from Amazon SQS.</li>
 * </ul>
 */
@Configuration
public class AwsConfig {

    private static TemplateLogger LOG;

    private final AppConfig appConfig;
    private final Environment environment;

    /**
     * Constructs an AwsConfig instance with application configuration and environment.
     *
     * @param appConfig   The application configuration containing AWS settings.
     * @param environment The Spring environment for profile detection.
     */
    public AwsConfig(AppConfig appConfig, Environment environment, AppLogger appLogger) {
        this.appConfig = appConfig;
        this.environment = environment;
        LOG = appLogger.getLogger(AwsConfig.class);
        LOG.info("AwsConfig initialized");
    }

    /**
     * Creates and configures an AWS S3Client bean.
     * Uses sandbox/localstack endpoint if the sandbox profile is active.
     *
     * @return Configured S3Client instance.
     */
    @Bean
    public S3Client s3Client() {
        LOG.info("AwsConfig:: s3Client bean creation started");
        AppConfig.Aws aws = appConfig.getAws();
        AppConfig.Aws.S3 s3 = aws.getS3();

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(aws.getRegion()));

        if (isSandboxProfile() && s3.getDefaultConfig().getEndpoint() != null) {
            LOG.info("AwsConfig:: s3Client using sandbox endpoint: {}", s3.getDefaultConfig().getEndpoint());
            builder.endpointOverride(URI.create(s3.getDefaultConfig().getEndpoint()));
            builder.forcePathStyle(true); // Required for LocalStack
        }

        builder.credentialsProvider(resolveCredentialsProvider(aws));

        S3Client client = builder.build();
        LOG.info("AwsConfig:: s3Client bean created");
        return client;
    }

    /**
     * Creates and configures an AWS SqsClient bean.
     * Uses sandbox/localstack endpoint if the sandbox profile is active.
     *
     * @return Configured SqsClient instance.
     */
    @Bean
    public SqsClient sqsClient() {
        LOG.info("AwsConfig:: sqsClient bean creation started");
        AppConfig.Aws aws = appConfig.getAws();
        AppConfig.Aws.Sqs sqs = aws.getSqs();

        SqsClientBuilder builder = SqsClient.builder()
                .region(Region.of(aws.getRegion()));

        if (isSandboxProfile() && sqs.getEndpoint() != null) {
            LOG.info("AwsConfig:: sqsClient using sandbox endpoint: {}", sqs.getEndpoint());
            builder.endpointOverride(URI.create(sqs.getEndpoint()));// Required for LocalStack
        }

        builder.credentialsProvider(resolveCredentialsProvider(aws));

        SqsClient client = builder.build();
        LOG.info("AwsConfig:: sqsClient bean created");
        return client;
    }

    /**
     * Resolves the AWS credentials provider based on configuration.
     * Uses static credentials if provided, otherwise falls back to the default provider.
     *
     * @param aws The AWS configuration.
     * @return AwsCredentialsProvider instance.
     */
    private AwsCredentialsProvider resolveCredentialsProvider(AppConfig.Aws aws) {
        LOG.info("AwsConfig:: resolveCredentialsProvider called");
        if (aws.getAccessKey() != null && aws.getSecretKey() != null) {
            LOG.info("AwsConfig:: Using static credentials");
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(aws.getAccessKey(), aws.getSecretKey())
            );
        }
        LOG.info("AwsConfig:: Using default credentials provider");
        return DefaultCredentialsProvider.create();
    }

    /**
     * Checks if the current Spring profile is 'sandbox'.
     * Logs all active profiles and indicates if sandbox is selected.
     *
     * @return true if sandbox profile is active, false otherwise.
     */
    private boolean isSandboxProfile() {
        LOG.info("AwsConfig:: isSandboxProfile check started");
        boolean sandboxSelected = false;
        for (String profile : environment.getActiveProfiles()) {
            LOG.info("AwsConfig:: Active profile: {}", profile);
            if ("sandbox".equalsIgnoreCase(profile)) {
                LOG.info("AwsConfig:: Sandbox profile detected");
                sandboxSelected = true;
            } else {
                LOG.info("AwsConfig:: Non-sandbox profile selected: {}", profile);
            }
        }
        if (!sandboxSelected) {
            LOG.info("AwsConfig:: Sandbox profile not detected");
        }
        return sandboxSelected;
    }
}
