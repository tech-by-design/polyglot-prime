package org.techbd.service.http.hub.prime.ux.mco;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds the MCO "Monthly Volume Comparison" report from per-month rows returned by
 * {@code mco_data.get_mco_monthly_volume}. Pure logic (no Spring, no database) so the
 * calculations and formatting are unit tested.
 *
 * <p>Layout, matching the ESMF spreadsheet: for the selected month M and N months back,
 * each MCO row has M's status, totals, processed and errors, then N pairs of
 * "Total Records for M-i" followed by "M-(i-1) compared to M-i" (difference and percent).
 *
 * <p>Rules:
 * <ul>
 * <li>Difference = current − previous; blank when either month has no counted batch.</li>
 * <li>Percent = difference ÷ previous, rounded to a whole number, half away from zero
 * (like Excel's ROUND); blank when either month is missing or previous is 0.</li>
 * <li>No of Errors = 0 when the status is FULLY_PROCESSED (or the older PROCESSED),
 * otherwise errored_count; blank when unknown.</li>
 * <li>Counts use thousands separators; negatives use accounting parentheses, e.g. (4,467).</li>
 * </ul>
 */
public final class MonthlyVolumeComparison {

    public static final int DEFAULT_MONTHS_BACK = 2;
    public static final int MAX_MONTHS_BACK = 12;

    private static final Pattern REPORTING_MONTH = Pattern.compile("^(0[1-9]|1[0-2])-\\d{4}$");
    private static final DateTimeFormatter MM_YYYY = DateTimeFormatter.ofPattern("MM-yyyy", Locale.US);
    private static final DateTimeFormatter MONTH_ONLY = DateTimeFormatter.ofPattern("MMMM", Locale.US);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "FULLY_PROCESSED", "Processed",
            "PROCESSED", "Processed",
            "PARTIALLY_PROCESSED", "Partially Processed",
            "INPROGRESS", "In Progress",
            "ERRORED", "Errored");

    private MonthlyVolumeComparison() {
    }

    /** One row from {@code mco_data.get_mco_monthly_volume}: one MCO, one month. */
    public record MonthVolume(
            String tenantId,
            String mcoCode,
            String mcoName,
            YearMonth month,
            String status,
            Long totalRecords,
            Long processedCount,
            Long erroredCount,
            Long resubmittedCount,
            long qualifyingBatchCount) {
    }

    /**
     * One "Total Records" + "Compared to" column pair. {@code index} 1 is the month before the
     * selected month; the comparison is {@code currentMonth} vs {@code previousMonth}.
     */
    public record ComparisonPair(
            int index,
            String previousMonth,
            String previousMonthLabel,
            String currentMonth,
            String currentMonthLabel,
            String totalKey,
            String differenceKey,
            String percentKey) {
    }

    /** JSON payload for the page. Months are {@code MM-YYYY}. */
    public record Report(
            String reportingMonth,
            String reportingMonthLabel,
            int monthsBack,
            List<ComparisonPair> comparisons,
            List<Map<String, Object>> rows) {
    }

    // ---------------------------------------------------------------- inputs

    /**
     * Parses a {@code MM-YYYY} reporting month. Null or blank means "latest in the data" and
     * returns null.
     *
     * @throws IllegalArgumentException when the value is not {@code MM-YYYY}
     */
    public static YearMonth parseReportingMonth(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String trimmed = value.trim();
        if (!REPORTING_MONTH.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("reporting_month must be MM-YYYY");
        }
        try {
            return YearMonth.parse(trimmed, MM_YYYY);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("reporting_month must be MM-YYYY", e);
        }
    }

    public static boolean isValidMonthsBack(final int monthsBack) {
        return monthsBack >= 1 && monthsBack <= MAX_MONTHS_BACK;
    }

    public static String toReportingMonth(final YearMonth month) {
        return month == null ? null : month.format(MM_YYYY);
    }

    // ---------------------------------------------------------------- report

    /**
     * Builds the report.
     *
     * @param volumes        rows from the database function (any order)
     * @param reportingMonth selected month, or null to use the latest month present in {@code volumes}
     * @param monthsBack     number of comparisons (1–12)
     */
    public static Report build(final List<MonthVolume> volumes, final YearMonth reportingMonth,
            final int monthsBack) {
        if (!isValidMonthsBack(monthsBack)) {
            throw new IllegalArgumentException("monthsBack must be between 1 and " + MAX_MONTHS_BACK);
        }
        final List<MonthVolume> input = volumes == null ? List.of() : volumes;

        final YearMonth selected = reportingMonth != null
                ? reportingMonth
                : input.stream().map(MonthVolume::month).filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(null);
        if (selected == null) {
            return new Report(null, null, monthsBack, List.of(), List.of());
        }

        final List<YearMonth> months = new ArrayList<>(monthsBack + 1);
        for (int i = 0; i <= monthsBack; i++) {
            months.add(selected.minusMonths(i));
        }
        final boolean spansYears = months.get(0).getYear() != months.get(monthsBack).getYear();

        final List<ComparisonPair> comparisons = new ArrayList<>(monthsBack);
        for (int i = 1; i <= monthsBack; i++) {
            comparisons.add(new ComparisonPair(
                    i,
                    toReportingMonth(months.get(i)),
                    monthLabel(months.get(i), spansYears),
                    toReportingMonth(months.get(i - 1)),
                    monthLabel(months.get(i - 1), spansYears),
                    "total_m" + i,
                    "diff_" + i,
                    "pct_" + i));
        }

        // Group by MCO (tenant), keeping only months inside the window.
        final Map<String, Map<YearMonth, MonthVolume>> byMco = new LinkedHashMap<>();
        for (final MonthVolume v : input) {
            if (v == null || v.tenantId() == null || v.month() == null || !months.contains(v.month())) {
                continue;
            }
            byMco.computeIfAbsent(v.tenantId(), k -> new LinkedHashMap<>()).put(v.month(), v);
        }

        final List<Map<String, Object>> rows = byMco.values().stream()
                .map(perMonth -> buildRow(perMonth, months, comparisons))
                .sorted(Comparator
                        .comparing((Map<String, Object> r) -> String.valueOf(r.get("mco_name")),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(r -> String.valueOf(r.get("mco_code")), String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        return new Report(toReportingMonth(selected), monthLabel(selected, true), monthsBack, comparisons, rows);
    }

    private static Map<String, Object> buildRow(final Map<YearMonth, MonthVolume> perMonth,
            final List<YearMonth> months, final List<ComparisonPair> comparisons) {
        // Name and code from the most recent month that has them.
        final MonthVolume identity = months.stream().map(perMonth::get).filter(Objects::nonNull)
                .findFirst().orElseThrow();
        final MonthVolume current = perMonth.get(months.get(0));

        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenant_id", identity.tenantId());
        row.put("mco_code", identity.mcoCode());
        row.put("mco_name", identity.mcoName() != null ? identity.mcoName() : identity.tenantId());

        final String status = current == null ? null : current.status();
        final Long total = current == null ? null : current.totalRecords();
        final Long processed = current == null ? null : current.processedCount();
        final Long errors = current == null ? null : errorsFor(status, current.erroredCount());

        row.put("status_code", status);
        row.put("status", current == null ? "" : statusLabel(status));
        putCount(row, "total_records", total);
        putCount(row, "processed_count", processed);
        putCount(row, "errors", errors);
        row.put("resubmitted_count", current == null ? null : current.resubmittedCount());

        final List<String> multiBatchMonths = new ArrayList<>();
        for (final YearMonth m : months) {
            final MonthVolume v = perMonth.get(m);
            if (v != null && v.qualifyingBatchCount() > 1) {
                multiBatchMonths.add(toReportingMonth(m));
            }
        }
        row.put("multi_batch_months", multiBatchMonths);

        for (final ComparisonPair pair : comparisons) {
            final Long currentTotal = totalFor(perMonth.get(months.get(pair.index() - 1)));
            final Long previousTotal = totalFor(perMonth.get(months.get(pair.index())));
            final Long diff = difference(currentTotal, previousTotal);
            final Integer pct = percentChange(currentTotal, previousTotal);

            putCount(row, pair.totalKey(), previousTotal);
            putCount(row, pair.differenceKey(), diff);
            row.put(pair.percentKey(), pct);
            row.put(pair.percentKey() + "_display", formatPercent(pct));
        }
        return row;
    }

    private static Long totalFor(final MonthVolume v) {
        return v == null ? null : v.totalRecords();
    }

    private static void putCount(final Map<String, Object> row, final String key, final Long value) {
        row.put(key, value);
        row.put(key + "_display", formatCount(value));
    }

    // ---------------------------------------------------------------- calculations

    /** current − previous, or null when either is missing. */
    public static Long difference(final Long current, final Long previous) {
        if (current == null || previous == null) {
            return null;
        }
        return current - previous;
    }

    /**
     * Whole-number percent change, rounded half away from zero; null when either value is
     * missing or previous is 0.
     */
    public static Integer percentChange(final Long current, final Long previous) {
        if (current == null || previous == null || previous == 0L) {
            return null;
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(previous), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    /** 0 for a fully processed batch, otherwise the errored count (null stays null). */
    public static Long errorsFor(final String status, final Long erroredCount) {
        if (status != null && ("FULLY_PROCESSED".equals(status.trim()) || "PROCESSED".equals(status.trim()))) {
            return 0L;
        }
        return erroredCount;
    }

    // ---------------------------------------------------------------- formatting

    /** "4,467", "(4,467)" for negatives, "" for null. */
    public static String formatCount(final Long value) {
        if (value == null) {
            return "";
        }
        final String digits = NumberFormat.getIntegerInstance(Locale.US).format(Math.abs(value));
        return value < 0 ? "(" + digits + ")" : digits;
    }

    /** "-4%", "0%", "2%", "65,900%", "" for null. */
    public static String formatPercent(final Integer percent) {
        return percent == null ? "" : NumberFormat.getIntegerInstance(Locale.US).format(percent) + "%";
    }

    /** Display label for a database status; unknown values are shown title-cased. */
    public static String statusLabel(final String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        final String known = STATUS_LABELS.get(status.trim());
        if (known != null) {
            return known;
        }
        final String[] words = status.trim().toLowerCase(Locale.US).split("_+");
        final StringBuilder label = new StringBuilder();
        for (final String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    static String monthLabel(final YearMonth month, final boolean withYear) {
        return month.format(withYear ? MONTH_YEAR : MONTH_ONLY);
    }
}
