package com.vetclinic.staff.controller;

import com.vetclinic.staff.dto.Shifts;
import com.vetclinic.staff.security.jwt.AuthenticatedUser;
import com.vetclinic.staff.service.ShiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** CN-39: xếp ca trực. Xếp ca là việc của quản lý nên chỉ ADMIN; người đi làm xem được lịch chung. */
@RestController
@RequestMapping("/staff/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Shifts.CreateResult create(@Valid @RequestBody Shifts.CreateRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser admin) {
        return shiftService.create(request, admin.userId());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','DOCTOR','ADMIN')")
    public List<Shifts.Response> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID userId) {
        return shiftService.search(from, to, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        shiftService.delete(id);
    }
}
