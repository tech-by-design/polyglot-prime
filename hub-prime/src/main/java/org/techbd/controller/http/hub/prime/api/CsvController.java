package org.techbd.controller.http.hub.prime.api;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.conf.Configuration;
import org.techbd.javapythonjunit.CsvValidationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;

@Controller
@Tag(name = "Tech by Design Hub HL7 Endpoints", description = "Tech by Design Hub HL7 Endpoints")
public class CsvController {

    private final CsvValidationService csvService;

    public CsvController(CsvValidationService csvService) {
        this.csvService = csvService;
    }

    @PostMapping(value = "/flatfile/csv/Bundle/$validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Endpoint to upload, process, and validate a CSV ZIP file", description = "Upload a ZIP file containing CSVs for processing and validation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSV files processed successfully", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {
                      "status": "Success",
                      "message": "CSV files processed successfully."
                    }
                    """))),
            @ApiResponse(responseCode = "400", description = "Validation Error: Missing or invalid parameter", content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(value = """
                            {
                              "status": "Error",
                              "message": "Validation Error: Required request body is missing."
                            }
                            """),
                    @ExampleObject(value = """
                            {
                              "status": "Error",
                              "message": "Validation Error: Required request header 'X-Tenant-ID' is missing."
                            }
                            """)
            })),
            @ApiResponse(responseCode = "500", description = "An unexpected system error occurred", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {
                      "status": "Error",
                      "message": "An unexpected system error occurred."
                    }
                    """)))
    })
    @ResponseBody
    public ResponseEntity<Object> handleCsvUpload(
            @Parameter(description = "ZIP file containing CSV data. Must not be null.", required = true) @RequestPart("file") @Nonnull MultipartFile file,
            @Parameter(description = "Tenant ID, a mandatory parameter.", required = true) @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID) String tenantId) {
        // Validate Tenant ID
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "Error",
                    "message", "Validation Error: Tenant ID must be provided."));
        }

        try {
            // Validate CSV and get results
            Map<String, Object> validationResult = csvService.validateCsvGroup();
            Map<String, Object> responseBody = Map.of(
                    "status", "Success",
                    "message", "CSV files processed successfully.",
                    "validationResults", validationResult);
            return ResponseEntity.ok(responseBody);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "Error",
                    "message", "Validation Error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "Error",
                    "message", "An unexpected system error occurred: " + e.getMessage()));
        }
    }
}
