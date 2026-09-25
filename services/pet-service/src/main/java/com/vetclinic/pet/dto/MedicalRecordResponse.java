package com.vetclinic.pet.dto;

import com.vetclinic.pet.domain.PrescriptionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Có sẵn petId, chủ nuôi và bác sĩ: trang "Bệnh án còn treo" của bác sĩ và trang lịch sử khám của
 * một con vật không phải hỏi thêm booking-service dòng nào.
 */
public record MedicalRecordResponse(
        UUID id,
        UUID appointmentId,
        UUID petId,
        UUID customerUserId,
        UUID doctorUserId,
        String diagnosis,
        String treatment,
        String notes,
        PrescriptionStatus status,
        List<PrescriptionItemResponse> prescriptionItems,
        Instant createdAt,
        Instant updatedAt
) {
}
