package com.vetclinic.auth.service;

import com.vetclinic.auth.domain.RoleName;
import com.vetclinic.auth.domain.User;
import com.vetclinic.auth.domain.UserStatus;
import com.vetclinic.auth.dto.PageResponse;
import com.vetclinic.auth.dto.UserResponse;
import com.vetclinic.auth.repository.UserRepository;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * VD-18: danh sách tài khoản cho quản trị viên. auth-service là nơi duy nhất giữ họ tên, email,
 * vai trò và trạng thái — trang Tài khoản, Khách hàng và Nhân viên đều đọc từ đây.
 *
 * Không trả mật khẩu băm hay token xác minh: UserResponse không có các field đó.
 */
@Service
@RequiredArgsConstructor
public class UserQueryService {

    static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(RoleName role, UserStatus status, String keyword, int page, int size) {
        Specification<User> spec = Specification.where(hasRole(role)).and(hasStatus(status)).and(matches(keyword));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));

        return PageResponse.of(userRepository.findAll(spec, pageable), UserQueryService::toResponse);
    }

    private static Specification<User> hasRole(RoleName role) {
        if (role == null) {
            return null;
        }
        // Subquery thay vì join thẳng: join làm một tài khoản nhiều vai trò hiện nhiều lần và
        // làm lệch tổng số bản ghi của trang.
        return (root, query, cb) -> {
            var sub = query.subquery(UUID.class);
            var user = sub.from(User.class);
            var roles = user.join("roles", JoinType.INNER);
            sub.select(user.get("id")).where(cb.equal(user.get("id"), root.get("id")), cb.equal(roles.get("name"), role));
            return cb.exists(sub);
        };
    }

    private static Specification<User> hasStatus(UserStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Tìm theo email hoặc họ tên, không phân biệt hoa thường. */
    private static Specification<User> matches(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            var fullName = cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName")));
            return cb.or(cb.like(cb.lower(root.get("email")), pattern, '\\'), cb.like(fullName, pattern, '\\'));
        };
    }

    // Người gõ "%" hay "_" là tìm đúng ký tự đó, không phải ký tự đại diện.
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.getStatus(), user.getRoles().stream().map(r -> r.getName().name()).sorted().toList(),
                user.getCreatedAt());
    }
}
