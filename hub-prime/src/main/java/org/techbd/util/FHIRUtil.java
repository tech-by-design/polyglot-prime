package org.techbd.util;

import java.time.Instant;
import java.util.ArrayList;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.conf.Configuration;
import org.techbd.config.Constants;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.service.http.hub.prime.AppConfig.FhirV4Config;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Component
public class FHIRUtil {

    private final AppConfig appConfig;

    public static Map<String, String> PROFILE_MAP;
    private static String BASE_FHIR_URL;
    public static final String BUNDLE = "bundle";
    private static final Logger LOG = LoggerFactory.getLogger(FHIRUtil.class);
    public FHIRUtil(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @PostConstruct
    private void init() {
        BASE_FHIR_URL = appConfig.getBaseFHIRURL();
        PROFILE_MAP = appConfig.getStructureDefinitionsUrls() != null
                ? Collections.unmodifiableMap(appConfig.getStructureDefinitionsUrls())
                : Collections.emptyMap();
    }

    public static String getProfileUrl(String key) {
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