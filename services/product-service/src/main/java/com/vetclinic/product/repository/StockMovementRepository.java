package com.vetclinic.product.repository;

import com.vetclinic.product.domain.StockMovement;
import com.vetclinic.product.domain.StockMovementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    List<StockMovement> findByProductIdOrderByCreatedAtDesc(UUID productId);

    /**
     * CN-31: RabbitMQ giao message theo kiểu at-least-once, nên cùng một sự kiện đơn hàng
     * có thể tới hai lần. Kiểm tra đã ghi nhận chưa trước khi trừ kho lần nữa.
     */
    boolean existsByProductIdAndReferenceIdAndType(UUID productId, UUID referenceId, StockMovementType type);
}
