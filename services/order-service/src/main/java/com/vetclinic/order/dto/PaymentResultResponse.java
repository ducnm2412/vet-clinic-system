package com.vetclinic.order.dto;

import com.vetclinic.order.domain.PaymentStatus;

import java.util.UUID;

/** Kết quả xử lý một thông báo thanh toán từ cổng. orderId/orderCode null khi không tìm thấy giao dịch. */
public record PaymentResultResponse(
        Outcome outcome,
        UUID orderId,
        String orderCode,
        PaymentStatus paymentStatus
) {

    public enum Outcome {
        /** Vừa ghi nhận đã trả tiền. */
        PAID,
        /** Đơn đã được ghi nhận đã trả từ trước (cổng gọi lại lần hai); không làm gì thêm. */
        ALREADY_PAID,
        /** Khách trả không thành công hoặc bấm huỷ; đơn vẫn chờ thanh toán, khách trả lại được. */
        FAILED,
        /** Không có đơn nào mang mã giao dịch này (link cũ đã bị thay hoặc mã lạ). */
        UNKNOWN_TRANSACTION,
        /** Số tiền cổng báo khác tổng đơn; không ghi nhận, cần người kiểm tra. */
        AMOUNT_MISMATCH
    }
}
