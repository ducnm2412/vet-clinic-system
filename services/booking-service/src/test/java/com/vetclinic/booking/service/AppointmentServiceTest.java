package com.vetclinic.booking.service;

import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.domain.AppointmentStatus;
import com.vetclinic.booking.domain.SlotStatus;
import com.vetclinic.booking.dto.AppointmentDetailResponse;
import com.vetclinic.booking.dto.AppointmentRequest;
import com.vetclinic.booking.dto.AppointmentResponse;
import com.vetclinic.booking.dto.PetResponse;
import com.vetclinic.booking.dto.SuggestedSlotResponse;
import com.vetclinic.booking.exception.ResourceNotFoundException;
import com.vetclinic.booking.exception.SlotFullyBookedException;
import com.vetclinic.booking.repository.AppointmentRepository;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "eureka.client.enabled=false")
class AppointmentServiceTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentSlotRepository appointmentSlotRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @MockBean
    private ProfileServiceClient profileServiceClient;

    @Test
    @Transactional
    void createAppointment_bookAvailableSlot_success() {
        when(profileServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());

        LocalDate date = LocalDate.of(2026, 12, 1);
        LocalTime time = LocalTime.of(9, 0);
        AppointmentSlot slot = appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(date).startTime(time).endTime(time.plusMinutes(30)).build());

        UUID petId = UUID.randomUUID();
        AppointmentRequest request = new AppointmentRequest(petId, date, time, "Checkup");

        AppointmentResponse response = appointmentService.createAppointment(
                UUID.randomUUID(), "Bearer test-token", request);

        assertThat(response.status()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(response.slotId()).isEqualTo(slot.getId());
        assertThat(response.petId()).isEqualTo(petId);

        AppointmentSlot reloaded = appointmentSlotRepository.findById(slot.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    @Transactional
    void createAppointment_petNotOwnedByCustomer_throwsResourceNotFound() {
        when(profileServiceClient.getMyPet(any(), any())).thenThrow(notFoundFromProfileService());

        LocalDate date = LocalDate.of(2026, 12, 2);
        LocalTime time = LocalTime.of(9, 0);
        appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(date).startTime(time).endTime(time.plusMinutes(30)).build());

        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), date, time, null);

        assertThatThrownBy(() -> appointmentService.createAppointment(UUID.randomUUID(), "Bearer test-token", request))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(appointmentRepository.count()).isZero();
    }

    @Test
    @Transactional
    void createAppointment_slotFullyBooked_suggestsNearestSameDayTime() {
        when(profileServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());

        LocalDate date = LocalDate.of(2026, 12, 3);
        LocalTime requestedTime = LocalTime.of(9, 0);
        // Không có slot nào lúc 9h, nhưng có slot trống lúc 9h30 cùng ngày -> phải được gợi ý.
        appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(date)
                .startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 0)).build());

        AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), date, requestedTime, null);

        assertThatThrownBy(() -> appointmentService.createAppointment(UUID.randomUUID(), "Bearer test-token", request))
                .isInstanceOf(SlotFullyBookedException.class)
                .satisfies(ex -> {
                    List<SuggestedSlotResponse> suggestions = ((SlotFullyBookedException) ex).getSuggestions();
                    assertThat(suggestions).hasSize(1);
                    assertThat(suggestions.get(0).startTime()).isEqualTo(LocalTime.of(9, 30));
                });
    }

    @Test
    void createAppointment_preventsDoubleBooking_underConcurrency() throws InterruptedException {
        when(profileServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());

        LocalDate date = LocalDate.of(2026, 12, 4);
        LocalTime time = LocalTime.of(9, 0);
        AppointmentSlot slot = appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(date).startTime(time).endTime(time.plusMinutes(30)).build());

        int threadCount = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                AppointmentRequest request = new AppointmentRequest(UUID.randomUUID(), date, time, null);
                try {
                    appointmentService.createAppointment(UUID.randomUUID(), "Bearer test-token", request);
                    successCount.incrementAndGet();
                } catch (SlotFullyBookedException expectedForLosers) {
                    // các thread thua cuộc sau khi slot duy nhất đã bị book — đúng như kỳ vọng.
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(appointmentRepository.count()).isEqualTo(1);

        appointmentRepository.deleteAll();
        appointmentSlotRepository.deleteById(slot.getId());
    }

    @Test
    @Transactional
    void getAppointmentDetail_enrichesWithPetFromProfileService() {
        when(profileServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());
        when(profileServiceClient.getPetById(any(), any())).thenReturn(dummyPet());

        LocalDate date = LocalDate.of(2026, 12, 5);
        LocalTime time = LocalTime.of(9, 0);
        appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(date).startTime(time).endTime(time.plusMinutes(30)).build());

        AppointmentResponse created = appointmentService.createAppointment(UUID.randomUUID(), "Bearer test-token",
                new AppointmentRequest(UUID.randomUUID(), date, time, "Checkup"));

        AppointmentDetailResponse detail = appointmentService.getAppointmentDetail(created.id(), "Bearer staff-token");

        assertThat(detail.id()).isEqualTo(created.id());
        assertThat(detail.pet().name()).isEqualTo("Milo");
    }

    @Test
    @Transactional
    void getAppointmentDetail_unknownId_throwsResourceNotFound() {
        assertThatThrownBy(() -> appointmentService.getAppointmentDetail(UUID.randomUUID(), "Bearer staff-token"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @Transactional
    void listMyAppointments_returnsOnlyOwnAppointments() {
        when(profileServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());

        LocalDate date = LocalDate.of(2026, 12, 6);
        appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(date)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(9, 30)).build());
        appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(date)
                .startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 0)).build());

        UUID customerA = UUID.randomUUID();
        UUID customerB = UUID.randomUUID();
        appointmentService.createAppointment(customerA, "Bearer test-token",
                new AppointmentRequest(UUID.randomUUID(), date, LocalTime.of(9, 0), null));
        appointmentService.createAppointment(customerB, "Bearer test-token",
                new AppointmentRequest(UUID.randomUUID(), date, LocalTime.of(9, 30), null));

        List<AppointmentResponse> customerAAppointments = appointmentService.listMyAppointments(customerA);

        assertThat(customerAAppointments).hasSize(1);
        assertThat(customerAAppointments.get(0).customerUserId()).isEqualTo(customerA);
    }

    @Test
    @Transactional
    void search_filtersByDoctorAndDate() {
        when(profileServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());

        LocalDate date = LocalDate.of(2026, 12, 7);
        UUID doctorA = UUID.randomUUID();
        UUID doctorB = UUID.randomUUID();
        appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(doctorA).date(date)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(9, 30)).build());
        appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(doctorB).date(date)
                .startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 0)).build());

        appointmentService.createAppointment(UUID.randomUUID(), "Bearer test-token",
                new AppointmentRequest(UUID.randomUUID(), date, LocalTime.of(9, 0), null));
        appointmentService.createAppointment(UUID.randomUUID(), "Bearer test-token",
                new AppointmentRequest(UUID.randomUUID(), date, LocalTime.of(9, 30), null));

        List<AppointmentResponse> doctorAResults = appointmentService.search(date, null, doctorA);

        assertThat(doctorAResults).hasSize(1);
        assertThat(doctorAResults.get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    @Transactional
    void cancelAppointment_byNonOwnerCustomer_throwsResourceNotFound() {
        when(profileServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());

        LocalDate date = LocalDate.of(2026, 12, 8);
        LocalTime time = LocalTime.of(9, 0);
        appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(date).startTime(time).endTime(time.plusMinutes(30)).build());

        UUID owner = UUID.randomUUID();
        AppointmentResponse created = appointmentService.createAppointment(owner, "Bearer test-token",
                new AppointmentRequest(UUID.randomUUID(), date, time, null));

        assertThatThrownBy(() -> appointmentService.cancelAppointment(created.id(), UUID.randomUUID(), false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @Transactional
    void updateStatus_updatesAppointmentStatus() {
        when(profileServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());

        LocalDate date = LocalDate.of(2026, 12, 9);
        LocalTime time = LocalTime.of(9, 0);
        appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(date).startTime(time).endTime(time.plusMinutes(30)).build());

        AppointmentResponse created = appointmentService.createAppointment(UUID.randomUUID(), "Bearer test-token",
                new AppointmentRequest(UUID.randomUUID(), date, time, null));

        AppointmentResponse updated = appointmentService.updateStatus(created.id(), AppointmentStatus.CONFIRMED);

        assertThat(updated.status()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    // Test quan trọng nhất của bước này: xác nhận migration V2 (partial unique index thay cho
    // UNIQUE(slot_id) cứng) thực sự cho phép đặt lại đúng slot sau khi appointment cũ bị huỷ —
    // trước migration, insert thứ 2 sẽ ném DataIntegrityViolationException do vi phạm unique cũ.
    @Test
    void cancelAppointment_releasesSlot_andAllowsRebookingSameSlot() {
        when(profileServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());

        LocalDate date = LocalDate.of(2026, 12, 11);
        LocalTime time = LocalTime.of(9, 0);
        AppointmentSlot slot = appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(date).startTime(time).endTime(time.plusMinutes(30)).build());

        AppointmentResponse first = appointmentService.createAppointment(UUID.randomUUID(), "Bearer test-token",
                new AppointmentRequest(UUID.randomUUID(), date, time, "First booking"));

        appointmentService.cancelAppointment(first.id(), first.customerUserId(), false);

        AppointmentSlot reloadedAfterCancel = appointmentSlotRepository.findById(slot.getId()).orElseThrow();
        assertThat(reloadedAfterCancel.getStatus()).isEqualTo(SlotStatus.AVAILABLE);

        AppointmentResponse second = appointmentService.createAppointment(UUID.randomUUID(), "Bearer test-token",
                new AppointmentRequest(UUID.randomUUID(), date, time, "Second booking after cancel"));

        assertThat(second.slotId()).isEqualTo(slot.getId());
        assertThat(second.status()).isEqualTo(AppointmentStatus.PENDING);

        appointmentRepository.deleteAll();
        appointmentSlotRepository.deleteById(slot.getId());
    }

    private PetResponse dummyPet() {
        return new PetResponse(UUID.randomUUID(), "Milo", "Dog", "Poodle", "MALE",
                LocalDate.of(2020, 1, 1), null, Instant.now(), Instant.now());
    }

    private FeignException notFoundFromProfileService() {
        Request request = Request.create(Request.HttpMethod.GET, "/profile/customer/me/pets/x",
                Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .status(404).reason("Not Found").request(request).headers(Map.of()).build();
        return FeignException.errorStatus("ProfileServiceClient#getMyPet", response);
    }
}
