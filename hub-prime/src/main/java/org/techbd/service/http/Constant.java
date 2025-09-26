package org.techbd.service.http;

import java.util.Arrays;

public class Constant {

    /** Stateless URLs */ 
    public static final String[] STATELESS_API_URLS = {
            "/Bundle", "/Bundle/**",
            "/flatfile/csv/Bundle", "/flatfile/csv/Bundle/**",
            "/Hl7/v2", "/Hl7/v2/",
            "/api/expect", "/api/expect/**"
    };

    /** Public URLs (Authentication not required) */
    public static final String[] UNAUTHENTICATED_URLS = {
            "/login/**", "/oauth2/**",
            "/",
            "/login/oauth2/code/**", 
            "/oauth2/authorization/**",
            // OAuth2 metadata discovery (e.g., .well-known/openid-configuration)
            "/.well-known/**",
            "/metadata",
            "/docs/api/interactive/swagger-ui/**", "/support/**",
            "/docs/api/interactive/**",
            "/docs/api/openapi/**",
            "/api/permissions/**",
            "/error", "/error/**"
    };

/** URLs excluded from RolePermissionInterceptor */
public static final String[] INTERCEPTOR_EXCLUDED_URLS = {
        // Auth & OAuth2
        "/login/**",
        "/logout/**",
        "/oauth2/**",
        "/login/oauth2/code/**",
        "/oauth2/authorization/**",

        "/",
        "/public/**",

        "/error",
        "/error/**",

        "/.well-known/**",
        "/metadata",
        "/docs/api/**",
        "/support/**",

        "/api/permissions/**",

        "/favicon.ico",
        "/css/**",
        "/js/**",
        "/images/**",
        "/webjars/**",
        "/fonts/**"
};


    public static final String HOME_PAGE_URL = "/home";
    public static final String LOGIN_PAGE_URL = "/login";
    public static final String SESSIONID_COOKIE = "JSESSIONID";
    public static final String LOGOUT_PAGE_URL = "/";
    public static final String SESSION_TIMEOUT_URL = "/?timeout=true";

    public static final long HSTS_MAX_AGE = 31536000; // HSTS max age for 1 year

    public static final boolean isStatelessApiUrl(String requestUrl) {
        if (requestUrl == null) {
            return false;
        }
        return Arrays.stream(STATELESS_API_URLS)
                .anyMatch(requestUrl::contains);
    }
}
