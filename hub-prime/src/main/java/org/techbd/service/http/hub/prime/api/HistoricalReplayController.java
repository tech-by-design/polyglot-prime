package org.techbd.service.http.hub.prime.api;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import io.opentelemetry.api.trace.Tracer;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.techbd.service.fhir.FHIRService;
import org.techbd.service.http.hub.CustomRequestWrapper;
import org.techbd.util.FHIRUtil;
import org.techbd.util.fhir.CoreFHIRUtil;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.techbd.conf.Configuration;
import org.techbd.config.Constants;
import io.micrometer.common.util.StringUtils;
import io.opentelemetry.api.trace.Span;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.Nonnull;

@Controller
@Tag(name = "Historical Replay Endpoints", description = "Historical Replay FHIR Endpoints")
public class HistoricalReplayController {

    private static final Logger LOG = LoggerFactory.getLogger(HistoricalReplayController.class);

    private final FHIRService fhirService;
    private final Tracer tracer;

    public HistoricalReplayController(Tracer tracer, FHIRService fhirService) {
        this.tracer = tracer;
        this.fhirService = fhirService;
    }

    @PostMapping(value = { "/historical-replay/Bundle", "/historical-replay/Bundle/" }, consumes = {
            MediaType.APPLICATION_JSON_VALUE, Constants.FHIR_CONTENT_TYPE_HEADER_VALUE })
    @Operation(summary = "Historical replay endpoint: validates FHIR bundles against the current IG, with optional Data Ledger and OperationOutcome size controls.", description = """
            Accepts FHIR bundles and validates them against the current version of the IG, same as <code>/Bundle</code>.
            <ul>
                <li><code>dataLedger</code>: defaults to <code>true</code> (Data Ledger calls proceed as-is). Set to <code>false</code> to prevent calls to the NYeC Data Ledger.</li>
                <li><code>ooSize</code>: defaults to <code>full</code> (all errors, warnings, and info messages included in the OperationOutcome).
                    Use <code>lite</code> to include only error/fatal issues, excluding warnings and info.
                    Use <code>none</code> to receive a plain HTTP 200 with no OperationOutcome body.</li>
            </ul>
            """)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request processed successfully."),
            @ApiResponse(responseCode = "400", description = "Validation Error: Missing or invalid parameter."),
            @ApiResponse(responseCode = "500", description = "An unexpected system error occurred.")
    })
    @ResponseBody
    public Object historicalReplayBundle(
            @Parameter(description = "FHIR Bundle payload.", required = true) final @RequestBody @Nonnull String payload,
            @Parameter(description = "Tenant ID.", required = true) @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
            @Parameter(description = "Set to <code>false</code> to skip NYeC Data Ledger calls. Defaults to <code>true</code> (calls proceed as-is).", required = false) @RequestParam(value = "dataLedger", required = false, defaultValue = "true") String dataLedger,
            @Parameter(description = "OperationOutcome size: <code>full</code> (default, all issues), <code>lite</code> (error/fatal only), <code>none</code> (HTTP 200 with no OperationOutcome — returns only <code>{ \\\"interactionId\\\": ... }</code> for tracking).", required = false) @RequestParam(value = "ooSize", required = false, defaultValue = "full") String ooSize,
            @Parameter(description = "Optional correlation ID (UUID).", required = false) @RequestHeader(value = "X-Correlation-ID", required = false) String coRrelationId,
            @Parameter(description = "Optional header to specify IG version.", required = false) @RequestHeader(value = "X-SHIN-NY-IG-Version", required = false) String requestedIgVersion,
            HttpServletRequest request, HttpServletResponse response) throws SQLException, IOException {
        Span span = tracer.spanBuilder("HistoricalReplayController.historicalReplayBundle").startSpan();
        try {
            if (tenantId == null || tenantId.trim().isEmpty()) {
                LOG.error("HistoricalReplayController:HistoricalReplay:: Tenant ID is missing or empty");
                throw new IllegalArgumentException("Tenant ID must be provided");
            }
            if (StringUtils.isNotEmpty(coRrelationId)) {
                try {
                    UUID.fromString(coRrelationId);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("X-Correlation-ID should be a valid UUID");
                }
            }
            if (StringUtils.isNotEmpty(dataLedger)
                    && !"true".equalsIgnoreCase(dataLedger.trim())
                    && !"false".equalsIgnoreCase(dataLedger.trim())) {
                throw new IllegalArgumentException("dataLedger must be either 'true' or 'false'");
            }
            final String normalizedOoSize = StringUtils.isNotEmpty(ooSize) ? ooSize.trim() : "full";
            if (!"full".equalsIgnoreCase(normalizedOoSize) && !"lite".equalsIgnoreCase(normalizedOoSize)
                    && !"none".equalsIgnoreCase(normalizedOoSize)) {
                throw new IllegalArgumentException("ooSize must be one of 'full', 'lite', or 'none'");
            }

            final var provenance = "%s.historicalReplayBundle".formatted(HistoricalReplayController.class.getName());
            request = new CustomRequestWrapper(request, payload);
            Map<String, Object> headers = CoreFHIRUtil.buildHeaderParametersMap(tenantId, null,
                    null, null, null, coRrelationId, provenance, requestedIgVersion);
            Map<String, Object> requestDetailsMap = FHIRUtil.extractRequestDetails(request);
            CoreFHIRUtil.buildRequestParametersMap(requestDetailsMap, null,
                    null, "FHIR", null, null, request.getRequestURI());
            requestDetailsMap.put(Constants.INTERACTION_ID, UUID.randomUUID().toString());
            requestDetailsMap.put(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME, Instant.now().toString());
            requestDetailsMap.putAll(headers);
            // Stash the historical-replay params so FHIRService can gate Data Ledger
            // calls and the controller can shape the OperationOutcome response below.
            requestDetailsMap.put(Constants.HISTORICAL_REPLAY_DATA_LEDGER, dataLedger);
            requestDetailsMap.put(Constants.HISTORICAL_REPLAY_OO_SIZE, normalizedOoSize);

            LOG.info(" Historical Replay START | interactionId={} | tenantId={} | dataLedger={} | ooSize={}",
                    requestDetailsMap.get(Constants.INTERACTION_ID), tenantId, dataLedger, normalizedOoSize);

            Map<String, Object> responseParameters = new HashMap<>();
            final var result = fhirService.processBundle(payload, requestDetailsMap, responseParameters);

            LOG.info(" Historical Replay processBundle COMPLETED | interactionId={}",
                    requestDetailsMap.get(Constants.INTERACTION_ID));

            CoreFHIRUtil.addCookieAndHeadersToResponse(response, responseParameters, requestDetailsMap);

            LOG.info(" Historical Replay applying OperationOutcome filter | interactionId={} | ooSize={}",
                    requestDetailsMap.get(Constants.INTERACTION_ID), normalizedOoSize);

            final var filtered = fhirService.applyOoSizeFilter(result, normalizedOoSize);
            if (filtered == null) {
                String interactionId = (String) requestDetailsMap.get(Constants.INTERACTION_ID);
                LOG.info(" Historical Replay returning HTTP 200 with EMPTY BODY | interactionId={}",
                        interactionId);
                return ResponseEntity.ok(
                        Map.of("interactionId", interactionId));
            }

            LOG.info(" Historical Replay returning OperationOutcome | interactionId={} | ooSize={}",
                    requestDetailsMap.get(Constants.INTERACTION_ID), normalizedOoSize);

            return filtered;
        } finally {
            span.end();
        }
    }

}
