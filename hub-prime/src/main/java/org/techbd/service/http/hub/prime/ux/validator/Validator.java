package org.techbd.service.http.hub.prime.ux.validator;

import org.jooq.Configuration;

public interface Validator {
    boolean isValid(String columnName, String columnValue, Configuration jooqConfig);
}
