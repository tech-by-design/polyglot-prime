package lib.aide.tabular;

import java.util.List;
import java.util.Map;

/**
 * Record representing a server-side rows request from AG Grid.
 * This record includes all necessary parameters for pagination, sorting, filtering, and grouping.
 *
 * @param startRow              The index of the first row to fetch.
 * @param endRow                The index of the last row to fetch.
 * @param rowGroupCols          The columns to group by.
 * @param valueCols             The columns to aggregate.
 * @param pivotCols             The columns to pivot.
 * @param pivotMode             Indicates if pivot mode is enabled.
 * @param groupKeys             The keys to group by.
 * @param filterModel           The filter model applied to the grid.
 * @param sortModel             The sort model applied to the grid.
 * @param requestContext        Additional context data for the request.
 * @param rangeSelection        The selected ranges in the grid.
 * @param aggregationFunctions  The aggregation functions to apply.
 */
public record TabularRowsRequest(
        int startRow,
        int endRow,
        List<ColumnVO> rowGroupCols,
        List<ColumnVO> valueCols,
        List<ColumnVO> pivotCols,
        boolean pivotMode,
        List<String> groupKeys,
        Map<String, FilterModel> filterModel,
        List<SortModel> sortModel,
        Map<String, Object> requestContext,
        List<RangeSelection> rangeSelection,
        List<AggregationFunction> aggregationFunctions
) {
    /**
     * Record representing a column definition.
     *
     * @param id           The column ID.
     * @param displayName  The display name of the column.
     * @param field        The field name of the column.
     * @param aggFunc      The aggregation function for the column.
     */
    public static record ColumnVO(String id, String displayName, String field, String aggFunc) {}

    /**
     * Record representing a filter model.
     *
     * @param filterType The type of the filter.
     * @param filter     The filter value.
     */
    public static record FilterModel(String filterType, Object filter) {}

    /**
     * Record representing a sort model.
     *
     * @param colId The ID of the column to sort.
     * @param sort  The sort direction (asc or desc).
     */
    public static record SortModel(String colId, String sort) {}

    /**
     * Record representing a range selection.
     *
     * @param startRow The starting row of the selection.
     * @param endRow   The ending row of the selection.
     */
    public static record RangeSelection(int startRow, int endRow) {}

    /**
     * Record representing an aggregation function.
     *
     * @param functionName The name of the aggregation function.
     * @param columns      The columns to which the aggregation function applies.
     */
    public static record AggregationFunction(String functionName, List<String> columns) {}
}
