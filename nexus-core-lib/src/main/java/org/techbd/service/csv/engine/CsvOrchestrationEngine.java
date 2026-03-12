package org.techbd.service.csv.engine;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.vfs2.FileObject;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.CoreAppConfig;
import org.techbd.config.Nature;
import org.techbd.config.State;
import org.techbd.model.csv.CsvDataValidationStatus;
import org.techbd.model.csv.CsvProcessingMetrics;
import org.techbd.model.csv.FileDetail;
import org.techbd.model.csv.FileType;
import org.techbd.model.csv.PayloadAndValidationOutcome;
import org.techbd.service.csv.CsvBundleProcessorService;
import org.techbd.service.csv.CsvService;
import org.techbd.service.vfs.VfsCoreService;
import org.techbd.service.vfs.VfsIngressConsumer;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionCsvRequest;
import org.techbd.udi.auto.jooq.ingress.routines.SatInteractionCsvRequestUpserted;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;
import org.techbd.util.fhir.CoreFHIRUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.validation.constraints.NotNull;

/**
 * The {@code OrchestrationEngine} class is responsible for managing and
 * orchestrating CSV validation sessions.
 */
@Component
public class CsvOrchestrationEngine {
    private final ConcurrentHashMap<String, OrchestrationSession> sessions;
    private final CoreAppConfig coreAppConfig;
    private final VfsCoreService vfsCoreService;
    private final DSLContext primaryDSLContext;
    private final CsvBundleProcessorService csvBundleProcessorService;
    private static TemplateLogger log;
    private static final Pattern FILE_PATTERN = Pattern.compile(
          "(SDOH_PtInfo|SDOH_QEadmin|SDOH_ScreeningProf|SDOH_ScreeningObs)_(.+)");

    public CsvOrchestrationEngine(final CoreAppConfig coreAppConfig, final VfsCoreService vfsCoreService, @Qualifier("primaryDslContext") final DSLContext primaryDSLContext,AppLogger appLogger,  CsvBundleProcessorService csvBundleProcessorService) {
        this.sessions = new ConcurrentHashMap<>();
        this.coreAppConfig = coreAppConfig;
        this.vfsCoreService = vfsCoreService;
        this.primaryDSLContext = primaryDSLContext;
        log = appLogger.getLogger(CsvOrchestrationEngine.class);
        this.csvBundleProcessorService = csvBundleProcessorService;
    }

    public List<OrchestrationSession> getSessions() {
        return Collections.unmodifiableList(new ArrayList<>(sessions.values()));
    }

    public void orchestrate(@NotNull final OrchestrationSession... newSessions) throws Exception {
        for (final OrchestrationSession session : newSessions) {
            sessions.put(session.getSessionId(), session);
            session.validate();
        }
    }

    public void clear(@NotNull final OrchestrationSession... sessionsToRemove) {
        if (sessionsToRemove != null && sessionsToRemove.length > 0) {
            for (OrchestrationSession session : sessionsToRemove) {
                sessions.remove(session.getSessionId());
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
        private Map<String,Object> requestParameters;
        private boolean generateBundle;

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

        public OrchestrationSessionBuilder withRequestParameters(final Map<String,Object> requestParameters) {
            this.requestParameters = requestParameters;
            return this;
        }

        public OrchestrationSessionBuilder withGenerateBundle(final boolean generateBundle) {
            this.generateBundle = generateBundle;
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
            return new OrchestrationSession(sessionId, tenantId, device, file, masterInteractionId, requestParameters,
                    generateBundle,CsvProcessingMetrics.builder());
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
        private List<FileDetail> filesNotProcessed;
        private CsvProcessingMetrics.CsvProcessingMetricsBuilder metricsBuilder;
        private Map<String, PayloadAndValidationOutcome> payloadAndValidationOutcomes;
        private final String tenantId;
        Map<String,Object> requestParameters;
        private final boolean generateBundle;

        public OrchestrationSession(final String sessionId, final String tenantId, final Device device,
                final MultipartFile file,
                final String masterInteractionId,
                final Map<String,Object> requestParameters,
                boolean generateBundle,CsvProcessingMetrics.CsvProcessingMetricsBuilder metricsBuilder) {
            this.sessionId = sessionId;
            this.tenantId = tenantId;
            this.device = device;
            this.file = file;
            this.validationResults = new HashMap<>();
            this.masterInteractionId = masterInteractionId;
            this.requestParameters = requestParameters;
            this.generateBundle = generateBundle;
            this.payloadAndValidationOutcomes = new HashMap<>();
            this.filesNotProcessed = new ArrayList<>();
            this.metricsBuilder = metricsBuilder;
        }

        public boolean isGenerateBundle() {
            return generateBundle;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getTenantId() {
            return tenantId;
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

        public Map<String, PayloadAndValidationOutcome> getPayloadAndValidationOutcomes() {
            return payloadAndValidationOutcomes;
        }

        public List<FileDetail> getFilesNotProcessed() {
            return filesNotProcessed;
        }

        public CsvProcessingMetrics.CsvProcessingMetricsBuilder  getMetricsBuilder() {
            return metricsBuilder;
        }

        public void validate() throws IOException {
            log.info("CsvOrchestrationEngine : validate - file : {} BEGIN for zipFileInteractionid : {}",
                    file.getOriginalFilename(), masterInteractionId);
            final Instant intiatedAt = Instant.now();
            final String originalFilename = file.getOriginalFilename();
            // Trigger CSV processing and validation
            this.validationResults = processScreenings(masterInteractionId, intiatedAt, originalFilename, tenantId);
            saveCombinedValidationResults(validationResults, masterInteractionId,metricsBuilder.build());
            log.info("CsvOrchestrationEngine : validate - file : {} END for zipFileInteractionid : {}",
                    file.getOriginalFilename(), masterInteractionId);
        }

        @Transactional
        private void saveScreeningGroup(final String groupInteractionId, final Map<String,Object> requestParameters,
                final MultipartFile file, final List<FileDetail> fileDetailList, final String tenantId) {
            log.info("CsvOrchestrationEngine saveScreeningGroup REGISTER State NONE to CSV_ACCEPT: BEGIN for zipFileInteractionId  : {} tenant id : {}",
                    masterInteractionId, tenantId);
            final var jooqCfg = primaryDSLContext.configuration();
            final var forwardedAt = OffsetDateTime.now();
            final var initRIHR = new RegisterInteractionCsvRequest();
            try {
                initRIHR.setPInteractionId(groupInteractionId);
                initRIHR.setPGroupHubInteractionId(groupInteractionId);
                initRIHR.setPSourceHubInteractionId(masterInteractionId);
                initRIHR.setPInteractionKey((String) requestParameters.get(org.techbd.config.Constants.REQUEST_URI));
                initRIHR.setPNature((JsonNode) Configuration.objectMapper.valueToTree(
                        Map.of("nature", Nature.ORIGINAL_FLAT_FILE_CSV.getDescription(), "tenant_id",
                                tenantId)));
                initRIHR.setPContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                initRIHR.setPCsvZipFileName(file.getOriginalFilename());
                initRIHR.setPSourceHubInteractionId(masterInteractionId);
                final InetAddress localHost = InetAddress.getLocalHost();
                final String ipAddress = localHost.getHostAddress();
                initRIHR.setPClientIpAddress(ipAddress);
                initRIHR.setPUserAgent((String) requestParameters.get(org.techbd.config.Constants.USER_AGENT));
                for (final FileDetail fileDetail : fileDetailList) {
                    switch (fileDetail.fileType()) {
                        case FileType.SDOH_PtInfo -> {
                            initRIHR.setPCsvDemographicDataFileName(fileDetail.filename());
                            initRIHR.setPCsvDemographicDataPayloadText(fileDetail.content());
                        }
                        case FileType.SDOH_QEadmin -> {
                            initRIHR.setPCsvQeAdminDataFileName(fileDetail.filename());
                            initRIHR.setPCsvQeAdminDataPayloadText(fileDetail.content());
                        }
                        case FileType.SDOH_ScreeningProf -> {
                            initRIHR.setPCsvScreeningProfileDataFileName(fileDetail.filename());
                            initRIHR.setPCsvScreeningProfileDataPayloadText(fileDetail.content());
                        }
                        case FileType.SDOH_ScreeningObs -> {
                            initRIHR.setPCsvScreeningObservationDataFileName(fileDetail.filename());
                            initRIHR.setPCsvScreeningObservationDataPayloadText(fileDetail.content());
                        }
                    }
                }

                initRIHR.setPCreatedAt(forwardedAt);
                initRIHR.setPCreatedBy(CsvService.class.getName());
                initRIHR.setPFromState(State.NONE.name());
                initRIHR.setPToState(State.CSV_ACCEPT.name());
                final var provenance = "%s.saveScreeningGroup"
                        .formatted(CsvService.class.getName());
                initRIHR.setPProvenance(provenance);
                initRIHR.setPCsvGroupId(masterInteractionId);
                initRIHR.setPTechbdVersionNumber(coreAppConfig.getVersion());
                final var start = Instant.now();
                final var execResult = initRIHR.execute(jooqCfg);
                final var end = Instant.now();
                final JsonNode responseFromDB = initRIHR.getReturnValue();
                final Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(responseFromDB);

                log.info(
                        "CsvOrchestrationEngine - REGISTER State NONE TO CSV_ACCEPT: END | zipFileinteractionId: {}, tenantId: {}, timeTaken: {} ms, error: {}, hub_nexus_interaction_id: {}{}",
                        masterInteractionId,
                        tenantId,
                        Duration.between(start, end).toMillis(),
                        responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
                        responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"),
                        execResult);
            } catch (final Exception e) {
                log.error("ERROR:: CsvOrchestrationEngine REGISTER State NONE TL CSV_ACCEPT CALL for zipFileInteractionId : {} tenant id : {}"
                        + initRIHR.getName() + " initRIHR error", masterInteractionId,
                        tenantId,
                        e);
            }
        }

        public static boolean extractValidValue(Map<String, Object> input) {
            if (input == null || !input.containsKey("validationResults")) {
                return false;
            }

            Object validationResults = input.get("validationResults");

            if (validationResults instanceof ObjectNode) {
                ObjectNode validationResultsNode = (ObjectNode) validationResults;

                // Check if errorSummary exists and is empty
                JsonNode errorsSummaryNode = validationResultsNode.get("errorsSummary");
                if (errorsSummaryNode != null && errorsSummaryNode.isArray() && errorsSummaryNode.size() > 0) {
                    return false; // Return false if errorsSummary is not empty
                }

                // Check the "report" node
                JsonNode reportNode = validationResultsNode.get("report");
                if (reportNode != null && reportNode.isObject()) {
                    JsonNode validNode = reportNode.get("valid");
                    if (validNode != null && validNode.isBoolean()) {
                        return validNode.asBoolean();
                    }
                }
            }

            return false;
        }

        @Transactional
        private void saveValidationResults(final Map<String, Object> validationResults,
                final String masterInteractionId,
                final String groupInteractionId,
                final String tenantId) {
            log.info("CsvOrchestrationEngine REGISTER State VALIDATION CSV_ACCEPT TO VALIDATION : BEGIN for zipFileInteractionId : {} tenant id : {}",
                    masterInteractionId, tenantId);
            final var jooqCfg = primaryDSLContext.configuration();
            final var createdAt = OffsetDateTime.now();
            final var initRIHR = new RegisterInteractionCsvRequest();
            try {
                initRIHR.setPInteractionId(groupInteractionId);
                initRIHR.setPGroupHubInteractionId(groupInteractionId);
                initRIHR.setPSourceHubInteractionId(masterInteractionId);
                initRIHR.setPInteractionKey((String) requestParameters.get(org.techbd.config.Constants.REQUEST_URI));
                initRIHR.setPNature((JsonNode) Configuration.objectMapper.valueToTree(
                        Map.of("nature", Nature.CSV_VALIDATION_RESULT.getDescription(), "tenant_id",
                                tenantId)));
                initRIHR.setPContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                //initRIHR.setCreatedAt(createdAt);
                initRIHR.setPCreatedBy(CsvService.class.getName());
                initRIHR.setPPayload((JsonNode) Configuration.objectMapper.valueToTree(validationResults));
                initRIHR.setPFromState(State.CSV_ACCEPT.name());
                if (extractValidValue(validationResults)) {
                    initRIHR.setPToState(State.VALIDATION_SUCCESS.name());
                } else {
                    initRIHR.setPToState(State.VALIDATION_FAILED.name());
                }
                final var provenance = "%s.saveValidationResults"
                        .formatted(CsvService.class.getName());
                initRIHR.setPProvenance(provenance);
                initRIHR.setPCsvGroupId(masterInteractionId);
                initRIHR.setPTechbdVersionNumber(coreAppConfig.getVersion());
                final var start = Instant.now();
                final var execResult = initRIHR.execute(jooqCfg);
                final var end = Instant.now();
                final JsonNode responseFromDB = initRIHR.getReturnValue();
                final Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(responseFromDB);
                log.info(
                        "CsvOrchestrationEngine - REGISTER State CSV_ACCEPT TO VALIDATION  : END | zipFileInteractionId: {}, tenantId: {}, timeTaken: {} ms, error: {}, hub_nexus_interaction_id: {}{}",
                        masterInteractionId,
                        tenantId,
                        Duration.between(start, end).toMillis(),
                        responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
                        responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"),
                        execResult);
            } catch (final Exception e) {
                log.error("ERROR:: CsvOrchestrationEngine REGISTER State CSV_ACCEPT TO VALIDATION  CALL for zipFileInteractionId : {} tenant id : {}"
                        + initRIHR.getName() + " initRIHR error", masterInteractionId,
                        tenantId,
                        e);
            }
        }

        @Transactional
        private void saveCombinedValidationResults(final Map<String, Object> combinedValidationResults,
                final String masterInteractionId,CsvProcessingMetrics metrics) {
            log.info("SaveCombinedValidationResults: BEGIN for zipFileInteractionId  : {} tenant id : {}",
                    masterInteractionId, tenantId);
            final var jooqCfg = primaryDSLContext.configuration();
            final var createdAt = OffsetDateTime.now();
            final var initRIHR = new SatInteractionCsvRequestUpserted();
            try {
                initRIHR.setInteractionId(masterInteractionId);
                initRIHR.setUri((String) requestParameters.get(org.techbd.config.Constants.REQUEST_URI));
                initRIHR.setNature(Nature.UPDATE_ZIP_FILE_PROCESSING_DETAILS.getDescription());
                initRIHR.setCreatedAt(createdAt);
                initRIHR.setCreatedBy(CsvService.class.getName());
                initRIHR.setPTechbdVersionNumber(coreAppConfig.getVersion());
                initRIHR.setPDataValidationStatus(metrics.getDataValidationStatus());
                initRIHR.setPNumberOfFhirBundlesGeneratedFromZipFile(metrics.getNumberOfFhirBundlesGeneratedFromZipFile());
                initRIHR.setPTotalNumberOfFilesInZipFile(metrics.getTotalNumberOfFilesInZipFile());
                initRIHR.setValidationResultPayload(
                        (JsonNode) Configuration.objectMapper.valueToTree(combinedValidationResults));      
                final var start = Instant.now();
                final var execResult = initRIHR.execute(jooqCfg);
                final var end = Instant.now();
                log.info(
                        "SaveCombinedValidationResults : END for zipFileInteractionId : {} tenant id : {} .Time taken : {} milliseconds"
                                + execResult,
                        masterInteractionId, tenantId,
                        Duration.between(start, end).toMillis());
            } catch (final Exception e) {
                log.error("ERROR:: saveCombinedValidationResults CALL for zipFileInteractionId : {} tenant id : {}"
                        + initRIHR.getName() + " initRIHR error", masterInteractionId,
                        tenantId,
                        e);
            }
        }

        /**
         * Extracts the "provenance" object from the provided map.
         *
         * @param operationOutcomeForThisGroup A map containing operation outcome
         *                                     details.
         * @return A map representing the "provenance" object, or an empty map if
         *         "provenance" is not found.
         */
        public static Map<String, Object> extractProvenance(Map<String, Object> operationOutcomeForThisGroup) {
            return Optional.ofNullable(operationOutcomeForThisGroup)
                    .map(map -> (Map<String, Object>) map.get("provenance"))
                    .orElse(Map.of());
        }

        private  Map<String, Object> createOperationOutcome(final String masterInteractionId,
                final String groupInteractionId,
                final String validationResults,
                final List<FileDetail> fileDetails, final Map<String,Object> requestParameters, final long zipFileSize,
                final Instant initiatedAt, final Instant completedAt, final String originalFileName) throws Exception {
            final Map<String, Object> provenance = populateProvenance(masterInteractionId,groupInteractionId, fileDetails, initiatedAt,
                    completedAt, originalFileName);
            return Map.of(
                    "resourceType", "OperationOutcome",
                    "zipFileInteractionId",masterInteractionId,
                    Constants.TECHBD_VERSION, coreAppConfig.getVersion(),
                    "groupInteractionId", groupInteractionId,
                    "validationResults", Configuration.objectMapper.readTree(validationResults),
                    "provenance", provenance);
        }

        public Map<String, Object> generateValidationResults(final String masterInteractionId,
                final Map<String,Object> requestParameters,
                final long zipFileSize,
                final Instant initiatedAt,
                final Instant completedAt,
                final String originalFileName, final List<Map<String, Object>> combinedValidationResult)
                throws Exception {

            Map<String, Object> result = new HashMap<>();

            final String userAgent = (String) requestParameters.get(org.techbd.config.Constants.USER_AGENT);
            final Device device = Device.INSTANCE;
            result.put("resourceType", "OperationOutcome");
            result.put("zipFileInteractionId", masterInteractionId);
            result.put(Constants.TECHBD_VERSION, coreAppConfig.getVersion());
            result.put("originalFileName", originalFileName);
            result.put("validationResults", combinedValidationResult);
            result.put("requestUri", requestParameters.get(org.techbd.config.Constants.REQUEST_URI));
            result.put("zipFileSize", zipFileSize);
            result.put("userAgent", userAgent);
            result.put("device", Map.of(
                    "deviceId", device.deviceId(),
                    "deviceName", device.deviceName()));
            result.put("initiatedAt", initiatedAt.toString());
            result.put("completedAt", completedAt.toString());
            // result.put("fileNotProcessed", this.filesNotProcessed);
            return result;
        }

        private Map<String, Object> populateProvenance(final String masterInteractionId,final String groupInteractionId,
                final List<FileDetail> fileDetails,
                final Instant initiatedAt, final Instant completedAt, final String originalFileName) {
            final List<String> fileNames = fileDetails.stream()
                    .map(FileDetail::filename)
                    .collect(Collectors.toList());
            return Map.of(
                    "resourceType", "Provenance",
                    "zipFileInteractionId",masterInteractionId,
                    Constants.TECHBD_VERSION, coreAppConfig.getVersion(),
                    "groupInteractionId", groupInteractionId,
                    "agent", List.of(Map.of(
                            "who", Map.of(
                                    "coding", List.of(Map.of(
                                            "system", "Validator",
                                            "display", "frictionless version 5.18.0"))))),
                    "initiatedAt", initiatedAt,
                    "completedAt", completedAt,
                    "description", "Validation of  files in " + originalFileName,
                    "validatedFiles", fileNames);
        }

        public Map<String, Object> processScreenings(final String masterInteractionId, final Instant initiatedAt,
                final String originalFileName, final String tenantId) {
            try {
                 metricsBuilder.dataValidationStatus(CsvDataValidationStatus.SUCCESS.getDescription());
                log.info("Inbound Folder Path: {} for zipFileInteractionId :{} ",
                        coreAppConfig.getCsv().validation().inboundPath(), masterInteractionId);
                log.info("Ingress Home Path: {} for zipFileInteractionId : {}",
                        coreAppConfig.getCsv().validation().ingressHomePath(), masterInteractionId);
                // Process ZIP files and get the session ID
                final UUID processId = processZipFilesFromInbound(masterInteractionId);
                log.info("ZIP files processed with session ID: {} for zipFileInteractionId :{} ", processId,
                        masterInteractionId);

                // Construct processed directory path
                final String processedDirPath = coreAppConfig.getCsv().validation().ingressHomePath() + "/" + processId
                        + "/ingress";

                copyFilesToProcessedDir(processedDirPath);
                createOutputFileInProcessedDir(processedDirPath);
                log.info("Attempting to resolve processed directory: {} for zipFileInteractionId : {}", processedDirPath,
                        masterInteractionId);

                // Get processed files for validation
                final FileObject processedDir = vfsCoreService
                        .resolveFile(Paths.get(processedDirPath).toAbsolutePath().toString());

                if (!vfsCoreService.fileExists(processedDir)) {
                    log.error("Processed directory does not exist: {} for zipFileInteractionId : {}", processedDirPath,
                            masterInteractionId);
                    throw new FileSystemException("Processed directory not found: " + processedDirPath);
                }
                final List<String> csvFiles = scanForCsvFiles(processedDir, masterInteractionId);

                final Map<String, List<FileDetail>> groupedFiles = FileProcessor.processAndGroupFiles(csvFiles);
                List<Map<String, Object>> combinedValidationResults = new ArrayList<>();
                int noOfValidGroups = 0;
                for (Map.Entry<String, List<FileDetail>> entry : groupedFiles.entrySet()) {
                    String groupKey = entry.getKey();
                    if (groupKey.equals("filesNotProcessed")) {
                        if (!entry.getValue().isEmpty()) {
                        this.filesNotProcessed = entry.getValue();
                        combinedValidationResults.add(
                                csvBundleProcessorService.createOperationOutcomeForFileNotProcessed(
                                        masterInteractionId, entry.getValue(), originalFileName));
                        metricsBuilder.dataValidationStatus(CsvDataValidationStatus.FAILED.getDescription());
                        }
                        continue;
                    }
                    List<FileDetail> fileDetails = entry.getValue();
                    Map<String, Object> operationOutcomeForThisGroup;
                    final String groupInteractionId = UUID.randomUUID().toString();
                    boolean isGroupValid = false;
                    
                    if (isGroupComplete(fileDetails)) {
                        operationOutcomeForThisGroup = validateScreeningGroup(groupInteractionId, groupKey, fileDetails,
                                originalFileName);
                        isGroupValid = extractValidValue(operationOutcomeForThisGroup);
                    } else {
                        metricsBuilder.dataValidationStatus(CsvDataValidationStatus.FAILED.getDescription());
                        // Incomplete group - generate error operation outcome
                        operationOutcomeForThisGroup = createIncompleteGroupOperationOutcome(
                                groupKey, fileDetails, originalFileName, masterInteractionId);
                        log.warn("Incomplete Group - Missing files for group {} for zipFileInteractionId : {}", groupKey, masterInteractionId);
                    }
                    if (!isGroupValid) {
                        metricsBuilder.dataValidationStatus(CsvDataValidationStatus.FAILED.getDescription());
                    } else {
                        noOfValidGroups++;
                    }
                    combinedValidationResults.add(operationOutcomeForThisGroup);
                    if (generateBundle) {
                        this.payloadAndValidationOutcomes.put(groupKey,
                                new PayloadAndValidationOutcome(fileDetails,
                                        isGroupValid,
                                        groupInteractionId, extractProvenance(operationOutcomeForThisGroup),
                                        operationOutcomeForThisGroup));
                    }
                }
                if (noOfValidGroups > 0 && CsvDataValidationStatus.FAILED.getDescription().equals(metricsBuilder.build().getDataValidationStatus())) {
                    metricsBuilder.dataValidationStatus(CsvDataValidationStatus.PARTIAL_SUCCESS.getDescription());
                }
                Instant completedAt = Instant.now();
                return generateValidationResults(masterInteractionId, requestParameters,
                        file.getSize(), initiatedAt, completedAt, originalFileName, combinedValidationResults);
            } catch (final Exception e) {
                log.error("Error in ZIP processing tasklet for zipFileInteractionId: {}", masterInteractionId, e);
                throw new RuntimeException("Error processing ZIP files for zipFileInteractionId: " + masterInteractionId + " - " + e.getMessage(), e);
            }
        }
        public boolean isGroupComplete(List<FileDetail> fileDetails) {
            Set<FileType> presentFileTypes = fileDetails.stream()
                    .map(FileDetail::fileType)
                    .collect(Collectors.toSet());

            // Define required file types
            Set<FileType> requiredFileTypes = Set.of(
                    FileType.SDOH_QEadmin,
                    FileType.SDOH_ScreeningObs,
                    FileType.SDOH_ScreeningProf,
                    FileType.SDOH_PtInfo);

            return presentFileTypes.containsAll(requiredFileTypes);
        }

        private Map<String, Object> createIncompleteGroupOperationOutcome(
                String groupKey,
                List<FileDetail> fileDetails,
                String originalFileName,
                String masterInteractionId) throws Exception {

            Instant initiatedAt = Instant.now();
            String groupInteractionId = UUID.randomUUID().toString();

            // Determine missing file types
            Set<FileType> requiredFileTypes = Set.of(
                    FileType.SDOH_QEadmin,
                    FileType.SDOH_ScreeningObs,
                    FileType.SDOH_ScreeningProf,
                    FileType.SDOH_PtInfo);

            Set<FileType> presentFileTypes = fileDetails.stream()
                    .map(FileDetail::fileType)
                    .collect(Collectors.toSet());

            Set<FileType> missingFileTypes = new HashSet<>(requiredFileTypes);
            missingFileTypes.removeAll(presentFileTypes);

            Map<String, Object> operationOutcome = new HashMap<>();
            operationOutcome.put("resourceType", "OperationOutcome");
            operationOutcome.put("zipFileInteractionId", masterInteractionId);
            operationOutcome.put(Constants.TECHBD_VERSION, coreAppConfig.getVersion());

            // Validation Results with Detailed Errors
            Map<String, Object> validationResults = new HashMap<>();
            List<Map<String, Object>> errors = new ArrayList<>();

            for (FileType missingType : missingFileTypes) {
                Map<String, Object> error = new HashMap<>();
                error.put("type", "missing-file-error");
                error.put("description", "Input file received is invalid.");
                error.put("message",
                        "Incomplete Group - Missing " + missingType.name() + " file for group " + groupKey);
                errors.add(error);
            }

            validationResults.put("errors", errors);
            operationOutcome.put("validationResults", validationResults);

            // Provenance Details
            Map<String, Object> provenance = new HashMap<>();
            provenance.put("resourceType", "Provenance");
            provenance.put("groupInteractionId", groupInteractionId);

            // Agent Details
            List<Map<String, Object>> agents = new ArrayList<>();
            Map<String, Object> agent = new HashMap<>();
            Map<String, Object> who = new HashMap<>();
            List<Map<String, Object>> coding = new ArrayList<>();
            Map<String, Object> agentCoding = new HashMap<>();
            agentCoding.put("system", "Validator");
            agentCoding.put("display", "TechByDesign");
            coding.add(agentCoding);
            who.put("coding", coding);
            agent.put("who", who);
            agents.add(agent);

            provenance.put("agent", agents);
            provenance.put("initiatedAt", initiatedAt);
            provenance.put("completedAt", Instant.now());
            provenance.put("description", "Validation of files in " + originalFileName);

            // Validated Files
            List<String> validatedFiles = fileDetails.stream()
                    .map(FileDetail::filename)
                    .collect(Collectors.toList());
            provenance.put("validatedFiles", validatedFiles);

            operationOutcome.put("provenance", provenance);

            // Save validation results
            saveValidationResults(
                    operationOutcome,
                    masterInteractionId,
                    groupInteractionId,
                    tenantId);

            return operationOutcome;
        }

        private Map<String, Object> validateScreeningGroup(String groupInteractionId, String groupKey,
                List<FileDetail> fileDetails,
                String originalFileName) throws Exception {
            Instant initiatedAtForThisGroup = Instant.now();

            // Log the group being processed
            log.info("Processing group {} with {} files for interactionId: {}", groupKey, fileDetails.size(),
                    masterInteractionId);
            saveScreeningGroup(groupInteractionId, requestParameters, file, fileDetails, tenantId);

            // Validate CSV files inside the group
            String validationResults = validateCsvUsingPython(fileDetails, masterInteractionId);
            Instant completedAtForThisGroup = Instant.now();

            Map<String, Object> operationOutomeForThisGroup = createOperationOutcome(masterInteractionId,
                    groupInteractionId, validationResults, fileDetails,
                    requestParameters,
                    file.getSize(), initiatedAtForThisGroup, completedAtForThisGroup, originalFileName);

            saveValidationResults(operationOutomeForThisGroup, masterInteractionId, groupInteractionId, tenantId);
            return operationOutomeForThisGroup;
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
            final Path pathToPythonExecutable = Paths.get(coreAppConfig.getCsv().validation().packagePath());
            final Path pathToPythonScript = Paths.get(coreAppConfig.getCsv().validation().pythonScriptPath());
            if (Files.notExists(processedDirPath)) {
                Files.createDirectories(processedDirPath);
            }
            Files.copy(pathToPythonExecutable, processedDirPath.resolve(pathToPythonExecutable.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.copy(pathToPythonScript, processedDirPath.resolve(pathToPythonScript.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        private UUID processZipFilesFromInbound(final String masterInteractionId)
                throws FileSystemException, org.apache.commons.vfs2.FileSystemException {
            log.info("CsvService : processZipFilesFromInbound - BEGIN for zipFileInteractionId :{}" + masterInteractionId);
            final FileObject inboundFO = vfsCoreService
                    .resolveFile(Paths.get(coreAppConfig.getCsv().validation().inboundPath()).toAbsolutePath().toString());
            final FileObject ingresshomeFO = vfsCoreService
                    .resolveFile(
                            Paths.get(coreAppConfig.getCsv().validation().ingressHomePath()).toAbsolutePath().toString());
            if (!vfsCoreService.fileExists(inboundFO)) {
                log.error("Inbound folder does not exist: {} for zipFileInteractionId :{} ", inboundFO.getName().getPath(),
                        masterInteractionId);
                log.error("Inbound folder does not exist: {} for zipFileInteractionId :{} ", inboundFO.getName().getPath(),
                        masterInteractionId);
                throw new FileSystemException("Inbound folder does not exist: " + inboundFO.getName().getPath());
            }
            vfsCoreService.validateAndCreateDirectories(ingresshomeFO);
            final VfsIngressConsumer consumer = vfsCoreService.createConsumer(
                    inboundFO,
                    this::extractGroupId,
                    this::isGroupComplete);

            // Important: Capture the returned session UUID and processed file paths
            final UUID processId = vfsCoreService.processFiles(consumer, ingresshomeFO,masterInteractionId);
            log.info("CsvService : processZipFilesFromInbound - END for zipFileInteractionId :{}" + masterInteractionId);
            return processId;
        }

        private List<String> scanForCsvFiles(final FileObject processedDir, String zipFileInteractionId)
                throws FileSystemException {

            final List<String> csvFiles = new ArrayList<>();
            int totalNumberOfFiles = 0;

            try {
                final FileObject[] children = processedDir.getChildren();

                if (children == null) {
                    log.warn("No children found in processed directory: {} for zipFileInteractionId: {}",
                            processedDir.getName().getPath(), zipFileInteractionId);
                    return csvFiles;
                }

                for (final FileObject child : children) {
                    if (child == null || child.getName() == null) {
                        continue;
                    }

                    final String fileName = child.getName().getBaseName();
                    // Skip directories
                    if (child.getType().hasChildren()) {
                        log.debug("Skipping directory: {} for zipFileInteractionId: {}", fileName,
                                zipFileInteractionId);
                        continue;
                    }
                    // Skip hidden/system files
                    if (fileName.startsWith(".") || fileName.endsWith(".lock") || fileName.startsWith("~")) {
                        log.debug("Skipping hidden/system file: {} for zipFileInteractionId: {}", fileName,
                                zipFileInteractionId);
                        continue;
                    }
                    // Skip explicitly excluded files
                    if ("validate-nyher-fhir-ig-equivalent.py".equals(fileName)
                            || "datapackage-nyher-fhir-ig-equivalent.json".equals(fileName)
                            || "output.json".equals(fileName)
                            || (fileName.startsWith(zipFileInteractionId) && fileName.endsWith(".zip"))) {
                        log.debug("Skipping excluded file: {} for zipFileInteractionId: {}", fileName,
                                zipFileInteractionId);
                        continue;
                    }
                    // Count all valid files
                    totalNumberOfFiles++;

                    // Collect only CSV file paths
                    if ("csv".equalsIgnoreCase(child.getName().getExtension())) {
                        csvFiles.add(child.getName().getPath());
                    }
                }

                if (csvFiles.isEmpty()) {
                    log.warn("No CSV files found in directory: {} for zipFileInteractionId: {}",
                            processedDir.getName().getPath(), zipFileInteractionId);
                }

            } catch (final org.apache.commons.vfs2.FileSystemException e) {
                log.error("Error collecting files from directory {} for zipFileInteractionId: {} -> {}",
                        processedDir.getName().getPath(), zipFileInteractionId, e.getMessage(), e);
            }
            log.info("Summary for zipFileInteractionId: {} -> Total files: {}, Total CSV files: {}",
                    zipFileInteractionId, totalNumberOfFiles, csvFiles.size());
            metricsBuilder.totalNumberOfFilesInZipFile(totalNumberOfFiles);
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

        public String validateCsvUsingPython(final List<FileDetail> fileDetails, final String zipFileInteractionId)
                throws Exception {
            log.info("CsvService : validateCsvUsingPython BEGIN for zipFileInteractionId :{} " + zipFileInteractionId);

            Process process = null; // ← CHANGE: Declare outside try block

            try {
                final var config = coreAppConfig.getCsv().validation();
                if (config == null) {
                    throw new IllegalStateException("CSV validation configuration is null");
                }

                // Enhanced validation input
                if (fileDetails == null || fileDetails.isEmpty()) {
                    log.error("No files provided for validation");
                    throw new IllegalArgumentException("No files provided for validation");
                }

                // Ensure the files exist and are valid using VFS before running the validation
                final List<FileObject> fileObjects = new ArrayList<>();
                for (final FileDetail fileDetail : fileDetails) {
                    log.info("Validating file: {} for zipFileInteractionId :{} ", fileDetail.filename(), zipFileInteractionId);
                    final FileObject file = vfsCoreService.resolveFile(fileDetail.filePath());
                    if (!vfsCoreService.fileExists(file)) {
                        log.error("File not found: {} for zipFileInteractionId :{}", fileDetail.filePath(),zipFileInteractionId);
                        throw new FileNotFoundException("File not found for zipFileInteractionId :" + zipFileInteractionId + " " + fileDetail.filePath());
                    }
                    fileObjects.add(file);
                }

                // Validate and create directories
                vfsCoreService.validateAndCreateDirectories(fileObjects.toArray(new FileObject[0]));

                // Build command to run Python script
                final List<String> command = buildValidationCommand(config, fileDetails);

                log.info("Executing validation command: {} for zipFileIInteractionId : {} ", String.join(" ", command), zipFileInteractionId);

                final ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.directory(new File(fileDetails.get(0).filePath()).getParentFile());
                processBuilder.command(command);
                processBuilder.redirectErrorStream(true);

                process = processBuilder.start();

                // Capture and handle output/error streams
                final StringBuilder output = new StringBuilder();
                final StringBuilder errorOutput = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;

                    while ((line = reader.readLine()) != null) {
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

                //Force destroy process immediately after waitFor
                process.destroyForcibly();

                if (exitCode != 0) {
                    log.error("Python script execution failed. Exit code: {}, Error: {} for zipFileInteractionId : {}",
                            exitCode, errorOutput.toString(), zipFileInteractionId);
                    throw new IOException("Python script execution failed with exit code " +
                            exitCode + ": " + errorOutput.toString());
                }
                log.info(
                        "CsvService : validateCsvUsingPython END for zipFileInteractionId :{} " + zipFileInteractionId);
                // Return parsed validation results
                return output.toString();

            } catch (IOException | InterruptedException e) {
                log.error("Error during CSV validation: {} for zipFileInteractionId : {}", e.getMessage(),
                        zipFileInteractionId, e);
                throw new RuntimeException("Error during CSV validation : " + e.getMessage(), e);
            } finally {
                // Cleanup block to force-kill process if still alive
                if (process != null && process.isAlive()) {
                    log.warn("Python process still alive, forcing destruction for zipFileInteractionId: {}",
                            zipFileInteractionId);
                    process.destroyForcibly();
                    try {
                        process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        private List<String> buildValidationCommand(CoreAppConfig.CsvValidation.Validation config,
                final List<FileDetail> fileDetails) {
            final List<String> command = new ArrayList<>();
            command.add(config.pythonExecutable());
            command.add("validate-nyher-fhir-ig-equivalent.py");
            command.add("datapackage-nyher-fhir-ig-equivalent.json");
            List<FileType> fileTypeOrder = Arrays.asList(
                    FileType.SDOH_QEadmin,
                    FileType.SDOH_ScreeningProf,
                    FileType.SDOH_ScreeningObs,
                    FileType.SDOH_PtInfo);
            Map<FileType, String> fileTypeToFileNameMap = new HashMap<>();
            for (FileDetail fileDetail : fileDetails) {
                fileTypeToFileNameMap.put(fileDetail.fileType(), fileDetail.filename());
            }
            for (FileType fileType : fileTypeOrder) {
                command.add(fileTypeToFileNameMap.get(fileType)); // Adding the filename in order
            }

            // Pad with empty strings if fewer than 7 files
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
    }

    // public static Map<FileType, FileDetail> processFiles(final List<String>
    // filePaths) {
    // final Map<FileType, FileDetail> fileMap = new EnumMap<>(FileType.class);

    // for (final String filePath : filePaths) {
    // try {
    // final Path path = Path.of(filePath);
    // final String filename = path.getFileName().toString();
    // final FileType fileType = FileType.fromFilename(filename);
    // final String content = Files.readString(path);
    // final FileDetail fileDetail = new FileDetail(filename, fileType, content);
    // fileMap.put(fileType, fileDetail);
    // } catch (final IOException e) {
    // log.error("Error reading file: " + filePath + " - " + e.getMessage());
    // } catch (final IllegalArgumentException e) {
    // log.error("Error processing file type for: " + filePath + " - " +
    // e.getMessage());
    // }
    // }
    // return fileMap;
    // }
}
