package com.vetclinic.profile.repository;

import com.vetclinic.profile.domain.DoctorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, UUID> {

    Optional<DoctorProfile> findByUserId(UUID userId);

    /** Hồ sơ có ảnh trên đĩa mà chưa có URL Cloudinary — đối tượng của LegacyPhotoMigration. */
    List<DoctorProfile> findByPhotoVersionGreaterThanAndPhotoUrlIsNull(int photoVersion);
}
