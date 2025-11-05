package org.techbd.ingest.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

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
    private List<Integer> mllpPorts = Collections.emptyList();
    private final S3Client s3Client;

    @Autowired
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

    public synchronized void loadConfig() {
        if (loaded.get()) {
            return;
        }
        String bucket = System.getenv(ENV_BUCKET);
        String key = System.getenv(ENV_KEY);
        String region = System.getenv(ENV_REGION) != null ? System.getenv(ENV_REGION) : "us-east-1";

        if (bucket == null || key == null) {
            log.error("PortConfig: Missing required environment variables {} or {}. Example values: {}='my-config-bucket', {}='configs/port-config.json'",
                    ENV_BUCKET, ENV_KEY, ENV_BUCKET, ENV_KEY);
            return;
        }

        // Use injected S3Client (configured centrally in AwsConfig)
        if (this.s3Client == null) {
            log.error("PortConfig: S3Client bean not available - cannot load port config");
            return;
        }
        S3Client s3 = this.s3Client;

        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        String rawJson = null;
        try {
            log.info("PortConfig: Attempting to load port config from s3://{}/{} (region={})", bucket, key, region);
            ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(req);
            byte[] data = bytes.asByteArray();
            log.info("PortConfig: Fetched {} bytes from s3://{}/{}", data.length, bucket, key);

            rawJson = new String(data, StandardCharsets.UTF_8);
            // Log up to first 2KB to avoid huge logs
            String preview = rawJson.length() > 2048 ? rawJson.substring(0, 2048) + "...(truncated)" : rawJson;
            log.info("PortConfig: JSON preview: {}", preview);

            ObjectMapper mapper = new ObjectMapper();
            // tolerate extra/unknown fields in the JSON so updates won't break parsing
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            portConfigurationList = mapper.readValue(rawJson, new TypeReference<List<PortEntry>>() {
            });
            // compute MLLP ports list (case-insensitive match)
            List<Integer> mports = new ArrayList<>();
            if (portConfigurationList != null) {
                mports = portConfigurationList.stream()
                        .filter(p -> p != null)
                        .filter(p -> {
                            String rt = p.responseType == null ? "" : p.responseType.trim();
                            String proto = p.protocol == null ? "" : p.protocol.trim();
                            return "mllp".equalsIgnoreCase(rt) && "tcp".equalsIgnoreCase(proto);
                        })
                        .map(p -> p.port)
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
            }
            this.mllpPorts = Collections.unmodifiableList(mports);

            loaded.set(true);
            log.info("PortConfig: Loaded {} port entries from s3://{}/{} ; mllp ports={}", portConfigurationList.size(), bucket, key, mllpPorts);
        } catch (JsonProcessingException jpe) {
            log.error("PortConfig: Failed to parse port config JSON (json length={}). JSON (truncated): {}",
                    rawJson == null ? 0 : rawJson.length(),
                    (rawJson != null && rawJson.length() > 0 ? rawJson.substring(0, Math.min(1024, rawJson.length())) + (rawJson.length() > 1024 ? "...(truncated)" : "") : "<empty>"),
                    jpe);
        } catch (SdkException sdkEx) {
            log.error("PortConfig: AWS SDK error while fetching s3://{}/{} (region={}). Exception: {}", bucket, key, region, sdkEx.toString(), sdkEx);
        } catch (IOException ioEx) {
            log.error("PortConfig: IO error while reading S3 object s3://{}/{} - message: {}", bucket, key, ioEx.getMessage(), ioEx);
        } catch (Exception e) {
            log.error("PortConfig: Unexpected error while loading port config from s3://{}/{} (region={}).", bucket, key, region, e);
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

    /**
     * Return list of ports configured for MLLP over TCP.
     * If config is not loaded or empty, returns empty list.
     */
    public List<Integer> getMllpPorts() {
        if (!isLoaded()) {
            loadConfig();
        }
        return mllpPorts != null ? mllpPorts : Collections.emptyList();
    }
}
