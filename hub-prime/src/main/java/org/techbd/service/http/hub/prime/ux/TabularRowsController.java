package org.techbd.service.http.hub.prime.ux;

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
        public Object tabularRowsCustom(final @PathVariable(required = false) String schemaName,
                        final @PathVariable String masterTableNameOrViewName, final @PathVariable String columnName,
                        final @PathVariable String columnValue) {

                // Fetch the result using the dynamically determined table and column; if
                // jOOQ-generated types were found, automatic column value mapping will occur
                final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                                masterTableNameOrViewName);
                return udiPrimeJpaConfig.dsl().selectFrom(typableTable.table())
                                .where(typableTable.column(columnName).eq(columnValue))
                                .fetch()
                                .intoMaps();
        }

        @Operation(summary = "SQL rows from a master table or view for a specific column value")
        @GetMapping("/api/ux/tabular/jooq/{schemaName}/{masterTableNameOrViewName}/{columnName}/{columnValue}/{columnName2}/{columnValue2}.json")
        @ResponseBody
        public Object tabularRowsCustomWithMultipleParams(final @PathVariable(required = false) String schemaName,
                        final @PathVariable String masterTableNameOrViewName, final @PathVariable String columnName,
                        final @PathVariable String columnValue, final @PathVariable String columnName2,
                        final @PathVariable String columnValue2) {

                // Fetch the result using the dynamically determined table and column; if
                // jOOQ-generated types were found, automatic column value mapping will occur
                String columnValue2LikePattern = "%" + columnValue2 + "%";
                final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                                masterTableNameOrViewName);
                return udiPrimeJpaConfig.dsl().selectFrom(typableTable.table())
                                .where(typableTable.column(columnName).eq(columnValue)
                                                .and(typableTable.column(columnName2).like(columnValue2LikePattern)))
                                .fetch()
                                .intoMaps();
        }

}
