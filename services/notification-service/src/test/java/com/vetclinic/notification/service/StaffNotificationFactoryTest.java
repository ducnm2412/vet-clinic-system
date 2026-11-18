package com.vetclinic.notification.service;

import com.vetclinic.notification.messaging.AppointmentNotificationEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StaffNotificationFactoryTest {

    private static AppointmentNotificationEvent event(String type, UUID doctor) {
        return new AppointmentNotificationEvent(type, UUID.randomUUID(), UUID.randomUUID(), "k@example.com", "Milo",
                null, "Ngứa tai", null, LocalDate.of(2026, 11, 20), LocalTime.of(9, 30), LocalTime.of(10, 0),
                null, null, doctor);
    }

    @Test
    void doctorAssigned_namesThePetAndTheTime() {
        var c = StaffNotificationFactory.forDoctor(event("CONFIRMED", UUID.randomUUID())).orElseThrow();

        assertThat(c.type()).isEqualTo("DOCTOR_ASSIGNED");
        assertThat(c.message()).contains("Milo", "09:30 ngày 20/11/2026", "Ngứa tai");
        assertThat(c.link()).isEqualTo("/doctor/appointments");
    }

    @Test
    void doctorTold_whenTheirAppointmentIsCancelled_byEitherSide() {
        var byCustomer = StaffNotificationFactory.forDoctor(event("CANCELLED_BY_CUSTOMER", UUID.randomUUID()))
                .orElseThrow();
        var byClinic = StaffNotificationFactory.forDoctor(event("CANCELLED_BY_CLINIC", UUID.randomUUID()))
                .orElseThrow();

        assertThat(byCustomer.type()).isEqualTo("APPOINTMENT_CANCELLED");
        assertThat(byCustomer.message()).contains("khách huỷ");
        assertThat(byClinic.message()).contains("Phòng khám đã huỷ");
    }

    @Test
    void noDoctor_orAnEventTheDoctorDoesNotCareAbout_meansNothingToSend() {
        assertThat(StaffNotificationFactory.forDoctor(event("CONFIRMED", null))).isEmpty();
        assertThat(StaffNotificationFactory.forDoctor(event("PROPOSAL", UUID.randomUUID()))).isEmpty();
        assertThat(StaffNotificationFactory.forDoctor(event("REQUEST_RECEIVED", UUID.randomUUID()))).isEmpty();
    }

    @Test
    void admin_isToldOnlyAboutNewRequests() {
        var c = StaffNotificationFactory.forAdmin(event("REQUEST_RECEIVED", null)).orElseThrow();

        assertThat(c.type()).isEqualTo("NEW_REQUEST");
        assertThat(c.link()).isEqualTo("/admin/appointment-requests");
        assertThat(StaffNotificationFactory.forAdmin(event("CONFIRMED", UUID.randomUUID()))).isEmpty();
    }

    @Test
    void paymentPending_mentionsTheDrugCountWhenThereAreDrugs() {
        assertThat(StaffNotificationFactory.paymentPending(3).message()).contains("3 loại thuốc");
        assertThat(StaffNotificationFactory.paymentPending(0).message()).doesNotContain("loại thuốc");
        assertThat(StaffNotificationFactory.paymentPending(1).link()).isEqualTo("/staff/payments");
    }

    @Test
    void unknownPetName_doesNotBreakTheText() {
        var e = new AppointmentNotificationEvent("CONFIRMED", UUID.randomUUID(), null, null, " ", null, null, null,
                null, null, null, null, null, UUID.randomUUID());

        assertThat(StaffNotificationFactory.forDoctor(e).orElseThrow().message()).contains("một thú cưng");
    }

    @Test
    void newOrder_saysHowItWillBePaidAndHowMuch() {
        var cod = StaffNotificationFactory.newOrder(new com.vetclinic.notification.messaging.OrderPlacedEvent(
                UUID.randomUUID(), "ORD-1", new java.math.BigDecimal("330000"), 2, "COD", "Lan", null, null));
        var online = StaffNotificationFactory.newOrder(new com.vetclinic.notification.messaging.OrderPlacedEvent(
                UUID.randomUUID(), "ORD-2", new java.math.BigDecimal("50000"), 1, "ONLINE", null, null, null));

        assertThat(cod.message()).contains("ORD-1", "của Lan", "2 sản phẩm", "330.000đ", "khi nhận hàng");
        assertThat(online.message()).contains("ORD-2", "đã thanh toán online").doesNotContain(" của ");
        assertThat(cod.link()).isEqualTo("/staff/orders");
        assertThat(cod.title()).isEqualTo("Có đơn hàng mới chờ xác nhận");
    }

    @Test
    void newOrder_toldToTheAssignedStaffMemberSaysItIsTheirs() {
        var assigned = StaffNotificationFactory.newOrder(new com.vetclinic.notification.messaging.OrderPlacedEvent(
                UUID.randomUUID(), "ORD-3", new java.math.BigDecimal("50000"), 1, "COD", "Lan", UUID.randomUUID(), "Minh"));

        assertThat(assigned.title()).isEqualTo("Bạn được giao một đơn hàng mới");
    }

    @Test
    void newOrderForAdmin_saysWhoHandlesItOrThatNobodyDoes() {
        var handled = StaffNotificationFactory.newOrderForAdmin(new com.vetclinic.notification.messaging.OrderPlacedEvent(
                UUID.randomUUID(), "ORD-4", new java.math.BigDecimal("473000"), 2, "COD", "Huy", UUID.randomUUID(),
                "Ngô Đức Minh"));
        var unhandled = StaffNotificationFactory.newOrderForAdmin(new com.vetclinic.notification.messaging.OrderPlacedEvent(
                UUID.randomUUID(), "ORD-5", new java.math.BigDecimal("473000"), 2, "ONLINE", "Huy", null, null));

        assertThat(handled.message()).contains("ORD-4", "473.000đ", "Giao cho Ngô Đức Minh");
        assertThat(handled.link()).isEqualTo("/admin/orders");
        assertThat(unhandled.message()).contains("ORD-5", "Chưa giao cho nhân viên nào");
    }

    @Test
    void staffAreToldAboutNewRequestsOnly() {
        assertThat(StaffNotificationFactory.forStaff(event("REQUEST_RECEIVED", null)).orElseThrow().link())
                .isEqualTo("/staff/appointments");
        assertThat(StaffNotificationFactory.forStaff(event("CONFIRMED", UUID.randomUUID()))).isEmpty();
    }
}
