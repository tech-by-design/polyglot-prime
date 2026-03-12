package org.techbd.corelib.util;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.techbd.corelib.config.Configuration;
import org.techbd.corelib.config.Constants;
import org.techbd.corelib.config.CoreAppConfig;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class CoreFHIRUtil {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CoreFHIRUtil.class);
    private final CoreAppConfig appConfig;
    public static Map<String, String> PROFILE_MAP;
    private static String BASE_FHIR_URL;
    public static final String BUNDLE = "bundle";

    public CoreFHIRUtil(CoreAppConfig appConfig) {
        this.appConfig = appConfig;
        BASE_FHIR_URL = appConfig.getBaseFHIRURL();
        PROFILE_MAP = appConfig.getStructureDefinitionsUrls() != null
                ? Collections.unmodifiableMap(appConfig.getStructureDefinitionsUrls())
                : Collections.emptyMap();
    }

    public static String getProfileUrl(String key) {
        if (BASE_FHIR_URL == null) {
            throw new IllegalStateException("FHIRUtil has not been initialized. Call initialize() first.");
        }
        return BASE_FHIR_URL + PROFILE_MAP.getOrDefault(key, "");
    }

    public static String getProfileUrl(String baseFhirUrl, String key) {
        return baseFhirUrl + PROFILE_MAP.getOrDefault(key, "");
    }

    public static String getBundleProfileUrl() {
        return getProfileUrl(BUNDLE);
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

    public static Map<String, Object> buildHeaderParametersMap(String tenantId, String customDataLakeApi,
            String dataLakeApiContentType, String requestUriToBeOverridden,
            String validationSeverityLevel, String healthCheck, String correlationId, String provenance,
            String requestedIgVersion) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.TENANT_ID, tenantId);
        addIfNotEmpty(headers, Constants.CUSTOM_DATA_LAKE_API, customDataLakeApi);
        addIfNotEmpty(headers, Constants.DATA_LAKE_API_CONTENT_TYPE, dataLakeApiContentType);
        addIfNotEmpty(headers, Constants.OVERRIDE_REQUEST_URI, requestUriToBeOverridden);
        addIfNotEmpty(headers, Constants.VALIDATION_SEVERITY_LEVEL, validationSeverityLevel);
        addIfNotEmpty(headers, Constants.HEALTH_CHECK, healthCheck);
        addIfNotEmpty(headers, Constants.CORRELATION_ID, correlationId);
        addIfNotEmpty(headers, Constants.PROVENANCE, provenance);
        addIfNotEmpty(headers, Constants.SHIN_NY_IG_VERSION, requestedIgVersion);

        return headers;
    }

    public static Map<String, Object> buildRequestParametersMap(Map<String, Object> requestParameters,
            Boolean deleteSessionCookie,
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

    public static void addCookieAndHeadersToResponse(HttpServletResponse response,
            Map<String, Object> responseParameters,
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

    public static String extractBundleId(String json, String interactionId) {
        try {
            JsonNode rootNode = Configuration.objectMapper.readTree(json);
            if (!"Bundle".equals(rootNode.path("resourceType").asText())) {
                return "Bundle id not provided";
            }
            return rootNode.path("id").asText("Bundle id not provided");
        } catch (Exception e) {
            LOG.error("Exception fetching bundle Id for interactionId :  ", interactionId, e.getMessage());
            return StringUtils.EMPTY;
        }
    }

    public static Map<String, Object> extractRequestDetails(HttpServletRequest request) {
        Map<String, Object> requestDetails = new LinkedHashMap<>();
        requestDetails.put(Constants.REQUEST_URL, request.getRequestURL().toString());
        requestDetails.put(Constants.FULL_URL, request.getRequestURL()
                .append(request.getQueryString() != null ? "?" + request.getQueryString() : "").toString());
        requestDetails.put(Constants.URI, request.getRequestURI());
        requestDetails.put(Constants.REQUEST_ID, UUID.randomUUID().toString());
        requestDetails.put(Constants.REQUEST_SESSION_ID, request.getSession().getId());
        requestDetails.put(Constants.REMOTE_ADDR, request.getRemoteAddr());
        requestDetails.put(Constants.REMOTE_HOST, request.getRemoteHost());
        requestDetails.put(Constants.REMOTE_PORT, request.getRemotePort());
        requestDetails.put(Constants.LOCAL_ADDR, request.getLocalAddr());
        requestDetails.put(Constants.LOCAL_PORT, request.getLocalPort());
        requestDetails.put(Constants.SERVER_NAME, request.getServerName());
        requestDetails.put(Constants.SERVER_PORT, request.getServerPort());
        requestDetails.put(Constants.SCHEME, request.getScheme());
        requestDetails.put(Constants.SECURE, request.isSecure());
        requestDetails.put(Constants.USER_AGENT, request.getHeader("User-Agent"));
        requestDetails.put(Constants.REFERER, request.getHeader("Referer"));
        requestDetails.put(Constants.ACCEPT_LANGUAGE, request.getHeader("Accept-Language"));
        requestDetails.put(Constants.TIMESTAMP, Instant.now());
        requestDetails.put(Constants.CONTENT_TYPE, request.getContentType());
        requestDetails.put(Constants.CHARACTER_ENCODING, request.getCharacterEncoding());
        requestDetails.put(Constants.CONTENT_LENGTH, request.getContentLength());
        requestDetails.put(Constants.QUERY_STRING, request.getQueryString());
        requestDetails.put(Constants.PROTOCOL, request.getProtocol());
        requestDetails.put(Constants.AUTH_TYPE, request.getAuthType());
        requestDetails.put(Constants.USER_PRINCIPAL,
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null);
        requestDetails.put(Constants.CONTEXT_PATH, request.getContextPath());
        requestDetails.put(Constants.SERVLET_PATH, request.getServletPath());
        requestDetails.put(Constants.PATH_INFO, request.getPathInfo());
        requestDetails.put(Constants.REQUESTED_SESSION_ID, request.getRequestedSessionId());
        requestDetails.put(Constants.REQUESTED_SESSION_ID_VALID, request.isRequestedSessionIdValid());
        requestDetails.put(Constants.LOCALE, request.getLocale().toString());

        Map<String, String> headers = new HashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name -> headers.put(name, request.getHeader(name)));
        requestDetails.put(Constants.HEADERS, headers);

        Map<String, String[]> parameterMap = request.getParameterMap();
        Map<String, Object> flatParams = new HashMap<>();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            flatParams.put(entry.getKey(),
                    entry.getValue().length == 1 ? entry.getValue()[0] : Arrays.asList(entry.getValue()));
        }
        requestDetails.put(Constants.PARAMETERS, flatParams);

        HttpSession session = request.getSession(false);
        if (session != null) {
            Map<String, Object> sessionAttributes = new HashMap<>();
            Enumeration<String> attributeNames = session.getAttributeNames();
            while (attributeNames.hasMoreElements()) {
                String attrName = attributeNames.nextElement();
                sessionAttributes.put(attrName, session.getAttribute(attrName));
            }
            requestDetails.put(Constants.SESSION, sessionAttributes);
        } else {
            requestDetails.put(Constants.SESSION, null);
        }

        if (request.getCookies() != null) {
            List<Map<String, Object>> cookies = Arrays.stream(request.getCookies())
                    .map(cookie -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", cookie.getName());
                        map.put("value", cookie.getValue());
                        map.put("domain", cookie.getDomain());
                        map.put("path", cookie.getPath());
                        map.put("maxAge", cookie.getMaxAge());
                        return map;
                    }).collect(Collectors.toList());
            requestDetails.put(Constants.COOKIES, cookies);
        } else {
            requestDetails.put(Constants.COOKIES, Collections.emptyList());
        }
        return requestDetails;
    }


}