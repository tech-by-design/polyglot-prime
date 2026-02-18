package org.techbd.controller.http.hub.prime.api;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.conf.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.CoreAppConfig;
import org.techbd.service.csv.CsvService;
import org.techbd.util.FHIRUtil;
import org.techbd.util.fhir.CoreFHIRUtil;

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
  private final CoreAppConfig coreAppConfig;

  public CsvController(CsvService csvService, CoreAppConfig coreAppConfig) {
    this.csvService = csvService;
    this.coreAppConfig = coreAppConfig;
  }

  private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getOriginalFilename() == null
        || file.getOriginalFilename().trim().isEmpty()) {
      throw new IllegalArgumentException(" Uploaded file is missing or empty.");
    }

    String originalFilename = file.getOriginalFilename();
    if (!originalFilename.toLowerCase().endsWith(".zip")) {
      throw new IllegalArgumentException(" Uploaded file must have a .zip extension.");
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
      @Parameter(hidden = true, description = "Parameter to specify origin of the request.", required = false) @RequestParam(value = "origin", required = false,defaultValue = "HTTP") String origin,
      @Parameter(hidden = true, description = "Parameter to specify sftp session id.", required = false) @RequestParam(value = "sftp-session-id", required = false) String sftpSessionId,
      HttpServletRequest request,
      HttpServletResponse response)
      throws Exception {

    validateFile(file);
    validateTenantId(tenantId);
    Map <String,Object> requestDetailsMap = FHIRUtil.extractRequestDetails(request);
    Map<String, Object> headerParameters = CoreFHIRUtil.buildHeaderParametersMap(tenantId, null,
        null,
        null, null, null, null,
        null,null);   
    CoreFHIRUtil.buildRequestParametersMap(requestDetailsMap,null,
        null, null, null, null, request.getRequestURI());
    requestDetailsMap.put(Constants.MASTER_INTERACTION_ID, UUID.randomUUID().toString());
    requestDetailsMap.put(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME, Instant.now().toString());
    Map<String, Object> responseParameters = new HashMap<>();
    requestDetailsMap.putAll(headerParameters);
    final var result  = csvService.validateCsvFile(file, requestDetailsMap, responseParameters);
    CoreFHIRUtil.addCookieAndHeadersToResponse(response, responseParameters, requestDetailsMap);
    return result;
  }

  @PostMapping(value = { "/flatfile/csv/Bundle", "/flatfile/csv/Bundle/" }, consumes = {
      MediaType.MULTIPART_FORM_DATA_VALUE })
  @ResponseBody
  public ResponseEntity<Object> handleCsvUploadAndConversion(
      @Parameter(description = "ZIP file containing CSV data. Must not be null.", required = true) @RequestPart("file") @Nonnull MultipartFile file,
      @Parameter(description = "Parameter to specify the Tenant ID. This is a <b>mandatory</b> parameter.", required = true) @RequestHeader(value = Configuration.Servlet.HeaderName.Request.TENANT_ID, required = true) String tenantId,
      @Parameter(description = "Optional header to specify the Datalake API URL. If not specified, the default URL mentioned in the application configuration will be used.", required = false) @RequestHeader(value = Constants.DATALAKE_API_URL, required = false) String customDataLakeApi,
      @Parameter(description = "Optional header to specify the base FHIR URL. If provided, it will be used in the generated FHIR; otherwise, the default value will be used.", required = false) @RequestHeader(value = "X-TechBD-Base-FHIR-URL", required = false) String baseFHIRURL,
      @Parameter(hidden = true, description = "Parameter to specify origin of the request.", required = false) @RequestParam(value = "origin", required = false,defaultValue = "HTTP") String origin,
      @Parameter(hidden = true, description = "Parameter to specify sftp session id.", required = false) @RequestParam(value = "sftp-session-id", required = false) String sftpSessionId,
      @Parameter(hidden = true, description = "Optional parameter to decide whether response should be synchronous or asynchronous.", required = false) @RequestParam(value = "immediate", required = false,defaultValue = "true") boolean isSync,
      @Parameter(description = "Optional header to set validation severity level (`information`, `warning`, `error`, `fatal`).", required = false) @RequestHeader(value = "X-TechBD-Validation-Severity-Level", required = false) String validationSeverityLevel,
      HttpServletRequest request,
      HttpServletResponse response) throws Exception {
        
    validateFile(file);
    validateTenantId(tenantId);
    CoreFHIRUtil.validateBaseFHIRProfileUrl(coreAppConfig, baseFHIRURL);
    Map <String,Object> requestDetailsMap = FHIRUtil.extractRequestDetails(request);
    Map<String, Object> headerParameters = CoreFHIRUtil.buildHeaderParametersMap(tenantId, customDataLakeApi,
        null,
        null, validationSeverityLevel, null, null,
        null,null);    
    CoreFHIRUtil.buildRequestParametersMap(requestDetailsMap,null,
        null, null, null, null, request.getRequestURI());
    requestDetailsMap.put(Constants.MASTER_INTERACTION_ID, UUID.randomUUID().toString());
    requestDetailsMap.put(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME, Instant.now().toString());
    requestDetailsMap.put(Constants.IMMEDIATE, isSync);
    if (validationSeverityLevel != null) {
      requestDetailsMap.put(Constants.VALIDATION_SEVERITY_LEVEL, validationSeverityLevel);
    }
    headerParameters.put(Constants.BASE_FHIR_URL, baseFHIRURL);
    requestDetailsMap.put(Constants.DATALAKE_API_URL, customDataLakeApi);
    requestDetailsMap.putAll(headerParameters);
    Map<String, Object> responseParameters = new HashMap<>();
    List<Object> processedFiles = csvService.processZipFile(file, requestDetailsMap, responseParameters);
    CoreFHIRUtil.addCookieAndHeadersToResponse(response, responseParameters, requestDetailsMap);
    return ResponseEntity.ok(processedFiles);
  }
}
