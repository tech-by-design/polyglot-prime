package org.techbd.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

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
     * Finds the first non-null and non-empty header value from the given header
     * names.
     *
     * @param request     the HttpServletRequest object
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

    /**
     * Retrieves the content of the specified URL as a String.
     * 
     * <p>
     * This method sends an HTTP GET request to the provided URL and returns the
     * response body as a String.
     * The content is read using UTF-8 encoding.
     * </p>
     * 
     * <p>
     * Usage example:
     * </p>
     * 
     * <pre>
     * {@code
     * String content = textFromURL("http://example.com");
     * System.out.println(content);
     * }
     * </pre>
     * 
     * @param requestURL the URL from which to retrieve the content
     * @return the content of the URL as a String
     * @throws IOException          if an I/O error occurs while sending or
     *                              receiving the HTTP request
     * @throws InterruptedException if the operation is interrupted
     */
    public static String textFromURL(final String requestURL) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestURL))
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        return response.body();
    }

}