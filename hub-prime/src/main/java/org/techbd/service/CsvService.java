package org.techbd.service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.conf.Configuration;
import org.techbd.model.csv.*;
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
import java.util.Map;
@Service
public class CsvService {

    private final CsvOrchestrationEngine engine;
    private static final Logger LOG = LoggerFactory.getLogger(CsvService.class);
    @Value("${org.techbd.service.http.interactions.saveUserDataToInteractions:true}")
    private boolean saveUserDataToInteractions;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;
    private final CsvBundleProcessorService csvBundleProcessorService;

    public CsvService(final CsvOrchestrationEngine engine, final UdiPrimeJpaConfig udiPrimeJpaConfig,
            final CsvBundleProcessorService csvBundleProcessorService) {
        this.engine = engine;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
        this.csvBundleProcessorService = csvBundleProcessorService;
    }

    public Object validateCsvFile(final MultipartFile file, final HttpServletRequest request,
            final HttpServletResponse response,
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

    private void saveArchiveInteraction(final org.jooq.Configuration jooqCfg, final HttpServletRequest request,
            final MultipartFile file,
            final String tenantId) {
        final var interactionId = getBundleInteractionId(request);
        LOG.info("REGISTER State NONE : BEGIN for inteaction id  : {} tenant id : {}",
                interactionId, tenantId);
        final var forwardedAt = OffsetDateTime.now();
        final var initRIHR = new RegisterInteractionHttpRequest();
        try {
            initRIHR.setOrigin("http");
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
    public List<Object> processZipFile(final MultipartFile file,final HttpServletRequest request ,HttpServletResponse response ,final String tenantId) throws Exception {
        CsvOrchestrationEngine.OrchestrationSession session = null;
        try {
            final var dslContext = udiPrimeJpaConfig.dsl();
            final var jooqCfg = dslContext.configuration();
            saveArchiveInteraction(jooqCfg, request, file, tenantId);
            final String masterInteractionId = getBundleInteractionId(request);
            session = engine.session()
                    .withMasterInteractionId(masterInteractionId)
                    .withSessionId(UUID.randomUUID().toString())
                    .withTenantId(tenantId)
                    .withGenerateBundle(true)
                    .withFile(file)
                    .withRequest(request)
                    .build();
            engine.orchestrate(session);
            return csvBundleProcessorService.processPayload(masterInteractionId,
            session.getPayloadAndValidationOutcomes(), request,
             response);
        } catch (final Exception ex) {
            LOG.error("Exception while processing file : {} ", file.getOriginalFilename(), ex);
        } finally {
            if (null == session) {
                engine.clear(session);
            }
        }
        return null;
       
    }
    
}
