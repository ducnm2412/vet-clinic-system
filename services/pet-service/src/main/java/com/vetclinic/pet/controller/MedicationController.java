package com.vetclinic.pet.controller;

import com.vetclinic.pet.dto.MedicationRequest;
import com.vetclinic.pet.dto.MedicationResponse;
import com.vetclinic.pet.service.MedicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Danh mục thuốc: bác sĩ gõ để gợi ý khi kê đơn; bác sĩ và ADMIN cùng quản lý. */
@RestController
@RequestMapping("/medications")
@PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;

    /** {@code q} trống thì liệt kê theo tên. Mặc định chỉ thuốc đang dùng; màn quản lý xin thêm thuốc đã ẩn. */
    @GetMapping
    public List<MedicationResponse> search(@RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "false") boolean includeInactive,
                                           @RequestParam(required = false) Integer limit) {
        return medicationService.search(q, includeInactive, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MedicationResponse create(@Valid @RequestBody MedicationRequest request) {
        return medicationService.create(request);
    }

    @PutMapping("/{id}")
    public MedicationResponse update(@PathVariable UUID id, @Valid @RequestBody MedicationRequest request) {
        return medicationService.update(id, request);
    }

    /** Ẩn khỏi gợi ý (không xoá cứng). Bật lại bằng PUT với {@code active=true}. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        medicationService.deactivate(id);
    }
}
