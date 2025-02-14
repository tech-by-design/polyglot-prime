package org.techbd.util;

import java.util.Collections;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.techbd.service.http.hub.prime.AppConfig;

import jakarta.annotation.PostConstruct;

@Component
public class FHIRUtil {

    private final AppConfig appConfig;

    private static Map<String, String> PROFILE_MAP;
    private static String BASE_FHIR_URL;
    public static final String BUNDLE = "bundle";
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
}

