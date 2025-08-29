package org.techbd.controller.http.hub.prime.api;

import org.jooq.DSLContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.techbd.service.http.InteractionsFilter;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class CheckRoleController {

  private final DSLContext dsl;
      private static final Logger LOG = LoggerFactory.getLogger(InteractionsFilter.class.getName());

 public CheckRoleController(DSLContext dsl) {
     this.dsl = dsl;}

     @GetMapping("/db-role")
    public Map<String, Object> getCurrentDbRole() {
        LOG.info("Received request to check current DB role");

        String sql = "SELECT current_user, session_user, current_role";
        LOG.debug("Executing SQL: {}", sql);

        Record record = dsl.fetchOne(sql);

        if (record != null) {
            Map<String, Object> result = record.intoMap();
            LOG.info("DB role query result: {}", result);
            return result;
        } else {
            LOG.warn("DB role query returned no result");
            return Map.of("error", "No result returned");
        }
    }
}