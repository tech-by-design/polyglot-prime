package org.techbd.service.http.hub.prime.ux.validator;

import java.util.HashMap;
import java.util.Map;

import org.jooq.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.techbd.udi.auto.jooq.ingress.routines.IsValidJsonpath;

@Component
public class JsonPathValidator implements Validator {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(JsonPathValidator.class);

    @Override
    public String getTableName() {
        return "json_action_rule";
    }

    @Override
    public boolean isValid(Map<String, String> rowData, Configuration jooqConfig) {

        String jsonPath = rowData.get("json_path");
        
        if (jsonPath == null) {
            return false;
        }

        IsValidJsonpath isValidJsonpath = new IsValidJsonpath();

        try {
            isValidJsonpath.setJsonPath(jsonPath);
            isValidJsonpath.execute(jooqConfig);
            boolean execResult = isValidJsonpath.getReturnValue();
            LOG.info("isValid - isValidJsonpath : {}", execResult);
            return execResult;
        } catch (Exception e) {
            LOG.error("Error validating JSON path: {}", e.getMessage());
            return false;
        }
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
