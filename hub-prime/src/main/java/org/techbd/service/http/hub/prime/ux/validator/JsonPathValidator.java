package org.techbd.service.http.hub.prime.ux.validator;

import java.util.HashMap;
import java.util.Map;

import org.jooq.Configuration;
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
    public ResponseEntity<Map<String, Object>> validate(Map<String, String> rowData, Configuration jooqConfig) {

        String jsonPath = rowData.get("json_path");
        Map<String, Object> responseBody = new HashMap<>();
        
        if (jsonPath == null) { 
            responseBody.put("message", "Invalid JSON Path.");
            return ResponseEntity.badRequest().body(responseBody);
        }

        IsValidJsonpath isValidJsonpath = new IsValidJsonpath();

        try {
            isValidJsonpath.setJsonPath(jsonPath);
            isValidJsonpath.execute(jooqConfig);
            boolean execResult = isValidJsonpath.getReturnValue();
            LOG.info("isValid - isValidJsonpath : {}", execResult);
            if(!execResult){
                responseBody.put("message", "Invalid JSON Path.");
                return ResponseEntity.badRequest().body(responseBody);
            } else{
                return ResponseEntity.noContent().build();
            }
        } catch (Exception e) {
            responseBody.put("message", "Error validating JSON path");
            return ResponseEntity.badRequest().body(responseBody);
        }
    }
    
}
