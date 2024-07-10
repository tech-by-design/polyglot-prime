package lib.aide.tabular;

import static org.assertj.core.api.Assertions.assertThat;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public class JooqRowsSupplierTest {
    static private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSimpleQuery() throws Exception {
        final var jsonRequest = """
                {
                    "startRow": 0,
                    "endRow": 100,
                    "rowGroupCols": [],
                    "valueCols": [
                        {"id": "country", "displayName": "Country", "field": "country", "aggFunc": null},
                        {"id": "gold", "displayName": "Gold Medals", "field": "gold", "aggFunc": null}
                    ],
                    "pivotCols": [],
                    "pivotMode": false,
                    "groupKeys": [],
                    "filterModel": {},
                    "sortModel": [],
                    "requestContext": {},
                    "rangeSelection": [],
                    "aggregationFunctions": []
                }
                """;

        final var request = objectMapper.readValue(jsonRequest, TabularRowsRequest.class);
        final var supplier = new JooqRowsSupplier.Builder()
                .withRequest(request)
                .withTable(DSL.table("medals"))
                .withDSL(DSL.using(SQLDialect.POSTGRES))
                .build();

        final var jooqQuery = supplier.query();

        final var expectedSQL = """
                SELECT "country", "gold"
                FROM medals
                OFFSET ? ROWS
                FETCH NEXT ? ROWS ONLY
                """;

        final var actualSQL = jooqQuery.query().getSQL();
        final var expectedParams = List.of(request.startRow(), request.endRow() - request.startRow());
        assertThat(actualSQL).isEqualToIgnoringWhitespace(expectedSQL);
        assertThat(jooqQuery.bindValues()).isEqualTo(expectedParams);
    }

    @Test
    public void testQueryWithFilterAndSort() throws Exception {
        final var jsonRequest = """
                {
                    "startRow": 0,
                    "endRow": 100,
                    "rowGroupCols": [],
                    "valueCols": [
                        {"id": "country", "displayName": "Country", "field": "country", "aggFunc": null},
                        {"id": "gold", "displayName": "Gold Medals", "field": "gold", "aggFunc": null}
                    ],
                    "pivotCols": [],
                    "pivotMode": false,
                    "groupKeys": [],
                    "filterModel": {
                        "country": {"filterType": "text", "filter": "USA"}
                    },
                    "sortModel": [
                        {"colId": "gold", "sort": "desc"}
                    ],
                    "requestContext": {},
                    "rangeSelection": [],
                    "aggregationFunctions": []
                }
                """;

        final var request = objectMapper.readValue(jsonRequest, TabularRowsRequest.class);
        final var supplier = new JooqRowsSupplier.Builder()
                .withRequest(request)
                .withTable(DSL.table("medals"))
                .withDSL(DSL.using(SQLDialect.POSTGRES))
                .build();

        final var jooqQuery = supplier.query();
        final var expectedSQL = """
                SELECT "country", "gold"
                FROM medals
                WHERE CAST("country" AS varchar) ILIKE ?
                ORDER BY "gold" DESC
                OFFSET ? ROWS
                FETCH NEXT ? ROWS ONLY
                """;

        final var actualSQL = jooqQuery.query().getSQL();
        final var expectedParams = List.of("USA", request.startRow(), request.endRow() - request.startRow());
        assertThat(actualSQL).isEqualToIgnoringWhitespace(expectedSQL);
        assertThat(jooqQuery.bindValues()).isEqualTo(expectedParams);
    }

    @Test
    public void testQueryWithGroupBy() throws Exception {
        final var jsonRequest = """
                {
                    "startRow": 0,
                    "endRow": 100,
                    "rowGroupCols": [
                        {"id": "country", "displayName": "Country", "field": "country", "aggFunc": null}
                    ],
                    "valueCols": [
                        {"id": "gold", "displayName": "Gold Medals", "field": "gold", "aggFunc": null}
                    ],
                    "pivotCols": [],
                    "pivotMode": false,
                    "groupKeys": [],
                    "filterModel": {},
                    "sortModel": [],
                    "requestContext": {},
                    "rangeSelection": [],
                    "aggregationFunctions": []
                }
                """;

        final var request = objectMapper.readValue(jsonRequest, TabularRowsRequest.class);
        final var supplier = new JooqRowsSupplier.Builder()
                .withRequest(request)
                .withTable(DSL.table("medals"))
                .withDSL(DSL.using(SQLDialect.POSTGRES))
                .build();

        final var jooqQuery = supplier.query();
        final var expectedSQL = """
                SELECT "gold", "country"
                FROM medals
                GROUP BY "country"
                OFFSET ? ROWS
                FETCH NEXT ? ROWS ONLY
                """;

        final var actualSQL = jooqQuery.query().getSQL();
        final var expectedParams = List.of(request.startRow(), request.endRow() - request.startRow());
        assertThat(actualSQL).isEqualToIgnoringWhitespace(expectedSQL);
        assertThat(jooqQuery.bindValues()).isEqualTo(expectedParams);
    }

    @Test
    public void testQueryWithAggregation() throws Exception {
        final var jsonRequest = """
                {
                    "startRow": 0,
                    "endRow": 100,
                    "rowGroupCols": [],
                    "valueCols": [],
                    "pivotCols": [],
                    "pivotMode": false,
                    "groupKeys": [],
                    "filterModel": {},
                    "sortModel": [],
                    "requestContext": {},
                    "rangeSelection": [],
                    "aggregationFunctions": [
                        {"functionName": "count", "columns": ["gold"]}
                    ]
                }
                """;

        final var request = objectMapper.readValue(jsonRequest, TabularRowsRequest.class);
        final var supplier = new JooqRowsSupplier.Builder()
                .withRequest(request)
                .withTable(DSL.table("medals"))
                .withDSL(DSL.using(SQLDialect.POSTGRES))
                .build();

        final var jooqQuery = supplier.query();
        final var expectedSQL = """
                SELECT count("gold")
                FROM medals
                OFFSET ? ROWS
                FETCH NEXT ? ROWS ONLY
                """;

        final var actualSQL = jooqQuery.query().getSQL();
        final var expectedParams = List.of(request.startRow(), request.endRow() - request.startRow());
        assertThat(actualSQL).isEqualToIgnoringWhitespace(expectedSQL);
        assertThat(jooqQuery.bindValues()).isEqualTo(expectedParams);
    }

    @Test
    public void testQueryWithMultipleAggregations() throws Exception {
        final var jsonRequest = """
                {
                    "startRow": 0,
                    "endRow": 100,
                    "rowGroupCols": [],
                    "valueCols": [],
                    "pivotCols": [],
                    "pivotMode": false,
                    "groupKeys": [],
                    "filterModel": {},
                    "sortModel": [],
                    "requestContext": {},
                    "rangeSelection": [],
                    "aggregationFunctions": [
                        {"functionName": "sum", "columns": ["gold"]},
                        {"functionName": "avg", "columns": ["silver"]}
                    ]
                }
                """;

        final var request = objectMapper.readValue(jsonRequest, TabularRowsRequest.class);
        final var supplier = new JooqRowsSupplier.Builder()
                .withRequest(request)
                .withTable(DSL.table("medals"))
                .withDSL(DSL.using(SQLDialect.POSTGRES))
                .build();

        final var jooqQuery = supplier.query();
        final var expectedSQL = """
                SELECT
                sum(CAST("gold" AS double precision)),
                avg(CAST("silver" AS double precision))
                FROM medals
                OFFSET ? ROWS
                FETCH NEXT ? ROWS ONLY
                """;

        final var actualSQL = jooqQuery.query().getSQL();
        final var expectedParams = List.of(request.startRow(), request.endRow() - request.startRow());
        assertThat(actualSQL).isEqualToIgnoringWhitespace(expectedSQL);
        assertThat(jooqQuery.bindValues()).isEqualTo(expectedParams);
    }

    @Test
    public void testQueryWithDateFilter() throws Exception {
        final var jsonRequest = """
                {
                    "startRow": 0,
                    "endRow": 100,
                    "rowGroupCols": [],
                    "valueCols": [
                        {"id": "event_date", "displayName": "Event Date", "field": "event_date", "aggFunc": null},
                        {"id": "participants", "displayName": "Participants", "field": "participants", "aggFunc": null}
                    ],
                    "pivotCols": [],
                    "pivotMode": false,
                    "groupKeys": [],
                    "filterModel": {
                        "event_date": {"filterType": "date", "filter": "2023-07-01"}
                    },
                    "sortModel": [],
                    "requestContext": {},
                    "rangeSelection": [],
                    "aggregationFunctions": []
                }
                """;

        final var request = objectMapper.readValue(jsonRequest, TabularRowsRequest.class);
        final var supplier = new JooqRowsSupplier.Builder()
                .withRequest(request)
                .withTable(DSL.table("events"))
                .withDSL(DSL.using(SQLDialect.POSTGRES))
                .build();

        final var jooqQuery = supplier.query();
        final var expectedSQL = """
                SELECT "event_date", "participants"
                FROM events
                WHERE "event_date" = ?
                OFFSET ? ROWS
                FETCH NEXT ? ROWS ONLY
                """;

        final var actualSQL = jooqQuery.query().getSQL();
        final var expectedParams = List.of("2023-07-01", request.startRow(), request.endRow() - request.startRow());
        assertThat(actualSQL).isEqualToIgnoringWhitespace(expectedSQL);
        assertThat(jooqQuery.bindValues()).isEqualTo(expectedParams);
    }
}
