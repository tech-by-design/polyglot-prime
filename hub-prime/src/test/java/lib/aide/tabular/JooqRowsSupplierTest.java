package lib.aide.tabular;

import static org.assertj.core.api.Assertions.assertThat;

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
                select "country", "gold" from medals limit ? offset ?
                """;

        final var actualSQL = jooqQuery.query().getSQL();
        assertThat(actualSQL).isEqualToIgnoringWhitespace(expectedSQL);
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
                select "country", "gold" from medals where "country" = ? order by "gold" desc limit ? offset ?
                """;

        final var actualSQL = jooqQuery.query().getSQL();
        assertThat(actualSQL).isEqualToIgnoringWhitespace(expectedSQL);
    }
}
