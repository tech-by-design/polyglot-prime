package org.techbd.service.http.hub.prime.ux;

import java.util.List;
import java.util.Map;

import org.jooq.impl.DSL;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import lib.aide.sql.dynamic.ServerRowsRequest;
import lib.aide.sql.dynamic.ServerRowsResponse;
import lib.aide.sql.dynamic.SqlQueryBuilder;

@Controller
@Tag(name = "TechBD Hub UX Dynamic SQL API Endpoints")
public class DynamicSqlQueryController {
    static private final Logger LOG = LoggerFactory.getLogger(DynamicSqlQueryController.class);

    private final UdiPrimeJpaConfig udiPrimeJpaConfig;

    public DynamicSqlQueryController(final UdiPrimeJpaConfig udiPrimeJpaConfig) {
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
    }

    @Operation(summary = "SQL rows from a master table or view")
    @PostMapping(value = { "/api/ux/aggrid/{masterTableNameOrViewName}.json",
            "/api/ux/aggrid/{schemaName}/{masterTableNameOrViewName}.json" }, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ServerRowsResponse aggridContent(@PathVariable(required = false) String schemaName,
            final @PathVariable String masterTableNameOrViewName,
            final @RequestBody @Nonnull ServerRowsRequest payload,
            @RequestHeader(value = "X-Include-Generated-SQL-In-Response", required = false) boolean includeGeneratedSqlInResp,
            @RequestHeader(value = "X-Include-Generated-SQL-In-Error-Response", required = false, defaultValue = "true") boolean includeGeneratedSqlInErrorResp) {

        ServerRowsResponse.DynamicSQL fromSQL = null;

        try {
            final var DSL = udiPrimeJpaConfig.dsl();
            final var sqlQueryBuilder = new SqlQueryBuilder(DSL);

            final var query = sqlQueryBuilder.createSql(payload, schemaName, masterTableNameOrViewName);
            fromSQL = new ServerRowsResponse.DynamicSQL(query.getSQL(), query.getBindValues());

            try {
                final var result = DSL.resultQuery(query.getSQL(), query.getBindValues().toArray()).fetch();
                return ServerRowsResponse.createResponse(payload, result.intoMaps(),
                        includeGeneratedSqlInResp ? fromSQL : null, null);
            } catch (Exception reportError) {
                LOG.error("aggridContent jOOQ", reportError);
                return ServerRowsResponse.createResponse(payload, List.of(),
                        includeGeneratedSqlInErrorResp ? fromSQL : null, reportError);
            }
        } catch (Exception reportError) {
            LOG.error("aggridContent", reportError);
            return ServerRowsResponse.createResponse(payload, List.of(),
                    includeGeneratedSqlInErrorResp ? fromSQL : null, reportError);
        }
    }

    @Operation(summary = "SQL rows from a master table or view for a specific column value")
    @GetMapping("/api/ux/get-records/{schemaName}/{masterTableNameOrViewName}/{columnName}/{columnValue}.json")
    @ResponseBody
    public List<Map<String, Object>> getRecords(final @PathVariable(required = false) String schemaName,
            final @PathVariable String masterTableNameOrViewName, final @PathVariable String columnName,
            final @PathVariable String columnValue) {
        final var result = udiPrimeJpaConfig.dsl().select()
                .from(DSL.table(schemaName != null ? DSL.name(schemaName, masterTableNameOrViewName)
                        : DSL.name(masterTableNameOrViewName)))
                .where(DSL.field(DSL.name(columnName)).eq(columnValue))
                .fetch();
        return result.intoMaps();
    }
}
