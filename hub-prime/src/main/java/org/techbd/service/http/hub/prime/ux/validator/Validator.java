package org.techbd.service.http.hub.prime.ux.validator;

import java.util.Map;

import org.jooq.Configuration;
import org.springframework.stereotype.Component;

@Component
public interface Validator {
    boolean isValid(Map<String, String> rowData, Configuration jooqConfig);
    String getTableName();
}
