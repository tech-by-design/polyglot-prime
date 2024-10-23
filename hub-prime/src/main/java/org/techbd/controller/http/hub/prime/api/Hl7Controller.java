package org.techbd.controller.http.hub.prime.api;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.techbd.conf.Configuration;
import org.techbd.service.http.hub.CustomRequestWrapper;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.service.http.hub.prime.api.Hl7Service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@Tag(name = "Tech by Design Hub HL7 Endpoints", description = "Tech by Design Hub HL7 Endpoints")
public class Hl7Controller {

        private static final Logger LOG = LoggerFactory.getLogger(Hl7Controller.class.getName());
        private final Hl7Service hl7Service;

        public Hl7Controller(final Hl7Service hl7Service) {
                this.hl7Service = hl7Service;
        }

        @PostMapping(value = { "/Hl7/v2", "/Hl7/v2/" }, consumes = { MediaType.TEXT_PLAIN_VALUE })
        @Operation(summary = "Endpoint to to validate, store, and then forward a HL7 v2 payload to SHIN-NY.", description = "Endpoint to to validate, store, and then forward a HL7 v2 payload to SHIN-NY.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Request processed successfully", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\n"
                                        +
                                        "  \"OperationOutcome\": {\n" +
                                        "    \"validationResults\": [\n" +
                                        "      {\n" +
                                        "        \"operationOutcome\": {\n" +
                                        "          \"resourceType\": \"OperationOutcome\",\n" +
                                        "          \"issue\": [\n" +
                                        "            {\n" +
                                        "              \"severity\": \"error\",\n" +
                                        "              \"diagnostics\": \"Error Message\",\n" +
                                        "              \"location\": [\n" +
                                        "                \"Bundle.entry[0].resource/*Patient/PatientExample*/.extension[0].extension[0].value.ofType(Coding)\",\n"
                                        +
                                        "                \"Line[1] Col[5190]\"\n" +
                                        "              ]\n" +
                                        "            }\n" +
                                        "          ]\n" +
                                        "        }\n" +
                                        "      }\n" +
                                        "    ],\n" +
                                        "    \"techByDesignDisposition\": [\n" +
                                        "      {\n" +
                                        "        \"action\": \"reject\",\n" +
                                        "        \"actionPayload\": {\n" +
                                        "          \"message\": \"reject message\",\n" +
                                        "          \"description\": \"rule name\"\n" +
                                        "        }\n" +
                                        "      }\n" +
                                        "    ],\n" +
                                        "    \"resourceType\": \"OperationOutcome\"\n" +
                                        "  }\n" +
                                        "}"))),

                        @ApiResponse(responseCode = "400", description = "Validation Error: Missing or invalid parameter", content = @Content(mediaType = "application/json", examples = {
                                        @ExampleObject(value = "{\n" +
                                                        "  \"status\": \"Error\",\n" +
                                                        "  \"message\": \"Validation Error: Required request body is missing.\"\n"
                                                        +
                                                        "}"),
                                        @ExampleObject(value = "{\n" +
                                                        "  \"status\": \"Error\",\n" +
                                                        "  \"message\": \"Validation Error: Required request header 'X-TechBD-Tenant-ID' for method parameter type String is not present.\"\n"
                                                        +
                                                        "}")
                        })),
                        @ApiResponse(responseCode = "500", description = "An unexpected system error occurred", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\n"
                                        +
                                        "  \"status\": \"Error\",\n" +
                                        "  \"message\": \"An unexpected system error occurred.\"\n" +
                                        "}")))
        })
        @ResponseBody
        @Async
        public Object validateHl7MessageAndForward(
                        @Parameter(description = "Payload for the API. This <b>must not</b> be <code>null</code>.", required = true) final @RequestBody @Nonnull String payload,
                        @Parameter(description = "Parameter to specify the Tenant ID. This is a <b>mandatory</b> parameter.", required = true) @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
                        @Parameter(description = "Boolean parameter to enable logging of the payload. Default value is <code>false</code>.", required = false) @RequestParam(value = "logPayloadEnabled", defaultValue = "false") boolean logPayloadEnabled,
                        HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
                if (tenantId == null || tenantId.trim().isEmpty()) {
                        LOG.error("FHIRController:Bundle Validate:: Tenant ID is missing or empty");
                        throw new IllegalArgumentException("Tenant ID must be provided");
                }
                request = new CustomRequestWrapper(request, payload);
                return hl7Service.processHl7Message(payload, tenantId, request, response, logPayloadEnabled);
        }
}
