package com.vetclinic.notification.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Hỏi auth-service ai đang giữ một vai trò. Gọi endpoint nội bộ (không qua gateway) bằng khoá
 * dùng chung, vì lúc xử lý sự kiện nền không có JWT của người dùng nào để chuyển tiếp.
 */
@FeignClient(name = "auth-service")
public interface AuthServiceClient {

    @GetMapping("/internal/users/ids")
    List<UUID> userIdsByRole(@RequestParam("role") String role,
                             @RequestHeader("X-Internal-Key") String internalKey);
}
