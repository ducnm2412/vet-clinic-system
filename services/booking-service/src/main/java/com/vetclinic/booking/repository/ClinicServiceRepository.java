package com.vetclinic.booking.repository;

import com.vetclinic.booking.domain.ClinicService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicServiceRepository extends JpaRepository<ClinicService, UUID> {

    List<ClinicService> findAllByOrderByDisplayOrderAscNameAsc();

    List<ClinicService> findByActiveTrueOrderByDisplayOrderAscNameAsc();

    Optional<ClinicService> findBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    boolean existsBySlug(String slug);
}
