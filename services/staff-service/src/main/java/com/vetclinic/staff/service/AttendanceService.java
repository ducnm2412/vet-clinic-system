package com.vetclinic.staff.service;

import com.vetclinic.staff.domain.AttendanceRecord;
import com.vetclinic.staff.domain.Shift;
import com.vetclinic.staff.dto.Attendance;
import com.vetclinic.staff.exception.StaffExceptions;
import com.vetclinic.staff.repository.AttendanceRecordRepository;
import com.vetclinic.staff.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * CN-38, CN-40: chấm công và tổng hợp giờ công.
 *
 * "Hôm nay" luôn tính theo giờ Việt Nam, không theo giờ máy chủ (container chạy UTC): ca chiều
 * bắt đầu 13h Việt Nam là 6h UTC cùng ngày, nhưng ca tối 21h Việt Nam đã sang ngày hôm sau ở UTC.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Dung sai khi so giờ vào/ra với ca: lệch tới chừng này vẫn tính là đúng giờ, quá thì mới ghi
     * muộn/sớm (kể cả số phút ghi ra là toàn bộ độ lệch, không trừ dung sai).
     */
    static final long GRACE_MINUTES = 10;

    /** Giờ bắt đầu sớm nhất và giờ kết thúc muộn nhất của các ca một người trong một ngày. */
    private record ShiftSpan(LocalTime start, LocalTime end) {
    }

    private record DayKey(UUID userId, LocalDate date) {
    }

    private final AttendanceRecordRepository attendanceRepository;
    private final ShiftRepository shiftRepository;

    @Transactional
    public Attendance.Record checkIn(UUID userId, String note) {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        attendanceRepository.findByUserIdAndDate(userId, today).ifPresent(existing -> {
            throw new StaffExceptions.InvalidStateException(existing.getCheckOutAt() == null
                    ? "Bạn đã vào ca lúc " + formatTime(existing.getCheckInAt()) + " rồi"
                    : "Hôm nay bạn đã chấm công xong, không vào ca lại được");
        });

        AttendanceRecord record = attendanceRepository.saveAndFlush(AttendanceRecord.builder()
                .userId(userId).date(today).checkInAt(Instant.now()).note(note).build());
        log.info("Vào ca: user={} ngày={}", userId, today);
        return toRecord(record, spanOf(userId, today));
    }

    @Transactional
    public Attendance.Record checkOut(UUID userId, String note) {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        AttendanceRecord record = attendanceRepository.findByUserIdAndDate(userId, today)
                .orElseThrow(() -> new StaffExceptions.InvalidStateException("Hôm nay bạn chưa vào ca"));

        if (record.getCheckOutAt() != null) {
            throw new StaffExceptions.InvalidStateException(
                    "Bạn đã ra ca lúc " + formatTime(record.getCheckOutAt()));
        }

        record.setCheckOutAt(Instant.now());
        if (note != null && !note.isBlank()) {
            record.setNote(note);
        }
        attendanceRepository.saveAndFlush(record);
        log.info("Ra ca: user={} ngày={} làm {} phút", userId, today, record.workedMinutes());
        return toRecord(record, spanOf(userId, today));
    }

    /** Bản ghi hôm nay, để giao diện biết nên hiện nút "Vào ca" hay "Ra ca". Chưa chấm thì null. */
    @Transactional(readOnly = true)
    public Attendance.Record today(UUID userId) {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        return attendanceRepository.findByUserIdAndDate(userId, today)
                .map(record -> toRecord(record, spanOf(userId, today)))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Attendance.Record> search(LocalDate from, LocalDate to, UUID userId) {
        ShiftService.validateRange(from, to);
        List<AttendanceRecord> records = attendanceRepository.search(from, to, userId);

        // Tra ca trực một lượt cho cả khoảng, tránh mỗi dòng một truy vấn.
        Map<DayKey, ShiftSpan> spans = spansOf(shiftRepository.search(from, to, userId));

        return records.stream()
                .map(r -> toRecord(r, spans.get(new DayKey(r.getUserId(), r.getDate()))))
                .toList();
    }

    /**
     * Các ngày có ca được xếp mà không có bản ghi chấm công. Chỉ tính ngày đã qua: hôm nay chưa
     * khép sổ (người ta có thể còn chưa tới ca), nên chưa gọi là vắng.
     */
    @Transactional(readOnly = true)
    public List<Attendance.Absence> absences(LocalDate from, LocalDate to, UUID userId) {
        ShiftService.validateRange(from, to);
        return findAbsences(
                spansOf(shiftRepository.search(from, to, userId)),
                attendanceRepository.search(from, to, userId));
    }

    private List<Attendance.Absence> findAbsences(Map<DayKey, ShiftSpan> spans, List<AttendanceRecord> records) {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        Set<DayKey> attended = new HashSet<>();
        for (AttendanceRecord r : records) {
            attended.add(new DayKey(r.getUserId(), r.getDate()));
        }
        return spans.entrySet().stream()
                .filter(e -> e.getKey().date().isBefore(today) && !attended.contains(e.getKey()))
                .map(e -> new Attendance.Absence(e.getKey().userId(), e.getKey().date(),
                        e.getValue().start(), e.getValue().end()))
                .sorted(Comparator.comparing(Attendance.Absence::date).reversed()
                        .thenComparing(a -> a.userId().toString()))
                .toList();
    }

    /** CN-40, CN-48: bảng công của cả phòng khám trong một kỳ. */
    @Transactional(readOnly = true)
    public Attendance.Timesheet timesheet(LocalDate from, LocalDate to, UUID userId) {
        ShiftService.validateRange(from, to);

        List<AttendanceRecord> records = attendanceRepository.search(from, to, userId);
        List<Shift> shifts = shiftRepository.search(from, to, userId);
        Map<DayKey, ShiftSpan> spans = spansOf(shifts);

        // [0] ngày công, [1] phút, [2] quên ra ca, [3] ca được xếp, [4] ngày muộn, [5] ngày về sớm, [6] ngày vắng
        Map<UUID, long[]> byUser = new TreeMap<>(Comparator.comparing(UUID::toString));
        for (AttendanceRecord record : records) {
            long[] row = byUser.computeIfAbsent(record.getUserId(), u -> new long[7]);
            ShiftSpan span = spans.get(new DayKey(record.getUserId(), record.getDate()));
            row[0]++;
            row[1] += record.workedMinutes();
            if (record.getCheckOutAt() == null) {
                row[2]++;
            }
            if (lateMinutes(record, span) > 0) {
                row[4]++;
            }
            if (earlyLeaveMinutes(record, span) > 0) {
                row[5]++;
            }
        }
        for (Shift shift : shifts) {
            byUser.computeIfAbsent(shift.getUserId(), u -> new long[7])[3]++;
        }
        for (Attendance.Absence absence : findAbsences(spans, records)) {
            byUser.computeIfAbsent(absence.userId(), u -> new long[7])[6]++;
        }

        List<Attendance.TimesheetRow> rows = byUser.entrySet().stream()
                .map(e -> new Attendance.TimesheetRow(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[2], e.getValue()[3], e.getValue()[4], e.getValue()[5], e.getValue()[6]))
                .sorted(Comparator.comparingLong(Attendance.TimesheetRow::totalMinutes).reversed())
                .toList();

        return new Attendance.Timesheet(from, to, rows);
    }

    private ShiftSpan spanOf(UUID userId, LocalDate date) {
        return spansOf(shiftRepository.findByUserIdAndDate(userId, date)).get(new DayKey(userId, date));
    }

    /**
     * Gộp các ca của một người trong một ngày thành một khoảng: vào muộn so với ca đầu, ra sớm so
     * với ca cuối. Ca sáng 8–12 và ca chiều 13–17 thành 8–17, nên nghỉ trưa không bị tính là về sớm.
     */
    private static Map<DayKey, ShiftSpan> spansOf(Collection<Shift> shifts) {
        Map<DayKey, ShiftSpan> spans = new HashMap<>();
        for (Shift s : shifts) {
            spans.merge(new DayKey(s.getUserId(), s.getDate()), new ShiftSpan(s.getStartTime(), s.getEndTime()),
                    (a, b) -> new ShiftSpan(
                            a.start().isBefore(b.start()) ? a.start() : b.start(),
                            a.end().isAfter(b.end()) ? a.end() : b.end()));
        }
        return spans;
    }

    /** Số phút vào muộn so với giờ bắt đầu ca; 0 nếu đúng giờ (trong dung sai), sớm, hoặc không có ca. */
    static long lateMinutes(AttendanceRecord record, ShiftSpan span) {
        if (span == null) {
            return 0;
        }
        LocalDateTime checkIn = record.getCheckInAt().atZone(CLINIC_ZONE).toLocalDateTime();
        long late = Duration.between(record.getDate().atTime(span.start()), checkIn).toMinutes();
        return late > GRACE_MINUTES ? late : 0;
    }

    /** Số phút ra ca sớm so với giờ kết thúc ca; 0 nếu chưa ra ca, đúng giờ (trong dung sai), hoặc không có ca. */
    static long earlyLeaveMinutes(AttendanceRecord record, ShiftSpan span) {
        if (span == null || record.getCheckOutAt() == null) {
            return 0;
        }
        LocalDateTime checkOut = record.getCheckOutAt().atZone(CLINIC_ZONE).toLocalDateTime();
        long early = Duration.between(checkOut, record.getDate().atTime(span.end())).toMinutes();
        return early > GRACE_MINUTES ? early : 0;
    }

    private static String formatTime(Instant instant) {
        return instant.atZone(CLINIC_ZONE).toLocalTime().withSecond(0).withNano(0).toString();
    }

    private static Attendance.Record toRecord(AttendanceRecord record, ShiftSpan span) {
        return new Attendance.Record(record.getId(), record.getUserId(), record.getDate(),
                record.getCheckInAt(), record.getCheckOutAt(), record.workedMinutes(),
                span == null ? null : span.start(), span == null ? null : span.end(),
                lateMinutes(record, span), earlyLeaveMinutes(record, span),
                record.getNote());
    }
}
