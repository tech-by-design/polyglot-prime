package org.techbd.ingest.config;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

@Component
public class MtlsConfig {

    @Value("${mtls.trust-store-mapping-param}")
    private String mappingParamName;

    // Optional SSM param name that contains enabled ports (JSON array or CSV)
    @Value("${mtls.enabled-ports-param:}")
    private String enabledPortsParamName;

    private final SsmClient ssmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public MtlsConfig(SsmClient ssmClient) {
        this.ssmClient = ssmClient;
    }

    public Map<String, String> getTrustStoreMappings() {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(mappingParamName)
                    .withDecryption(true)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            String json = response.parameter().value();
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse trust store mappings JSON from SSM param: " + mappingParamName, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load trust store mappings from SSM param: " + mappingParamName, e);
        }
    }

    /**
     * Loads the set of enabled mTLS ports from SSM parameter.
     * Supports JSON array (e.g. [8080,8443]) or comma-separated list ("8080,8443").
     * Returns an empty set on error or if no param configured.
     */
    public Set<Integer> getEnabledPorts() {
        if (enabledPortsParamName == null || enabledPortsParamName.isBlank()) {
            return Collections.emptySet();
        }
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(enabledPortsParamName)
                    .withDecryption(true)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            String val = response.parameter().value();
            if (val == null || val.isBlank()) return Collections.emptySet();

            val = val.trim();
            List<Integer> ports;
            if (val.startsWith("[")) {
                ports = objectMapper.readValue(val, new TypeReference<List<Integer>>() {});
            } else {
                ports = Arrays.stream(val.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());
            }
            return new HashSet<>(ports);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
}
