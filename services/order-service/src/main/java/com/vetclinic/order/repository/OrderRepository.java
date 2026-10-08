package com.vetclinic.order.repository;

import com.vetclinic.order.domain.Order;
import com.vetclinic.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    Optional<Order> findByGatewayTxnRef(String gatewayTxnRef);

    /** Khoá dòng khi huỷ đơn để không ghi đè kết quả thanh toán cổng vừa báo về. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    /** Id các đơn online chưa trả mà đã quá hạn, cũ nhất trước. */
    @Query("""
            select o.id from Order o
            where o.paymentMethod = com.vetclinic.order.domain.PaymentMethod.ONLINE
              and o.paymentStatus = com.vetclinic.order.domain.PaymentStatus.UNPAID
              and o.status = com.vetclinic.order.domain.OrderStatus.PENDING
              and o.paymentExpiresAt < :now
            order by o.paymentExpiresAt""")
    List<UUID> findExpiredUnpaidOnlineOrderIds(@Param("now") Instant now, Pageable pageable);

    /** Khoá dòng khi xử lý kết quả cổng để hai thông báo cùng lúc không ghi đè nhau. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.gatewayTxnRef = :txnRef")
    Optional<Order> findByGatewayTxnRefForUpdate(@Param("txnRef") String txnRef);

    /** Một khoản khám chỉ được nằm trong một hoá đơn. */
    boolean existsByExamPaymentId(UUID examPaymentId);

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

    /**
     * Doanh thu tính lúc đơn hoàn tất: đơn COD là lúc giao xong, hoá đơn tại quầy là lúc thu tiền.
     * Trừ exam_amount vì khoản khám đã nằm trong số liệu của payment-service; cộng cả total thì
     * tiền khám bị tính hai lần.
     */
    @Query(value = """
            SELECT CAST(completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS date) AS day, COUNT(*), COALESCE(SUM(total - exam_amount), 0)
            FROM orders WHERE status = 'COMPLETED' AND completed_at >= :start AND completed_at < :end
            GROUP BY day""", nativeQuery = true)
    List<Object[]> sumCompletedPerDay(@Param("start") Instant start, @Param("end") Instant end);

    @Query(value = """
            SELECT CAST(cancelled_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS date) AS day, COUNT(*)
            FROM orders WHERE status = 'CANCELLED' AND cancelled_at >= :start AND cancelled_at < :end
            GROUP BY day""", nativeQuery = true)
    List<Object[]> countCancelledPerDay(@Param("start") Instant start, @Param("end") Instant end);
}
