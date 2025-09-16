package org.techbd.ingest.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;@Configuration
@ConfigurationProperties(prefix = "org.techbd")
@ConfigurationPropertiesScan
@Data
public class AppConfig {

    private Aws aws;
    private Soap soap;

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
            private BucketConfig defaultConfig; // maps to org.techbd.aws.s3.default
            private BucketConfig holdConfig;          // maps to org.techbd.aws.s3.hold

            @Data
            public static class BucketConfig {
                private String bucket;
                private String metadataBucket;
                private String endpoint;
            }
        }

        @Data
        public static class Sqs {
            private String baseUrl;
            private String fifoQueueUrl;
            private String endpoint;
        }
    }

    @Data
    public static class Soap {
        private Wsa wsa;
        private Techbd techbd;

        @Data
        public static class Wsa {
            private String namespace;
            private String prefix;
            private String action;
            private String to;
        }

        @Data
        public static class Techbd {
            private String namespace;
            private String prefix;
        }
    }
}
