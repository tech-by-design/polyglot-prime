package lib.aide.tabular;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.ResultQuery;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.TableLike;
import org.jooq.impl.DSL;
import org.techbd.udi.auto.jooq.ingress.tables.GetFhirNeedsAttention;
import org.techbd.udi.auto.jooq.ingress.tables.GetFhirNeedsAttentionDetails;
import org.techbd.udi.auto.jooq.ingress.tables.GetFhirScnSubmission;
import org.techbd.udi.auto.jooq.ingress.tables.GetFhirScnSubmissionDetails;
import org.techbd.udi.auto.jooq.ingress.tables.GetInteractionHttpRequest;
import org.techbd.udi.auto.jooq.ingress.tables.GetMissingDatalakeSubmissionDetails;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JooqRowsSupplierForSP {

    private final DSLContext dslContext;
    private final String schemaName;
    private final String storedProcName;
    private final String paramsJson;
    private final TabularRowsRequestForSP payload;

    private static final Pattern VALID_PATTERN_FOR_SCHEMA_AND_TABLE_AND_COLUMN = Pattern.compile("^[a-zA-Z0-9_]+$");
    public static final String DATE_TIME_FORMAT_YMDHMS = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_TIME_FORMAT_MDYHMS = "MM-dd-yyyy HH:mm:ss";
    public static final String DATE_TIME_FORMAT_MDY = "MM-dd-yyyy";

    private JooqRowsSupplierForSP(Builder builder) {
        this.dslContext = builder.dslContext;
        this.schemaName = builder.schemaName;
        this.storedProcName = builder.storedProcName;
        this.paramsJson = builder.paramsJson;
        this.payload = builder.payload;
    }

    public static Builder builder(DSLContext dslContext) {
        return new Builder(dslContext);
    }

    public List<Map<String, Object>> fetchData() throws Exception {
        // Construct base query
        SelectJoinStep<Record> baseQuery = dslContext.select().from(getDynamicTablelike(storedProcName, paramsJson));

        // Apply filters
        Condition conditions = buildConditions(payload);

        // Apply pagination and sorting
        SelectConditionStep<?> conditionQuery = baseQuery.where(conditions);
        ResultQuery<?> finalQuery = applySortingAndPagination(conditionQuery, payload);

        // Fetch results
        Result<?> result = finalQuery.fetch();
        return formatData(result.intoMaps());
    }

    private Condition buildConditions(TabularRowsRequestForSP payload) {
        Condition conditions = DSL.trueCondition();

        if (payload.filterModel() != null && !payload.filterModel().isEmpty()) {
            for (Map.Entry<String, TabularRowsRequestForSP.FilterModel> entry : payload.filterModel().entrySet()) {
                String column = entry.getKey();
                TabularRowsRequestForSP.FilterModel filterModel = entry.getValue();

                if (filterModel.conditions() != null && !filterModel.conditions().isEmpty()) {
                    // Handle multiple conditions for the column
                    List<Condition> subConditions = filterModel.conditions().stream()
                            .map(condition -> buildSingleCondition(column, condition))
                            .collect(Collectors.toList());

                    Condition combinedSubConditions = combineConditions(subConditions, filterModel.operator());
                    conditions = combineConditions(List.of(conditions, combinedSubConditions), "AND");
                } else {
                    // Handle a single condition for the column
                    Condition singleCondition = buildSingleCondition(column, filterModel);
                    conditions = combineConditions(List.of(conditions, singleCondition), "AND");
                }
            }
        }

        return conditions;
    }

    private Condition combineConditions(List<Condition> conditions, String operator) {
        if (conditions == null || conditions.isEmpty()) {
            return DSL.trueCondition();
        }

        return conditions.stream().reduce((cond1, cond2) -> {
            switch (operator.toUpperCase()) {
                case "AND" -> {
                    return cond1.and(cond2);
                }
                case "OR" -> {
                    return cond1.or(cond2);
                }
                default ->
                    throw new IllegalArgumentException("Invalid operator in the payload: " + operator);
            }
        }).orElse(DSL.trueCondition());
    }

    private Condition buildSingleCondition(String column, TabularRowsRequestForSP.FilterCondition condition) {
        switch (condition.filterType()) {
            case "number" -> {
                return switch (condition.type()) { // Total 9 conditions for agNumberColumnFilter
                    case "equals" ->
                        DSL.field(column).eq(condition.filter());
                    case "notEqual" ->
                        DSL.field(column).ne(condition.filter());
                    case "lessThan" ->
                        DSL.field(column).lt(condition.filter());
                    case "lessThanOrEqual" ->
                        DSL.field(column).le(condition.filter());
                    case "greaterThan" ->
                        DSL.field(column).gt(condition.filter());
                    case "greaterThanOrEqual" ->
                        DSL.field(column).ge(condition.filter());
                    case "inRange" ->
                        DSL.field(column).between(condition.filter(), condition.filterTo());
                    case "blank" ->
                        DSL.field(column).isNull();
                    case "notBlank" ->
                        DSL.field(column).isNotNull();
                    default ->
                        throw new IllegalArgumentException("Unsupported condition type in payload: " + condition.type());
                };
            }
            case "text" -> { // Total 8 conditions for agTextColumnFilter
                return switch (condition.type()) {
                    case "contains" ->
                        DSL.field(column).contains((String) condition.filter());
                    case "notContains" ->
                        DSL.field(column).notContains((String) condition.filter());
                    case "equals" ->
                        DSL.field(column).equalIgnoreCase((String) condition.filter());
                    case "notEqual" ->
                        DSL.lower(DSL.field(column, String.class)).ne(DSL.lower(DSL.val((String) condition.filter())));
                    case "startsWith" ->
                        DSL.field(column).startsWithIgnoreCase((String) condition.filter());
                    case "endsWith" ->
                        DSL.field(column).endsWithIgnoreCase((String) condition.filter());
                    case "blank" ->
                        DSL.field(column).isNull();
                    case "notBlank" ->
                        DSL.field(column).isNotNull();
                    default ->
                        throw new IllegalArgumentException("Unsupported condition type in payload: " + condition.type());
                };
            }
            case "date" -> {
                return switch (condition.type()) {
                    case "equals" ->
                        DSL.field(column).eq(convertStringToLocalDate(condition.dateFrom(), DATE_TIME_FORMAT_YMDHMS));
                    case "notEqual" ->
                        DSL.field(column).ne(convertStringToLocalDate(condition.dateFrom(), DATE_TIME_FORMAT_YMDHMS));
                    case "lessThan" ->
                        DSL.field(column).lt(convertStringToLocalDate(condition.dateFrom(), DATE_TIME_FORMAT_YMDHMS));
                    case "greaterThan" ->
                        DSL.field(column).gt(convertStringToLocalDate(condition.dateFrom(), DATE_TIME_FORMAT_YMDHMS));
                    case "inRange" ->
                        DSL.field(column).between(
                        convertStringToLocalDate(condition.dateFrom(), DATE_TIME_FORMAT_YMDHMS),
                        convertStringToLocalDate(condition.dateTo(), DATE_TIME_FORMAT_YMDHMS));
                    case "blank" ->
                        DSL.field(column).isNull();
                    case "notBlank" ->
                        DSL.field(column).isNotNull();
                    default ->
                        throw new IllegalArgumentException("Unsupported condition type in payload: " + condition.type());
                };
            }
            case "boolean" -> { // Reserved for future use
                throw new IllegalArgumentException("Unsupported filter type in payload: " + condition.filterType());
            }
            case "set" -> { // Reserved for future use
                throw new IllegalArgumentException("Unsupported filter type in payload: " + condition.filterType());
            }
            case "multi" -> { // Reserved for future use
                throw new IllegalArgumentException("Unsupported filter type in payload: " + condition.filterType());
            }
            case "custom" -> { // Reserved for future use
                throw new IllegalArgumentException("Unsupported filter type in payload: " + condition.filterType());
            }
            default ->
                throw new IllegalArgumentException("Unsupported filter type in payload: " + condition.filterType());
        }
    }

    private Condition buildSingleCondition(String column, TabularRowsRequestForSP.FilterModel filterModel) {
        switch (filterModel.filterType()) {
            case "number" -> { // Total 9 conditions for agNumberColumnFilter
                return switch (filterModel.type()) {
                    case "equals" ->
                        DSL.field(column).eq(filterModel.filter());
                    case "notEqual" ->
                        DSL.field(column).ne(filterModel.filter());
                    case "lessThan" ->
                        DSL.field(column).lt(filterModel.filter());
                    case "lessThanOrEqual" ->
                        DSL.field(column).le(filterModel.filter());
                    case "greaterThan" ->
                        DSL.field(column).gt(filterModel.filter());
                    case "greaterThanOrEqual" ->
                        DSL.field(column).ge(filterModel.filter());
                    case "inRange" ->
                        DSL.field(column).between(filterModel.filter(), filterModel.filterTo());
                    case "blank" ->
                        DSL.field(column).isNull();
                    case "notBlank" ->
                        DSL.field(column).isNotNull();
                    default ->
                        throw new IllegalArgumentException("Unsupported condition type in payload: " + filterModel.type());
                };
            }
            case "text" -> { // Total 8 conditions for agTextColumnFilter
                return switch (filterModel.type()) {
                    case "contains" ->
                        DSL.field(column).contains((String) filterModel.filter());
                    case "notContains" ->
                        DSL.field(column).notContains((String) filterModel.filter());
                    case "equals" ->
                        DSL.field(column).equalIgnoreCase((String) filterModel.filter());
                    case "notEqual" ->
                        DSL.lower(DSL.field(column, String.class)).ne(DSL.lower(DSL.val((String) filterModel.filter())));
                    case "startsWith" ->
                        DSL.field(column).startsWithIgnoreCase((String) filterModel.filter());
                    case "endsWith" ->
                        DSL.field(column).endsWithIgnoreCase((String) filterModel.filter());
                    case "blank" ->
                        DSL.field(column).isNull();
                    case "notBlank" ->
                        DSL.field(column).isNotNull();
                    default ->
                        throw new IllegalArgumentException("Unsupported condition type in payload: " + filterModel.type());
                };
            }
            case "date" -> {
                return switch (filterModel.type()) {
                    case "equals" ->
                        DSL.field(column).eq(convertStringToLocalDate(filterModel.dateFrom(), DATE_TIME_FORMAT_YMDHMS));
                    case "notEqual" ->
                        DSL.field(column).ne(convertStringToLocalDate(filterModel.dateFrom(), DATE_TIME_FORMAT_YMDHMS));
                    case "lessThan" ->
                        DSL.field(column).lt(convertStringToLocalDate(filterModel.dateFrom(), DATE_TIME_FORMAT_YMDHMS));
                    case "greaterThan" ->
                        DSL.field(column).gt(convertStringToLocalDate(filterModel.dateFrom(), DATE_TIME_FORMAT_YMDHMS));
                    case "inRange" ->
                        DSL.field(column).between(
                        convertStringToLocalDate(filterModel.dateFrom(), DATE_TIME_FORMAT_YMDHMS),
                        convertStringToLocalDate(filterModel.dateTo(), DATE_TIME_FORMAT_YMDHMS));
                    case "blank" ->
                        DSL.field(column).isNull();
                    case "notBlank" ->
                        DSL.field(column).isNotNull();
                    default ->
                        throw new IllegalArgumentException("Unsupported condition type in payload: " + filterModel.type());
                };
            }
            case "boolean" -> { // Reserved for future use
                throw new IllegalArgumentException("Unsupported filter type in payload: " + filterModel.filterType());
            }
            case "set" -> { // Reserved for future use
                throw new IllegalArgumentException("Unsupported filter type in payload: " + filterModel.filterType());
            }
            case "multi" -> { // Reserved for future use
                throw new IllegalArgumentException("Unsupported filter type in payload: " + filterModel.filterType());
            }
            case "custom" -> { // Reserved for future use
                throw new IllegalArgumentException("Unsupported filter type in payload: " + filterModel.filterType());
            }
            default ->
                throw new IllegalArgumentException("Unsupported filter type in payload: " + filterModel.filterType());
        }
    }

    ResultQuery<?> applySortingAndPagination(SelectConditionStep<?> query, TabularRowsRequestForSP payload) {
        List<OrderField<?>> orderFields = new ArrayList<>();
        if (payload.sortModel() != null && !payload.sortModel().isEmpty()) {
            for (TabularRowsRequestForSP.SortModel sortModel : payload.sortModel()) {
                Field<?> field = DSL.field(DSL.name(sortModel.colId()));
                if ("asc".equalsIgnoreCase(sortModel.sort())) {
                    orderFields.add(field.asc());
                } else if ("desc".equalsIgnoreCase(sortModel.sort())) {
                    orderFields.add(field.desc());
                }
            }
            return query.orderBy(orderFields).limit(payload.startRow(), payload.endRow() - payload.startRow());
        }
        return query.limit(payload.startRow(), payload.endRow() - payload.startRow());
    }

    LocalDate convertStringToLocalDate(String dateString, String pattern) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDate.parse(dateString, formatter);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format: " + dateString, e);
        }
    }

    private List<Map<String, Object>> formatData(List<Map<String, Object>> data) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_MDYHMS);
        return data.stream()
                .map(row -> row.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            Object value = entry.getValue();
                            if (value != null) {
                                if (value instanceof OffsetDateTime offsetDateTime) {
                                    return offsetDateTime
                                            .atZoneSameInstant(ZoneId.of("America/New_York"))
                                            .toLocalDateTime()
                                            .format(formatter);
                                }
                            }
                            return value;
                        })))
                .collect(Collectors.toList());
    }

    TableLike<?> getDynamicTablelike(String storedProcName, String paramsJson) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_MDY);

        switch (storedProcName) {
            case "get_fhir_scn_submission" -> {
                Map<String, LocalDate> paramMap = parseDates(paramsJson, objectMapper, formatter);
                return new GetFhirScnSubmission().call(paramMap.get("start_date"), paramMap.get("end_date"));
            }
            case "get_fhir_scn_submission_details" -> {
                objectMapper = new ObjectMapper();
                Map<String, String> dateMap = objectMapper.readValue(paramsJson, Map.class);

                String tenantId = dateMap.get("tenant_id");
                formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy");
                LocalDate localStartDate = LocalDate.parse(dateMap.get("start_date"), formatter);
                LocalDate localEndDate = LocalDate.parse(dateMap.get("end_date"), formatter);

                return new GetFhirScnSubmissionDetails().call(tenantId, localStartDate, localEndDate);
            }
            case "get_fhir_needs_attention" -> {
                Map<String, LocalDate> paramMap = parseDates(paramsJson, objectMapper, formatter);
                return new GetFhirNeedsAttention().call(paramMap.get("start_date"), paramMap.get("end_date"));
            }
            case "get_interaction_http_request" -> {
                return new GetInteractionHttpRequest().call();
            }
            case "get_fhir_needs_attention_details", "get_missing_datalake_submission_details" -> {
                Map<String, LocalDate> dateMap = parseDates(paramsJson, objectMapper, formatter);
                Map<String, String> paramsMap = objectMapper.readValue(paramsJson, Map.class);
                String tenantId = paramsMap.get("tenant_id").toLowerCase();

                if (storedProcName.equals("get_fhir_needs_attention_details")) {
                    return new GetFhirNeedsAttentionDetails().call(tenantId, dateMap.get("start_date"), dateMap.get("end_date"));
                } else {
                    return new GetMissingDatalakeSubmissionDetails().call(tenantId, dateMap.get("start_date"), dateMap.get("end_date"));
                }
            }
            default ->
                throw new IllegalArgumentException("Invalid stored procedure name: " + storedProcName);
        }
    }

    private Map<String, LocalDate> parseDates(String paramsJson, ObjectMapper objectMapper, DateTimeFormatter formatter) throws Exception {
        Map<String, String> stringMap = objectMapper.readValue(paramsJson, Map.class);
        LocalDate startDate = LocalDate.parse(stringMap.get("start_date"), formatter);
        LocalDate endDate = LocalDate.parse(stringMap.get("end_date"), formatter);
        return Map.of(
                "start_date", startDate,
                "end_date", endDate
        );
    }

    public static class Builder {

        private final DSLContext dslContext;
        private String schemaName;
        private String storedProcName;
        private String paramsJson;
        private TabularRowsRequestForSP payload;

        private Builder(DSLContext dslContext) {
            this.dslContext = dslContext;
        }

        public Builder withSchemaName(String schemaName) {
            this.schemaName = schemaName;
            return this;
        }

        public Builder withStoredProcName(String storedProcName) {
            this.storedProcName = storedProcName;
            return this;
        }

        public Builder withParamsJson(String paramsJson) {
            this.paramsJson = paramsJson;
            return this;
        }

        public Builder withPayload(TabularRowsRequestForSP payload) {
            this.payload = payload;
            return this;
        }

        public JooqRowsSupplierForSP build() {
            return new JooqRowsSupplierForSP(this);
        }
    }
}
