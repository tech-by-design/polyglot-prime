package lib.aide.tabular;

import java.util.List;
import java.util.Map;

/**
 * Record representing a server-side rows request from AG Grid. This record
 * includes all necessary parameters for pagination, sorting, filtering, and
 * grouping.
 *
 * @param startRow The index of the first row to fetch.
 * @param endRow The index of the last row to fetch.
 * @param rowGroupCols The columns to group by.
 * @param valueCols The columns to aggregate.
 * @param pivotCols The columns to pivot.
 * @param pivotMode Indicates if pivot mode is enabled.
 * @param groupKeys The keys to group by.
 * @param filterModel The filter model applied to the grid.
 * @param sortModel The sort model applied to the grid.
 * @param requestContext Additional context data for the request.
 * @param rangeSelection The selected ranges in the grid.
 * @param aggregationFunctions The aggregation functions to apply.
 */
public record TabularRowsRequest(
        int startRow,
        int endRow,
        Map<String, FilterModel> filterModel,
        List<ColumnVO> valueCols,
        List<ColumnVO> rowGroupCols,
        boolean pivotMode,
        List<ColumnVO> pivotCols,
        List<String> groupKeys,
        List<SortModel> sortModel,
        Map<String, Object> requestContext,
        List<RangeSelection> rangeSelection,
        List<AggregationFunction> aggregationFunctions) {

    /**
     * Record representing a column definition.
     *
     * @param id The column ID.
     * @param displayName The display name of the column.
     * @param field The field name of the column.
     * @param aggFunc The aggregation function for the column.
     */
    public record ColumnVO(String id, String displayName, String field, String aggFunc) {

    }

    /**
     * Record representing a filter model.
     *
     * @param filterType The type of the filter.
     * @param type The comparison type (e.g., equals, contains).
     * @param filter The filter value.
     *
     */
    public record ConditionsFilterModel(
            String filterType, // Type of the filter (text, number, etc.)
            String type, // Type of comparison (equals, contains, etc.)
            Object filter,
            Object secondFilter, // Filter value for between filters
            String dateFrom,
            String dateTo
            ) {

        public ConditionsFilterModel(String filterType, String type, Object filter) {
            this(filterType, type, filter, null, null, null);
        }
    }

    /**
     * Record representing a filter model.
     *
     */
    public record FilterModel(
            String filterType, // Type of the filter (text, number, etc.)
            String type, // Type of comparison (equals, contains, etc.)
            Object filter, // Filter value for simple filters
            String operator, // Logical operator (AND/OR) for complex filters
            List<ConditionsFilterModel> conditions, // Nested conditions for complex filters
            Object secondFilter, // Filter value for between filters
            String dateFrom,
            String dateTo) {
        // Constructors to handle both simple and complex filter cases

        /**
         * Constructor for simple filters (no conditions, just filter type and
         * value).
         *
         * @param filterType The type of the filter.
         * @param type The comparison type (e.g., equals, contains).
         * @param filter The filter value.
         */
        public FilterModel(String filterType, String type, Object filter, String dateFrom, String dateTo) {
            this(filterType, type, filter, null, null, null, dateFrom, dateTo); // No operator or conditions for simple filters
        }

        /**
         * Constructor for complex filters with conditions and operators.
         *
         * @param filterType The type of the filter.
         * @param operator The logical operator (e.g., AND/OR).
         * @param conditions The list of conditions.
         */
        public FilterModel(String filterType, String operator, List<ConditionsFilterModel> conditions, String dateFrom, String dateTo) {
            this(filterType, null, null, operator, conditions, null, dateFrom, dateTo); // No type or filter for complex filters
        }
    }

    /**
     * Record representing a sort model.
     *
     * @param colId The ID of the column to sort.
     * @param sort The sort direction (asc or desc).
     */
    public record SortModel(String colId, String sort) {

    }

    /**
     * Record representing a range selection.
     *
     * @param startRow The starting row of the selection.
     * @param endRow The ending row of the selection.
     */
    public record RangeSelection(int startRow, int endRow) {

    }

    /**
     * Record representing an aggregation function.
     *
     * @param functionName The name of the aggregation function.
     * @param columns The columns to which the aggregation function applies.
     */
    public record AggregationFunction(String functionName, List<String> columns) {

    }
}
