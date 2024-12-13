package org.techbd.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections.CollectionUtils;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.FileDetail;
import org.techbd.model.csv.FileType;
import org.techbd.model.csv.PayloadAndValidationOutcome;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.service.converters.csv.CsvToFhirConverter;
import org.techbd.service.http.hub.prime.api.FHIRService;
import org.techbd.util.CsvConversionUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class CsvBundleProcessorService {

    private final CsvToFhirConverter csvToFhirConverter;
    private final FHIRService fhirService;

    public CsvBundleProcessorService(final CsvToFhirConverter csvToFhirConverter, final FHIRService fhirService) {
        this.csvToFhirConverter = csvToFhirConverter;
        this.fhirService = fhirService;
    }

    public List<Object> processPayload(final String masterInteractionId,
            final Map<String, PayloadAndValidationOutcome> payloadAndValidationOutcomes, HttpServletRequest request,
            HttpServletResponse response) {
        final List<Object> resultBundles = new ArrayList<>();
        final List<Future<Object>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (final var entry : payloadAndValidationOutcomes.entrySet()) {
                final String key = entry.getKey();
                final PayloadAndValidationOutcome outcome = entry.getValue();
                final String groupInteractionId = outcome.groupInteractionId();
                for (final FileDetail fileDetail : outcome.fileDetails()) {
                    final Callable<Object> task = () -> {
                        try {
                            final String content = fileDetail.content();

                            final FileType fileType = fileDetail.fileType();

                            Map<String, List<DemographicData>> demographicData = null;
                            Map<String, List<ScreeningProfileData>> screeningProfileData = null;
                            Map<String, List<QeAdminData>> qeAdminData = null;
                            Map<String, List<ScreeningObservationData>> screeningObservationData = null;

                            switch (fileType) {
                                case DEMOGRAPHIC_DATA ->
                                    demographicData = CsvConversionUtil.convertCsvStringToDemographicData(content);
                                case SCREENING_PROFILE_DATA -> screeningProfileData = CsvConversionUtil
                                        .convertCsvStringToScreeningProfileData(content);
                                case QE_ADMIN_DATA ->
                                    qeAdminData = CsvConversionUtil.convertCsvStringToQeAdminData(content);
                                case SCREENING_OBSERVATION_DATA -> screeningObservationData = CsvConversionUtil
                                        .convertCsvStringToScreeningObservationData(content);
                                default -> throw new IllegalStateException("Unexpected value: " + fileType);
                            }

                            // Validate and handle missing data, add OperationOutcome if needed
                            validateDataAndAddOutcome(demographicData, "DEMOGRAPHIC_DATA", resultBundles);
                            validateDataAndAddOutcome(screeningProfileData, "SCREENING_PROFILE_DATA", resultBundles);
                            validateDataAndAddOutcome(qeAdminData, "QE_ADMIN_DATA", resultBundles);
                            validateDataAndAddOutcome(screeningObservationData, "SCREENING_OBSERVATION_DATA",
                                    resultBundles);

                            if (screeningProfileData != null) {
                                processScreeningProfileData(executor, demographicData, screeningProfileData, qeAdminData,
                                        screeningObservationData, resultBundles, request, response,groupInteractionId,masterInteractionId);
                            }

                        } catch (final IOException e) {
                            System.err.println(
                                    "Error processing file: " + fileDetail.filename() + ", Error: " + e.getMessage());
                        }
                        return null;
                    };

                    // Submit the main task
                    futures.add(executor.submit(task));
                }
            }

            // Wait for all futures to complete
            for (final Future<Object> future : futures) {
                future.get();
            }
        } catch (final Exception e) {
            throw new RuntimeException("Error occurred during processing: " + e.getMessage(), e);
        }

        return resultBundles;
    }

    private <T> void validateDataAndAddOutcome(final Map<String, List<T>> dataMap, final String fileType,
            final List<Object> resultBundles) {
        if (dataMap == null || dataMap.isEmpty()) {
            addOperationOutcome(fileType, resultBundles);
        }
    }

    private void addOperationOutcome(final String fileType, final List<Object> resultBundles) {
        final OperationOutcome operationOutcome = new OperationOutcome();
        final OperationOutcomeIssueComponent issue = operationOutcome.addIssue();
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(OperationOutcome.IssueType.NULL);
        issue.setDiagnostics(fileType + " data is missing.");
        resultBundles.add(operationOutcome);
    }

    private void processScreeningProfileData(final ExecutorService executor,
            final Map<String, List<DemographicData>> demographicData,
            final Map<String, List<ScreeningProfileData>> screeningProfileData,
            final Map<String, List<QeAdminData>> qeAdminData,
            final Map<String, List<ScreeningObservationData>> screeningObservationData,
            final List<Object> resultBundles, HttpServletRequest request, HttpServletResponse response,
            String groupInteractionId, String masterInteractionId) {
        screeningProfileData.forEach((patientMrIdValue, profileList) -> {
            final Callable<Object> screeningProfileTask = () -> {
                try {
                    final List<DemographicData> demographicList = qeAdminData != null
                            ? demographicData.get(patientMrIdValue)
                            : List.of();
                    final List<QeAdminData> qeAdminList = qeAdminData != null ? qeAdminData.get(patientMrIdValue)
                            : List.of();
                    final List<ScreeningObservationData> screeningObservationList = screeningObservationData != null
                            ? screeningObservationData.get(profileList.get(0).getEncounterId())
                            : List.of(); // Get observation of current encounter
                    if (CollectionUtils.isEmpty(demographicList) || CollectionUtils.isEmpty(qeAdminList)
                            || CollectionUtils.isEmpty(screeningObservationList)) {
                        throw new IllegalArgumentException(
                                "Data missing in one or more files for patientMrIdValue: " + patientMrIdValue);
                    }

                    final var bundle = csvToFhirConverter.convert(
                            demographicList.get(0),
                            qeAdminList.get(0),
                            profileList.get(0),
                            screeningObservationList,
                            groupInteractionId);
                    final Callable<Object> processBundleTask = () -> {
                        return fhirService.processBundle(
                                bundle, "tenant-id", null, null, null, null, null,
                                Boolean.toString(false), false,
                                false,
                                false, request, response, null, true, null);
                    };
                    final Future<Object> futureResult = executor.submit(processBundleTask);
                    final Object processResult = futureResult.get();
                    return processResult;
                } catch (final Exception e) {
                    System.err.println("Error processing patient data for MrId: " + patientMrIdValue + ", Error: "
                            + e.getMessage());
                    return null;
                }
            };
            try {
                executor.submit(screeningProfileTask).get();
            } catch (final Exception e) {
                System.err.println("Error submitting task for screening profile data: " + e.getMessage());
            }
        });
    }

}
