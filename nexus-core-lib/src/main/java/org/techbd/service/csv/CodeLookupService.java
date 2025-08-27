package org.techbd.service.csv;

import static org.techbd.udi.auto.jooq.ingress.Tables.REF_CODE_LOOKUP_CODE_VIEW;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.jooq.Record2;
import org.springframework.stereotype.Service;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class CodeLookupService {

    private final TemplateLogger LOG;
    private final ObjectMapper objectMapper;

    public CodeLookupService(AppLogger appLogger) {
        this.objectMapper = new ObjectMapper();
        this.LOG = appLogger.getLogger(CodeLookupService.class);
    }

    public Map<String, Map<String, String>> fetchCode(DSLContext dsl, String interactionId) {
        LOG.info("CodeLookupService::fetchCode fetching values from database - BEGIN - interaction Id: {}", interactionId);
        try {
            List<Record2<String, String>> records = dsl
                    .select(REF_CODE_LOOKUP_CODE_VIEW.CODE_TYPE, REF_CODE_LOOKUP_CODE_VIEW.CODES.cast(String.class))
                    .from(REF_CODE_LOOKUP_CODE_VIEW)
                    .fetch();
    
            Map<String, Map<String, String>> result = records.stream()
                    .collect(Collectors.toMap(
                            Record2::component1,
                            record -> {
                                List<Map<String, String>> codesList = parseJsonCodes(record.component2());
                                return codesList.stream()
                                    .filter(map -> map != null && map.get("code") != null)
                                    .collect(Collectors.toMap(
                                        map -> map.get("code").toLowerCase(),
                                        map -> map.get("code"),
                                        (existing, replacement) -> existing
                                    ));
                            },
                            (existing, replacement) -> existing
                    ));
    
            return result;
        } catch (Exception ex) {
            LOG.error("Exception during fetching values from database", ex);
        }
        LOG.info("CodeLookupService::fetchCode fetching values from database - END - interaction Id: {}", interactionId);
        return Collections.emptyMap();
    }

    public Map<String, Map<String, String>> fetchSystem(DSLContext dsl, String interactionId) {
        LOG.info("CodeLookupService::fetchSystem fetching values from database - BEGIN - interaction Id: {}", interactionId);
        try {
            List<Record2<String, String>> records = dsl
                    .select(REF_CODE_LOOKUP_CODE_VIEW.CODE_TYPE, REF_CODE_LOOKUP_CODE_VIEW.CODES.cast(String.class))
                    .from(REF_CODE_LOOKUP_CODE_VIEW)
                    .fetch();
    
            Map<String, Map<String, String>> result = records.stream()
                    .collect(Collectors.toMap(
                            Record2::component1,
                            record -> {
                                List<Map<String, String>> codesList = parseJsonCodes(record.component2());
                                return codesList.stream()
                                    .filter(map -> map != null && map.get("code") != null && map.get("system") != null)
                                    .collect(Collectors.toMap(
                                        map -> map.get("code"),
                                        map -> map.get("system"),
                                        (existing, replacement) -> existing
                                    ));
                            },
                            (existing, replacement) -> existing
                    ));
    
            return result;
        } catch (Exception ex) {
            LOG.error("Exception during fetching values from database", ex);
        }
        LOG.info("CodeLookupService::fetchSystem fetching values from database - END - interaction Id: {}", interactionId);
        return Collections.emptyMap();
    }

    public Map<String, Map<String, String>> fetchDisplay(DSLContext dsl, String interactionId) {
        LOG.info("CodeLookupService::fetchDisplay fetching values from database - BEGIN - interaction Id: {}", interactionId);
        try {
            List<Record2<String, String>> records = dsl
                    .select(REF_CODE_LOOKUP_CODE_VIEW.CODE_TYPE, REF_CODE_LOOKUP_CODE_VIEW.CODES.cast(String.class))
                    .from(REF_CODE_LOOKUP_CODE_VIEW)
                    .fetch();

            Map<String, Map<String, String>> result = records.stream()
                    .collect(Collectors.toMap(
                            Record2::component1,
                            record -> {
                                List<Map<String, String>> codesList = parseJsonCodes(record.component2());
                                return codesList.stream()
                                    .filter(map -> map != null && map.get("code") != null && map.get("display") != null)
                                    .collect(Collectors.toMap(
                                        map -> map.get("code"),
                                        map -> map.get("display"),
                                        (existing, replacement) -> existing
                                    ));
                            },
                            (existing, replacement) -> existing
                    ));

            return result;
        } catch (Exception ex) {
            LOG.error("Exception during fetching display values from database", ex);
        }
        LOG.info("CodeLookupService::fetchDisplay fetching values from database - END - interaction Id: {}", interactionId);
        return Collections.emptyMap();
    }

    private List<Map<String, String>> parseJsonCodes(String jsonString) {
        try {
            if (jsonString == null) {
                LOG.warn("Received null JSON string");
                return Collections.emptyList();
            }

            return objectMapper.readValue(
                jsonString,
                new TypeReference<List<Map<String, String>>>() {}
            );
        } catch (JsonProcessingException e) {
            LOG.error("Error parsing JSON codes: {}", e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            LOG.error("Unexpected error while parsing JSON codes: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

}
