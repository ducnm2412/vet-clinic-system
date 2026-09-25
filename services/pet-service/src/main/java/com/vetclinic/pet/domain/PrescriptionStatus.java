package com.vetclinic.pet.domain;

/**
 * Vòng đời một đơn thuốc: bác sĩ kê xong là PENDING (chờ khách trả tiền) → PAID khi
 * payment-service báo đã thu → RECEIVED khi khách nhận thuốc.
 *
 * Phòng khám thu tiền và giao thuốc cùng một lượt ở quầy, nên PAID thường chỉ tồn tại trong
 * khoảnh khắc: xem {@code MedicalRecordService#markPrescriptionPaid}.
 */
public enum PrescriptionStatus {
    PENDING,
    PAID,
    RECEIVED
}
