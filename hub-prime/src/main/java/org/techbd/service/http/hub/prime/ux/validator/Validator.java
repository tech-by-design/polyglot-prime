package org.techbd.service.http.hub.prime.ux.validator;

import java.util.Map;

import org.jooq.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public interface Validator {
    ResponseEntity<Map<String, Object>> validate(Map<String, String> rowData, Configuration jooqConfig);
    String getTableName();
}
