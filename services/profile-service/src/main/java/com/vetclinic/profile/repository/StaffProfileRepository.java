package com.vetclinic.profile.repository;

import com.vetclinic.profile.domain.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, UUID> {

    Optional<StaffProfile> findByUserId(UUID userId);
}
