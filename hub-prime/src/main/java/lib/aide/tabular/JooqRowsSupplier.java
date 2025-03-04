package lib.aide.tabular;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.SelectGroupByStep;
import org.jooq.SelectLimitStep;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.conf.RenderKeywordCase;
import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.util.NoOpUtils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public final class JooqRowsSupplier implements TabularRowsSupplier<JooqRowsSupplier.JooqProvenance> {

    static private final Logger LOG = LoggerFactory.getLogger(JooqRowsSupplier.class);

    public record TypableTable(Table<?> table, boolean stronglyTyped) {

        static public TypableTable fromTablesRegistry(@Nonnull Class<?> tablesRegistry, @Nullable String schemaName,
                                                      @Nonnull String tableLikeName) {
    
            // Attempt to find a generated table reference using reflection
            try {
                // String qualifiedTableName = schemaName != null ? schemaName.toUpperCase() + "." + tableLikeName.toUpperCase() : tableLikeName.toUpperCase();
                // final var field = tablesRegistry.getField(qualifiedTableName);

                // looking for Tables.TABLISH_NAME ("tablish" means table or view)
                final var field = tablesRegistry.getField(tableLikeName.toUpperCase());
                return new TypableTable((Table<?>) field.get(null), true);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return new TypableTable(DSL.table(schemaName != null ? DSL.name(schemaName, tableLikeName)
                        : DSL.name(tableLikeName)), false);
            }
        }
    
        public Field<Object> column(final String columnName) {
            if (this.stronglyTyped) {
                try {
                    // try to find the Tables.TABLE.COLUMN_NAME in jOOQ generated code
                    final var instanceInTableRef = table.getClass().getField(columnName.toUpperCase());
                    if (instanceInTableRef.get(table) instanceof Field<?> columnField) {
                        return columnField.coerce(Object.class);
                    } else {
                        return DSL.field(DSL.name(columnName));
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    NoOpUtils.ignore(e);
                }
            }
            return DSL.field(DSL.name(columnName));
        }
    }

    public record JooqQuery(Query query, List<Object> bindValues, boolean stronglyTyped) {

    }

    public record JooqProvenance(String fromSQL, List<Object> bindValues, boolean stronglyTyped) {

    }

    private final TabularRowsRequest request;
    private final TypableTable typableTable;
    private final DSLContext dsl;
    private final boolean includeGeneratedSqlInResp;
    private final boolean includeGeneratedSqlInErrorResp;
    private final Logger logger;
    private final Query customQuery;
    private final List<Object> customBindValues;

    private JooqRowsSupplier(final Builder builder) {
        this.request = builder.request;
        this.typableTable = builder.table;
        this.dsl = builder.dsl;
        this.includeGeneratedSqlInResp = builder.includeGeneratedSqlInResp;
        this.includeGeneratedSqlInErrorResp = builder.includeGeneratedSqlInErrorResp;
        this.logger = builder.logger;
        this.customQuery = builder.customQuery;
        this.customBindValues = builder.customBindValues;
    }

    public TabularRowsRequest request() {
        return request;
    }

    public boolean isStronglyTyped() {
        return typableTable.stronglyTyped();
    }

    public TypableTable table() {
        return typableTable;
    }

    @Override
    public TabularRowsResponse<JooqProvenance> response() {
        final var jq = query();
        final var provenance = new JooqProvenance(jq.query.getSQL(), jq.bindValues(), typableTable.stronglyTyped);
        try {
            Instant start = Instant.now();
            final var query = jq.query();
            final var result = dsl.fetch(query.getSQL(), jq.bindValues().toArray());
            final var data = result.intoMaps();
            // Format date fields in the result set
            final var formattedData = new ArrayList<Map<String, Object>>();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");
            for (Map<String, Object> row : data) {
                Map<String, Object> formattedRow = new HashMap<>(row);

                row.forEach((column, value) -> {

                    if (value instanceof OffsetDateTime) {
                        LOG.info("", value);
                        formattedRow.put(column, ((OffsetDateTime) value)
                                .atZoneSameInstant(ZoneId.of("America/New_York"))
                                .toLocalDateTime()
                                .format(formatter));
                    } else if (value instanceof java.sql.Date) {
                        // Convert java.sql.Date to LocalDate
                        LocalDate localDate = ((java.sql.Date) value).toLocalDate();
                        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy");
                        formattedRow.put(column, localDate.format(dateFormatter));
                    }
                });
                formattedData.add(formattedRow);
            }
            var lastRow = request.startRow() + formattedData.size();
            if (formattedData.size() < (request.endRow() - request.startRow())) {
                lastRow = -1;
            }
            Instant end = Instant.now();
            long timeTaken = Duration.between(start, end).toNanos();
            System.out.println(
                    "Time taken by JOOQ: %d ns (%d s)".formatted(timeTaken, Duration.between(start, end).toSeconds()));
            return new TabularRowsResponse<>(includeGeneratedSqlInResp ? provenance : null, formattedData, lastRow,
                    null);
        } catch (Exception e) {
            if (logger != null) {
                logger.error("JooqRowsSupplier error", e);
            }
            return new TabularRowsResponse<>(includeGeneratedSqlInResp ? provenance : null, null, -1,
            includeGeneratedSqlInErrorResp ? e.getMessage() : null);
        }
    }

    private Condition finalCondition;

    @SuppressWarnings("unchecked")
    public JooqQuery query() {
        final var selectFields = new ArrayList<Field<?>>();
        final var whereConditions = new ArrayList<Condition>();
        final var bindValues = new ArrayList<Object>();
        final var sortFields = new ArrayList<SortField<?>>();
        final var groupByFields = new ArrayList<Field<?>>();

        finalCondition = null;

        // // Adding columns to select
        // if (request.valueCols() != null)
        // request.valueCols().forEach(col ->
        // selectFields.add(typableTable.column(col.field())));
        // Check if groupKeys are available
        if (request.groupKeys() != null && !request.groupKeys().isEmpty()) {
            // Adding the select field '*'
            selectFields.add(DSL.field("*"));
            // Adding where conditions based on groupKeys and rowGroupCols
            for (int i = 0; i < request.rowGroupCols().size(); i++) {
                final var col = request.rowGroupCols().get(i);
                final var value = request.groupKeys().get(i);
                final var condition = typableTable.column(col.field()).eq(value);
                whereConditions.add(condition);
                bindValues.add(value);
            }
        } else {
            // Adding grouping
            if (request.rowGroupCols() != null) {
                request.rowGroupCols().forEach(col -> {
                    final var field = typableTable.column(col.field());
                    groupByFields.add(field);
                    selectFields.add(field);
                });
            }

            // Adding columns to select if no grouping
            if (groupByFields.isEmpty() && request.valueCols() != null) {
                request.valueCols().forEach(col -> selectFields.add(typableTable.column(col.field())));
            }
        }
        // Adding filters
        if (request.filterModel() != null) {
            request.filterModel().forEach((field, filter) -> {

                if (filter.operator() == null) {
                    final var singleWhereConditions = new ArrayList<Condition>();
                    final var condition = createCondition(field, filter);
                    LOG.info("filter.operator() : {}", filter.operator());
                    whereConditions.add(condition);
                    singleWhereConditions.add(condition);
                    if (finalCondition == null) {
                        finalCondition = DSL.and(singleWhereConditions);
                    } else {
                        finalCondition = DSL.and(finalCondition, DSL.and(singleWhereConditions));
                    }
                    if (filter.type().equals("like") || filter.type().equals("contains")) {
                        bindValues.add("%" + filter.filter() + "%");
                    } else if (filter.filter() != null) {
                        bindValues.add(filter.filter());
                    }
                    if (filter.type().equals("between")) {
                        bindValues.add(filter.secondFilter());
                    }
                    if (filter.type().equals("inRange")) {
                        // Parse the decoded startDateValue and endDateValue into LocalDateTime
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        LocalDateTime startDateTime = LocalDateTime.parse(filter.dateFrom(), formatter);
                        LocalDateTime endDateTime = LocalDateTime.parse(filter.dateTo(), formatter);

                        // Check if the time part of endDateTime is 00:00:00 and update to 23:59:59 if
                        // true
                        if (endDateTime.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                            endDateTime = endDateTime.with(LocalTime.of(23, 59, 59));
                        }

                        bindValues.add(startDateTime);
                        bindValues.add(endDateTime);
                    }
                } else {
                    final var multipleWhereConditions = new ArrayList<Condition>();
                    LOG.info("filter.conditions() exist");
                    filter.conditions().forEach((filterModel) -> {

                        LOG.info("filter.operator() exist");
                        // ******** */
                        final var condition = createConditionSub(field, filterModel.type(), filterModel.filter(),
                                filterModel.secondFilter(), filterModel.dateFrom(), filterModel.dateTo());
                        LOG.info("filter.operator() : {}", filter.operator());
                        whereConditions.add(condition);
                        if ("OR".equalsIgnoreCase(filter.operator())) {
                            LOG.info("OR Filter");
                            multipleWhereConditions.add(DSL.or(condition));
                        }
                        if ("AND".equalsIgnoreCase(filter.operator())) {
                            LOG.info("AND Filter");
                            multipleWhereConditions.add(DSL.and(condition));
                        }

                        LOG.info("filter.where condition :{}",
                                multipleWhereConditions.get(multipleWhereConditions.size() - 1));
                        if (filterModel.type().equals("like") || filterModel.type().equals("contains")) {
                            bindValues.add("%" + filterModel.filter() + "%");
                        } else if (filterModel.filter() != null) {
                            bindValues.add(filterModel.filter());
                        }
                        if (filterModel.type().equals("between")) {
                            bindValues.add(filterModel.secondFilter());
                        }
                        if (filterModel.type().equals("inRange")) { // Date related
                            // yyyy-MM-dd HH:mm:ss is the format obtained from the AGGrid. Convert dateFrom
                            // and dateTo
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                            LocalDateTime startDateTime = LocalDateTime.parse(filterModel.dateFrom(), formatter);
                            LocalDateTime endDateTime = LocalDateTime.parse(filterModel.dateTo(), formatter);

                            // Check if the time part of endDateTime is 00:00:00 and update to 23:59:59 if
                            // true
                            if (endDateTime.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                                endDateTime = endDateTime.with(LocalTime.of(23, 59, 59));
                            }

                            bindValues.add(startDateTime);
                            bindValues.add(endDateTime);
                        }

                    });
                    if (finalCondition != null) {

                        if ("OR".equalsIgnoreCase(filter.operator())) {
                            finalCondition = DSL.and(finalCondition, DSL.or(multipleWhereConditions));
                        }
                        if ("AND".equalsIgnoreCase(filter.operator())) {
                            finalCondition = DSL.and(finalCondition, DSL.and(multipleWhereConditions));
                        }
                    } else {
                        if ("OR".equalsIgnoreCase(filter.operator())) {
                            finalCondition = DSL.or(multipleWhereConditions);
                        }
                        if ("AND".equalsIgnoreCase(filter.operator())) {
                            finalCondition = DSL.and(multipleWhereConditions);
                        }
                    }

                }
            });
        }

        // Adding sorting
        if (request.sortModel() != null) {
            for (final var sort : request.sortModel()) {
                final var sortField = typableTable.column(sort.colId());
                switch (sort.sort()) {
                    case "asc" ->
                        sortFields.add(sortField.asc());
                    case "desc" ->
                        sortFields.add(sortField.desc());
                }
            }
        }

        // Adding grouping
        if (request.rowGroupCols() != null) {
            request.rowGroupCols().forEach(col -> {
                final var field = typableTable.column(col.field());
                groupByFields.add(field);
                selectFields.add(field);
            });
        }

        // Adding aggregations
        if (request.aggregationFunctions() != null) {
            request.aggregationFunctions().forEach(aggFunc -> {
                aggFunc.columns().forEach(col -> {
                    final var field = typableTable.column(col);
                    final var aggregationField = switch (aggFunc.functionName().toLowerCase()) {
                        case "sum" ->
                            DSL.sum(field.cast(Double.class));
                        case "avg" ->
                            DSL.avg(field.cast(Double.class));
                        case "count" ->
                            DSL.count(field);
                        default ->
                            throw new IllegalArgumentException(
                                    "Unknown aggregation function: " + aggFunc.functionName());
                    };
                    selectFields.add(aggregationField);
                });
            });
        }

        // Creating the base query
        final var limit = request.endRow() - request.startRow();
        if (customQuery != null) {
            LOG.info("Custom Query for Single Schema {} :", customQuery);

            final var select = groupByFields.isEmpty()
                    ? ((SelectLimitStep<Record>) customQuery)
                            .limit(request.startRow(), limit)
                    : ((SelectGroupByStep<Record>) customQuery)
                            .groupBy(groupByFields)
                            // .orderBy(sortFields)
                            .limit(request.startRow(), limit);
            LOG.info("Select Query : {}", select);
            if (customBindValues != null && customBindValues.size() > 0) {
                for (Object customBind : customBindValues) {
                    bindValues.add(customBind);
                }
            }

            bindValues.add(request.startRow());
            bindValues.add(limit);
            LOG.info("Prepared Select Statement : {}", select);
            return new JooqQuery(select, bindValues, typableTable.stronglyTyped);
        }

        LOG.info("Query for Single Schema {} :", typableTable.table);
        if (finalCondition == null) {
            final var select = groupByFields.isEmpty()
                    ? this.dsl.select(selectFields).from(typableTable.table).where(whereConditions).orderBy(sortFields)
                            .limit(request.startRow(), limit)
                    : this.dsl.select(selectFields).from(typableTable.table).where(whereConditions)
                            .groupBy(groupByFields)
                            .orderBy(sortFields)
                            .limit(request.startRow(), limit);
            LOG.info("Select Query : {}", select);
            bindValues.add(request.startRow());
            bindValues.add(limit);
            LOG.info("Prepared Select Statement : {}", select);
            return new JooqQuery(select, bindValues, typableTable.stronglyTyped);
        } else {
            final var select = groupByFields.isEmpty()
                    ? this.dsl.select(selectFields).from(typableTable.table).where(finalCondition).orderBy(sortFields)
                            .limit(request.startRow(), limit)
                    : this.dsl.select(selectFields).from(typableTable.table).where(finalCondition)
                            .groupBy(groupByFields)
                            .orderBy(sortFields)
                            .limit(request.startRow(), limit);
            LOG.info("Select Query : {}", select);
            bindValues.add(request.startRow());
            bindValues.add(limit);
            LOG.info("Prepared Select Statement : {}", select);
            return new JooqQuery(select, bindValues, typableTable.stronglyTyped);
        }

    }

    private Condition createCondition(final String field, final TabularRowsRequest.FilterModel filter) {
        return createConditionSub(field, filter.type(), filter.filter(), filter.secondFilter(), filter.dateFrom(),
                filter.dateTo());
    }

    private Condition createConditionSub(final String field, String type, Object filter, Object secondfilter,
            Object dateFrom, Object dateTo) {
        final var dslField = typableTable.column(field);
        return switch (type) {
            case "blank" ->
                dslField.isNull();
            case "notBlank" ->
                 dslField.isNotNull().and(DSL.condition("TRIM({0}) <> ''", dslField));
            case "like" ->
                dslField.likeIgnoreCase("%" + filter + "%");
            case "equals" ->
                dslField.equalIgnoreCase(filter.toString());
            case "notEqual" ->
                DSL.condition("{0} NOT ILIKE {1}", dslField, DSL.param(field, filter));
            case "number" ->
                dslField.eq(DSL.param(field, filter));
            case "date" ->
                dslField.eq(DSL.param(field, filter));
            case "contains" ->
                dslField.likeIgnoreCase("%" + filter + "%");
            case "notContains" ->
                dslField.notLikeIgnoreCase("%" + filter + "%");  // Only works if supported
             case "startsWith" ->
                dslField.startsWithIgnoreCase(filter);
            case "endsWith" ->
                dslField.endsWithIgnoreCase(filter);
            case "lessOrEqual" ->
                dslField.lessOrEqual(filter);
            case "greatersOrEqual" ->
                dslField.greaterOrEqual(filter);
            case "greaterThan" ->
                dslField.greaterThan(filter);
            case "lessThan" ->
                dslField.lessThan(filter);
            case "between" ->
                dslField.between(filter, secondfilter);
            case "inRange" ->
                dslField.between(dateFrom, dateTo);

            default ->
                throw new IllegalArgumentException(
                        "Unknown filter type '" + type + "' in filter for field '" + field
                                + "' see JooqRowsSupplier::createCondition");
        };
    }

    public static final class Builder {

        private TabularRowsRequest request;
        private TypableTable table;
        private DSLContext dsl;
        private boolean includeGeneratedSqlInResp;
        private boolean includeGeneratedSqlInErrorResp;
        private Logger logger;
        private Query customQuery;
        private List<Object> customBindValues;

        public Builder withRequest(final TabularRowsRequest request) {
            this.request = request;
            return this;
        }

        public Builder withTable(final Table<?> table) {
            this.table = new TypableTable(table, true);
            return this;
        }

        public Builder withTable(Class<?> tablesClass, @Nullable String schemaName, String tableLikeName) {
            this.table = TypableTable.fromTablesRegistry(tablesClass, schemaName, tableLikeName);
            return this;
        }

        public Builder withDSL(final DSLContext dsl) {
            this.dsl = dsl.configuration().derive(new Settings()
                    .withRenderFormatted(true)
                    .withRenderKeywordCase(RenderKeywordCase.UPPER)
                    .withRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_QUOTED)).dsl();
            return this;
        }

        public Builder includeGeneratedSqlInResp(boolean flag) {
            this.includeGeneratedSqlInResp = flag;
            return this;
        }

        public Builder includeGeneratedSqlInErrorResp(boolean flag) {
            this.includeGeneratedSqlInErrorResp = flag;
            return this;
        }

        public Builder withLogger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder withQuery(Class<?> tablesClass, String schema, String tableLikeName, Query customQuery,
                ArrayList<Object> bindValues) {
            TypableTable table = TypableTable.fromTablesRegistry(tablesClass, schema, tableLikeName);
            this.table = table;
            this.customQuery = customQuery;
            if (bindValues.size() > 0) {
                this.customBindValues = bindValues;
            }
            LOG.info("Custom Query prepared {}:", this.customQuery);
            return this;
        }

        public JooqRowsSupplier build() {
            return new JooqRowsSupplier(this);
        }
    }

}
