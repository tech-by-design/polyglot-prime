package org.techbd.ingest.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

/**
 * Singleton Spring Boot component to load port configuration from AWS S3 or local JSON (for sandbox).
 */
@Component
public class PortConfig implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PortConfig.class);
    private static final String ENV_BUCKET = "PORT_CONFIG_S3_BUCKET";
    private static final String ENV_KEY = "PORT_CONFIG_S3_KEY";
    private static final String ENV_REGION = "AWS_REGION";
    private static final String ENV_PROFILE = "SPRING_PROFILES_ACTIVE";

    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private List<PortEntry> portConfigurationList = Collections.emptyList();
    private List<Integer> mllpPorts = Collections.emptyList();
    private final S3Client s3Client;

    @Autowired
    public PortConfig(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Getter
    public static class PortEntry {
        public int port;
        public String responseType;
        public String protocol;
        public String ipAddress;
        public String cidrBlock;
        public List<String> whitelistIps;
        public boolean mtlsEnabled;
        public String mtls;
        public String execType;
        public String trafficTo;
        public String route;
        public String queue;
        public String dataDir;
        public String metadataDir;
        public String sourceId;
        public String msgType;

        public boolean isMtlsEnabled() {
            return mtlsEnabled || (mtls != null && !mtls.isBlank());
        }
    }

    @Override
    public void afterPropertiesSet() {
        loadConfig();
    }

    public synchronized void loadConfig() {
        if (loaded.get()) return;

        String activeProfile = System.getenv(ENV_PROFILE);
        if ("sandbox".equalsIgnoreCase(activeProfile)) {
            log.info("PortConfig: Sandbox profile detected - loading configuration from local file.");
            loadConfigFromLocal();
            return;
        }

        String bucket = System.getenv(ENV_BUCKET);
        String key = System.getenv(ENV_KEY);
        String region = System.getenv(ENV_REGION) != null ? System.getenv(ENV_REGION) : "us-east-1";

        if (bucket == null || key == null) {
            log.error("PortConfig: Missing required environment variables {} or {}", ENV_BUCKET, ENV_KEY);
            return;
        }

        if (this.s3Client == null) {
            log.error("PortConfig: S3Client bean not available - cannot load port config");
            return;
        }

        try {
            log.info("PortConfig: Loading port config from s3://{}/{} (region={})", bucket, key, region);
            var req = GetObjectRequest.builder().bucket(bucket).key(key).build();
            var bytes = s3Client.getObjectAsBytes(req);
            var rawJson = new String(bytes.asByteArray(), StandardCharsets.UTF_8);
            parseAndSetConfig(rawJson);
            loaded.set(true);
        } catch (JsonProcessingException jpe) {
            log.error("PortConfig: Failed to parse port config JSON", jpe);
        } catch (SdkException | IOException ex) {
            log.error("PortConfig: Error while reading S3 object s3://{}/{}", bucket, key, ex);
        } catch (Exception e) {
            log.error("PortConfig: Unexpected error while loading from S3", e);
        }
    }

    /**
     * Loads configuration from local JSON file in sandbox mode.
     */
    private void loadConfigFromLocal() {
        try {
            var path = Path.of("src/main/resources/list.json");
            if (!Files.exists(path)) {
                log.error("PortConfig: Local config file not found at {}", path.toAbsolutePath());
                return;
            }

            var rawJson = Files.readString(path, StandardCharsets.UTF_8);
            log.info("PortConfig: Loaded local JSON config ({} bytes)", rawJson.length());
            parseAndSetConfig(rawJson);
            loaded.set(true);
        } catch (IOException e) {
            log.error("PortConfig: Failed to read local config file from resources", e);
        } catch (Exception e) {
            log.error("PortConfig: Unexpected error while loading local config", e);
        }
    }

    /**
     * Common JSON parsing logic shared by S3 and local loaders.
     */
    private void parseAndSetConfig(String rawJson) throws IOException {
        var mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var configList = mapper.readValue(rawJson, new TypeReference<List<PortEntry>>() {});
        portConfigurationList = Optional.ofNullable(configList).orElse(Collections.emptyList());

        var mports = portConfigurationList.stream()
                .filter(Objects::nonNull)
                .filter(p -> "mllp".equalsIgnoreCase(p.responseType) && "tcp".equalsIgnoreCase(p.protocol))
                .map(p -> p.port)
                .distinct()
                .sorted()
                .toList();

        mllpPorts = Collections.unmodifiableList(mports);
        log.info("PortConfig: Parsed {} port entries; MLLP ports={}", portConfigurationList.size(), mllpPorts);
    }

    public boolean isLoaded() {
        return loaded.get();
    }

    public List<PortEntry> getPortConfigurationList() {
        if (!isLoaded()) loadConfig();
        return portConfigurationList;
    }

    public List<Integer> getMllpPorts() {
        if (!isLoaded()) loadConfig();
        return mllpPorts != null ? mllpPorts : Collections.emptyList();
    }

    public Optional<PortEntry> findEntryForPort(int port) {
        if (!isLoaded()) loadConfig();
        return portConfigurationList.stream()
                .filter(Objects::nonNull)
                .filter(p -> p.port == port)
                .findFirst();
    }
}
