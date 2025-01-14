package org.techbd.service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.techbd.util.CsvConversionUtil;

import com.fasterxml.jackson.databind.JsonNode;

import io.micrometer.common.util.StringUtils;
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
            final Map<String, PayloadAndValidationOutcome> payloadAndValidationOutcomes,
            List<String> filesNotProcessed,
            HttpServletRequest request,
            HttpServletResponse response,
            String tenantId, String originalFileName) {
        final List<Object> resultBundles = new ArrayList<>();

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
                    }
                    validateAndThrowIfDataMissing(demographicData, screeningProfileData, qeAdminData,
                            screeningObservationData);
                    if (screeningProfileData != null) {
                        resultBundles.addAll(processScreening(groupKey, demographicData, screeningProfileData,
                                qeAdminData, screeningObservationData, request, response, groupInteractionId,
                                masterInteractionId,
                                tenantId, outcome.isValid(), outcome));
                    }
                } else {
                    resultBundles.add(outcome.validationResults());
                }
            } catch (Exception e) {
                LOG.error("Error processing payload: " + e.getMessage(), e);
                resultBundles.add(
                        createOperationOutcomeForError(masterInteractionId, groupInteractionId, "", "", e, provenance));
            }
        }
        if (CollectionUtils.isNotEmpty(filesNotProcessed)) {
            resultBundles
                    .add(createOperationOutcomeForFileNotProcessed(masterInteractionId,  filesNotProcessed,
                            originalFileName));
        }
        return resultBundles;
    }

    public String addBundleProvenance(Map<String, Object> existingProvenance, List<String> bundleGeneratedFrom,
            String patientMrnId, String encounterId, Instant initiatedAt, Instant completedAt) throws Exception {
        Map<String, Object> newProvenance = new HashMap<>();
        newProvenance.put("resourceType", "Provenance");
        newProvenance.put("description",
                "Bundle created from provided files for the given patientMrnId and encounterId");
        newProvenance.put("validatedFiles", bundleGeneratedFrom);
        newProvenance.put("initiatedAt", initiatedAt.toString());
        newProvenance.put("completedAt", completedAt.toString());
        newProvenance.put("patientMrnId", patientMrnId);
        newProvenance.put("encounterId", encounterId);
        Map<String, Object> agent = new HashMap<>();
        Map<String, String> whoCoding = new HashMap<>();
        whoCoding.put("system", "generator");
        whoCoding.put("display", "TechByDesign");
        agent.put("who", Collections.singletonMap("coding", List.of(whoCoding)));
        newProvenance.put("agent", List.of(agent));
        List<Map<String, Object>> provenanceList = new ArrayList<>();
        provenanceList.add(existingProvenance);
        provenanceList.add(newProvenance);
        return Configuration.objectMapper.writeValueAsString(provenanceList);
    }

    public void addHapiFhirValidation(Map<String, Object> provenance, String validationDescription) {
        @SuppressWarnings("unchecked")
        Map<String, Object> agent = new HashMap<>();
        Map<String, String> whoCoding = new HashMap<>();

        whoCoding.put("system", "Validator");
        whoCoding.put("display", "HAPI FHIR Validation");

        agent.put("who", Collections.singletonMap("coding", List.of(whoCoding)));
        provenance.put("hapiFhirValidation", Map.of(
                "agent", List.of(agent),
                "description", validationDescription));
    }

    private void saveConvertedFHIR(boolean isValid, String masterInteractionId, String groupKey,
            String groupInteractionId,String interactionId, final HttpServletRequest request,
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
            initRIHR.setInteractionId(interactionId);
            initRIHR.setGroupHubInteractionId(groupInteractionId);
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

    private List<Object> processScreening(String groupKey,
            final Map<String, List<DemographicData>> demographicData,
            final Map<String, List<ScreeningProfileData>> screeningProfileData,
            final Map<String, List<QeAdminData>> qeAdminData,
            final Map<String, List<ScreeningObservationData>> screeningObservationData,
            HttpServletRequest request,
            HttpServletResponse response,
            String groupInteractionId,
            String masterInteractionId,
            String tenantId, boolean isValid, PayloadAndValidationOutcome payloadAndValidationOutcome)
            throws IOException {

        List<Object> results = new ArrayList<>();

        screeningProfileData.forEach((encounterId, profileList) -> {
            for (ScreeningProfileData profile : profileList) {
                final String interactionId = UUID.randomUUID().toString();
                try {
                    final List<DemographicData> demographicList = demographicData.getOrDefault(
                            profile.getPatientMrIdValue(),
                            List.of());
                    final List<QeAdminData> qeAdminList = qeAdminData.getOrDefault(profile.getPatientMrIdValue(),
                            List.of());
                    final List<ScreeningObservationData> screeningObservationList = screeningObservationData
                            .getOrDefault(profile.getEncounterId(), List.of());

                    if (demographicList.isEmpty() || qeAdminList.isEmpty() || screeningObservationList.isEmpty()) {
                        String errorMessage = String.format(
                                "Data missing in one or more files for patientMrIdValue: %s",
                                profile.getPatientMrIdValue());
                        LOG.error(errorMessage);
                        throw new IllegalArgumentException(errorMessage);
                    }
                    Instant initiatedAt = Instant.now();
                    final var bundle = csvToFhirConverter.convert(
                            demographicList.get(0),
                            qeAdminList.get(0),
                            profile,
                            screeningObservationList,
                            interactionId);
                    Instant completedAt = Instant.now();
                    if (bundle != null) {
                        String updatedProvenance = addBundleProvenance(payloadAndValidationOutcome.provenance(),
                                getFileNames(payloadAndValidationOutcome.fileDetails()),
                                profile.getPatientMrIdValue(), profile.getEncounterId(), initiatedAt, completedAt);
                        saveConvertedFHIR(isValid, masterInteractionId, groupKey, groupInteractionId,interactionId, request,
                                bundle, tenantId);
                        results.add(fhirService.processBundle(
                                bundle, tenantId, null, null, null, null, null,
                                Boolean.toString(false), false,
                                false, false,
                                request, response,
                                updatedProvenance,
                                true, null, interactionId, groupInteractionId,
                                masterInteractionId, SourceType.CSV.name(),null));
                    } else {
                        results.add(createOperationOutcomeForError(masterInteractionId, interactionId,
                                profile.getPatientMrIdValue(), profile.getEncounterId(),
                                new Exception("Bundle not created"),
                                payloadAndValidationOutcome.provenance()));
                    }
                } catch (final Exception e) {
                    LOG.error(String.format(
                            "Error processing patient data for MrId: %s, interactionId: %s, Error: %s",
                            profile.getPatientMrIdValue(), interactionId, e.getMessage()), e);
                    results.add(createOperationOutcomeForError(masterInteractionId, interactionId,
                            profile.getPatientMrIdValue(), profile.getEncounterId(), e,
                            payloadAndValidationOutcome.provenance()));
                }
            }
        });

        return results;
    }

    public static List<String> getFileNames(List<FileDetail> fileDetails) {
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
            Map<String, Object> provenance) {
        OperationOutcome operationOutcome = new OperationOutcome();
        OperationOutcome.OperationOutcomeIssueComponent issue = operationOutcome.addIssue();
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(OperationOutcome.IssueType.EXCEPTION);
        issue.setDiagnostics(
                "Error processing data for Master Interaction ID: " + masterInteractionId +
                        ", Interaction ID: " + groupInteractionId +
                        ", Patient MRN: " + patientMrIdValue +
                        ", EncounterID : " + encounterId +
                        ", Error: " + e.getMessage());

        // Construct the desired structure with validationResults
        return Map.of(
                "masterInteractionId", masterInteractionId,
                "groupInteractionId", groupInteractionId,
                "patientMrId", patientMrIdValue,
                "encounterId", encounterId,
                "provenance", provenance,
                "validationResults", Map.of(
                        "issue", List.of(Map.of(
                                "severity", "ERROR",
                                "code", "EXCEPTION",
                                "diagnostics",
                                "Error processing data for Master Interaction ID: " + masterInteractionId +
                                        ", Interaction ID: " + groupInteractionId +
                                        ", Patient MRN: " + patientMrIdValue +
                                        ", EncounterID : " + encounterId +
                                        ", Error: " + e.getMessage())),
                        "resourceType", "OperationOutcome"));
    }

    private Map<String, Object> createOperationOutcomeForFileNotProcessed(
            final String masterInteractionId,
            final List<String> filesNotProcessed, String originalFileName) {
        OperationOutcome operationOutcome = new OperationOutcome();
        OperationOutcome.OperationOutcomeIssueComponent issue = operationOutcome.addIssue();
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(OperationOutcome.IssueType.NOTFOUND);

        StringBuilder diagnosticsMessage = new StringBuilder();

        if (filesNotProcessed != null && !filesNotProcessed.isEmpty()) {
            diagnosticsMessage.append("Files not processed: in input zip file : ");
            diagnosticsMessage.append(String.join(", ", filesNotProcessed));
            StringBuilder remediation = new StringBuilder();
            remediation.append("Filenames must start with one of the following prefixes: ");
            for (FileType type : FileType.values()) {
                remediation.append(type.name()).append(", ");
            }
            if (remediation.length() > 0) {
                remediation.setLength(remediation.length() - 2);
            }
            Map<String, Object> issueDetails = Map.of(
                    "severity", "ERROR",
                    "code", "NOTFOUND",
                    "diagnostics", diagnosticsMessage.toString(),
                    "remediation", remediation.toString());

            return Map.of(
                    "masterInteractionId", masterInteractionId,
                    "originalFileName", originalFileName,
                    "validationResults", Map.of(
                            "issue", List.of(issueDetails),
                            "resourceType", "OperationOutcome"));
        }
        return Collections.emptyMap();
    }
}
