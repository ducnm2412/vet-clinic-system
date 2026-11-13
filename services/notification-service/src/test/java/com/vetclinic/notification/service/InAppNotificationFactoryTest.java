package com.vetclinic.notification.service;

import com.vetclinic.notification.messaging.AppointmentNotificationEvent;
import com.vetclinic.notification.service.InAppNotificationFactory.Content;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InAppNotificationFactoryTest {

    private final UUID appointmentId = UUID.randomUUID();

    private AppointmentNotificationEvent event(String type, String doctor) {
        return new AppointmentNotificationEvent(type, appointmentId, UUID.randomUUID(), "k@example.com", "Milo",
                null, null, doctor, LocalDate.of(2026, 11, 13), LocalTime.of(8, 30),
                LocalTime.of(9, 0), LocalDate.of(2026, 11, 13), LocalTime.of(8, 0));
    }

    @Test
    void proposal_namesBothTimesAndLinksToTheAppointment() {
        Content c = InAppNotificationFactory.forAppointment(event("PROPOSAL", "Khoa")).orElseThrow();

        assertThat(c.title()).isEqualTo("Phòng khám đề xuất giờ khác cho Milo");
        assertThat(c.message()).contains("08:00 ngày 13/11/2026").contains("08:30 ngày 13/11/2026")
                .contains("bác sĩ Khoa");
        assertThat(c.link()).isEqualTo("/appointments/" + appointmentId);
    }

    @Test
    void confirmed_mentionsTheDoctorWhenKnown_andOmitsItWhenNot() {
        assertThat(InAppNotificationFactory.forAppointment(event("CONFIRMED", "Khoa")).orElseThrow().message())
                .contains("bác sĩ Khoa");
        assertThat(InAppNotificationFactory.forAppointment(event("CONFIRMED", null)).orElseThrow().message())
                .doesNotContain("bác sĩ");
    }

    @Test
    void requestReceived_saysItIsNotConfirmedYet() {
        Content c = InAppNotificationFactory.forAppointment(event("REQUEST_RECEIVED", null)).orElseThrow();

        assertThat(c.title()).contains("Milo");
        assertThat(c.message()).contains("chưa phải lịch đã chốt");
    }

    @Test
    void cancelledByClinic_linksToBookingANewOne_notTheCancelledAppointment() {
        Content c = InAppNotificationFactory.forAppointment(event("CANCELLED_BY_CLINIC", null)).orElseThrow();

        assertThat(c.link()).isEqualTo("/appointments/create");
    }

    @Test
    void unknownType_producesNothing() {
        assertThat(InAppNotificationFactory.forAppointment(event("SOMETHING_NEW", null))).isEmpty();
        assertThat(InAppNotificationFactory.forAppointment(event(null, null))).isEmpty();
    }

    @Test
    void blankPetName_fallsBackToAGenericWord() {
        AppointmentNotificationEvent e = new AppointmentNotificationEvent("CONFIRMED", appointmentId,
                UUID.randomUUID(), null, "  ", null, null, null, null, null, null, null, null);

        assertThat(InAppNotificationFactory.forAppointment(e).orElseThrow().title()).contains("thú cưng của bạn");
    }
}
