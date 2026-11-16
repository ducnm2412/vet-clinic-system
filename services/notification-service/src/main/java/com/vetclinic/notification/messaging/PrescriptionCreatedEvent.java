package com.vetclinic.notification.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * Mirror tối thiểu của pet-service PrescriptionCreatedEvent: chỉ cần biết có phiếu thu mới và bác sĩ
 * kê mấy loại thuốc. Các trường khác (giá, dịch vụ...) bỏ qua.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrescriptionCreatedEvent(UUID medicalRecordId, List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String medicationName) {
    }

    public int drugCount() {
        return items == null ? 0 : items.size();
    }
}
