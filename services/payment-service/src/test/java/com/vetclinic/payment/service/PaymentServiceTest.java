package com.vetclinic.payment.service;

import com.vetclinic.payment.domain.Payment;
import com.vetclinic.payment.domain.PaymentMethod;
import com.vetclinic.payment.domain.PaymentStatus;
import com.vetclinic.payment.dto.PaymentResponse;
import com.vetclinic.payment.exception.InvalidPaymentStateException;
import com.vetclinic.payment.exception.ResourceNotFoundException;
import com.vetclinic.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class PaymentServiceTest {

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
