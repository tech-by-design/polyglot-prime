package org.techbd.controller.http.hub.prime.api;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.conf.Configuration;
import org.techbd.service.csv.CsvService;

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
public class CsvController {
    private final CsvService csvService;

    public CsvController(CsvService csvService) {
        this.csvService = csvService;
    }

    @PostMapping(value = {"/flatfile/csv", "/flatfile/csv/"}, consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @Operation(summary = "Endpoint to upload, process, and validate a CSV ZIP file",
               description = "Endpoint to upload a ZIP file containing CSVs for processing and validation.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "CSV files processed successfully", 
                         content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\n" +
                                 "  \"status\": \"Success\",\n" +
                                 "  \"message\": \"CSV files processed successfully.\"\n" +
                                 "}"))),
            @ApiResponse(responseCode = "400", description = "Validation Error: Missing or invalid parameter", 
                         content = @Content(mediaType = "application/json", examples = {
                                 @ExampleObject(value = "{\n" +
                                         "  \"status\": \"Error\",\n" +
                                         "  \"message\": \"Validation Error: Required request body is missing.\"\n" +
                                         "}"),
                                 @ExampleObject(value = "{\n" +
                                         "  \"status\": \"Error\",\n" +
                                         "  \"message\": \"Validation Error: Required request header 'X-Tenant-ID' is missing.\"\n" +
                                         "}")
                         })),
            @ApiResponse(responseCode = "500", description = "An unexpected system error occurred", 
                         content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\n" +
                                 "  \"status\": \"Error\",\n" +
                                 "  \"message\": \"An unexpected system error occurred.\"\n" +
                                 "}")))
    })
    @ResponseBody
    public ResponseEntity<Object> handleCsvUpload(
            @Parameter(description = "ZIP file containing CSV data. Must not be null.", required = true)
            @RequestPart("file") @Nonnull MultipartFile file,
            @Parameter(description = "Parameter to specify the Tenant ID. This is a <b>mandatory</b> parameter.", required = true) @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID must be provided");
        }

        try {
            csvService.processZipFile(file);
            return ResponseEntity.ok().body("{ \"status\": \"Success\", \"message\": \"CSV files processed successfully.\" }");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{ \"status\": \"Error\", \"message\": \"Error processing CSV files: " + e.getMessage() + "\" }");
        }
    }

}
