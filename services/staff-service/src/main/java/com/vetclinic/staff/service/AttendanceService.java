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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

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
        return toRecord(record, shiftsOf(userId, today));
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
        return toRecord(record, shiftsOf(userId, today));
    }

    /** Bản ghi hôm nay, để giao diện biết nên hiện nút "Vào ca" hay "Ra ca". Chưa chấm thì null. */
    @Transactional(readOnly = true)
    public Attendance.Record today(UUID userId) {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        return attendanceRepository.findByUserIdAndDate(userId, today)
                .map(record -> toRecord(record, shiftsOf(userId, today)))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Attendance.Record> search(LocalDate from, LocalDate to, UUID userId) {
        ShiftService.validateRange(from, to);
        List<AttendanceRecord> records = attendanceRepository.search(from, to, userId);

        // Tra ca trực một lượt cho cả khoảng, tránh mỗi dòng một truy vấn.
        Map<String, Shift> shiftByKey = shiftRepository.search(from, to, userId).stream()
                .collect(Collectors.toMap(s -> key(s.getUserId(), s.getDate()), s -> s,
                        (a, b) -> a.getStartTime().isBefore(b.getStartTime()) ? a : b));

        return records.stream()
                .map(r -> toRecord(r, shiftByKey.get(key(r.getUserId(), r.getDate()))))
                .toList();
    }

    /** CN-40, CN-48: bảng công của cả phòng khám trong một kỳ. */
    @Transactional(readOnly = true)
    public Attendance.Timesheet timesheet(LocalDate from, LocalDate to, UUID userId) {
        ShiftService.validateRange(from, to);

        Map<UUID, long[]> byUser = new TreeMap<>(Comparator.comparing(UUID::toString));
        for (AttendanceRecord record : attendanceRepository.search(from, to, userId)) {
            long[] row = byUser.computeIfAbsent(record.getUserId(), u -> new long[4]);
            row[0]++;
            row[1] += record.workedMinutes();
            if (record.getCheckOutAt() == null) {
                row[2]++;
            }
        }
        for (Shift shift : shiftRepository.search(from, to, userId)) {
            byUser.computeIfAbsent(shift.getUserId(), u -> new long[4])[3]++;
        }

        List<Attendance.TimesheetRow> rows = byUser.entrySet().stream()
                .map(e -> new Attendance.TimesheetRow(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[2], e.getValue()[3]))
                .sorted(Comparator.comparingLong(Attendance.TimesheetRow::totalMinutes).reversed())
                .toList();

        return new Attendance.Timesheet(from, to, rows);
    }

    private Shift shiftsOf(UUID userId, LocalDate date) {
        return shiftRepository.findByUserIdAndDate(userId, date).stream()
                .min(Comparator.comparing(Shift::getStartTime))
                .orElse(null);
    }

    private static String key(UUID userId, LocalDate date) {
        return userId + "@" + date;
    }

    private static String formatTime(Instant instant) {
        return instant.atZone(CLINIC_ZONE).toLocalTime().withSecond(0).withNano(0).toString();
    }

    private static Attendance.Record toRecord(AttendanceRecord record, Shift shift) {
        return new Attendance.Record(record.getId(), record.getUserId(), record.getDate(),
                record.getCheckInAt(), record.getCheckOutAt(), record.workedMinutes(),
                shift == null ? null : shift.getStartTime(), shift == null ? null : shift.getEndTime(),
                record.getNote());
    }
}
