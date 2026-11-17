package com.vetclinic.staff.controller;

import com.vetclinic.staff.dto.Leaves;
import com.vetclinic.staff.security.jwt.AuthenticatedUser;
import com.vetclinic.staff.service.LeaveService;
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

/** Ngày nghỉ do admin ghi tay. Chỉ ADMIN xem và sửa: lý do nghỉ là chuyện riêng của từng người. */
@RestController
@RequestMapping("/staff/leaves")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Leaves.CreateResult create(@Valid @RequestBody Leaves.CreateRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser admin) {
        return leaveService.create(request, admin.userId());
    }

    @GetMapping
    public List<Leaves.Response> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID userId) {
        return leaveService.search(from, to, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        leaveService.delete(id);
    }
}
