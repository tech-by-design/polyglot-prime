package org.techbd.service.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class UxReportableObservability implements HandlerInterceptor, WebMvcConfigurer {

    private static final String START_TIME_ATTRIBUTE = "startTime";

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler)
            throws Exception {
        request.setAttribute(START_TIME_ATTRIBUTE, Instant.now());
        return true;
    }

    @Override
    public void afterCompletion(final HttpServletRequest request, final HttpServletResponse response,
            final Object handler, final Exception ex)
            throws Exception {
        final var startTime = (Instant) request.getAttribute(START_TIME_ATTRIBUTE);
        final var finishTime = Instant.now();
        final var duration = Duration.between(startTime, finishTime);

        final var startTimeText = startTime.toString();
        final var finishTimeText = finishTime.toString();
        final var durationMsText = String.valueOf(duration.toMillis());
        final var durationNsText = String.valueOf(duration.toNanos());

        // set response headers for those clients that can access HTTP headers
        response.addHeader("X-Observability-Metric-Interaction-Start-Time", startTimeText);
        response.addHeader("X-Observability-Metric-Interaction-Finish-Time", finishTimeText);
        response.addHeader("X-Observability-Metric-Interaction-Duration-Nanosecs", durationMsText);
        response.addHeader("X-Observability-Metric-Interaction-Duration-Millisecs", durationNsText);
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains;");       
        // set a cookie which is accessible to a JavaScript user agent that cannot
        // access HTTP headers (usually HTML pages in web browser cannot access HTTP
        // response headers)
        final var metricCookie = new Cookie("Observability-Metric-Interaction-Active",
                URLEncoder.encode("{ \"startTime\": \"" + startTimeText + "\", \"finishTime\": \"" + finishTimeText
                        + "\", \"durationMillisecs\": \"" + durationMsText + "\", \"durationNanosecs\": \""
                        + durationNsText + "\" }", StandardCharsets.UTF_8.toString()));
        metricCookie.setPath("/"); // Set path as required
        metricCookie.setHttpOnly(false); // Ensure the cookie is accessible via JavaScript
        response.addCookie(metricCookie);
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }
}