package org.techbd.util.fhir;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.techbd.config.AppConfig;
import org.techbd.config.AppConfig.FhirV4Config;
import org.techbd.config.Constants;
import org.techbd.config.Interactions.Header;

public class FHIRUtil {

    private static AppConfig appConfig;
    private static Map<String, String> PROFILE_MAP;
    private static String BASE_FHIR_URL;
    public static final String BUNDLE = "bundle";

    // Initialize the utility with configuration
    public static void initialize(AppConfig config) {
        appConfig = config;
        BASE_FHIR_URL = config.getBaseFHIRURL();
        PROFILE_MAP = config.getStructureDefinitionsUrls() != null
                ? Collections.unmodifiableMap(config.getStructureDefinitionsUrls())
                : Collections.emptyMap();
    }

    public static String getProfileUrl(String key) {
        if (BASE_FHIR_URL == null) {
            throw new IllegalStateException("FHIRUtil has not been initialized. Call initialize() first.");
        }
        return BASE_FHIR_URL + PROFILE_MAP.getOrDefault(key, "");
    }

    public static String getBundleProfileUrl() {
        return getProfileUrl(BUNDLE);
    }

    public static String getProfileUrl(String baseFhirUrl, String key) {
        return baseFhirUrl + PROFILE_MAP.getOrDefault(key, "");
    }

    public static List<String> getAllowedProfileUrls(AppConfig appConfig) {
        List<String> allowedProfileUrls = new ArrayList<>();

        if (appConfig.getIgPackages() != null && appConfig.getIgPackages().containsKey("fhir-v4")) {
            FhirV4Config fhirV4Config = appConfig.getIgPackages().get("fhir-v4");
            Map<String, Map<String, String>> shinNyPackages = fhirV4Config.getShinnyPackages();

            for (Map<String, String> igPackage : shinNyPackages.values()) {
                String profileBaseUrl = igPackage.getOrDefault("profile-base-url", "");
                String packageFhirProfileUrl = getProfileUrl(profileBaseUrl, BUNDLE);
                allowedProfileUrls.add(packageFhirProfileUrl);
            }
        }
        return allowedProfileUrls;
    }

    public static void validateBaseFHIRProfileUrl(AppConfig appConfig, String baseFHIRProfileUrl) {
        if (StringUtils.isNotEmpty(baseFHIRProfileUrl)) {
            String profileUrl = getProfileUrl(baseFHIRProfileUrl, BUNDLE);
            List<String> allowedUrls = getAllowedProfileUrls(appConfig);

            if (!allowedUrls.contains(profileUrl)) {
                String supportedUrls = String.join(", ", allowedUrls);
                throw new IllegalArgumentException("Invalid Base FHIR profile URL provided : " + baseFHIRProfileUrl +
                        " in header 'X-TechBD-Base-FHIR-URL' . Supported  SHIN-NY IG URLs: " + supportedUrls);
            }
        }
    }

    public static List<Header> createResponseHeaders(Map<String, String> headerParameters,
            Map<String, String> requestParameters) {
        final var startTime = Instant
                .parse(requestParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME));
        final var finishTime = Instant.now();
        final Duration duration = Duration.between(startTime, finishTime);

        // Convert time metrics to string
        final String startTimeText = startTime.toString();
        final String finishTimeText = finishTime.toString();
        final String durationMsText = String.valueOf(duration.toMillis());
        final String durationNsText = String.valueOf(duration.toNanos());

        // Add observability headers
        headerParameters.put("X-Observability-Metric-Interaction-Start-Time", startTimeText);
        headerParameters.put("X-Observability-Metric-Interaction-Finish-Time", finishTimeText);
        headerParameters.put("X-Observability-Metric-Interaction-Duration-Millisecs", durationMsText);
        headerParameters.put("X-Observability-Metric-Interaction-Duration-Nanosecs", durationNsText);

        // Convert Map to List<Header>
        return headerParameters.entrySet().stream()
                .map(entry -> new Header(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Creates a list of Header records by merging values from both headerParameters
     * and requestParameters.
     * If a key appears in both maps, the value from requestParameters will
     * overwrite the value from headerParameters.
     *
     * @param headerParameters  a Map containing header parameters (e.g. from client
     *                          headers)
     * @param requestParameters a Map containing request parameters (e.g. from the
     *                          sourceMap)
     * @return a List of Header records containing the merged headers.
     */
    public static List<Header> createRequestHeaders(Map<String, String> headerParameters,
            Map<String, String> requestParameters) {
        // Merge headerParameters and requestParameters into a single map.
        Map<String, String> mergedMap = new HashMap<>();
        if (headerParameters != null) {
            mergedMap.putAll(headerParameters);
        }
        if (requestParameters != null) {
            mergedMap.putAll(requestParameters);
        }

        // Convert the merged map into a List of Header records.
        return mergedMap.entrySet().stream()
                .map(entry -> new Header(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
}