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
    // receive bên pet-service), nhưng chặn nếu chưa có amount (PENDING_AMOUNT).
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

        complete(payment, PaymentMethod.CASH);
        return toResponse(payment);
    }

    // Hoá đơn tại quầy (order-service) đã thu tiền và có gộp khoản khám này: hoàn tất khoản khám với
    // đúng phương thức nhân viên đã thu. Gọi từ consumer RabbitMQ nên KHÔNG ném lỗi với dữ liệu lạ:
    // Spring AMQP mặc định requeue message bị lỗi, một message sai sẽ bị giao lại vô hạn. Chỉ ghi log
    // rồi bỏ; các trường hợp bình thường (đã COMPLETED do redeliver) lặng lẽ bỏ qua.
    @Transactional
    public void completeFromInvoice(UUID paymentId, String method, UUID orderId) {
        PaymentMethod paymentMethod;
        try {
            paymentMethod = PaymentMethod.valueOf(method);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.error("Invoice {} paid with unknown method '{}', payment {} left untouched", orderId, method, paymentId);
            return;
        }

        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            log.error("Invoice {} references unknown payment {}, ignored", orderId, paymentId);
            return;
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info("Payment {} already COMPLETED, invoice {} event ignored", paymentId, orderId);
            return;
        }

        if (payment.getStatus() != PaymentStatus.PENDING_PAYMENT) {
            log.error("Invoice {} paid but payment {} is {} (expected PENDING_PAYMENT), left untouched",
                    orderId, paymentId, payment.getStatus());
            return;
        }

        complete(payment, paymentMethod);
        log.info("Payment {} completed as {} via invoice {}", paymentId, paymentMethod, orderId);
    }

    // Chuyển PENDING_PAYMENT -> COMPLETED: đường duy nhất để một khoản được coi là đã thu, dùng chung
    // cho thu tiền mặt trực tiếp và cho hoá đơn tại quầy.
    private void complete(Payment payment, PaymentMethod method) {
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(Instant.now());
        paymentRepository.saveAndFlush(payment);

        // publishEvent ở đây, publish RabbitMQ thật sự xảy ra ở PaymentEventPublisher sau khi
        // transaction này commit (AFTER_COMMIT). Các nhánh idempotent của hàm gọi không vào đây vì
        // event payment.completed đã được gửi ở lần hoàn tất đầu tiên rồi.
        applicationEventPublisher.publishEvent(
                new PaymentCompletedEvent(payment.getId(), payment.getMedicalRecordId(), payment.getAppointmentId()));

        log.info("Payment {} confirmed as {}", payment.getId(), method);
    }

    // Order-service gọi để biết số tiền và chủ khoản khám khi lập hoá đơn gộp.
    @Transactional(readOnly = true)
    public PaymentResponse get(UUID id) {
        return toResponse(getOrThrow(id));
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
