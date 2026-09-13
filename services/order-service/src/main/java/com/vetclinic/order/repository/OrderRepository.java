package com.vetclinic.order.repository;

import com.vetclinic.order.domain.Order;
import com.vetclinic.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** CN-36: nhân viên xem toàn bộ đơn, lọc theo trạng thái (null = tất cả). */
    @Query("select o from Order o where (:status is null or o.status = :status) order by o.createdAt desc")
    Page<Order> findAllByStatus(@Param("status") OrderStatus status, Pageable pageable);

    // ---------- CN-46: số liệu cho reporting-service ----------
    // Gộp theo NGÀY GIỜ VIỆT NAM, không theo UTC: đơn giao lúc 6 giờ sáng phải rơi vào hôm đó.
    // Mỗi dòng: [ngày, số đơn] hoặc [ngày, số đơn, tổng tiền]. Ngày không có đơn thì không có dòng.

    @Query(value = """
            SELECT CAST(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS date) AS day, COUNT(*)
            FROM orders WHERE created_at >= :start AND created_at < :end
            GROUP BY day""", nativeQuery = true)
    List<Object[]> countCreatedPerDay(@Param("start") Instant start, @Param("end") Instant end);

    /** Doanh thu tính lúc giao xong: thanh toán COD, trước đó chưa thu được đồng nào. */
    @Query(value = """
            SELECT CAST(completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS date) AS day, COUNT(*), COALESCE(SUM(total), 0)
            FROM orders WHERE status = 'COMPLETED' AND completed_at >= :start AND completed_at < :end
            GROUP BY day""", nativeQuery = true)
    List<Object[]> sumCompletedPerDay(@Param("start") Instant start, @Param("end") Instant end);

    @Query(value = """
            SELECT CAST(cancelled_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS date) AS day, COUNT(*)
            FROM orders WHERE status = 'CANCELLED' AND cancelled_at >= :start AND cancelled_at < :end
            GROUP BY day""", nativeQuery = true)
    List<Object[]> countCancelledPerDay(@Param("start") Instant start, @Param("end") Instant end);
}
