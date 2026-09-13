package com.vetclinic.booking.repository;

import com.vetclinic.booking.domain.BlockedDoctor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BlockedDoctorRepository extends JpaRepository<BlockedDoctor, UUID> {
}
