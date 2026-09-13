package com.vetclinic.profile.repository;

import com.vetclinic.profile.domain.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {

    Optional<CustomerProfile> findByUserId(UUID userId);

    List<CustomerProfile> findByUserIdIn(Collection<UUID> userIds);
}
