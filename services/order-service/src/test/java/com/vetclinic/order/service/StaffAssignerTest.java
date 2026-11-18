package com.vetclinic.order.service;

import com.vetclinic.order.client.AuthInternalClient;
import com.vetclinic.order.client.StaffInternalClient;
import com.vetclinic.order.domain.Order;
import com.vetclinic.order.domain.OrderChannel;
import com.vetclinic.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaffAssignerTest {

    @Mock private AuthInternalClient authClient;
    @Mock private StaffInternalClient staffClient;
    @Mock private OrderRepository orderRepository;

    private StaffAssigner assigner;
    private final UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private final UUID c = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private final UUID doctor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assigner = new StaffAssigner(authClient, staffClient, orderRepository, "key");
        when(authClient.userIdsByRole("STAFF", "key")).thenReturn(List.of(a, b, c));
        when(staffClient.userIdsOnShift(anyString(), eq("key"))).thenReturn(List.of(a, b, doctor));
        when(orderRepository.assignmentLoad(any(Collection.class), any(Instant.class))).thenReturn(List.of());
        when(authClient.userNames(any(List.class), eq("key"))).thenAnswer(inv -> {
            UUID id = ((List<UUID>) inv.getArgument(0)).get(0);
            return java.util.Map.of(id, id.equals(b) ? "Nhan Vien B" : "Nhan Vien");
        });
    }

    private Order onlineOrder() {
        return Order.builder().id(UUID.randomUUID()).orderCode("VC-1").channel(OrderChannel.ONLINE).build();
    }

    private static Object[] load(UUID id, long count, Instant last) {
        return new Object[]{id, count, last};
    }

    @Test
    void givesTheOrderToTheStaffMemberWithTheFewestOrdersToday() {
        when(orderRepository.assignmentLoad(any(Collection.class), any(Instant.class)))
                .thenReturn(List.<Object[]>of(load(a, 3, Instant.now()), load(b, 1, Instant.now())));
        Order order = onlineOrder();

        assigner.assign(order);

        assertThat(order.getAssignedStaffId()).isEqualTo(b);
        assertThat(order.getAssignedStaffName()).isEqualTo("Nhan Vien B");
        assertThat(order.getAssignedAt()).isNotNull();
    }

    @Test
    void aNameThatCannotBeLookedUp_doesNotStopTheAssignment() {
        when(authClient.userNames(any(List.class), eq("key"))).thenThrow(new RuntimeException("auth down"));
        Order order = onlineOrder();

        assigner.assign(order);

        assertThat(order.getAssignedStaffId()).isNotNull();
        assertThat(order.getAssignedStaffName()).isNull();
    }

    @Test
    void whenTied_theOneAssignedLongestAgoGoesFirst_andNeverAssignedBeatsAssigned() {
        Instant now = Instant.now();
        when(orderRepository.assignmentLoad(any(Collection.class), any(Instant.class)))
                .thenReturn(List.<Object[]>of(load(a, 1, now), load(b, 1, now.minusSeconds(600))));
        assertThat(assigner.pick(java.time.LocalDate.now())).contains(b);

        when(orderRepository.assignmentLoad(any(Collection.class), any(Instant.class)))
                .thenReturn(List.<Object[]>of(load(a, 1, now)));
        assertThat(assigner.pick(java.time.LocalDate.now())).contains(b);   // b chưa được giao đơn nào hôm nay
    }

    @Test
    void onlyStaffWhoHaveAShiftAreConsidered_notDoctorsNorStaffOffToday() {
        // c không có ca, doctor có ca nhưng không phải nhân viên: chỉ còn a và b.
        for (int i = 0; i < 6; i++) {
            Order order = onlineOrder();
            assigner.assign(order);
            assertThat(order.getAssignedStaffId()).isIn(a, b);
        }
    }

    @Test
    void nobodyOnShift_leavesTheOrderUnassigned() {
        when(staffClient.userIdsOnShift(anyString(), eq("key"))).thenReturn(List.of(doctor));
        Order order = onlineOrder();

        assigner.assign(order);

        assertThat(order.getAssignedStaffId()).isNull();
    }

    @Test
    void anOtherServiceBeingDown_neverBreaksTheOrder() {
        when(staffClient.userIdsOnShift(anyString(), eq("key"))).thenThrow(new RuntimeException("staff-service down"));
        Order order = onlineOrder();

        assigner.assign(order);   // không ném lỗi

        assertThat(order.getAssignedStaffId()).isNull();
    }

    @Test
    void counterInvoicesAreNotAssigned() {
        Order order = Order.builder().id(UUID.randomUUID()).orderCode("VC-2").channel(OrderChannel.COUNTER).build();

        assigner.assign(order);

        assertThat(order.getAssignedStaffId()).isNull();
        verifyNoInteractions(authClient, staffClient);
    }

    @Test
    void anAlreadyAssignedOrderIsLeftAlone() {
        Order order = onlineOrder();
        order.setAssignedStaffId(c);

        assigner.assign(order);

        assertThat(order.getAssignedStaffId()).isEqualTo(c);
        verifyNoInteractions(authClient, staffClient);
    }
}
