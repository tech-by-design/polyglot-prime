package org.techbd.service.http.hub.prime.ux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.techbd.service.http.PermissionService;
import org.techbd.service.http.hub.prime.ux.mco.MonthlyVolumeComparison.MonthVolume;

import jakarta.servlet.http.HttpServletRequest;

class MonitoringControllerMonthlyVolumeTest {

    private DSLContext dsl;
    private PermissionService permissionService;
    private MonitoringController controller;

    @BeforeEach
    void setUp() throws Exception {
        dsl = mock(DSLContext.class);
        permissionService = mock(PermissionService.class);
        controller = new MonitoringController(mock(Presentation.class), dsl, permissionService);
    }

    private void allowScreen(final boolean allowed) {
        when(permissionService.isAllowedForRole(eq(MonitoringController.MONTHLY_VOLUME_LABEL),
                any(HttpServletRequest.class))).thenReturn(allowed);
    }

    @Test
    void testApiRefusesRoleWithoutScreenPermissionBeforeQuerying() {
        allowScreen(false);
        final var response = controller.getMcoMonthlyVolume(null, 2, new MockHttpServletRequest());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(dsl);
    }

    @Test
    void testApiRejectsMalformedReportingMonth() {
        allowScreen(true);
        final var response = controller.getMcoMonthlyVolume("2026-09'; drop table x; --", 2,
                new MockHttpServletRequest());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(dsl);
    }

    @Test
    void testApiRejectsMonthsBackOutOfRange() {
        allowScreen(true);
        assertThat(controller.getMcoMonthlyVolume("09-2026", 0, new MockHttpServletRequest()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.getMcoMonthlyVolume("09-2026", 13, new MockHttpServletRequest()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(dsl);
    }

    @Test
    void testMapsDatabaseRowToMonthVolume() {
        final var ctx = DSL.using(SQLDialect.POSTGRES);
        final Field<String> tenantId = DSL.field("tenant_id", String.class);
        final Field<String> mcoCode = DSL.field("mco_code", String.class);
        final Field<String> mcoName = DSL.field("mco_name", String.class);
        final Field<LocalDate> month = DSL.field("reporting_month_date", LocalDate.class);
        final Field<String> status = DSL.field("status", String.class);
        final Field<Integer> total = DSL.field("total_records", Integer.class);
        final Field<Integer> processed = DSL.field("processed_count", Integer.class);
        final Field<Integer> errored = DSL.field("errored_count", Integer.class);
        final Field<Integer> resubmitted = DSL.field("resubmitted_count", Integer.class);
        final Field<Long> batches = DSL.field("qualifying_batch_count", Long.class);

        final Record r = ctx.newRecord(tenantId, mcoCode, mcoName, month, status, total, processed, errored,
                resubmitted, batches);
        r.set(tenantId, "t1");
        r.set(mcoCode, "TXD");
        r.set(mcoName, "TxD");
        r.set(month, LocalDate.of(2026, 9, 1));
        r.set(status, "PARTIALLY_PROCESSED");
        r.set(total, 1200);
        r.set(processed, 1100);
        r.set(errored, null);
        r.set(resubmitted, 2);
        r.set(batches, 3L);

        assertThat(MonitoringController.toMonthVolume(r)).isEqualTo(new MonthVolume("t1", "TXD", "TxD",
                YearMonth.of(2026, 9), "PARTIALLY_PROCESSED", 1200L, 1100L, null, 2L, 3L));
    }
}
