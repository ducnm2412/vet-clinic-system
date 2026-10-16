package com.vetclinic.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

/**
 * Lấy họ tên khách ở auth-service để điền vào hoá đơn quầy. Đính token của nhân viên đang mở hoá
 * đơn: endpoint bên kia chỉ mở cho STAFF/ADMIN.
 */
@FeignClient(name = "auth-service")
public interface AuthClient {

    @GetMapping("/auth/customers/{userId}")
    CustomerAccountView getCustomer(@PathVariable("userId") UUID userId,
                                    @RequestHeader("Authorization") String bearerToken);

    /** Chỉ khai các trường order-service dùng. */
    record CustomerAccountView(UUID id, String firstName, String lastName) {
    }
}
