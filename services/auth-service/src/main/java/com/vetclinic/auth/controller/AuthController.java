package com.vetclinic.auth.controller;

import com.vetclinic.auth.dto.AuthResponse;
import com.vetclinic.auth.dto.CreateCustomerAccountRequest;
import com.vetclinic.auth.dto.LoginRequest;
import com.vetclinic.auth.dto.MessageResponse;
import com.vetclinic.auth.dto.RefreshTokenRequest;
import com.vetclinic.auth.dto.RegisterRequest;
import com.vetclinic.auth.dto.UserResponse;
import com.vetclinic.auth.domain.RoleName;
import com.vetclinic.auth.dto.PageResponse;
import com.vetclinic.auth.service.UserQueryService;
import com.vetclinic.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserQueryService userQueryService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * CN-19: lễ tân mở tài khoản cho khách vãng lai ngay tại quầy.
     *
     * Nằm ở đây chứ không ở AdminController vì người làm việc này là nhân viên lễ tân, không phải
     * quản trị — mà cả nhánh /admin/** thì chỉ ADMIN vào được.
     *
     * Quyền khai trong SecurityConfig (STAFF hoặc ADMIN), không phải bằng @PreAuthorize: service
     * này chưa bật @EnableMethodSecurity nên annotation đó sẽ im lặng không chạy.
     */
    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createCustomerAccount(@Valid @RequestBody CreateCustomerAccountRequest request) {
        return authService.createCustomerAccount(request);
    }

    /**
     * CN-19: lễ tân tìm khách đang đứng ở quầy để đặt lịch hộ.
     *
     * Khoá cứng role CUSTOMER chứ không mở {@code /admin/users} cho nhân viên: lễ tân cần tìm
     * khách, không cần thấy danh sách tài khoản bác sĩ và quản trị.
     */
    @GetMapping("/customers")
    public PageResponse<UserResponse> searchCustomers(@RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return userQueryService.search(RoleName.CUSTOMER, null, keyword, page, size);
    }

    @GetMapping("/verify-email")
    public MessageResponse verifyEmail(@RequestParam String token) {
        return authService.verifyEmail(token);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Không đòi access token — cả lý do tồn tại của endpoint này là access token đã hết hạn.
     * Refresh token trong thân request chính là bằng chứng danh tính.
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authService.getCurrentUser(authentication.getName());
    }
}
