package com.vetclinic.pet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * {@code @Valid} trên danh sách thuốc là cần: không có nó thì ràng buộc của từng dòng thuốc không
 * được kiểm, một dòng thiếu tên thuốc sẽ đi thẳng xuống database rồi vỡ ở NOT NULL (500 thay vì
 * 400). Bản cũ bên booking-service thiếu chỗ này.
 */
public record MedicalRecordRequest(
        @NotBlank String diagnosis,
        String treatment,
        String notes,
        List<@Valid PrescriptionItemRequest> prescriptionItems
) {
}
