package org.techbd.service.http.hub.prime.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.techbd.conf.Configuration;
import org.techbd.service.http.hub.prime.AppConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "TechBD Hub Expectations Endpoints")
public class ExpectController {

    private final AppConfig appConfig;
    private static final Logger LOG = LoggerFactory.getLogger(ExpectController.class.getName());

    public ExpectController(final Environment environment, final AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @PostMapping(value = {"/api/expect/register"}, consumes = {MediaType.APPLICATION_JSON_VALUE,
        AppConfig.Servlet.FHIR_CONTENT_TYPE_HEADER_VALUE})
    @Operation(summary = "TODO: prper documentation here")
    @ResponseBody
    public Object validateBundle(final @RequestBody @Nonnull String payload,
            @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
            @RequestParam(value = "include-request-in-outcome", required = false) boolean includeRequestInOutcome,
            final HttpServletRequest request) {

        LOG.info("Inside the expectation register end point");
        //TODO: create hub_expectation table and satellite table and save data here
        return payload;
    }
}
