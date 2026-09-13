package com.vetclinic.reporting.service;

import com.vetclinic.reporting.client.SourceClients.AppointmentByDoctor;
import com.vetclinic.reporting.client.SourceClients.AppointmentCounts;
import com.vetclinic.reporting.client.SourceClients.AppointmentDaily;
import com.vetclinic.reporting.client.SourceClients.AppointmentStats;
import com.vetclinic.reporting.client.SourceClients.BookingStatsClient;
import com.vetclinic.reporting.client.SourceClients.Doctor;
import com.vetclinic.reporting.client.SourceClients.OrderDaily;
import com.vetclinic.reporting.client.SourceClients.OrderStatsClient;
import com.vetclinic.reporting.client.SourceClients.PaymentDaily;
import com.vetclinic.reporting.client.SourceClients.PaymentStatsClient;
import com.vetclinic.reporting.client.SourceClients.ProfileClient;
import com.vetclinic.reporting.dto.Interval;
import com.vetclinic.reporting.dto.InvalidReportRangeException;
import com.vetclinic.reporting.dto.Reports;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    private static final String TOKEN = "Bearer admin";

    @Mock private OrderStatsClient orderStats;
    @Mock private PaymentStatsClient paymentStats;
    @Mock private BookingStatsClient bookingStats;
    @Mock private ProfileClient profile;
    @InjectMocks private ReportingService service;

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    private static BigDecimal vnd(String amount) {
        return new BigDecimal(amount);
    }

    private static AppointmentCounts counts(long pending, long confirmed, long completed, long cancelled, long noShow) {
        return new AppointmentCounts(pending + confirmed + completed + cancelled + noShow,
                pending, confirmed, completed, cancelled, noShow);
    }

    // ---------- CN-46: doanh thu ----------

    @Test
    void dailyRevenueFillsEmptyDaysWithZeroAndAddsBothSources() {
        LocalDate from = d("2026-09-01");
        LocalDate to = d("2026-09-03");
        when(orderStats.daily(from.toString(), to.toString(), TOKEN)).thenReturn(List.of(
                new OrderDaily(d("2026-09-01"), 2, 1, 0, vnd("150000"))));
        when(paymentStats.daily(from.toString(), to.toString(), TOKEN)).thenReturn(List.of(
                new PaymentDaily(d("2026-09-01"), 1, vnd("50000")),
                new PaymentDaily(d("2026-09-03"), 2, vnd("300000"))));

        Reports.Revenue report = service.revenue(from, to, Interval.DAY, TOKEN);

        assertThat(report.points()).extracting(Reports.RevenuePoint::periodStart)
                .containsExactly(d("2026-09-01"), d("2026-09-02"), d("2026-09-03"));
        assertThat(report.points()).extracting(Reports.RevenuePoint::total)
                .containsExactly(vnd("200000"), BigDecimal.ZERO, vnd("300000"));
        assertThat(report.totals()).isEqualTo(new Reports.RevenueTotals(
                vnd("150000"), vnd("350000"), vnd("500000"), 1L, 3L));
        assertThat(report.unavailable()).isEmpty();
    }

    @Test
    void quarterlyRevenueGroupsMonthsAndClipsPeriodsToRange() {
        LocalDate from = d("2026-02-15");
        LocalDate to = d("2026-08-10");
        when(orderStats.daily(from.toString(), to.toString(), TOKEN)).thenReturn(List.of(
                new OrderDaily(d("2026-02-20"), 1, 1, 0, vnd("100")),
                new OrderDaily(d("2026-03-31"), 1, 1, 0, vnd("200")),
                new OrderDaily(d("2026-04-01"), 1, 1, 0, vnd("400"))));
        when(paymentStats.daily(from.toString(), to.toString(), TOKEN)).thenReturn(List.of());

        Reports.Revenue report = service.revenue(from, to, Interval.QUARTER, TOKEN);

        assertThat(report.points()).extracting(Reports.RevenuePoint::periodStart)
                .containsExactly(d("2026-01-01"), d("2026-04-01"), d("2026-07-01"));
        assertThat(report.points()).extracting(Reports.RevenuePoint::sales)
                .containsExactly(vnd("300"), vnd("400"), BigDecimal.ZERO);
        // Kỳ cuối bị cắt ở ngày "to" chứ không kéo tới hết quý.
        assertThat(report.points().get(2).periodEnd()).isEqualTo(to);
        assertThat(report.points().get(1).periodEnd()).isEqualTo(d("2026-06-30"));
    }

    @Test
    void sourceDownMeansNullAndListedNotZero() {
        LocalDate from = d("2026-09-01");
        LocalDate to = d("2026-09-02");
        when(orderStats.daily(from.toString(), to.toString(), TOKEN)).thenThrow(new RuntimeException("order-service down"));
        when(paymentStats.daily(from.toString(), to.toString(), TOKEN)).thenReturn(List.of(new PaymentDaily(from, 1, vnd("90000"))));

        Reports.Revenue report = service.revenue(from, to, Interval.DAY, TOKEN);

        assertThat(report.unavailable()).containsExactly(ReportingService.SALES);
        assertThat(report.totals().sales()).isNull();
        assertThat(report.totals().ordersCompleted()).isNull();
        assertThat(report.totals().total()).isEqualTo(vnd("90000"));
        assertThat(report.points()).allSatisfy(p -> assertThat(p.sales()).isNull());
    }

    @Test
    void rejectsBadRangesBeforeCallingAnySource() {
        assertThatThrownBy(() -> service.revenue(d("2026-09-02"), d("2026-09-01"), Interval.DAY, TOKEN))
                .isInstanceOf(InvalidReportRangeException.class);
        assertThatThrownBy(() -> service.revenue(d("2026-01-01"), d("2026-06-30"), Interval.DAY, TOKEN))
                .isInstanceOf(InvalidReportRangeException.class);
        assertThatThrownBy(() -> service.summary(d("2024-01-01"), d("2026-06-30"), TOKEN))
                .isInstanceOf(InvalidReportRangeException.class);
        verifyNoInteractions(orderStats, paymentStats, bookingStats, profile);
    }

    // ---------- CN-47: lịch khám ----------

    @Test
    void appointmentReportComputesRatesAndNamesDoctors() {
        LocalDate from = d("2026-09-01");
        LocalDate to = d("2026-09-30");
        UUID khoa = UUID.randomUUID();
        UUID unnamed = UUID.randomUUID();
        when(bookingStats.stats(from.toString(), to.toString(), TOKEN)).thenReturn(new AppointmentStats(
                List.of(new AppointmentDaily(d("2026-09-02"), counts(1, 1, 4, 3, 1)),
                        new AppointmentDaily(d("2026-09-20"), counts(0, 0, 0, 0, 0))),
                List.of(new AppointmentByDoctor(khoa, counts(1, 1, 4, 3, 1)))));
        when(profile.doctors()).thenReturn(List.of(new Doctor(khoa, " Trần Minh Khoa ", "Nội khoa"),
                new Doctor(unnamed, null, "Ngoại khoa")));

        Reports.Appointments report = service.appointments(from, to, Interval.MONTH, TOKEN);

        assertThat(report.totals().total()).isEqualTo(10);
        assertThat(report.cancellationRate()).isEqualTo(0.3);
        assertThat(report.noShowRate()).isEqualTo(0.1);
        assertThat(report.points()).hasSize(1);
        assertThat(report.byDoctor()).singleElement().satisfies(row -> {
            assertThat(row.doctorName()).isEqualTo("Trần Minh Khoa");
            assertThat(row.cancellationRate()).isEqualTo(0.3);
        });
    }

    @Test
    void noAppointmentsMeansNoRateRatherThanZeroPercent() {
        LocalDate day = d("2026-09-01");
        when(bookingStats.stats(day.toString(), day.toString(), TOKEN)).thenReturn(new AppointmentStats(List.of(), List.of()));
        when(profile.doctors()).thenReturn(List.of());

        Reports.Appointments report = service.appointments(day, day, Interval.DAY, TOKEN);

        assertThat(report.cancellationRate()).isNull();
        assertThat(report.points()).singleElement()
                .satisfies(p -> assertThat(p.counts()).isEqualTo(AppointmentCounts.ZERO));
    }

    @Test
    void profileDownStillReturnsCountsWithoutNames() {
        LocalDate day = d("2026-09-01");
        UUID doctor = UUID.randomUUID();
        when(bookingStats.stats(day.toString(), day.toString(), TOKEN)).thenReturn(new AppointmentStats(
                List.of(new AppointmentDaily(day, counts(0, 0, 2, 0, 0))),
                List.of(new AppointmentByDoctor(doctor, counts(0, 0, 2, 0, 0)))));
        when(profile.doctors()).thenThrow(new RuntimeException("profile-service down"));

        Reports.Appointments report = service.appointments(day, day, Interval.DAY, TOKEN);

        assertThat(report.byDoctor()).singleElement().satisfies(row -> {
            assertThat(row.doctorUserId()).isEqualTo(doctor);
            assertThat(row.doctorName()).isNull();
        });
    }

    @Test
    void appointmentReportIs503WhenBookingDown() {
        when(bookingStats.stats(any(), any(), any())).thenThrow(new RuntimeException("booking-service down"));

        assertThatThrownBy(() -> service.appointments(d("2026-09-01"), d("2026-09-02"), Interval.DAY, TOKEN))
                .isInstanceOf(SourceUnavailableException.class);
    }

    // ---------- CN-49: tổng quan ----------

    @Test
    void summaryKeepsWorkingPartsWhenOneSourceIsDown() {
        LocalDate from = d("2026-09-01");
        LocalDate to = d("2026-09-30");
        when(orderStats.daily(from.toString(), to.toString(), TOKEN)).thenReturn(List.of(
                new OrderDaily(d("2026-09-01"), 3, 1, 1, vnd("120000")),
                new OrderDaily(d("2026-09-05"), 2, 2, 0, vnd("80000"))));
        when(paymentStats.daily(from.toString(), to.toString(), TOKEN)).thenReturn(List.of(new PaymentDaily(from, 1, vnd("50000"))));
        when(bookingStats.stats(from.toString(), to.toString(), TOKEN)).thenThrow(new RuntimeException("booking-service down"));

        Reports.Summary summary = service.summary(from, to, TOKEN);

        assertThat(summary.totalRevenue()).isEqualTo(vnd("250000"));
        assertThat(summary.orders()).isEqualTo(new Reports.OrderTotals(5, 3, 1));
        assertThat(summary.appointments()).isNull();
        assertThat(summary.cancellationRate()).isNull();
        assertThat(summary.unavailable()).containsExactly(ReportingService.APPOINTMENTS);
    }
}
