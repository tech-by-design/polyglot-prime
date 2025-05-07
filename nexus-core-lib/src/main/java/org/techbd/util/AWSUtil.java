package org.techbd.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

public class AWSUtil {
    private static final Logger LOG = LoggerFactory.getLogger(AWSUtil.class.getName());

    /**
     * Retrieves the value of a secret from AWS Secrets Manager.
     *
     * @param secretName the name or ARN of the secret to retrieve
     * @return the value of the secret as a {@link String}, or {@code null} if
     *         retrieval fails
     */
    public static String getValue(String secretName) {
        Region region = Region.US_EAST_1;
        SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                .region(region)
                .build();
        String secret = null;
        try {
            GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();
            GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
            secret = valueResponse.secretString();
        } catch (SecretsManagerException e) {
            LOG.error("ERROR: Failed to retrieve the secret '{}' - AWS Error: {}", secretName,
                    e.awsErrorDetails().errorMessage(), e);
        } catch (Exception e) {
            LOG.error("ERROR: Failed to retrieve the secret '{}' due to unexpected error: ", secretName, e);
        } finally {
            secretsClient.close();
        }
        if (secret == null) {
            LOG.warn("Secret '{}' could not be retrieved or is null.", secretName);
        }
        return secret;
    }
}
