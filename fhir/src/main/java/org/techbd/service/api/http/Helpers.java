package org.techbd.service.api.http;

import jakarta.servlet.http.HttpServletRequest;

public class Helpers {

    public static String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme(); // http
        String serverName = request.getServerName(); // hostname.com
        int serverPort = request.getServerPort(); // 80
        String contextPath = request.getContextPath(); // /myapp

        // Construct the full URL
        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);

        if (("http".equals(scheme) && serverPort != 80) || ("https".equals(scheme) && serverPort != 443)) {
            url.append(":").append(serverPort);
        }

        url.append(contextPath);
        return url.toString();
    }

    /**
     * Finds the first non-null and non-empty header value from the given header names.
     *
     * @param request the HttpServletRequest object
     * @param headerNames the array of header names to check
     * @return the first non-null and non-empty header value, or null if none found
     */
    public static String findFirstHeaderValue(HttpServletRequest request, String... headerNames) {
        for (String headerName : headerNames) {
            String headerValue = request.getHeader(headerName);
            if (headerValue != null && !headerValue.isEmpty()) {
                return headerValue;
            }
        }
        return null;
    }

}