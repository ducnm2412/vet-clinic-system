package com.vetclinic.notification.service;

import com.vetclinic.notification.messaging.OrderStatusChangedEvent;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Thông báo trên chuông và thư báo khách khi đơn hàng đổi trạng thái. */
class OrderStatusNotificationTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailService emailService = new EmailService(mailSender);
    private final UUID orderId = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:3000");
    }

    private OrderStatusChangedEvent event(String status, String method, String reason, boolean refund) {
        return new OrderStatusChangedEvent(orderId, "VC261116-AB12", UUID.randomUUID(), "khach@example.com",
                "Nguyễn Văn A", status, method, new BigDecimal("330000"), reason, refund, false);
    }

    private String mail(OrderStatusChangedEvent event) throws Exception {
        MimeMessage mime = new MimeMessage((jakarta.mail.Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mime);
        assertThat(emailService.sendOrderStatus(event)).isTrue();
        verify(mailSender).send(mime);
        mime.saveChanges();
        assertThat(mime.getAllRecipients()[0].toString()).isEqualTo("khach@example.com");
        return mime.getSubject() + "\n" + mime.getContent();
    }

    // ---------- chuông ----------

    @Test
    void everyStepHasABellMessageLinkingToTheOrder() {
        for (String status : new String[]{"CONFIRMED", "SHIPPING", "COMPLETED", "CANCELLED"}) {
            var c = InAppNotificationFactory.forOrder(event(status, "COD", null, false)).orElseThrow();
            assertThat(c.title()).contains("VC261116-AB12");
            assertThat(c.link()).isEqualTo("/user/orders/" + orderId);
        }
    }

    @Test
    void shipping_asksTheCustomerToConfirmReceiptAndMentionsCodPayment() {
        var cod = InAppNotificationFactory.forOrder(event("SHIPPING", "COD", null, false)).orElseThrow();
        var online = InAppNotificationFactory.forOrder(event("SHIPPING", "ONLINE", null, false)).orElseThrow();

        assertThat(cod.message()).contains("Đã nhận hàng").contains("thanh toán khi nhận hàng");
        assertThat(online.message()).contains("Đã nhận hàng").doesNotContain("thanh toán khi nhận hàng");
    }

    @Test
    void cancelled_givesTheReasonAndTheRefundPromiseOnlyWhenPaid() {
        var paid = InAppNotificationFactory.forOrder(event("CANCELLED", "ONLINE", "Hết hàng", true)).orElseThrow();
        var unpaid = InAppNotificationFactory.forOrder(event("CANCELLED", "COD", null, false)).orElseThrow();

        assertThat(paid.message()).contains("Hết hàng").contains("hoàn lại tiền");
        assertThat(unpaid.message()).doesNotContain("lý do").doesNotContain("hoàn");
    }

    @Test
    void unknownStatus_hasNoBellMessage() {
        assertThat(InAppNotificationFactory.forOrder(event("PENDING", "COD", null, false))).isEmpty();
    }

    // ---------- email ----------

    @Test
    void shippingMail_linksToTheOrderAndAsksToConfirmReceipt() throws Exception {
        String mail = mail(event("SHIPPING", "COD", null, false));

        assertThat(mail).contains("Đơn hàng VC261116-AB12 đang được giao");
        assertThat(mail).contains("Nguyễn Văn A").contains("thanh toán khi nhận hàng").contains("Đã nhận hàng");
        assertThat(mail).contains("http://localhost:3000/user/orders/" + orderId);
    }

    @Test
    void completedMail_thanksTheCustomer() throws Exception {
        assertThat(mail(event("COMPLETED", "COD", null, false))).contains("đã giao xong").contains("Cảm ơn");
    }

    @Test
    void cancelledMail_escapesTheReasonAndMentionsRefund() throws Exception {
        String mail = mail(event("CANCELLED", "ONLINE", "<script>x</script>", true));

        assertThat(mail).contains("đã bị huỷ").contains("&lt;script&gt;").doesNotContain("<script>");
        assertThat(mail).contains("hoàn lại tiền");
    }

    @Test
    void confirmedAndUnknownStatuses_sendNoMail() {
        assertThat(emailService.sendOrderStatus(event("CONFIRMED", "COD", null, false))).isFalse();
        assertThat(emailService.sendOrderStatus(event("WHATEVER", "COD", null, false))).isFalse();
        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }
}
