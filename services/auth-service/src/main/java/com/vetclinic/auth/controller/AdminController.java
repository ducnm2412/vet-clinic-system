package com.vetclinic.auth.controller;

import com.vetclinic.auth.domain.RoleName;
import com.vetclinic.auth.domain.UserStatus;
import com.vetclinic.auth.dto.CreateStaffAccountRequest;
import com.vetclinic.auth.dto.MessageResponse;
import com.vetclinic.auth.dto.PageResponse;
import com.vetclinic.auth.dto.UserResponse;
import com.vetclinic.auth.security.jwt.AuthenticatedUser;
import com.vetclinic.auth.service.AccountStatusService;
import com.vetclinic.auth.service.AuthService;
import com.vetclinic.auth.service.UserQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Toàn bộ /admin/** chỉ ADMIN — khai trong SecurityConfig.
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuthService authService;
    private final UserQueryService userQueryService;
    private final AccountStatusService accountStatusService;

    /** VD-18: danh sách tài khoản, mọi tham số tuỳ chọn. Mới tạo lên đầu; size tối đa 100. */
    @GetMapping("/users")
    public PageResponse<UserResponse> listUsers(@RequestParam(required = false) RoleName role,
                                                @RequestParam(required = false) UserStatus status,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return userQueryService.search(role, status, keyword, page, size);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse createStaffAccount(@Valid @RequestBody CreateStaffAccountRequest request) {
        return authService.createStaffAccount(request);
    }

    /** CN-08: khoá thay cho xoá. Không tự khoá mình, không khoá admin đang hoạt động cuối cùng (409). */
    @PutMapping("/users/{userId}/lock")
    public UserResponse lockUser(@PathVariable UUID userId, @AuthenticationPrincipal AuthenticatedUser admin) {
        return accountStatusService.lock(userId, admin.userId());
    }

    @PutMapping("/users/{userId}/unlock")
    public UserResponse unlockUser(@PathVariable UUID userId) {
        return accountStatusService.unlock(userId);
    }

    @DeleteMapping("/users/{userId}")
    public MessageResponse deleteUser(@PathVariable UUID userId) {
        return authService.deleteUser(userId);
    }
}
