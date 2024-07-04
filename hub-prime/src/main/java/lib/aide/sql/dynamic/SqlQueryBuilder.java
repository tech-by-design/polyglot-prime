package lib.aide.sql.dynamic;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.jooq.CaseConditionStep;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.SelectSelectStep;
import org.jooq.SortField;
import org.jooq.conf.RenderKeywordCase;
import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import com.google.common.collect.Sets;

public class SqlQueryBuilder {

    private List<String> groupKeys;
    private List<String> rowGroups;
    private List<String> rowGroupsToInclude;
    private boolean isGrouping;
    private List<ColumnVO> valueColumns;
    private List<ColumnVO> pivotColumns;
    private Map<String, ColumnFilter> filterModel;
    private List<SortModel> sortModel;
    private int startRow, endRow;
    private List<ColumnVO> rowGroupCols;
    private Map<String, List<String>> pivotValues;
    private boolean isPivotMode;

    private final DSLContext dsl;

    public SqlQueryBuilder(final DSLContext dsl) {
        var settings = new Settings()
                .withRenderFormatted(true)
                .withRenderKeywordCase(RenderKeywordCase.UPPER)
                .withRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_QUOTED);

        this.dsl = dsl.configuration().derive(settings).dsl();
    }

    public Query createSql(final ServerRowsRequest request, final String tableName,
            final Map<String, List<String>> pivotValues) {
        this.valueColumns = Optional.ofNullable(request.getValueCols()).orElse(List.of());
        this.pivotColumns = Optional.ofNullable(request.getPivotCols()).orElse(List.of());
        this.groupKeys = Optional.ofNullable(request.getGroupKeys()).orElse(List.of());
        this.rowGroupCols = Optional.ofNullable(request.getRowGroupCols()).orElse(List.of());
        this.pivotValues = Optional.ofNullable(pivotValues).orElse(Map.of());
        this.isPivotMode = request.isPivotMode();
        this.rowGroups = getRowGroups();
        this.rowGroupsToInclude = getRowGroupsToInclude();
        this.isGrouping = rowGroups.size() > 0 && (rowGroups.size() >= groupKeys.size());
        this.filterModel = Optional.ofNullable(request.getFilterModel()).orElse(Map.of());
        this.sortModel = Optional.ofNullable(request.getSortModel()).orElse(List.of());
        this.startRow = request.getStartRow();
        this.endRow = request.getEndRow();

        final var select = selectSql();
        final var from = select.from(DSL.table(DSL.name(tableName)));
        final var where = from.where(whereSql());
        final var groupByFields = groupBySql();
        final var groupBy = groupByFields.isEmpty() ? where : where.groupBy(groupByFields);
        final var finalQuery = groupBy.orderBy(orderBySql()).offset(startRow).limit(endRow - startRow + 1);

        // Bind variables for execution
        List<Object> bindValues = new ArrayList<>();
        if (isGrouping) {
            bindValues.addAll(groupKeys);
        }
        if (!filterModel.isEmpty()) {
            filterModel.forEach((col, filter) -> {
                if (filter instanceof NumberColumnFilter) {
                    NumberColumnFilter numFilter = (NumberColumnFilter) filter;
                    bindValues.add(numFilter.getFilter());
                    if (numFilter.getFilterTo() != null) {
                        bindValues.add(numFilter.getFilterTo());
                    }
                } else if (filter instanceof TextColumnFilter) {
                    TextColumnFilter textFilter = (TextColumnFilter) filter;
                    bindValues.add(textFilter.getFilter());
                } else if (filter instanceof SetColumnFilter) {
                    SetColumnFilter setFilter = (SetColumnFilter) filter;
                    bindValues.addAll(setFilter.getValues());
                }
            });
        }
        bindValues.add(startRow);
        bindValues.add(endRow - startRow + 1);

        // Bind each value individually
        for (int i = 0; i < bindValues.size(); i++) {
            finalQuery.bind(i + 1, bindValues.get(i));
        }

        return finalQuery;
    }

    private SelectSelectStep<Record> selectSql() {
        final List<Field<?>> selectCols;
        if (isPivotMode && !pivotColumns.isEmpty()) {
            if (rowGroupsToInclude.isEmpty()) {
                selectCols = pivotColumns.stream().map(col -> DSL.field(DSL.name(col.getField()))).collect(toList());
            } else {
                selectCols = concat(rowGroupsToInclude.stream().map(DSL::field), extractPivotStatements())
                        .collect(toList());
            }
        } else {
            final List<Field<?>> valueCols = valueColumns.stream()
                    .map(valueCol -> {
                        if (valueCol.getAggFunc() == null || valueCol.getAggFunc().isEmpty()) {
                            return DSL.field(DSL.name(valueCol.getField()));
                        } else {
                            String aggFunction = valueCol.getAggFunc() + "(\"" + valueCol.getField() + "\")";
                            return DSL.field(aggFunction).as(DSL.name(valueCol.getField()));
                        }
                    })
                    .collect(toList());

            selectCols = concat(rowGroupsToInclude.stream().map(DSL::field), valueCols.stream())
                    .collect(toList());
        }

        return dsl.select(selectCols);
    }

    private Condition whereSql() {
        final var groupConditions = getGroupColumns().reduce(DSL.noCondition(), Condition::and);
        final var filterConditions = getFilters().reduce(DSL.noCondition(), Condition::and);
        return groupConditions.and(filterConditions);
    }

    private List<Field<?>> groupBySql() {
        return isGrouping ? rowGroupsToInclude.stream().map(DSL::field).collect(toList()) : List.of();
    }

    private List<SortField<?>> orderBySql() {
        final var num = this.isGrouping ? groupKeys.size() + 1 : Integer.MAX_VALUE;

        return sortModel.stream()
                .filter(model -> !this.isGrouping || rowGroups.contains(model.getColId()))
                .map(model -> {
                    final var field = DSL.field(DSL.name(model.getColId()));
                    return model.getSort().equalsIgnoreCase("asc") ? field.asc() : field.desc();
                })
                .limit(num)
                .collect(toList());
    }

    private Stream<Condition> getFilters() {
        return filterModel.entrySet().stream().map(entry -> {
            final var columnName = entry.getKey();
            final var filter = entry.getValue();

            if (filter instanceof SetColumnFilter) {
                return setFilter(columnName, (SetColumnFilter) filter);
            } else if (filter instanceof NumberColumnFilter) {
                return numberFilter(columnName, (NumberColumnFilter) filter);
            } else if (filter instanceof TextColumnFilter) {
                return textFilter(columnName, (TextColumnFilter) filter);
            }

            return DSL.noCondition();
        });
    }

    private Condition textFilter(final String columnName, final TextColumnFilter filter) {
        final var field = DSL.field(DSL.name(columnName), SQLDataType.VARCHAR);
        return switch (filter.getType()) {
            case "contains" -> field.like('%' + filter.getFilter() + '%');
            case "notContains" -> field.notLike('%' + filter.getFilter() + '%');
            case "equals" -> field.eq(filter.getFilter());
            case "notEqual" -> field.ne(filter.getFilter());
            case "startsWith" -> field.like(filter.getFilter() + '%');
            case "endsWith" -> field.like('%' + filter.getFilter());
            case "blank" -> field.isNull();
            case "notBlank" -> field.isNotNull();
            default -> DSL.noCondition();
        };
    }

    private Condition setFilter(final String columnName, final SetColumnFilter filter) {
        final var field = DSL.field(DSL.name(columnName), SQLDataType.VARCHAR);
        return filter.getValues().isEmpty() ? field.in("") : field.in(filter.getValues());
    }

    private Condition numberFilter(final String columnName, final NumberColumnFilter filter) {
        final var field = DSL.field(DSL.name(columnName), SQLDataType.INTEGER);
        return switch (filter.getType()) {
            case "equals" -> field.eq(filter.getFilter());
            case "notEqual" -> field.ne(filter.getFilter());
            case "lessThan" -> field.lt(filter.getFilter());
            case "lessThanOrEqual" -> field.le(filter.getFilter());
            case "greaterThan" -> field.gt(filter.getFilter());
            case "greaterThanOrEqual" -> field.ge(filter.getFilter());
            case "inRange" -> field.between(filter.getFilter(), filter.getFilterTo());
            default -> DSL.noCondition();
        };
    }

    private Stream<Field<?>> extractPivotStatements() {
        // Create pairs of pivot col and pivot value i.e. (DEALTYPE, Financial),
        // (BIDTYPE, Sell)...
        List<Set<Pair<String, String>>> pivotPairs = pivotValues.entrySet().stream()
                .map(e -> e.getValue().stream()
                        .map(pivotValue -> Pair.of(e.getKey(), pivotValue))
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .collect(Collectors.toList());

        // Create a cartesian product of CASE statements for all pivot and value columns
        // combinations
        // i.e. sum(CASE WHEN DEALTYPE = 'Financial' THEN CASE WHEN BIDTYPE = 'Sell'
        // THEN CURRENTVALUE END END)
        return Sets.cartesianProduct(pivotPairs)
                .stream()
                .flatMap(pairs -> {
                    String pivotColStr = pairs.stream()
                            .map(Pair::getRight)
                            .collect(Collectors.joining("_"));

                    return valueColumns.stream()
                            .map(valueCol -> {
                                // Initialize the case statement
                                CaseConditionStep<Integer> caseField = null;

                                for (var pair : pairs) {
                                    Field<String> currentField = DSL.field(DSL.name(pair.getLeft()), String.class);
                                    Field<Integer> valueField = DSL.field(DSL.name(valueCol.getField()), Integer.class);

                                    // Start or chain the case statement
                                    if (caseField == null) {
                                        caseField = DSL.when(currentField.eq(pair.getRight()), valueField);
                                    } else {
                                        caseField = caseField.when(currentField.eq(pair.getRight()), valueField);
                                    }
                                }

                                // Finalize the case statement with an otherwise clause
                                @SuppressWarnings("null")
                                Field<Integer> finalCaseField = caseField.otherwise((Integer) null);

                                Field<?> aggregatedField = DSL.field(
                                        "{0}({1})",
                                        SQLDataType.INTEGER,
                                        DSL.keyword(valueCol.getAggFunc()),
                                        finalCaseField);

                                return aggregatedField.as(pivotColStr + "_" + valueCol.getField());
                            }).collect(Collectors.toList()).stream();
                });
    }

    private List<String> getRowGroupsToInclude() {
        return rowGroups.stream()
                .limit(groupKeys.size() + 1)
                .collect(toList());
    }

    private Stream<Condition> getGroupColumns() {
        return IntStream.range(0, groupKeys.size())
                .mapToObj(i -> DSL.field(DSL.name(rowGroups.get(i))).eq(groupKeys.get(i)));
    }

    private List<String> getRowGroups() {
        return rowGroupCols.stream()
                .map(ColumnVO::getField)
                .collect(toList());
    }
}
