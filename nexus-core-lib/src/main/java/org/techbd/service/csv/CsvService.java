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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.config.Configuration;
import org.techbd.config.Constants;
import org.techbd.config.MirthJooqConfig;
import org.techbd.config.Origin;
import org.techbd.config.SourceType;
import org.techbd.service.csv.engine.CsvOrchestrationEngine;
import org.techbd.service.dataledger.CoreDataLedgerApiClient;
import org.techbd.service.dataledger.CoreDataLedgerApiClient.DataLedgerPayload;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterInteractionHttpRequest;

import com.fasterxml.jackson.databind.JsonNode;

import io.micrometer.common.util.StringUtils;
@Service
public class CsvService {

    private final CsvOrchestrationEngine engine;
    private static final Logger LOG = LoggerFactory.getLogger(CsvService.class);
    @Value("${org.techbd.service.http.interactions.saveUserDataToInteractions:true}")
    private boolean saveUserDataToInteractions;
    private final CsvBundleProcessorService csvBundleProcessorService;
	private final CoreDataLedgerApiClient coreDataLedgerApiClient;
    public CsvService(final CsvOrchestrationEngine engine,
            final CsvBundleProcessorService csvBundleProcessorService,CoreDataLedgerApiClient coreDataLedgerApiClient) {
        this.engine = engine;
        this.csvBundleProcessorService = csvBundleProcessorService;
        this.coreDataLedgerApiClient = coreDataLedgerApiClient;
    }

    public Object validateCsvFile(final MultipartFile file, final Map<String,String> requestParameters,
            final Map<String,String> headerParameters,Map<String,Object> resonseParameters) throws Exception {
            //         public Object validateCsvFile(final MultipartFile file, final HttpServletRequest request,
            // final HttpServletResponse response,
            // final String tenantId,String origin,String sftpSessionId) throws Exception {
        CsvOrchestrationEngine.OrchestrationSession session = null;
        try {
            final var dslContext = MirthJooqConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            final var zipFileInteractionId = requestParameters.get(Constants.INTERACTION_ID);
            saveArchiveInteraction(zipFileInteractionId,jooqCfg, requestParameters, headerParameters, file);
            session = engine.session()
                    .withMasterInteractionId(zipFileInteractionId)
                    .withSessionId(UUID.randomUUID().toString())
                    .withTenantId(requestParameters.get(Constants.TENANT_ID))
                    .withFile(file)
                    .withRequestParameters(requestParameters)
                    .withHeaderParameters(headerParameters)
                    .build();
            engine.orchestrate(session);
            return session.getValidationResults();
        } finally {
            if (null == session) {
                engine.clear(session);
            }
        }
    }

    private void saveArchiveInteraction(String zipFileInteractionId,final org.jooq.Configuration jooqCfg, final Map<String,String> requestParameters,Map<String,String> headerParameters,
    final MultipartFile file) {
        final var tenantId = requestParameters.get(Constants.TENANT_ID  );
        LOG.info("REGISTER State NONE : BEGIN for inteaction id  : {} tenant id : {}",
                zipFileInteractionId, requestParameters.get(Constants.TENANT_ID));
        final var forwardedAt = OffsetDateTime.now();
        final var initRIHR = new RegisterInteractionHttpRequest();
        try {
            initRIHR.setOrigin(null == requestParameters.get(Constants.ORIGIN) ? Origin.HTTP.name():requestParameters.get(Constants.ORIGIN));
            initRIHR.setInteractionId(zipFileInteractionId);
            initRIHR.setInteractionKey(requestParameters.get(Constants.REQUEST_URI));
            initRIHR.setNature((JsonNode) Configuration.objectMapper.valueToTree(
                    Map.of("nature", "Original CSV Zip Archive", "tenant_id",
                            tenantId)));
            initRIHR.setContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
            initRIHR.setCsvZipFileContent(file.getBytes());
            initRIHR.setCsvZipFileName(file.getOriginalFilename());
            initRIHR.setCreatedAt(forwardedAt);
            final InetAddress localHost = InetAddress.getLocalHost();
            final String ipAddress = localHost.getHostAddress();
            initRIHR.setClientIpAddress(ipAddress);
            initRIHR.setUserAgent(headerParameters.get(Constants.USER_AGENT));
            initRIHR.setCreatedBy(CsvService.class.getName());
            final var provenance = "%s.saveArchiveInteraction".formatted(CsvService.class.getName());
            initRIHR.setProvenance(provenance);
            initRIHR.setCsvGroupId(zipFileInteractionId);
            if (saveUserDataToInteractions) {
                setUserDetails(initRIHR, requestParameters);
            }
            final var start = Instant.now();
            final var execResult = initRIHR.execute(jooqCfg);
            final var end = Instant.now();
            LOG.info(
                    "REGISTER State NONE : END for interaction id : {} tenant id : {} .Time taken : {} milliseconds"
                            + execResult,
                    zipFileInteractionId, tenantId,
                    Duration.between(start, end).toMillis());
        } catch (final Exception e) {
            LOG.error("ERROR:: REGISTER State NONE CALL for interaction id : {} tenant id : {}"
                    + initRIHR.getName() + " initRIHR error", zipFileInteractionId,
                    tenantId,
                    e);
        }
    }

    private void setUserDetails(RegisterInteractionHttpRequest rihr, Map<String, String> requestParameters) {
        rihr.setUserName(StringUtils.isEmpty(requestParameters.get(Constants.USER_NAME)) ? Constants.DEFAULT_USER_NAME : requestParameters.get(Constants.USER_NAME));
        rihr.setUserId(StringUtils.isEmpty(requestParameters.get(Constants.USER_ID)) ? Constants.DEFAULT_USER_ID : requestParameters.get(Constants.USER_ID));
        rihr.setUserSession(UUID.randomUUID().toString());
        rihr.setUserRole(StringUtils.isEmpty(requestParameters.get(Constants.USER_ROLE)) ? Constants.DEFAULT_USER_ROLE : requestParameters.get(Constants.USER_ROLE));
    }    

    /**
     * Processes a Zip file uploaded as a MultipartFile and extracts data into
     * corresponding lists.
     *
     * @param file The uploaded zip file (MultipartFile).
     * @throws Exception If an error occurs during processing the zip file or CSV
     *                   parsing.
     */
    public List<Object> processZipFile(final MultipartFile file,final Map<String,String> requestParameters , Map<String,String> headerParameters, Map<String,Object> responseParameters ) throws Exception {
        // public List<Object> processZipFile(final MultipartFile file,final HttpServletRequest request ,HttpServletResponse response ,final String tenantId,String origin,String sftpSessionId,String baseFHIRUrl) throws Exception {
    
        CsvOrchestrationEngine.OrchestrationSession session = null;
        try {
            final var zipFileInteractionId = requestParameters.get(Constants.INTERACTION_ID);
            final var tenantId = requestParameters.get(Constants.TENANT_ID);

             DataLedgerPayload dataLedgerPayload = DataLedgerPayload.create(CoreDataLedgerApiClient.Actor.TECHBD.getValue(), CoreDataLedgerApiClient.Action.RECEIVED.getValue(), CoreDataLedgerApiClient.Actor.TECHBD.getValue(), zipFileInteractionId
			);
			final var dataLedgerProvenance = "%s.processZipFile".formatted(CsvService.class.getName());
            coreDataLedgerApiClient.processRequest(dataLedgerPayload,zipFileInteractionId,dataLedgerProvenance,SourceType.CSV.name(),null);
            final var dslContext = MirthJooqConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            saveArchiveInteraction(zipFileInteractionId,jooqCfg, requestParameters,headerParameters ,file);
            session = engine.session()
                    .withMasterInteractionId(zipFileInteractionId)
                    .withSessionId(UUID.randomUUID().toString())
                    .withTenantId(tenantId)
                    .withGenerateBundle(true)
                    .withFile(file)
                    .withRequestParameters(requestParameters)
                    .withHeaderParameters(headerParameters)
                    .build();
            engine.orchestrate(session);
            return csvBundleProcessorService.processPayload(zipFileInteractionId,
            session.getPayloadAndValidationOutcomes(), session.getFilesNotProcessed(),requestParameters, headerParameters,
             responseParameters,tenantId,file.getOriginalFilename(),headerParameters.get(Constants.BASE_FHIR_URL));
        } finally {
            if (null == session) {
                engine.clear(session);
            }
        }
    }
    
}
