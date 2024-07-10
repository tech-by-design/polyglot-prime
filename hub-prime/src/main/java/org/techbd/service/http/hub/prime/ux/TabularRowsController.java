package org.techbd.service.http.hub.prime.ux;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.techbd.udi.UdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.Tables;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import lib.aide.tabular.JooqRowsSupplier;
import lib.aide.tabular.TabularRowsRequest;
import lib.aide.tabular.TabularRowsResponse;

@Controller
@Tag(name = "TechBD Hub Tabular Row API Endpoints for AG Grid")
public class TabularRowsController {
    static private final Logger LOG = LoggerFactory.getLogger(TabularRowsController.class);

    private final UdiPrimeJpaConfig udiPrimeJpaConfig;

    public TabularRowsController(final UdiPrimeJpaConfig udiPrimeJpaConfig) {
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
    }

    @Operation(summary = "SQL rows from a master table or view")
    @PostMapping(value = { "/api/ux/tabular/jooq/{masterTableNameOrViewName}.json",
            "/api/ux/tabular/jooq/{schemaName}/{masterTableNameOrViewName}.json" }, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public TabularRowsResponse<?> tabularRows(@PathVariable(required = false) String schemaName,
            final @PathVariable String masterTableNameOrViewName,
            final @RequestBody @Nonnull TabularRowsRequest payload,
            @RequestHeader(value = "X-Include-Generated-SQL-In-Response", required = false) boolean includeGeneratedSqlInResp,
            @RequestHeader(value = "X-Include-Generated-SQL-In-Error-Response", required = false, defaultValue = "true") boolean includeGeneratedSqlInErrorResp) {

        return new JooqRowsSupplier.Builder()
                .withRequest(payload)
                .withTable(Tables.class, schemaName, masterTableNameOrViewName)
                .withDSL(udiPrimeJpaConfig.dsl())
                .withLogger(LOG)
                .includeGeneratedSqlInResp(includeGeneratedSqlInResp)
                .includeGeneratedSqlInErrorResp(includeGeneratedSqlInErrorResp)
                .build()
                .response();
    }

    @Operation(summary = "SQL rows from a master table or view for a specific column value")
    @GetMapping("/api/ux/tabular/jooq/{schemaName}/{masterTableNameOrViewName}/{columnName}/{columnValue}.json")
    @ResponseBody
    public List<Map<String, Object>> tabularRowsByColValue(final @PathVariable(required = false) String schemaName,
            final @PathVariable String masterTableNameOrViewName, final @PathVariable String columnName,
            final @PathVariable String columnValue) throws JsonMappingException, JsonProcessingException {

        final var jrs = new JooqRowsSupplier.Builder()
                .withRequest(new TabularRowsRequest(0, 100,
                        Map.of(columnName, new TabularRowsRequest.FilterModel("equals", columnValue)), null, null,
                        false, null,
                        null, null, null, null, null))
                .withTable(Tables.class, schemaName, masterTableNameOrViewName)
                .withDSL(udiPrimeJpaConfig.dsl())
                .withLogger(LOG)
                .build();

        final var response = jrs.response();
        if (!jrs.isStronglyTyped()) {
            LOG.warn(
                    "tabularRowsByColValue expected strongly typed %s.%s.%s (%s) but table was not in Tables registry so using untyped jOOQ table. This may result in unmapped JSON or other column mapping issues."
                            .formatted(schemaName, masterTableNameOrViewName, columnName, columnValue));
        }

        // TODO: ***** IMPORTANT *****
        if (jrs.table() instanceof org.techbd.udi.auto.jooq.ingress.tables.InteractionHttpRequest table) {
            // figure out why, even though the instance of table is correct (strongly
            // typed), the `payload` is coming out as JSONB and is being printed to STDOUT
            // but not making its way to the user agent as JSON
            System.out.println(table.PAYLOAD);
            System.out.println(response.data().get(0).get("payload").toString().length());

            // already tried using Configuration.objectMapper.writeValueAsString(response.data())
        }

        return response.data();       
    }
}
