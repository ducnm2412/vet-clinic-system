package com.vetclinic.staff.controller;

import com.vetclinic.staff.dto.Attendance;
import com.vetclinic.staff.security.jwt.AuthenticatedUser;
import com.vetclinic.staff.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * CN-38, CN-40, CN-48: chấm công.
 *
 * Người đi làm chỉ chấm công cho CHÍNH MÌNH — userId lấy từ token, không nhận từ client, nếu
 * không thì ai cũng chấm công hộ người khác.
 */
@RestController
@RequestMapping("/staff/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/check-in")
    @PreAuthorize("hasAnyRole('STAFF','DOCTOR','ADMIN')")
    public Attendance.Record checkIn(@Valid @RequestBody(required = false) Attendance.Request request,
                                     @AuthenticationPrincipal AuthenticatedUser me) {
        return attendanceService.checkIn(me.userId(), noteOf(request));
    }

    @PostMapping("/check-out")
    @PreAuthorize("hasAnyRole('STAFF','DOCTOR','ADMIN')")
    public Attendance.Record checkOut(@Valid @RequestBody(required = false) Attendance.Request request,
                                      @AuthenticationPrincipal AuthenticatedUser me) {
        return attendanceService.checkOut(me.userId(), noteOf(request));
    }

    /** Bản ghi hôm nay của chính mình, để giao diện biết hiện nút Vào ca hay Ra ca. */
    @GetMapping("/me/today")
    @PreAuthorize("hasAnyRole('STAFF','DOCTOR','ADMIN')")
    public Attendance.Record today(@AuthenticationPrincipal AuthenticatedUser me) {
        return attendanceService.today(me.userId());
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('STAFF','DOCTOR','ADMIN')")
    public List<Attendance.Record> mine(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal AuthenticatedUser me) {
        return attendanceService.search(from, to, me.userId());
    }

    // ---------- CN-40, CN-48: quản lý xem của cả phòng khám ----------

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Attendance.Record> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID userId) {
        return attendanceService.search(from, to, userId);
    }

    @GetMapping("/timesheet")
    @PreAuthorize("hasRole('ADMIN')")
    public Attendance.Timesheet timesheet(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID userId) {
        return attendanceService.timesheet(from, to, userId);
    }

    private static String noteOf(Attendance.Request request) {
        return request == null ? null : request.note();
    }
}
