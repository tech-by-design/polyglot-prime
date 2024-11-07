package org.techbd.service.http.hub.prime.ux.validator;

import java.util.HashMap;
import java.util.Map;

import org.jooq.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.techbd.udi.auto.jooq.ingress.routines.IsValidJsonpath;

public class JsonPathValidator implements Validator {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(JsonPathValidator.class);

    @Override
    public boolean isValid(String columnName, String columnValue, Configuration jooqConfig) {

        if (columnName == null || columnValue == null) {
            return false;
        }

        if (columnName.equals("json_path")) {
            IsValidJsonpath isValidJsonpath = new IsValidJsonpath();

            try {
                isValidJsonpath.setJsonPath(columnValue);
                isValidJsonpath.execute(jooqConfig);
                boolean execResult = isValidJsonpath.getReturnValue();
                LOG.info("isValid - isValidJsonpath : {}", execResult);
                return execResult;
            } catch (Exception e) {
                LOG.error("Error validating JSON path: {}", e.getMessage());
                return false;
            }
        }
        return true;
    }

    public ResponseEntity<Map<String, Object>> handleInvalidJsonPath() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("message", "Invalid JSON Path.");
        return ResponseEntity.badRequest().body(responseBody);
    }

     public ResponseEntity<Map<String, Object>> handleJsonPathValidationError() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("message", "An error occurred while validating the JSON path.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseBody);
    }
    
}
