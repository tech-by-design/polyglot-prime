package org.techbd.util.fhir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.CoreAppConfig;
import org.techbd.config.CoreAppConfig.FhirV4Config;
import org.techbd.config.Interactions.Header;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CoreFHIRUtil {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CoreFHIRUtil.class);
    private final CoreAppConfig coreAppConfig;
    public static Map<String, String> PROFILE_MAP;
    private static String BASE_FHIR_URL;
    public static final String BUNDLE = "bundle";

    public CoreFHIRUtil(CoreAppConfig coreAppConfig) {
        this.coreAppConfig = coreAppConfig;
        BASE_FHIR_URL = coreAppConfig.getBaseFHIRURL();
        PROFILE_MAP = coreAppConfig.getStructureDefinitionsUrls() != null
                ? Collections.unmodifiableMap(coreAppConfig.getStructureDefinitionsUrls())
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

    public static List<String> getAllowedProfileUrls(CoreAppConfig appConfig) {
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

    public static void validateBaseFHIRProfileUrl(CoreAppConfig appConfig, String baseFHIRProfileUrl) {
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
        final var startTime = requestParameters != null && requestParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME) != null 
            ? Instant.parse(requestParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME))
            : Instant.now();
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
        public static String extractBundleId(String json,String interactionId) {
        try {
            JsonNode rootNode = Configuration.objectMapper.readTree(json);
            if (!"Bundle".equals(rootNode.path("resourceType").asText())) {
                return "Bundle id not provided"; 
            }
            return rootNode.path("id").asText("Bundle id not provided");
        } catch (Exception e) {
            LOG.error("Exception fetching bundle Id for interactionId :  ",interactionId , e.getMessage());
            return StringUtils.EMPTY;
        }
    }

    
   
    public static Map<String, Object> buildHeaderParametersMap(String tenantId, String customDataLakeApi,
            String dataLakeApiContentType, String requestUriToBeOverridden,
            String validationSeverityLevel, String healthCheck, String correlationId, String provenance, String requestedIgVersion   ) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.TENANT_ID, tenantId);
        addIfNotEmpty(headers, Constants.CUSTOM_DATA_LAKE_API, customDataLakeApi);
        addIfNotEmpty(headers, Constants.DATA_LAKE_API_CONTENT_TYPE, dataLakeApiContentType);
        addIfNotEmpty(headers, Constants.OVERRIDE_REQUEST_URI, requestUriToBeOverridden);
        addIfNotEmpty(headers, Constants.VALIDATION_SEVERITY_LEVEL, validationSeverityLevel);
        addIfNotEmpty(headers, Constants.HEALTH_CHECK, healthCheck);
        addIfNotEmpty(headers, Constants.CORRELATION_ID, correlationId);
        addIfNotEmpty(headers, Constants.PROVENANCE, provenance);
        addIfNotEmpty(headers, Constants.SHIN_NY_IG_VERSION, requestedIgVersion );
        
        return headers;
    }

    public static Map<String, Object> buildRequestParametersMap(Map<String,Object> requestParameters ,Boolean deleteSessionCookie,
            String mtlsStrategy, String source, String groupInteractionId, String masterInteractionId,
            String requestUri) {
        addIfNotEmpty(requestParameters, Constants.MTLS_STRATEGY, mtlsStrategy);
        addIfNotEmpty(requestParameters, Constants.SOURCE_TYPE, source);
        addIfNotEmpty(requestParameters, Constants.GROUP_INTERACTION_ID, groupInteractionId);
        addIfNotEmpty(requestParameters, Constants.MASTER_INTERACTION_ID, masterInteractionId);
        addIfNotEmpty(requestParameters, Constants.REQUEST_URI, requestUri);
        if (null != deleteSessionCookie) {
            requestParameters.put(Constants.DELETE_SESSION, Boolean.toString(deleteSessionCookie));
        }
        return requestParameters;
    }

    public static void addIfNotEmpty(Map<String, Object> headers, String key, String value) {
        if (StringUtils.isNotEmpty(value)) {
            headers.put(key, value);
        }
    }

    
    public static void addCookieAndHeadersToResponse(HttpServletResponse response, Map<String, Object> responseParameters,
            Map<String, Object> requestParameters) {
        if (responseParameters.get(Constants.METRIC_COOKIE) != null) {
            response.addCookie((Cookie) responseParameters.get(Constants.METRIC_COOKIE));
        }
        if (responseParameters.get(Constants.HEADER) != null) {
            response.addHeader(Constants.HEADER, responseParameters.get(Constants.HEADER).toString());
        }
        if (requestParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME) != null) {
            response.addHeader(Constants.START_TIME_ATTRIBUTE,
                    (String) requestParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME));
        }
        if (responseParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME) != null) {
            response.addHeader(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME,
                    responseParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME).toString());
        }
        if (responseParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_FINISH_TIME) != null) {
            response.addHeader(Constants.OBSERVABILITY_METRIC_INTERACTION_FINISH_TIME,
                    responseParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_FINISH_TIME).toString());
        }
        if (responseParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_DURATION_NANOSECS) != null) {
            response.addHeader(Constants.OBSERVABILITY_METRIC_INTERACTION_DURATION_NANOSECS,
                    responseParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_DURATION_NANOSECS).toString());
        }
    }

    public static Map<String, Object> extractFields(JsonNode payload) {
        var result = new HashMap<String, Object>();
        for (var key : new String[] { "error", "interaction_id", "hub_nexus_interaction_id" }) {
            if (payload.has(key)) {
                result.put(key, payload.get(key).asText());
            }
        }

        if (payload.has("payload")) {
            result.put("payload", payload.get("payload"));
        }

        return result;
    }

    
    public static boolean ensureEngineVersionMatches(String requestedPath) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            URL resourceUrl = classLoader.getResource(requestedPath);

            if (resourceUrl == null) {
                LOG.warn("IG path '{}' not found in classpath.", requestedPath);
                return false;
            }

            LOG.info("Checking IG resources at: {}", resourceUrl);

            // --- Handle "file:" protocol (local dev) ---
            if ("file".equalsIgnoreCase(resourceUrl.getProtocol())) {
                File dir = new File(resourceUrl.toURI());
                if (!dir.exists() || !dir.isDirectory()) {
                    LOG.warn("IG path is not a valid directory: {}", dir.getAbsolutePath());
                    return false;
                }

                String folderName = dir.getName();
                if (folderName.startsWith("v")) {
                    folderName = folderName.substring(1);
                }

                File[] files = dir.listFiles();
                if (files != null && files.length > 0) {
                    LOG.info("✅ Matching IG version '{}' found in local path: {}", folderName, requestedPath);
                    return true;
                } else {
                    LOG.warn("IG version folder '{}' is empty in path: {}", folderName, requestedPath);
                }
                return false;
            }

            // --- Handle "jar:" protocol (production / Spring Boot) ---
            if ("jar".equalsIgnoreCase(resourceUrl.getProtocol())) {
                try (InputStream is = resourceUrl.openStream()) {
                    if (is != null) {
                        LOG.info("✅ IG version '{}' exists inside JAR for path: {}", requestedPath, resourceUrl);
                        return true;
                    }
                } catch (IOException e) {
                    LOG.error("Error accessing IG path inside JAR: {}", requestedPath, e);
                    return false;
                }
            }

            LOG.warn("⚠ Unsupported protocol '{}' for IG path: {}", resourceUrl.getProtocol(), resourceUrl);
            return false;

        } catch (Exception e) {
            LOG.error("Error resolving IG folder path: {}", requestedPath, e);
            return false;
        }
    }
}