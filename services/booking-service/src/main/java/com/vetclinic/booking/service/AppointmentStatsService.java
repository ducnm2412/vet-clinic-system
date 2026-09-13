package com.vetclinic.booking.service;

import com.vetclinic.booking.dto.AppointmentStatsResponse;
import com.vetclinic.booking.dto.AppointmentStatsResponse.ByDoctor;
import com.vetclinic.booking.dto.AppointmentStatsResponse.Counts;
import com.vetclinic.booking.dto.AppointmentStatsResponse.Daily;
import com.vetclinic.booking.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** CN-47: thống kê lịch khám, chỉ ADMIN đọc được (qua reporting-service). */
@Service
@RequiredArgsConstructor
public class AppointmentStatsService {

    static final long MAX_DAYS = 731;

    private final AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    public AppointmentStatsResponse stats(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from phải trước hoặc bằng to");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng thời gian tối đa " + MAX_DAYS + " ngày");
        }

        List<Daily> daily = appointmentRepository.countByDayAndStatus(from, to).stream()
                .map(row -> new Daily(toDate(row[0]), counts(row)))
                .sorted(Comparator.comparing(Daily::date))
                .toList();

        // Bác sĩ nhiều ca nhất lên đầu.
        List<ByDoctor> byDoctor = appointmentRepository.countByDoctorAndStatus(from, to).stream()
                .map(row -> new ByDoctor(toUuid(row[0]), counts(row)))
                .sorted(Comparator.comparingLong((ByDoctor d) -> d.counts().total()).reversed())
                .toList();

        return new AppointmentStatsResponse(daily, byDoctor);
    }

    private static Counts counts(Object[] row) {
        return new Counts(n(row[1]), n(row[2]), n(row[3]), n(row[4]), n(row[5]), n(row[6]));
    }

    private static long n(Object value) {
        return ((Number) value).longValue();
    }

    // Driver trả ngày là java.sql.Date hoặc LocalDate, uuid là UUID hoặc chuỗi tuỳ phiên bản.
    private static LocalDate toDate(Object value) {
        return value instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) value;
    }

    private static UUID toUuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }
}
