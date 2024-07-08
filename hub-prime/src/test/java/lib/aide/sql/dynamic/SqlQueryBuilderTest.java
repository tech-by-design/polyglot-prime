package lib.aide.sql.dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SqlQueryBuilderTest {

    private DSLContext dsl;
    private SqlQueryBuilder sqlQueryBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        dsl = DSL.using(SQLDialect.POSTGRES);
        sqlQueryBuilder = new SqlQueryBuilder(dsl);
    }

    @Test
    public void testSimpleSelect() throws Exception {
        String jsonRequest = """
                    {
                        "startRow": 0,
                        "endRow": 10,
                        "rowGroupCols": [],
                        "valueCols": [{"field": "region"}, {"field": "sales"}],
                        "pivotMode": false,
                        "groupKeys": [],
                        "filterModel": {},
                        "sortModel": []
                    }
                """;
        var request = objectMapper.readValue(jsonRequest, ServerRowsRequest.class);

        // Verify the deserialized request object
        assertThat(request.getValueCols()).hasSize(2);
        assertThat(request.getValueCols().get(0).getField()).isEqualTo("region");
        assertThat(request.getValueCols().get(1).getField()).isEqualTo("sales");

        var query = sqlQueryBuilder.createSql(request, null, "sales_data");
        var expected = """
                    SELECT "region", "sales"
                    FROM "sales_data"
                    OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """.stripIndent();

        assertThat(query.getSQL()).isEqualToIgnoringWhitespace(expected);

        // Verify bind values
        List<Object> expectedBindValues = List.of(0L, 11L);
        assertThat(query.getBindValues()).containsExactlyElementsOf(expectedBindValues);
    }

    @Test
    public void testSimpleSelectWithSorting() throws Exception {
        String jsonRequest = """
                    {
                        "startRow": 0,
                        "endRow": 10,
                        "rowGroupCols": [],
                        "valueCols": [{"field": "region"}, {"field": "sales"}],
                        "pivotMode": false,
                        "groupKeys": [],
                        "filterModel": {},
                        "sortModel": [{"colId": "region", "sort": "asc"}, {"colId": "sales", "sort": "desc"}]
                    }
                """;
        var request = objectMapper.readValue(jsonRequest, ServerRowsRequest.class);

        // Verify the deserialized request object
        assertThat(request.getValueCols()).hasSize(2);
        assertThat(request.getValueCols().get(0).getField()).isEqualTo("region");
        assertThat(request.getValueCols().get(1).getField()).isEqualTo("sales");
        assertThat(request.getSortModel()).hasSize(2);
        assertThat(request.getSortModel().get(0).getColId()).isEqualTo("region");
        assertThat(request.getSortModel().get(0).getSort()).isEqualTo("asc");
        assertThat(request.getSortModel().get(1).getColId()).isEqualTo("sales");
        assertThat(request.getSortModel().get(1).getSort()).isEqualTo("desc");

        var query = sqlQueryBuilder.createSql(request, null, "sales_data");
        var expected = """
                    SELECT "region", "sales"
                    FROM "sales_data"
                    ORDER BY "region" ASC, "sales" DESC
                    OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """.stripIndent();

        assertThat(query.getSQL()).isEqualToIgnoringWhitespace(expected);

        // Verify bind values
        List<Object> expectedBindValues = List.of(0L, 11L);
        assertThat(query.getBindValues()).containsExactlyElementsOf(expectedBindValues);
    }

    @Test
    public void testSelectWithSortingAndFiltering() throws Exception {
        String jsonRequest = """
                    {
                        "startRow": 0,
                        "endRow": 10,
                        "rowGroupCols": [],
                        "valueCols": [{"field": "region"}, {"field": "sales"}],
                        "pivotMode": false,
                        "groupKeys": [],
                        "filterModel": {"region": {"filterType": "text", "type": "equals", "filter": "North"}},
                        "sortModel": [{"colId": "region", "sort": "asc"}, {"colId": "sales", "sort": "desc"}]
                    }
                """;
        var request = objectMapper.readValue(jsonRequest, ServerRowsRequest.class);

        // Verify the deserialized request object
        assertThat(request.getValueCols()).hasSize(2);
        assertThat(request.getValueCols().get(0).getField()).isEqualTo("region");
        assertThat(request.getValueCols().get(1).getField()).isEqualTo("sales");
        assertThat(request.getSortModel()).hasSize(2);
        assertThat(request.getSortModel().get(0).getColId()).isEqualTo("region");
        assertThat(request.getSortModel().get(0).getSort()).isEqualTo("asc");
        assertThat(request.getSortModel().get(1).getColId()).isEqualTo("sales");
        assertThat(request.getSortModel().get(1).getSort()).isEqualTo("desc");
        assertThat(((TextColumnFilter) request.getFilterModel().get("region")).getFilter()).isEqualTo("North");

        var query = sqlQueryBuilder.createSql(request, null, "sales_data");
        var expected = """
                    SELECT "region", "sales"
                    FROM "sales_data"
                    WHERE "region" = ?
                    ORDER BY "region" ASC, "sales" DESC
                    OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """.stripIndent();

        assertThat(query.getSQL()).isEqualToIgnoringWhitespace(expected);

        // Verify bind values
        List<Object> expectedBindValues = List.of("North", 0L, 11L);
        assertThat(query.getBindValues()).containsExactlyElementsOf(expectedBindValues);
    }

    @Test
    public void testSelectWithSortingFilteringAndGrouping() throws Exception {
        String jsonRequest = """
                    {
                        "startRow": 0,
                        "endRow": 10,
                        "rowGroupCols": [{"field": "region"}],
                        "valueCols": [{"aggFunc": "sum", "field": "sales"}],
                        "pivotMode": false,
                        "groupKeys": ["region"],
                        "filterModel": {"sales": {"filterType": "number", "type": "greaterThan", "filter": 1000}},
                        "sortModel": [{"colId": "region", "sort": "asc"}]
                    }
                """;
        var request = objectMapper.readValue(jsonRequest, ServerRowsRequest.class);

        // Verify the deserialized request object
        assertThat(request.getRowGroupCols()).hasSize(1);
        assertThat(request.getRowGroupCols().get(0).getField()).isEqualTo("region");
        assertThat(request.getValueCols()).hasSize(1);
        assertThat(request.getValueCols().get(0).getAggFunc()).isEqualTo("sum");
        assertThat(request.getValueCols().get(0).getField()).isEqualTo("sales");
        assertThat(request.getGroupKeys()).containsExactly("region");
        assertThat(((NumberColumnFilter) request.getFilterModel().get("sales")).getFilter()).isEqualTo(1000);
        assertThat(request.getSortModel()).hasSize(1);
        assertThat(request.getSortModel().get(0).getColId()).isEqualTo("region");
        assertThat(request.getSortModel().get(0).getSort()).isEqualTo("asc");

        var query = sqlQueryBuilder.createSql(request, null, "sales_data");
        var expected = """
                    SELECT region, sum("sales") AS "sales"
                    FROM "sales_data"
                    WHERE ("region" = ? AND "sales" > ?)
                    GROUP BY region
                    ORDER BY "region" ASC
                    OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """.stripIndent();

        assertThat(query.getSQL()).isEqualToIgnoringWhitespace(expected);

        // Verify bind values
        List<Object> expectedBindValues = List.of("region", 1000, 0L, 11L);
        assertThat(query.getBindValues()).containsExactlyElementsOf(expectedBindValues);
    }

    @Test
    public void testSelectWithPivoting() throws Exception {
        String jsonRequest = """
                    {
                        "startRow": 0,
                        "endRow": 10,
                        "rowGroupCols": [{"field": "region"}],
                        "valueCols": [{"aggFunc": "sum", "field": "sales"}],
                        "pivotCols": [{"field": "quarter"}],
                        "pivotMode": true,
                        "groupKeys": ["region"],
                        "filterModel": {},
                        "sortModel": [{"colId": "region", "sort": "asc"}]
                    }
                """;
        var request = objectMapper.readValue(jsonRequest, ServerRowsRequest.class);

        // TODO: figure out how to handle pivot values in request
        var pivotValues = Map.of("quarter", List.of("Q1", "Q2", "Q3", "Q4"));

        // Verify the deserialized request object
        assertThat(request.getStartRow()).isEqualTo(0);
        assertThat(request.getEndRow()).isEqualTo(10);
        assertThat(request.getRowGroupCols()).hasSize(1);
        assertThat(request.getRowGroupCols().get(0).getField()).isEqualTo("region");
        assertThat(request.getValueCols()).hasSize(1);
        assertThat(request.getValueCols().get(0).getAggFunc()).isEqualTo("sum");
        assertThat(request.getValueCols().get(0).getField()).isEqualTo("sales");
        assertThat(request.getPivotCols()).hasSize(1);
        assertThat(request.getPivotCols().get(0).getField()).isEqualTo("quarter");
        assertThat(request.isPivotMode()).isTrue();
        assertThat(request.getGroupKeys()).containsExactly("region");
        assertThat(request.getFilterModel()).isEmpty();
        assertThat(request.getSortModel()).hasSize(1);
        assertThat(request.getSortModel().get(0).getColId()).isEqualTo("region");
        assertThat(request.getSortModel().get(0).getSort()).isEqualTo("asc");

        var query = sqlQueryBuilder.createSql(request, null, "sales_data");
        var expectedSql = """
                    SELECT "region",
                           sum(CASE WHEN "quarter" = 'Q1' THEN "sales" END) AS "pivot_0_sales",
                           sum(CASE WHEN "quarter" = 'Q2' THEN "sales" END) AS "pivot_1_sales",
                           sum(CASE WHEN "quarter" = 'Q3' THEN "sales" END) AS "pivot_2_sales",
                           sum(CASE WHEN "quarter" = 'Q4' THEN "sales" END) AS "pivot_3_sales"
                    FROM "sales_data"
                    WHERE ("region" = ?)
                    GROUP BY "region"
                    ORDER BY "region" ASC
                    OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """.stripIndent();

        assertThat(query.getSQL()).isEqualToIgnoringWhitespace(expectedSql);

        // Verify bind values
        List<Object> expectedBindValues = List.of("region", 0L, 10L);
        assertThat(query.getBindValues()).containsExactlyElementsOf(expectedBindValues);
    }
}
