package com.vetclinic.payment.service;

import com.vetclinic.payment.domain.Payment;
import com.vetclinic.payment.domain.PaymentItem;
import com.vetclinic.payment.domain.PaymentMethod;
import com.vetclinic.payment.domain.PaymentStatus;
import com.vetclinic.payment.dto.PaymentItemResponse;
import com.vetclinic.payment.dto.PaymentResponse;
import com.vetclinic.payment.exception.InvalidPaymentStateException;
import com.vetclinic.payment.exception.ResourceNotFoundException;
import com.vetclinic.payment.messaging.PaymentCompletedEvent;
import com.vetclinic.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    // Hàng đợi cho staff: chờ nhập tiền (PENDING_AMOUNT) hoặc chờ thu tiền (PENDING_PAYMENT).
    @Transactional(readOnly = true)
    public List<PaymentResponse> listPending() {
        return paymentRepository
                .findByStatusInOrderByCreatedAtAsc(List.of(PaymentStatus.PENDING_AMOUNT, PaymentStatus.PENDING_PAYMENT))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Staff nhập số tiền cần thu (chưa có price catalog nên không tự tính được) — chỉ hợp lệ khi
    // đang chờ nhập tiền, tránh sửa amount ngầm sau khi khách đã được yêu cầu thanh toán số cũ.
    @Transactional
    public PaymentResponse setAmount(UUID id, BigDecimal amount) {
        Payment payment = getOrThrow(id);

        if (payment.getStatus() != PaymentStatus.PENDING_AMOUNT) {
            throw new InvalidPaymentStateException(
                    "Payment " + id + " is not awaiting amount (status=" + payment.getStatus() + ")");
        }

        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING_PAYMENT);
        paymentRepository.saveAndFlush(payment);
        return toResponse(payment);
    }

    // Staff xác nhận đã thu tiền mặt tại quầy — idempotent nếu gọi lại khi đã COMPLETED (giống
    // receivePrescription bên booking-service), nhưng chặn nếu chưa có amount (PENDING_AMOUNT).
    @Transactional
    public PaymentResponse confirmCash(UUID id) {
        Payment payment = getOrThrow(id);

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return toResponse(payment);
        }

        if (payment.getStatus() != PaymentStatus.PENDING_PAYMENT) {
            throw new InvalidPaymentStateException(
                    "Payment " + id + " is not awaiting payment (status=" + payment.getStatus() + ")");
        }

        payment.setMethod(PaymentMethod.CASH);
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(Instant.now());
        paymentRepository.saveAndFlush(payment);

        // publishEvent ở đây, publish RabbitMQ thật sự xảy ra ở PaymentEventPublisher sau khi
        // transaction này commit (AFTER_COMMIT) — không publish ở nhánh idempotent phía trên vì
        // event payment.completed đã được gửi lần confirm-cash đầu tiên rồi.
        applicationEventPublisher.publishEvent(
                new PaymentCompletedEvent(payment.getId(), payment.getMedicalRecordId(), payment.getAppointmentId()));

        log.info("Payment {} confirmed as CASH", id);
        return toResponse(payment);
    }

    private Payment getOrThrow(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getMedicalRecordId(), payment.getAppointmentId(),
                payment.getCustomerUserId(), payment.getAmount(), payment.getMethod(), payment.getStatus(),
                payment.getPaidAt(), payment.getItems().stream().map(this::toItemResponse).toList(),
                payment.getCreatedAt(), payment.getUpdatedAt());
    }

    private PaymentItemResponse toItemResponse(PaymentItem item) {
        return new PaymentItemResponse(item.getId(), item.getMedicationName(), item.getDosage(), item.getFrequency(),
                item.getDurationDays(), item.getUnitPrice(), item.getQuantity(), item.getLineAmount());
    }
}
