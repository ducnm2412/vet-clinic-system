package com.vetclinic.booking.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Bản sao tối thiểu của sự kiện pet-service phát khi bác sĩ lưu bệnh án lần đầu. Chỉ cần biết lịch hẹn
 * nào đã khám xong; các trường khác (thuốc, giá, dịch vụ) là việc của payment-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrescriptionCreatedEvent(UUID appointmentId) {
}
