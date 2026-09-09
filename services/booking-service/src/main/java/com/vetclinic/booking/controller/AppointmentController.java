package com.vetclinic.booking.controller;

import com.vetclinic.booking.domain.AppointmentStatus;
import com.vetclinic.booking.dto.AppointmentDetailResponse;
import com.vetclinic.booking.dto.AppointmentRequest;
import com.vetclinic.booking.dto.AppointmentResponse;
import com.vetclinic.booking.dto.AppointmentStatusUpdateRequest;
import com.vetclinic.booking.security.RoleUtils;
import com.vetclinic.booking.security.jwt.AuthenticatedUser;
import com.vetclinic.booking.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping("/booking/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public AppointmentResponse createAppointment(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @RequestHeader("Authorization") String bearerToken,
                                                  @Valid @RequestBody AppointmentRequest request) {
        return appointmentService.createAppointment(principal.userId(), bearerToken, request);
    }

    @GetMapping("/booking/appointments/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<AppointmentResponse> listMyAppointments(@AuthenticationPrincipal AuthenticatedUser principal) {
        return appointmentService.listMyAppointments(principal.userId());
    }

    @GetMapping("/booking/appointments/doctor/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<AppointmentResponse> listMyDoctorAppointments(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointmentService.search(date, null, principal.userId());
    }

    @GetMapping("/booking/appointments")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public List<AppointmentResponse> searchAppointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) UUID doctorUserId) {
        return appointmentService.search(date, status, doctorUserId);
    }

    @GetMapping("/booking/appointments/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public AppointmentDetailResponse getAppointmentDetail(@PathVariable UUID id,
                                                           @RequestHeader("Authorization") String bearerToken) {
        return appointmentService.getAppointmentDetail(id, bearerToken);
    }

    @PutMapping("/booking/appointments/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF','ADMIN')")
    public AppointmentResponse cancelAppointment(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  Authentication authentication,
                                                  @PathVariable UUID id) {
        boolean privileged = RoleUtils.hasAnyRole(authentication, "STAFF", "ADMIN");
        return appointmentService.cancelAppointment(id, principal.userId(), privileged);
    }

    @PutMapping("/booking/appointments/{id}/status")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public AppointmentResponse updateStatus(@PathVariable UUID id,
                                             @Valid @RequestBody AppointmentStatusUpdateRequest request) {
        return appointmentService.updateStatus(id, request.status());
    }
}
