package com.vetclinic.order.service;

import com.vetclinic.order.domain.OrderChannel;
import com.vetclinic.order.domain.OrderStatus;
import com.vetclinic.order.domain.PaymentStatus;
import com.vetclinic.order.dto.OrderResponse;
import com.vetclinic.order.messaging.PaymentAmountSetEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Lập hoá đơn chờ thanh toán cho khoản khám. Cố ý KHÔNG đánh dấu @Transactional: mỗi lần gọi
 * OrderService là một transaction riêng, nên khi ràng buộc UNIQUE chặn người tạo trùng thì còn
 * chỗ để đọc lại hoá đơn đã có (trong một transaction đã hỏng thì không đọc tiếp được).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamInvoiceService {

    private final OrderService orderService;
    private final CustomerInfoLookup customerInfoLookup;

    /** Xử lý sự kiện payment.amount-set. Dữ liệu thiếu/sai thì bỏ qua và ghi log, không ném lỗi. */
    public void createFromAmountSet(PaymentAmountSetEvent event) {
        if (event == null || event.paymentId() == null || event.customerUserId() == null
                || event.amount() == null || event.amount().signum() <= 0) {
            log.warn("Bỏ qua sự kiện payment.amount-set thiếu dữ liệu: {}", event);
            return;
        }

        try {
            orderService.createExamInvoiceIfAbsent(event.paymentId(), event.customerUserId(), event.amount());
        } catch (DataIntegrityViolationException e) {
            // Nhân viên vừa mở khoản khám trước khi sự kiện tới: hoá đơn đã có, việc đã xong.
            log.info("Khoản khám {} đã có hoá đơn, bỏ qua sự kiện", event.paymentId());
        }
    }

    /**
     * Nhân viên mở hoá đơn của khoản khám; chưa có thì lập bù. Hoá đơn còn tên tạm hoặc thiếu số điện
     * thoại thì điền thông tin khách (tra bằng token của nhân viên); tra không được thì để nguyên.
     */
    public OrderResponse getOrCreate(UUID examPaymentId, String bearerToken) {
        OrderResponse invoice;
        try {
            invoice = orderService.getOrCreateExamInvoice(examPaymentId, bearerToken);
        } catch (DataIntegrityViolationException e) {
            // Thua cuộc đua với sự kiện: lần này hoá đơn đã có nên được đọc ra mà không gọi payment-service.
            invoice = orderService.getOrCreateExamInvoice(examPaymentId, bearerToken);
        }
        return withCustomerInfo(invoice, bearerToken);
    }

    private OrderResponse withCustomerInfo(OrderResponse invoice, String bearerToken) {
        boolean waiting = invoice.channel() == OrderChannel.COUNTER
                && invoice.status() == OrderStatus.PENDING
                && invoice.paymentStatus() == PaymentStatus.UNPAID;
        boolean missingName = OrderService.DEFAULT_CUSTOMER_NAME.equals(invoice.recipientName());
        boolean missingPhone = invoice.recipientPhone() == null || invoice.recipientPhone().isBlank();
        if (!waiting || invoice.userId() == null || !(missingName || missingPhone)) {
            return invoice;
        }

        try {
            CustomerInfoLookup.CustomerInfo info = customerInfoLookup.find(invoice.userId(), bearerToken);
            return info.isEmpty() ? invoice : orderService.applyCustomerInfo(invoice.id(), info.name(), info.phone());
        } catch (RuntimeException e) {
            // Điền thông tin chỉ là tiện cho nhân viên; hỏng ở đây không được chặn việc mở hoá đơn.
            log.warn("Không điền được thông tin khách cho hoá đơn {}", invoice.id(), e);
            return invoice;
        }
    }
}
