package org.techbd.service.converters.shinny;

import static org.techbd.udi.auto.jooq.ingress.Tables.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.DSLContext;
import org.jooq.Record2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.techbd.util.YamlUtil;

@Service
public class CodeLookupService {


    private static final Logger LOG = LoggerFactory.getLogger(YamlUtil.class.getName());

    public static Map<String, Map<String, String>> getCodeResourceFromDb(DSLContext dsl) {
        LOG.info("YamlUtil::getCodeResourceFromDb fetching values from database - BEGIN");
        try {
            List<Record2<String, String>> records = dsl
                    .select(REF_CODE_LOOKUP_CODE_VIEW.CODE_TYPE, REF_CODE_LOOKUP_CODE_VIEW.CODES)
                    .from(REF_CODE_LOOKUP_CODE_VIEW)
                    .fetch();
    
            Map<String, Map<String, String>> result = records.stream()
                    .collect(Collectors.toMap(
                            Record2::component1,
                            record -> parseCommaSeparatedValues(record.component2()).stream()
                                    .collect(Collectors.toMap(
                                            val -> val.toLowerCase(),
                                            val -> val,
                                            (existing, replacement) -> existing
                                    ))
                    ));
    
            return result;
        } catch (Exception ex) {
            LOG.error("Exception during fetching values from database", ex);
        }
        LOG.info("YamlUtil::getCodeResourceFromDb fetching values from database - END");
        return Collections.emptyMap();
    }

    public static Map<String, Map<String, String>> getSystemResourceFromDb(DSLContext dsl) {
        LOG.info("YamlUtil::getSystemResourceFromDb fetching values from database - BEGIN");
        try {
            List<Record2<String, String>> records = dsl
                    .select(REF_CODE_LOOKUP_SYSTEM_VIEW.CODE_TYPE, REF_CODE_LOOKUP_SYSTEM_VIEW.SYSTEM_VALUES)
                    .from(REF_CODE_LOOKUP_SYSTEM_VIEW)
                    .fetch();
    
            Map<String, Map<String, String>> result = records.stream()
                    .collect(Collectors.toMap(
                            Record2::component1,
                            record -> parseCommaSeparatedValues(record.component2()).stream()
                                    .collect(Collectors.toMap(
                                            val -> val.toLowerCase(),
                                            val -> val,
                                            (existing, replacement) -> existing
                                    ))
                    ));
    
            return result;
        } catch (Exception ex) {
            LOG.error("Exception during fetching values from database", ex);
        }
        LOG.info("YamlUtil::getSystemResourceFromDb fetching values from database - END");
        return Collections.emptyMap();
    }

    private static List<String> parseCommaSeparatedValues(Object value) {
        if (value instanceof String) {
            return Stream.of(((String) value).split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        } else if (value instanceof List<?>) {
            return ((List<?>) value).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

}
