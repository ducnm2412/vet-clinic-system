package com.vetclinic.payment.messaging;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Bản sao riêng của payment-service cho event prescription.created (pet-service publish) —
// mỗi service tự sở hữu model của mình, Jackson chỉ cần khớp tên field JSON, không cần dùng
// chung class giữa hai service.
public record PrescriptionCreatedEvent(
        UUID medicalRecordId,
        UUID appointmentId,
        UUID customerUserId,
        List<Item> items,
        // Dịch vụ của lịch hẹn và giá tham khảo; null nếu không có hoặc pet-service bản cũ chưa gửi.
        String serviceName,
        BigDecimal servicePrice
) {
    // unitPrice: giá danh mục lúc kê (null = thuốc ngoài danh mục/chưa có giá, hoặc pet-service bản cũ chưa gửi).
    public record Item(String medicationName, String dosage, String frequency, Integer durationDays,
                       BigDecimal unitPrice) {
    }
}
