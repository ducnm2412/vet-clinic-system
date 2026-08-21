package com.vetclinic.profile.repository;

import com.vetclinic.profile.domain.DoctorLicense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorLicenseRepository extends JpaRepository<DoctorLicense, UUID> {

    List<DoctorLicense> findByDoctorProfileId(UUID doctorProfileId);

    Optional<DoctorLicense> findByIdAndDoctorProfileId(UUID id, UUID doctorProfileId);
}
