package com.vetclinic.pet.repository;

import com.vetclinic.pet.domain.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, UUID> {
}
