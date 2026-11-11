package com.vetclinic.booking.service;

import com.vetclinic.booking.client.PetServiceClient;
import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.domain.ClinicSchedule;
import com.vetclinic.booking.dto.AppointmentRequest;
import com.vetclinic.booking.dto.AppointmentResponse;
import com.vetclinic.booking.dto.DoctorSummaryResponse;
import com.vetclinic.booking.dto.PetResponse;
import com.vetclinic.booking.messaging.AppointmentNotificationEvent;
import com.vetclinic.booking.messaging.AppointmentNotificationEvent.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Thư báo khách ở từng bước của lịch đặt theo yêu cầu. Test bọc transaction nên sự kiện chưa gửi
 * lên RabbitMQ (chỉ gửi sau commit) — ở đây chỉ kiểm sự kiện được phát đúng lúc, đúng nội dung.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
@RecordApplicationEvents
class AppointmentNotificationEventsTest {

    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime TEN = LocalTime.of(10, 0);

    @Autowired
    private AppointmentRequestService requests;
    @Autowired
    private AppointmentAssignmentService assignment;
    @Autowired
    private AppointmentService appointments;
    @Autowired
    private ApplicationEvents events;

    @MockBean
    private PetServiceClient petServiceClient;
    @MockBean
    private ProfileServiceClient profileServiceClient;

    private final LocalDate day = LocalDate.now(ClinicSchedule.ZONE_ID).plusDays(16);
    private final UUID doctor = UUID.randomUUID();
    private final UUID customer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(petServiceClient.getMyPet(any(), any())).thenReturn(new PetResponse(UUID.randomUUID(),
                UUID.randomUUID(), "Milo", "Dog", "Poodle", "MALE", LocalDate.of(2020, 1, 1), null, null, null,
                Instant.now(), Instant.now()));
        when(profileServiceClient.listDoctors()).thenReturn(List.of(
                new DoctorSummaryResponse(UUID.randomUUID(), doctor, "Trần Minh Khoa", null, null, null),
                new DoctorSummaryResponse(UUID.randomUUID(), UUID.randomUUID(), "BS Khác", null, null, null)));
    }

    @Test
    void sendingARequest_emailsTheCustomerThatItWasReceived() {
        AppointmentResponse r = request("khach@example.com", NINE);

        AppointmentNotificationEvent e = only(Type.REQUEST_RECEIVED);
        assertThat(e.appointmentId()).isEqualTo(r.id());
        assertThat(e.customerEmail()).isEqualTo("khach@example.com");
        assertThat(e.petName()).isEqualTo("Milo");
        assertThat(e.date()).isEqualTo(day);
        assertThat(e.startTime()).isEqualTo(NINE);
        assertThat(e.doctorName()).isNull();
    }

    @Test
    void assigning_emailsTheConfirmationWithTheDoctorsName() {
        AppointmentResponse r = request("khach@example.com", NINE);

        assignment.assign(r.id(), doctor);

        AppointmentNotificationEvent e = only(Type.CONFIRMED);
        assertThat(e.doctorName()).isEqualTo("Trần Minh Khoa");
        assertThat(e.date()).isEqualTo(day);
        assertThat(e.startTime()).isEqualTo(NINE);
        assertThat(e.endTime()).isEqualTo(NINE.plusMinutes(30));
    }

    @Test
    void proposing_emailsBothTheNewTimeAndTheOneTheCustomerAskedFor() {
        AppointmentResponse r = request("khach@example.com", NINE);

        assignment.propose(r.id(), doctor, day, TEN);

        AppointmentNotificationEvent e = only(Type.PROPOSAL);
        assertThat(e.startTime()).isEqualTo(TEN);
        assertThat(e.preferredTime()).isEqualTo(NINE);
        assertThat(e.preferredDate()).isEqualTo(day);
        assertThat(e.doctorName()).isEqualTo("Trần Minh Khoa");
    }

    @Test
    void acceptingTheProposal_emailsAConfirmation() {
        AppointmentResponse r = request("khach@example.com", NINE);
        assignment.propose(r.id(), doctor, day, TEN);

        assignment.acceptProposal(r.id(), customer);

        AppointmentNotificationEvent e = only(Type.CONFIRMED);
        assertThat(e.startTime()).isEqualTo(TEN);
        assertThat(e.doctorName()).isEqualTo("Trần Minh Khoa");
    }

    @Test
    void whenTheClinicCancels_theCustomerIsToldButNotWhenTheyCancelThemselves() {
        AppointmentResponse byClinic = request("khach@example.com", NINE);
        appointments.cancelAppointment(byClinic.id(), UUID.randomUUID(), true);
        assertThat(only(Type.CANCELLED_BY_CLINIC).appointmentId()).isEqualTo(byClinic.id());

        AppointmentResponse byCustomer = request("khach@example.com", TEN);
        appointments.cancelAppointment(byCustomer.id(), customer, false);
        // Vẫn chỉ một thư huỷ — lần khách tự huỷ không thêm thư.
        assertThat(sent(Type.CANCELLED_BY_CLINIC)).hasSize(1);
    }

    @Test
    void aCustomerCancellingTheirOwnRequestAsStaff_isNotMistakenForTheClinicCancelling() {
        AppointmentResponse r = request("khach@example.com", NINE);
        // Nhân viên kiêm chủ lịch (cùng userId): vẫn là chính họ huỷ.
        appointments.cancelAppointment(r.id(), customer, true);
        assertThat(sent(Type.CANCELLED_BY_CLINIC)).isEmpty();
    }

    @Test
    void withoutAnEmailOnFile_nothingIsSent() {
        AppointmentResponse r = request(null, NINE);

        assignment.assign(r.id(), doctor);

        assertThat(events.stream(AppointmentNotificationEvent.class).count()).isZero();
    }

    // ---- helpers ----

    private AppointmentResponse request(String email, LocalTime time) {
        return requests.createRequest(customer, email, "Bearer t",
                new AppointmentRequest(UUID.randomUUID(), day, time, null, "Ngứa tai"));
    }

    private List<AppointmentNotificationEvent> sent(Type type) {
        return events.stream(AppointmentNotificationEvent.class).filter(e -> e.type() == type).toList();
    }

    private AppointmentNotificationEvent only(Type type) {
        List<AppointmentNotificationEvent> list = sent(type);
        assertThat(list).hasSize(1);
        return list.get(0);
    }
}
