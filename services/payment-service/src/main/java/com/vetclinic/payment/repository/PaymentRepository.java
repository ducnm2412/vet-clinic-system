package com.vetclinic.payment.repository;

import com.vetclinic.payment.domain.Payment;
import com.vetclinic.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByMedicalRecordId(UUID medicalRecordId);

    // Hàng đợi cho staff: PENDING_AMOUNT (chờ nhập tiền) + PENDING_PAYMENT (chờ thu tiền).
    List<Payment> findByStatusInOrderByCreatedAtAsc(List<PaymentStatus> statuses);
}
