package com.vetclinic.order.messaging;

import com.vetclinic.order.service.ExamInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Khoản khám vừa có số tiền thì lập sẵn hoá đơn chờ thanh toán để khách thấy trong "Đơn hàng". */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentAmountSetListener {

    private final ExamInvoiceService examInvoiceService;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_AMOUNT_SET_QUEUE)
    public void onAmountSet(PaymentAmountSetEvent event) {
        try {
            examInvoiceService.createFromAmountSet(event);
        } catch (RuntimeException e) {
            // Không ném ra ngoài: Rabbit sẽ đưa lại message ngay và vòng lặp đó không bao giờ dừng nếu
            // lỗi nằm ở dữ liệu. Mất sự kiện này không mất gì thật sự — nhân viên mở khoản khám thì
            // hoá đơn được tạo bù (ExamInvoiceService.getOrCreate).
            log.error("Không tạo được hoá đơn chờ cho khoản khám {}", event == null ? null : event.paymentId(), e);
        }
    }
}
