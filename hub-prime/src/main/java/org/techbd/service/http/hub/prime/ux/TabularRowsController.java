package org.techbd.service.http.hub.prime.ux;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

    private final UdiPrimeJpaConfig udiPrimeJpaConfig;

    public TabularRowsController(final UdiPrimeJpaConfig udiPrimeJpaConfig) {
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
    }

    @Operation(summary = "Fetch SQL rows from a master table or view with optional schema specification", description = """
            Retrieves rows from a specified master table or view, optionally within a specific schema.
            The request body contains the filter criteria (via `TabularRowsRequest`) used to query the data.
            Headers allow the client to include generated SQL in the response or error response for debugging or auditing purposes.
            If the schema name is omitted, the default schema will be used.
            """)
    @PostMapping(value = { "/api/ux/tabular/jooq/{masterTableNameOrViewName}.json",
            "/api/ux/tabular/jooq/{schemaName}/{masterTableNameOrViewName}.json" }, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public TabularRowsResponse<?> tabularRows(
            @Parameter(description = "Mandatory path variable to mention schema name.", required = true) @PathVariable(required = false) String schemaName,
            @Parameter(description = "Mandatory path variable to mention the table or view name.", required = true) final @PathVariable String masterTableNameOrViewName,
            @Parameter(description = "Payload for the API. This <b>must not</b> be <code>null</code>.", required = true) final @RequestBody @Nonnull TabularRowsRequest payload,
            @Parameter(description = "Header to mention whether the generated SQL to be included in the response.", required = false) @RequestHeader(value = "X-Include-Generated-SQL-In-Response", required = false, defaultValue = "false") boolean includeGeneratedSqlInResp,
            @Parameter(description = "Header to mention whether the generated SQL to be included in the error response. This will be taken <code>true</code> by default.", required = false) @RequestHeader(value = "X-Include-Generated-SQL-In-Error-Response", required = false, defaultValue = "true") boolean includeGeneratedSqlInErrorResp) {

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

        // Fetch the result using the dynamically determined table and column; if
        // jOOQ-generated types were found, automatic column value mapping will occur
        final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                masterTableNameOrViewName);
        List<Map<String, Object>> result = udiPrimeJpaConfig.dsl().selectFrom(typableTable.table())
                .where(typableTable.column(columnName).eq(columnValue))
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

        String columnValue2LikePattern = "%" + columnValue2 + "%";

        final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                masterTableNameOrViewName);

        List<Map<String, Object>> result = udiPrimeJpaConfig.dsl().selectFrom(typableTable.table())
                .where(typableTable.column(columnName).eq(columnValue)
                        .and(typableTable.column(columnName2).like(columnValue2LikePattern)))
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

        // Decode URL-encoded values
        String decodedColumnValue1 = URLDecoder.decode(columnValue1, StandardCharsets.UTF_8);
        String decodedColumnValue2 = URLDecoder.decode(columnValue2, StandardCharsets.UTF_8);
        String decodedColumnValue3 = URLDecoder.decode(columnValue3, StandardCharsets.UTF_8);

        // Fetch the result using the dynamically determined table and column; if
        // jOOQ-generated types were found, automatic column value mapping will occur
        final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                masterTableNameOrViewName);
        List<Map<String, Object>> result = udiPrimeJpaConfig.dsl().selectFrom(typableTable.table())
                .where(typableTable.column(columnName1).eq(decodedColumnValue1)
                        .and(typableTable.column(columnName2).eq(decodedColumnValue2))
                        .and(typableTable.column(columnName3).eq(decodedColumnValue3)))
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

    @Operation(summary = "SQL rows from a master table or view between specified start and end date-times", description = """
            Retrieves submission counts from a specified schema and view where the values in the date-time column `{dateField}` fall
            between the provided start date-time `{startDateTimeValue}` and end date-time `{endDateTimeValue}`.

            Optionally, you can specify an additional column `{columnName1}` and its corresponding value `{columnValue1}` to further
            filter the results. The date-time values should be provided in 'MM-dd-yyyy HH:mm:ss' format.

            For example, to retrieve counts for the date column 'submission_date' between '09-10-2024 10:25:30' and '09-10-2024 12:25:30',
            use those values in the respective fields. If no `{columnName1}` and `{columnValue1}` are provided, the API will only filter
            based on the date range.
            """)
    @GetMapping({
            "/api/ux/tabular/jooq/{schemaName}/{viewName}/{dateField}/{startDateTimeValue}/{endDateTimeValue}.json",
            "/api/ux/tabular/jooq/{schemaName}/{viewName}/{columnName1}/{columnValue1}/{dateField}/{startDateTimeValue}/{endDateTimeValue}.json"
    })
    @ResponseBody
    public Object getSubmissionCountsBetweenDates(
            @Parameter(description = "Mandatory path variable to mention schema name.", required = true) final @PathVariable(required = true) String schemaName,
            @Parameter(description = "Path variable to mention the view name.", required = true) final @PathVariable String viewName,
            @Parameter(description = "Path variable to mention column 1 name.", required = false) final @PathVariable(required = false) String columnName1,
            @Parameter(description = "Path variable to mention column 1 value.", required = false) final @PathVariable(required = false) String columnValue1,
            @Parameter(description = "Path variable to mention date field.", required = false) final @PathVariable String dateField,
            @Parameter(description = "Path variable to mention the start date. Expected format is 'MM-dd-yyyy HH:mm:ss'", required = false) final @PathVariable String startDateTimeValue,
            @Parameter(description = "Path variable to mention the end date.. Expected format is 'MM-dd-yyyy HH:mm:ss'", required = false) final @PathVariable String endDateTimeValue)
            throws UnsupportedEncodingException {

        // URL decode the date-time values to handle spaces encoded as %20 or +
        String decodedStartDate = URLDecoder.decode(startDateTimeValue, "UTF-8");
        String decodedEndDate = URLDecoder.decode(endDateTimeValue, "UTF-8");

        // Define the table from which you want to fetch the data
        final var typableTable = JooqRowsSupplier.TypableTable.fromTablesRegistry(Tables.class, schemaName,
                viewName);

        // Parse the decoded startDateValue and endDateValue into LocalDateTime
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");
        LocalDateTime startDateTime = LocalDateTime.parse(decodedStartDate, formatter); // Parse with time
        // included
        LocalDateTime endDateTime = LocalDateTime.parse(decodedEndDate, formatter); // Parse with time included

        // Build the query
        var query = udiPrimeJpaConfig.dsl().selectFrom(typableTable.table())
                .where(typableTable.column(dateField).between(startDateTime, endDateTime));

        // If columnName1 and columnValue1 are provided, add them to the query
        if (columnName1 != null && !columnName1.isEmpty() && columnValue1 != null && !columnValue1.isEmpty()) {
            query = query.and(typableTable.column(columnName1).eq(columnValue1));
        }

        // Execute the query and return the results
        return query.fetch().intoMaps();

    }
}
