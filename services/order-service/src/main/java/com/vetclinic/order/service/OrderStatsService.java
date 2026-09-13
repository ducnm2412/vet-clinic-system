package com.vetclinic.order.service;

import com.vetclinic.order.dto.OrderDailyStatsResponse;
import com.vetclinic.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** CN-46: số liệu đơn hàng theo ngày, chỉ ADMIN đọc được (qua reporting-service). */
@Service
@RequiredArgsConstructor
public class OrderStatsService {

    /** Phải trùng múi giờ viết cứng trong câu SQL của OrderRepository. */
    static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final long MAX_DAYS = 731;

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<OrderDailyStatsResponse> daily(LocalDate from, LocalDate to) {
        validate(from, to);
        Instant start = from.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();

        Map<LocalDate, long[]> counts = new TreeMap<>();
        Map<LocalDate, BigDecimal> revenue = new TreeMap<>();

        for (Object[] row : orderRepository.countCreatedPerDay(start, end)) {
            counts.computeIfAbsent(toDate(row[0]), d -> new long[3])[0] = toLong(row[1]);
        }
        for (Object[] row : orderRepository.sumCompletedPerDay(start, end)) {
            LocalDate day = toDate(row[0]);
            counts.computeIfAbsent(day, d -> new long[3])[1] = toLong(row[1]);
            revenue.put(day, toMoney(row[2]));
        }
        for (Object[] row : orderRepository.countCancelledPerDay(start, end)) {
            counts.computeIfAbsent(toDate(row[0]), d -> new long[3])[2] = toLong(row[1]);
        }

        return counts.entrySet().stream()
                .map(e -> new OrderDailyStatsResponse(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2],
                        revenue.getOrDefault(e.getKey(), BigDecimal.ZERO)))
                .toList();
    }

    static void validate(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from phải trước hoặc bằng to");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng thời gian tối đa " + MAX_DAYS + " ngày");
        }
    }

    // Driver trả ngày là java.sql.Date hoặc LocalDate, số là Long hoặc BigInteger tuỳ phiên bản.
    private static LocalDate toDate(Object value) {
        return value instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) value;
    }

    private static long toLong(Object value) {
        return ((Number) value).longValue();
    }

    private static BigDecimal toMoney(Object value) {
        return value instanceof BigDecimal money ? money : new BigDecimal(value.toString());
    }
}
