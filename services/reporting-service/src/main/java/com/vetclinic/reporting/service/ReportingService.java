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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * CN-46, CN-47, CN-49: ghép số liệu từ order-service (bán hàng), payment-service (tiền khám) và
 * booking-service (lịch khám). Không lưu gì — mỗi lần gọi là hỏi lại nguồn.
 *
 * Một nguồn hỏng không làm hỏng cả báo cáo: phần đó thành null và tên nguồn vào
 * {@code unavailable}. Xem quy ước trong {@link Reports}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportingService {

    public static final String SALES = "sales";
    public static final String SERVICES = "services";
    public static final String APPOINTMENTS = "appointments";

    static final long MAX_DAYS = 731;
    /** Theo ngày mà xem hai năm là 731 cột — không đọc được. */
    static final long MAX_DAYS_DAILY = 92;

    private final OrderStatsClient orderStats;
    private final PaymentStatsClient paymentStats;
    private final BookingStatsClient bookingStats;
    private final ProfileClient profile;

    public Reports.Revenue revenue(LocalDate from, LocalDate to, Interval interval, String bearerToken) {
        validate(from, to, interval);
        List<String> unavailable = new ArrayList<>();
        List<OrderDaily> orders = fetch(SALES, unavailable, () -> orderStats.daily(from.toString(), to.toString(), bearerToken));
        List<PaymentDaily> payments = fetch(SERVICES, unavailable, () -> paymentStats.daily(from.toString(), to.toString(), bearerToken));

        Map<LocalDate, BigDecimal> sales = bucket(from, to, interval, orders, OrderDaily::date, OrderDaily::revenue);
        Map<LocalDate, BigDecimal> services = bucket(from, to, interval, payments, PaymentDaily::date, PaymentDaily::revenue);

        List<Reports.RevenuePoint> points = new ArrayList<>();
        for (LocalDate start : periodStarts(from, to, interval)) {
            BigDecimal s = orders == null ? null : sales.getOrDefault(start, BigDecimal.ZERO);
            BigDecimal v = payments == null ? null : services.getOrDefault(start, BigDecimal.ZERO);
            points.add(new Reports.RevenuePoint(start, periodEnd(start, to, interval), s, v, sum(s, v)));
        }

        BigDecimal salesTotal = orders == null ? null : total(orders, OrderDaily::revenue);
        BigDecimal servicesTotal = payments == null ? null : total(payments, PaymentDaily::revenue);
        Reports.RevenueTotals totals = new Reports.RevenueTotals(salesTotal, servicesTotal,
                sum(salesTotal, servicesTotal),
                orders == null ? null : orders.stream().mapToLong(OrderDaily::ordersCompleted).sum(),
                payments == null ? null : payments.stream().mapToLong(PaymentDaily::payments).sum());

        return new Reports.Revenue(from, to, interval, points, totals, unavailable);
    }

    public Reports.Appointments appointments(LocalDate from, LocalDate to, Interval interval, String bearerToken) {
        validate(from, to, interval);
        AppointmentStats stats = fetch(APPOINTMENTS, new ArrayList<>(), () -> bookingStats.stats(from.toString(), to.toString(), bearerToken));
        if (stats == null) {
            // Báo cáo này chỉ có một nguồn — không có gì để trả một nửa.
            throw new SourceUnavailableException("Không lấy được số liệu lịch khám, thử lại sau");
        }

        Map<LocalDate, AppointmentCounts> byPeriod = new HashMap<>();
        for (AppointmentDaily day : stats.daily()) {
            byPeriod.merge(interval.periodStart(day.date()), day.counts(), AppointmentCounts::plus);
        }
        List<Reports.AppointmentPoint> points = periodStarts(from, to, interval).stream()
                .map(start -> new Reports.AppointmentPoint(start, periodEnd(start, to, interval),
                        byPeriod.getOrDefault(start, AppointmentCounts.ZERO)))
                .toList();

        AppointmentCounts totals = stats.daily().stream()
                .map(AppointmentDaily::counts)
                .reduce(AppointmentCounts.ZERO, AppointmentCounts::plus);

        Map<UUID, String> names = doctorNames();
        List<Reports.DoctorRow> byDoctor = stats.byDoctor().stream()
                .map((AppointmentByDoctor d) -> new Reports.DoctorRow(d.doctorUserId(), names.get(d.doctorUserId()),
                        d.counts(), rate(d.counts().cancelled(), d.counts().total())))
                .toList();

        return new Reports.Appointments(from, to, interval, totals, rate(totals.cancelled(), totals.total()),
                rate(totals.noShow(), totals.total()), points, byDoctor);
    }

    public Reports.Summary summary(LocalDate from, LocalDate to, String bearerToken) {
        validate(from, to, Interval.MONTH);
        List<String> unavailable = new ArrayList<>();
        List<OrderDaily> orders = fetch(SALES, unavailable, () -> orderStats.daily(from.toString(), to.toString(), bearerToken));
        List<PaymentDaily> payments = fetch(SERVICES, unavailable, () -> paymentStats.daily(from.toString(), to.toString(), bearerToken));
        AppointmentStats stats = fetch(APPOINTMENTS, unavailable, () -> bookingStats.stats(from.toString(), to.toString(), bearerToken));

        BigDecimal sales = orders == null ? null : total(orders, OrderDaily::revenue);
        BigDecimal services = payments == null ? null : total(payments, PaymentDaily::revenue);
        Reports.OrderTotals orderTotals = orders == null ? null : new Reports.OrderTotals(
                orders.stream().mapToLong(OrderDaily::ordersCreated).sum(),
                orders.stream().mapToLong(OrderDaily::ordersCompleted).sum(),
                orders.stream().mapToLong(OrderDaily::ordersCancelled).sum());
        AppointmentCounts appointments = stats == null ? null : stats.daily().stream()
                .map(AppointmentDaily::counts)
                .reduce(AppointmentCounts.ZERO, AppointmentCounts::plus);

        return new Reports.Summary(from, to, sales, services, sum(sales, services), orderTotals, appointments,
                appointments == null ? null : rate(appointments.cancelled(), appointments.total()), unavailable);
    }

    // ---------- khoảng thời gian ----------

    static void validate(LocalDate from, LocalDate to, Interval interval) {
        if (from.isAfter(to)) {
            throw new InvalidReportRangeException("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) {
            throw new InvalidReportRangeException("Chỉ xem được tối đa " + MAX_DAYS + " ngày một lần");
        }
        if (interval == Interval.DAY && days > MAX_DAYS_DAILY) {
            throw new InvalidReportRangeException("Xem theo ngày tối đa " + MAX_DAYS_DAILY
                    + " ngày — khoảng dài hơn hãy xem theo tháng hoặc quý");
        }
    }

    /** Mọi kỳ trong khoảng, kể cả kỳ không có số liệu — biểu đồ không được nhảy cóc qua ngày trống. */
    static List<LocalDate> periodStarts(LocalDate from, LocalDate to, Interval interval) {
        List<LocalDate> starts = new ArrayList<>();
        for (LocalDate p = interval.periodStart(from); !p.isAfter(to); p = interval.nextPeriodStart(p)) {
            starts.add(p);
        }
        return starts;
    }

    private static LocalDate periodEnd(LocalDate start, LocalDate to, Interval interval) {
        LocalDate end = interval.nextPeriodStart(start).minusDays(1);
        return end.isAfter(to) ? to : end;
    }

    // ---------- gộp số ----------

    private static <T> Map<LocalDate, BigDecimal> bucket(LocalDate from, LocalDate to, Interval interval, List<T> rows,
                                                         Function<T, LocalDate> date, Function<T, BigDecimal> amount) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        if (rows == null) {
            return result;
        }
        for (T row : rows) {
            LocalDate d = date.apply(row);
            if (d.isBefore(from) || d.isAfter(to)) {
                continue;
            }
            result.merge(interval.periodStart(d), nonNull(amount.apply(row)), BigDecimal::add);
        }
        return result;
    }

    private static <T> BigDecimal total(List<T> rows, Function<T, BigDecimal> amount) {
        return rows.stream().map(amount).map(ReportingService::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Tổng của các phần lấy được; cả hai đều không lấy được thì không có tổng. */
    private static BigDecimal sum(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return null;
        }
        return nonNull(a).add(nonNull(b));
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Tỉ lệ 0..1; không có lịch nào thì không có tỉ lệ (null), không phải 0%. */
    private static Double rate(long part, long whole) {
        return whole == 0 ? null : (double) part / whole;
    }

    // ---------- gọi nguồn ----------

    private <T> T fetch(String source, List<String> unavailable, Supplier<T> call) {
        try {
            return Objects.requireNonNull(call.get());
        } catch (RuntimeException e) {
            log.warn("Nguồn '{}' không trả số liệu: {}", source, e.toString());
            unavailable.add(source);
            return null;
        }
    }

    private Map<UUID, String> doctorNames() {
        Map<UUID, String> names = new HashMap<>();
        try {
            for (Doctor d : profile.doctors()) {
                if (d.userId() != null && d.fullName() != null && !d.fullName().isBlank()) {
                    names.put(d.userId(), d.fullName().trim());
                }
            }
        } catch (RuntimeException e) {
            // Thiếu tên thì bảng hiện "bác sĩ chưa rõ tên", số liệu vẫn đúng.
            log.warn("Không tra được tên bác sĩ: {}", e.toString());
        }
        return names;
    }
}
