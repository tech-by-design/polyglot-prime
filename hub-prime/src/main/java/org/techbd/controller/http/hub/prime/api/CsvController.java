package org.techbd.controller.http.hub.prime.api;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.conf.Configuration;
import org.techbd.service.CsvService;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@Tag(name = "Tech by Design Hub CSV Endpoints", description = "Tech by Design Hub CSV Endpoints")
public class CsvController {

  private static final Logger log = LoggerFactory.getLogger(CsvController.class);
  private final CsvService csvService;

  public CsvController(CsvService csvService) {
    this.csvService = csvService;
  }

  private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getOriginalFilename() == null
        || file.getOriginalFilename().trim().isEmpty()) {
      throw new IllegalArgumentException("Validation Error: Uploaded file is missing or empty.");
    }

    String originalFilename = file.getOriginalFilename();
    if (!originalFilename.toLowerCase().endsWith(".zip")) {
      throw new IllegalArgumentException("Validation Error: Uploaded file must have a .zip extension.");
    }
  }

  private void validateTenantId(String tenantId) {
    if (tenantId == null || tenantId.trim().isEmpty()) {
      throw new IllegalArgumentException("Tenant ID must be provided.");
    }
  }

  @PostMapping(value = { "/flatfile/csv/Bundle/$validate",
      "/flatfile/csv/Bundle/$validate/" }, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseBody
  public Object handleCsvUpload(
      @Parameter(description = "ZIP file containing CSV data. Must not be null.", required = true) @RequestPart("file") @Nonnull MultipartFile file,
      @Parameter(description = "Tenant ID, a mandatory parameter.", required = true) @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID) String tenantId,
      @Parameter(description = "Parameter to specify origin of the request.", required = false) @RequestParam(value = "origin", required = false, defaultValue = "HTTP") String origin,
      @Parameter(description = "Parameter to specify sftp session id.", required = false) @RequestParam(value = "sftp-session-id", required = false) String sftpSessionId,
      HttpServletRequest request,
      HttpServletResponse response)
      throws Exception {

    try {
      validateFile(file);
      validateTenantId(tenantId);
      return csvService.validateCsvFile(file, request, response, tenantId, origin, sftpSessionId);
    } catch (IllegalArgumentException e) {
      log.error("Validation error: {}", e.getMessage());
      return ResponseEntity.badRequest().body("{\"status\":\"Error\", \"message\":\"" + e.getMessage() + "\"}");
}
  }

  @PostMapping(value = { "/flatfile/csv/Bundle",
      "/flatfile/csv/Bundle/" }, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseBody
  @Async
  public List<Object> handleCsvUploadAndConversion(
      @Parameter(description = "ZIP file containing CSV data. Must not be null.", required = true) @RequestPart("file") @Nonnull MultipartFile file,
      @Parameter(description = "Parameter to specify the Tenant ID. This is a <b>mandatory</b> parameter.", required = true) @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
      @Parameter(description = "Parameter to specify origin of the request.", required = false) @RequestParam(value = "origin", required = false, defaultValue = "HTTP") String origin,
      @Parameter(description = "Parameter to specify sftp session id.", required = false) @RequestParam(value = "sftp-session-id", required = false) String sftpSessionId,
      @Parameter(description = "Optional parameter to specify if transformed validation issue needs to be appended.", required = false) @RequestParam(value = "append-validation-issue", required = false, defaultValue = "true")  boolean appendValidationIssue,
      HttpServletRequest request,
      HttpServletResponse response) throws Exception {

    if (tenantId == null || tenantId.trim().isEmpty()) {
      throw new IllegalArgumentException("Tenant ID must be provided");
    }
   
    return csvService.processZipFile(file, request, response, tenantId,origin,sftpSessionId,appendValidationIssue);
  }
}
