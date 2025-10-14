package org.techbd.ingest.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
* Singleton Spring Boot component to load port configuration from a local file.
*
* File location: src/test/resources/portconfig.json
*/
@Component
public class PortConfig implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PortConfig.class);
    private static final String LOCAL_CONFIG_PATH = "src/main/resources/portconfig.json";

    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private List<PortEntry> portConfigurationList = Collections.emptyList();

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

        String rawJson = null;
        try {
            log.info("PortConfig: Attempting to load port config from local file: {}", LOCAL_CONFIG_PATH);
            rawJson = Files.readString(Path.of(LOCAL_CONFIG_PATH), StandardCharsets.UTF_8);
            log.debug("PortConfig: Fetched {} bytes from {}", rawJson.getBytes(StandardCharsets.UTF_8).length, LOCAL_CONFIG_PATH);

            // Log up to first 2KB to avoid huge logs
            String preview = rawJson.length() > 2048 ? rawJson.substring(0, 2048) + "...(truncated)" : rawJson;
            log.trace("PortConfig: JSON preview: {}", preview);

            ObjectMapper mapper = new ObjectMapper();
            portConfigurationList = mapper.readValue(rawJson, new TypeReference<List<PortEntry>>() {});
            loaded.set(true);
            log.info("PortConfig: Loaded {} port entries from {}", portConfigurationList.size(), LOCAL_CONFIG_PATH);

        } catch (JsonProcessingException jpe) {
            log.error("PortConfig: Failed to parse port config JSON (json length={}). JSON (truncated): {}",
                    rawJson == null ? 0 : rawJson.length(),
                    (rawJson != null && rawJson.length() > 0 ? rawJson.substring(0, Math.min(1024, rawJson.length())) + (rawJson.length() > 1024 ? "...(truncated)" : "") : "<empty>"),
                    jpe);
        } catch (IOException ioEx) {
            log.error("PortConfig: IO error while reading {} - message: {}", LOCAL_CONFIG_PATH, ioEx.getMessage(), ioEx);
        } catch (Exception e) {
            log.error("PortConfig: Unexpected error while loading port config from {}.", LOCAL_CONFIG_PATH, e);
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