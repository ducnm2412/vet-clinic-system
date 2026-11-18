package com.vetclinic.auth.controller;

import com.vetclinic.auth.domain.RoleName;
import com.vetclinic.auth.domain.UserStatus;
import com.vetclinic.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Endpoint giữa các service — chỉ vào được bằng khoá nội bộ (xem InternalApiKeyFilter). */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final UserRepository userRepository;

    /** Id các tài khoản đang hoạt động có vai trò này — để gửi thông báo cho cả nhóm. */
    @GetMapping("/users/ids")
    public List<UUID> userIdsByRole(@RequestParam RoleName role) {
        return userRepository.findIdsByRoleAndStatus(role, UserStatus.ACTIVE);
    }

    /** Họ tên theo id, để service khác ghi tên người phụ trách lên dữ liệu của mình. Id không có thì bỏ qua. */
    @GetMapping("/users/names")
    public Map<UUID, String> userNames(@RequestParam List<UUID> ids) {
        Map<UUID, String> names = new LinkedHashMap<>();
        userRepository.findAllById(ids).forEach(user -> names.put(user.getId(),
                ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                        + (user.getLastName() == null ? "" : user.getLastName())).trim()));
        return names;
    }
}
