package com.vetclinic.booking.controller;

import com.vetclinic.booking.dto.ClinicServiceRequest;
import com.vetclinic.booking.dto.ClinicServiceResponse;
import com.vetclinic.booking.security.RoleUtils;
import com.vetclinic.booking.service.ClinicServiceCatalog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * VD-21. Danh sách công khai vì trang chủ và trang đặt lịch đều cần đọc khi chưa đăng nhập;
 * thêm và sửa là việc của ADMIN.
 */
@RestController
@RequestMapping("/booking/services")
@RequiredArgsConstructor
public class ClinicServiceController {

    private final ClinicServiceCatalog clinicServiceCatalog;

    @GetMapping
    public List<ClinicServiceResponse> list(Authentication authentication) {
        // Dịch vụ đã ngừng chỉ người trong phòng khám thấy — khách vãng lai gửi tham số gì cũng
        // chỉ nhận được danh sách đang cung cấp (cùng lối nghĩ với VD-01 bên product-service).
        boolean includeInactive = authentication != null
                && RoleUtils.hasAnyRole(authentication, "STAFF", "ADMIN");
        return clinicServiceCatalog.list(includeInactive);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ClinicServiceResponse create(@Valid @RequestBody ClinicServiceRequest request) {
        return clinicServiceCatalog.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ClinicServiceResponse update(@PathVariable UUID id, @Valid @RequestBody ClinicServiceRequest request) {
        return clinicServiceCatalog.update(id, request);
    }

    // Không có xoá: lịch hẹn cũ vẫn trỏ tới dịch vụ này (cùng lý do với VD-03).
    @PutMapping("/{id}/hide")
    @PreAuthorize("hasRole('ADMIN')")
    public ClinicServiceResponse hide(@PathVariable UUID id) {
        return clinicServiceCatalog.setActive(id, false);
    }

    @PutMapping("/{id}/unhide")
    @PreAuthorize("hasRole('ADMIN')")
    public ClinicServiceResponse unhide(@PathVariable UUID id) {
        return clinicServiceCatalog.setActive(id, true);
    }
}
