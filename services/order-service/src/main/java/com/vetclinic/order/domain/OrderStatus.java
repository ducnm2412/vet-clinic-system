package com.vetclinic.order.domain;

import java.util.Set;

/**
 * CN-35: luồng trạng thái đơn hàng.
 *
 * Đề tài dùng COD nên không có bước "chờ thanh toán" của cổng trực tuyến: đơn tạo ra là
 * PENDING (chờ nhân viên xác nhận), tiền chỉ thu khi giao xong.
 */
public enum OrderStatus {

    /** Khách vừa đặt, chờ nhân viên xác nhận. Chưa trừ kho. */
    PENDING,

    /** Nhân viên đã xác nhận — đây là lúc chốt bán và trừ tồn kho. */
    CONFIRMED,

    /** Đang giao tới khách. */
    SHIPPING,

    /** Đã giao và thu tiền xong. */
    COMPLETED,

    /** Đã huỷ. Nếu huỷ sau CONFIRMED thì phải hoàn kho. */
    CANCELLED;

    /**
     * Các trạng thái được phép chuyển tới từ trạng thái hiện tại. Khai ngay trong enum để
     * luật luồng nằm một chỗ, không rải rác trong service.
     */
    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PENDING -> Set.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> Set.of(SHIPPING, CANCELLED);
            case SHIPPING -> Set.of(COMPLETED);
            // Trạng thái kết thúc, không đi tiếp được nữa.
            case COMPLETED, CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return allowedNext().contains(target);
    }

    /** Từ CONFIRMED trở đi là kho đã bị trừ, huỷ thì phải trả hàng về. */
    public boolean stockAlreadyDeducted() {
        return this == CONFIRMED || this == SHIPPING || this == COMPLETED;
    }
}
