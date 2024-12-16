package org.techbd.service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
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
import org.techbd.service.converters.csv.CsvToFhirConverter;
import org.techbd.service.http.hub.prime.api.FHIRService;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;
import org.techbd.util.CsvConversionUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
@Service
public class CsvBundleProcessorService {
    private static final Logger LOG = LoggerFactory.getLogger(CsvBundleProcessorService.class.getName());
    private final CsvToFhirConverter csvToFhirConverter;
    private final FHIRService fhirService;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;

    public CsvBundleProcessorService(final CsvToFhirConverter csvToFhirConverter, final FHIRService fhirService,
            final UdiPrimeJpaConfig udiPrimeJpaConfig) {
        this.csvToFhirConverter = csvToFhirConverter;
        this.fhirService = fhirService;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
    }

    public List<Object> processPayload(final String masterInteractionId,
            final Map<String, PayloadAndValidationOutcome> payloadAndValidationOutcomes, HttpServletRequest request,
            HttpServletResponse response, String tenantId) {
        final List<Object> resultBundles = new ArrayList<>();
        final List<Future<Object>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (final var entry : payloadAndValidationOutcomes.entrySet()) {
                final String groupKey = entry.getKey();
                final PayloadAndValidationOutcome outcome = entry.getValue();
                final String groupInteractionId = outcome.groupInteractionId();
                Map<String, List<DemographicData>> demographicData = null;
                Map<String, List<ScreeningProfileData>> screeningProfileData = null;
                Map<String, List<QeAdminData>> qeAdminData = null;
                Map<String, List<ScreeningObservationData>> screeningObservationData = null;
                for (final FileDetail fileDetail : outcome.fileDetails()) {
                    try {
                        final String content = fileDetail.content();
                        final FileType fileType = fileDetail.fileType();
                        switch (fileType) {
                            case DEMOGRAPHIC_DATA ->
                                demographicData = CsvConversionUtil.convertCsvStringToDemographicData(content);
                            case SCREENING_PROFILE_DATA ->
                                screeningProfileData = CsvConversionUtil
                                        .convertCsvStringToScreeningProfileData(content);
                            case QE_ADMIN_DATA ->
                                qeAdminData = CsvConversionUtil.convertCsvStringToQeAdminData(content);
                            case SCREENING_OBSERVATION_DATA ->
                                screeningObservationData = CsvConversionUtil
                                        .convertCsvStringToScreeningObservationData(content);
                            default -> throw new IllegalStateException("Unexpected value: " + fileType);
                        }

                    } catch (final IOException e) {
                        LOG.error(
                                "Error processing file: " + fileDetail.filename() + ", Error: " + e.getMessage());
                    }
                }
                validateAndThrowIfDataMissing(demographicData, screeningProfileData, qeAdminData,
                        screeningObservationData);
                if (screeningProfileData != null) {
                    resultBundles.addAll(processScreeningProfileData(executor, groupKey, demographicData,
                            screeningProfileData,
                            qeAdminData, screeningObservationData, resultBundles, request, response, groupInteractionId,
                            masterInteractionId, tenantId, outcome.isValid()));
                }
            }
            for (final Future<Object> future : futures) {
                future.get();
            }
        } catch (final Exception e) {
            LOG.error("Error occurred during processing: " + e.getMessage(), e);
            createOperationOutcomeForError(masterInteractionId, StringUtils.EMPTY, StringUtils.EMPTY, e);
        }
        return resultBundles;
    }

    private void saveConvertedFHIR(boolean isValid, String masterInteractionId, String groupKey,
            String groupInteractionId, final HttpServletRequest request,
            final String payload,
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
            initRIHR.setSourceHubInteractionId(masterInteractionId);
            initRIHR.setInteractionKey(request.getRequestURI());
            initRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                    Map.of("nature", "Converted to FHIR", "tenant_id",
                            tenantId)));
            initRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
            initRIHR.setPayload(Configuration.objectMapper.readTree(payload));
            initRIHR.setCreatedAt(forwardedAt);
            initRIHR.setCreatedBy(CsvService.class.getName());
            initRIHR.setFromState(isValid ? "VALIDATION SUCCESS" : "VALIDATION FAILED");
            initRIHR.setFromState(isPayloadInstanceOfBundle(payload) ? "CONVERTED_TO_FHIR" : "FHIR_CONVERSION_FAILED");
            final var provenance = "%s.saveConvertedFHIR".formatted(CsvBundleProcessorService.class.getName());
            initRIHR.setProvenance(provenance);
            initRIHR.setCsvGroupId(groupKey);
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

    public boolean isPayloadInstanceOfBundle(String jsonString) {
        try {
            Gson gson = new Gson();
            Bundle bundle = gson.fromJson(jsonString, Bundle.class);
            return bundle instanceof Bundle;
        } catch (Exception e) {
            LOG.error("Error parsing string: " + e.getMessage());
            return false;
        }
    }

    private void validateAndThrowIfDataMissing(
            final Map<String, List<DemographicData>> demographicData,
            final Map<String, List<ScreeningProfileData>> screeningProfileData,
            final Map<String, List<QeAdminData>> qeAdminData,
            final Map<String, List<ScreeningObservationData>> screeningObservationData) {

        StringBuilder missingDataMessage = new StringBuilder("The following required data maps are missing or empty: ");

        boolean isAnyMissing = false;

        if (demographicData == null || demographicData.isEmpty()) {
            missingDataMessage.append("DEMOGRAPHIC_DATA, ");
            isAnyMissing = true;
        }
        if (screeningProfileData == null || screeningProfileData.isEmpty()) {
            missingDataMessage.append("SCREENING_PROFILE_DATA, ");
            isAnyMissing = true;
        }
        if (qeAdminData == null || qeAdminData.isEmpty()) {
            missingDataMessage.append("QE_ADMIN_DATA, ");
            isAnyMissing = true;
        }
        if (screeningObservationData == null || screeningObservationData.isEmpty()) {
            missingDataMessage.append("SCREENING_OBSERVATION_DATA, ");
            isAnyMissing = true;
        }

        if (isAnyMissing) {
            missingDataMessage.setLength(missingDataMessage.length() - 2);
            LOG.error(missingDataMessage.toString());
            throw new IllegalArgumentException(missingDataMessage.toString());
        }
    }

    private List<Object> processScreeningProfileData(final ExecutorService executor, String groupKey,
            final Map<String, List<DemographicData>> demographicData,
            final Map<String, List<ScreeningProfileData>> screeningProfileData,
            final Map<String, List<QeAdminData>> qeAdminData,
            final Map<String, List<ScreeningObservationData>> screeningObservationData,
            final List<Object> resultBundles,
            HttpServletRequest request,
            HttpServletResponse response,
            String groupInteractionId,
            String masterInteractionId,
            String tenantId, boolean isValid) {
        List<Object> results = new ArrayList<>();
        screeningProfileData.forEach((patientMrIdValue, profileList) -> {
            for (ScreeningProfileData profile : profileList) {
                final Callable<Object> screeningProfileTask = () -> {
                    try {
                        final List<DemographicData> demographicList = demographicData != null
                                ? demographicData.get(patientMrIdValue)
                                : List.of();
                        final List<QeAdminData> qeAdminList = qeAdminData != null
                                ? qeAdminData.get(patientMrIdValue)
                                : List.of();
                        final List<ScreeningObservationData> screeningObservationList = screeningObservationData != null
                                ? screeningObservationData.get(profile.getEncounterId())
                                : List.of(); // Get observation for the current encounter

                        if (CollectionUtils.isEmpty(demographicList) || CollectionUtils.isEmpty(qeAdminList)
                                || CollectionUtils.isEmpty(screeningObservationList)) {
                            LOG.error("Data missing in one or more files for patientMrIdValue: " + patientMrIdValue);
                            throw new IllegalArgumentException(
                                    "Data missing in one or more files for patientMrIdValue: " + patientMrIdValue);
                        }
                        final Callable<Object> processBundleTask = () -> {
                            final var bundle = csvToFhirConverter.convert(
                                    demographicList.get(0),
                                    qeAdminList.get(0),
                                    profile,
                                    screeningObservationList,
                                    groupInteractionId);
                            saveConvertedFHIR(isValid, masterInteractionId, groupKey, groupInteractionId, request,
                                    bundle,
                                    tenantId);
                            if (null != bundle) {
                                return fhirService.processBundle(
                                        bundle, "tenant-id", null, null, null, null, null,
                                        Boolean.toString(false), false,
                                        false, false, request, response, null, true, null);
                            } else {
                                return createOperationOutcomeForError(masterInteractionId, groupInteractionId,
                                        patientMrIdValue, new Exception("Bundle not created"));
                            }
                        };
                        final Future<Object> futureResult = executor.submit(processBundleTask);
                        return futureResult.get();
                    } catch (final Exception e) {
                        LOG.error("Error processing patient data for MrId: " + patientMrIdValue + ", Error: "
                                + e.getMessage());
                        return createOperationOutcomeForError(masterInteractionId, groupInteractionId, patientMrIdValue,
                                e);
                    }
                };

                try {
                    Object result = executor.submit(screeningProfileTask).get();
                    results.add(result);
                } catch (final Exception e) {
                    LOG.error("Error submitting task for screening profile data: " + e.getMessage());
                    results.add(
                            createOperationOutcomeForError(masterInteractionId, groupInteractionId, patientMrIdValue,
                                    e));
                }
            }
        });
        return results;
    }

    private OperationOutcome createOperationOutcomeForError(final String masterInteractionId,
            final String groupInteractionId,
            final String patientMrIdValue,
            final Exception e) {
        OperationOutcome operationOutcome = new OperationOutcome();
        OperationOutcome.OperationOutcomeIssueComponent issue = operationOutcome.addIssue();
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(OperationOutcome.IssueType.EXCEPTION);
        issue.setDiagnostics(
                "Error processing data for Master Interaction ID: " + masterInteractionId +
                        ", Group Interaction ID: " + groupInteractionId +
                        ", Patient MRN: " + patientMrIdValue +
                        ", Error: " + e.getMessage());
        return operationOutcome;
    }

}
