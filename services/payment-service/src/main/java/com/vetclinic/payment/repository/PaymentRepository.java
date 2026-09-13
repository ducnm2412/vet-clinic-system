package com.vetclinic.payment.repository;

import com.vetclinic.payment.domain.Payment;
import com.vetclinic.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByMedicalRecordId(UUID medicalRecordId);

    // Hàng đợi cho staff: PENDING_AMOUNT (chờ nhập tiền) + PENDING_PAYMENT (chờ thu tiền).
    List<Payment> findByStatusInOrderByCreatedAtAsc(List<PaymentStatus> statuses);

    // CN-46: tiền đã thu, gộp theo NGÀY GIỜ VIỆT NAM lúc thu (paid_at), không theo UTC.
    // Mỗi dòng: [ngày, số lượt thu, tổng tiền]. Ngày không thu đồng nào thì không có dòng.
    @Query(value = """
            SELECT CAST(paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS date) AS day, COUNT(*), COALESCE(SUM(amount), 0)
            FROM payments WHERE status = 'COMPLETED' AND paid_at >= :start AND paid_at < :end
            GROUP BY day""", nativeQuery = true)
    List<Object[]> sumCompletedPerDay(@Param("start") Instant start, @Param("end") Instant end);
}
