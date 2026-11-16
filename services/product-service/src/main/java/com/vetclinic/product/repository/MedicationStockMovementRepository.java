package com.vetclinic.product.repository;

import com.vetclinic.product.domain.MedicationStockMovement;
import com.vetclinic.product.domain.StockMovementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MedicationStockMovementRepository extends JpaRepository<MedicationStockMovement, UUID> {

    List<MedicationStockMovement> findByMedicationIdOrderByCreatedAtDesc(UUID medicationId);

    /** RabbitMQ giao at-least-once: kiểm tra phiếu thu này đã trừ kho thuốc đó chưa. */
    boolean existsByMedicationIdAndReferenceIdAndType(UUID medicationId, UUID referenceId, StockMovementType type);
}
