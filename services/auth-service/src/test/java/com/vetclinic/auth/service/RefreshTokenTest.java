package com.vetclinic.auth.service;

import com.vetclinic.auth.domain.RoleName;
import com.vetclinic.auth.domain.User;
import com.vetclinic.auth.domain.UserStatus;
import com.vetclinic.auth.dto.AuthResponse;
import com.vetclinic.auth.dto.CreateStaffAccountRequest;
import com.vetclinic.auth.dto.LoginRequest;
import com.vetclinic.auth.exception.InvalidRefreshTokenException;
import com.vetclinic.auth.repository.RefreshTokenRepository;
import com.vetclinic.auth.repository.UserRepository;
import com.vetclinic.auth.security.jwt.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * VD-05 — làm mới phiên đăng nhập.
 *
 * Refresh token là chuỗi ngẫu nhiên lưu dạng băm, xoay vòng mỗi lần dùng. Mỗi test dưới đây
 * chốt một thuộc tính an toàn cụ thể, không chỉ "gọi được là xong".
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class RefreshTokenTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private AuthResponse loginAs(String email) {
        authService.createStaffAccount(new CreateStaffAccountRequest("Refresh", "Tester", email, PASSWORD, RoleName.STAFF));
        return authService.login(new LoginRequest(email, PASSWORD));
    }

    @Test
    void refresh_returnsNewUsablePair() {
        AuthResponse first = loginAs("rt-basic@example.com");

        AuthResponse second = authService.refresh(first.refreshToken());

        assertThat(second.accessToken()).isNotBlank();
        assertThat(second.refreshToken()).isNotBlank().isNotEqualTo(first.refreshToken());
        // Access token mới phải mang đủ danh tính và vai trò như lúc đăng nhập.
        var claims = jwtUtil.parseClaims(second.accessToken());
        assertThat(claims.getSubject()).isEqualTo("rt-basic@example.com");
        assertThat(jwtUtil.extractRoles(claims)).containsExactly("STAFF");
    }

    @Test
    void refresh_rotates_oldTokenStopsWorking() {
        AuthResponse first = loginAs("rt-rotate@example.com");
        authService.refresh(first.refreshToken());

        assertThatThrownBy(() -> authService.refresh(first.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshToken_isNotAJwt_soItCannotBeUsedAsAccessToken() {
        AuthResponse res = loginAs("rt-opaque@example.com");

        // Bản cũ là JWT ký cùng khoá với access token — chỉ tình cờ không dùng thay được vì
        // thiếu claim userId. Chuỗi ngẫu nhiên thì không có đường nào để lọt qua bộ lọc JWT.
        assertThat(res.refreshToken()).doesNotContain(".");
        assertThat(jwtUtil.isTokenValid(res.refreshToken())).isFalse();
    }

    @Test
    void refreshToken_isStoredHashed_neverInPlainText() {
        AuthResponse res = loginAs("rt-hash@example.com");

        // Lộ bảng refresh_tokens thì kẻ đọc được cũng không có token dùng được.
        assertThat(refreshTokenRepository.findAll())
                .extracting(t -> t.getTokenHash())
                .doesNotContain(res.refreshToken())
                .allSatisfy(hash -> assertThat(hash).hasSize(64));
    }

    @Test
    void refresh_unknownToken_throws() {
        assertThatThrownBy(() -> authService.refresh("khong-ton-tai"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_expiredToken_throws() {
        AuthResponse res = loginAs("rt-expired@example.com");
        refreshTokenRepository.findAll().forEach(t -> t.setExpiresAt(Instant.now().minusSeconds(1)));
        refreshTokenRepository.flush();

        assertThatThrownBy(() -> authService.refresh(res.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_whenUserNoLongerActive_throws() {
        AuthResponse res = loginAs("rt-inactive@example.com");
        User user = userRepository.findByEmail("rt-inactive@example.com").orElseThrow();
        user.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(user);

        // Refresh token còn hạn không có nghĩa là tài khoản còn được phép dùng.
        assertThatThrownBy(() -> authService.refresh(res.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logout_revokesToken() {
        AuthResponse res = loginAs("rt-logout@example.com");

        authService.logout(res.refreshToken());

        assertThatThrownBy(() -> authService.refresh(res.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logout_unknownToken_isSilent() {
        // Đăng xuất lần hai, hay đăng xuất bằng token đã hết hạn, không được báo lỗi —
        // người dùng chỉ muốn thoát ra, không cần biết token còn hay không.
        authService.logout("khong-ton-tai");
    }

    /**
     * Phát hiện token bị đánh cắp.
     *
     * Token đã xoay vòng mà vẫn có người đem ra dùng nghĩa là có hai bên cùng giữ nó — một
     * trong hai là kẻ gian, và không biết bên nào. Nên thu hồi SẠCH mọi phiên của tài khoản.
     *
     * Test này cố tình chạy NGOÀI transaction. Lệnh thu hồi xảy ra ngay trước khi ném lỗi, và
     * @Transactional mặc định rollback khi có exception — tức là thu hồi xong lại bị huỷ ngầm.
     * Nếu để test trong transaction bao ngoài như các test khác, nó sẽ đọc được thay đổi chưa
     * commit và xanh trong khi thực tế lệnh thu hồi chưa bao giờ được ghi xuống.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void refresh_reusingRotatedToken_revokesEverySessionOfThatUser() {
        String email = "rt-reuse@example.com";
        try {
            AuthResponse stolen = loginAs(email);
            AuthResponse legit = authService.refresh(stolen.refreshToken());

            assertThatThrownBy(() -> authService.refresh(stolen.refreshToken()))
                    .isInstanceOf(InvalidRefreshTokenException.class);

            // Token mới nhất của người dùng thật cũng phải chết theo — và việc đó phải đã commit.
            assertThatThrownBy(() -> authService.refresh(legit.refreshToken()))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        } finally {
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
        }
    }
}
