package com.vetclinic.payment.messaging;

import java.util.List;
import java.util.UUID;

/**
 * Phát cùng lúc với {@link PaymentCompletedEvent} khi phiếu thu đơn thuốc đã thu tiền (routing key
 * payment.dispensed): các thuốc quầy xác nhận đã phát và số lượng, để product-service trừ kho thuốc.
 *
 * Tách khỏi PaymentCompletedEvent để pet-service (nghe sự kiện đó) không phải đổi gì. Khớp với
 * com.vetclinic.product.messaging.PrescriptionDispensedEvent.
 */
public record PrescriptionDispensedEvent(UUID paymentId, List<Line> lines) {

    public record Line(String medicationName, int quantity) {
    }
}
