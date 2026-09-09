package com.vetclinic.order.service;

import com.vetclinic.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Luật chuyển trạng thái là phần dễ sai nhất của CN-35, nên khoá lại bằng test riêng. */
class OrderStatusTest {

    @Test
    void pending_canGoToConfirmedOrCancelled() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void pending_cannotSkipToShippingOrCompleted() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPING)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
    }

    @Test
    void confirmed_canShipOrCancel() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.SHIPPING)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void shipping_canOnlyComplete() {
        assertThat(OrderStatus.SHIPPING.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
        // Đang trên đường giao thì không huỷ được nữa — muốn huỷ phải hoàn tất rồi làm đơn trả.
        assertThat(OrderStatus.SHIPPING.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void terminalStates_haveNoNextStep() {
        assertThat(OrderStatus.COMPLETED.allowedNext()).isEmpty();
        assertThat(OrderStatus.CANCELLED.allowedNext()).isEmpty();
    }

    @Test
    void stockDeducted_onlyFromConfirmedOnward() {
        // Quyết định quan trọng: kho trừ lúc XÁC NHẬN, không phải lúc đặt hay lúc giao xong.
        assertThat(OrderStatus.PENDING.stockAlreadyDeducted()).isFalse();
        assertThat(OrderStatus.CONFIRMED.stockAlreadyDeducted()).isTrue();
        assertThat(OrderStatus.SHIPPING.stockAlreadyDeducted()).isTrue();
        assertThat(OrderStatus.COMPLETED.stockAlreadyDeducted()).isTrue();
        assertThat(OrderStatus.CANCELLED.stockAlreadyDeducted()).isFalse();
    }
}
