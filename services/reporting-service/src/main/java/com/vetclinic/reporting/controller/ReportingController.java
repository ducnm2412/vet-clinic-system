package com.vetclinic.reporting.controller;

import com.vetclinic.reporting.dto.Interval;
import com.vetclinic.reporting.dto.Reports;
import com.vetclinic.reporting.service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * CN-46, CN-47, CN-49. Chỉ ADMIN.
 *
 * Bỏ trống from/to thì xem 30 ngày gần nhất tính tới hôm nay (giờ Việt Nam).
 */
@RestController
@RequestMapping("/reporting")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReportingController {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ReportingService reportingService;

    @GetMapping("/summary")
    public Reports.Summary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader("Authorization") String bearerToken) {
        LocalDate end = to != null ? to : today();
        return reportingService.summary(from != null ? from : end.minusDays(29), end, bearerToken);
    }

    @GetMapping("/revenue")
    public Reports.Revenue revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String interval,
            @RequestHeader("Authorization") String bearerToken) {
        LocalDate end = to != null ? to : today();
        return reportingService.revenue(from != null ? from : end.minusDays(29), end, Interval.parse(interval),
                bearerToken);
    }

    @GetMapping("/appointments")
    public Reports.Appointments appointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String interval,
            @RequestHeader("Authorization") String bearerToken) {
        LocalDate end = to != null ? to : today();
        return reportingService.appointments(from != null ? from : end.minusDays(29), end, Interval.parse(interval),
                bearerToken);
    }

    private static LocalDate today() {
        return LocalDate.now(CLINIC_ZONE);
    }
}
