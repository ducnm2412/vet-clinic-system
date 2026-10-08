package com.vetclinic.order.payment;

import com.vetclinic.order.domain.Order;

import java.util.Map;
import java.util.Optional;

/**
 * Cổng thanh toán online cho đơn đặt qua giỏ hàng. Luồng đơn hàng chỉ biết interface này;
 * cổng thật (VNPAY, MoMo) chỉ cần cài đặt lại hai hàm dưới đây, không phải sửa nghiệp vụ đơn.
 */
public interface OnlinePaymentGateway {

    /**
     * Địa chỉ để khách sang trả tiền cho đơn này, gắn với mã giao dịch txnRef vừa cấp.
     * clientIp là địa chỉ của khách (cổng thật yêu cầu gửi kèm để chống gian lận).
     */
    String buildPaymentUrl(Order order, String txnRef, String clientIp);

    /**
     * Kiểm tra chữ ký kết quả cổng báo về rồi rút ra kết quả.
     *
     * @throws InvalidGatewaySignatureException chữ ký sai hoặc dữ liệu thiếu/không đọc được
     */
    GatewayResult verifyCallback(Map<String, String> params);

    /**
     * Hỏi thẳng cổng trạng thái thật của giao dịch hiện tại của đơn, dùng khi thông báo của cổng có thể đã
     * bị lỡ (job tự huỷ đơn quá hạn phải chắc đơn thật sự chưa trả). Trả empty khi cổng không có giao dịch
     * này hoặc cổng không hỗ trợ hỏi lại.
     *
     * @throws GatewayQueryException không xác định được (cổng không trả lời, trả lời không tin được)
     */
    default Optional<GatewayResult> queryTransaction(Order order) {
        return Optional.empty();
    }
}
