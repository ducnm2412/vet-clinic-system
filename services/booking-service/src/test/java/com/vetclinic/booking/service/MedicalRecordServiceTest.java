package com.vetclinic.booking.service;

import com.vetclinic.booking.client.PetServiceClient;
import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.domain.MedicalRecord;
import com.vetclinic.booking.domain.PrescriptionStatus;
import com.vetclinic.booking.dto.AppointmentRequest;
import com.vetclinic.booking.dto.AppointmentResponse;
import com.vetclinic.booking.dto.MedicalRecordRequest;
import com.vetclinic.booking.dto.MedicalRecordResponse;
import com.vetclinic.booking.dto.PetResponse;
import com.vetclinic.booking.dto.PrescriptionItemRequest;
import com.vetclinic.booking.exception.PrescriptionNotPaidException;
import com.vetclinic.booking.exception.ResourceNotFoundException;
import com.vetclinic.booking.repository.AppointmentRepository;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import com.vetclinic.booking.repository.MedicalRecordRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class MedicalRecordServiceTest {

    @Autowired
    private MedicalRecordService medicalRecordService;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentSlotRepository appointmentSlotRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private MedicalRecordRepository medicalRecordRepository;

    @MockBean
    private PetServiceClient petServiceClient;

    @MockBean
    private ProfileServiceClient profileServiceClient;

    @Test
    void createOrUpdateMedicalRecord_thenGet_roundTripsWithPrescriptionItems() {
        when(petServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());
        UUID customerUserId = UUID.randomUUID();
        AppointmentResponse appointment = bookAppointment(customerUserId, LocalDate.of(2026, 12, 20), LocalTime.of(9, 0));

        MedicalRecordRequest request = new MedicalRecordRequest("Otitis", "Ear cleaning + drops", "Recheck in 2 weeks",
                List.of(new PrescriptionItemRequest("Otibiovet", "5 drops", "2x/day", 10, null)));

        MedicalRecordResponse created = medicalRecordService.createOrUpdateMedicalRecord(appointment.id(), request);

        assertThat(created.diagnosis()).isEqualTo("Otitis");
        assertThat(created.status()).isEqualTo(PrescriptionStatus.PENDING);
        assertThat(created.prescriptionItems()).hasSize(1);
        assertThat(created.prescriptionItems().get(0).medicationName()).isEqualTo("Otibiovet");

        MedicalRecordResponse fetched = medicalRecordService.getMedicalRecord(appointment.id(), customerUserId, false);
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.prescriptionItems()).hasSize(1);
    }

    @Test
    void createOrUpdateMedicalRecord_calledTwice_replacesPrescriptionItems() {
        when(petServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());
        AppointmentResponse appointment = bookAppointment(UUID.randomUUID(), LocalDate.of(2026, 12, 21), LocalTime.of(9, 0));

        medicalRecordService.createOrUpdateMedicalRecord(appointment.id(), new MedicalRecordRequest(
                "Initial diagnosis", null, null,
                List.of(new PrescriptionItemRequest("DrugA", "1 tab", "1x/day", 5, null))));

        MedicalRecordResponse updated = medicalRecordService.createOrUpdateMedicalRecord(appointment.id(),
                new MedicalRecordRequest("Updated diagnosis", "New treatment", null,
                        List.of(new PrescriptionItemRequest("DrugB", "2 tabs", "2x/day", 7, null))));

        assertThat(updated.diagnosis()).isEqualTo("Updated diagnosis");
        assertThat(updated.prescriptionItems()).hasSize(1);
        assertThat(updated.prescriptionItems().get(0).medicationName()).isEqualTo("DrugB");
    }

    @Test
    void getMedicalRecord_asNonOwningCustomer_throwsResourceNotFound() {
        when(petServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());
        UUID owner = UUID.randomUUID();
        AppointmentResponse appointment = bookAppointment(owner, LocalDate.of(2026, 12, 22), LocalTime.of(9, 0));
        medicalRecordService.createOrUpdateMedicalRecord(appointment.id(),
                new MedicalRecordRequest("Diagnosis", null, null, null));

        assertThatThrownBy(() -> medicalRecordService.getMedicalRecord(appointment.id(), UUID.randomUUID(), false))
                .isInstanceOf(ResourceNotFoundException.class);

        MedicalRecordResponse asStaff = medicalRecordService.getMedicalRecord(appointment.id(), UUID.randomUUID(), true);
        assertThat(asStaff.diagnosis()).isEqualTo("Diagnosis");
    }

    @Test
    void getMedicalRecord_noneCreatedYet_throwsResourceNotFound() {
        when(petServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());
        AppointmentResponse appointment = bookAppointment(UUID.randomUUID(), LocalDate.of(2026, 12, 23), LocalTime.of(9, 0));

        assertThatThrownBy(() -> medicalRecordService.getMedicalRecord(appointment.id(), UUID.randomUUID(), true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void receivePrescription_beforePayment_throwsPrescriptionNotPaid() {
        when(petServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());
        AppointmentResponse appointment = bookAppointment(UUID.randomUUID(), LocalDate.of(2026, 12, 24), LocalTime.of(9, 0));
        medicalRecordService.createOrUpdateMedicalRecord(appointment.id(),
                new MedicalRecordRequest("Diagnosis", null, null, null));

        // Chưa thanh toán (vẫn PENDING) — staff không được phép tiếp nhận.
        assertThatThrownBy(() -> medicalRecordService.receivePrescription(appointment.id()))
                .isInstanceOf(PrescriptionNotPaidException.class);
    }

    @Test
    void markPrescriptionPaid_transitionsPendingDirectlyToReceived() {
        // Phòng khám thu tiền và giao thuốc cùng một lượt ở quầy — event payment.completed
        // phải đưa thẳng PENDING -> RECEIVED, không dừng ở PAID chờ staff tiếp nhận thêm bước nữa.
        when(petServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());
        AppointmentResponse appointment = bookAppointment(UUID.randomUUID(), LocalDate.of(2026, 12, 24), LocalTime.of(9, 0));
        MedicalRecordResponse created = medicalRecordService.createOrUpdateMedicalRecord(appointment.id(),
                new MedicalRecordRequest("Diagnosis", null, null, null));

        // Giả lập payment-service báo đã thanh toán xong (payment.completed event).
        medicalRecordService.markPrescriptionPaid(created.id());

        MedicalRecordResponse record = medicalRecordService.getMedicalRecord(appointment.id(), UUID.randomUUID(), true);
        assertThat(record.status()).isEqualTo(PrescriptionStatus.RECEIVED);
    }

    @Test
    void receivePrescription_afterPayment_isIdempotentNoOp() {
        // receivePrescription() vẫn còn đó cho trường hợp cần tiếp nhận thủ công (vd: record cũ
        // đang dừng ở PAID từ trước khi có thay đổi này) — gọi lại trên record đã RECEIVED không
        // lỗi, chỉ trả về đúng trạng thái hiện tại.
        when(petServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());
        AppointmentResponse appointment = bookAppointment(UUID.randomUUID(), LocalDate.of(2026, 12, 24), LocalTime.of(9, 0));
        medicalRecordService.createOrUpdateMedicalRecord(appointment.id(),
                new MedicalRecordRequest("Diagnosis", null, null, null));

        MedicalRecord record = medicalRecordRepository.findByAppointmentId(appointment.id()).orElseThrow();
        record.setStatus(PrescriptionStatus.PAID);
        medicalRecordRepository.saveAndFlush(record);

        MedicalRecordResponse received = medicalRecordService.receivePrescription(appointment.id());
        assertThat(received.status()).isEqualTo(PrescriptionStatus.RECEIVED);

        MedicalRecordResponse receivedAgain = medicalRecordService.receivePrescription(appointment.id());
        assertThat(receivedAgain.status()).isEqualTo(PrescriptionStatus.RECEIVED);
    }

    @Test
    void receivePrescription_noRecordYet_throwsResourceNotFound() {
        when(petServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());
        AppointmentResponse appointment = bookAppointment(UUID.randomUUID(), LocalDate.of(2026, 12, 25), LocalTime.of(9, 0));

        assertThatThrownBy(() -> medicalRecordService.receivePrescription(appointment.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markPrescriptionPaid_unknownMedicalRecordId_doesNotThrow() {
        // Event trễ/sai id không được làm consumer crash (message sẽ không được requeue vô hạn).
        medicalRecordService.markPrescriptionPaid(UUID.randomUUID());
    }

    @Test
    void listPendingPrescriptions_onlyIncludesPaidOnes() {
        // markPrescriptionPaid() không còn để lại record nào ở PAID (đi thẳng sang RECEIVED) —
        // dựng trạng thái PAID trực tiếp qua repository để kiểm truy vấn của
        // listPendingPrescriptions() vẫn lọc đúng, phòng khi có record cũ còn kẹt ở PAID.
        when(petServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());
        AppointmentResponse notYetPaid = bookAppointment(UUID.randomUUID(), LocalDate.of(2026, 12, 26), LocalTime.of(9, 0));
        AppointmentResponse paidAndPending = bookAppointment(UUID.randomUUID(), LocalDate.of(2026, 12, 26), LocalTime.of(9, 30));
        AppointmentResponse alreadyReceived = bookAppointment(UUID.randomUUID(), LocalDate.of(2026, 12, 26), LocalTime.of(10, 0));

        medicalRecordService.createOrUpdateMedicalRecord(notYetPaid.id(),
                new MedicalRecordRequest("Not paid yet", null, null, null));
        medicalRecordService.createOrUpdateMedicalRecord(paidAndPending.id(),
                new MedicalRecordRequest("Paid, waiting pickup", null, null, null));
        medicalRecordService.createOrUpdateMedicalRecord(alreadyReceived.id(),
                new MedicalRecordRequest("Already received", null, null, null));

        setStatus(paidAndPending.id(), PrescriptionStatus.PAID);
        setStatus(alreadyReceived.id(), PrescriptionStatus.RECEIVED);

        List<MedicalRecordResponse> pending = medicalRecordService.listPendingPrescriptions();

        assertThat(pending).extracting(MedicalRecordResponse::appointmentId).contains(paidAndPending.id());
        assertThat(pending).extracting(MedicalRecordResponse::appointmentId)
                .doesNotContain(notYetPaid.id(), alreadyReceived.id());
    }

    private void setStatus(UUID appointmentId, PrescriptionStatus status) {
        MedicalRecord record = medicalRecordRepository.findByAppointmentId(appointmentId).orElseThrow();
        record.setStatus(status);
        medicalRecordRepository.saveAndFlush(record);
    }

    private AppointmentResponse bookAppointment(UUID customerUserId, LocalDate date, LocalTime time) {
        appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(date).startTime(time).endTime(time.plusMinutes(30)).build());
        return appointmentService.createAppointment(customerUserId, "Bearer test-token",
                new AppointmentRequest(UUID.randomUUID(), date, time, null));
    }

    private PetResponse dummyPet() {
        return new PetResponse(UUID.randomUUID(), "Milo", "Dog", "Poodle", "MALE",
                LocalDate.of(2020, 1, 1), null, Instant.now(), Instant.now());
    }
}
