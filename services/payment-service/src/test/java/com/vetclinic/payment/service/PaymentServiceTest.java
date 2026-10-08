package com.vetclinic.payment.service;

import com.vetclinic.payment.domain.Payment;
import com.vetclinic.payment.domain.PaymentMethod;
import com.vetclinic.payment.domain.PaymentStatus;
import com.vetclinic.payment.dto.PaymentResponse;
import com.vetclinic.payment.exception.InvalidPaymentStateException;
import com.vetclinic.payment.exception.ResourceNotFoundException;
import com.vetclinic.payment.messaging.PaymentCompletedEvent;
import com.vetclinic.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @Transactional: mỗi test tự rollback, không cần dọn DB tay (giống MedicalRecordServiceTest).
// Lưu ý: confirmCash() gọi applicationEventPublisher.publishEvent(...), nhưng vì transaction test
// không bao giờ commit thật nên PaymentEventPublisher (AFTER_COMMIT) sẽ KHÔNG publish lên RabbitMQ
// ở đây — hành vi publish thật được test riêng ở PaymentEventPublisherTest (không @Transactional).
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
@RecordApplicationEvents
class PaymentServiceTest {

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void listPending_includesPendingAmountAndPendingPayment_excludesCompleted() {
        Payment pendingAmount = seedPayment(PaymentStatus.PENDING_AMOUNT, null);
        Payment pendingPayment = seedPayment(PaymentStatus.PENDING_PAYMENT, new BigDecimal("100000"));
        Payment completed = seedPayment(PaymentStatus.COMPLETED, new BigDecimal("50000"));

        List<PaymentResponse> pending = paymentService.listPending();

        assertThat(pending).extracting(PaymentResponse::id)
                .contains(pendingAmount.getId(), pendingPayment.getId())
                .doesNotContain(completed.getId());
    }

    @Test
    void setAmount_onPendingAmount_transitionsToPendingPayment() {
        Payment payment = seedPayment(PaymentStatus.PENDING_AMOUNT, null);

        PaymentResponse updated = paymentService.setAmount(payment.getId(), new BigDecimal("150000"));

        assertThat(updated.status()).isEqualTo(PaymentStatus.PENDING_PAYMENT);
        assertThat(updated.amount()).isEqualByComparingTo("150000");
    }

    @Test
    void setAmount_onNonPendingAmountPayment_throwsInvalidPaymentState() {
        Payment payment = seedPayment(PaymentStatus.PENDING_PAYMENT, new BigDecimal("100000"));

        assertThatThrownBy(() -> paymentService.setAmount(payment.getId(), new BigDecimal("200000")))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void setAmount_unknownId_throwsResourceNotFound() {
        assertThatThrownBy(() -> paymentService.setAmount(UUID.randomUUID(), new BigDecimal("100000")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void confirmCash_onPendingPayment_completesWithCashMethodAndPaidAt() {
        Payment payment = seedPayment(PaymentStatus.PENDING_PAYMENT, new BigDecimal("150000"));

        PaymentResponse confirmed = paymentService.confirmCash(payment.getId());

        assertThat(confirmed.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(confirmed.method()).isEqualTo(PaymentMethod.CASH);
        assertThat(confirmed.paidAt()).isNotNull();
    }

    @Test
    void confirmCash_onPendingAmount_throwsInvalidPaymentState() {
        Payment payment = seedPayment(PaymentStatus.PENDING_AMOUNT, null);

        assertThatThrownBy(() -> paymentService.confirmCash(payment.getId()))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void confirmCash_calledTwice_isIdempotent() {
        Payment payment = seedPayment(PaymentStatus.PENDING_PAYMENT, new BigDecimal("150000"));

        PaymentResponse first = paymentService.confirmCash(payment.getId());
        PaymentResponse second = paymentService.confirmCash(payment.getId());

        assertThat(second.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(second.paidAt()).isEqualTo(first.paidAt());
    }

    @Test
    void confirmCash_unknownId_throwsResourceNotFound() {
        assertThatThrownBy(() -> paymentService.confirmCash(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- đọc một khoản (order-service dùng khi lập hoá đơn gộp) ----------

    @Test
    void get_returnsAmountStatusAndOwner() {
        Payment payment = seedPayment(PaymentStatus.PENDING_PAYMENT, new BigDecimal("120000"));

        PaymentResponse found = paymentService.get(payment.getId());

        assertThat(found.id()).isEqualTo(payment.getId());
        assertThat(found.status()).isEqualTo(PaymentStatus.PENDING_PAYMENT);
        assertThat(found.amount()).isEqualByComparingTo("120000");
        assertThat(found.customerUserId()).isEqualTo(payment.getCustomerUserId());
    }

    @Test
    void get_unknownId_throwsResourceNotFound() {
        assertThatThrownBy(() -> paymentService.get(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- hoàn tất từ hoá đơn tại quầy ----------

    @Test
    void completeFromInvoice_completesWithTheMethodStaffCollected_andAnnouncesIt() {
        Payment payment = seedPayment(PaymentStatus.PENDING_PAYMENT, new BigDecimal("150000"));

        paymentService.completeFromInvoice(payment.getId(), "BANK_TRANSFER", UUID.randomUUID());

        Payment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(reloaded.getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(reloaded.getPaidAt()).isNotNull();
        // pet-service nhận payment.completed này để chuyển bệnh án sang RECEIVED.
        assertThat(events.stream(PaymentCompletedEvent.class)).singleElement().satisfies(e -> {
            assertThat(e.paymentId()).isEqualTo(payment.getId());
            assertThat(e.medicalRecordId()).isEqualTo(payment.getMedicalRecordId());
        });
    }

    @Test
    void completeFromInvoice_redelivered_doesNothingTheSecondTime() {
        Payment payment = seedPayment(PaymentStatus.PENDING_PAYMENT, new BigDecimal("150000"));
        paymentService.completeFromInvoice(payment.getId(), "CASH", UUID.randomUUID());
        var firstPaidAt = paymentRepository.findById(payment.getId()).orElseThrow().getPaidAt();

        paymentService.completeFromInvoice(payment.getId(), "BANK_TRANSFER", UUID.randomUUID());

        Payment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(reloaded.getMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(reloaded.getPaidAt()).isEqualTo(firstPaidAt);
        assertThat(events.stream(PaymentCompletedEvent.class)).hasSize(1);
    }

    @Test
    void completeFromInvoice_afterCashAlreadyCollected_keepsTheOriginalCollection() {
        Payment payment = seedPayment(PaymentStatus.PENDING_PAYMENT, new BigDecimal("150000"));
        paymentService.confirmCash(payment.getId());

        paymentService.completeFromInvoice(payment.getId(), "BANK_TRANSFER", UUID.randomUUID());

        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getMethod())
                .isEqualTo(PaymentMethod.CASH);
        assertThat(events.stream(PaymentCompletedEvent.class)).hasSize(1);
    }

    // Mọi trường hợp dưới đây phải trả về bình thường: ném lỗi trong consumer RabbitMQ làm message bị
    // giao lại vô hạn.
    @Test
    void completeFromInvoice_badInput_isIgnoredWithoutThrowing() {
        Payment awaitingAmount = seedPayment(PaymentStatus.PENDING_AMOUNT, null);
        Payment ready = seedPayment(PaymentStatus.PENDING_PAYMENT, new BigDecimal("1000"));

        paymentService.completeFromInvoice(awaitingAmount.getId(), "CASH", UUID.randomUUID());
        paymentService.completeFromInvoice(UUID.randomUUID(), "CASH", UUID.randomUUID());
        paymentService.completeFromInvoice(ready.getId(), "BITCOIN", UUID.randomUUID());
        paymentService.completeFromInvoice(ready.getId(), null, UUID.randomUUID());

        assertThat(paymentRepository.findById(awaitingAmount.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING_AMOUNT);
        assertThat(paymentRepository.findById(ready.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING_PAYMENT);
        assertThat(events.stream(PaymentCompletedEvent.class)).isEmpty();
    }

    private Payment seedPayment(PaymentStatus status, BigDecimal amount) {
        Payment payment = Payment.builder()
                .medicalRecordId(UUID.randomUUID())
                .appointmentId(UUID.randomUUID())
                .customerUserId(UUID.randomUUID())
                .status(status)
                .amount(amount)
                .build();
        return paymentRepository.saveAndFlush(payment);
    }
}
