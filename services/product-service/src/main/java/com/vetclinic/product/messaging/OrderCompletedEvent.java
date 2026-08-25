package com.vetclinic.product.messaging;

import java.util.List;
import java.util.UUID;

/**
 * Sự kiện "đơn hàng thành công" do order-service phát (CN-37).
 * order-service chưa được code — hợp đồng này là thoả thuận trước để khi làm tới nơi
 * chỉ cần publish đúng cấu trúc là product-service tự trừ kho.
 */
public record OrderCompletedEvent(UUID orderId, List<Line> lines) {

    public record Line(UUID productId, int quantity) {
    }
}
