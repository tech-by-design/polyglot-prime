package org.techbd.service.http.hub.prime.ux;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nonnull;
import lib.aide.tabular.JooqRowsSupplier;
import lib.aide.tabular.TabularRowsRequest;
import lib.aide.tabular.TabularRowsResponse;

@Controller
@Tag(name = "Tech by Design Hub Tabular Row API Endpoints for AG Grid")
public class TabularRowsController {

    private static final Logger LOG = LoggerFactory.getLogger(TabularRowsController.class);

    private static final Pattern VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final UdiPrimeJpaConfig udiPrimeJpaConfig;

    public TabularRowsController(final UdiPrimeJpaConfig udiPrimeJpaConfig) {
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
    }

    @Operation(summary = "Fetch SQL rows from a master table or view with schema specification", description = """
            Retrieves rows from a specified master table or view, within a specific schema.
            The request body contains the filter criteria (via `TabularRowsRequest`) used to query the data.
            Headers allow the client to include generated SQL in the response or error response for debugging or auditing purposes.
            If the schema name is omitted, the default schema will be used.
            """)
    @PostMapping(value = {
        "/api/ux/tabular/jooq/{schemaName}/{masterTableNameOrViewName}.json"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public TabularRowsResponse<?> tabularRows(
            @Parameter(description = "Mandatory path variable to mention schema name.", required = true) @PathVariable(required = true) String schemaName,
            @Parameter(description = "Mandatory path variable to mention the table or view name.", required = true) final @PathVariable String masterTableNameOrViewName,
            @Parameter(description = "Payload for the API. This <b>must not</b> be <code>null</code>.", required = true) final @RequestBody @Nonnull TabularRowsRequest payload,
            @Parameter(description = "Header to mention whether the generated SQL to be included in the response.", required = false) @RequestHeader(value = "X-Include-Generated-SQL-In-Response", required = false, defaultValue = "false") boolean includeGeneratedSqlInResp,
            @Parameter(description = "Header to mention whether the generated SQL to be included in the error response. This will be taken <code>true</code> by default.", required = false) @RequestHeader(value = "X-Include-Generated-SQL-In-Error-Response", required = false, defaultValue = "true") boolean includeGeneratedSqlInErrorResp) {

        if (!VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN.matcher(schemaName).matches()
                || !VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN.matcher(masterTableNameOrViewName).matches()) {
            throw new IllegalArgumentException("Invalid schema or table name.");
        }
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

    @Operation(summary = "Retrieve SQL rows from a master table or view for a specific column value", description = """
            Fetches rows from the specified schema and master table or view where the value in column `{columnName}` exactly matches `{columnValue}`.
            "For example, to retrieve rows from a table 'orders' where the 'status' column equals 'shipped', pass 'shipped' as `{columnValue}`.
            """)
    @GetMapping("/api/ux/tabular/jooq/{schemaName}/{masterTableNameOrViewName}/{columnName}/{columnValue}.json")
    @ResponseBody
    public Object tabularRowsCustom(
            @Parameter(description = "Mandatory path variable to mention schema name.", required = true) final @PathVariable(required = false) String schemaName,
            @Parameter(description = "Mandatory path variable to mention the table or view name.", required = true) final @PathVariable String masterTableNameOrViewName,
            @Parameter(description = "Mandatory path variable to mention the column name.", required = true) final @PathVariable String columnName,
            @Parameter(description = "Mandatory path variable to mention the column value.", required = true) final @PathVariable String columnValue) {

        if (!VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN.matcher(schemaName).matches()
                || !VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN.matcher(masterTableNameOrViewName).matches()
                || !VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN.matcher(columnName).matches()) {
            throw new IllegalArgumentException("Invalid schema or table or column name.");
        }
        // Fetch the result using the dynamically determined table and column; if
        // jOOQ-generated types were found, automatic column value mapping will occur
        final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                masterTableNameOrViewName);
        List<Map<String, Object>> result = udiPrimeJpaConfig.dsl().selectFrom(typableTable.table())
                .where(DSL.field(typableTable.column(columnName)).eq(DSL.val(columnValue)))
                .fetch()
                .intoMaps();

        ZoneId newYorkZone = ZoneId.of("America/New_York");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");

        result.forEach(row -> row.forEach((key, value) -> {
            if (value instanceof OffsetDateTime) {
                ZonedDateTime newYorkTime = ((OffsetDateTime) value).atZoneSameInstant(newYorkZone);
                row.put(key, newYorkTime.format(formatter));
            } else if (value instanceof java.time.LocalDate) {
                // Convert java.sql.Date to LocalDate
                LocalDate localDate = (LocalDate) value;
                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy");
                row.put(key, localDate.format(dateFormatter));
            }
        }));

        return result;
    }

    @Operation(summary = "Retrieve SQL rows from a master table or view with specific column value checks", description = """
            Fetches rows from the specified schema and master table or view where the value in column `{columnName}` exactly matches `{columnValue}`, and the value in column `{columnName2}` matches the pattern '{columnValue2}' using a LIKE condition.
            For example, to retrieve rows from a table 'users' where the 'status' column equals 'active' and the 'email' column contains 'example.com', pass 'active' as `{columnValue}` and 'example.com' as `{columnValue2}`.
            """)
    @GetMapping("/api/ux/tabular/jooq/{schemaName}/{masterTableNameOrViewName}/{columnName}/{columnValue}/{columnName2}/{columnValue2}.json")
    @ResponseBody
    public Object tabularRowsCustomWithMultipleParams(
            @Parameter(description = "Mandatory path variable to mention schema name.", required = true) final @PathVariable(required = false) String schemaName,
            @Parameter(description = "Mandatory path variable to mention the table or view name.", required = true) final @PathVariable String masterTableNameOrViewName,
            @Parameter(description = "Mandatory path variable to mention the column name.", required = true) final @PathVariable String columnName,
            @Parameter(description = "Mandatory path variable to mention the column value.", required = true) final @PathVariable String columnValue,
            @Parameter(description = "Mandatory path variable to mention the column2 name.", required = true) final @PathVariable String columnName2,
            @Parameter(description = "Mandatory path variable to mention the column2 value.", required = true) final @PathVariable String columnValue2) {

        if (!VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN.matcher(schemaName).matches()
                || !VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN.matcher(masterTableNameOrViewName).matches()
                || !VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN.matcher(columnName).matches()
                || !VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN.matcher(columnName2).matches()) {
            throw new IllegalArgumentException("Invalid schema or table or column name.");
        }
        String columnValue2LikePattern = "%" + columnValue2 + "%";

        final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                masterTableNameOrViewName);

        List<Map<String, Object>> result = udiPrimeJpaConfig.dsl().selectFrom(typableTable.table())
                .where(DSL.field(typableTable.column(columnName)).eq(DSL.val(columnValue))
                        .and(DSL.field(typableTable.column(columnName2)).like(DSL.val(columnValue2LikePattern))))
                .fetch()
                .intoMaps();

        ZoneId newYorkZone = ZoneId.of("America/New_York");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");

        result.forEach(row -> row.forEach((key, value) -> {
            if (value != null) {
                // Log the data type of the value
                LOG.info("Key: {}, Value: {}, Data Type: {}", key, value, value.getClass().getName());
            }
            if (value instanceof OffsetDateTime) {
                ZonedDateTime newYorkTime = ((OffsetDateTime) value).atZoneSameInstant(newYorkZone);
                row.put(key, newYorkTime.format(formatter));
            } else if (value instanceof java.time.LocalDate) {
                // Convert java.sql.Date to LocalDate
                LocalDate localDate = (LocalDate) value;
                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy");
                row.put(key, localDate.format(dateFormatter));
            }
        }));

        return result;
    }

    @Operation(summary = "Retrieve SQL rows from a master table or view with multiple column value checks", description = """
            Fetches rows from the specified schema and master table or view where the values in three different columns `{columnName1}`, `{columnName2}`, and `{columnName3}` match the provided values `{columnValue1}`, `{columnValue2}`, and `{columnValue3}` respectively.
            For example, if you want to retrieve rows from a table 'orders' in schema 'sales' where the columns 'order_status', 'customer_id', and 'order_date' match the values 'pending', '12345', and '2024-09-17', provide those values in the corresponding URL parameters.
            """)
    @GetMapping("/api/ux/tabular/jooq/multiparam/{schemaName}/{masterTableNameOrViewName}/{columnName1}/{columnValue1}/{columnName2}/{columnValue2}/{columnName3}/{columnValue3}.json")
    @ResponseBody
    public Object tabularRowsCustomWithMultipleParamsChecks(
            @Parameter(description = "Path variable to mention the schema name.", required = true) final @PathVariable(required = false) String schemaName,
            @Parameter(description = "Path variable to mention the master table or view name.", required = true) final @PathVariable String masterTableNameOrViewName,
            @Parameter(description = "Path variable to mention the columnn 1 name.", required = true) final @PathVariable String columnName1,
            @Parameter(description = "Path variable to mention the column 1 value.", required = true) final @PathVariable String columnValue1,
            @Parameter(description = "Path variable to mention the column 2 name.", required = true) final @PathVariable String columnName2,
            @Parameter(description = "Path variable to mention the column 2 value.", required = true) final @PathVariable String columnValue2,
            @Parameter(description = "Path variable to mention the column 3 name.", required = true) final @PathVariable String columnName3,
            @Parameter(description = "Path variable to mention the column 3 value.", required = true) final @PathVariable String columnValue3) {

        if (!VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN.matcher(schemaName).matches()
                || !VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN.matcher(masterTableNameOrViewName).matches()
                || !VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN.matcher(columnName1).matches()
                || !VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN.matcher(columnName2).matches()
                || !VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN.matcher(columnName3).matches()) {
            throw new IllegalArgumentException("Invalid schema or table or column name.");
        }
        // Decode URL-encoded values
        String decodedColumnValue1 = URLDecoder.decode(columnValue1, StandardCharsets.UTF_8);
        String decodedColumnValue2 = URLDecoder.decode(columnValue2, StandardCharsets.UTF_8);
        String decodedColumnValue3 = URLDecoder.decode(columnValue3, StandardCharsets.UTF_8);

        // Fetch the result using the dynamically determined table and column; if
        // jOOQ-generated types were found, automatic column value mapping will occur
        final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                masterTableNameOrViewName);
        List<Map<String, Object>> result = udiPrimeJpaConfig.dsl().selectFrom(typableTable.table())
                .where(DSL.field(typableTable.column(columnName1)).eq(DSL.val(decodedColumnValue1))
                        .and(DSL.field(typableTable.column(columnName2)).eq(DSL.val(decodedColumnValue2)))
                        .and(DSL.field(typableTable.column(columnName3)).eq(DSL.val(decodedColumnValue3))))
                .fetch()
                .intoMaps();

        ZoneId newYorkZone = ZoneId.of("America/New_York");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");

        result.forEach(row -> row.forEach((key, value) -> {
            if (value instanceof OffsetDateTime) {
                ZonedDateTime newYorkTime = ((OffsetDateTime) value).atZoneSameInstant(newYorkZone);
                row.put(key, newYorkTime.format(formatter));
            } else if (value instanceof java.sql.Date) {
                // Convert java.sql.Date to LocalDate
                LocalDate localDate = ((java.sql.Date) value).toLocalDate();
                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy");
                row.put(key, localDate.format(dateFormatter));
            }
        }));

        return result;
    }

}
