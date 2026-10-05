package com.vetclinic.auth.controller;

import com.vetclinic.auth.dto.AccessTokenResponse;
import com.vetclinic.auth.dto.AuthResponse;
import com.vetclinic.auth.dto.CreateCustomerAccountRequest;
import com.vetclinic.auth.dto.FacebookLoginRequest;
import com.vetclinic.auth.dto.ForgotPasswordRequest;
import com.vetclinic.auth.dto.GoogleLoginRequest;
import com.vetclinic.auth.dto.LoginRequest;
import com.vetclinic.auth.dto.MessageResponse;
import com.vetclinic.auth.dto.RegisterRequest;
import com.vetclinic.auth.dto.ResetPasswordRequest;
import com.vetclinic.auth.dto.UserResponse;
import com.vetclinic.auth.domain.RoleName;
import com.vetclinic.auth.dto.PageResponse;
import com.vetclinic.auth.exception.InvalidRefreshTokenException;
import com.vetclinic.auth.security.RefreshCookieFactory;
import com.vetclinic.auth.service.UserQueryService;
import com.vetclinic.auth.service.AuthService;
import com.vetclinic.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
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
    private final PasswordResetService passwordResetService;
    private final RefreshCookieFactory refreshCookieFactory;

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
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    /** Dang nhap/dang ky bang Google - idToken lay thang tu Google phia trinh duyet. */
    @PostMapping("/google")
    public ResponseEntity<AccessTokenResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return withRefreshCookie(authService.loginWithGoogle(request.idToken()));
    }

    /** Dang nhap/dang ky bang Facebook - accessToken lay thang tu Facebook JS SDK phia trinh duyet. */
    @PostMapping("/facebook")
    public ResponseEntity<AccessTokenResponse> loginWithFacebook(@Valid @RequestBody FacebookLoginRequest request) {
        return withRefreshCookie(authService.loginWithFacebook(request.accessToken()));
    }

    /**
     * Không đòi access token — cả lý do tồn tại của endpoint này là access token đã hết hạn.
     * Refresh token nằm trong cookie httpOnly (trình duyệt tự gửi kèm) chính là bằng chứng danh
     * tính, không còn trong thân request nữa.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new InvalidRefreshTokenException();
        }
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
                .build();
    }

    /** Luôn 200 với cùng một thông báo, dù email có tồn tại hay không — không lộ tài khoản nào có thật. */
    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return passwordResetService.requestReset(request.email());
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return passwordResetService.reset(request.token(), request.newPassword());
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authService.getCurrentUser(authentication.getName());
    }

    private ResponseEntity<AccessTokenResponse> withRefreshCookie(AuthResponse authResponse) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.build(authResponse.refreshToken()).toString())
                .body(new AccessTokenResponse(authResponse.accessToken()));
    }
}
