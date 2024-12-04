package org.techbd.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.techbd.orchestrate.csv.CsvOrchestrationEngine;
import org.techbd.service.http.InteractionsFilter;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class CsvService {

    CsvOrchestrationEngine engine;
    private static final Logger log = LoggerFactory.getLogger(CsvService.class);

    public Object validateCsvFile(MultipartFile file, HttpServletRequest request) throws Exception {
        CsvOrchestrationEngine.OrchestrationSession session = null;
        try {
            session = engine.session()
                    .withMasterInteractionId(getBundleInteractionId(request))
                    .withSessionId(UUID.randomUUID().toString())
                    .withFile(file)
                    .withRequest(request)
                    .build();
            engine.orchestrate(session);
            return session.getValidationResults();
        } catch (Exception ex) {
            log.error("Exception while processing file : {} ", file.getOriginalFilename(), ex);
        } finally {
            if (null == session) {
                engine.clear(session);
            }
        }
        return null;
    }

    private String getBundleInteractionId(HttpServletRequest request) {
        return InteractionsFilter.getActiveRequestEnc(request).requestId()
                .toString();
    }
}
