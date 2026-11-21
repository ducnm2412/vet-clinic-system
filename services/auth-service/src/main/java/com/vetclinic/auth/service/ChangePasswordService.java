package com.vetclinic.auth.service;

import com.vetclinic.auth.domain.User;
import com.vetclinic.auth.domain.UserStatus;
import com.vetclinic.auth.dto.ChangePasswordRequest;
import com.vetclinic.auth.dto.MessageResponse;
import com.vetclinic.auth.exception.AccountLockedException;
import com.vetclinic.auth.exception.InvalidCredentialsException;
import com.vetclinic.auth.exception.PasswordChangeException;
import com.vetclinic.auth.repository.RefreshTokenRepository;
import com.vetclinic.auth.repository.UserRepository;
import com.vetclinic.auth.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Đổi mật khẩu khi đang đăng nhập (khác với quên mật khẩu: ở đây người dùng biết mật khẩu cũ).
 *
 * Phải nhập đúng mật khẩu hiện tại — chỉ cầm access token thì chưa đủ, vì máy đang đăng nhập có
 * thể bị người khác mượn. Đổi xong thì thu hồi refresh token của các thiết bị KHÁC, giữ lại phiên
 * của thiết bị đang đổi để người dùng không bị đá ra giữa chừng. (Access token đã cấp ở thiết bị
 * khác vẫn sống nốt tối đa 15 phút — JWT stateless không thu hồi được.)
 */
@Service
@RequiredArgsConstructor
public class ChangePasswordService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MessageResponse change(String email, String currentRefreshToken, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountLockedException();
        }

        // Đăng nhập bằng Google/Facebook thì chưa có mật khẩu để đối chiếu; cho họ đặt qua "Quên mật khẩu".
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new PasswordChangeException(null,
                    "Tài khoản này đăng nhập bằng Google/Facebook nên chưa có mật khẩu. "
                            + "Hãy dùng \"Quên mật khẩu\" để đặt mật khẩu.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new PasswordChangeException("currentPassword", "Mật khẩu hiện tại không đúng");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new PasswordChangeException("newPassword", "Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));

        // Không có cookie thì không biết đâu là thiết bị này: chuỗi rỗng không khớp hash nào nên thu hồi hết.
        String keepHash = currentRefreshToken == null ? "" : TokenHasher.hash(currentRefreshToken);
        refreshTokenRepository.revokeAllActiveForUserExcept(user.getId(), Instant.now(), keepHash);

        return new MessageResponse("Đã đổi mật khẩu.");
    }
}
