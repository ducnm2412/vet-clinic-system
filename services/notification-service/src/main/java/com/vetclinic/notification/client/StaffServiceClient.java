package com.vetclinic.notification.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/** Hỏi staff-service ai có ca làm trong một ngày (yyyy-MM-dd). Gọi endpoint nội bộ bằng khoá dùng chung. */
@FeignClient(name = "staff-service")
public interface StaffServiceClient {

    @GetMapping("/internal/shifts/user-ids")
    List<UUID> userIdsOnShift(@RequestParam("date") String date,
                              @RequestHeader("X-Internal-Key") String internalKey);
}
