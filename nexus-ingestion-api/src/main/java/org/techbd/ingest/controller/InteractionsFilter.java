package org.techbd.ingest.controller;

import java.io.IOException;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.techbd.ingest.feature.FeatureEnum;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class InteractionsFilter extends OncePerRequestFilter {

    private final TemplateLogger LOG;

    public InteractionsFilter(AppLogger appLogger)  {
        this.LOG = appLogger.getLogger(InteractionsFilter.class);
    }   

    @Override
    protected void doFilterInternal(final HttpServletRequest origRequest, final HttpServletResponse origResponse,
                                    final FilterChain chain) throws IOException, ServletException {
        if (FeatureEnum.isEnabled(FeatureEnum.DEBUG_LOG_REQUEST_HEADERS)
                && !origRequest.getRequestURI().equals("/")
                && !origRequest.getRequestURI().startsWith("/actuator/health")) {
            String interactionId = UUID.randomUUID().toString();
            origRequest.setAttribute("interactionId", interactionId);
            LOG.info("Incoming Request - interactionId={}", interactionId);

            var headerNames = origRequest.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                var headerName = headerNames.nextElement();
                var headerValue = origRequest.getHeader(headerName);
                LOG.info("{} - Header: {} = {} for interaction id: {}", FeatureEnum.DEBUG_LOG_REQUEST_HEADERS,
                        headerName, headerValue, interactionId);
            }
        }
        chain.doFilter(origRequest, origResponse);
    }
}
