package org.techbd.orchestrate.csv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.vfs2.FileObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.conf.Configuration;
import org.techbd.service.CsvService;
import org.techbd.service.VfsCoreService;
import org.techbd.service.http.InteractionsFilter;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.oauth2.sdk.util.CollectionUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import lib.aide.vfs.VfsIngressConsumer;

/**
 * The {@code OrchestrationEngine} class is responsible for managing and
 * orchestrating CSV validation sessions.
 */
@Component
public class CsvOrchestrationEngine {
    private final List<OrchestrationSession> sessions;
    private final AppConfig appConfig;
    private final VfsCoreService vfsCoreService;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;
    private static final Logger log = LoggerFactory.getLogger(CsvOrchestrationEngine.class);
    private static final Pattern FILE_PATTERN = Pattern.compile(
            "(DEMOGRAPHIC_DATA|QE_ADMIN_DATA|SCREENING)_(.+)");

    public CsvOrchestrationEngine(final AppConfig appConfig, final VfsCoreService vfsCoreService,
            final UdiPrimeJpaConfig udiPrimeJpaConfig) {
        this.sessions = new ArrayList<>();
        this.appConfig = appConfig;
        this.vfsCoreService = vfsCoreService;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
    }

    public List<OrchestrationSession> getSessions() {
        return Collections.unmodifiableList(sessions);
    }

    public synchronized void orchestrate(@NotNull final OrchestrationSession... sessions) {
        for (final OrchestrationSession session : sessions) {
            this.sessions.add(session);
            session.validate();
        }
    }

    public void clear(@NotNull final OrchestrationSession... sessionsToRemove) {
        if (sessionsToRemove != null && CollectionUtils.isNotEmpty(sessions)) {
            synchronized (this) {
                final Set<String> sessionIdsToRemove = Arrays.stream(sessionsToRemove)
                        .map(OrchestrationSession::getSessionId)
                        .collect(Collectors.toSet());
                final Iterator<OrchestrationSession> iterator = this.sessions.iterator();
                while (iterator.hasNext()) {
                    final OrchestrationSession session = iterator.next();
                    if (sessionIdsToRemove.contains(session.getSessionId())) {
                        iterator.remove();
                    }
                }
            }
        }
    }

    public CsvOrchestrationEngine.OrchestrationSessionBuilder session() {
        return new OrchestrationSessionBuilder();
    }

    /**
     * Builder class for creating instances of {@link OrchestrationSession}.
     */
    public class OrchestrationSessionBuilder {
        private String sessionId;
        private String tenantId;
        private Device device;
        private MultipartFile file;
        private String masterInteractionId;
        private HttpServletRequest request;

        public OrchestrationSessionBuilder withSessionId(final String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public OrchestrationSessionBuilder withTenantId(final String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public OrchestrationSessionBuilder withDevice(final Device device) {
            this.device = device;
            return this;
        }

        public OrchestrationSessionBuilder withFile(final MultipartFile file) {
            this.file = file;
            return this;
        }

        public OrchestrationSessionBuilder withMasterInteractionId(final String masterInteractionId) {
            this.masterInteractionId = masterInteractionId;
            return this;
        }

        public OrchestrationSessionBuilder withRequest(final HttpServletRequest request) {
            this.request = request;
            return this;
        }

        public OrchestrationSession build() {
            if (sessionId == null) {
                sessionId = UUID.randomUUID().toString();
            }
            if (device == null) {
                device = Device.INSTANCE;
            }
            if (file == null) {
                throw new IllegalArgumentException("File must not be null");
            }
            return new OrchestrationSession(sessionId, tenantId, device, file, masterInteractionId, request);
        }
    }

    public record Device(String deviceId, String deviceName) {

        public static final Device INSTANCE = createDefault();

        public static Device createDefault() {
            try {
                final InetAddress localHost = InetAddress.getLocalHost();
                final String ipAddress = localHost.getHostAddress();
                final String hostName = localHost.getHostName();
                return new Device(ipAddress, hostName);
            } catch (final UnknownHostException e) {
                return new Device("Unable to retrieve the localhost information", e.toString());
            }
        }
    }

    public class OrchestrationSession {
        private final String sessionId;
        private final String masterInteractionId;
        private final Device device;
        private final MultipartFile file;
        private Map<String, Object> validationResults;
        private String tenantId;

        HttpServletRequest request;

        public OrchestrationSession(final String sessionId, final String tenantId, final Device device,
                final MultipartFile file,
                final String masterInteractionId,
                final HttpServletRequest request) {
            this.sessionId = sessionId;
            this.tenantId = tenantId;
            this.device = device;
            this.file = file;
            this.validationResults = new HashMap<>();
            this.masterInteractionId = masterInteractionId;
            this.request = request;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getTenantId() {
            return sessionId;
        }

        public Device getDevice() {
            return device;
        }

        public MultipartFile getFile() {
            return file;
        }

        public String getMasterInteractionId() {
            return masterInteractionId;
        }

        public Map<String, Object> getValidationResults() {
            return validationResults;
        }

        public void validate() {
            log.info("CsvOrchestrationEngine : validate - file : {} BEGIN for interaction id : {}",
                    file.getOriginalFilename(), masterInteractionId);
            try {
                final Instant intiatedAt = Instant.now();
                final String originalFilename = file.getOriginalFilename();
                final String uniqueFilename = masterInteractionId + "_"
                        + (originalFilename != null ? originalFilename : "upload.zip");
                final Path destinationPath = Path.of(appConfig.getCsv().validation().inboundPath(), uniqueFilename);
                Files.createDirectories(destinationPath.getParent());

                // Save the uploaded file to the inbound folder
                Files.copy(file.getInputStream(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("File saved to: {}", destinationPath);

                // Trigger CSV processing and validation
                this.validationResults = processScreenings(masterInteractionId, intiatedAt, originalFilename, tenantId);
                saveValidationResults(this.validationResults, tenantId);
            } catch (final IllegalArgumentException e) {
                log.error("Validation Error", e);
                this.validationResults = Map.of(
                        "status", "Error",
                        "message", "Validation Error: " + e.getMessage());
            } catch (final Exception e) {
                log.error("Unexpected system error", e);
                this.validationResults = Map.of(
                        "status", "Error",
                        "message", "An unexpected system error occurred: " + e.getMessage());
            }
        }

        private void saveScreeningGroup(final HttpServletRequest request,
                final MultipartFile file, final Map<FileType, FileDetail> fileMap, final String tenantId) {
            final var interactionId = getBundleInteractionId(request);
            log.info("REGISTER State NONE : BEGIN for inteaction id  : {} tenant id : {}",
                    interactionId, tenantId);
            final var dslContext = udiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            final var forwardedAt = OffsetDateTime.now();
            final var initRIHR = new RegisterInteractionHttpRequest();
            try {
                initRIHR.setInteractionId(interactionId);
                initRIHR.setInteractionKey(request.getRequestURI());
                initRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                        Map.of("nature", "Original Flat File CSV", "tenant_id",
                                tenantId)));
                initRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                initRIHR.setCsvZipFileName(file.getOriginalFilename());
                // initRIHR.set
                initRIHR.setCsvDemographicDataFileName(fileMap.get(FileType.DEMOGRAPHIC_DATA).filename);
                initRIHR.setCsvDemographicDataPayloadText(fileMap.get(FileType.DEMOGRAPHIC_DATA).content);
                initRIHR.setCsvQeAdminDataFileName(fileMap.get(FileType.QE_ADMIN_DATA).filename);
                initRIHR.setCsvQeAdminDataPayloadText(fileMap.get(FileType.QE_ADMIN_DATA).content);

                initRIHR.setCsvScreeningObservationDataFileName(
                        fileMap.get(FileType.SCREENING_OBSERVATION_DATA).filename);
                initRIHR.setCsvScreeningObservationDataPayloadText(
                        fileMap.get(FileType.SCREENING_OBSERVATION_DATA).content);
                // To-Do : setCsvScreeningResourcesDataFileName adeded with new file
                // SCREENING_PROFILE_DATA
                initRIHR.setCsvScreeningResourcesDataFileName(fileMap.get(FileType.SCREENING_PROFILE_DATA).filename);
                initRIHR.setCsvScreeningResourcesDataPayloadText(
                        fileMap.get(FileType.SCREENING_PROFILE_DATA).content);
                initRIHR.setCreatedAt(forwardedAt);
                initRIHR.setCreatedBy(CsvService.class.getName());
                initRIHR.setToState("CSV_ACCEPT");
                final var provenance = "%s.saveScreeningGroup"
                        .formatted(CsvService.class.getName());
                initRIHR.setProvenance(provenance);
                initRIHR.setCsvGroupId(interactionId);
                final var start = Instant.now();
                final var execResult = initRIHR.execute(jooqCfg);
                final var end = Instant.now();
                log.info(
                        "REGISTER State NONE : END for interaction id : {} tenant id : {} .Time taken : {} milliseconds"
                                + execResult,
                        interactionId, tenantId,
                        Duration.between(start, end).toMillis());
            } catch (final Exception e) {
                log.error("ERROR:: REGISTER State NONE CALL for interaction id : {} tenant id : {}"
                        + initRIHR.getName() + " initRIHR error", interactionId,
                        tenantId,
                        e);
            }
        }

        /**
         * Checks if the "valid" field in "validationResults.report" is true.
         *
         * @param jsonMap The input JSON represented as a Map<String, Object>.
         * @return true if "valid" is true; otherwise, false.
         */
        public static boolean isValid(final Map<String, Object> csvValidationResult) {
            if (csvValidationResult == null || !csvValidationResult.containsKey("validationResults")) {
                return false;
            }

            final Object validationResults = csvValidationResult.get("validationResults");
            if (!(validationResults instanceof Map<?, ?>)) {
                return false;
            }

            @SuppressWarnings("unchecked")
            final Map<String, Object> validationResultsMap = (Map<String, Object>) validationResults;
            final Object report = validationResultsMap.get("report");

            if (!(report instanceof Map<?, ?>)) {
                return false;
            }

            @SuppressWarnings("unchecked")
            final Map<String, Object> reportMap = (Map<String, Object>) report;
            final Object valid = reportMap.get("valid");

            return Boolean.TRUE.equals(valid);
        }

        private void saveValidationResults(final Map<String, Object> validationResults,
                final String tenantId) {
            final var interactionId = getBundleInteractionId(request);
            log.info("REGISTER State VALIDATION : BEGIN for inteaction id  : {} tenant id : {}",
                    interactionId, tenantId);
            final var dslContext = udiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            final var createdAt = OffsetDateTime.now();
            final var initRIHR = new RegisterInteractionHttpRequest();
            try {
                initRIHR.setInteractionId(interactionId);
                initRIHR.setInteractionKey(request.getRequestURI());
                initRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                        Map.of("nature", "CSV Validation Result", "tenant_id",
                                tenantId)));
                initRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                initRIHR.setCreatedAt(createdAt);
                initRIHR.setCreatedBy(CsvService.class.getName());
                initRIHR.setPayload((JsonNode) Configuration.objectMapper.valueToTree(validationResults));
                initRIHR.setFromState("CSV_ACCEPT");
                if (isValid(validationResults)) {
                    initRIHR.setToState("VALIDATION_SUCCESS");
                } else {
                    initRIHR.setToState("VALIDATION_FAILED");
                }
                // initRIHR.setValidation
                final var provenance = "%s.saveValidationResults"
                        .formatted(CsvService.class.getName());
                initRIHR.setProvenance(provenance);
                initRIHR.setCsvGroupId(interactionId);
                final var start = Instant.now();
                final var execResult = initRIHR.execute(jooqCfg);
                final var end = Instant.now();
                log.info(
                        "REGISTER State VALIDATION : END for interaction id : {} tenant id : {} .Time taken : {} milliseconds"
                                + execResult,
                        interactionId, tenantId,
                        Duration.between(start, end).toMillis());
            } catch (final Exception e) {
                log.error("ERROR:: REGISTER State VALIDATION CALL for interaction id : {} tenant id : {}"
                        + initRIHR.getName() + " initRIHR error", interactionId,
                        tenantId,
                        e);
            }
        }

        private static Map<String, Object> createOperationOutcome(final String interactionId,
                final String validationResults,
                final List<String> fileNames, final HttpServletRequest request, final long zipFileSize,
                final Instant initiatedAt, final Instant completedAt, final String originalFileName) throws Exception {
            // Populate provenance with additional details like user agent, device, and URI
            final Map<String, Object> provenance = populateProvenance(interactionId, fileNames, initiatedAt,
                    completedAt, originalFileName);

            // Get user agent and device details
            final String userAgent = request.getHeader("User-Agent");
            final Device device = Device.INSTANCE; // Assuming Device class returns device details

            // Populate the outer map with additional details
            return Map.of(
                    "resourceType", "OperationOutcome",
                    "bundleSessionId", interactionId,
                    "validationResults", Configuration.objectMapper.readTree(validationResults),
                    "provenance", provenance,
                    "requestUri", request.getRequestURI(),
                    "zipFileSize", zipFileSize,
                    "userAgent", userAgent,
                    "device", Map.of(
                            "deviceId", device.deviceId(),
                            "deviceName", device.deviceName()
                    // "initiatedAt", ZonedDateTime.now().minusMinutes(10).toString(), // Example
                    // initiated time
                    // "completedAt", ZonedDateTime.now().toString() // Example completed time
                    ));
        }

        private static Map<String, Object> populateProvenance(final String interactionId, final List<String> fileNames,
                final Instant initiatedAt, final Instant completedAt, final String originalFileName) {
            return Map.of(
                    "resourceType", "Provenance",
                    // "recorded", ZonedDateTime.now().toString(),
                    "interactionId", interactionId,
                    "agent", List.of(Map.of(
                            "who", Map.of(
                                    "coding", List.of(Map.of(
                                            "system", "Validator",
                                            "display", "frictionless version 5.18.0"))))),
                    // "role", List.of(Map.of(
                    // "coding", List.of(Map.of(
                    // "system", "http://hl7.org/fhir/provenance-agent-role",
                    // "code", "validator",
                    // "display", "Validator")))),
                    // "who", Map.of(
                    // "identifier", Map.of(
                    // "value", "Validator"),
                    // "display", "frictionless version 5.18.0"))),
                    "initiatedAt", initiatedAt,
                    "completedAt", completedAt,
                    "description", "Validation of  files in " + originalFileName,
                    "validatedFiles", fileNames);
        }

        public Map<String, Object> processScreenings(final String interactionId, final Instant initiatedAt,
                final String originalFileName, final String tenantId) {
            try {
                log.info("Inbound Folder Path: {} for interactionid :{} ",
                        appConfig.getCsv().validation().inboundPath(), interactionId);
                log.info("Ingress Home Path: {} for interactionId : {}",
                        appConfig.getCsv().validation().ingessHomePath(), interactionId);
                // Process ZIP files and get the session ID
                final UUID processId = processZipFilesFromInbound(interactionId);
                log.info("ZIP files processed with session ID: {} for interaction id :{} ", processId, interactionId);

                // Construct processed directory path
                final String processedDirPath = appConfig.getCsv().validation().ingessHomePath() + "/" + processId
                        + "/ingress";

                copyFilesToProcessedDir(processedDirPath);
                createOutputFileInProcessedDir(processedDirPath);
                log.info("Attempting to resolve processed directory: {} for interactionId : {}", processedDirPath,
                        interactionId);

                // Get processed files for validation
                final FileObject processedDir = vfsCoreService
                        .resolveFile(Paths.get(processedDirPath).toAbsolutePath().toString());

                if (!vfsCoreService.fileExists(processedDir)) {
                    log.error("Processed directory does not exist: {} for interactionId : {}", processedDirPath,
                            interactionId);
                    throw new FileSystemException("Processed directory not found: " + processedDirPath);
                }

                // Collect CSV files for validation
                final List<String> csvFiles = scanForCsvFiles(processedDir, interactionId);

                final Map<FileType, FileDetail> files = processFiles(csvFiles);
                saveScreeningGroup(request, file, files, tenantId);
                log.info("Found {} CSV files for validation for interactionId :{}", csvFiles.size(), interactionId);

                if (csvFiles.isEmpty()) {
                    log.warn("No CSV files found for validation. Skipping validation.for interactionId :{} ",
                            interactionId);
                    return null;
                }

                // Validate CSV files
                final String validationResults = validateCsvUsingPython(csvFiles, interactionId);

                final Instant completedAt = Instant.now();
                return createOperationOutcome(interactionId, validationResults, csvFiles, request, file.getSize(),
                        initiatedAt, completedAt, originalFileName);

            } catch (final Exception e) {
                log.error("Error in ZIP processing tasklet: {} for interactionId :{} ", e.getMessage(), e);
                throw new RuntimeException("Error processing ZIP files: " + e.getMessage(), e);
            }
        }

        private void createOutputFileInProcessedDir(final String processedDirPathStr) throws IOException {
            final Path processedDirPath = Paths.get(processedDirPathStr);
            final Path outputJsonPath = processedDirPath.resolve("output.json");
            if (Files.notExists(outputJsonPath)) {
                Files.createFile(outputJsonPath);
            }
        }

        public void copyFilesToProcessedDir(final String processedDirPathStr) throws IOException {
            final Path processedDirPath = Paths.get(processedDirPathStr);
            final Path pathToPythonExecutable = Paths.get(appConfig.getCsv().validation().packagePath());
            final Path pathToPythonScript = Paths.get(appConfig.getCsv().validation().pythonScriptPath());
            if (Files.notExists(processedDirPath)) {
                Files.createDirectories(processedDirPath);
            }
            Files.copy(pathToPythonExecutable, processedDirPath.resolve(pathToPythonExecutable.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.copy(pathToPythonScript, processedDirPath.resolve(pathToPythonScript.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        private UUID processZipFilesFromInbound(final String interactionId)
                throws FileSystemException, org.apache.commons.vfs2.FileSystemException {
            log.info("CsvService : processZipFilesFromInbound - BEGIN for interactionId :{}" + interactionId);
            final FileObject inboundFO = vfsCoreService
                    .resolveFile(Paths.get(appConfig.getCsv().validation().inboundPath()).toAbsolutePath().toString());
            final FileObject ingresshomeFO = vfsCoreService
                    .resolveFile(
                            Paths.get(appConfig.getCsv().validation().ingessHomePath()).toAbsolutePath().toString());
            if (!vfsCoreService.fileExists(inboundFO)) {
                log.error("Inbound folder does not exist: {} for interactionId :{} ", inboundFO.getName().getPath(),
                        interactionId);
                throw new FileSystemException("Inbound folder does not exist: " + inboundFO.getName().getPath());
            }
            vfsCoreService.validateAndCreateDirectories(ingresshomeFO);
            final VfsIngressConsumer consumer = vfsCoreService.createConsumer(
                    inboundFO,
                    this::extractGroupId,
                    this::isGroupComplete);

            // Important: Capture the returned session UUID and processed file paths
            final UUID processId = vfsCoreService.processFiles(consumer, ingresshomeFO);
            log.info("CsvService : processZipFilesFromInbound - BEGIN for interactionId :{}" + interactionId);
            return processId;
        }

        private List<String> scanForCsvFiles(final FileObject processedDir, String interactionId)
                throws FileSystemException {
            final List<String> csvFiles = new ArrayList<>();

            try {
                final FileObject[] children = processedDir.getChildren();

                if (children == null) {
                    log.warn("No children found in processed directory: {} for interactionId :{}",
                            processedDir.getName().getPath(), interactionId);
                    return csvFiles;
                }

                for (final FileObject child : children) {
                    // Enhanced null and extension checking
                    if (child != null
                            && child.getName() != null
                            && "csv".equalsIgnoreCase(child.getName().getExtension())) {
                        log.info("Found CSV file: {}", child.getName().getPath());
                        csvFiles.add(child.getName().getPath());
                    }
                }

                if (csvFiles.isEmpty()) {
                    log.warn("No CSV files found in directory: {}", processedDir.getName().getPath());
                }
            } catch (final org.apache.commons.vfs2.FileSystemException e) {
                log.error("Error collecting CSV files from directory {}: {}",
                        processedDir.getName().getPath(), e.getMessage(), e);
            }

            return csvFiles;
        }

        // private Map<String, Object> validateFiles(List<String> csvFiles) {
        // Map<String, Object> validationResults = new HashMap<>();
        // if (csvFiles == null || csvFiles.isEmpty()) {
        // log.warn("No CSV files provided for validation when csvFiles == null ");
        // validationResults.put("status", "NO_FILES");
        // validationResults.put("message", "No CSV files found for validation");
        // return validationResults;
        // }

        // // Group files by test case number
        // Map<String, List<String>> groupedFiles = csvFiles.stream()
        // .collect(Collectors.groupingBy(filePath -> {
        // // Extract test case number from file path
        // String fileName = Paths.get(filePath).getFileName().toString();
        // // Extract the testcase number using regex
        // // please change the grouping logic:
        // // To-do:change grouping logic
        // Pattern pattern = Pattern.compile(".*-testcase(\d+)\.csv$");
        // var matcher = pattern.matcher(fileName);
        // if (matcher.find()) {
        // return matcher.group(1); // Returns the test case number
        // }
        // return "unknown";
        // }));

        // // Process each group together
        // for (Map.Entry<String, List<String>> entry : groupedFiles.entrySet()) {
        // String testCaseNum = entry.getKey();
        // List<String> group = entry.getValue();

        // try {
        // log.debug("Starting CSV validation for test case {}: {}", testCaseNum,
        // group);
        // Map<String, Object> groupResults = validateCsvGroup(group);
        // validationResults.put("testcase_" + testCaseNum, groupResults);
        // log.debug("Validation results for test case {}: {}", testCaseNum,
        // groupResults);
        // } catch (Exception e) {
        // log.error("Error validating CSV files for test case {}: {}", testCaseNum,
        // e.getMessage(), e);
        // Map<String, Object> errorResult = new HashMap<>();
        // errorResult.put("validationError", e.getMessage());
        // errorResult.put("status", "FAILED");
        // validationResults.put("testcase_" + testCaseNum, errorResult);
        // }
        // }

        // return validationResults;
        // }

        public String validateCsvUsingPython(final List<String> filePaths, final String interactionId)
                throws Exception {
            log.info("CsvService : validateCsvUsingPython BEGIN for interactionId :{} " + interactionId);
            try {
                final var config = appConfig.getCsv().validation();
                if (config == null) {
                    throw new IllegalStateException("CSV validation configuration is null");
                }

                // Enhanced validation input
                if (filePaths == null || filePaths.isEmpty()) {
                    log.error("No files provided for validation");
                    throw new IllegalArgumentException("No files provided for validation");
                }

                // Ensure the files exist and are valid using VFS before running the validation
                final List<FileObject> fileObjects = new ArrayList<>();
                for (final String filePath : filePaths) {
                    log.info("Validating file: {}", filePath);
                    final FileObject file = vfsCoreService.resolveFile(filePath);
                    if (!vfsCoreService.fileExists(file)) {
                        log.error("File not found: {}", filePath);
                        throw new FileNotFoundException("File not found: " + filePath);
                    }
                    fileObjects.add(file);
                }

                // Validate and create directories
                vfsCoreService.validateAndCreateDirectories(fileObjects.toArray(new FileObject[0]));

                // Build command to run Python script
                final List<String> command = buildValidationCommand(config, filePaths);

                log.info("Executing validation command: {}", String.join(" ", command));

                final ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.directory(new File(filePaths.get(0)).getParentFile());
                processBuilder.command(command);
                processBuilder.redirectErrorStream(true);

                final Process process = processBuilder.start();

                // Capture and handle output/error streams
                final StringBuilder output = new StringBuilder();
                final StringBuilder errorOutput = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;

                    while ((line = reader.readLine()) != null) {
                        log.info("argument : " + line);
                        output.append(line).append("\n");
                    }
                }

                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                }

                final int exitCode = process.waitFor();
                if (exitCode != 0) {
                    log.error("Python script execution failed. Exit code: {}, Error: {}",
                            exitCode, errorOutput.toString());
                    throw new IOException("Python script execution failed with exit code " +
                            exitCode + ": " + errorOutput.toString());
                }
                log.info("CsvService : validateCsvUsingPython END for interactionId :{} " + interactionId);
                // Return parsed validation results
                return output.toString();

            } catch (IOException | InterruptedException e) {
                log.error("Error during CSV validation: {}", e.getMessage(), e);
                throw new RuntimeException("Error during CSV validation", e);
            }
        }

        private List<String> buildValidationCommand(final AppConfig.CsvValidation.Validation config,
                final List<String> filePaths) {
            final List<String> command = new ArrayList<>();
            command.add(config.pythonExecutable());
            command.add("validate-nyher-fhir-ig-equivalent.py");
            command.add("datapackage-nyher-fhir-ig-equivalent.json");
            Map<FileType, String> fileTypeToFileNameMap = filePaths.stream()
                    .map(path -> path.substring(path.lastIndexOf("/") + 1))
                    .collect(Collectors.toMap(
                            FileType::fromFilename,
                            filename -> filename));
            command.add(fileTypeToFileNameMap.get(FileType.QE_ADMIN_DATA));
            command.add(fileTypeToFileNameMap.get(FileType.SCREENING_OBSERVATION_DATA));
            command.add(fileTypeToFileNameMap.get(FileType.SCREENING_PROFILE_DATA));
            command.add(fileTypeToFileNameMap.get(FileType.DEMOGRAPHIC_DATA));

            // Pad with empty strings if fewer than 7 files
            while (command.size() < 7) { // 1 (python) + 1 (script) + 1 (package) + 4 (files) //TODO CHECK IF THIS IS
                                         // NEEDED ACCORDING TO NUMBER OF FILES.
                command.add("");
            }

            // Add output path
            // command.add("output.json");

            return command;
        }

        private String extractGroupId(final FileObject file) {
            final String fileName = file.getName().getBaseName();
            final var matcher = FILE_PATTERN.matcher(fileName);
            return matcher.matches() ? matcher.group(2) : null;
        }

        private boolean isGroupComplete(final VfsIngressConsumer.IngressGroup group) {
            if (group == null || group.groupedEntries().isEmpty()) {
                return false;
            }

            boolean hasDemographic = false;
            boolean hasQeAdmin = false;
            boolean hasScreening = false;
            // please add other files also according to command

            for (final VfsIngressConsumer.IngressIndividual entry : group.groupedEntries()) {
                final String fileName = entry.entry().getName().getBaseName();
                if (fileName.startsWith("DEMOGRAPHIC_DATA")) {
                    hasDemographic = true;
                } else if (fileName.startsWith("QE_ADMIN_DATA")) {
                    hasQeAdmin = true;
                } else if (fileName.startsWith("SCREENING")) {
                    hasScreening = true;
                }
            }

            return hasDemographic && hasQeAdmin && hasScreening;
        }

        private String getBundleInteractionId(final HttpServletRequest request) {
            return InteractionsFilter.getActiveRequestEnc(request).requestId()
                    .toString();
        }
    }

    public static Map<FileType, FileDetail> processFiles(final List<String> filePaths) {
        final Map<FileType, FileDetail> fileMap = new EnumMap<>(FileType.class);

        for (final String filePath : filePaths) {
            try {
                final Path path = Path.of(filePath);
                final String filename = path.getFileName().toString();
                final FileType fileType = FileType.fromFilename(filename);
                final String content = Files.readString(path);
                final FileDetail fileDetail = new FileDetail(filename, fileType, content);
                fileMap.put(fileType, fileDetail);
            } catch (final IOException e) {
                log.error("Error reading file: " + filePath + " - " + e.getMessage());
            } catch (final IllegalArgumentException e) {
                log.error("Error processing file type for: " + filePath + " - " + e.getMessage());
            }
        }

        return fileMap;
    }

    public enum FileType {
        DEMOGRAPHIC_DATA,
        QE_ADMIN_DATA,
        SCREENING_OBSERVATION_DATA,
        SCREENING_PROFILE_DATA;

        public static FileType fromFilename(final String filename) {
            for (final FileType type : values()) {
                if (filename.startsWith(type.name())) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown file type in filename: " + filename);
        }
    }

    public record FileDetail(String filename, FileType fileType, String content) {
    }
}
