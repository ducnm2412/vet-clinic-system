package com.vetclinic.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/** Ai có ca trong một ngày (yyyy-MM-dd), gọi bằng khoá nội bộ. */
@FeignClient(name = "staff-service", contextId = "staffInternalClient")
public interface StaffInternalClient {

    @GetMapping("/internal/shifts/user-ids")
    List<UUID> userIdsOnShift(@RequestParam("date") String date, @RequestHeader("X-Internal-Key") String internalKey);
}
