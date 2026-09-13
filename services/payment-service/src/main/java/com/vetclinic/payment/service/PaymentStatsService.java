package com.vetclinic.payment.service;

import com.vetclinic.payment.dto.PaymentDailyStatsResponse;
import com.vetclinic.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/** CN-46: doanh thu dịch vụ khám theo ngày, chỉ ADMIN đọc được (qua reporting-service). */
@Service
@RequiredArgsConstructor
public class PaymentStatsService {

    /** Phải trùng múi giờ viết cứng trong câu SQL của PaymentRepository. */
    static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final long MAX_DAYS = 731;

    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public List<PaymentDailyStatsResponse> daily(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from phải trước hoặc bằng to");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng thời gian tối đa " + MAX_DAYS + " ngày");
        }

        return paymentRepository.sumCompletedPerDay(
                        from.atStartOfDay(CLINIC_ZONE).toInstant(),
                        to.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant())
                .stream()
                .map(row -> new PaymentDailyStatsResponse(
                        // Driver trả ngày là java.sql.Date hoặc LocalDate, số là Long hoặc BigInteger.
                        row[0] instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) row[0],
                        ((Number) row[1]).longValue(),
                        row[2] instanceof BigDecimal money ? money : new BigDecimal(row[2].toString())))
                .sorted(Comparator.comparing(PaymentDailyStatsResponse::date))
                .toList();
    }
}
