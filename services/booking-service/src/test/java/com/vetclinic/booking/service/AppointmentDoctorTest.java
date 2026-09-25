package com.vetclinic.booking.service;

import com.vetclinic.booking.client.PetServiceClient;
import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.domain.AppointmentStatus;
import com.vetclinic.booking.dto.AppointmentDetailResponse;
import com.vetclinic.booking.dto.AppointmentRequest;
import com.vetclinic.booking.dto.AppointmentResponse;
import com.vetclinic.booking.dto.DoctorSummaryResponse;
import com.vetclinic.booking.messaging.AppointmentCreatedEvent;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import com.vetclinic.booking.dto.PetResponse;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
 * VD-16 — mọi response lịch hẹn phải nói được bác sĩ nào khám.
 *
 * Trước đây chỉ có `slotId`, trong khi `GET /booking/appointments` lại lọc được theo
 * `doctorUserId`: lọc theo bác sĩ thì được, hiện bác sĩ lên màn hình thì không. Mỗi test dưới
 * đây khoá một đường trả response khác nhau, vì chỉ cần sót một đường là màn hình tương ứng lại
 * trống cột bác sĩ.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
@RecordApplicationEvents
class AppointmentDoctorTest {

    @Autowired
    private ApplicationEvents events;

    private static final LocalDate DATE = LocalDate.of(2027, 6, 1);
    private static final LocalTime NINE = LocalTime.of(9, 0);

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentSlotRepository appointmentSlotRepository;

    @MockBean
    private ProfileServiceClient profileServiceClient;

    @MockBean
    private PetServiceClient petServiceClient;

    private AppointmentSlot slotFor(UUID doctorUserId, LocalTime start) {
        return appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(doctorUserId).date(DATE).startTime(start).endTime(start.plusMinutes(30)).build());
    }

    private AppointmentResponse book(UUID customer, LocalTime start) {
        when(petServiceClient.getMyPet(any(), any())).thenReturn(pet());
        return appointmentService.createAppointment(customer, "Bearer t", new AppointmentRequest(UUID.randomUUID(), DATE, start, null));
    }

    @Test
    void create_returnsTheDoctorTheSystemAssigned() {
        UUID doctor = UUID.randomUUID();
        slotFor(doctor, NINE);

        // Khách không chọn bác sĩ — hệ thống tự xếp. Càng phải trả về cho khách biết đã xếp ai.
        assertThat(book(UUID.randomUUID(), NINE).doctorUserId()).isEqualTo(doctor);
    }

    @Test
    void listMine_includesDoctor() {
        UUID doctor = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        slotFor(doctor, NINE);
        book(customer, NINE);

        List<AppointmentResponse> mine = appointmentService.listMyAppointments(customer);

        assertThat(mine).singleElement().extracting(AppointmentResponse::doctorUserId).isEqualTo(doctor);
    }

    @Test
    void search_includesDoctorOnEveryRow() {
        // Chỗ VD-16 gây khó nhất: lễ tân xem danh sách chung của nhiều bác sĩ.
        UUID doctorA = UUID.randomUUID();
        UUID doctorB = UUID.randomUUID();
        slotFor(doctorA, NINE);
        slotFor(doctorB, LocalTime.of(9, 30));
        book(UUID.randomUUID(), NINE);
        book(UUID.randomUUID(), LocalTime.of(9, 30));

        List<AppointmentResponse> all = appointmentService.search(DATE, null, null);

        assertThat(all).extracting(AppointmentResponse::doctorUserId).containsExactlyInAnyOrder(doctorA, doctorB);
    }

    @Test
    void detail_includesDoctor() {
        UUID doctor = UUID.randomUUID();
        slotFor(doctor, NINE);
        AppointmentResponse created = book(UUID.randomUUID(), NINE);
        when(petServiceClient.getPetById(any(), any())).thenReturn(pet());

        AppointmentDetailResponse detail = appointmentService.getAppointmentDetail(created.id(), "Bearer staff");

        assertThat(detail.doctorUserId()).isEqualTo(doctor);
    }

    @Test
    void cancelAndStatusUpdate_keepDoctorInResponse() {
        // Huỷ lịch mở slot ra cho người khác, nhưng lịch đã huỷ vẫn là của bác sĩ đó —
        // không được trả về null chỉ vì slot giờ AVAILABLE.
        UUID doctor = UUID.randomUUID();
        slotFor(doctor, NINE);
        AppointmentResponse created = book(UUID.randomUUID(), NINE);

        assertThat(appointmentService.updateStatus(created.id(), AppointmentStatus.CONFIRMED).doctorUserId())
                .isEqualTo(doctor);
        assertThat(appointmentService.cancelAppointment(created.id(), null, true).doctorUserId())
                .isEqualTo(doctor);
    }

    @Test
    void createdEvent_carriesEverythingTheConfirmationEmailNeeds() {
        // CN-43: notification-service chỉ soạn email, không gọi ngược sang auth/profile.
        UUID doctor = UUID.randomUUID();
        slotFor(doctor, NINE);
        when(petServiceClient.getMyPet(any(), any())).thenReturn(pet());
        when(profileServiceClient.listDoctors()).thenReturn(List.of(
                new DoctorSummaryResponse(UUID.randomUUID(), doctor, "Trần Minh Khoa", "Nội khoa", null, 8)));

        appointmentService.createAppointment(UUID.randomUUID(), "khach@example.com", "Bearer t",
                new AppointmentRequest(UUID.randomUUID(), DATE, NINE, "Bỏ ăn"));

        AppointmentCreatedEvent e = events.stream(AppointmentCreatedEvent.class).findFirst().orElseThrow();
        assertThat(e.customerEmail()).isEqualTo("khach@example.com");
        assertThat(e.petName()).isEqualTo("Milo");
        assertThat(e.doctorName()).isEqualTo("Trần Minh Khoa");
        assertThat(e.reason()).isEqualTo("Bỏ ăn");
    }

    @Test
    void createdEvent_whenProfileServiceDown_stillBooks() {
        // Tra tên bác sĩ hỏng thì email bớt một dòng, KHÔNG được làm hỏng việc đặt lịch.
        UUID doctor = UUID.randomUUID();
        slotFor(doctor, NINE);
        when(petServiceClient.getMyPet(any(), any())).thenReturn(pet());
        when(profileServiceClient.listDoctors()).thenThrow(new RuntimeException("profile-service down"));

        AppointmentResponse res = appointmentService.createAppointment(UUID.randomUUID(), "khach@example.com",
                "Bearer t", new AppointmentRequest(UUID.randomUUID(), DATE, NINE, null));

        assertThat(res.doctorUserId()).isEqualTo(doctor);
        assertThat(events.stream(AppointmentCreatedEvent.class).findFirst().orElseThrow().doctorName()).isNull();
    }

    private static PetResponse pet() {
        return new PetResponse(UUID.randomUUID(), "Milo", "Chó", "Poodle", "MALE",
                LocalDate.of(2020, 1, 1), null, Instant.now(), Instant.now());
    }
}
