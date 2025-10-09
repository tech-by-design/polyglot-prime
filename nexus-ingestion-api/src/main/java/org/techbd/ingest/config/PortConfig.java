package org.techbd.ingest.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

// import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import software.amazon.awssdk.services.s3.S3Client;

/**
 * Singleton Spring Boot component to load port configuration from AWS S3.
 *
 * Required environment variables: - PORT_CONFIG_S3_BUCKET: S3 bucket name -
 * PORT_CONFIG_S3_KEY: S3 key (path to JSON file) - AWS_REGION: AWS region
 * (optional, defaults to us-east-1) - AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY:
 * (optional, if not using IAM role)
 */
@Component
public class PortConfig implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PortConfig.class);
    private static final String ENV_BUCKET = "PORT_CONFIG_S3_BUCKET";
    private static final String ENV_KEY = "PORT_CONFIG_S3_KEY";
    private static final String ENV_REGION = "AWS_REGION";

    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private List<PortEntry> portConfigurationList = Collections.emptyList();
    private final S3Client s3Client;

    // Default constructor for Spring
    public PortConfig() {
        this.s3Client = null;
    }

    // Constructor for test injection (not used by Spring)
    public PortConfig(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public static class PortEntry {

        public int port;
        public String responseType;
        public String protocol;

        // legacy single-ip fields (keep for backward compatibility)
        public String ipAddress;
        public String cidrBlock;

        // JSON may provide multiple whitelist entries
        public List<String> whitelistIps;

        // mtls may be represented as a boolean or as a string/id in different configs
        public boolean mtlsEnabled;
        public String mtls;

        public String execType;
        public String trafficTo;

        // support both "route" and "routeTo" JSON keys
        public String route;

        public String queue;
        public String dataDir;
        public String metadataDir;

        // Convenience: consider mtls enabled if boolean true or mtls string present
        public boolean isMtlsEnabled() {
            if (mtlsEnabled) {
                return true;
            }
            return mtls != null && !mtls.isBlank();
        }
    }

    @Override
    public void afterPropertiesSet() {
        loadConfig();
    }

    public void loadConfig() {
        if (loaded.get()) {
            return;
        }
        try {
            String bucket = System.getenv(ENV_BUCKET);
            String key = System.getenv(ENV_KEY);
            String region = System.getenv(ENV_REGION) != null ? System.getenv(ENV_REGION) : "us-east-1";
            if (bucket == null || key == null) {
                log.error("PortConfig: Missing required environment variables {} or {}", ENV_BUCKET, ENV_KEY);
                return;
            }
            S3Client s3 = (this.s3Client != null)
                    ? this.s3Client
                    : S3Client.builder().region(software.amazon.awssdk.regions.Region.of(region)).build();
            software.amazon.awssdk.services.s3.model.GetObjectRequest req
                    = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build();
            try (InputStream is = s3.getObject(req)) {
                ObjectMapper mapper = new ObjectMapper();
                portConfigurationList = mapper.readValue(is, new TypeReference<List<PortEntry>>() {
                });
                loaded.set(true);
                log.info("PortConfig: Loaded {} port entries from s3://{}/{}", portConfigurationList.size(), bucket, key);
            }
        } catch (Exception e) {
            log.error("PortConfig: Failed to load port config from S3", e);
        }
    }

    public boolean isLoaded() {
        return loaded.get();
    }

    public List<PortEntry> getPortConfigurationList() {
        if (!isLoaded()) {
            loadConfig();
        }
        return portConfigurationList;
    }
}
