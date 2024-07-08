package org.techbd.service.http.hub.prime.ux;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
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
import org.techbd.udi.auto.jooq.ingress.Tables;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
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

        return serverRowsResponse(
                udiPrimeJpaConfig.dsl(),
                payload,
                schemaName,
                masterTableNameOrViewName,
                includeGeneratedSqlInResp,
                includeGeneratedSqlInErrorResp,
                null // No customization needed for this endpoint
        );
    }

    @Operation(summary = "SQL rows from a master table or view for a specific column value")
    @GetMapping("/api/ux/get-records/{schemaName}/{masterTableNameOrViewName}/{columnName}/{columnValue}.json")
    @ResponseBody
    public Object getRecords(final @PathVariable(required = false) String schemaName,
            final @PathVariable String masterTableNameOrViewName, final @PathVariable String columnName,
            final @PathVariable String columnValue) {
        Table<?> table;
        Field<Object> column;

        // TODO: add PostgreSQL RLS checks to ensure invalid row access not possible

        // Attempt to find a generated table and column reference using reflection; when
        // we can use a jOOQ-generated class it means that special column types like
        // JSON will work (otherwise pure dynamic without generated jOOQ assistance may
        // treat certain columns incorrectly).
        try {
            final var staticInTableClass = Tables.class.getField(masterTableNameOrViewName.toUpperCase());
            table = (Table<?>) staticInTableClass.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // If not found, fall back to dynamic table reference
            table = DSL.table(schemaName != null ? DSL.name(schemaName, masterTableNameOrViewName)
                    : DSL.name(masterTableNameOrViewName));
        }

        try {
            final var instanceInTableRef = table.getClass().getField(columnName.toUpperCase());
            if (instanceInTableRef.get(table) instanceof org.jooq.Field<?> columnField) {
                column = columnField.coerce(Object.class);
            } else {
                column = DSL.field(DSL.name(columnName));
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            column = DSL.field(DSL.name(columnName));
        }

        // Fetch the result using the dynamically determined table and column; if
        // jOOQ-generated types were found, automatic column value mapping will occur
        final var result = udiPrimeJpaConfig.dsl().selectFrom(table)
                .where(column.eq(columnValue))
                .fetch();

        return result.intoMaps();
    }

    /**
     * Generates a ServerRowsResponse by executing a SQL query dynamically.
     *
     * @param dsl                            The DSL context to be used for the
     *                                       query.
     * @param payload                        The request payload containing the
     *                                       query details.
     * @param schemaName                     The schema name of the table or view,
     *                                       can be null.
     * @param masterTableNameOrViewName      The name of the master table or view.
     * @param includeGeneratedSqlInResp      Whether to include the generated SQL in
     *                                       the response.
     * @param includeGeneratedSqlInErrorResp Whether to include the generated SQL in
     *                                       the error response.
     * @param customizeRows                  A nullable function that accepts a list
     *                                       of rows (as maps) and allows
     *                                       modification of the row entries before
     *                                       creating the response.
     * @return A ServerRowsResponse object containing the result of the query.
     */
    public static ServerRowsResponse serverRowsResponse(@Nonnull DSLContext dsl,
            @Nonnull ServerRowsRequest ssRequest,
            @Nullable String schemaName,
            @Nonnull String masterTableNameOrViewName,
            boolean includeGeneratedSqlInResp,
            boolean includeGeneratedSqlInErrorResp,
            @Nullable Consumer<List<Map<String, Object>>> customizeRows) {
        ServerRowsResponse.DynamicSQL fromSQL = null;

        try {
            final var sqlQueryBuilder = new SqlQueryBuilder(dsl);
            Table<?> table;

            // Attempt to find a generated table reference using reflection;
            // when we can use a jOOQ-generated class it means that special
            // column types like JSON will work (otherwise pure dynamic without
            // generated jOOQ assistance may treat certain columns incorrectly).
            try {
                final var field = Tables.class.getField(masterTableNameOrViewName.toUpperCase());
                table = (Table<?>) field.get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // If not found, fall back to dynamic table reference
                table = DSL.table(schemaName != null ? DSL.name(schemaName, masterTableNameOrViewName)
                        : DSL.name(masterTableNameOrViewName));
            }

            // TODO: pass in tableReference to `createSql` otherwise it will be treated as
            // dynamic
            final var query = sqlQueryBuilder.createSql(ssRequest, schemaName, table.getName());
            fromSQL = new ServerRowsResponse.DynamicSQL(query.getSQL(), query.getBindValues());

            try {
                final var result = dsl.resultQuery(query.getSQL(), query.getBindValues().toArray()).fetch();
                final var rows = result.intoMaps();

                // Customize rows if the function is provided
                if (customizeRows != null) {
                    customizeRows.accept(rows);
                }

                return ServerRowsResponse.createResponse(ssRequest, rows,
                        includeGeneratedSqlInResp ? fromSQL : null, null);
            } catch (Exception reportError) {
                LOG.error("serverRowsResponse jOOQ", reportError);
                return ServerRowsResponse.createResponse(ssRequest, List.of(),
                        includeGeneratedSqlInErrorResp ? fromSQL : null, reportError);
            }
        } catch (Exception reportError) {
            LOG.error("serverRowsResponse", reportError);
            return ServerRowsResponse.createResponse(ssRequest, List.of(),
                    includeGeneratedSqlInErrorResp ? fromSQL : null, reportError);
        }
    }
}
