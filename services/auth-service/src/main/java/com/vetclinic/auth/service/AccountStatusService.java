package com.vetclinic.auth.service;

import com.vetclinic.auth.domain.RoleName;
import com.vetclinic.auth.domain.User;
import com.vetclinic.auth.domain.UserStatus;
import com.vetclinic.auth.dto.UserResponse;
import com.vetclinic.auth.exception.AccountStatusChangeException;
import com.vetclinic.auth.exception.UserNotFoundException;
import com.vetclinic.auth.messaging.UserStatusChangedEvent;
import com.vetclinic.auth.repository.RefreshTokenRepository;
import com.vetclinic.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CN-08 / VD-06: khoá và mở khoá tài khoản. Quản trị viên dùng thay cho xoá — xoá hẳn làm lịch
 * khám, bệnh án và đơn hàng cũ mất người liên quan.
 *
 * Khoá có hiệu lực:
 * - ngay lập tức với đăng nhập mới và làm mới phiên (mọi refresh token bị thu hồi);
 * - access token đang cầm vẫn dùng được tới khi hết hạn (mặc định 15 phút), vì các service tự
 *   kiểm chữ ký JWT chứ không hỏi lại auth-service mỗi request.
 */
@Service
@RequiredArgsConstructor
public class AccountStatusService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UserResponse lock(UUID targetId, UUID actorId) {
        User user = userRepository.findById(targetId).orElseThrow(() -> new UserNotFoundException(targetId));

        if (targetId.equals(actorId)) {
            throw new AccountStatusChangeException("You cannot lock your own account");
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            return toResponse(user);
        }
        if (isAdmin(user) && user.getStatus() == UserStatus.ACTIVE && countActiveAdmins() <= 1) {
            throw new AccountStatusChangeException("Cannot lock the last active admin account");
        }

        user.setStatus(UserStatus.LOCKED);
        refreshTokenRepository.revokeAllActiveForUser(user.getId(), Instant.now());
        eventPublisher.publishEvent(new UserStatusChangedEvent(user.getId(), roleNames(user), true));
        return toResponse(user);
    }

    @Transactional
    public UserResponse unlock(UUID targetId) {
        User user = userRepository.findById(targetId).orElseThrow(() -> new UserNotFoundException(targetId));
        if (user.getStatus() != UserStatus.LOCKED) {
            return toResponse(user);
        }

        // Khoá lúc chưa xác minh email thì mở khoá xong vẫn phải xác minh — không được dùng
        // khoá/mở khoá để lách bước đó. Token xác minh chỉ bị xoá khi đã bấm link.
        user.setStatus(user.getVerificationToken() != null ? UserStatus.INACTIVE : UserStatus.ACTIVE);
        eventPublisher.publishEvent(new UserStatusChangedEvent(user.getId(), roleNames(user), false));
        return toResponse(user);
    }

    private long countActiveAdmins() {
        Specification<User> activeAdmin = (root, query, cb) -> {
            query.distinct(true);
            return cb.and(cb.equal(root.get("status"), UserStatus.ACTIVE),
                    cb.equal(root.join("roles").get("name"), RoleName.ADMIN));
        };
        return userRepository.count(activeAdmin);
    }

    private static boolean isAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMIN);
    }

    private static List<String> roleNames(User user) {
        return user.getRoles().stream().map(r -> r.getName().name()).sorted().toList();
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.getStatus(), roleNames(user), user.getCreatedAt());
    }
}
