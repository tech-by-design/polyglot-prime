package org.techbd.service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.conf.Configuration;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.orchestrate.csv.CsvOrchestrationEngine;
import org.techbd.service.converters.csv.CsvToFhirConverter;
import org.techbd.service.http.Interactions;
import org.techbd.service.http.InteractionsFilter;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;
import org.techbd.util.CsvConversionUtil;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service

public class CsvService {

    private final CsvOrchestrationEngine engine;
    private static final Logger LOG = LoggerFactory.getLogger(CsvService.class);
   @Value("${org.techbd.service.http.interactions.saveUserDataToInteractions:true}")
    private boolean saveUserDataToInteractions;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;
    private final CsvToFhirConverter csvToFhirConverter;

    public CsvService ( final CsvOrchestrationEngine engine,final UdiPrimeJpaConfig udiPrimeJpaConfig,CsvToFhirConverter csvToFhirConverter) {
        this.engine =engine;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
        this.csvToFhirConverter = csvToFhirConverter;
    }
    
    public Object validateCsvFile(final MultipartFile file, final HttpServletRequest request, final HttpServletResponse response,
            final String tenantId) throws Exception {
        CsvOrchestrationEngine.OrchestrationSession session = null;
        try {
            final var dslContext = udiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            saveArchiveInteraction(jooqCfg, request, file, tenantId);
            session = engine.session()
                    .withMasterInteractionId(getBundleInteractionId(request))
                    .withSessionId(UUID.randomUUID().toString())
                    .withTenantId(tenantId)
                    .withFile(file)
                    .withRequest(request)
                    .build();
            engine.orchestrate(session);
            return session.getValidationResults();
        } catch (final Exception ex) {
            LOG.error("Exception while processing file : {} ", file.getOriginalFilename(), ex);
        } finally {
            if (null == session) {
                engine.clear(session);
            }
        }
        return null;
    }

    private String getBundleInteractionId(final HttpServletRequest request) {
        return InteractionsFilter.getActiveRequestEnc(request).requestId()
                .toString();
    }
    
    private void saveArchiveInteraction(final org.jooq.Configuration jooqCfg, final HttpServletRequest request, final MultipartFile file,
            final String tenantId) {
        final var interactionId = getBundleInteractionId(request);
        LOG.info("REGISTER State NONE : BEGIN for inteaction id  : {} tenant id : {}",
                interactionId, tenantId);
        final var forwardedAt = OffsetDateTime.now();
        final var initRIHR = new RegisterInteractionHttpRequest();
        try {
            initRIHR.setInteractionId(interactionId);
            initRIHR.setInteractionKey(request.getRequestURI());
            initRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                    Map.of("nature", "Original CSV Zip Archive", "tenant_id",
                            tenantId)));
            initRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
            initRIHR.setCsvZipFileContent(file.getBytes());
            initRIHR.setCsvZipFileName(file.getOriginalFilename());
            initRIHR.setCreatedAt(forwardedAt);
            initRIHR.setCreatedBy(CsvService.class.getName());
            final var provenance = "%s.saveArchiveInteraction".formatted(CsvService.class.getName());
            initRIHR.setProvenance(provenance);
            initRIHR.setCsvGroupId(interactionId);
            if (saveUserDataToInteractions) {
                Interactions.setUserDetails(initRIHR, request);
            }
            final var start = Instant.now();
            final var execResult = initRIHR.execute(jooqCfg);
            final var end = Instant.now();
            LOG.info(
                    "REGISTER State NONE : END for interaction id : {} tenant id : {} .Time taken : {} milliseconds"
                            + execResult,
                    interactionId, tenantId,
                    Duration.between(start, end).toMillis());
        } catch (final Exception e) {
            LOG.error("ERROR:: REGISTER State NONE CALL for interaction id : {} tenant id : {}"
                    + initRIHR.getName() + " initRIHR error", interactionId,
                    tenantId,
                    e);
        }
    }

     /**
     * Processes a Zip file uploaded as a MultipartFile and extracts data into
     * corresponding lists.
     *
     * @param file The uploaded zip file (MultipartFile).
     * @throws Exception If an error occurs during processing the zip file or CSV
     *                   parsing.
     */
    public void processZipFile(MultipartFile file) throws Exception {
        String qeAdminDataStr = getQeAdminCsvData(); //TODO - integrate vfs to unzip and read actual data
        String screeningDataStr = getScreeningCsv(); //TODO - integrate vfs to unzip and read actual data
        String demographicDataStr = getDemographicData();//TODO - integrate vfs to unzip and read actual data
        String screeningResourceDataStr = getScreeningResourceData(); //TODO -integrate vfx to unzip and read actual data
        List<DemographicData> demographicData = CsvConversionUtil.convertCsvStringToDemographicData(demographicDataStr);
        List<ScreeningObservationData> screeningData = CsvConversionUtil.convertCsvStringToScreeningData(screeningDataStr);
        List<QeAdminData> qeAdminData = CsvConversionUtil.convertCsvStringToQeAdminData(qeAdminDataStr);
        List<ScreeningProfileData> screeningResourceData = CsvConversionUtil.convertCsvStringToScreeningResourceData(screeningResourceDataStr);
        if (CollectionUtils.isEmpty(demographicData) || CollectionUtils.isEmpty(screeningData) || CollectionUtils.isEmpty(qeAdminData)) {
            throw new IllegalArgumentException("Invalid Zip File"); //TODO later change with custom exception
        }
        csvToFhirConverter.convert(demographicData.get(0),screeningData,qeAdminData.get(0), screeningResourceData.get(0),"int-id");//TODO -pass interaction id
    }
    private String getQeAdminCsvData() {
        return """
                PAT_MRN_ID|FACILITY_ID|FACILITY_LONG_NAME|ORGANIZATION_TYPE|FACILITY_ADDRESS1|FACILITY_ADDRESS2|FACILITY_CITY|FACILITY_STATE|FACILITY_ZIP|VISIT_PART_2_FLAG|VISIT_OMH_FLAG|VISIT_OPWDD_FLAG
                qcs-test-20240603-testcase4-MRN|CNYSCN|Crossroads NY Social Care Network|SCN|25 W 45th st|Suite 16|New York|New York|10036|No|No|No
                """;
    }

    private String getScreeningCsv() {
        return "PATIENT_MR_ID|FACILITY_ID|ENCOUNTER_ID|ENCOUNTER_CLASS_CODE|ENCOUNTER_CLASS_CODE_DESCRIPTION|ENCOUNTER_CLASS_CODE_SYSTEM|ENCOUNTER_STATUS_CODE|ENCOUNTER_STATUS_CODE_DESCRIPTION|ENCOUNTER_STATUS_CODE_SYSTEM|ENCOUNTER_TYPE_CODE|ENCOUNTER_TYPE_CODE_DESCRIPTION|ENCOUNTER_TYPE_CODE_SYSTEM|ENCOUNTER_START_TIME|ENCOUNTER_END_TIME|ENCOUNTER_LAST_UPDATED|LOCATION_NAME|LOCATION_STATUS|LOCATION_TYPE_CODE|LOCATION_TYPE_SYSTEM|LOCATION_ADDRESS1|LOCATION_ADDRESS2|LOCATION_CITY|LOCATION_DISTRICT|LOCATION_STATE|LOCATION_ZIP|LOCATION_PHYSICAL_TYPE_CODE|LOCATION_PHYSICAL_TYPE_SYSTEM|SCREENING_STATUS_CODE|SCREENING_CODE|SCREENING_CODE_DESCRIPTION|SCREENING_CODE_SYSTEM_NAME|RECORDED_TIME|QUESTION_CODE|QUESTION_CODE_DESCRIPTION|QUESTION_CODE_SYSTEM_NAME|UCUM_UNITS|SDOH_DOMAIN|PARENT_QUESTION_CODE|ANSWER_CODE|ANSWER_CODE_DESCRIPTION|ANSWER_CODE_SYSTEM_NAME\n" +
               "11223344|CUMC|EncounterExample|FLD|field|http://terminology.hl7.org/CodeSystem/v3-ActCode|finished|Finished|http://terminology.hl7.org/CodeSystem/v3-ActCode|405672008|Direct questioning (procedure)|http://snomed.info/sct|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|downtown location|active|CSC|http://terminology.hl7.org/CodeSystem/v3-RoleCode|115 Broadway Suite #1601||New York|MANHATTAN|NY|10006|bu|http://terminology.hl7.org/CodeSystem/location-physical-type|unknown|96777-8|Accountable health communities (AHC) health-related social needs screening (HRSN) tool|http://loinc.org|2023-07-12T16:08:00.000Z|71802-3|What is your living situation today?|http://loinc.org||Homelessness, Housing Instability||LA31993-1|I have a steady place to live|http://loinc.org\n" +
               "11223344|CUMC|EncounterExample|FLD|field|http://terminology.hl7.org/CodeSystem/v3-ActCode|finished|Finished|http://terminology.hl7.org/CodeSystem/v3-ActCode|405672008|Direct questioning (procedure)|http://snomed.info/sct|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|downtown location|active|CSC|http://terminology.hl7.org/CodeSystem/v3-RoleCode|116 Broadway Suite #1601||New York|MANHATTAN|NY|10007|bu|http://terminology.hl7.org/CodeSystem/location-physical-type|unknown|96777-8|Accountable health communities (AHC) health-related social needs screening (HRSN) tool|http://loinc.org|2023-07-12T16:08:00.000Z|96778-6|Think about the place you live. Do you have problems with any of the following?|http://loinc.org||Inadequate Housing||LA28580-1|Mold|http://loinc.org";
    }
    
    private String getDemographicData() {
        return "PATIENT_MR_ID|FACILITY_ID|CONSENT_STATUS|CONSENT_TIME|GIVEN_NAME|MIDDLE_NAME|FAMILY_NAME|GENDER|SEX_AT_BIRTH_CODE|SEX_AT_BIRTH_CODE_DESCRIPTION|SEX_AT_BIRTH_CODE_SYSTEM|PATIENT_BIRTH_DATE|ADDRESS1|ADDRESS2|CITY|DISTRICT|STATE|ZIP|PHONE|SSN|PERSONAL_PRONOUNS_CODE|PERSONAL_PRONOUNS_CODE_DESCRIPTION|PERSONAL_PRONOUNS_CODE_SYSTEM_NAME|GENDER_IDENTITY_CODE|GENDER_IDENTITY_CODE_DESCRIPTION|GENDER_IDENTITY_CODE_SYSTEM_NAME|SEXUAL_ORIENTATION_CODE|SEXUAL_ORIENTATION_CODE_DESCRIPTION|SEXUAL_ORIENTATION_CODE_SYSTEM_NAME|PREFERRED_LANGUAGE_CODE|PREFERRED_LANGUAGE_CODE_DESCRIPTION|PREFERRED_LANGUAGE_CODE_SYSTEM_NAME|RACE_CODE|RACE_CODE_DESCRIPTION|RACE_CODE_SYSTEM_NAME|ETHNICITY_CODE|ETHNICITY_CODE_DESCRIPTION|ETHNICITY_CODE_SYSTEM_NAME|MEDICAID_CIN|PATIENT_LAST_UPDATED|RELATIONSHIP_PERSON_CODE|RELATIONSHIP_PERSON_DESCRIPTION|RELATIONSHIP_PERSON_SYSTEM|RELATIONSHIP_PERSON_GIVEN_NAME|RELATIONSHIP_PERSON_FAMILY_NAME|RELATIONSHIP_PERSON_TELECOM_SYSTEM|RELATIONSHIP_PERSON_TELECOM_VALUE\n" +
               "11223344|CUMC|active|2024-02-23T00:00:00Z|Jon|Bob|Doe|male|M|Male|http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex|1981-07-16|115 Broadway Apt2||New York|MANHATTAN|NY|10032|1234567890|999-34-2964|LA29518-0|he/him/his/his/himself|http://loinc.org|LA22878-5|Identifies as male|http://loinc.org|LA4489-6|Unknown|http://loinc.org|en|English|urn:ietf:bcp:47|2028-9|Asian|urn:oid:2.16.840.1.113883.6.238|2135-2|Hispanic or Latino|urn:oid:2.16.840.1.113883.6.238|AA12345C|2024-02-23T00:00:00.00Z|MTH|Mother|http://terminology.hl7.org/CodeSystem/v2-0063|Joyce|Doe|Phone|1234567890";
    }
    private String getScreeningResourceData() {
        return "PATIENT_MR_ID_VALUE|FACILITY_ID|CONSENT_PROFILE|CONSENT_LAST_UPDATED|CONSENT_TEXT_STATUS|CONSENT_STATUS|CONSENT_SCOPE_CODE|CONSENT_SCOPE_TEXT|CONSENT_CATEGORY_IDSCL_CODE|CONSENT_CATEGORY_IDSCL_SYSTEM|CONSENT_CATEGORY_LOINC_CODE|CONSENT_CATEGORY_LOINC_SYSTEM|CONSENT_CATEGORY_LOINC_DISPLAY|CONSENT_DATE_TIME|CONSENT_POLICY_AUTHORITY|CONSENT_PROVISION_TYPE|ENCOUNTER_ID|ENCOUNTER_CLASS_CODE|ENCOUNTER_CLASS_CODE_DESCRIPTION|ENCOUNTER_CLASS_CODE_SYSTEM|ENCOUNTER_STATUS_CODE|ENCOUNTER_STATUS_CODE_DESCRIPTION|ENCOUNTER_STATUS_CODE_SYSTEM|ENCOUNTER_TYPE_CODE|ENCOUNTER_TYPE_CODE_DESCRIPTION|ENCOUNTER_TYPE_CODE_SYSTEM|ENCOUNTER_START_TIME|ENCOUNTER_END_TIME|ENCOUNTER_LAST_UPDATED|ENCOUNTER_PROFILE|ENCOUNTER_TEXT_STATUS|LOCATION_NAME|LOCATION_STATUS|LOCATION_TYPE_CODE|LOCATION_TYPE_SYSTEM|LOCATION_ADDRESS1|LOCATION_ADDRESS2|LOCATION_CITY|LOCATION_DISTRICT|LOCATION_STATE|LOCATION_ZIP|LOCATION_PHYSICAL_TYPE_CODE|LOCATION_PHYSICAL_TYPE_SYSTEM|LOCATION_TEXT_STATUS|LOCATION_LAST_UPDATED|SCREENING_LAST_UPDATED|SCREENING_PROFILE|SCREENING_LANGUAGE|SCREENING_TEXT_STATUS|SCREENING_CODE_SYSTEM_NAME|QUESTION_CODE_SYSTEM_NAME|OBSERVATION_CATEGORY_SDOH_SYSTEM|OBSERVATION_CATEGORY_SOCIAL_HISTORY_CODE|OBSERVATION_CATEGORY_SOCIAL_HISTORY_SYSTEM|OBSERVATION_CATEGORY_SURVEY_CODE|OBSERVATION_CATEGORY_SURVEY_SYSTEM|OBSERVATION_CATEGORY_SNOMED_SYSTEM|ANSWER_CODE_SYSTEM_NAME\n"
                + "11223344|CUMC|http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-Consent|2024-02-23T00:00:00Z|generated|active|treatment|treatment|IDSCL|http://terminology.hl7.org/CodeSystem/v3-ActCode|59284-0|http://loinc.org|Patient Consent|2024-02-23T00:00:00Z|urn:uuid:d1eaac1a-22b7-4bb6-9c62-cc95d6fdf1a5|permit|EncounterExample|FLD|field|http://terminology.hl7.org/CodeSystem/v3-ActCode|finished|Finished|http://terminology.hl7.org/CodeSystem/v3-ActCode|405672008|Direct questioning (procedure)|http://snomed.info/sct|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-encounter|generated|downtown location|active|CSC|http://terminology.hl7.org/CodeSystem/v3-RoleCode|115 Broadway Suite #1601||New York|MANHATTAN|NY|10006|bu|http://terminology.hl7.org/CodeSystem/location-physical-type|generated|2024-02-23T00:00:00Z|2024-02-23T00:00:00Z|http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-observation-screening-response|en|generated|http://loinc.org|http://loinc.org|http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes|social-history|http://terminology.hl7.org/CodeSystem/observation-category|survey|http://terminology.hl7.org/CodeSystem/observation-category|http://snomed.info/sct|http://loinc.org";
    }
}
