package org.techbd.service.http.hub.prime.ux.mco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.techbd.service.http.hub.prime.ux.mco.MonthlyVolumeComparison.MonthVolume;
import org.techbd.service.http.hub.prime.ux.mco.MonthlyVolumeComparison.Report;

class MonthlyVolumeComparisonTest {

    private static final YearMonth SEP = YearMonth.of(2026, 9);
    private static final YearMonth AUG = YearMonth.of(2026, 8);
    private static final YearMonth JUL = YearMonth.of(2026, 7);
    private static final YearMonth JUN = YearMonth.of(2026, 6);
    private static final YearMonth MAY = YearMonth.of(2026, 5);

    private static MonthVolume volume(final String tenant, final String name, final YearMonth month,
            final String status, final Long total) {
        return new MonthVolume(tenant, tenant.toUpperCase(), name, month, status, total, total, 0L, 0L, 1);
    }

    // ---------------------------------------------------------------- calculations

    @Test
    void testDifferenceIsCurrentMinusPrevious() {
        assertThat(MonthlyVolumeComparison.difference(110L, 100L)).isEqualTo(10L);
        assertThat(MonthlyVolumeComparison.difference(95_533L, 100_000L)).isEqualTo(-4_467L);
        assertThat(MonthlyVolumeComparison.difference(5L, 5L)).isZero();
    }

    @Test
    void testDifferenceIsBlankWhenEitherMonthIsMissing() {
        assertThat(MonthlyVolumeComparison.difference(null, 100L)).isNull();
        assertThat(MonthlyVolumeComparison.difference(100L, null)).isNull();
    }

    @ParameterizedTest(name = "{0} vs {1} -> {2}%")
    @CsvSource({
            "102, 100, 2",
            "96, 100, -4",
            "100, 100, 0",
            "100001, 100000, 0", // 0.001% rounds to 0
            "95533, 100000, -4", // -4.467%
            "1025, 1000, 3", // 2.5% rounds half away from zero
            "975, 1000, -3", // -2.5% rounds half away from zero
            "1024, 1000, 2",
            "200, 100, 100"
    })
    void testPercentChangeRoundsToWholeNumbers(final long current, final long previous, final int expected) {
        assertThat(MonthlyVolumeComparison.percentChange(current, previous)).isEqualTo(expected);
    }

    @Test
    void testPercentIsBlankWhenPreviousIsZeroOrMissing() {
        assertThat(MonthlyVolumeComparison.percentChange(50L, 0L)).isNull();
        assertThat(MonthlyVolumeComparison.percentChange(0L, 0L)).isNull();
        assertThat(MonthlyVolumeComparison.percentChange(50L, null)).isNull();
        assertThat(MonthlyVolumeComparison.percentChange(null, 50L)).isNull();
    }

    @Test
    void testErrorsAreZeroOnceFullyProcessed() {
        assertThat(MonthlyVolumeComparison.errorsFor("FULLY_PROCESSED", 12L)).isZero();
        assertThat(MonthlyVolumeComparison.errorsFor("PROCESSED", 12L)).isZero();
        assertThat(MonthlyVolumeComparison.errorsFor("PARTIALLY_PROCESSED", 12L)).isEqualTo(12L);
        assertThat(MonthlyVolumeComparison.errorsFor("ERRORED", 12L)).isEqualTo(12L);
        assertThat(MonthlyVolumeComparison.errorsFor("INPROGRESS", null)).isNull();
    }

    // ---------------------------------------------------------------- formatting

    @Test
    void testCountsUseThousandsSeparatorsAndAccountingNegatives() {
        assertThat(MonthlyVolumeComparison.formatCount(4_467L)).isEqualTo("4,467");
        assertThat(MonthlyVolumeComparison.formatCount(-4_467L)).isEqualTo("(4,467)");
        assertThat(MonthlyVolumeComparison.formatCount(1_234_567L)).isEqualTo("1,234,567");
        assertThat(MonthlyVolumeComparison.formatCount(0L)).isEqualTo("0");
        assertThat(MonthlyVolumeComparison.formatCount(-5L)).isEqualTo("(5)");
        assertThat(MonthlyVolumeComparison.formatCount(null)).isEmpty();
    }

    @Test
    void testPercentsAreWholeNumbers() {
        assertThat(MonthlyVolumeComparison.formatPercent(-4)).isEqualTo("-4%");
        assertThat(MonthlyVolumeComparison.formatPercent(0)).isEqualTo("0%");
        assertThat(MonthlyVolumeComparison.formatPercent(2)).isEqualTo("2%");
        assertThat(MonthlyVolumeComparison.formatPercent(65_900)).isEqualTo("65,900%");
        assertThat(MonthlyVolumeComparison.formatPercent(-1_250)).isEqualTo("-1,250%");
        assertThat(MonthlyVolumeComparison.formatPercent(null)).isEmpty();
    }

    @Test
    void testStatusLabels() {
        assertThat(MonthlyVolumeComparison.statusLabel("FULLY_PROCESSED")).isEqualTo("Processed");
        assertThat(MonthlyVolumeComparison.statusLabel("PROCESSED")).isEqualTo("Processed");
        assertThat(MonthlyVolumeComparison.statusLabel("PARTIALLY_PROCESSED")).isEqualTo("Partially Processed");
        assertThat(MonthlyVolumeComparison.statusLabel("INPROGRESS")).isEqualTo("In Progress");
        assertThat(MonthlyVolumeComparison.statusLabel("ERRORED")).isEqualTo("Errored");
        assertThat(MonthlyVolumeComparison.statusLabel("SOME_NEW_STATUS")).isEqualTo("Some New Status");
        assertThat(MonthlyVolumeComparison.statusLabel(null)).isEmpty();
    }

    // ---------------------------------------------------------------- inputs

    @Test
    void testParsesReportingMonth() {
        assertThat(MonthlyVolumeComparison.parseReportingMonth("09-2026")).isEqualTo(SEP);
        assertThat(MonthlyVolumeComparison.parseReportingMonth(" 09-2026 ")).isEqualTo(SEP);
        assertThat(MonthlyVolumeComparison.parseReportingMonth(null)).isNull();
        assertThat(MonthlyVolumeComparison.parseReportingMonth("  ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "2026-09", "9-2026", "13-2026", "00-2026", "09-26", "09-2026'; --" })
    void testRejectsMalformedReportingMonth(final String bad) {
        assertThatThrownBy(() -> MonthlyVolumeComparison.parseReportingMonth(bad))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testMonthsBackRange() {
        assertThat(MonthlyVolumeComparison.isValidMonthsBack(1)).isTrue();
        assertThat(MonthlyVolumeComparison.isValidMonthsBack(12)).isTrue();
        assertThat(MonthlyVolumeComparison.isValidMonthsBack(0)).isFalse();
        assertThat(MonthlyVolumeComparison.isValidMonthsBack(13)).isFalse();
        assertThatThrownBy(() -> MonthlyVolumeComparison.build(List.of(), SEP, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------- report

    @Test
    void testBuildsSpreadsheetLayoutForFourComparisons() {
        final Report report = MonthlyVolumeComparison.build(List.of(
                volume("a", "Alpha", SEP, "FULLY_PROCESSED", 95_533L),
                volume("a", "Alpha", AUG, "FULLY_PROCESSED", 100_000L),
                volume("a", "Alpha", JUL, "FULLY_PROCESSED", 100_000L),
                volume("a", "Alpha", JUN, "FULLY_PROCESSED", 98_000L),
                volume("a", "Alpha", MAY, "FULLY_PROCESSED", 98_000L)), SEP, 4);

        assertThat(report.reportingMonth()).isEqualTo("09-2026");
        assertThat(report.comparisons()).hasSize(4);
        assertThat(report.comparisons().get(0).currentMonthLabel()).isEqualTo("September");
        assertThat(report.comparisons().get(0).previousMonthLabel()).isEqualTo("August");
        assertThat(report.comparisons().get(3).currentMonthLabel()).isEqualTo("June");
        assertThat(report.comparisons().get(3).previousMonthLabel()).isEqualTo("May");

        final Map<String, Object> row = report.rows().get(0);
        assertThat(row)
                .containsEntry("status", "Processed")
                .containsEntry("total_records_display", "95,533")
                .containsEntry("errors", 0L)
                // September vs August
                .containsEntry("total_m1_display", "100,000")
                .containsEntry("diff_1", -4_467L)
                .containsEntry("diff_1_display", "(4,467)")
                .containsEntry("pct_1", -4)
                .containsEntry("pct_1_display", "-4%")
                // August vs July
                .containsEntry("diff_2_display", "0")
                .containsEntry("pct_2_display", "0%")
                // July vs June
                .containsEntry("diff_3_display", "2,000")
                .containsEntry("pct_3_display", "2%");
    }

    @Test
    void testMissingMonthsAreBlankNotZero() {
        final Report report = MonthlyVolumeComparison.build(List.of(
                volume("a", "Alpha", SEP, "FULLY_PROCESSED", 120L),
                // no August file
                volume("a", "Alpha", JUL, "FULLY_PROCESSED", 100L)), SEP, 2);

        assertThat(report.rows().get(0))
                .containsEntry("total_m1", null)
                .containsEntry("total_m1_display", "")
                // September vs August: August missing
                .containsEntry("diff_1", null)
                .containsEntry("diff_1_display", "")
                .containsEntry("pct_1_display", "")
                // August vs July: August missing
                .containsEntry("diff_2_display", "")
                .containsEntry("total_m2_display", "100");
    }

    @Test
    void testSelectedMonthMissingLeavesItsColumnsBlank() {
        final Report report = MonthlyVolumeComparison.build(List.of(
                volume("a", "Alpha", AUG, "FULLY_PROCESSED", 100L)), SEP, 1);

        assertThat(report.rows().get(0))
                .containsEntry("status", "")
                .containsEntry("total_records_display", "")
                .containsEntry("errors_display", "")
                .containsEntry("total_m1_display", "100")
                .containsEntry("diff_1_display", "");
    }

    @Test
    void testPreviousMonthZeroShowsDifferenceButNoPercent() {
        final Report report = MonthlyVolumeComparison.build(List.of(
                volume("a", "Alpha", SEP, "FULLY_PROCESSED", 250L),
                volume("a", "Alpha", AUG, "FULLY_PROCESSED", 0L)), SEP, 1);

        assertThat(report.rows().get(0))
                .containsEntry("total_m1_display", "0")
                .containsEntry("diff_1_display", "250")
                .containsEntry("pct_1", null)
                .containsEntry("pct_1_display", "");
    }

    @Test
    void testErrorsFollowStatusForSelectedMonth() {
        final Report report = MonthlyVolumeComparison.build(List.of(
                new MonthVolume("a", "A", "Alpha", SEP, "PARTIALLY_PROCESSED", 100L, 90L, 10L, 1L, 1),
                new MonthVolume("b", "B", "Bravo", SEP, "FULLY_PROCESSED", 100L, 100L, 7L, 1L, 1),
                new MonthVolume("c", "C", "Charlie", SEP, "INPROGRESS", 100L, null, null, 0L, 1)),
                SEP, 1);

        assertThat(report.rows().get(0)).containsEntry("errors_display", "10");
        assertThat(report.rows().get(1)).containsEntry("errors_display", "0");
        assertThat(report.rows().get(2))
                .containsEntry("errors_display", "")
                .containsEntry("processed_count_display", "");
    }

    @Test
    void testRowsAreSortedByMcoNameIgnoringCase() {
        final Report report = MonthlyVolumeComparison.build(List.of(
                volume("t1", "molina", SEP, "FULLY_PROCESSED", 1L),
                volume("t2", "Amida Care", SEP, "FULLY_PROCESSED", 1L),
                volume("t3", "Healthfirst", SEP, "FULLY_PROCESSED", 1L)), SEP, 1);

        assertThat(report.rows()).extracting(r -> r.get("mco_name"))
                .containsExactly("Amida Care", "Healthfirst", "molina");
    }

    @Test
    void testDefaultsToLatestMonthInDataAndIgnoresMonthsOutsideWindow() {
        final Report report = MonthlyVolumeComparison.build(List.of(
                volume("a", "Alpha", AUG, "FULLY_PROCESSED", 10L),
                volume("a", "Alpha", SEP, "FULLY_PROCESSED", 20L),
                volume("a", "Alpha", MAY, "FULLY_PROCESSED", 999L)), null, 1);

        assertThat(report.reportingMonth()).isEqualTo("09-2026");
        assertThat(report.rows().get(0)).containsEntry("total_m1_display", "10");
        assertThat(report.rows().get(0).keySet()).noneMatch(k -> k.startsWith("total_m2"));
    }

    @Test
    void testEmptyDataGivesEmptyReport() {
        final Report report = MonthlyVolumeComparison.build(List.of(), null, 4);

        assertThat(report.reportingMonth()).isNull();
        assertThat(report.rows()).isEmpty();
        assertThat(report.comparisons()).isEmpty();
    }

    @Test
    void testFlagsMonthsWithMoreThanOneQualifyingBatch() {
        final Report report = MonthlyVolumeComparison.build(List.of(
                new MonthVolume("a", "A", "Alpha", SEP, "FULLY_PROCESSED", 5L, 5L, 0L, 0L, 3),
                new MonthVolume("a", "A", "Alpha", AUG, "FULLY_PROCESSED", 5L, 5L, 0L, 0L, 1)), SEP, 1);

        assertThat(report.rows().get(0)).containsEntry("multi_batch_months", List.of("09-2026"));
    }

    @Test
    void testLabelsIncludeYearWhenRangeCrossesYears() {
        final YearMonth feb = YearMonth.of(2027, 2);
        final Report report = MonthlyVolumeComparison.build(List.of(
                volume("a", "Alpha", feb, "FULLY_PROCESSED", 5L)), feb, 4);

        assertThat(report.comparisons().get(0).currentMonthLabel()).isEqualTo("February 2027");
        assertThat(report.comparisons().get(3).previousMonthLabel()).isEqualTo("October 2026");
    }
}
