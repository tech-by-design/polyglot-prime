package org.techbd.ingest.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.techbd.ingest.feature.FeatureEnum;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
//@WebFilter(urlPatterns = "/*")
public class InteractionsFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(InteractionsFilter.class);
   
    @Override
    protected void doFilterInternal(final HttpServletRequest origRequest, final HttpServletResponse origResponse,
                                    final FilterChain chain) throws IOException, ServletException {
               
        // Generate interactionId early
        String interactionId = UUID.randomUUID().toString();

        // Store in request attribute
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

        // ✅ Always call the next filter in the chain unless rejected
        chain.doFilter(origRequest, origResponse);
    }
}
