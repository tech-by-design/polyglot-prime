package org.techbd.csv.service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.checkerframework.checker.units.qual.m;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.techbd.corelib.config.Configuration;
import org.techbd.corelib.config.Constants;
import org.techbd.csv.config.AppConfig;
import org.techbd.corelib.config.CoreUdiPrimeJpaConfig;
import org.techbd.csv.config.Nature;
import org.techbd.corelib.config.SourceType;
import org.techbd.corelib.config.State;
import org.techbd.csv.converters.CsvToFhirConverter;
import org.techbd.csv.model.CsvDataValidationStatus;
import org.techbd.csv.model.CsvProcessingMetrics;
import org.techbd.csv.model.CsvProcessingMetrics.CsvProcessingMetricsBuilder;
import org.techbd.csv.model.DemographicData;
import org.techbd.csv.model.FileDetail;
import org.techbd.csv.model.FileType;
import org.techbd.csv.model.PayloadAndValidationOutcome;
import org.techbd.csv.model.QeAdminData;
import org.techbd.csv.model.ScreeningObservationData;
import org.techbd.csv.model.ScreeningProfileData;
import org.techbd.corelib.service.dataledger.DataLedgerApiClient;
import org.techbd.corelib.service.dataledger.DataLedgerApiClient.DataLedgerPayload;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionCsvRequest;
import org.techbd.udi.auto.jooq.ingress.routines.SatInteractionCsvRequestUpserted;
import org.techbd.corelib.util.AppLogger;
import org.techbd.corelib.util.TemplateLogger;
import org.techbd.csv.util.CsvConversionUtil;
import org.techbd.corelib.util.CoreFHIRUtil;

import com.fasterxml.jackson.databind.JsonNode;

import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.Cookie;

@Service
public class CsvBundleProcessorService {
    private final TemplateLogger LOG;
    private final CsvToFhirConverter csvToFhirConverter;
    // private final FHIRService fhirService;
    private final DataLedgerApiClient coreDataLedgerApiClient;
    private final AppConfig appConfig;
    private final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig;
    private final FhirValidationServiceClient fhirValidationServiceClient;

    public CsvBundleProcessorService(final CsvToFhirConverter csvToFhirConverter,
    DataLedgerApiClient coreDataLedgerApiClient,AppConfig appConfig, final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig,
    AppLogger appLogger, FhirValidationServiceClient fhirValidationServiceClient) {
        this.csvToFhirConverter = csvToFhirConverter;
        // this.fhirService = fhirService;
        this.coreDataLedgerApiClient = coreDataLedgerApiClient;
        this.appConfig = appConfig;
        this.coreUdiPrimeJpaConfig = coreUdiPrimeJpaConfig;
        this.fhirValidationServiceClient = fhirValidationServiceClient;
        this.LOG = appLogger.getLogger(CsvBundleProcessorService.class);
    }

    public List<Object> processPayload(final String masterInteractionId,
            final Map<String, PayloadAndValidationOutcome> payloadAndValidationOutcomes,
            final List<FileDetail> filesNotProcessed,
            final Map<String,Object> requestParameters,
            final Map<String,Object> responseParameters,
            final String tenantId, final String originalFileName,String baseFHIRUrl, CsvProcessingMetricsBuilder metricsBuilder) {
        LOG.info("ProcessPayload: BEGIN for zipFileInteractionId: {}, tenantId: {}, baseFHIRURL: {}", masterInteractionId, tenantId, baseFHIRUrl);
       // Ensure severity level is passed through the chain
       String severityLevel = (String) requestParameters.getOrDefault(
               Constants.VALIDATION_SEVERITY_LEVEL,
               appConfig.getValidationSeverityLevel()// Default to error if not specified
       );
       requestParameters.put(Constants.VALIDATION_SEVERITY_LEVEL, severityLevel);
       LOG.debug("CsvBundleProcessorService:: Using validation severity level: {}", severityLevel);

        final List<Object> resultBundles = new ArrayList<>();
        final List<Object> miscErrors = new ArrayList<>();
        boolean isAllCsvConvertedToFhir = true;
        AtomicInteger totalNumberOfBundlesGenerated = new AtomicInteger(0);
        for (final var entry : payloadAndValidationOutcomes.entrySet()) {
            final String groupKey = entry.getKey();
            final PayloadAndValidationOutcome outcome = entry.getValue();
            final String groupInteractionId = outcome.groupInteractionId();
            final Map<String, Object> provenance = outcome.provenance();
            try {

                if (outcome.isValid()) {
                    Map<String, List<DemographicData>> demographicData = null;
                    Map<String, List<ScreeningProfileData>> screeningProfileData = null;
                    Map<String, List<QeAdminData>> qeAdminData = null;
                    Map<String, List<ScreeningObservationData>> screeningObservationData = null;

                    for (final FileDetail fileDetail : outcome.fileDetails()) {
                        final String content = fileDetail.content();
                        final FileType fileType = fileDetail.fileType();
                        switch (fileType) {
                            case SDOH_PtInfo ->
                                demographicData = CsvConversionUtil.convertCsvStringToDemographicData(content,masterInteractionId ,appConfig.getVersion() );
                            case SDOH_ScreeningProf -> screeningProfileData = CsvConversionUtil
                                    .convertCsvStringToScreeningProfileData(content,masterInteractionId ,appConfig.getVersion() );
                            case SDOH_QEadmin ->
                                qeAdminData = CsvConversionUtil.convertCsvStringToQeAdminData(content,masterInteractionId ,appConfig.getVersion() );
                            case SDOH_ScreeningObs -> screeningObservationData = CsvConversionUtil
                                    .convertCsvStringToScreeningObservationData(content,masterInteractionId ,appConfig.getVersion() );
                            default -> throw new IllegalStateException("Unexpected value: " + fileType);
                        }
                    }
                    validateAndThrowIfDataMissing(demographicData, screeningProfileData, qeAdminData,
                            screeningObservationData);
                    if (screeningProfileData != null) {
                        resultBundles.addAll(processScreening(groupKey, demographicData, screeningProfileData,
                                qeAdminData, screeningObservationData, requestParameters, responseParameters, groupInteractionId,
                                masterInteractionId,
                                tenantId, outcome.isValid(), outcome, isAllCsvConvertedToFhir,baseFHIRUrl,totalNumberOfBundlesGenerated,metricsBuilder));
                    }
                } else {
                        metricsBuilder.dataValidationStatus(CsvDataValidationStatus.FAILED.getDescription());
                        DataLedgerApiClient.DataLedgerPayload dataLedgerPayload = DataLedgerPayload.create(
                        DataLedgerApiClient.Actor.TECHBD.getValue(), DataLedgerApiClient.Action.SENT.getValue(), 
                        DataLedgerApiClient.Actor.INVALID_CSV.getValue(), masterInteractionId);
                        final var dataLedgerProvenance = "%s.processPayload".formatted(CsvBundleProcessorService.class.getName());
                        coreDataLedgerApiClient.processRequest(dataLedgerPayload,masterInteractionId,masterInteractionId,groupInteractionId,dataLedgerProvenance,SourceType.CSV.name(),createAdditionalDetails(outcome));
                        isAllCsvConvertedToFhir = false;
                        resultBundles.add(outcome.validationResults());
                }
            } catch (final Exception e) {
                LOG.error("Error processing payload: " + e.getMessage(), e);
                metricsBuilder.dataValidationStatus(CsvDataValidationStatus.FAILED.getDescription());
                final Map<String, Object> errors = createOperationOutcomeForError(masterInteractionId, groupInteractionId, "",
                        "", e, provenance,outcome.fileDetails(),requestParameters);                                
                DataLedgerPayload dataLedgerPayload = DataLedgerPayload.create(
                DataLedgerApiClient.Actor.TECHBD.getValue(), DataLedgerApiClient.Action.SENT.getValue(), 
                DataLedgerApiClient.Actor.INVALID_CSV.getValue(), masterInteractionId);
                final var dataLedgerProvenance = "%s.processPayload".formatted(CsvBundleProcessorService.class.getName());
                coreDataLedgerApiClient.processRequest(dataLedgerPayload,masterInteractionId,masterInteractionId,groupInteractionId,dataLedgerProvenance,SourceType.CSV.name(),errors);
                resultBundles.add(errors);
                miscErrors.add(errors);
                isAllCsvConvertedToFhir = false;
            }
        }
        metricsBuilder.numberOfFhirBundlesGeneratedFromZipFile(totalNumberOfBundlesGenerated.get());
        if (CollectionUtils.isNotEmpty(filesNotProcessed)) {
             metricsBuilder.dataValidationStatus(CsvDataValidationStatus.FAILED.getDescription());
            final Map<String, Object> fileNotProcessedError = createOperationOutcomeForFileNotProcessed(masterInteractionId,
                    filesNotProcessed,
                    originalFileName);
            DataLedgerPayload dataLedgerPayload = DataLedgerPayload.create(
            DataLedgerApiClient.Actor.TECHBD.getValue(), DataLedgerApiClient.Action.SENT.getValue(), 
            DataLedgerApiClient.Actor.INVALID_CSV.getValue(), masterInteractionId);
            final var dataLedgerProvenance = "%s.sendPostRequest".formatted(CsvBundleProcessorService.class.getName());
            coreDataLedgerApiClient.processRequest(dataLedgerPayload,masterInteractionId,masterInteractionId,null,dataLedgerProvenance,SourceType.CSV.name(),fileNotProcessedError);
            resultBundles.add(fileNotProcessedError);
            miscErrors.add(fileNotProcessedError);
            isAllCsvConvertedToFhir = false;
        }
        if (CsvDataValidationStatus.FAILED.getDescription().equals(metricsBuilder.build().getDataValidationStatus())
                && totalNumberOfBundlesGenerated.get() > 0) {
            metricsBuilder.dataValidationStatus(CsvDataValidationStatus.PARTIAL_SUCCESS.getDescription());
        }
        saveMiscErrorAndStatus(miscErrors, isAllCsvConvertedToFhir, masterInteractionId, requestParameters,metricsBuilder.build());
        addObservabilityHeadersToResponse(requestParameters, responseParameters);
        LOG.info("ProcessPayload: END for zipFileInteractionId: {}, tenantId: {}, baseFHIRURL: {}", masterInteractionId, tenantId, baseFHIRUrl);
      //  resultBundles.add(metricsBuilder.build());
        return resultBundles;
    }
    public static Map<String, Object> createAdditionalDetails(PayloadAndValidationOutcome outcome) {
        Map<String, Object> additionalDetails = new HashMap<>();
        additionalDetails.put("error", "Frictionless validation failed");
        additionalDetails.put("fileDetails", outcome.fileDetails());
        additionalDetails.put("isValid", outcome.isValid());
        return additionalDetails;
    }
    private void saveMiscErrorAndStatus(final List<Object> miscError, final boolean allCSvConvertedToFHIR,
            final String masterInteractionId, final Map<String,Object> requestParameters,CsvProcessingMetrics metrics) {
        LOG.info("SaveMiscErrorAndStatus: BEGIN for inteaction id  : {} ",
                masterInteractionId);
        //final var status = allCSvConvertedToFHIR ? "PROCESSED_SUCESSFULLY" : "PARTIALLY_PROCESSED";
        final var dslContext = coreUdiPrimeJpaConfig.dsl();
        final var jooqCfg = dslContext.configuration();
        final var createdAt = OffsetDateTime.now();
        final var initRIHR = new SatInteractionCsvRequestUpserted();
        try {
          //  initRIHR.setStatus(status);    
            initRIHR.setInteractionId(masterInteractionId);
            initRIHR.setUri((String) requestParameters.get(Constants.REQUEST_URI));
            initRIHR.setNature(Nature.UPDATE_ZIP_FILE_PROCESSING_DETAILS.getDescription());
            initRIHR.setCreatedAt(createdAt);
            initRIHR.setCreatedBy(CsvService.class.getName());
            initRIHR.setPTechbdVersionNumber(appConfig.getVersion());
            initRIHR.setPDataValidationStatus(metrics.getDataValidationStatus());
            initRIHR.setPNumberOfFhirBundlesGeneratedFromZipFile(metrics.getNumberOfFhirBundlesGeneratedFromZipFile());
            initRIHR.setPTotalNumberOfFilesInZipFile(metrics.getTotalNumberOfFilesInZipFile());
            initRIHR.setZipFileProcessingErrors(CollectionUtils.isNotEmpty(miscError) ?
                    (JsonNode) Configuration.objectMapper.valueToTree(miscError):null);
            // Extract and set client IP address and user agent for consistency with other services
            String clientIpAddress = null;
            if (requestParameters.containsKey(Constants.CLIENT_IP_ADDRESS)) {
                clientIpAddress = (String) requestParameters.get(Constants.CLIENT_IP_ADDRESS);
            }
            initRIHR.setClientIpAddress(clientIpAddress);
            String userAgent = null;
            if (requestParameters.containsKey(Constants.USER_AGENT)) {
                userAgent = (String) requestParameters.get(Constants.USER_AGENT);
            }
            initRIHR.setUserAgent(userAgent);
            final var start = Instant.now();
            final var execResult = initRIHR.execute(jooqCfg);
            final var end = Instant.now();
            LOG.info(
                    "SaveMiscErrorAndStatus : END for interaction id : {} .Time taken : {} milliseconds"
                            + execResult,
                    masterInteractionId,
                    Duration.between(start, end).toMillis());
        } catch (final Exception e) {
            LOG.error("ERROR:: SaveMiscErrorAndStatus CALL for interaction id : {} "
                    + initRIHR.getName() + " initRIHR error", masterInteractionId,
                    e);
        }
    }

    public String addBundleProvenance(final Map<String, Object> existingProvenance, final List<String> bundleGeneratedFrom,
            final String patientMrnId, final String encounterId, final Instant initiatedAt, final Instant completedAt) throws Exception {
        final Map<String, Object> newProvenance = new HashMap<>();
        newProvenance.put("resourceType", "Provenance");
        newProvenance.put("description",
                "Bundle created from provided files for the given patientMrnId and encounterId");
        newProvenance.put("validatedFiles", bundleGeneratedFrom);
        newProvenance.put("initiatedAt", initiatedAt.toString());
        newProvenance.put("completedAt", completedAt.toString());
        newProvenance.put("patientMrnId", patientMrnId);
        newProvenance.put("encounterId", encounterId);
        final Map<String, Object> agent = new HashMap<>();
        final Map<String, String> whoCoding = new HashMap<>();
        whoCoding.put("system", "generator");
        whoCoding.put("display", "TechByDesign");
        agent.put("who", Collections.singletonMap("coding", List.of(whoCoding)));
        newProvenance.put("agent", List.of(agent));
        final List<Map<String, Object>> provenanceList = new ArrayList<>();
        provenanceList.add(existingProvenance);
        provenanceList.add(newProvenance);
        return Configuration.objectMapper.writeValueAsString(provenanceList);
    }

    public void addHapiFhirValidation(final Map<String, Object> provenance, final String validationDescription) {
        @SuppressWarnings("unchecked")
        final
        Map<String, Object> agent = new HashMap<>();
        final Map<String, String> whoCoding = new HashMap<>();

        whoCoding.put("system", "Validator");
        whoCoding.put("display", "HAPI FHIR Validation");

        agent.put("who", Collections.singletonMap("coding", List.of(whoCoding)));
        provenance.put("hapiFhirValidation", Map.of(
                "agent", List.of(agent),
                "description", validationDescription));
    }

    private void saveFhirConversionStatus(final boolean isValid, final String masterInteractionId, final String groupKey,
            final String groupInteractionId, final String interactionId, final Map<String,Object> requestParameters,
            final String payload, final Map<String, Object> operationOutcome,
            final String tenantId) {
        LOG.info(
                "REGISTER State CONVERTED_TO_FHIR : BEGIN for master InteractionId :{} group interaction id  : {} tenant id : {}",
                masterInteractionId, groupInteractionId, tenantId);
        final var forwardedAt = OffsetDateTime.now();
        final var initRIHR = new RegisterInteractionCsvRequest();
        try {
            final var dslContext = coreUdiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            initRIHR.setPOrigin("http");
            initRIHR.setPInteractionId(groupInteractionId);
            initRIHR.setPGroupHubInteractionId(groupInteractionId);
            initRIHR.setPSourceHubInteractionId(masterInteractionId);
            initRIHR.setPInteractionKey((String) requestParameters.get(org.techbd.csv.config.Constants.REQUEST_URI));
            initRIHR.setPNature((JsonNode) Configuration.objectMapper.valueToTree(
                    Map.of("nature", Nature.CONVERTED_TO_FHIR.getDescription(), "tenant_id",
                            tenantId)));
            initRIHR.setPContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
            initRIHR.setPPayload(null != operationOutcome && operationOutcome.size() > 0
                    ? Configuration.objectMapper.valueToTree(operationOutcome)
                    : Configuration.objectMapper.readTree(payload));
            initRIHR.setPCreatedAt(forwardedAt);
            initRIHR.setPCreatedBy(CsvService.class.getName());
            // Extract and set client IP address and user agent for consistency with other services
            String clientIpAddress = null;
            if (requestParameters.containsKey(Constants.CLIENT_IP_ADDRESS)) {
                clientIpAddress = (String) requestParameters.get(Constants.CLIENT_IP_ADDRESS);
            }
            initRIHR.setPClientIpAddress(clientIpAddress);
            String userAgent = null;
            if (requestParameters.containsKey(Constants.USER_AGENT)) {
                userAgent = (String) requestParameters.get(Constants.USER_AGENT);
            }
            initRIHR.setPUserAgent(userAgent);
            initRIHR.setPFromState(isValid ? State.VALIDATION_SUCCESS.name() : State.VALIDATION_FAILED.name());
            initRIHR.setPToState(StringUtils.isNotEmpty(payload) ? State.CONVERTED_TO_FHIR.name() : State.FHIR_CONVERSION_FAILED.name());
            final var provenance = "%s.saveConvertedFHIR".formatted(CsvBundleProcessorService.class.getName());
            initRIHR.setPProvenance(provenance);
            initRIHR.setPCsvGroupId(groupInteractionId);
            initRIHR.setPTechbdVersionNumber(appConfig.getVersion());
            final var start = Instant.now();
            final var execResult = initRIHR.execute(jooqCfg);
            final var end = Instant.now();
            final JsonNode responseFromDB = initRIHR.getReturnValue();
            final Map<String, Object> responseAttributes = CoreFHIRUtil.extractFields(responseFromDB);
            LOG.info(
                    "CsvBundleProcessorService - REGISTER State CONVERTED_TO_FHIR : END | masterInteractionId: {}, groupInteractionId: {}, tenantId: {}, timeTaken: {} ms, error: {}, hub_nexus_interaction_id: {}{}",
                    masterInteractionId,
                    groupInteractionId,
                    tenantId,
                    Duration.between(start, end).toMillis(),
                    responseAttributes.getOrDefault(Constants.KEY_ERROR, "N/A"),
                    responseAttributes.getOrDefault(Constants.KEY_HUB_NEXUS_INTERACTION_ID, "N/A"),
                    execResult);
        } catch (final Exception e) {
            LOG.error(
                    "ERROR:: REGISTER State CONVERTED_TO_FHIR CALL for master interaction id : {}  group InteractionId :{} tenant id : {}"
                            + initRIHR.getName() + " initRIHR error",
                    masterInteractionId, groupInteractionId,
                    tenantId,
                    e);
        }
    }

private void validateAndThrowIfDataMissing(
        final Map<String, List<DemographicData>> demographicData,
        final Map<String, List<ScreeningProfileData>> screeningProfileData,
        final Map<String, List<QeAdminData>> qeAdminData,
        final Map<String, List<ScreeningObservationData>> screeningObservationData) {
    
    final StringBuilder missingDataMessage = new StringBuilder("The following required data maps are missing: ");
    boolean isAnyMissing = false;
    
    if (demographicData == null || demographicData.isEmpty()) {
        missingDataMessage.append("SDOH_PtInfo, ");
        isAnyMissing = true;
    }
    
    if (screeningProfileData == null || screeningProfileData.isEmpty()) {
        missingDataMessage.append("SDOH_ScreeningProf, ");
        isAnyMissing = true;
    }
    
    if (qeAdminData == null || qeAdminData.isEmpty()) {
        missingDataMessage.append("SDOH_QEadmin, ");
        isAnyMissing = true;
    }
    
    if (screeningObservationData == null || screeningObservationData.isEmpty()) {
        missingDataMessage.append("SDOH_ScreeningObs, ");
        isAnyMissing = true;
    }
    
    if (isAnyMissing) {
        throw new IllegalArgumentException(missingDataMessage.toString());
    }
}
    
private List<Object> processScreening(final String groupKey,
            final Map<String, List<DemographicData>> demographicData,
            final Map<String, List<ScreeningProfileData>> screeningProfileData,
            final Map<String, List<QeAdminData>> qeAdminData,
            final Map<String, List<ScreeningObservationData>> screeningObservationData,
            final Map<String,Object> requestParameters,
            final Map<String,Object> responseParameters,
            final String groupInteractionId,
            final String masterInteractionId,
            final String tenantId, final boolean isValid, final PayloadAndValidationOutcome payloadAndValidationOutcome,
            boolean isAllCsvConvertedToFhir,String baseFHIRUrl,AtomicInteger totalNumberOfBundlesGenerated,CsvProcessingMetricsBuilder metricsBuilder)
            throws IOException {
        LOG.info("CsvBundleProcessorService processScreening: BEGIN for zipFileInteractionId: {}, groupInteractionId :{}, tenantId: {}, baseFHIRURL: {}", masterInteractionId, groupInteractionId, tenantId, baseFHIRUrl);
        final List<Object> results = new ArrayList<>();
        final AtomicInteger errorCount = new AtomicInteger();
        screeningProfileData.forEach((encounterId, profileList) -> {
            for (final ScreeningProfileData profile : profileList) {
                final String interactionId = UUID.randomUUID().toString();
                String bundle = null;
                try {
                    final List<DemographicData> demographicList = demographicData.getOrDefault(
                            profile.getPatientMrIdValue(),
                            List.of());
                    final List<QeAdminData> qeAdminList = qeAdminData.getOrDefault(profile.getPatientMrIdValue(),
                            List.of());
                    final List<ScreeningObservationData> screeningObservationList = screeningObservationData
                            .getOrDefault(profile.getEncounterId(), List.of());

                    if (demographicList.isEmpty() || qeAdminList.isEmpty() || screeningObservationList.isEmpty()) {
                        final String errorMessage = String.format(
                                "Data missing in one or more files for patientMrIdValue: %s",
                                profile.getPatientMrIdValue());
                        LOG.error(errorMessage);
                        throw new IllegalArgumentException(errorMessage);
                    }
                    final Instant initiatedAt = Instant.now();
                    bundle = csvToFhirConverter.convert(
                            demographicList.get(0),
                            qeAdminList.get(0),
                            profile,
                            screeningObservationList,
                            interactionId,baseFHIRUrl);
                    final Instant completedAt = Instant.now();
                    if (bundle != null) {
                        totalNumberOfBundlesGenerated.getAndIncrement();
                        final String updatedProvenance = addBundleProvenance(payloadAndValidationOutcome.provenance(),
                                getFileNames(payloadAndValidationOutcome.fileDetails()),
                                profile.getPatientMrIdValue(), profile.getEncounterId(), initiatedAt, completedAt);
                        saveFhirConversionStatus(isValid, masterInteractionId, groupKey, groupInteractionId,
                                interactionId, requestParameters,
                                bundle, null, tenantId);
                        
                              Map<String, Object> headers = CoreFHIRUtil.buildHeaderParametersMap(
                                tenantId,
                                null,
                                null,
                                null,
                                (String) requestParameters.get(Constants.VALIDATION_SEVERITY_LEVEL), // Cast to String, // Pass severity level
                                null,
                                null,
                                updatedProvenance, null);
                       CoreFHIRUtil.buildRequestParametersMap(requestParameters,
                            false, null, SourceType.CSV.name(),  groupInteractionId, masterInteractionId,(String) requestParameters.get(Constants.REQUEST_URI));
                        requestParameters.put(Constants.INTERACTION_ID, interactionId);
                        requestParameters.put(Constants.GROUP_INTERACTION_ID, groupInteractionId);
                        requestParameters.put(Constants.MASTER_INTERACTION_ID, masterInteractionId);
                        // Add sourceType with the correct key for FHIR validation service client
                        requestParameters.put("sourceType", SourceType.CSV.name());
                        requestParameters.put("groupInteractionId", groupInteractionId);
                        requestParameters.put("masterInteractionId", masterInteractionId);
                        requestParameters.putAll(headers);
                        // Call FHIR validation service
                        try {

                            Object validationResult = fhirValidationServiceClient.validateBundle(
                                    bundle, // FHIR bundle JSON string
                                    interactionId, // Current interaction ID
                                    tenantId, // Tenant ID
                                    requestParameters // Map containing ALL headers and parameters
                            );

                            results.add(validationResult);
                            LOG.info(
                                    "Bundle validated and result saved to database for patient MrId: {}, interactionId: {}, masterInteractionId: {}, groupInteractionId: {}",
                                    profile.getPatientMrIdValue(), interactionId, masterInteractionId,
                                    groupInteractionId);
                        } catch (Exception validationException) {
                            LOG.error(
                                    "FHIR validation failed for patient MrId: {}, interactionId: {}, masterInteractionId: {}, groupInteractionId: {}, error: {}",
                                    profile.getPatientMrIdValue(), interactionId, masterInteractionId,
                                    groupInteractionId, validationException.getMessage(), validationException);
                            final Map<String, Object> validationError = createOperationOutcomeForError(
                                    masterInteractionId, interactionId,
                                    profile.getPatientMrIdValue(), profile.getEncounterId(), validationException,
                                    payloadAndValidationOutcome.provenance(), payloadAndValidationOutcome.fileDetails(),
                                    requestParameters);
                            results.add(validationError);

                        }
                        LOG.error(
                                "Bundle generated for  patient  MrId: {}, interactionId: {}, masterInteractionId: {}, groupInteractionId :{}",
                                profile.getPatientMrIdValue(), interactionId, masterInteractionId, groupInteractionId);
                    } else {
                        metricsBuilder.dataValidationStatus(CsvDataValidationStatus.FAILED.getDescription());
                        LOG.error("Bundle not generated for  patient  MrId: {}, interactionId: {}, masterInteractionId: {}, groupInteractionId :{}",
                                profile.getPatientMrIdValue(), interactionId, masterInteractionId,groupInteractionId);
                        errorCount.incrementAndGet();
                        final Map<String, Object> result = createOperationOutcomeForError(masterInteractionId, interactionId,
                                profile.getPatientMrIdValue(), profile.getEncounterId(),
                                new Exception("Bundle not created"),
                                payloadAndValidationOutcome.provenance(),payloadAndValidationOutcome.fileDetails(),requestParameters);
                        String bundleId =CoreFHIRUtil.extractBundleId(bundle, tenantId);                                
                        DataLedgerPayload dataLedgerPayload = DataLedgerPayload.create(
                        DataLedgerApiClient.Actor.TECHBD.getValue(), DataLedgerApiClient.Action.SENT.getValue(), 
                        DataLedgerApiClient.Actor.INVALID_CSV.getValue(), bundleId != null ? bundleId : masterInteractionId);
                        final var dataLedgerProvenance = "%s.processScreening".formatted(CsvBundleProcessorService.class.getName());
                        coreDataLedgerApiClient.processRequest(dataLedgerPayload,interactionId,masterInteractionId,groupInteractionId,dataLedgerProvenance,SourceType.CSV.name(),result);        
                        results.add(result);
                        saveFhirConversionStatus(isValid, masterInteractionId, groupKey, groupInteractionId,
                                interactionId, requestParameters,
                                bundle, result, tenantId);
                    }
                } catch (final Exception e) {
                    errorCount.incrementAndGet();
                    metricsBuilder.dataValidationStatus(CsvDataValidationStatus.FAILED.getDescription());
                    final Map<String, Object> result = createOperationOutcomeForError(masterInteractionId, interactionId,
                            profile.getPatientMrIdValue(), profile.getEncounterId(), e,
                            payloadAndValidationOutcome.provenance(),payloadAndValidationOutcome.fileDetails(),requestParameters);
                    String bundleId =CoreFHIRUtil.extractBundleId(bundle, tenantId);                                
                    DataLedgerPayload dataLedgerPayload = DataLedgerPayload.create(
                    DataLedgerApiClient.Actor.TECHBD.getValue(), DataLedgerApiClient.Action.SENT.getValue(), 
                    DataLedgerApiClient.Actor.INVALID_CSV.getValue(), bundleId != null ? bundleId : masterInteractionId);
                    final var dataLedgerProvenance = "%s.processScreening".formatted(CsvBundleProcessorService.class.getName());
                    coreDataLedgerApiClient.processRequest(dataLedgerPayload,interactionId,masterInteractionId,groupInteractionId,dataLedgerProvenance,SourceType.CSV.name(),result);
                    LOG.error("Error processing patient data for MrId:{}, interactionId: {}, masterInteractionId:{} , groupInteractionId:{}, Error:{}",
                            profile.getPatientMrIdValue(), interactionId,masterInteractionId,groupInteractionId, e.getMessage(), e);
                    results.add(result);
                    saveFhirConversionStatus(isValid, masterInteractionId, groupKey, groupInteractionId, interactionId,
                            requestParameters,
                            bundle, result, tenantId);
                }
            }
        });
        if (errorCount.get() > 0) {
            isAllCsvConvertedToFhir = false;
        }
        LOG.info("CsvBundleProcessorService processScreening: END for zipFileInteractionId: {}, groupInteractionId :{}, tenantId: {}, baseFHIRURL: {}", masterInteractionId, groupInteractionId, tenantId, baseFHIRUrl);
        return results;
    }

    public static List<String> getFileNames(final List<FileDetail> fileDetails) {
        if (fileDetails != null) {
            return fileDetails.stream()
                    .map(FileDetail::filename)
                    .collect(Collectors.toList());
        }
        return List.of(); // return an empty list if the input is null
    }

    private Map<String, Object> createOperationOutcomeForError(
            final String masterInteractionId,
            final String groupInteractionId,
            final String patientMrIdValue,
            final String encounterId,
            final Exception e,
            final Map<String, Object> provenance,List<FileDetail> fileDetails,final Map<String, Object> requestParameters) {
        if (e == null) {
            return Collections.emptyMap();
        }

        final String diagnosticsMessage = "Error processing data for Master Interaction ID: " + masterInteractionId +
                ", Interaction ID: " + groupInteractionId +
                ", Patient MRN: " + patientMrIdValue +
                ", EncounterID : " + encounterId +
                ", Error: " + e.getMessage();

        final String remediationMessage = "Error processing data.";
                // Get severity level from header or use default
            String severityLevel = appConfig.getValidationSeverityLevel(); // Get default from config
            if (requestParameters != null && requestParameters.containsKey(Constants.VALIDATION_SEVERITY_LEVEL)) {
                severityLevel = ((String) requestParameters.get(Constants.VALIDATION_SEVERITY_LEVEL)).toLowerCase();
            }

        final Map<String, Object> errorDetails = Map.of(
                "type", "processing-error",
                "severity", severityLevel,
                "description", remediationMessage,
                "message", diagnosticsMessage);

        return Map.of(
                "masterInteractionId", masterInteractionId,
                "groupInteractionId", groupInteractionId,
                Constants.TECHBD_VERSION, appConfig.getVersion(),
                "patientMrId", patientMrIdValue,
                "encounterId", encounterId,
                "provenance", provenance,
                "fileDetails", fileDetails,
                "validationResults", Map.of(
                        "errors", List.of(errorDetails),
                        "resourceType", "OperationOutcome")
                        );
    }
    public Map<String, Object> createOperationOutcomeForFileNotProcessed(
        final String masterInteractionId,
        final List<FileDetail> filesNotProcessed,
        final String originalFileName) {

        if (filesNotProcessed == null || filesNotProcessed.isEmpty()) {
            return Collections.emptyMap();
        }
    
        // Group by subType + reason to allow distinct reasons within a single subType
        Map<String, List<FileDetail>> grouped = filesNotProcessed.stream()
                .collect(Collectors.groupingBy(fd -> {
                    String reason = fd.reason();
                    if (reason == null || reason.contains("Invalid file prefix")) {
                        return "invalid-prefix|Invalid file prefix";
                    } else if (reason.contains("Group blocked by")) {
                        return "incomplete-group-due-to-encoding|" + reason;
                    } else if (reason.contains("not UTF-8 encoded")) {
                        return "wrong-encoding|File is not UTF-8 encoded";
                    } else {
                        return "unknown|Unknown reason";
                    }
                }));
    
        List<Map<String, Object>> errors = new ArrayList<>();
    
        for (Map.Entry<String, List<FileDetail>> entry : grouped.entrySet()) {
            String[] keyParts = entry.getKey().split("\\|", 2);
            String subType = keyParts[0];
            String reason = keyParts.length > 1 ? keyParts[1] : "Unknown reason";
    
            String description;
            switch (subType) {
                case "invalid-prefix":
                    description = "Filenames must start with one of the following prefixes: " +
                            Arrays.stream(FileType.values())
                                    .map(Enum::name)
                                    .collect(Collectors.joining(", "));
                    break;
                case "incomplete-group-due-to-encoding":
                    description = "Not processed as other files in the group were not UTF-8 encoded";
                    break;
                case "wrong-encoding":
                    description = "File is not UTF-8 encoded";
                    break;
                default:
                    description = "Unknown reason";
            }
    
            List<String> filenames = entry.getValue().stream()
                    .map(FileDetail::filename)
                    .collect(Collectors.toList());
    
            Map<String, Object> errorGroup = new LinkedHashMap<>();
            errorGroup.put("type", "files-not-processed");
            errorGroup.put("subType", subType);
            errorGroup.put("description", description);
            errorGroup.put("reason", reason);
            errorGroup.put("files", filenames);
    
            errors.add(errorGroup);
        }
    
        return Map.of(
                "zipFileInteractionId", masterInteractionId,
                Constants.TECHBD_VERSION, appConfig.getVersion(),
                "originalFileName", originalFileName,
                "validationResults", Map.of(
                        "resourceType", "OperationOutcome",
                        "errors", errors
                )
        );
    }

   private void addObservabilityHeadersToResponse(final Map<String,Object> requestParameters, final Map<String,Object> responseParameters) {
                final var startTime = requestParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME) != null ? 
                    Instant.parse((String) requestParameters.get(Constants.OBSERVABILITY_METRIC_INTERACTION_START_TIME)) : 
                    Instant.now();
                final var finishTime = Instant.now();
                final Duration duration = Duration.between(startTime, finishTime);

                final String startTimeText = startTime.toString();
                final String finishTimeText = finishTime.toString();
                final String durationMsText = String.valueOf(duration.toMillis());
                final String durationNsText = String.valueOf(duration.toNanos());

                // set response headers for those clients that can access HTTP headers
                responseParameters.put("X-Observability-Metric-Interaction-Start-Time", startTimeText);
                responseParameters.put("X-Observability-Metric-Interaction-Finish-Time", finishTimeText);
                responseParameters.put("X-Observability-Metric-Interaction-Duration-Nanosecs", durationMsText);
                responseParameters.put(Constants.HEADER, durationNsText);

                // set a cookie which is accessible to a JavaScript user agent that cannot
                // access HTTP headers (usually HTML pages in web browser cannot access HTTP
                // response headers)
                try {
                        final var metricCookie = new Cookie("Observability-Metric-Interaction-Active",
                                        URLEncoder.encode("{ \"startTime\": \"" + startTimeText
                                                        + "\", \"finishTime\": \"" + finishTimeText
                                                        + "\", \"durationMillisecs\": \"" + durationMsText
                                                        + "\", \"durationNanosecs\": \""
                                                        + durationNsText + "\" }", StandardCharsets.UTF_8.toString()));
                        metricCookie.setPath("/"); // Set path as required
                        metricCookie.setHttpOnly(false); // Ensure the cookie is accessible via JavaScript
                        responseParameters.put(Constants.METRIC_COOKIE, metricCookie);
                } catch (final UnsupportedEncodingException ex) {
                        LOG.error("Exception during setting  Observability-Metric-Interaction-Active cookie to response header",
                                        ex);
                }
        }
}
