package org.techbd.service.http;

import java.io.IOException;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@ConditionalOnProperty(name = "AUTH_PROVIDER", havingValue = "github")
public class GitHubRlsInitializationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubRlsInitializationFilter.class);

    private final DSLContext primaryDslContext;
    private DSLContext readerDslContext;

    public GitHubRlsInitializationFilter(
            @Qualifier("primaryDslContext") DSLContext primaryDslContext) {
        this.primaryDslContext = primaryDslContext;
    }

    @Autowired(required = false)
    public void setUdiReaderConfig(
            @Qualifier("secondaryDslContext") DSLContext readerDslContext) {
        this.readerDslContext = readerDslContext;
    }

    private DSLContext getDsl() {
        return readerDslContext != null ? readerDslContext : primaryDslContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null
                && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {

            try {
                getDsl().fetchValue(
                        "select techbd_udi_ingress.set_admin_context_for_github_login(?)",
                        "github");

                LOG.info("GitHub admin context initialized.");
            } catch (Exception e) {
                LOG.error("Failed to initialize GitHub admin context", e);
            }
        }

        filterChain.doFilter(request, response);
    }
}