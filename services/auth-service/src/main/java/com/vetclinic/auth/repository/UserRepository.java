package com.vetclinic.auth.repository;

import com.vetclinic.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// JpaSpecificationExecutor: danh sách tài khoản của admin lọc theo nhiều điều kiện tuỳ chọn —
// ghép Specification tránh được lỗi "could not determine data type" của PostgreSQL khi viết
// "(:param IS NULL OR ...)" trong JPQL.
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByVerificationToken(String verificationToken);

    Optional<User> findByGoogleSub(String googleSub);

    Optional<User> findByFacebookId(String facebookId);

    @Query("select u.id from User u join u.roles r where r.name = :role and u.status = :status order by u.id")
    List<UUID> findIdsByRoleAndStatus(@Param("role") com.vetclinic.auth.domain.RoleName role,
                                      @Param("status") com.vetclinic.auth.domain.UserStatus status);
}
