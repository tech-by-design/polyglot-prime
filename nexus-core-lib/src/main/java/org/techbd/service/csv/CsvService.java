package org.techbd.service.csv;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.CoreUdiPrimeJpaConfig;
import org.techbd.config.Nature;
import org.techbd.config.Origin;
import org.techbd.config.SourceType;
import org.techbd.config.State;
import org.techbd.service.csv.engine.CsvOrchestrationEngine;
import org.techbd.service.dataledger.CoreDataLedgerApiClient;
import org.techbd.service.dataledger.CoreDataLedgerApiClient.DataLedgerPayload;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionCsvRequest;
import org.techbd.util.fhir.CoreFHIRUtil;

import com.fasterxml.jackson.databind.JsonNode;
@Service
public class CsvService {

    private final CsvOrchestrationEngine engine;
    private static final Logger LOG = LoggerFactory.getLogger(CsvService.class);
    private final CsvBundleProcessorService csvBundleProcessorService;
	private final CoreDataLedgerApiClient coreDataLedgerApiClient;
    private final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig;
    public CsvService(final CsvOrchestrationEngine engine,
            final CsvBundleProcessorService csvBundleProcessorService,CoreDataLedgerApiClient coreDataLedgerApiClient,
            final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig) {
        this.engine = engine;
        this.csvBundleProcessorService = csvBundleProcessorService;
        this.coreDataLedgerApiClient = coreDataLedgerApiClient;
        this.coreUdiPrimeJpaConfig = coreUdiPrimeJpaConfig;
    }

    public Object validateCsvFile(final MultipartFile file, final Map<String,Object> requestParameters,
        Map<String,Object> resonseParameters) throws Exception {
            //         public Object validateCsvFile(final MultipartFile file, final HttpServletRequest request,
            // final HttpServletResponse response,
            // final String tenantId,String origin,String sftpSessionId) throws Exception {
        final var zipFileInteractionId = (String) requestParameters.get(Constants.MASTER_INTERACTION_ID);
        LOG.info("CsvService validateCsvFile BEGIN zip File interaction id  : {} tenant id : {}",
                zipFileInteractionId, requestParameters.get(Constants.TENANT_ID));
        CsvOrchestrationEngine.OrchestrationSession session = null;
        try {
            final var dslContext = coreUdiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            
            saveArchiveInteraction(zipFileInteractionId,jooqCfg, requestParameters, file);
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
            return session.getValidationResults();
        } finally {
            if (null == session) {
                engine.clear(session);
            }
        }
    }

    private void saveArchiveInteraction(String zipFileInteractionId, final org.jooq.Configuration jooqCfg,
            final Map<String, Object> requestParameters,
            final MultipartFile file) {
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

    /**
     * Processes a Zip file uploaded as a MultipartFile and extracts data into
     * corresponding lists.
     *
     * @param file The uploaded zip file (MultipartFile).
     * @throws Exception If an error occurs during processing the zip file or CSV
     *                   parsing.
     */
    public List<Object> processZipFile(final MultipartFile file,final Map<String,Object> requestParameters , Map<String,Object> responseParameters ) throws Exception {
        // public List<Object> processZipFile(final MultipartFile file,final HttpServletRequest request ,HttpServletResponse response ,final String tenantId,String origin,String sftpSessionId,String baseFHIRUrl) throws Exception {
        final var zipFileInteractionId = (String) requestParameters.get(Constants.MASTER_INTERACTION_ID);
        final var tenantId = (String) requestParameters.get(Constants.TENANT_ID);
        LOG.info("CsvService processZipFile  -BEGIN zipFileInteractionId  : {} tenant id : {}",
                zipFileInteractionId, tenantId);
        CsvOrchestrationEngine.OrchestrationSession session = null;
        try {     
             DataLedgerPayload dataLedgerPayload = DataLedgerPayload.create(CoreDataLedgerApiClient.Actor.TECHBD.getValue(), CoreDataLedgerApiClient.Action.RECEIVED.getValue(), CoreDataLedgerApiClient.Actor.TECHBD.getValue(), zipFileInteractionId
			);
			final var dataLedgerProvenance = "%s.processZipFile".formatted(CsvService.class.getName());
            coreDataLedgerApiClient.processRequest(dataLedgerPayload,zipFileInteractionId,dataLedgerProvenance,SourceType.CSV.name(),null);
            final var dslContext = coreUdiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            saveArchiveInteraction(zipFileInteractionId,jooqCfg, requestParameters,file);
            session = engine.session()
                    .withMasterInteractionId(zipFileInteractionId)
                    .withSessionId(UUID.randomUUID().toString())
                    .withTenantId(tenantId)
                    .withGenerateBundle(true)
                    .withFile(file)
                    .withRequestParameters(requestParameters)
                    .build();
            engine.orchestrate(session);
            return csvBundleProcessorService.processPayload(zipFileInteractionId,
            session.getPayloadAndValidationOutcomes(), session.getFilesNotProcessed(),requestParameters,
             responseParameters,tenantId,file.getOriginalFilename(),(String) requestParameters.get(Constants.BASE_FHIR_URL));
        } finally {
            LOG.info("CsvService processZipFile  -END zipFileInteractionId  : {} tenant id : {}",
                zipFileInteractionId, tenantId);
            if (null == session) {
                engine.clear(session);
            }
        }
    }
    
}
