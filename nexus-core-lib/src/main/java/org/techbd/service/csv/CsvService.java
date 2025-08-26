package org.techbd.service.csv;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.CoreAppConfig;
import org.techbd.config.CoreUdiPrimeJpaConfig;
import org.techbd.config.CsvProcessingState;
import org.techbd.config.Nature;
import org.techbd.config.Origin;
import org.techbd.config.SourceType;
import org.techbd.config.State;
import org.techbd.service.csv.engine.CsvOrchestrationEngine;
import org.techbd.service.dataledger.CoreDataLedgerApiClient;
import org.techbd.service.dataledger.CoreDataLedgerApiClient.DataLedgerPayload;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionCsvRequest;
import org.techbd.udi.auto.jooq.ingress.routines.SatInteractionCsvRequestUpserted;
import org.techbd.util.AppLogger;
import org.techbd.util.SystemDiagnosticsLogger;
import org.techbd.util.TemplateLogger;
import org.techbd.util.fhir.CoreFHIRUtil;

import com.fasterxml.jackson.databind.JsonNode;

@Service
public class CsvService {
    private final CsvOrchestrationEngine engine;
    private final TemplateLogger LOG;
    private final CsvBundleProcessorService csvBundleProcessorService;
    private final CoreDataLedgerApiClient coreDataLedgerApiClient;
    private final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig;
    private final TaskExecutor asyncTaskExecutor;
    private final CoreAppConfig coreAppConfig;

    public CsvService(
            final CsvOrchestrationEngine engine,
            final CsvBundleProcessorService csvBundleProcessorService,
            final CoreDataLedgerApiClient coreDataLedgerApiClient,
            final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig,
            @Qualifier("asyncTaskExecutor") final TaskExecutor asyncTaskExecutor,
            final CoreAppConfig coreAppConfig,AppLogger appLogger) {
        this.engine = engine;
        this.csvBundleProcessorService = csvBundleProcessorService;
        this.coreDataLedgerApiClient = coreDataLedgerApiClient;
        this.coreUdiPrimeJpaConfig = coreUdiPrimeJpaConfig;
        this.asyncTaskExecutor = asyncTaskExecutor;
        this.coreAppConfig = coreAppConfig;
        this.LOG = appLogger.getLogger(CsvService.class);
    }

    public Object validateCsvFile(final MultipartFile file, final Map<String, Object> requestParameters,
            Map<String, Object> resonseParameters) throws Exception {
        final var zipFileInteractionId = (String) requestParameters.get(Constants.MASTER_INTERACTION_ID);
        LOG.info("CsvService validateCsvFile BEGIN zip File interaction id  : {} tenant id : {}",
                zipFileInteractionId, requestParameters.get(Constants.TENANT_ID));
        CsvOrchestrationEngine.OrchestrationSession session = null;
        try {
            final var dslContext = coreUdiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            saveArchiveInteraction(zipFileInteractionId, jooqCfg, requestParameters, file,
                    CsvProcessingState.PROCESSING_COMPLETED);
            saveIncomingFileToInboundFolder(file, zipFileInteractionId);         
            session = engine.session()
                    .withMasterInteractionId(zipFileInteractionId)
                    .withSessionId(UUID.randomUUID().toString())
                    .withTenantId((String) requestParameters.get(Constants.TENANT_ID))
                    .withFile(file)
                    .withRequestParameters(requestParameters)
                    .build();
            engine.orchestrate(session);
            LOG.info("CsvService validateCsvFile END zip File interaction id  : {} tenant id : {}",
                    zipFileInteractionId, requestParameters.get(Constants.TENANT_ID));
            Map<String, Object> fullOperationOutcome =  session.getValidationResults();
            saveMiscErrorsForValidation(session.getFilesNotProcessed(), zipFileInteractionId, requestParameters, file.getOriginalFilename());
            saveFullOperationOutcome(fullOperationOutcome, zipFileInteractionId, requestParameters);
            return fullOperationOutcome;
        } finally {
            if (null == session) {
                engine.clear(session);
            }
        }
    }

    private void saveArchiveInteractionStatus(
            String zipFileInteractionId,
            org.jooq.Configuration jooqCfg,
            CsvProcessingState state,Map<String,Object> requestParameters) {

        LOG.info("CsvService saveArchiveInteraction - STATUS UPDATE ONLY | zipFileInteractionId: {}, newState: {}",
                zipFileInteractionId, state.name());

        final var updateRIHR = new SatInteractionCsvRequestUpserted();

        try {
            updateRIHR.setInteractionId(zipFileInteractionId);
            updateRIHR.setUri((String) requestParameters.get(Constants.REQUEST_URI));
            updateRIHR.setStatus(state.name());
            updateRIHR.setNature(Nature.UPDATE_ZIP_FILE_PROCESSING_DETAILS.getDescription());
            final var start = Instant.now();
            final var execResult = updateRIHR.execute(jooqCfg);
            final var end = Instant.now();
            // final JsonNode responseFromDB = updateRIHR.getReturnValue();
            // final Map<String, Object> responseAttributes =
            // CoreFHIRUtil.extractFields(responseFromDB);
            // LOG.info("CsvService - STATUS UPDATE END | zipFileInteractionId: {},
            // newState: {}, timeTaken: {} ms, error: {}, hub_nexus_interaction_id: {}{}",
            // zipFileInteractionId,
            // state.name(),
            // Duration.between(start, end).toMillis(),
            // responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
            // responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID,
            // "N/A"),
            // execResult);
        } catch (Exception e) {
            LOG.error("ERROR:: Status update failed for interactionId: {}, state: {}", zipFileInteractionId,
                    state.name(), e);
        }
    }

    private void saveArchiveInteraction(String zipFileInteractionId, final org.jooq.Configuration jooqCfg,
            final Map<String, Object> requestParameters,
            final MultipartFile file, final CsvProcessingState state) {
        final var tenantId = requestParameters.get(Constants.TENANT_ID);
        LOG.info("CsvService saveArchiveInteraction  -BEGIN zipFileInteractionId  : {} tenant id : {}",
                zipFileInteractionId, tenantId);
        final var forwardedAt = OffsetDateTime.now();
        final var initRIHR = new RegisterInteractionCsvRequest();
        try {
            initRIHR.setPOrigin(null == requestParameters.get(Constants.ORIGIN) ? Origin.HTTP.name()
                    : (String) requestParameters.get(Constants.ORIGIN));
            initRIHR.setPInteractionId(zipFileInteractionId);
            initRIHR.setPInteractionKey((String) requestParameters.get(Constants.REQUEST_URI));
            initRIHR.setPNature((JsonNode) Configuration.objectMapper.valueToTree(
                    Map.of("nature", Nature.ORIGINAL_CSV_ZIP_ARCHIVE.getDescription(), "tenant_id",
                            tenantId)));
            initRIHR.setPFromState(State.NONE.name());
            initRIHR.setPToState(State.NONE.name());
            initRIHR.setPContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
            initRIHR.setPCsvZipFileContent(file.getBytes());
            initRIHR.setPCsvZipFileName(file.getOriginalFilename());
            initRIHR.setPCreatedAt(forwardedAt);
            initRIHR.setPCsvStatus(state.name());
            final InetAddress localHost = InetAddress.getLocalHost();
            final String ipAddress = localHost.getHostAddress();
            initRIHR.setPClientIpAddress(ipAddress);
            initRIHR.setPUserAgent((String) requestParameters.get(Constants.USER_AGENT));
            initRIHR.setPCreatedBy(CsvService.class.getName());
            final var provenance = "%s.saveArchiveInteraction".formatted(CsvService.class.getName());
            initRIHR.setPProvenance(provenance);
            initRIHR.setPCsvGroupId(zipFileInteractionId);
            setUserDetails(initRIHR, requestParameters);
            final var start = Instant.now();
            final var execResult = initRIHR.execute(jooqCfg);
            final var end = Instant.now();
            final JsonNode responseFromDB = initRIHR.getReturnValue();
            final Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(responseFromDB);
            LOG.info(
                    "CsvServoce - saveArchiveInteraction END | zipFileInteractionId: {}, tenantId: {}, timeTaken: {} ms, error: {}, hub_nexus_interaction_id: {}{}",
                    zipFileInteractionId,
                    tenantId,
                    Duration.between(start, end).toMillis(),
                    responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
                    responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"),
                    execResult);
        } catch (final Exception e) {
            LOG.error("ERROR:: REGISTER State NONE CALL for interaction id : {} tenant id : {}"
                    + initRIHR.getName() + " initRIHR error", zipFileInteractionId,
                    tenantId,
                    e);
        }
    }

    private void setUserDetails(RegisterInteractionCsvRequest rihr, Map<String, Object> requestParameters) {
        rihr.setPUserName(null == requestParameters.get(Constants.USER_NAME) ? Constants.DEFAULT_USER_NAME
                : (String) requestParameters.get(Constants.USER_NAME));
        rihr.setPUserId(null == requestParameters.get(Constants.USER_ID) ? Constants.DEFAULT_USER_ID
                : (String) requestParameters.get(Constants.USER_ID));
        rihr.setPUserSession(UUID.randomUUID().toString());
        rihr.setPUserRole(null == requestParameters.get(Constants.USER_ROLE) ? Constants.DEFAULT_USER_ROLE
                : (String) requestParameters.get(Constants.USER_ROLE));
    }

    private void auditInitialReceipt(
            String interactionId,
            String provenance,
            Map<String, Object> requestParams,
            MultipartFile file,
            org.jooq.Configuration jooqCfg) {

        var dataLedgerPayload = DataLedgerPayload.create(
                CoreDataLedgerApiClient.Actor.TECHBD.getValue(),
                CoreDataLedgerApiClient.Action.RECEIVED.getValue(),
                CoreDataLedgerApiClient.Actor.TECHBD.getValue(),
                interactionId);

        coreDataLedgerApiClient.processRequest(dataLedgerPayload, interactionId, provenance,
                SourceType.CSV.name(), null);
        saveArchiveInteraction(interactionId, jooqCfg, requestParams, file, CsvProcessingState.RECEIVED);
    }

    private List<Object> processSync(
            String interactionId,
            String tenantId,
            Map<String, Object> requestParams,
            Map<String, Object> responseParams,
            MultipartFile file,
            org.jooq.Configuration jooqCfg,
            long start) throws Exception {

        CsvOrchestrationEngine.OrchestrationSession session = null;

        try {
            saveArchiveInteractionStatus(interactionId, jooqCfg,
                    CsvProcessingState.PROCESSING_INPROGRESS, requestParams);
            session = engine.session()
                    .withMasterInteractionId(interactionId)
                    .withSessionId(UUID.randomUUID().toString())
                    .withTenantId(tenantId)
                    .withGenerateBundle(true)
                    .withFile(file)
                    .withRequestParameters(requestParams)
                    .build();

            engine.orchestrate(session);

            List<Object> fullOperationOutcome = csvBundleProcessorService.processPayload(
                    interactionId,
                    session.getPayloadAndValidationOutcomes(),
                    session.getFilesNotProcessed(),
                    requestParams,
                    responseParams,
                    tenantId,
                    file.getOriginalFilename(),
                    (String) requestParams.get(Constants.BASE_FHIR_URL));

            saveFullOperationOutcome(fullOperationOutcome, interactionId, requestParams);
            LOG.info("Synchronous processing completed for zipFileInteractionId: {}", interactionId);
            return fullOperationOutcome;
        } catch (Exception ex) {
            LOG.error("Synchronous processing failed for zipFileInteractionId: {}. Reason: {}",
                    interactionId, ex.getMessage(), ex);
            saveArchiveInteractionStatus(interactionId, jooqCfg, CsvProcessingState.PROCESSING_FAILED, requestParams);
            SystemDiagnosticsLogger.logResourceStats(interactionId,
                    coreUdiPrimeJpaConfig.udiPrimaryDataSource(), asyncTaskExecutor,coreAppConfig.getVersion());
            throw ex;
        } finally {
            engine.clear(session);
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            LOG.info("Synchronous cleanup complete for zipFileInteractionId: {}, Total time taken: {} ms",
                    interactionId, durationMs);
        }
    }

    private void processAsync(
            String interactionId,
            String tenantId,
            Map<String, Object> requestParams,
            Map<String, Object> responseParams,
            MultipartFile file,
            org.jooq.Configuration jooqCfg,
            long start) {        
        CompletableFuture.runAsync(() -> {
            CsvOrchestrationEngine.OrchestrationSession session = null;
            try {
                saveArchiveInteractionStatus(interactionId, jooqCfg,
                        CsvProcessingState.PROCESSING_INPROGRESS, requestParams);

                session = engine.session()
                        .withMasterInteractionId(interactionId)
                        .withSessionId(UUID.randomUUID().toString())
                        .withTenantId(tenantId)
                        .withGenerateBundle(true)
                        .withFile(file)
                        .withRequestParameters(requestParams)
                        .build();

                engine.orchestrate(session);

                List<Object> fullOperationOutcome = csvBundleProcessorService.processPayload(
                        interactionId,
                        session.getPayloadAndValidationOutcomes(),
                        session.getFilesNotProcessed(),
                        requestParams,
                        responseParams,
                        tenantId,
                        file.getOriginalFilename(),
                        (String) requestParams.get(Constants.BASE_FHIR_URL));

                saveFullOperationOutcome(fullOperationOutcome, interactionId, requestParams);
                LOG.info("Asynchronous processing completed for zipFileInteractionId: {}",
                        interactionId);
            } catch (Exception ex) {
                LOG.error("Asynchronous processing failed for zipFileInteractionId: {}. Reason: {}",
                        interactionId, ex.getMessage(), ex);
                saveArchiveInteractionStatus(interactionId, jooqCfg,
                        CsvProcessingState.PROCESSING_FAILED, requestParams);
                SystemDiagnosticsLogger.logResourceStats(interactionId,
                        coreUdiPrimeJpaConfig.udiPrimaryDataSource(), asyncTaskExecutor,coreAppConfig.getVersion());
            } finally {
                engine.clear(session);
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                LOG.info("Asynchronous cleanup complete for zipFileInteractionId: {}, Total time taken: {} ms",
                        interactionId, durationMs);
            }
        }, asyncTaskExecutor);
    }

    private Map<String, Object> buildAsyncResponse(String interactionId) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "RECEIVED");
        response.put("message",
                "Your file has been received and is being processed. You can track the progress using the interaction ID provided below. Please refer to the Hub UI  Interactions > CSV via HTTPs tab for detailed status updates.");
        response.put("zipFileInteractionId", interactionId);
        return response;
    }

    public List<Object> processZipFile(
            final MultipartFile file,
            final Map<String, Object> requestParameters,
            final Map<String, Object> responseParameters) throws Exception {

        final var zipFileInteractionId = (String) requestParameters.get(Constants.MASTER_INTERACTION_ID);
        final var tenantId = (String) requestParameters.get(Constants.TENANT_ID);
        final var provenance = "%s.processZipFile".formatted(CsvService.class.getName());
        final String isSync = String.valueOf(requestParameters.get(Constants.IMMEDIATE));
        final var dslContext = coreUdiPrimeJpaConfig.dsl();
        final var jooqCfg = dslContext.configuration();

        LOG.info("CsvService processZipFile - BEGIN zipFileInteractionId: {} tenantId: {} isSync: {}",
                zipFileInteractionId, tenantId, isSync);

        // Ledger + Initial Archive
        auditInitialReceipt(zipFileInteractionId, provenance, requestParameters, file, jooqCfg);
        saveIncomingFileToInboundFolder(file, zipFileInteractionId);           
        long start = System.nanoTime();

        if ("true".equalsIgnoreCase(isSync)) {
            LOG.info("Starting synchronous processing for zipFileInteractionId: {}", zipFileInteractionId);
            return processSync(zipFileInteractionId, tenantId, requestParameters, responseParameters, file,
                    jooqCfg, start);
        } else {
            LOG.info("Starting asynchronous processing for zipFileInteractionId: {}", zipFileInteractionId);
            processAsync(zipFileInteractionId, tenantId, requestParameters, responseParameters, file,
                    jooqCfg, start);

            Map<String, Object> response = buildAsyncResponse(zipFileInteractionId);
            LOG.info("Returning interim async response for zipFileInteractionId: {} tenantId: {}",
                    zipFileInteractionId, tenantId);
            return List.of(response);
        }
    }

    private void saveFullOperationOutcome(final Map<String, Object> fullOperationOutcome,
                    final String masterInteractionId, Map<String, Object> requestParameters) {
            LOG.info("CsvService::saveFullOperationOutcome BEGIN for zipFileInteractionId  : {}",
                            masterInteractionId);
            final var dslContext = coreUdiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            final var createdAt = OffsetDateTime.now();
            final var initRIHR = new SatInteractionCsvRequestUpserted();
            try {
                    initRIHR.setInteractionId(masterInteractionId);
                    initRIHR.setUri((String) requestParameters.get(org.techbd.config.Constants.REQUEST_URI));
                    initRIHR.setNature(Nature.UPDATE_ZIP_FILE_PROCESSING_DETAILS.getDescription());
                    initRIHR.setStatus(CsvProcessingState.PROCESSING_COMPLETED.name());
                    initRIHR.setCreatedAt(createdAt);
                    initRIHR.setCreatedBy(CsvService.class.getName());
                    initRIHR.setPFullOperationOutcome(
                                    (JsonNode) Configuration.objectMapper.valueToTree(fullOperationOutcome));
                    final var start = Instant.now();
                    final var execResult = initRIHR.execute(jooqCfg);
                    final var end = Instant.now();
                    LOG.info(
                                    "CsvService::saveFullOperationOutcome : END for zipFileInteractionId : {} .Time taken : {} milliseconds"
                                                    + execResult,
                                    masterInteractionId,
                                    Duration.between(start, end).toMillis());
            } catch (final Exception e) {
                    LOG.error("ERROR:: saveFullOperationOutcome CALL for zipFileInteractionId : {}"
                                    + initRIHR.getName() + " initRIHR error", masterInteractionId,
                                    e);
            }
    }

    
    private void saveFullOperationOutcome(final List<Object> fullOperationOutcome,
                    final String masterInteractionId, Map<String, Object> requestParameters) {
            LOG.info("CsvService::saveFullOperationOutcome BEGIN for zipFileInteractionId  : {}",
                            masterInteractionId);
            final var dslContext = coreUdiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            final var createdAt = OffsetDateTime.now();
            final var initRIHR = new SatInteractionCsvRequestUpserted();
            try {
                    initRIHR.setInteractionId(masterInteractionId);
                    initRIHR.setUri((String) requestParameters.get(org.techbd.config.Constants.REQUEST_URI));
                    initRIHR.setNature(Nature.UPDATE_ZIP_FILE_PROCESSING_DETAILS.getDescription());
                    initRIHR.setStatus(CsvProcessingState.PROCESSING_COMPLETED.name());
                    initRIHR.setCreatedAt(createdAt);
                    initRIHR.setCreatedBy(CsvService.class.getName());
                    initRIHR.setPFullOperationOutcome(
                                    (JsonNode) Configuration.objectMapper.valueToTree(fullOperationOutcome));
                    final var start = Instant.now();
                    final var execResult = initRIHR.execute(jooqCfg);
                    final var end = Instant.now();
                    LOG.info(
                                    "CsvService::saveFullOperationOutcome : END for zipFileInteractionId : {} .Time taken : {} milliseconds"
                                                    + execResult,
                                    masterInteractionId,
                                    Duration.between(start, end).toMillis());
            } catch (final Exception e) {
                    LOG.error("ERROR:: saveFullOperationOutcome CALL for zipFileInteractionId : {}"
                                    + initRIHR.getName() + " initRIHR error", masterInteractionId,
                                    e);
            }
    }
    
    private void saveMiscErrorsForValidation(final List<org.techbd.model.csv.FileDetail> filesNotProcessed,
            final String masterInteractionId, final Map<String, Object> requestParameters, final String originalFileName) {
        if (filesNotProcessed == null || filesNotProcessed.isEmpty()) {
            return;
        }
        
        final Map<String, Object> fileNotProcessedError = csvBundleProcessorService.createOperationOutcomeForFileNotProcessed(
                masterInteractionId, filesNotProcessed, originalFileName);
        
        final List<Object> miscErrors = List.of(fileNotProcessedError);
        
        LOG.info("SaveMiscErrorsForValidation: BEGIN for zipFileInteractionId: {}", masterInteractionId);
        final var dslContext = coreUdiPrimeJpaConfig.dsl();
        final var jooqCfg = dslContext.configuration();
        final var createdAt = OffsetDateTime.now();
        final var initRIHR = new SatInteractionCsvRequestUpserted();
        try {
            initRIHR.setInteractionId(masterInteractionId);
            initRIHR.setUri((String) requestParameters.get(Constants.REQUEST_URI));
            initRIHR.setNature(Nature.UPDATE_ZIP_FILE_PROCESSING_DETAILS.getDescription());
            initRIHR.setCreatedAt(createdAt);
            initRIHR.setCreatedBy(CsvService.class.getName());
            initRIHR.setZipFileProcessingErrors(
                    (JsonNode) Configuration.objectMapper.valueToTree(miscErrors));
            final var start = Instant.now();
            final var execResult = initRIHR.execute(jooqCfg);
            final var end = Instant.now();
            LOG.info(
                    "SaveMiscErrorsForValidation: END for zipFileInteractionId: {} .Time taken: {} milliseconds{}",
                    masterInteractionId,
                    Duration.between(start, end).toMillis(),
                    execResult);
        } catch (final Exception e) {
            LOG.error("ERROR:: SaveMiscErrorsForValidation CALL for zipFileInteractionId: {}"
                    + initRIHR.getName() + " initRIHR error", masterInteractionId, e);
        }
    }

     /**
     * Saves a MultipartFile to a given inbound directory with a unique filename
     * based on the interactionId.
     *
     * @param file                MultipartFile from request
     * @param masterInteractionId unique interaction identifier
     * @param inboundDir          base inbound directory path
     * @return Path to the saved file
     * @throws IOException if saving fails
     */
    public Path saveIncomingFileToInboundFolder(
            MultipartFile file,
            String masterInteractionId) throws IOException {
        final String originalFilename = file.getOriginalFilename();
        final String safeFilename = (originalFilename != null && !originalFilename.isBlank())
                ? originalFilename
                : "upload.zip";
        final String uniqueFilename = masterInteractionId + "_" + safeFilename;
        final Path destinationPath = Path.of(coreAppConfig.getCsv().validation().inboundPath(), uniqueFilename);
        Files.createDirectories(destinationPath.getParent());
        Files.copy(file.getInputStream(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
        return destinationPath;
    }
}
