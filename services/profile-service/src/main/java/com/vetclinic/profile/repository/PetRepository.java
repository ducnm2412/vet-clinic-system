package com.vetclinic.profile.repository;

import com.vetclinic.profile.domain.Pet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PetRepository extends JpaRepository<Pet, UUID> {

    List<Pet> findByCustomerProfileId(UUID customerProfileId);

    Optional<Pet> findByIdAndCustomerProfileId(UUID id, UUID customerProfileId);
}
