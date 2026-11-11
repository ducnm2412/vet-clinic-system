package com.vetclinic.pet.dto;

import com.vetclinic.pet.domain.PrescriptionTemplate;
import com.vetclinic.pet.domain.PrescriptionTemplateItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Chi tiết một mẫu, kèm tên/đơn vị/lưu ý của từng thuốc lấy từ danh mục để frontend chép thẳng vào
 * form bệnh án. {@code medicationActive=false} nghĩa là thuốc đã bị ẩn khỏi danh mục sau khi mẫu
 * được tạo — giao diện nên báo cho bác sĩ.
 */
public record PrescriptionTemplateResponse(
        UUID id,
        String name,
        String diagnosis,
        String treatment,
        String notes,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<Item> items
) {
    public record Item(
            UUID id,
            UUID medicationId,
            String medicationName,
            String medicationUnit,
            String medicationCaution,
            boolean medicationActive,
            String dosage,
            String frequency,
            Integer durationDays,
            String notes
    ) {
        static Item from(PrescriptionTemplateItem item) {
            return new Item(item.getId(), item.getMedication().getId(), item.getMedication().getName(),
                    item.getMedication().getUnit(), item.getMedication().getCaution(),
                    item.getMedication().isActive(), item.getDosage(), item.getFrequency(),
                    item.getDurationDays(), item.getNotes());
        }
    }

    public static PrescriptionTemplateResponse from(PrescriptionTemplate t) {
        return new PrescriptionTemplateResponse(t.getId(), t.getName(), t.getDiagnosis(), t.getTreatment(),
                t.getNotes(), t.getCreatedBy(), t.getCreatedAt(), t.getUpdatedAt(),
                t.getItems().stream().map(Item::from).toList());
    }
}
