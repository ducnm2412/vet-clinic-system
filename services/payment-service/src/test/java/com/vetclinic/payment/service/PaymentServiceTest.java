package com.vetclinic.payment.service;

import com.vetclinic.payment.domain.Payment;
import com.vetclinic.payment.domain.PaymentItem;
import com.vetclinic.payment.domain.PaymentMethod;
import com.vetclinic.payment.domain.PaymentStatus;
import com.vetclinic.payment.dto.PaymentResponse;
import com.vetclinic.payment.dto.SetItemsRequest;
import com.vetclinic.payment.exception.InvalidPaymentRequestException;
import com.vetclinic.payment.exception.InvalidPaymentStateException;
import com.vetclinic.payment.exception.ResourceNotFoundException;
import com.vetclinic.payment.messaging.PaymentAmountSetEvent;
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
    void setAmount_publishesAmountSetEventWithCustomerAndAmount() {
        Payment payment = seedPayment(PaymentStatus.PENDING_AMOUNT, null);

        paymentService.setAmount(payment.getId(), new BigDecimal("150000"));

        assertThat(events.stream(PaymentAmountSetEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.paymentId()).isEqualTo(payment.getId());
            assertThat(event.customerUserId()).isEqualTo(payment.getCustomerUserId());
            assertThat(event.amount()).isEqualByComparingTo("150000");
        });
    }

    @Test
    void setAmount_rejected_publishesNoEvent() {
        Payment payment = seedPayment(PaymentStatus.PENDING_PAYMENT, new BigDecimal("100000"));

        assertThatThrownBy(() -> paymentService.setAmount(payment.getId(), new BigDecimal("200000")))
                .isInstanceOf(InvalidPaymentStateException.class);

        assertThat(events.stream(PaymentAmountSetEvent.class)).isEmpty();
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

    // ---- setItems: chốt tiền theo từng dòng thuốc ----

    @Test
    void setItems_computesLineAmountsAndTotal_andMovesToPendingPayment() {
        Payment payment = seedPaymentWithItems(2);
        PaymentItem a = payment.getItems().get(0);
        PaymentItem b = payment.getItems().get(1);

        PaymentResponse updated = setItems(payment.getId(), List.of(
                new SetItemsRequest.Line(a.getId(), new BigDecimal("12000"), 10),
                new SetItemsRequest.Line(b.getId(), new BigDecimal("3500.50"), 3)));

        assertThat(updated.status()).isEqualTo(PaymentStatus.PENDING_PAYMENT);
        // 12000 x 10 + 3500,50 x 3 = 130501,50
        assertThat(updated.amount()).isEqualByComparingTo("130501.50");
        assertThat(updated.items()).extracting("lineAmount", BigDecimal.class)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("120000"), new BigDecimal("10501.50"));
        assertThat(updated.items()).extracting("quantity").containsExactlyInAnyOrder(10, 3);
    }

    @Test
    void setItems_publishesAmountSetEventWithTheTotal() {
        Payment payment = seedPaymentWithItems(1);

        setItems(payment.getId(), List.of(
                new SetItemsRequest.Line(payment.getItems().get(0).getId(), new BigDecimal("5000"), 4)));

        assertThat(events.stream(PaymentAmountSetEvent.class)).singleElement()
                .satisfies(e -> assertThat(e.amount()).isEqualByComparingTo("20000"));
    }

    @Test
    void setItems_missingALine_isRejectedAndChangesNothing() {
        Payment payment = seedPaymentWithItems(2);

        assertThatThrownBy(() -> setItems(payment.getId(), List.of(
                new SetItemsRequest.Line(payment.getItems().get(0).getId(), new BigDecimal("1000"), 1))))
                .isInstanceOf(InvalidPaymentRequestException.class);

        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING_AMOUNT);
        assertThat(events.stream(PaymentAmountSetEvent.class)).isEmpty();
    }

    @Test
    void setItems_foreignOrDuplicatedLine_isRejected() {
        Payment payment = seedPaymentWithItems(1);
        UUID mine = payment.getItems().get(0).getId();

        assertThatThrownBy(() -> setItems(payment.getId(), List.of(
                new SetItemsRequest.Line(mine, new BigDecimal("1000"), 1),
                new SetItemsRequest.Line(UUID.randomUUID(), new BigDecimal("1000"), 1))))
                .isInstanceOf(InvalidPaymentRequestException.class);
        assertThatThrownBy(() -> setItems(payment.getId(), List.of(
                new SetItemsRequest.Line(mine, new BigDecimal("1000"), 1),
                new SetItemsRequest.Line(mine, new BigDecimal("1000"), 1))))
                .isInstanceOf(InvalidPaymentRequestException.class);
    }

    @Test
    void setItems_zeroTotal_isRejected() {
        Payment payment = seedPaymentWithItems(1);

        assertThatThrownBy(() -> setItems(payment.getId(), List.of(
                new SetItemsRequest.Line(payment.getItems().get(0).getId(), BigDecimal.ZERO, 5))))
                .isInstanceOf(InvalidPaymentRequestException.class);
    }

    @Test
    void setItems_whenNotAwaitingAmount_isConflict() {
        Payment payment = seedPaymentWithItems(1);
        paymentService.setAmount(payment.getId(), new BigDecimal("50000"));

        assertThatThrownBy(() -> setItems(payment.getId(), List.of(
                new SetItemsRequest.Line(payment.getItems().get(0).getId(), new BigDecimal("1000"), 1))))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    // ---- tiền dịch vụ + tiền thuốc ----

    @Test
    void setItems_addsTheServiceFeeToTheDrugTotal() {
        Payment payment = seedPaymentWithItems(1);
        payment.setServiceName("Khám, tư vấn & điều trị bệnh");
        payment.setServiceAmount(new BigDecimal("150000"));
        paymentRepository.saveAndFlush(payment);

        PaymentResponse updated = setItems(payment.getId(), List.of(
                new SetItemsRequest.Line(payment.getItems().get(0).getId(), new BigDecimal("2000"), 10)));

        // 150000 (dịch vụ) + 2000 x 10 (thuốc)
        assertThat(updated.amount()).isEqualByComparingTo("170000");
        assertThat(updated.serviceAmount()).isEqualByComparingTo("150000");
        assertThat(updated.serviceName()).isEqualTo("Khám, tư vấn & điều trị bệnh");
    }

    @Test
    void setItems_staffCanCorrectTheServiceFee() {
        Payment payment = seedPaymentWithItems(1);
        payment.setServiceAmount(new BigDecimal("150000"));
        paymentRepository.saveAndFlush(payment);

        PaymentResponse updated = paymentService.setItems(payment.getId(), new SetItemsRequest(List.of(
                new SetItemsRequest.Line(payment.getItems().get(0).getId(), new BigDecimal("1000"), 1)),
                new BigDecimal("200000"), "Siêu âm"));

        assertThat(updated.amount()).isEqualByComparingTo("201000");
        assertThat(updated.serviceName()).isEqualTo("Siêu âm");
    }

    @Test
    void setItems_serviceOnly_whenNoDrugWasPrescribed() {
        Payment payment = seedPaymentWithItems(0);
        payment.setServiceAmount(new BigDecimal("150000"));
        paymentRepository.saveAndFlush(payment);

        PaymentResponse updated = setItems(payment.getId(), List.of());

        assertThat(updated.status()).isEqualTo(PaymentStatus.PENDING_PAYMENT);
        assertThat(updated.amount()).isEqualByComparingTo("150000");
    }

    @Test
    void setItems_noServiceAndNoDrug_isRejectedAsZeroTotal() {
        Payment payment = seedPaymentWithItems(0);

        assertThatThrownBy(() -> setItems(payment.getId(), List.of()))
                .isInstanceOf(InvalidPaymentRequestException.class);
    }

    private PaymentResponse setItems(UUID id, List<SetItemsRequest.Line> lines) {
        return paymentService.setItems(id, new SetItemsRequest(lines, null, null));
    }

    private Payment seedPaymentWithItems(int count) {
        Payment payment = Payment.builder()
                .medicalRecordId(UUID.randomUUID())
                .appointmentId(UUID.randomUUID())
                .customerUserId(UUID.randomUUID())
                .status(PaymentStatus.PENDING_AMOUNT)
                .build();
        for (int i = 0; i < count; i++) {
            payment.getItems().add(PaymentItem.builder()
                    .payment(payment).medicationName("Thuoc " + i).dosage("1 viên").frequency("2 lần/ngày").build());
        }
        return paymentRepository.saveAndFlush(payment);
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
