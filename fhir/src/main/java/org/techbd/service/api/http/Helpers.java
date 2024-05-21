package org.techbd.service.api.http;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

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

        if ((scheme.equals("http") && serverPort != 80) || (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }

        url.append(contextPath);
        return url.toString();
    }

    public static final Map<String, Object> getRequestInfo(HttpServletRequest request, Map<String, Object> payload) {
        Map<String, Object> requestInfo = new HashMap<>();

        // Get headers
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        requestInfo.put("headers", headers);

        // Get parameters
        Map<String, String[]> parameters = request.getParameterMap();
        requestInfo.put("parameters", parameters);

        // Get method
        requestInfo.put("method", request.getMethod());

        // Get request URI
        requestInfo.put("requestURI", request.getRequestURI());

        // Get query string
        requestInfo.put("queryString", request.getQueryString());

        // Get remote address
        requestInfo.put("remoteAddr", request.getRemoteAddr());

        // Get session attributes if any
        Map<String, Object> sessionAttributes = new HashMap<>();
        Enumeration<String> attributeNames = request.getSession().getAttributeNames();
        while (attributeNames.hasMoreElements()) {
            String attributeName = attributeNames.nextElement();
            sessionAttributes.put(attributeName, request.getSession().getAttribute(attributeName));
        }
        requestInfo.put("sessionAttributes", sessionAttributes);

        // Include the request body (payload)
        requestInfo.put("body", payload);

        return requestInfo;
    }

}
