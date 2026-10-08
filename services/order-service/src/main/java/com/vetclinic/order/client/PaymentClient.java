package com.vetclinic.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Đọc khoản khám/thuốc ở payment-service để gộp vào hoá đơn tại quầy.
 *
 * Đính token của nhân viên đang lập hoá đơn: endpoint bên kia chỉ mở cho STAFF/ADMIN, giống cách
 * ProductClient.deductStock đang làm, nên hai service không cần danh tính riêng.
 */
@FeignClient(name = "payment-service")
public interface PaymentClient {

    @GetMapping("/payment/payments/{id}")
    PaymentView getPayment(@PathVariable("id") UUID id, @RequestHeader("Authorization") String bearerToken);

    /**
     * Chỉ khai các trường order-service dùng. status để String thay vì enum: khỏi phải đồng bộ
     * enum giữa hai service, giá trị lạ cũng không làm vỡ việc đọc.
     */
    record PaymentView(UUID id, UUID customerUserId, BigDecimal amount, String status) {
    }
}
