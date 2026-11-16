package com.vetclinic.product.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * payment-service phát khi một phiếu thu đơn thuốc đã thu tiền (routing key payment.dispensed): danh
 * sách thuốc quầy xác nhận đã phát và số lượng. Khớp với com.vetclinic.payment.messaging.PrescriptionDispensedEvent;
 * field lạ bị Jackson bỏ qua.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrescriptionDispensedEvent(UUID paymentId, List<Line> lines) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(String medicationName, Integer quantity) {
    }
}
