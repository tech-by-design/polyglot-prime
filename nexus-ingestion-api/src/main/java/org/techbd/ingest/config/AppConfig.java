package org.techbd.ingest.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

/**
 * AppConfig holds application-specific settings, mapping them from application.yml or application.properties.
 * It provides AWS-related configurations such as region, secret name, access key, secret key, and service endpoints.
 */
@Configuration
@ConfigurationProperties(prefix = "org.techbd")
@ConfigurationPropertiesScan
@Data
public class AppConfig {

    private Aws aws;

    @Data
    public static class Aws {
        private String region;
        private String secretName;
        private String accessKey;
        private String secretKey;
        private S3 s3;
        private Sqs sqs;

        @Data
        public static class S3 {
            private String bucket;
            private String metadataBucket;
            private String endpoint;
        }

        @Data
        public static class Sqs {
            private String baseUrl;
            private String fifoQueueUrl;
            private String endpoint;
        }
    }
}