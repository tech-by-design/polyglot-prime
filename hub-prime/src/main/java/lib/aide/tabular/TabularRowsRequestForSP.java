package lib.aide.tabular;

import java.util.List;
import java.util.Map;

/**
 * Record representing a server-side rows request from AG Grid. This record
 * includes all necessary parameters for pagination, sorting, filtering, and
 * grouping.
 *
 * @param startRow            The index of the first row to fetch.
 * @param endRow              The index of the last row to fetch.
 * @param rowGroupCols        The columns to group by.
 * @param valueCols           The columns to aggregate.
 * @param pivotCols           The columns to pivot.
 * @param pivotMode           Indicates if pivot mode is enabled.
 * @param groupKeys           The keys to group by.
 * @param filterModel         The filter model applied to the grid.
 * @param sortModel           The sort model applied to the grid.
 * @param requestContext      Additional context data for the request.
 * @param rangeSelection      The selected ranges in the grid.
 * @param aggregationFunctions The aggregation functions to apply.
 */
public record TabularRowsRequestForSP(
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
     * @param id          The column ID.
     * @param displayName The display name of the column.
     * @param field       The field name of the column.
     * @param aggFunc     The aggregation function for the column.
     */
    public record ColumnVO(String id, String displayName, String field, String aggFunc) {}

    /**
     * Record representing a filter model.
     *
     * @param filterType  The type of the filter (e.g., text, number).
     * @param type        The comparison type (e.g., equals, inRange).
     * @param filter      The primary filter value.
     * @param filterTo    The secondary filter value (e.g., for "between").
     * @param operator    Logical operator (AND/OR) for combining conditions.
     * @param conditions  Nested conditions for complex filters.
     * @param secondFilter A secondary filter for complex logic.
     * @param dateFrom    Starting date for date range filters.
     * @param dateTo      Ending date for date range filters.
     */
    public record FilterModel(
            String filterType,
            String type,
            Object filter,
            Object filterTo,
            String operator,
            List<FilterCondition> conditions,
            Object secondFilter,
            String dateFrom,
            String dateTo) {

        /**
         * Constructor for simple filters.
         *
         * @param filterType The type of the filter.
         * @param type       The comparison type.
         * @param filter     The filter value.
         */
        public FilterModel(String filterType, String type, Object filter) {
            this(filterType, type, filter, null, null, null, null, null, null);
        }

        /**
         * Constructor for complex filters with conditions and an operator.
         *
         * @param filterType The type of the filter.
         * @param operator   The logical operator (AND/OR).
         * @param conditions The nested conditions.
         */
        public FilterModel(String filterType, String operator, List<FilterCondition> conditions) {
            this(filterType, null, null, null, operator, conditions, null, null, null);
        }
    }

    /**
     * Record representing a single condition in a complex filter.
     *
     * @param filterType The type of the filter (e.g., text, number).
     * @param type       The comparison type (e.g., equals, inRange).
     * @param filter     The primary filter value.
     * @param filterTo   The secondary filter value (for range comparisons).
     * @param dateFrom   Starting date for date range filters.
     * @param dateTo     Ending date for date range filters.
     */
    public record FilterCondition(
            String filterType, // e.g., "text", "number", etc.
            String type, // e.g., "equals", "contains", etc.
            Object filter, // Single filter value
            Object filterTo,
            String dateFrom, // For date filters
            String dateTo // For date filters
            ) {
    }

    /**
     * Record representing a sort model.
     *
     * @param colId The column to sort.
     * @param sort  The sort direction (asc/desc).
     */
    public record SortModel(String colId, String sort) {

    }

    /**
     * Record representing a range selection.
     *
     * @param startRow The starting row of the selection.
     * @param endRow   The ending row of the selection.
     */
    public record RangeSelection(int startRow, int endRow) {

    }

    /**
     * Record representing an aggregation function.
     *
     * @param functionName The name of the aggregation function.
     * @param columns      The columns to which the function applies.
     */
    public record AggregationFunction(String functionName, List<String> columns) {

    }
}




