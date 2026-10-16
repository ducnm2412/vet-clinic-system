package com.vetclinic.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

/** Lấy số điện thoại khách ở profile-service để điền vào hoá đơn quầy (token nhân viên, STAFF/ADMIN). */
@FeignClient(name = "profile-service")
public interface ProfileClient {

    @GetMapping("/profile/customer/by-user/{userId}")
    CustomerProfileView getCustomerProfile(@PathVariable("userId") UUID userId,
                                           @RequestHeader("Authorization") String bearerToken);

    /** Chỉ khai các trường order-service dùng. */
    record CustomerProfileView(UUID userId, String phone) {
    }
}
