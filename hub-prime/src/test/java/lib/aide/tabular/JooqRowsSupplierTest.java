package lib.aide.tabular;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

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
                                        "country": {"filterType": "equals", "type": "equals", "filter": "USA"}
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
                                WHERE "country" = ?
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
        public void testQueryWithSingleGroupBy() throws Exception {
                final var jsonRequest = """
                                {
                                    "startRow": 0,
                                    "endRow": 100,
                                    "rowGroupCols": [
                                        {"id": "country", "displayName": "Country", "field": "country", "aggFunc": null}
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
                                SELECT "country"
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
        public void testQueryWithGroupBy() throws Exception {
                final var jsonRequest = """
                                {
                                "startRow": 0,
                                "endRow": 100,
                                "rowGroupCols": [
                                {"id": "country", "displayName": "Country", "field": "country", "aggFunc":
                                null},
                                {"id": "gold", "displayName": "Gold Medals", "field": "gold", "aggFunc":
                                null}
                                ],
                                "valueCols": [
                                {"id": "gold", "displayName": "Gold Medals", "field": "gold", "aggFunc":
                                null}
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

                final var request = objectMapper.readValue(jsonRequest,
                                TabularRowsRequest.class);
                final var supplier = new JooqRowsSupplier.Builder()
                                .withRequest(request)
                                .withTable(DSL.table("medals"))
                                .withDSL(DSL.using(SQLDialect.POSTGRES))
                                .build();

                final var jooqQuery = supplier.query();
                final var expectedSQL = """
                                SELECT "country", "gold"
                                FROM medals
                                GROUP BY "country", "gold"
                                OFFSET ? ROWS
                                FETCH NEXT ? ROWS ONLY
                                """;

                final var actualSQL = jooqQuery.query().getSQL();
                final var expectedParams = List.of(request.startRow(), request.endRow() -
                                request.startRow());
                assertThat(actualSQL).isEqualToIgnoringWhitespace(expectedSQL);
                assertThat(jooqQuery.bindValues()).isEqualTo(expectedParams);
        }

        @Test
        public void testQueryWithDataForSingleGroupBy() throws Exception {

                final var jsonRequest = """
                                {
                                        "startRow": 0,
                                        "endRow": 100,
                                        "rowGroupCols": [
                                          {
                                            "id": "country",
                                            "displayName": "Country",
                                            "field": "country"
                                          }
                                        ],
                                        "valueCols": [],
                                        "pivotCols": [],
                                        "pivotMode": false,
                                        "groupKeys": [
                                          "USA"
                                        ],
                                        "filterModel": {},
                                        "sortModel": []
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
                                SELECT *
                                FROM medals
                                WHERE "country" = ?
                                OFFSET ? ROWS
                                FETCH NEXT ? ROWS ONLY
                                """;

                final var actualSQL = jooqQuery.query().getSQL();
                final var expectedParams = List.of("USA", request.startRow(), request.endRow() - request.startRow());
                assertThat(actualSQL).isEqualToIgnoringWhitespace(expectedSQL);
                assertThat(jooqQuery.bindValues()).isEqualTo(expectedParams);
        }

}
