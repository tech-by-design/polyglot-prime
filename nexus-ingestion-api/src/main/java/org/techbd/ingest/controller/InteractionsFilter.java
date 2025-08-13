package org.techbd.ingest.controller;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.techbd.ingest.feature.FeatureEnum;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class InteractionsFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(InteractionsFilter.class);
   
    @Override
    protected void doFilterInternal(final HttpServletRequest origRequest, final HttpServletResponse origResponse,
                                    final FilterChain chain) throws IOException, ServletException {
        String interactionId = UUID.randomUUID().toString();
        origRequest.setAttribute("interactionId", interactionId);
        LOG.info("Incoming Request - interactionId={}", interactionId);

        if (FeatureEnum.isEnabled(FeatureEnum.DEBUG_LOG_REQUEST_HEADERS)) {
            var headerNames = origRequest.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                var headerName = headerNames.nextElement();
                var headerValue = origRequest.getHeader(headerName);
                LOG.info("{} - Header: {} = {}", FeatureEnum.DEBUG_LOG_REQUEST_HEADERS, interactionId, headerName, headerValue);
            }
        }
        chain.doFilter(origRequest, origResponse);
    }
}
