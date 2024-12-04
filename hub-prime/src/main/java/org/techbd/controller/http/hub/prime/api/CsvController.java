package org.techbd.controller.http.hub.prime.api;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.conf.Configuration;
import org.techbd.service.CsvValidationService;
import org.techbd.service.http.hub.prime.AppConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;

@Controller
@Tag(name = "Tech by Design Hub CSV Endpoints", description = "Tech by Design Hub CSV Endpoints")
public class CsvController {
    private static final Logger log = LoggerFactory.getLogger(CsvController.class);
    private final AppConfig config;
    private final CsvValidationService csvService;

     public CsvController(
            CsvValidationService csvService,AppConfig config           
             ) {
        this.csvService = csvService;
        this.config = config;
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
            @Parameter(description = "ZIP file containing CSV data. Must not be null.", required = true) 
            @RequestPart("file") @Nonnull MultipartFile file,
            @Parameter(description = "Tenant ID, a mandatory parameter.", required = true) 
            @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID) String tenantId) throws Exception{
        if (tenantId == null || tenantId.trim().isEmpty()) {
                log.error("CsvController: handleCsvUpload:: Tenant ID is missing or empty");
                throw new IllegalArgumentException("Tenant ID must be provided");
            }

        try {
            // Generate a unique filename
            String originalFilename = file.getOriginalFilename();
            String uniqueFilename = UUID.randomUUID() + "_" + (originalFilename != null ? originalFilename : "upload.zip");
            Path destinationPath = Path.of(config.getCsv().validation().inboundPath(), uniqueFilename);

            // Ensure the inbound folder exists
            Files.createDirectories(destinationPath.getParent());

            // Save the uploaded file to the inbound folder
            Files.copy(file.getInputStream(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("File saved to: {}", destinationPath);

            // Trigger CSV processing and validation
            csvService.executeZipProcessing();

            // Prepare response
            Map<String, Object> responseBody = Map.of(
                    "status", "Success",
                    "message", "CSV files processed successfully.",
                    "filename", uniqueFilename);
            
            return ResponseEntity.ok(responseBody);

        } catch (IllegalArgumentException e) {
            log.error("Validation Error", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "Error",
                    "message", "Validation Error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected system error", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "Error",
                    "message", "An unexpected system error occurred: " + e.getMessage()));
        }
    }
}
