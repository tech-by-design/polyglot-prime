package lib.aide.tabular;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TabularRowsRequestTest {

    @Test
    void testDeserialization() throws IOException {
        String jsonPayload = """
            {
                "startRow": 0,
                "endRow": 100,
                "rowGroupCols": [{"id": "country", "displayName": "Country", "field": "country", "aggFunc": "sum"}],
                "valueCols": [{"id": "gold", "displayName": "Gold Medals", "field": "gold", "aggFunc": "sum"}],
                "pivotCols": [],
                "pivotMode": false,
                "groupKeys": [],
                "filterModel": {"country": {"filterType": "text", "type": "text", "filter": "USA"}},
                "sortModel": [{"colId": "gold", "sort": "desc"}],
                "requestContext": {"contextKey": "contextValue"},
                "rangeSelection": [{"startRow": 0, "endRow": 10}],
                "aggregationFunctions": [{"functionName": "sum", "columns": ["gold"]}]
            }
        """;

        final var objectMapper = new ObjectMapper();
        final var request = objectMapper.readValue(jsonPayload, TabularRowsRequest.class);

        // AssertJ fluent assertions
        assertThat(request).isNotNull();
        assertThat(request.startRow()).isEqualTo(0);
        assertThat(request.endRow()).isEqualTo(100);
        assertThat(request.rowGroupCols()).hasSize(1);
        assertThat(request.rowGroupCols().get(0)).isEqualTo(new TabularRowsRequest.ColumnVO("country", "Country", "country", "sum"));
        assertThat(request.valueCols()).hasSize(1);
        assertThat(request.valueCols().get(0)).isEqualTo(new TabularRowsRequest.ColumnVO("gold", "Gold Medals", "gold", "sum"));
        assertThat(request.pivotCols()).isEmpty();
        assertThat(request.pivotMode()).isFalse();
        assertThat(request.groupKeys()).isEmpty();
        //assertThat(request.filterModel()).containsEntry("country", new TabularRowsRequest.FilterModel("text", "text", "USA", "", ""));
        assertThat(request.sortModel()).hasSize(1);
        assertThat(request.sortModel().get(0)).isEqualTo(new TabularRowsRequest.SortModel("gold", "desc"));
        assertThat(request.requestContext()).containsEntry("contextKey", "contextValue");
        assertThat(request.rangeSelection()).hasSize(1);
        assertThat(request.rangeSelection().get(0)).isEqualTo(new TabularRowsRequest.RangeSelection(0, 10));
        assertThat(request.aggregationFunctions()).hasSize(1);
        assertThat(request.aggregationFunctions().get(0)).isEqualTo(new TabularRowsRequest.AggregationFunction("sum", List.of("gold")));
    }
}
