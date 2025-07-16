package org.techbd.ingest.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
/*  
 * AppConfig is a configuration class that holds application-specific settings.
 * It uses Spring's ConfigurationProperties to map properties from application.yml or application.properties.
 * This class is used to access AWS-related configurations such as region, secret name, access key, and secret key.
 */
@org.springframework.context.annotation.Configuration
@ConfigurationProperties(prefix = "org.techbd")
@ConfigurationPropertiesScan
public class AppConfig {
    private Aws aws;

    public Aws getAws() {
        return aws;
    }

    public void setAws(Aws aws) {
        this.aws = aws;
    }

    public static class Aws {
        private String region;
        private String secretName;
        private String accessKey;
        private String secretKey;

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        private S3 s3;
        private Sqs sqs;

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getSecretName() {
            return secretName;
        }

        public void setSecretName(String secretName) {
            this.secretName = secretName;
        }

        public S3 getS3() {
            return s3;
        }

        public void setS3(S3 s3) {
            this.s3 = s3;
        }

        public Sqs getSqs() {
            return sqs;
        }

        public void setSqs(Sqs sqs) {
            this.sqs = sqs;
        }

        public static class S3 {
            private String bucket;
            private String endpoint;

            public String getBucket() {
                return bucket;
            }

            public void setBucket(String bucket) {
                this.bucket = bucket;
            }

            public String getEndpoint() {
                return endpoint;
            }

            public void setEndpoint(String endpoint) {
                this.endpoint = endpoint;
            }
        }

        public static class Sqs {
            private String baseUrl;
            private String fifoQueueUrl;
            private String endpoint;

            public String getEndpoint() {
                return endpoint;
            }

            public void setEndpoint(String endpoint) {
                this.endpoint = endpoint;
            }

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }

            public String getFifoQueueUrl() {
                return fifoQueueUrl;
            }

            public void setFifoQueueUrl(String fifoQueueUrl) {
                this.fifoQueueUrl = fifoQueueUrl;
            }
        }
    }
}