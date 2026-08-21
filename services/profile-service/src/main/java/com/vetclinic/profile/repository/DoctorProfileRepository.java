package com.vetclinic.profile.repository;

import com.vetclinic.profile.domain.DoctorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, UUID> {

    Optional<DoctorProfile> findByUserId(UUID userId);
}
