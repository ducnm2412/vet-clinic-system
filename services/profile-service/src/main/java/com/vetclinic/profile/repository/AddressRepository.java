package com.vetclinic.profile.repository;

import com.vetclinic.profile.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByCustomerProfileId(UUID customerProfileId);

    Optional<Address> findByIdAndCustomerProfileId(UUID id, UUID customerProfileId);
}
