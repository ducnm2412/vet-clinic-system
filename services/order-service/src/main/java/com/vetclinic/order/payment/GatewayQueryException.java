package com.vetclinic.order.payment;

/**
 * Không biết được trạng thái thật của giao dịch ở cổng: cổng không trả lời, trả lời lỗi hoặc trả lời mà chữ ký
 * không khớp. Khác "cổng nói không có giao dịch" (đó là kết quả rỗng): ở đây chưa kết luận được gì nên đơn
 * không được huỷ vội.
 */
public class GatewayQueryException extends RuntimeException {

    public GatewayQueryException(String message) {
        super(message);
    }

    public GatewayQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
