package lib.aide.tabular;

import java.util.ArrayList;
import java.util.List;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.conf.RenderKeywordCase;
import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.slf4j.Logger;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public final class JooqRowsSupplier implements TabularRowsSupplier<JooqRowsSupplier.JooqProvenance> {
    public static record TypableTable(Table<?> table, boolean stronglyTyped) {
        static public TypableTable fromTablesRegistry(@Nonnull Class<?> tablesRegistry, @Nullable String schemaName,
                @Nonnull String tableLikeName) {

            // Attempt to find a generated table reference using reflection;
            // when we can use a jOOQ-generated class it means that special
            // column types like JSON will work (otherwise pure dynamic without
            // generated jOOQ assistance may treat certain columns incorrectly).
            try {
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
                    if (instanceInTableRef.get(table) instanceof org.jooq.Field<?> columnField) {
                        return columnField.coerce(Object.class);
                    } else {
                        return DSL.field(DSL.name(columnName));
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
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

    private JooqRowsSupplier(final Builder builder) {
        this.request = builder.request;
        this.typableTable = builder.table;
        this.dsl = builder.dsl;
        this.includeGeneratedSqlInResp = builder.includeGeneratedSqlInResp;
        this.includeGeneratedSqlInErrorResp = builder.includeGeneratedSqlInErrorResp;
        this.logger = builder.logger;
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
            final var query = jq.query();
            final var result = dsl.fetch(query.getSQL(), jq.bindValues().toArray());
            final var data = result.intoMaps();

            var lastRow = request.startRow() + data.size();
            if (data.size() < (request.endRow() - request.startRow())) {
                lastRow = -1;
            }

            return new TabularRowsResponse<>(includeGeneratedSqlInResp ? provenance : null, data, lastRow, null);
        } catch (Exception e) {
            if (logger != null)
                logger.error("JooqRowsSupplier error", e);
            return new TabularRowsResponse<>(includeGeneratedSqlInErrorResp ? provenance : null, null, -1,
                    e.getMessage());
        }
    }

    public JooqQuery query() {
        final var selectFields = new ArrayList<Field<?>>();
        final var whereConditions = new ArrayList<Condition>();
        final var bindValues = new ArrayList<Object>();
        final var sortFields = new ArrayList<SortField<?>>();
        final var groupByFields = new ArrayList<Field<?>>();

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
                final var condition = createCondition(field, filter);
                whereConditions.add(condition);
                bindValues.add(filter.filter());
            });
        }

        // Adding sorting
        if (request.sortModel() != null) {
            for (final var sort : request.sortModel()) {
                final var sortField = typableTable.column(sort.colId());
                switch (sort.sort()) {
                    case "asc" -> sortFields.add(sortField.asc());
                    case "desc" -> sortFields.add(sortField.desc());
                    default -> throw new IllegalArgumentException(
                        "Unknown sort order: Please specify a sort order.");
                }
            }
        }

        // Adding aggregations
        if (request.aggregationFunctions() != null) {
            request.aggregationFunctions().forEach(aggFunc -> {
                aggFunc.columns().forEach(col -> {
                    final var field = typableTable.column(col);
                    final var aggregationField = switch (aggFunc.functionName().toLowerCase()) {
                        case "sum" -> DSL.sum(field.cast(Double.class));
                        case "avg" -> DSL.avg(field.cast(Double.class));
                        case "count" -> DSL.count(field);
                        default -> throw new IllegalArgumentException(
                                "Unknown aggregation function: " + aggFunc.functionName());
                    };
                    selectFields.add(aggregationField);
                });
            });
        }

        // Creating the base query
        final var limit = request.endRow() - request.startRow();
        final var select = !groupByFields.isEmpty()
                ? this.dsl.select(selectFields).from(typableTable.table()).where(whereConditions).groupBy(groupByFields)
                        .orderBy(sortFields).limit(request.startRow(), limit)
                : this.dsl.select(selectFields).from(typableTable.table()).where(whereConditions).orderBy(sortFields)
                        .limit(request.startRow(), limit);

        bindValues.add(request.startRow());
        bindValues.add(limit);

        return new JooqQuery(select, bindValues, typableTable.stronglyTyped());
    }

    private Condition createCondition(final String field, final TabularRowsRequest.FilterModel filter) {
        final var dslField = typableTable.column(field);
        return switch (filter.type()) {
            case "like" -> dslField.likeIgnoreCase("%" + filter.filter() + "%");
            case "contains" -> dslField.likeIgnoreCase("%" + filter.filter() + "%");
            case "equals" -> dslField.eq(DSL.param(field, filter.filter()));
            case "number" -> dslField.eq(DSL.param(field, filter.filter()));
            case "date" -> dslField.eq(DSL.param(field, filter.filter()));
            default -> throw new IllegalArgumentException(
                    "Unknown filter type '" + filter.filterType() + "' in filter for field '" + field
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

        public JooqRowsSupplier build() {
            return new JooqRowsSupplier(this);
        }
    }
}
