package com.vetclinic.order.repository;

import com.vetclinic.order.domain.Order;
import com.vetclinic.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** CN-36: nhân viên xem toàn bộ đơn, lọc theo trạng thái (null = tất cả). */
    @Query("select o from Order o where (:status is null or o.status = :status) order by o.createdAt desc")
    Page<Order> findAllByStatus(@Param("status") OrderStatus status, Pageable pageable);
}
