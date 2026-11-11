package com.vetclinic.booking.controller;

import com.vetclinic.booking.dto.AppointmentResponse;
import com.vetclinic.booking.dto.AssignDoctorRequest;
import com.vetclinic.booking.dto.DoctorAvailabilityResponse;
import com.vetclinic.booking.dto.ProposeTimeRequest;
import com.vetclinic.booking.security.jwt.AuthenticatedUser;
import com.vetclinic.booking.service.AppointmentAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Admin xếp bác sĩ cho các yêu cầu đặt lịch; khách đồng ý hoặc huỷ giờ được đề xuất. */
@RestController
@RequiredArgsConstructor
public class AppointmentAssignmentController {

    private final AppointmentAssignmentService assignmentService;

    @GetMapping("/booking/appointments/awaiting")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AppointmentResponse> listAwaiting() {
        return assignmentService.listAwaiting();
    }

    @GetMapping("/booking/appointments/{id}/available-doctors")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DoctorAvailabilityResponse> availableDoctors(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) {
        return assignmentService.availableDoctors(id, date, time);
    }

    @PutMapping("/booking/appointments/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public AppointmentResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignDoctorRequest request) {
        return assignmentService.assign(id, request.doctorUserId());
    }

    @PutMapping("/booking/appointments/{id}/propose")
    @PreAuthorize("hasRole('ADMIN')")
    public AppointmentResponse propose(@PathVariable UUID id, @Valid @RequestBody ProposeTimeRequest request) {
        return assignmentService.propose(id, request.doctorUserId(), request.date(), request.startTime());
    }

    @PutMapping("/booking/appointments/{id}/accept-proposal")
    @PreAuthorize("hasRole('CUSTOMER')")
    public AppointmentResponse acceptProposal(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable UUID id) {
        return assignmentService.acceptProposal(id, principal.userId());
    }
}
