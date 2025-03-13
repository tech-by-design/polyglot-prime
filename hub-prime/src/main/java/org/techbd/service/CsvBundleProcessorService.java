package org.techbd.service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.techbd.conf.Configuration;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.FileDetail;
import org.techbd.model.csv.FileType;
import org.techbd.model.csv.PayloadAndValidationOutcome;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.constants.SourceType;
import org.techbd.service.converters.csv.CsvToFhirConverter;
import org.techbd.service.http.hub.prime.api.FHIRService;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;
import org.techbd.udi.auto.jooq.ingress.routines.SatInteractionCsvRequestUpserted;
import org.techbd.util.CsvConversionUtil;

import com.fasterxml.jackson.databind.JsonNode;

import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class CsvBundleProcessorService {
    private static final Logger LOG = LoggerFactory.getLogger(CsvBundleProcessorService.class.getName());
    private final CsvToFhirConverter csvToFhirConverter;
    private final FHIRService fhirService;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;
    private static final String START_TIME_ATTRIBUTE = "startTime";

    public CsvBundleProcessorService(final CsvToFhirConverter csvToFhirConverter, final FHIRService fhirService,
            final UdiPrimeJpaConfig udiPrimeJpaConfig) {
        this.csvToFhirConverter = csvToFhirConverter;
        this.fhirService = fhirService;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
    }

    public List<Object> processPayload(final String masterInteractionId,
            final Map<String, PayloadAndValidationOutcome> payloadAndValidationOutcomes,
            final List<String> filesNotProcessed,
            final HttpServletRequest request,
            final HttpServletResponse response,
            final String tenantId, final String originalFileName,String baseFHIRUrl) {
        final List<Object> resultBundles = new ArrayList<>();
        final List<Object> miscErrors = new ArrayList<>();
        boolean isAllCsvConvertedToFhir = true;
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
                                demographicData = CsvConversionUtil.convertCsvStringToDemographicData(content);
                            case SDOH_ScreeningProf -> screeningProfileData = CsvConversionUtil
                                    .convertCsvStringToScreeningProfileData(content);
                            case SDOH_QEadmin ->
                                qeAdminData = CsvConversionUtil.convertCsvStringToQeAdminData(content);
                            case SDOH_ScreeningObs -> screeningObservationData = CsvConversionUtil
                                    .convertCsvStringToScreeningObservationData(content);
                            default -> throw new IllegalStateException("Unexpected value: " + fileType);
                        }
                    }
                    validateAndThrowIfDataMissing(demographicData, screeningProfileData, qeAdminData,
                            screeningObservationData);
                    if (screeningProfileData != null) {
                        resultBundles.addAll(processScreening(groupKey, demographicData, screeningProfileData,
                                qeAdminData, screeningObservationData, request, response, groupInteractionId,
                                masterInteractionId,
                                tenantId, outcome.isValid(), outcome, isAllCsvConvertedToFhir,baseFHIRUrl));
                    }
                } else {
                    isAllCsvConvertedToFhir = false;
                    resultBundles.add(outcome.validationResults());
                }
            } catch (final Exception e) {
                LOG.error("Error processing payload: " + e.getMessage(), e);
                final Map<String, Object> errors = createOperationOutcomeForError(masterInteractionId, groupInteractionId, "",
                        "", e, provenance);
                resultBundles.add(errors);
                miscErrors.add(errors);
                isAllCsvConvertedToFhir = false;
            }
        }
        if (CollectionUtils.isNotEmpty(filesNotProcessed)) {
            final Map<String, Object> fileNotProcessedError = createOperationOutcomeForFileNotProcessed(masterInteractionId,
                    filesNotProcessed,
                    originalFileName);
            resultBundles.add(fileNotProcessedError);
            miscErrors.add(fileNotProcessedError);
            isAllCsvConvertedToFhir = false;
        }
        saveMiscErrorAndStatus(miscErrors, isAllCsvConvertedToFhir, masterInteractionId, request);
        addObservabilityHeadersToResponse(request, response);
        return resultBundles;
    }

    private void saveMiscErrorAndStatus(final List<Object> miscError, final boolean allCSvConvertedToFHIR,
            final String masterInteractionId, final HttpServletRequest request) {
        LOG.info("SaveMiscErrorAndStatus: BEGIN for inteaction id  : {} ",
                masterInteractionId);
        final var status = allCSvConvertedToFHIR ? "PROCESSED_SUCESSFULLY" : "PARTIALLY_PROCESSED";
        final var dslContext = udiPrimeJpaConfig.dsl();
        final var jooqCfg = dslContext.configuration();
        final var createdAt = OffsetDateTime.now();
        final var initRIHR = new SatInteractionCsvRequestUpserted();
        try {
            initRIHR.setStatus(status);    
            initRIHR.setInteractionId(masterInteractionId);
            initRIHR.setUri(request.getRequestURI());
            initRIHR.setNature("Update Zip File Processing Details");
            initRIHR.setCreatedAt(createdAt);
            initRIHR.setCreatedBy(CsvService.class.getName());
            initRIHR.setZipFileProcessingErrors(CollectionUtils.isNotEmpty(miscError) ?
                    (JsonNode) Configuration.objectMapper.valueToTree(miscError):null);
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
            final String groupInteractionId, final String interactionId, final HttpServletRequest request,
            final String payload, final Map<String, Object> operationOutcome,
            final String tenantId) {
        LOG.info(
                "REGISTER State CONVERTED_TO_FHIR : BEGIN for master InteractionId :{} group interaction id  : {} tenant id : {}",
                masterInteractionId, groupInteractionId, tenantId);
        final var forwardedAt = OffsetDateTime.now();
        final var initRIHR = new RegisterInteractionHttpRequest();
        try {
            final var dslContext = udiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            initRIHR.setOrigin("http");
            initRIHR.setInteractionId(groupInteractionId);
            initRIHR.setGroupHubInteractionId(groupInteractionId);
            initRIHR.setSourceHubInteractionId(masterInteractionId);
            initRIHR.setInteractionKey(request.getRequestURI());
            initRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                    Map.of("nature", "Converted to FHIR", "tenant_id",
                            tenantId)));
            initRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
            initRIHR.setPayload(null != operationOutcome && operationOutcome.size() > 0
                    ? Configuration.objectMapper.valueToTree(operationOutcome)
                    : Configuration.objectMapper.readTree(payload));
            initRIHR.setCreatedAt(forwardedAt);
            initRIHR.setCreatedBy(CsvService.class.getName());
            initRIHR.setFromState(isValid ? "VALIDATION SUCCESS" : "VALIDATION FAILED");
            initRIHR.setToState(StringUtils.isNotEmpty(payload) ? "CONVERTED_TO_FHIR" : "FHIR_CONVERSION_FAILED");
            final var provenance = "%s.saveConvertedFHIR".formatted(CsvBundleProcessorService.class.getName());
            initRIHR.setProvenance(provenance);
            initRIHR.setCsvGroupId(groupInteractionId);
            final var start = Instant.now();
            final var execResult = initRIHR.execute(jooqCfg);
            final var end = Instant.now();
            LOG.info(
                    "REGISTER State CONVERTED_TO_FHIR : END for master interaction id : {}  group interaction id :{} tenant id : {} .Time taken : {} milliseconds"
                            + execResult,
                    masterInteractionId, groupInteractionId, tenantId,
                    Duration.between(start, end).toMillis());
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
            final HttpServletRequest request,
            final HttpServletResponse response,
            final String groupInteractionId,
            final String masterInteractionId,
            final String tenantId, final boolean isValid, final PayloadAndValidationOutcome payloadAndValidationOutcome,
            boolean isAllCsvConvertedToFhir,String baseFHIRUrl)
            throws IOException {

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
                        final String updatedProvenance = addBundleProvenance(payloadAndValidationOutcome.provenance(),
                                getFileNames(payloadAndValidationOutcome.fileDetails()),
                                profile.getPatientMrIdValue(), profile.getEncounterId(), initiatedAt, completedAt);
                        saveFhirConversionStatus(isValid, masterInteractionId, groupKey, groupInteractionId,
                                interactionId, request,
                                bundle, null, tenantId);
                        results.add(fhirService.processBundle(
                                bundle, tenantId, null, null, null, false,
                                request, response,
                                updatedProvenance,
                                null, interactionId, groupInteractionId,
                                masterInteractionId, SourceType.CSV.name(), null,null));
                    } else {
                        LOG.error("Bundle not generated for  patient  MrId: {}, interactionId: {}, masterInteractionId: {}, groupInteractionId :{}",
                                profile.getPatientMrIdValue(), interactionId, masterInteractionId,groupInteractionId);
                        errorCount.incrementAndGet();
                        final Map<String, Object> result = createOperationOutcomeForError(masterInteractionId, interactionId,
                                profile.getPatientMrIdValue(), profile.getEncounterId(),
                                new Exception("Bundle not created"),
                                payloadAndValidationOutcome.provenance());
                        results.add(result);
                        saveFhirConversionStatus(isValid, masterInteractionId, groupKey, groupInteractionId,
                                interactionId, request,
                                bundle, result, tenantId);
                    }
                } catch (final Exception e) {
                    errorCount.incrementAndGet();
                    final Map<String, Object> result = createOperationOutcomeForError(masterInteractionId, interactionId,
                            profile.getPatientMrIdValue(), profile.getEncounterId(), e,
                            payloadAndValidationOutcome.provenance());
                    LOG.error("Error processing patient data for MrId:{}, interactionId: {}, masterInteractionId:{} , groupInteractionId:{}, Error:{}",
                            profile.getPatientMrIdValue(), interactionId,masterInteractionId,groupInteractionId, e.getMessage(), e);
                    results.add(result);
                    saveFhirConversionStatus(isValid, masterInteractionId, groupKey, groupInteractionId, interactionId,
                            request,
                            bundle, result, tenantId);
                }
            }
        });
        if (errorCount.get() > 0) {
            isAllCsvConvertedToFhir = false;
        }
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
            final Map<String, Object> provenance) {
        if (e == null) {
            return Collections.emptyMap();
        }

        final String diagnosticsMessage = "Error processing data for Master Interaction ID: " + masterInteractionId +
                ", Interaction ID: " + groupInteractionId +
                ", Patient MRN: " + patientMrIdValue +
                ", EncounterID : " + encounterId +
                ", Error: " + e.getMessage();

        final String remediationMessage = "Error processing data.";

        final Map<String, Object> errorDetails = Map.of(
                "type", "processing-error",
                "description", remediationMessage,
                "message", diagnosticsMessage);

        return Map.of(
                "masterInteractionId", masterInteractionId,
                "groupInteractionId", groupInteractionId,
                "patientMrId", patientMrIdValue,
                "encounterId", encounterId,
                "provenance", provenance,
                "validationResults", Map.of(
                        "errors", List.of(errorDetails),
                        "resourceType", "OperationOutcome"));
    }

    private Map<String, Object> createOperationOutcomeForFileNotProcessed(
            final String masterInteractionId,
            final List<String> filesNotProcessed, final String originalFileName) {
        if (filesNotProcessed == null || filesNotProcessed.isEmpty()) {
            return Collections.emptyMap();
        }

        final StringBuilder diagnosticsMessage = new StringBuilder("Files not processed: in input zip file : ");
        diagnosticsMessage.append(String.join(", ", filesNotProcessed));

        final StringBuilder remediation = new StringBuilder("Filenames must start with one of the following prefixes: ");
        for (final FileType type : FileType.values()) {
            remediation.append(type.name()).append(", ");
        }
        if (remediation.length() > 0) {
            remediation.setLength(remediation.length() - 2); // Remove trailing comma and space
        }

        final Map<String, Object> errorDetails = Map.of(
                "type", "files-not-processed",
                "description", remediation.toString(),
                "message", diagnosticsMessage.toString());

        return Map.of(
                "masterInteractionId", masterInteractionId,
                "originalFileName", originalFileName,
                "validationResults", Map.of(
                        "errors", List.of(errorDetails),
                        "resourceType", "OperationOutcome"));
    }
   private void addObservabilityHeadersToResponse(final HttpServletRequest request, final HttpServletResponse response) {
                final var startTime = (Instant) request.getAttribute(START_TIME_ATTRIBUTE);
                final var finishTime = Instant.now();
                final Duration duration = Duration.between(startTime, finishTime);

                final String startTimeText = startTime.toString();
                final String finishTimeText = finishTime.toString();
                final String durationMsText = String.valueOf(duration.toMillis());
                final String durationNsText = String.valueOf(duration.toNanos());

                // set response headers for those clients that can access HTTP headers
                response.addHeader("X-Observability-Metric-Interaction-Start-Time", startTimeText);
                response.addHeader("X-Observability-Metric-Interaction-Finish-Time", finishTimeText);
                response.addHeader("X-Observability-Metric-Interaction-Duration-Nanosecs", durationMsText);
                response.addHeader("X-Observability-Metric-Interaction-Duration-Millisecs", durationNsText);

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
                        response.addCookie(metricCookie);
                } catch (final UnsupportedEncodingException ex) {
                        LOG.error("Exception during setting  Observability-Metric-Interaction-Active cookie to response header",
                                        ex);
                }
        }
}
