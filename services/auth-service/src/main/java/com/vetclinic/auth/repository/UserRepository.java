package com.vetclinic.auth.repository;

import com.vetclinic.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

// JpaSpecificationExecutor: danh sách tài khoản của admin lọc theo nhiều điều kiện tuỳ chọn —
// ghép Specification tránh được lỗi "could not determine data type" của PostgreSQL khi viết
// "(:param IS NULL OR ...)" trong JPQL.
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByVerificationToken(String verificationToken);
}
