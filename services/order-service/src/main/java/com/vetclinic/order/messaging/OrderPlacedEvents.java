package com.vetclinic.order.messaging;

import com.vetclinic.order.domain.Order;

/** Dựng {@link OrderPlacedEvent} từ đơn, dùng chung cho các chỗ phát. */
public final class OrderPlacedEvents {

    private OrderPlacedEvents() {
    }

    public static OrderPlacedEvent of(Order order) {
        return new OrderPlacedEvent(order.getId(), order.getOrderCode(), order.getTotal(),
                order.getItems() == null ? 0 : order.getItems().size(),
                order.getPaymentMethod() == null ? null : order.getPaymentMethod().name(),
                order.getRecipientName());
    }
}
