package org.techbd.service.http.hub.prime.api;

import java.util.Map;

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
import org.techbd.service.http.InteractionsFilter;
import org.techbd.service.http.hub.prime.AppConfig;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "Tech by Design Hub Expectations Endpoints",
        description = "Tech by Design Hub Expectations Endpoints")
public class ExpectController {

    private static final Logger LOG = LoggerFactory.getLogger(ExpectController.class.getName());

    public ExpectController(@SuppressWarnings("PMD.UnusedFormalParameter") final Environment environment,
            @SuppressWarnings("PMD.UnusedFormalParameter") final AppConfig appConfig) {
    }

    @SuppressWarnings("unchecked")
    @PostMapping(value = {"/api/expect/fhir/bundle"}, consumes = {MediaType.APPLICATION_JSON_VALUE,
        AppConfig.Servlet.FHIR_CONTENT_TYPE_HEADER_VALUE})
    @Operation(summary = "This endpoint is designed to store data from the SCN, which is also expected to be sent by the QEs. The data will be tracked using the 'id' of the payload JSON, with the assumption that each 'id' is unique and will not be reused.",
            description = "API endpoint is designed to store data from the SCN")
    @ResponseBody
    public Object expectFhirBundle(
            @Parameter(description = "Payload for the API. This <b>must not</b> be <code>null</code>.", required = true)
            final @RequestBody @Nonnull String payload,
            @Parameter(description = "Parameter to specify the Tenant ID. This is a <b>mandatory</b> parameter.", required = true)
            @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
            @Parameter(description = "Optional parameter to decide whether the request is to be included in the outcome.", required = false)
            @RequestParam(value = "include-request-in-outcome", required = false) boolean includeRequestInOutcome,
            final HttpServletRequest request) {

        final var interactionId = InteractionsFilter.getActiveRequestEnc(request).requestId().toString();
        var objectMapper = new ObjectMapper();
        Map<String, Object> payloadMap;

        try {
            payloadMap = objectMapper.readValue(payload, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse payload JSON", e);
        }
        payloadMap.put("interactionId", interactionId);
        return payloadMap;
    }
}
