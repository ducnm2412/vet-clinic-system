package com.vetclinic.pet.controller;

import com.vetclinic.pet.dto.PrescriptionTemplateRequest;
import com.vetclinic.pet.dto.PrescriptionTemplateResponse;
import com.vetclinic.pet.dto.PrescriptionTemplateSummary;
import com.vetclinic.pet.security.jwt.AuthenticatedUser;
import com.vetclinic.pet.service.PrescriptionTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

/** Đơn mẫu theo bệnh thường gặp, dùng chung cả phòng khám. Bác sĩ và ADMIN cùng dùng và quản lý. */
@RestController
@RequestMapping("/prescription-templates")
@PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
@RequiredArgsConstructor
public class PrescriptionTemplateController {

    private final PrescriptionTemplateService templateService;

    /** Danh sách gọn cho ô chọn mẫu; {@code q} lọc theo tên (không phân biệt dấu). */
    @GetMapping
    public List<PrescriptionTemplateSummary> search(@RequestParam(required = false) String q) {
        return templateService.search(q);
    }

    @GetMapping("/{id}")
    public PrescriptionTemplateResponse get(@PathVariable UUID id) {
        return templateService.get(id);
    }

    /** Người tạo lấy từ token, không nhận từ client. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrescriptionTemplateResponse create(@AuthenticationPrincipal AuthenticatedUser me,
                                               @Valid @RequestBody PrescriptionTemplateRequest request) {
        return templateService.create(me.userId(), request);
    }

    /** Thay toàn bộ nội dung mẫu, kể cả danh sách thuốc. */
    @PutMapping("/{id}")
    public PrescriptionTemplateResponse update(@PathVariable UUID id,
                                               @Valid @RequestBody PrescriptionTemplateRequest request) {
        return templateService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        templateService.delete(id);
    }
}
