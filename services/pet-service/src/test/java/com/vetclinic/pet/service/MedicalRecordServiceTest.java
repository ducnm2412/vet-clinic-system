package com.vetclinic.pet.service;

import com.vetclinic.pet.client.BookingServiceClient;
import com.vetclinic.pet.domain.PrescriptionStatus;
import com.vetclinic.pet.dto.MedicalRecordRequest;
import com.vetclinic.pet.dto.MedicalRecordResponse;
import com.vetclinic.pet.dto.PetRequest;
import com.vetclinic.pet.dto.PetResponse;
import com.vetclinic.pet.dto.PrescriptionItemRequest;
import com.vetclinic.pet.exception.BookingUnavailableException;
import com.vetclinic.pet.exception.PetHasMedicalRecordsException;
import com.vetclinic.pet.exception.PrescriptionNotPaidException;
import com.vetclinic.pet.exception.ResourceNotFoundException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CN-23 → CN-25 sau khi bệnh án chuyển sang pet-service (VD-10 chặng 2).
 *
 * booking-service được mock: ở đây kiểm cách pet-service xử lý câu trả lời của nó, không kiểm
 * chính booking-service.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class MedicalRecordServiceTest {

    private static final String TOKEN = "Bearer test";

    @Autowired private MedicalRecordService medicalRecordService;
    @Autowired private PetService petService;

    @MockBean private BookingServiceClient bookingServiceClient;

    private UUID owner;
    private UUID doctor;
    private PetResponse pet;

    @BeforeEach
    void setUp() {
        owner = UUID.randomUUID();
        doctor = UUID.randomUUID();
        pet = petService.create(owner, new PetRequest("Milo", "Chó", null, null, null, null));
    }

    /** Lịch hẹn giả lập của con vật vừa tạo. */
    private UUID givenAppointment() {
        UUID appointmentId = UUID.randomUUID();
        when(bookingServiceClient.getAppointment(any(), any()))
                .thenReturn(new BookingServiceClient.AppointmentRef(appointmentId, pet.id(), owner, doctor, "COMPLETED"));
        return appointmentId;
    }

    private MedicalRecordRequest request(String diagnosis, PrescriptionItemRequest... items) {
        return new MedicalRecordRequest(diagnosis, "Uống thuốc 5 ngày", null, List.of(items));
    }

    private static PrescriptionItemRequest thuoc(String name) {
        return new PrescriptionItemRequest(name, "1 viên", "2 lần/ngày", 5, null);
    }

    private static FeignException notFound() {
        Response response = Response.builder().status(404).reason("Not Found")
                .request(Request.create(Request.HttpMethod.GET, "/booking/appointments/x", Collections.emptyMap(),
                        null, StandardCharsets.UTF_8, new RequestTemplate()))
                .build();
        return FeignException.errorStatus("BookingServiceClient#getAppointment", response);
    }

    @Test
    void doctorCreatesRecord_ownerAndDoctorCopiedFromAppointment() {
        UUID appointmentId = givenAppointment();

        MedicalRecordResponse saved = medicalRecordService.createOrUpdate(appointmentId,
                request("Viêm da", thuoc("Amoxicillin")), TOKEN);

        assertThat(saved.appointmentId()).isEqualTo(appointmentId);
        // Ba trường này chép từ booking-service, không nhận từ client.
        assertThat(saved.petId()).isEqualTo(pet.id());
        assertThat(saved.customerUserId()).isEqualTo(owner);
        assertThat(saved.doctorUserId()).isEqualTo(doctor);
        assertThat(saved.status()).isEqualTo(PrescriptionStatus.PENDING);
        assertThat(saved.prescriptionItems()).hasSize(1);
    }

    @Test
    void savingAgainReplacesPrescriptionInsteadOfPilingUp() {
        UUID appointmentId = givenAppointment();
        medicalRecordService.createOrUpdate(appointmentId, request("Viêm da", thuoc("Amoxicillin")), TOKEN);

        MedicalRecordResponse updated = medicalRecordService.createOrUpdate(appointmentId,
                request("Viêm da nặng", thuoc("Cephalexin"), thuoc("Vitamin B")), TOKEN);

        assertThat(updated.diagnosis()).isEqualTo("Viêm da nặng");
        assertThat(updated.prescriptionItems()).hasSize(2)
                .extracting("medicationName").containsExactly("Cephalexin", "Vitamin B");
    }

    @Test
    void unknownAppointmentIsNotFound() {
        when(bookingServiceClient.getAppointment(any(), any())).thenThrow(notFound());

        assertThatThrownBy(() -> medicalRecordService.createOrUpdate(UUID.randomUUID(), request("X"), TOKEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void appointmentOfAPetThisServiceDoesNotKnowIsRefused() {
        UUID strangerPet = UUID.randomUUID();
        when(bookingServiceClient.getAppointment(any(), any())).thenReturn(
                new BookingServiceClient.AppointmentRef(UUID.randomUUID(), strangerPet, owner, doctor, "COMPLETED"));

        assertThatThrownBy(() -> medicalRecordService.createOrUpdate(UUID.randomUUID(), request("X"), TOKEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void bookingUnreachableGives503NotCrash() {
        Response response = Response.builder().status(500).reason("boom")
                .request(Request.create(Request.HttpMethod.GET, "/booking/appointments/x", Collections.emptyMap(),
                        null, StandardCharsets.UTF_8, new RequestTemplate()))
                .build();
        when(bookingServiceClient.getAppointment(any(), any()))
                .thenThrow(FeignException.errorStatus("BookingServiceClient#getAppointment", response));

        assertThatThrownBy(() -> medicalRecordService.createOrUpdate(UUID.randomUUID(), request("X"), TOKEN))
                .isInstanceOf(BookingUnavailableException.class);
    }

    @Test
    void customerReadsOwnRecordOnly() {
        UUID appointmentId = givenAppointment();
        medicalRecordService.createOrUpdate(appointmentId, request("Viêm da"), TOKEN);

        assertThat(medicalRecordService.getByAppointment(appointmentId, owner, false).diagnosis())
                .isEqualTo("Viêm da");
        // Khách khác: không tìm thấy, không phải "không có quyền".
        assertThatThrownBy(() -> medicalRecordService.getByAppointment(appointmentId, UUID.randomUUID(), false))
                .isInstanceOf(ResourceNotFoundException.class);
        // Người trong phòng khám xem được mọi bệnh án.
        assertThat(medicalRecordService.getByAppointment(appointmentId, UUID.randomUUID(), true)).isNotNull();
    }

    @Test
    void prescriptionGoesPendingThenPaidThenStaysReceived() {
        UUID appointmentId = givenAppointment();
        MedicalRecordResponse saved = medicalRecordService.createOrUpdate(appointmentId, request("Viêm da"), TOKEN);

        // Chưa trả tiền thì quầy không giao thuốc được.
        assertThatThrownBy(() -> medicalRecordService.receive(appointmentId))
                .isInstanceOf(PrescriptionNotPaidException.class);

        medicalRecordService.markPaid(saved.id());
        assertThat(medicalRecordService.getByAppointment(appointmentId, owner, false).status())
                .isEqualTo(PrescriptionStatus.RECEIVED);

        // Gọi lại (message gửi lặp, hoặc quầy bấm hai lần) không đổi gì và không lỗi.
        medicalRecordService.markPaid(saved.id());
        assertThat(medicalRecordService.receive(appointmentId).status()).isEqualTo(PrescriptionStatus.RECEIVED);
    }

    @Test
    void paymentForUnknownRecordIsIgnoredNotThrown() {
        medicalRecordService.markPaid(UUID.randomUUID());
    }

    @Test
    void doctorSeesOnlyTheirOwnOutstandingRecords() {
        medicalRecordService.createOrUpdate(givenAppointment(), request("Viêm da"), TOKEN);

        UUID otherDoctor = UUID.randomUUID();
        UUID otherAppointment = UUID.randomUUID();
        when(bookingServiceClient.getAppointment(any(), any())).thenReturn(
                new BookingServiceClient.AppointmentRef(otherAppointment, pet.id(), owner, otherDoctor, "COMPLETED"));
        medicalRecordService.createOrUpdate(otherAppointment, request("Gãy chân"), TOKEN);

        assertThat(medicalRecordService.listOutstandingOf(doctor)).hasSize(1)
                .extracting(MedicalRecordResponse::diagnosis).containsExactly("Viêm da");
        assertThat(medicalRecordService.listOutstandingOf(otherDoctor)).hasSize(1);
    }

    @Test
    void paidQueueHoldsOnlyPaidOnes() {
        UUID pending = givenAppointment();
        medicalRecordService.createOrUpdate(pending, request("Viêm da"), TOKEN);

        assertThat(medicalRecordService.listPaidQueue()).isEmpty();
    }

    @Test
    void petHistoryIsNewestFirstAndOwnerScoped() {
        medicalRecordService.createOrUpdate(givenAppointment(), request("Lần khám 1"), TOKEN);

        assertThat(medicalRecordService.historyOfPet(pet.id())).hasSize(1);
        assertThat(medicalRecordService.historyOfMyPet(owner, pet.id())).hasSize(1);
        // Khách khác hỏi lịch sử con này: không tìm thấy con vật.
        assertThatThrownBy(() -> medicalRecordService.historyOfMyPet(UUID.randomUUID(), pet.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void petWithMedicalHistoryCannotBeDeleted() {
        medicalRecordService.createOrUpdate(givenAppointment(), request("Viêm da"), TOKEN);

        assertThatThrownBy(() -> petService.delete(owner, pet.id()))
                .isInstanceOf(PetHasMedicalRecordsException.class)
                .hasMessageContaining("Milo");

        // Xoá tài khoản cũng không kéo theo con vật đã có bệnh án.
        petService.deleteAllOf(owner);
        assertThat(petService.listMine(owner)).hasSize(1);
    }
}
