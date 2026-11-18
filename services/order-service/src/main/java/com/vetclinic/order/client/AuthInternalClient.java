package com.vetclinic.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Danh sách tài khoản theo vai trò, gọi bằng khoá nội bộ (không có JWT người dùng). */
@FeignClient(name = "auth-service", contextId = "authInternalClient")
public interface AuthInternalClient {

    @GetMapping("/internal/users/ids")
    List<UUID> userIdsByRole(@RequestParam("role") String role, @RequestHeader("X-Internal-Key") String internalKey);

    @GetMapping("/internal/users/names")
    Map<UUID, String> userNames(@RequestParam("ids") List<UUID> ids, @RequestHeader("X-Internal-Key") String internalKey);
}
