package com.vetclinic.booking.controller;

import com.vetclinic.booking.dto.AppointmentStatsResponse;
import com.vetclinic.booking.service.AppointmentStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** CN-47: nguồn số liệu lịch khám cho reporting-service. */
@RestController
@RequiredArgsConstructor
public class AppointmentStatsController {

    private final AppointmentStatsService appointmentStatsService;

    @GetMapping("/booking/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public AppointmentStatsResponse stats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return appointmentStatsService.stats(from, to);
    }
}
