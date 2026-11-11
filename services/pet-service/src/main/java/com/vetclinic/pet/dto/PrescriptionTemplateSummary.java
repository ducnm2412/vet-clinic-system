package com.vetclinic.pet.dto;

import com.vetclinic.pet.domain.PrescriptionTemplate;

import java.util.UUID;

/** Dòng gọn trong ô chọn mẫu: đủ để bác sĩ nhận ra mẫu, chi tiết lấy riêng khi chọn. */
public record PrescriptionTemplateSummary(
        UUID id,
        String name,
        String diagnosis,
        int itemCount
) {
    public static PrescriptionTemplateSummary from(PrescriptionTemplate t) {
        return new PrescriptionTemplateSummary(t.getId(), t.getName(), t.getDiagnosis(), t.getItems().size());
    }
}
